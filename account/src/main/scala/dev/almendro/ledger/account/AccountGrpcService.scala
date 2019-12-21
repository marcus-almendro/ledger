package dev.almendro.ledger.account

import java.util.concurrent.ForkJoinPool
import akka.pattern.AskableActorRef
import akka.util.Timeout
import dev.almendro.ledger.services.account.AccountServiceGrpc.AccountService
import dev.almendro.ledger.services.account.AccountMessage
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class AccountGrpcService(accountRegion: AskableActorRef)
  extends AccountService {
  implicit val timeout: Timeout = 5 seconds
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(new ForkJoinPool(16))
  val serde = new GrpcSerializer()

  override def ask(in: AccountMessage): Future[AccountMessage] = {
    val future = accountRegion ? serde.fromGrpcFormat(in)
    future.map {
      case msg: Account.AccountMessage => serde.toGrpcFormat(msg)
    }
  }
}
