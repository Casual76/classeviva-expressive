package dev.antigravity.classevivaexpressive.feature.dashboard

import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardTodayMomentTest {

  private fun lesson(time: String, end: String?, subject: String, duration: Int = 60) = Lesson(
    id = "$time-$subject",
    subject = subject,
    date = "2026-09-23",
    time = time,
    durationMinutes = duration,
    endTime = end,
  )

  private val day = listOf(
    lesson("08:00", "09:00", "ITALIANO"),
    lesson("09:00", "10:00", "STORIA"),
    lesson("10:00", "11:00", "STORIA"),
    lesson("11:10", null, "FISICA", duration = 50),
  )

  @Test
  fun live_fromTheFirstInstant() {
    assertEquals(TodayMoment.Live(index = 0, minutesLeft = 60, next = 1), todayMoment(day, LocalTime.of(8, 0)))
  }

  @Test
  fun live_countsDownToTheEndOfBackToBackHoursOfTheSameSubject() {
    assertEquals(TodayMoment.Live(index = 1, minutesLeft = 90, next = 3), todayMoment(day, LocalTime.of(9, 30)))
  }

  @Test
  fun breakTime_pointsAtTheNextLesson() {
    assertEquals(TodayMoment.Next(index = 3, minutesUntil = 5), todayMoment(day, LocalTime.of(11, 5)))
  }

  @Test
  fun beforeSchool_pointsAtTheFirstLesson() {
    assertEquals(TodayMoment.Next(index = 0, minutesUntil = 30), todayMoment(day, LocalTime.of(7, 30)))
  }

  @Test
  fun missingEndTime_usesTheDuration() {
    assertEquals(TodayMoment.Over, todayMoment(day, LocalTime.of(12, 0)))
    assertEquals(TodayMoment.Live(index = 3, minutesLeft = 1, next = null), todayMoment(day, LocalTime.of(11, 59)))
  }

  @Test
  fun noLessons_isEmpty() {
    assertEquals(TodayMoment.Empty, todayMoment(emptyList(), LocalTime.NOON))
  }
}
