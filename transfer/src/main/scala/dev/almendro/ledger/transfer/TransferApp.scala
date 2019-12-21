package dev.almendro.ledger.transfer

import scala.concurrent.ExecutionContext.Implicits._

object TransferApp extends App {
  val queueIn = "transfers.in"
  val channel = RabbitMqChannelFactory.getChannel(queueIn)
  val transferManager = new TransferManager(AccountServiceFactory.getAccountService)
  val consumer = new RabbitMqConsumer(transferManager, queueIn, channel)
  consumer.start()
}
