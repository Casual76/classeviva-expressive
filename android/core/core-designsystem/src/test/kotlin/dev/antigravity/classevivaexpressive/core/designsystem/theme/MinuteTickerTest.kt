package dev.antigravity.classevivaexpressive.core.designsystem.theme

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinuteTickerTest {

  @Test
  fun waitsAFullMinuteWhenTheMinuteHasJustTurned() {
    assertEquals(60_000L, millisUntilNextMinute(LocalDateTime.of(2026, 3, 14, 10, 30, 0, 0)))
  }

  @Test
  fun waitsOnlyTheRemainderNearTheEdge() {
    val justBefore = LocalDateTime.of(2026, 3, 14, 10, 30, 59, 900_000_000)
    assertEquals(100L, millisUntilNextMinute(justBefore))
  }

  @Test
  fun neverWaitsZero() {
    // Un'attesa di zero trasformerebbe il ticker in un ciclo che gira a vuoto: il bordo esatto
    // dell'ultimo nanosecondo deve comunque produrre un'attesa.
    val lastInstant = LocalDateTime.of(2026, 3, 14, 10, 30, 59, 999_999_999)
    assertTrue(millisUntilNextMinute(lastInstant) >= 1L)
  }

  @Test
  fun theWaitIsAlwaysWithinAMinute() {
    (0..59).forEach { second ->
      val now = LocalDateTime.of(2026, 3, 14, 10, 30, second, 0)
      val wait = millisUntilNextMinute(now)
      assertTrue("secondo $second -> attesa $wait", wait in 1L..60_000L)
    }
  }
}
