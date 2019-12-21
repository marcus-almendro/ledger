package dev.almendro.ledger.transfer

import com.newmotion.akka.rabbitmq.{Channel, Connection, ConnectionFactory}
import com.rabbitmq.client.Address
import org.slf4j.LoggerFactory

object RabbitMqChannelFactory {
  private val log = LoggerFactory.getLogger("RabbitMqChannelFactory")

  def getChannel(queue: String): Channel = {
    val host = sys.env("RABBITMQ_HOST")
    val port = sys.env("RABBITMQ_PORT").toInt
    waitPortOpen(host, port)

    val factory = new ConnectionFactory()
    val connection: Connection = factory.newConnection(Array(new Address(host, port)))
    val channel: Channel = connection.createChannel()
    channel.queueDeclarePassive(queue)
    channel.basicQos(sys.env("RABBITMQ_QOS").toInt)
    log.info(s"Connected to RabbitMQ at $host:$port!")
    channel
  }

  def waitPortOpen(host: String, port: Int): Boolean = {
    var trying = true
    while(trying) {
      try {
        val socket = new java.net.Socket(host, port)
        socket.close()
        trying = false
      } catch {
        case _: Throwable =>
      }
      log.info(s"Waiting for Rabbit MQ go online at $host:$port")
      Thread.sleep(1000)
    }
    true
  }
}
