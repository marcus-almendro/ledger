package dev.almendro.ledger.account

case class Money(amount: BigDecimal) {
  val precision = 8
  def + (other: Money): Money = Money(this.amount + other.amount)
  def - (other: Money): Money = Money(this.amount - other.amount)
  def >=(other: Money): Boolean = this.amount >= other.amount
  def <=(other: Money): Boolean = this.amount <= other.amount
}

object Money {
  val ZERO = Money(0)
}