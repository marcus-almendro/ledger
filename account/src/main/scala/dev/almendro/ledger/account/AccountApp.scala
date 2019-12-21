package dev.almendro.ledger.account

import java.util.concurrent.ForkJoinPool
import akka.actor.{ActorSystem, _}
import akka.cluster.sharding._
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import dev.almendro.ledger.services.account.AccountServiceGrpc
import io.grpc.ServerBuilder
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

object AccountApp extends App {

  implicit val system: ActorSystem = ActorSystem("account-cluster", ConfigFactory.parseMap(Map(
    "akka.remote.artery.canonical.hostname" -> sys.env("HOSTNAME"),
  ).asJava).withFallback(ConfigFactory.load()))

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(new ForkJoinPool(16))

  val sharding = ClusterSharding(system)

  val accountRegion: ActorRef =
    sharding.start(
      typeName = "Account",
      entityProps = Props[Account],
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case cmd: Account.Command => (cmd.accountId.toString, cmd)
      },
      extractShardId = {
        case cmd: Account.Command => (cmd.accountId % 50).toString
      }
    )

  val serverBuilder = ServerBuilder.forPort(8080)
  serverBuilder.addService(AccountServiceGrpc.bindService(new AccountGrpcService(accountRegion), ec))
  serverBuilder.build().start()
  println("Server listening on port 8080")
}
