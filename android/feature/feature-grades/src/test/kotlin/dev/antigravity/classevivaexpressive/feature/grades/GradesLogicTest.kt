package dev.antigravity.classevivaexpressive.feature.grades

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import org.junit.Assert.*
import org.junit.Test

import java.time.LocalDate

class GradesLogicTest {
  @Test
  fun selectCurrentPeriodCode_prefersCurrentDateWhenNothingIsSelected() {
    val periods = listOf(
      Period("T1", 1, "Trimestre", "Trimestre", false, "2025-09-01", "2026-01-31"),
      Period("P2", 2, "Pentamestre", "Pentamestre", true, "2026-02-01", "2026-06-10"),
    )

    val selected = selectCurrentPeriodCode(periods, selectedCode = null, today = LocalDate.of(2026, 3, 29))

    assertEquals("P2", selected)
  }

  @Test
  fun filterGradesByPeriod_usesCodeAndDateFallback() {
    val periods = listOf(
      Period("P2", 2, "Pentamestre", "Pentamestre", true, "2026-02-01", "2026-06-10"),
    )
    val grades = listOf(
      Grade(id = "1", subject = "Matematica", valueLabel = "7", numericValue = 7.0, date = "2026-02-10", type = "Scritto", periodCode = "P2"),
      Grade(id = "2", subject = "Fisica", valueLabel = "8", numericValue = 8.0, date = "2026-03-12", type = "Orale", periodCode = null),
      Grade(id = "3", subject = "Storia", valueLabel = "6", numericValue = 6.0, date = "2025-11-12", type = "Orale", periodCode = "T1"),
    )

    val filtered = filterGradesByPeriod(grades, periods, "P2")

    assertEquals(listOf("1", "2"), filtered.map { it.id })
  }

  @Test
  fun calculateRequiredGradeMessage_handlesWeightedAndUnreachableTargets() {
    val weightedGrades = listOf(
      Grade(id = "1", subject = "Matematica", valueLabel = "6", numericValue = 6.0, date = "2026-02-10", type = "Scritto", weight = 2.0),
      Grade(id = "2", subject = "Matematica", valueLabel = "7", numericValue = 7.0, date = "2026-03-10", type = "Orale", weight = 1.0),
    )
    val impossibleGrades = listOf(
      Grade(id = "3", subject = "Fisica", valueLabel = "4", numericValue = 4.0, date = "2026-02-10", type = "Scritto", weight = 1.0),
    )

    assertEquals("Serve almeno 9,0", calculateRequiredGradeMessage(weightedGrades, 7.0))
    assertEquals("Lontano dal target", calculateRequiredGradeMessage(impossibleGrades, 9.0))
  }

  @Test
  fun calculateRequiredGradeMessage_warnsWhenAverageIsAlreadyReached() {
    val grades = listOf(
      Grade(id = "1", subject = "Italiano", valueLabel = "8", numericValue = 8.0, date = "2026-02-10", type = "Scritto", weight = 1.0),
      Grade(id = "2", subject = "Italiano", valueLabel = "9", numericValue = 9.0, date = "2026-03-10", type = "Orale", weight = 1.0),
    )

    assertEquals("Soglia sicura: 5,5", calculateRequiredGradeMessage(grades, 7.5))
  }

  @Test
  fun calculateRequiredGradeMessage_relaxesWhenTargetIsAlreadySafeEvenWithZero() {
    val grades = listOf(
      Grade(id = "1", subject = "Storia", valueLabel = "9", numericValue = 9.0, date = "2026-02-10", type = "Scritto", weight = 1.0),
      Grade(id = "2", subject = "Storia", valueLabel = "9", numericValue = 9.0, date = "2026-03-10", type = "Orale", weight = 1.0),
    )

    assertEquals("Target sicuro", calculateRequiredGradeMessage(grades, 6.0))
  }

  @Test
  fun calculateRequiredGradeMessage_withEmptyGradeListReturnsTargetMessage() {
    assertEquals("Serve almeno 7,0", calculateRequiredGradeMessage(emptyList(), 7.0))
  }

