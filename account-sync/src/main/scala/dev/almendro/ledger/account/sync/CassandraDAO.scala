package dev.almendro.ledger.account.sync

import java.time.Instant
import java.util.UUID
import dev.almendro.ledger.services.account.AccountMessage
import dev.almendro.ledger.services.account.AccountMessage.MsgType
import io.getquill._

case class Account(
                    accountId: Int,
                    status: String,
                    balance: Option[BigDecimal] = None
                  )

case class AccountEntry(
                         accountId: Int,
                         seqNumber: Int,
                         entryDate: Instant,
                         entryReason: String,
                         correlationId: UUID,
                         amount: BigDecimal
                       )

object CassandraDAO {
  val ctx = new CassandraSyncContext(SnakeCase, "ctx")
  import ctx._

  def save(evt: AccountMessage): Unit = {
    evt.msgType match {
      case MsgType.AccountOpened(value) =>
        saveAccount(Account(evt.accountId, "open", Some(BigDecimal(value.initialAmount))))

      case MsgType.AccountClosed(_) =>
        saveAccount(Account(evt.accountId, "closed"))

      case MsgType.AccountBlocked(value) =>
        saveAccount(Account(evt.accountId, "blocked"))

      case MsgType.AccountUnblocked(value) =>
        saveAccount(Account(evt.accountId, "open"))

      case MsgType.Withdrawn(value) =>
          saveAccount(Account(evt.accountId, "open", Some(BigDecimal(value.currentBalance))))
          saveAccountEntry(
            AccountEntry(
              evt.accountId,
              value.seqNumber.toInt,
              Instant.ofEpochSecond(value.timestamp),
              "withdraw",
              UUID.fromString(evt.correlationId),
              BigDecimal(value.amount))
          )

      case MsgType.Deposited(value) =>
          saveAccount(Account(evt.accountId, "open", Some(BigDecimal(value.currentBalance))))
          saveAccountEntry(
            AccountEntry(
              evt.accountId,
              value.seqNumber.toInt,
              Instant.ofEpochSecond(value.timestamp),
              "deposit",
              UUID.fromString(evt.correlationId),
              BigDecimal(value.amount))
          )

      case MsgType.WithdrawCancelled(value) =>
          saveAccount(Account(evt.accountId, "open", Some(BigDecimal(value.currentBalance))))
          saveAccountEntry(
            AccountEntry(
              evt.accountId,
              value.seqNumber.toInt,
              Instant.ofEpochSecond(value.timestamp),
              "withdraw cancelled",
              UUID.fromString(evt.correlationId),
              BigDecimal(value.amount))
          )
    }
  }

  private def saveAccount(evt: Account) = {
    evt.balance match {
      case Some(_) => ctx.run(query[Account].insert(lift(evt)))
      case None => ctx.run(query[Account].filter(_.accountId == lift(evt).accountId).update(_.status -> lift(evt).status))
    }
  }

  private def saveAccountEntry(evt: AccountEntry) = {
    ctx.run(query[AccountEntry].insert(lift(evt)))
  }
}
