package dev.antigravity.classevivaexpressive.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolYearRefTest {

  @Test
  fun august_stillBelongsToTheYearThatIsEnding() {
    // Classeviva answers every request for a year it has not opened with
    // "school year not started yet", so rolling over in August left the whole app empty for a month.
    assertEquals(SchoolYearRef(2025, 2026), SchoolYearRef.current(nowYear = 2026, nowMonth = 8))
  }

  @Test
  fun september_startsTheNewYear() {
    assertEquals(SchoolYearRef(2026, 2027), SchoolYearRef.current(nowYear = 2026, nowMonth = 9))
  }

  @Test
  fun midYear_monthsBeforeSeptemberBelongToTheYearThatStartedLastAutumn() {
    assertEquals(SchoolYearRef(2025, 2026), SchoolYearRef.current(nowYear = 2026, nowMonth = 1))
    assertEquals(SchoolYearRef(2025, 2026), SchoolYearRef.current(nowYear = 2026, nowMonth = 6))
  }

  @Test
  fun december_staysInTheYearThatStartedInSeptember() {
    assertEquals(SchoolYearRef(2026, 2027), SchoolYearRef.current(nowYear = 2026, nowMonth = 12))
  }

  @Test
  fun previousOf_stepsBackOneYear() {
    assertEquals(SchoolYearRef(2025, 2026), SchoolYearRef.previousOf(SchoolYearRef(2026, 2027)))
  }

  @Test
  fun labelAndId_areStableAcrossTheBoundary() {
    val year = SchoolYearRef.current(nowYear = 2026, nowMonth = 9)

    assertEquals("2026-2027", year.id)
    assertEquals("2026/27", year.label)
  }
}
