package dev.antigravity.classevivaexpressive.feature.lessons

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonsCalendarRolloverTest {
  private val schoolDays = stableSchoolDays().map(DayOfWeek::name)

  private fun historyDays(today: LocalDate, offset: Int = 0) =
    (0L..5L).map { schoolWeekStart(offset, today).plusDays(it).toString() }

  @Test
  fun automaticTimetableSelectionFollowsTheNewDayWhileAManualSelectionStaysPinned() {
    val tuesday = LocalDate.of(2026, 11, 17)
    val wednesday = tuesday.plusDays(1)
    assertEquals("TUESDAY", resolveSchoolDayKey(schoolDays, null, tuesday.dayOfWeek.name))
    assertEquals("WEDNESDAY", resolveSchoolDayKey(schoolDays, null, wednesday.dayOfWeek.name))
    assertEquals("FRIDAY", resolveSchoolDayKey(schoolDays, "FRIDAY", wednesday.dayOfWeek.name))
  }

  @Test
  fun returningAfterSundayUsesTheNewWeekAndDropsAnOutdatedDateSelection() {
    val sunday = LocalDate.of(2026, 11, 22)
    val monday = sunday.plusDays(1)
    assertEquals(LocalDate.of(2026, 11, 16), schoolWeekStart(0, sunday))
    assertEquals(LocalDate.of(2026, 11, 23), schoolWeekStart(0, monday))
    assertEquals(
      monday.toString(),
      resolveSchoolDayKey(historyDays(monday), "2026-11-17", monday.toString()),
    )
  }

  @Test
  fun historyAutomaticSelectionFollowsMidnightWithinTheSameWeek() {
    val tuesday = LocalDate.of(2026, 11, 17)
    val wednesday = tuesday.plusDays(1)
    assertEquals(tuesday.toString(), resolveSchoolDayKey(historyDays(tuesday), null, tuesday.toString()))
    assertEquals(wednesday.toString(), resolveSchoolDayKey(historyDays(wednesday), null, wednesday.toString()))
    assertEquals(tuesday.toString(), resolveSchoolDayKey(historyDays(wednesday), tuesday.toString(), wednesday.toString()))
  }

  @Test
  fun relativeHistoryOffsetSurvivesTheNewYearAndWeekRollover() {
    val sunday = LocalDate.of(2027, 1, 3)
    val monday = sunday.plusDays(1)
    assertEquals(LocalDate.of(2026, 12, 21), schoolWeekStart(-1, sunday))
    assertEquals(LocalDate.of(2026, 12, 28), schoolWeekStart(-1, monday))
    assertEquals("2026-12-28", resolveSchoolDayKey(historyDays(monday, -1), null, monday.toString()))
  }

  @Test
  fun sundayFallsBackToTheFirstSchoolDay() {
    assertEquals("MONDAY", resolveSchoolDayKey(schoolDays, null, "SUNDAY"))
  }
}
