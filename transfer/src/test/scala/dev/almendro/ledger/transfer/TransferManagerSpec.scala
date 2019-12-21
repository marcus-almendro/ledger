package dev.almendro.ledger.transfer

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import dev.almendro.ledger.services.ClockProvider
import dev.almendro.ledger.services.account.AccountServiceGrpc.AccountService
import dev.almendro.ledger.services.account._
import dev.almendro.ledger.services.transfer.{Transfer, TransferExecuted, TransferFailedDueToDepositAccount, TransferFailedDueToWithdrawAccount, TransferMessage}
import org.scalamock.scalatest.MockFactory
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

object TransferManagerSpec {
  val correlationId = UUID.randomUUID().toString
  ClockProvider.setClock(Clock.fixed(Instant.MIN, ZoneId.systemDefault()))
  val withdrawAccountMessage = AccountMessage(1, correlationId).withWithdraw(Withdraw("10"))
  val withdrawnAccountMessage = AccountMessage(1, correlationId).withWithdrawn(Withdrawn("1", "10"))
  val depositAccountMessage = AccountMessage(2, correlationId).withDeposit(Deposit("10"))
  val depositedAccountMessage = AccountMessage(2, correlationId).withDeposited(Deposited("1", "10"))
  val cancelWithdraw = AccountMessage(1, correlationId).withCancelWithdraw(CancelWithdraw(Some(Withdraw("10"))))
  val withdrawCancelled = AccountMessage(1, correlationId).withWithdrawCancelled(WithdrawCancelled("2", "10"))
  val insufficientFunds = AccountMessage(1, correlationId).withInsufficientFunds(InsufficientFunds.defaultInstance)
  val invalidOperation = AccountMessage(2, correlationId).withInvalidOperation(InvalidOperation("error"))
  val transferExecuted = TransferMessage(correlationId).withTransferExecuted(TransferExecuted(Instant.MIN.getEpochSecond))
  val errorWithdraw = TransferMessage(correlationId).withTransferFailedDueToWithdrawAccount(TransferFailedDueToWithdrawAccount.defaultInstance)
  val errorDeposit = TransferMessage(correlationId).withTransferFailedDueToDepositAccount(TransferFailedDueToDepositAccount.defaultInstance)
}

class TransferManagerSpec extends FlatSpec
  with Matchers
  with MockFactory {
  import TransferManagerSpec._

  "An account with funds" should "be able to transfer funds to another account" in {
    val svc = mock[AccountService]
    val tm = new TransferManager(svc)

    svc.ask _ expects withdrawAccountMessage returning Future(withdrawnAccountMessage)
    svc.ask _ expects depositAccountMessage returning Future(depositedAccountMessage)

    val ret = tm.executeTransfer(new TransferMessage(correlationId = correlationId).withTransfer(Transfer(1, 2, "10")))
    val result = Await.result(ret, 1 second)
    result shouldEqual transferExecuted
  }

  "An account without funds" should "not be able to transfer funds to another account" in {
    val svc = mock[AccountService]
    val tm = new TransferManager(svc)

    svc.ask _ expects withdrawAccountMessage returning Future(insufficientFunds)

    val ret = tm.executeTransfer(new TransferMessage(correlationId = correlationId).withTransfer(Transfer(1, 2, "10")))
    val result = Await.result(ret, 1 second)
    result shouldEqual errorWithdraw
  }

  it should "rollback withdraw if deposit account is closed" in {
    val svc = mock[AccountService]
    val tm = new TransferManager(svc)

    svc.ask _ expects withdrawAccountMessage returning Future(withdrawnAccountMessage)
    svc.ask _ expects depositAccountMessage returning Future(invalidOperation)
    svc.ask _ expects cancelWithdraw returning Future(withdrawCancelled)

    val ret = tm.executeTransfer(new TransferMessage(correlationId = correlationId).withTransfer(Transfer(1, 2, "10")))
    val result = Await.result(ret, 1 second)
    result shouldEqual errorDeposit
  }

  it should "not be able to transfer if withdraw account is closed" in {
    val svc = mock[AccountService]
    val tm = new TransferManager(svc)

    svc.ask _ expects withdrawAccountMessage returning Future(invalidOperation)

    val ret = tm.executeTransfer(new TransferMessage(correlationId = correlationId).withTransfer(Transfer(1, 2, "10")))
    val result = Await.result(ret, 1 second)
    result shouldEqual errorWithdraw
  }
}
