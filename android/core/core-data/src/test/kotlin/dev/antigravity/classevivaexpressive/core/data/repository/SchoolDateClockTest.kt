package dev.antigravity.classevivaexpressive.core.data.repository

import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolDateClockTest {
  @Test
  fun midnightEmitsTheNextDayWithoutDatabaseOrNetworkChanges() = runTest {
    var now = ZonedDateTime.parse("2026-11-17T23:59:59+01:00[Europe/Rome]")
    val delays = mutableListOf<Long>()
    val days = observeSchoolDate(now = { now }, pause = { millis ->
      delays += millis
      now = now.plusNanos(millis * 1_000_000)
    }).take(2).toList()
    assertEquals(listOf(LocalDate.parse("2026-11-17"), LocalDate.parse("2026-11-18")), days)
    assertEquals(listOf(1_000L), delays)
  }

  @Test
  fun daylightSavingAndManualTimezoneChangesRecoverWithinOneMinute() = runTest {
    var now = ZonedDateTime.parse("2026-10-25T02:59:59+02:00[Europe/Rome]")
    val delays = mutableListOf<Long>()
    val days = observeSchoolDate(now = { now }, pause = { millis ->
      delays += millis
      now = if (delays.size == 1) now.plusSeconds(1) else ZonedDateTime.parse("2026-10-26T00:01:00+09:00[Asia/Tokyo]")
    }).take(2).toList()
    assertEquals(listOf(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-26")), days)
    assertTrue(delays.all { it in 1L..60_000L })
  }
}
