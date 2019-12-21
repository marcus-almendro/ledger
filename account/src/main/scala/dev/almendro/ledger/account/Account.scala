package dev.almendro.ledger.account

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import dev.almendro.ledger.services.ClockProvider

import scala.concurrent.duration._
import scala.language.postfixOps

object Account {
  case class AccountState(accountId: Int,
                          balance: Money,
                          seqNumber: BigInt,
                          events: Cache[Command, Event] = getCache)
                         (implicit log: LoggingAdapter) {

    def getCachedEvent(cmd: Command): Option[Event] = {
      val cachedEvent = events.getIfPresent(cmd)
      log.debug(s"Getting $cmd from cache: key ${if(cachedEvent == null) "NOT" else ""}FOUND")
      Option(cachedEvent)
    }

    def applyEvent(evt: Event, cmd: Command): AccountState = {
      if (cmd != null) {
        events.get(cmd, _ => {
          log.debug(s"Putting value into cache $evt")
          evt
        })
      }
      evt match {
        case AccountOpened(_, initialAmount, _, _) => copy(balance = initialAmount)
        case AccountClosed(_, _, _) => this
        case AccountBlocked(_, _, _) => this
        case AccountUnblocked(_, _, _) => this
        case Withdrawn(_, seqNumber, _, currentBalance, _, _) => copy(balance = currentBalance, seqNumber = seqNumber)
        case Deposited(_, seqNumber, _, currentBalance, _, _) => copy(balance = currentBalance, seqNumber = seqNumber)
        case WithdrawCancelled(_, seqNumber, _, currentBalance, _, _) => copy(balance = currentBalance, seqNumber = seqNumber)
        case _ => throw new IllegalStateException()
      }
    }
  }

  def getCache: Cache[Command, Event]  = {
    Caffeine.newBuilder()
      .maximumSize(100000L)
      .expireAfterWrite(5, TimeUnit.SECONDS)
      .build[Command, Event]
  }

  sealed trait AccountSerializable

  case class AccountSnapshot(accountId: Int, balance: BigDecimal, seqNumber: BigInt) extends AccountSerializable

  sealed trait AccountMessage extends AccountSerializable {
    val accountId: Int
    val correlationId: UUID
  }

  sealed trait Command extends AccountMessage
  case class OpenAccount(accountId: Int, initialAmount: Money, correlationId: UUID) extends Command
  case class CloseAccount(accountId: Int, correlationId: UUID) extends Command
  case class BlockAccount(accountId: Int, correlationId: UUID) extends Command
  case class UnblockAccount(accountId: Int, correlationId: UUID) extends Command
  case class Withdraw(accountId: Int, amount: Money, correlationId: UUID) extends Command
  case class Deposit(accountId: Int, amount: Money, correlationId: UUID) extends Command
  case class CancelWithdraw(accountId: Int, withdrawCmd: Withdraw, correlationId: UUID) extends Command

  sealed trait Event extends AccountMessage {
    val timestamp: Long
  }
  case class AccountOpened(accountId: Int, initialAmount: Money, correlationId: UUID, timestamp: Long) extends Event
  case class AccountClosed(accountId: Int, correlationId: UUID, timestamp: Long) extends Event
  case class AccountBlocked(accountId: Int, correlationId: UUID, timestamp: Long) extends Event
  case class AccountUnblocked(accountId: Int, correlationId: UUID, timestamp: Long) extends Event
  case class Withdrawn(accountId: Int, seqNumber: BigInt, amount: Money, currentBalance: Money, correlationId: UUID, timestamp: Long) extends Event
  case class Deposited(accountId: Int, seqNumber: BigInt, amount: Money, currentBalance: Money, correlationId: UUID, timestamp: Long) extends Event
  case class WithdrawCancelled(accountId: Int, seqNumber: BigInt, amount: Money, currentBalance: Money, correlationId: UUID, timestamp: Long) extends Event

  sealed trait CommandReply extends AccountMessage
  case class InvalidOperation(accountId: Int, reason: String, correlationId: UUID) extends CommandReply
  case class InsufficientFunds(accountId: Int, correlationId: UUID) extends CommandReply

  case object Stop
}

class Account extends PersistentActor with ActorLogging {
  import Account._

  override def persistenceId: String = "Account-" + self.path.name
  var state: AccountState = AccountState(self.path.name.toInt, Money.ZERO, 0)(log)

  override def receiveCommand: Receive = getIdempotentReceiveCommandFor(emptyAccount)

