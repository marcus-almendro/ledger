package dev.almendro.ledger.transfer

import com.newmotion.akka.rabbitmq.{BasicProperties, DefaultConsumer}
import com.rabbitmq.client.{Channel, Envelope, MessageProperties}
import dev.almendro.ledger.services.transfer.TransferMessage
import dev.almendro.ledger.services.transfer.TransferMessage.MsgType
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class RabbitMqConsumer(transferManager: TransferManager, queueIn: String, channel: Channel)
                      (implicit ec: ExecutionContext) extends DefaultConsumer(channel) {

  private val log = LoggerFactory.getLogger(classOf[RabbitMqConsumer])

  override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {

    val transferMessage = TransferMessage.parseFrom(body)

    log.debug("received: " + transferMessage)

    transferManager.executeTransfer(transferMessage).onComplete(f = {

      case Failure(_) =>
        channel.basicNack(envelope.getDeliveryTag, false, true)

      case Success(value) => value.asInstanceOf[TransferMessage].msgType match {
        case MsgType.TransferExecuted(_)
             | MsgType.TransferFailedDueToWithdrawAccount(_)
             | MsgType.TransferFailedDueToDepositAccount(_) =>

          channel.basicPublish("", properties.getReplyTo, MessageProperties.PERSISTENT_BASIC, value.toByteArray)

          channel.basicAck(envelope.getDeliveryTag, false)

        case _ =>
          channel.basicNack(envelope.getDeliveryTag, false, true)
      }
    })
  }

  def start(): Unit = channel.basicConsume(queueIn, false, this)
}