  @Test
  fun selectCurrentPeriodCode_returnsNullForEmptyPeriodList() {
    assertNull(selectCurrentPeriodCode(emptyList(), selectedCode = null, today = LocalDate.of(2026, 3, 15)))
  }

  @Test
  fun selectCurrentPeriodCode_returnsFallbackLastPeriodWhenDateOutOfAllRanges() {
    val periods = listOf(
      Period("T1", 1, "Trimestre", "Trimestre", false, "2025-09-01", "2026-01-31"),
      Period("P2", 2, "Pentamestre", "Pentamestre", true, "2026-02-01", "2026-06-10"),
    )

    // Date after all periods end
    val selected = selectCurrentPeriodCode(periods, selectedCode = null, today = LocalDate.of(2027, 1, 1))

    assertEquals("P2", selected)
  }

  @Test
  fun filterGradesByPeriod_excludesGradeWithWrongCodeAndOutOfDateRange() {
    val periods = listOf(
      Period("P2", 2, "Pentamestre", "Pentamestre", true, "2026-02-01", "2026-06-10"),
    )
    val grades = listOf(
      Grade(id = "1", subject = "Matematica", valueLabel = "7", numericValue = 7.0, date = "2025-11-10", type = "Scritto", periodCode = "T1"),
    )

    val filtered = filterGradesByPeriod(grades, periods, "P2")

    assertTrue(filtered.isEmpty())
  }

  // ─── calculateSubjectAverage ──────────────────────────────────────────────

  @Test
  fun calculateSubjectAverage_computesWeightedAverage() {
    val grades = listOf(
      Grade(id = "1", subject = "Matematica", valueLabel = "6", numericValue = 6.0, date = "2026-02-10", type = "Scritto", weight = 2.0),
      Grade(id = "2", subject = "Matematica", valueLabel = "9", numericValue = 9.0, date = "2026-03-10", type = "Orale", weight = 1.0),
    )

    // (6*2 + 9*1) / (2+1) = 21/3 = 7.0
    assertEquals(7.0, calculateSubjectAverage(grades)!!, 0.001)
  }

  @Test
  fun calculateSubjectAverage_usesDefaultWeightOneWhenWeightNull() {
    val grades = listOf(
      Grade(id = "1", subject = "Italiano", valueLabel = "8", numericValue = 8.0, date = "2026-02-10", type = "Scritto"),
      Grade(id = "2", subject = "Italiano", valueLabel = "6", numericValue = 6.0, date = "2026-03-10", type = "Orale"),
    )

    assertEquals(7.0, calculateSubjectAverage(grades)!!, 0.001)
  }

  @Test
  fun calculateSubjectAverage_returnsNullForEmptyList() {
    assertNull(calculateSubjectAverage(emptyList()))
  }

  @Test
  fun calculateSubjectAverage_ignoresGradesWithNullNumericValue() {
    val grades = listOf(
      Grade(id = "1", subject = "Fisica", valueLabel = "NC", numericValue = null, date = "2026-02-10", type = "Orale"),
      Grade(id = "2", subject = "Fisica", valueLabel = "8", numericValue = 8.0, date = "2026-03-10", type = "Scritto"),
    )

    assertEquals(8.0, calculateSubjectAverage(grades)!!, 0.001)
  }

  // ─── calculateOverallAverage ──────────────────────────────────────────────

  @Test
  fun calculateOverallAverage_averagesSubjectAverages() {
    // Matematica avg = 8.0, Italiano avg = 6.0 → overall = 7.0
    val grades = listOf(
      Grade(id = "1", subject = "Matematica", valueLabel = "8", numericValue = 8.0, date = "2026-02-10", type = "Scritto"),
      Grade(id = "2", subject = "Italiano", valueLabel = "6", numericValue = 6.0, date = "2026-02-11", type = "Scritto"),
    )

    assertEquals(7.0, calculateOverallAverage(grades)!!, 0.001)
  }

  @Test
  fun calculateOverallAverage_returnsNullForEmptyList() {
    assertNull(calculateOverallAverage(emptyList()))
  }
}
