package dev.almendro.ledger.account

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import akka.actor._
import akka.testkit._
import com.typesafe.config.ConfigFactory
import Account._
import dev.almendro.ledger.services.ClockProvider
import org.scalatest._

class AccountSpec
  extends TestKit(ActorSystem("AccountSpec", config = ConfigFactory.load().getConfig("test")))
    with Matchers
    with fixture.FlatSpecLike
    with ImplicitSender
    with OneInstancePerTest
{
  case class FixtureParam(account: ActorRef, closedAccount: ActorRef)

  ClockProvider.setClock(Clock.fixed(Instant.MIN, ZoneId.systemDefault()))

  val A1_ID = 1
  val C1_ID = 2
  val epoch = Instant.MIN.getEpochSecond

  def withFixture(test: OneArgTest): Outcome = {
    val correlationId1 = UUID.randomUUID()
    val correlationId2 = UUID.randomUUID()

    val a1 = system.actorOf(Props[Account], "1")
    a1 ! OpenAccount(A1_ID, Money(10), correlationId1)
    expectMsg(AccountOpened(A1_ID, Money(10), correlationId1, epoch))

    val c1 = system.actorOf(Props[Account], "2")
    c1 ! OpenAccount(C1_ID, Money(10), correlationId1)
    c1 ! CloseAccount(C1_ID, correlationId2)
    expectMsg(AccountOpened(C1_ID, Money(10), correlationId1, epoch))
    expectMsg(AccountClosed(C1_ID, correlationId2, epoch))

    withFixture(test.toNoArgTest(FixtureParam(a1, c1)))
  }

  "An account with funds" should "be able to withdraw a value less than total balance" in { f =>
      val c1 = UUID.randomUUID()
      f.account ! Withdraw(A1_ID, Money(5), c1)
      expectMsg(Withdrawn(A1_ID, 1, Money(5), Money(5), c1, epoch))
  }

  it should "be able to withdraw a value up to total balance" in { f =>
    val c1 = UUID.randomUUID()
    f.account ! Withdraw(A1_ID, Money(10), c1)
    expectMsg(Withdrawn(A1_ID, 1, Money(10), Money.ZERO, c1, epoch))

  }

  it should "not be able to withdraw a value greater than total balance" in { f =>
    val c1 = UUID.randomUUID()
    f.account ! Withdraw(A1_ID, Money(15), c1)
    expectMsg(InsufficientFunds(A1_ID, c1))
  }

  "An open account" should "accept deposit" in { f =>
    val c1 = UUID.randomUUID()
    f.account ! Deposit(A1_ID, Money(5), c1)
    expectMsg(Deposited(A1_ID, 1, Money(5), Money(15), c1, epoch))
  }

  it should "be idempotent for 5 seconds" in { f =>
    val c1 = UUID.randomUUID()
    f.account ! Deposit(A1_ID, Money(5), c1)
    expectMsg(Deposited(A1_ID, 1, Money(5), Money(15), c1, epoch))

    f.account ! Deposit(A1_ID, Money(5), c1)
    expectMsg(Deposited(A1_ID, 1, Money(5), Money(15), c1, epoch))

    Thread.sleep(6000)

    f.account ! Deposit(A1_ID, Money(5), c1)
    expectMsg(Deposited(A1_ID, 2, Money(5), Money(20), c1, epoch))
  }

  "A closed account" should "not accept deposit" in { f =>
    f.closedAccount ! Deposit(C1_ID, Money(5), UUID.randomUUID())
    expectMsgAllClassOf(classOf[InvalidOperation])
  }
}