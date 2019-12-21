package dev.almendro.ledger.account.sync

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.eventstore.EventStoreSerialization
import akka.persistence.eventstore.query.scaladsl.EventStoreReadJournal
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.serialization.SerializationExtension
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import dev.almendro.ledger.services.account.{AccountMessage, Withdraw}
import eventstore.{Content, Event, EventData}

object AccountSyncApp extends App {
  implicit val system: ActorSystem = ActorSystem("account-sync")
  implicit val materializer = ActorMaterializer()

  val readJournal = PersistenceQuery(system).readJournalFor[EventStoreReadJournal](EventStoreReadJournal.Identifier)
  val source = readJournal.eventsByPersistenceId("$ce-Account", 0, Long.MaxValue)
  source.runWith(Sink.foreach(e => CassandraDAO.save(e.event.asInstanceOf[AccountMessage])))
}