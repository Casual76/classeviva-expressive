package dev.antigravity.classevivaexpressive.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeWeightsTest {
  private fun grade(value: Double?, weight: Double? = null, subject: String = "Matematica") = Grade(
    id = "$subject:$value:$weight", subject = subject, valueLabel = value?.toString() ?: "NC",
    numericValue = value, date = "2026-11-17", type = "Scritto", weight = weight,
  )

  @Test
  fun factorsAndPercentagesShareTheExistingAssistantWeightPolicy() {
    assertEquals(1.0, grade(8.0).effectiveWeight(), 0.0)
    assertEquals(1.0, grade(8.0, 100.0).effectiveWeight(), 0.0)
    assertEquals(0.5, grade(8.0, 50.0).effectiveWeight(), 0.0)
    assertEquals(0.5, grade(8.0, 0.5).effectiveWeight(), 0.0)
    assertEquals(2.0, grade(8.0, 2.0).effectiveWeight(), 0.0)
  }

  @Test
  fun zeroNegativeAndNonFiniteWeightsDoNotContribute() {
    listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { weight ->
      assertEquals(0.0, grade(4.0, weight).effectiveWeight(), 0.0)
      assertNull(subjectGradeAverage(listOf(grade(4.0, weight))))
    }
    assertEquals(8.0, subjectGradeAverage(listOf(grade(4.0, 0.0), grade(8.0, 1.0)))!!, 0.0)
  }

  @Test
  fun invalidNumericValuesAndExcludedOnlySubjectsDoNotPolluteOverallAverage() {
    val grades = listOf(
      grade(Double.NaN), grade(Double.POSITIVE_INFINITY), grade(null),
      grade(4.0, 0.0, "Storia"), grade(8.0, 1.0, "Italiano"),
    )
    assertEquals(8.0, overallGradeAverage(grades)!!, 0.0)
    assertNull(overallGradeAverage(emptyList()))
  }

  @Test
  fun overallAverageGivesEachSubjectOneContributionAfterWeightingItsMarks() {
    val grades = listOf(grade(2.0, 2.0), grade(8.0, 1.0), grade(10.0, 1.0, "Storia"))
    assertEquals(4.0, subjectGradeAverage(grades.take(2))!!, 0.0)
    assertEquals(7.0, overallGradeAverage(grades)!!, 0.0)
  }
}
