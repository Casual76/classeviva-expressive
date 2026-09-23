package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekTimeGridMathTest {

  @Test
  fun range_roundsOutToWholeHours() {
    assertEquals(MinuteSpan(8 * 60, 14 * 60), timeGridRange(listOf(MinuteSpan(8 * 60 + 10, 13 * 60 + 50))))
    assertEquals(MinuteSpan(8 * 60, 13 * 60), timeGridRange(listOf(MinuteSpan(8 * 60, 13 * 60))))
  }

  @Test
  fun range_withNothing_isAMorning() {
    assertEquals(WeekTimeGridDefaults.DefaultRange, timeGridRange(emptyList()))
  }

  @Test
  fun range_staysInsideTheClamp() {
    val range = timeGridRange(listOf(MinuteSpan(5 * 60, 6 * 60), MinuteSpan(21 * 60, 23 * 60)))
    assertEquals(WeekTimeGridDefaults.Clamp, range)
  }

  @Test
  fun lanes_disjointSpansShareOneLane() {
    val lanes = timeGridLanes(listOf(MinuteSpan(480, 540), MinuteSpan(540, 600)))
    assertEquals(listOf(TimeGridLane(0, 1), TimeGridLane(0, 1)), lanes)
  }

  @Test
  fun lanes_overlappingSpansSitSideBySide() {
    val lanes = timeGridLanes(listOf(MinuteSpan(480, 540), MinuteSpan(500, 560)))
    assertEquals(listOf(TimeGridLane(0, 2), TimeGridLane(1, 2)), lanes)
  }

  @Test
  fun lanes_aChainReusesTheFreedLane() {
    // A tocca B, B tocca C, A e C no: due corsie, e C torna nella prima.
    val lanes = timeGridLanes(listOf(MinuteSpan(480, 540), MinuteSpan(520, 600), MinuteSpan(550, 620)))
    assertEquals(listOf(TimeGridLane(0, 2), TimeGridLane(1, 2), TimeGridLane(0, 2)), lanes)
  }

  @Test
  fun lanes_countPerCluster() {
    val lanes = timeGridLanes(listOf(MinuteSpan(480, 540), MinuteSpan(480, 540), MinuteSpan(660, 720)))
    assertEquals(TimeGridLane(0, 1), lanes[2])
  }

  @Test
  fun minuteHeight_fitsTheDayButNeverBelowReadable() {
    val range = MinuteSpan(480, 840)
    // Tanto spazio: vince il tetto.
    assertEquals(WeekTimeGridDefaults.MaxMinuteHeight, timeGridMinuteHeight(2000.dp, range, 60, 1f))
    // Poco spazio: vince la leggibilita' dell'ora piu' corta.
    val tight = timeGridMinuteHeight(200.dp, range, 60, 1f)
    assertTrue(tight >= WeekTimeGridDefaults.ReadableEvent / 60)
    // Testo grande: il minuto si allunga.
    assertTrue(timeGridMinuteHeight(200.dp, range, 60, 1.5f) > tight)
  }
}
