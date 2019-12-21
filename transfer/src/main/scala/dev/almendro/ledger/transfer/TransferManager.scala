package dev.almendro.ledger.transfer

import dev.almendro.ledger.services.account.AccountMessage.MsgType
import dev.almendro.ledger.services.account.AccountServiceGrpc.AccountService
import dev.almendro.ledger.services.account.{AccountMessage, CancelWithdraw, Deposit, Withdraw}
import dev.almendro.ledger.services.transfer._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import dev.almendro.ledger.services.ClockProvider

class TransferManager(accountService: AccountService)(implicit ec: ExecutionContext) {

  def executeTransfer(transfer: TransferMessage): Future[TransferMessage] =
    sendWithdraw(transfer)

  private def sendWithdraw(cmd: TransferMessage): Future[TransferMessage] = {

    accountService.ask(AccountMessage(cmd.getTransfer.fromAccountId, cmd.correlationId.toString)
      .withWithdraw(Withdraw(cmd.getTransfer.amount.toString))) transformWith {
      case Success(value) =>
        value.msgType match {
          case _: MsgType.Withdrawn =>
            sendDeposit(cmd)

          case _: MsgType.InvalidOperation | _: MsgType.InsufficientFunds =>
            Future(TransferMessage(cmd.correlationId.toString)
              .withTransferFailedDueToWithdrawAccount(TransferFailedDueToWithdrawAccount.defaultInstance))
        }
      case Failure(_) =>
        Future(TransferMessage(cmd.correlationId.toString)
          .withInvalidOperation(InvalidOperation("error in the future")))
    }
  }

  private def sendDeposit(cmd: TransferMessage): Future[TransferMessage] = {

    accountService.ask(AccountMessage(cmd.getTransfer.toAccountId, cmd.correlationId.toString)
      .withDeposit(Deposit(cmd.getTransfer.amount.toString))) transformWith {
      case Success(value) =>
        value.msgType match {
          case _: MsgType.Deposited =>
            Future(TransferMessage(cmd.correlationId)
              .withTransferExecuted(TransferExecuted(ClockProvider.getTimestamp)))

          case _: MsgType.InvalidOperation =>
            cancelWithdraw(cmd)
        }
      case Failure(_) =>
        Future(TransferMessage(cmd.correlationId.toString)
          .withInvalidOperation(InvalidOperation("error in the future")))
    }
  }

  private def cancelWithdraw(cmd: TransferMessage): Future[TransferMessage] = {

    accountService.ask(AccountMessage(cmd.getTransfer.fromAccountId, cmd.correlationId.toString)
      .withCancelWithdraw(CancelWithdraw(Some(Withdraw(cmd.getTransfer.amount.toString))))) transformWith {

      case Success(value) =>
        value.msgType match {
          case _: MsgType.WithdrawCancelled =>
            Future(TransferMessage(cmd.correlationId)
              .withTransferFailedDueToDepositAccount(TransferFailedDueToDepositAccount.defaultInstance))

          case _: MsgType.InvalidOperation =>
            Future(TransferMessage(cmd.correlationId)
              .withTransferFailedAndCannotRollback(TransferFailedAndCannotRollback.defaultInstance))
        }
      case Failure(_) =>
        Future(TransferMessage(cmd.correlationId.toString)
          .withInvalidOperation(InvalidOperation("error in the future")))
    }
  }
}

