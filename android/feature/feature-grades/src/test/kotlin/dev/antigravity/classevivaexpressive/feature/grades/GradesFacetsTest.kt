package dev.antigravity.classevivaexpressive.feature.grades

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GradesFacetsTest {
  private fun grade(id: String, subject: String, value: Double, weight: Double = 1.0, period: String = "P2") = Grade(
    id = id, subject = subject, valueLabel = value.toString(), numericValue = value,
    date = "2026-03-10", type = "Scritto", weight = weight, periodCode = period,
  )

  @Test
  fun collapsedBarUsesTheSameWeightedSubjectAverageAsTheHero() {
    val grades = listOf(
      grade("m1", "Matematica", 2.0, weight = 2.0),
      grade("m2", "Matematica", 8.0),
      grade("s1", "Storia", 10.0),
    )

    // Matematica: (2*2+8)/3 = 4; Storia: 10. Media delle materie: 7.
    // La vecchia barra mostrava (2+8+10)/3 = 6,67.
    assertEquals(7.0, calculateOverallAverage(grades)!!, 0.0001)
    assertEquals("Media 7,00", buildGradesFacets(grades, emptySet()).first { it.startsWith("Media ") })
  }

  @Test
  fun factsForTheVisiblePeriodExcludeOtherPeriodsInAverageAndNewGrades() {
    val grades = listOf(
      grade("old", "Matematica", 2.0, period = "T1"),
      grade("new", "Matematica", 8.0, period = "P2"),
    )
    val periods = listOf(
      Period("T1", 1, "Trimestre", "Trimestre", false, "2025-09-01", "2026-01-31"),
      Period("P2", 2, "Pentamestre", "Pentamestre", true, "2026-02-01", "2026-06-10"),
    )
    val visible = filterGradesByPeriod(grades, periods, "P2")
    val facets = buildGradesFacets(visible, emptySet())

    assertEquals(listOf("1 voto nuovo", "Media 8,00"), facets)
    assertFalse(facets.any { it.contains("insufficienz") })
  }

  @Test
  fun noNumericGradesDoNotInventAnAverage() {
    val grades = listOf(grade("nc", "Matematica", 8.0).copy(numericValue = null, valueLabel = "NC"))
    assertEquals(listOf("1 voto nuovo"), buildGradesFacets(grades, emptySet()))
  }
}
