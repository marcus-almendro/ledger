package dev.almendro.ledger.services

import java.time.{Clock, Instant}

object ClockProvider {
  private var clock = Clock.systemUTC()

  def getTimestamp: Long = Instant.now(clock).getEpochSecond

  def setClock(clock: Clock): Unit = {
    this.clock = clock
  }
}