  def getIdempotentReceiveCommandFor(next: Receive): Receive = {
    case cmd: Command =>
      log.info(s"Received command $cmd")
      state.getCachedEvent(cmd) match {
        case None => LoggingReceive(next)(context)(cmd)
        case Some(evt) => sendResponse(evt)
      }

    case ReceiveTimeout =>
      log.info("Received Timeout")
      context.parent ! Passivate(stopMessage = Stop)

    case Stop =>
      log.info("Received Stop Command")
      context.stop(self)
  }

  def emptyAccount: Receive = {
    case cmd @ OpenAccount(id, initialAmount, correlationId) =>
        persist(AccountOpened(id, initialAmount, correlationId, ClockProvider.getTimestamp)) { evt =>
          updateState(evt, cmd)
          sendResponse(evt)
        }
    case c: Command => sendResponse(InvalidOperation(c.accountId, "Operation not permitted for current state", c.correlationId))
  }

  def openedAccount: Receive = {
    case cmd: Command =>
      val response = cmd match {

        case CloseAccount(id, correlationId) =>
          AccountClosed(id, correlationId, ClockProvider.getTimestamp)

        case BlockAccount(id, correlationId) =>
          AccountBlocked(id, correlationId, ClockProvider.getTimestamp)

        case Withdraw(id, amount, correlationId) =>
          if (state.balance >= amount)
            Withdrawn(id, state.seqNumber + 1, amount, state.balance - amount, correlationId, ClockProvider.getTimestamp)
          else
            InsufficientFunds(id, correlationId)

        case Deposit(id, amount, correlationId) =>
          Deposited(id, state.seqNumber + 1, amount, state.balance + amount, correlationId, ClockProvider.getTimestamp)

        case CancelWithdraw(_, withdrawToCancel, correlationId) =>
          state.getCachedEvent(withdrawToCancel) match {
            case Some(Withdrawn(id, seqNumber, amount, _, _, _)) =>
              WithdrawCancelled(id, seqNumber, amount, state.balance + amount, correlationId, ClockProvider.getTimestamp)

            case _ =>
              InvalidOperation(state.accountId, s"Withdraw $withdrawToCancel not found!", correlationId) //todo: look in journal?
          }

        case c: Command =>
          InvalidOperation(c.accountId, "Operation not permitted for current state", c.correlationId)
      }

    response match {
      case event: Event =>
        persist(event) { e =>
          updateState(e, cmd)
          sendResponse(e)
        }

      case cmdReply: CommandReply =>
        sendResponse(cmdReply)
    }
  }

  def closedAccount: Receive = {
    case c: Command =>
      sendResponse(InvalidOperation(c.accountId, "Operation not permitted for current state", c.correlationId))
  }

  def blockedAccount: Receive = {
    case cmd @ CloseAccount(id, correlationId) =>
        persist(AccountClosed(id, correlationId, ClockProvider.getTimestamp)) { evt =>
          updateState(evt, cmd)
          sendResponse(evt)
        }
    case cmd @ UnblockAccount(id, correlationId) =>
        persist(AccountUnblocked(id, correlationId, ClockProvider.getTimestamp)) { evt =>
          updateState(evt, cmd)
          sendResponse(evt)
        }
    case c: Command => sendResponse(InvalidOperation(c.accountId, "Operation not permitted for current state", c.correlationId))
  }

  override def receiveRecover: Receive = {
    case evt: Event => updateState(evt, null)
    case SnapshotOffer(_, snapshot: AccountSnapshot) =>
      log.info(s"Recovering from snapshot $snapshot")
      state = state.copy(balance = Money(snapshot.balance), seqNumber = snapshot.seqNumber)(log)
  }

  def updateState(evt: Event, cmd: Command): Unit = {
    state = state.applyEvent(evt, cmd)
    context.become(getIdempotentReceiveCommandFor(evt match {
      case _: AccountOpened => openedAccount
      case _: AccountClosed => closedAccount
      case _: AccountBlocked => blockedAccount
      case _: AccountUnblocked => openedAccount
      case _: Withdrawn => openedAccount
      case _: Deposited => openedAccount
      case _: WithdrawCancelled => openedAccount
    }))

    if (state.seqNumber > 0 && state.seqNumber % 10 == 0) {
      val snapshot = AccountSnapshot(state.accountId, state.balance.amount, state.seqNumber)
      log.info(s"Saving snapshot $snapshot")
      saveSnapshot(snapshot)
    }
  }

  def sendResponse(response: Any): Unit = {
    log.info(s"Sending response $response")
    sender() ! response
    context.setReceiveTimeout(15 seconds)
  }
}
