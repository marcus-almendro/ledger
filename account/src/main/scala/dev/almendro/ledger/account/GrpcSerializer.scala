package dev.almendro.ledger.account

import java.util.UUID
import akka.actor.InvalidMessageException
import akka.serialization.Serializer
import dev.almendro.ledger.services.account._
import dev.almendro.ledger.services.account.AccountMessage.MsgType

class GrpcSerializer extends Serializer {
  def fromGrpcFormat(a: dev.almendro.ledger.services.account.AccountMessage): Account.AccountMessage = {
    val result = a.msgType match {
      case MsgType.Empty => throw InvalidMessageException("Invalid GRPC message")

      case MsgType.OpenAccount(m) =>
        Account.OpenAccount(a.accountId, Money(BigDecimal(m.initialAmount)), UUID.fromString(a.correlationId))

      case MsgType.CloseAccount(m) =>
        Account.CloseAccount(a.accountId, UUID.fromString(a.correlationId))

      case MsgType.BlockAccount(m) =>
        Account.BlockAccount(a.accountId, UUID.fromString(a.correlationId))

      case MsgType.UnblockAccount(m) =>
        Account.UnblockAccount(a.accountId, UUID.fromString(a.correlationId))

      case MsgType.Withdraw(m) =>
        Account.Withdraw(a.accountId, Money(BigDecimal(m.amount)), UUID.fromString(a.correlationId))

      case MsgType.Deposit(m) =>
        Account.Deposit(a.accountId, Money(BigDecimal(m.amount)), UUID.fromString(a.correlationId))

      case MsgType.CancelWithdraw(m) =>
        Account.CancelWithdraw(a.accountId,
          Account.Withdraw(a.accountId, Money(BigDecimal(m.getWithdrawCmd.amount)), UUID.fromString(a.correlationId)),
          UUID.fromString(a.correlationId))

      case MsgType.AccountOpened(m) =>
        Account.AccountOpened(a.accountId, Money(BigDecimal(m.initialAmount)), UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.AccountClosed(m) =>
        Account.AccountClosed(a.accountId, UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.AccountBlocked(m) =>
        Account.AccountBlocked(a.accountId, UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.AccountUnblocked(m) =>
        Account.AccountUnblocked(a.accountId, UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.Withdrawn(m) =>
        Account.Withdrawn(a.accountId, BigInt(m.seqNumber), Money(BigDecimal(m.amount)), Money(BigDecimal(m.currentBalance)), UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.Deposited(m) =>
        Account.Deposited(a.accountId, BigInt(m.seqNumber), Money(BigDecimal(m.amount)), Money(BigDecimal(m.currentBalance)), UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.WithdrawCancelled(m) =>
        Account.WithdrawCancelled(a.accountId, BigInt(m.seqNumber), Money(BigDecimal(m.amount)), Money(BigDecimal(m.currentBalance)), UUID.fromString(a.correlationId), m.timestamp)

      case MsgType.InvalidOperation(m) =>
        Account.InvalidOperation(a.accountId, m.reason, UUID.fromString(a.correlationId))

      case MsgType.InsufficientFunds(m) =>
        Account.InsufficientFunds(a.accountId, UUID.fromString(a.correlationId))
    }
    result
  }

  def toGrpcFormat(a: Account.AccountMessage): AccountMessage = {
    val result = a match {
      case command: Account.Command =>
        command match {
          case Account.OpenAccount(accountId, initialAmount, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withOpenAccount(OpenAccount(initialAmount.amount.toString))

          case Account.CloseAccount(accountId, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withCloseAccount(CloseAccount())

          case Account.BlockAccount(accountId, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withBlockAccount(BlockAccount())

          case Account.UnblockAccount(accountId, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withUnblockAccount(UnblockAccount())

          case Account.Withdraw(accountId, amount, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withWithdraw(Withdraw(amount.amount.toString))

          case Account.Deposit(accountId, amount, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withDeposit(Deposit(amount.amount.toString()))

          case Account.CancelWithdraw(accountId, withdrawCmd, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withCancelWithdraw(CancelWithdraw(Some(Withdraw(withdrawCmd.amount.amount.toString))))
        }
      case event: Account.Event =>
        event match {
          case Account.AccountOpened(accountId, initialAmount, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
              .withAccountOpened(AccountOpened(initialAmount.amount.toString, timestamp))

          case Account.AccountClosed(accountId, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
              .withAccountClosed(AccountClosed(timestamp))

          case Account.AccountBlocked(accountId, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
              .withAccountBlocked(AccountBlocked(timestamp))

          case Account.AccountUnblocked(accountId, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
              .withAccountUnblocked(AccountUnblocked(timestamp))

          case Account.Withdrawn(accountId, seqNumber, amount, currentBalance, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
              .withWithdrawn(Withdrawn(seqNumber.toString, amount.amount.toString, currentBalance.amount.toString, timestamp))

          case Account.Deposited(accountId, seqNumber, amount, currentBalance, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
              .withDeposited(Deposited(seqNumber.toString, amount.amount.toString, currentBalance.amount.toString, timestamp))

          case Account.WithdrawCancelled(accountId, seqNumber, amount, currentBalance, correlationId, timestamp) =>
            AccountMessage(accountId, correlationId.toString)
            .withWithdrawCancelled(WithdrawCancelled(seqNumber.toString, amount.amount.toString, currentBalance.amount.toString, timestamp))

        }
      case reply: Account.CommandReply =>
        reply match {
          case Account.InvalidOperation(accountId, reason, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
              .withInvalidOperation(InvalidOperation(reason))

          case Account.InsufficientFunds(accountId, correlationId) =>
            AccountMessage(accountId, correlationId.toString)
            .withInsufficientFunds(InsufficientFunds())

        }
    }
    result
  }

  override def identifier: Int = 215684

  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case msg: AccountMessage => msg.toByteArray
      case msg: Account.AccountMessage => toGrpcFormat(msg).toByteArray
    }

  }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    fromGrpcFormat(AccountMessage.parseFrom(bytes))
}
