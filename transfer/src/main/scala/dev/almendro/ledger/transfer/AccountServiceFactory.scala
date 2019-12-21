package dev.almendro.ledger.transfer

import dev.almendro.ledger.services.account.AccountServiceGrpc
import io.grpc.ManagedChannelBuilder

object AccountServiceFactory {
  def getAccountService: AccountServiceGrpc.AccountServiceStub = {
    val channelBuilder = ManagedChannelBuilder.forAddress(
      sys.env("ACCOUNT_CLUSTER_HOSTNAME"),
      sys.env("ACCOUNT_CLUSTER_PORT").toInt
    )
    channelBuilder.usePlaintext(true)
    AccountServiceGrpc.stub(channelBuilder.build())
  }
}
