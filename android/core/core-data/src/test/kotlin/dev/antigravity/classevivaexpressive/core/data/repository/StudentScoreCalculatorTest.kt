package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.GradeDistribution
import dev.antigravity.classevivaexpressive.core.domain.model.StatsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentScoreCalculatorTest {
  @Test
  fun computeStudentScore_rewardsStrongPerformance() {
    val snapshot = computeStudentScore(
      stats = StatsSnapshot(
        overallAverage = 8.2,
        gradeDistribution = GradeDistribution(
          good = 2,
          veryGood = 3,
          excellent = 2,
        ),
      ),
      absences = listOf(
        AbsenceRecord(id = "1", date = "2026-03-01", type = AbsenceType.ABSENCE, justified = true),
        AbsenceRecord(id = "2", date = "2026-03-15", type = AbsenceType.LATE, justified = true),
      ),
    )

    assertEquals("Elite", snapshot.label)
    assertTrue(snapshot.score >= 86.0)
    assertEquals(3, snapshot.components.size)
    assertTrue(snapshot.sharePayload.isNotBlank())
  }

  @Test
  fun computeStudentScore_penalizesHeavyAbsences() {
    val snapshot = computeStudentScore(
      stats = StatsSnapshot(
        overallAverage = 6.4,
        gradeDistribution = GradeDistribution(sufficient = 4),
      ),
      absences = (1..18).map { index ->
        AbsenceRecord(
          id = index.toString(),
          date = "2026-02-${index.toString().padStart(2, '0')}",
          type = AbsenceType.ABSENCE,
          justified = false,
        )
      },
    )

    assertEquals("Da rilanciare", snapshot.label)
    assertTrue(snapshot.score < 55.0)
  }
}
