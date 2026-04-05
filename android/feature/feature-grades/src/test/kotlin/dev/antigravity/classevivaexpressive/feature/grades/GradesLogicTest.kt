package dev.antigravity.classevivaexpressive.feature.grades

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import org.junit.Assert.assertEquals
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

    assertEquals("devi prendere almeno 9,0", calculateRequiredGradeMessage(weightedGrades, 7.0))
    assertEquals("Media non raggiungibile con un singolo voto", calculateRequiredGradeMessage(impossibleGrades, 9.0))
  }

  @Test
  fun calculateRequiredGradeMessage_warnsWhenAverageIsAlreadyReached() {
    val grades = listOf(
      Grade(id = "1", subject = "Italiano", valueLabel = "8", numericValue = 8.0, date = "2026-02-10", type = "Scritto", weight = 1.0),
      Grade(id = "2", subject = "Italiano", valueLabel = "9", numericValue = 9.0, date = "2026-03-10", type = "Orale", weight = 1.0),
    )

    assertEquals("non prendere meno di 5,5", calculateRequiredGradeMessage(grades, 7.5))
  }

  @Test
  fun calculateRequiredGradeMessage_relaxesWhenTargetIsAlreadySafeEvenWithZero() {
    val grades = listOf(
      Grade(id = "1", subject = "Storia", valueLabel = "9", numericValue = 9.0, date = "2026-02-10", type = "Scritto", weight = 1.0),
      Grade(id = "2", subject = "Storia", valueLabel = "9", numericValue = 9.0, date = "2026-03-10", type = "Orale", weight = 1.0),
    )

    assertEquals("non ti preoccupare!", calculateRequiredGradeMessage(grades, 6.0))
  }
}
