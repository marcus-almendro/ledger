package dev.almendro.ledger.account.sync

import akka.persistence.eventstore.EventStoreSerializer
import dev.almendro.ledger.services.account._
import eventstore.core.Content
import eventstore.{Event, EventData}

class GrpcSerializer extends EventStoreSerializer {

  override def identifier: Int = 215684

  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case msg: AccountMessage => msg.toByteArray
    }
  }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    AccountMessage.parseFrom(bytes)

  override def toEvent(o: AnyRef): EventData =
    EventData("GrpcAccountMessage", Content(toBinary(o)))

  override def fromEvent(event: Event, manifest: Class[_]): AnyRef =
    AccountMessage.parseFrom(event.data.data.value.toArray)
}
