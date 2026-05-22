package dev.antigravity.classevivaexpressive.core.data.change

import dev.antigravity.classevivaexpressive.core.database.database.AgendaItemEntity
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.GradeVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeComparisonTest {
  @Test
  fun gradeComparison_ignoresParserBaselineAndFormattingChanges() {
    val current = gradeEntity(
      type = "Orale",
      period = "Primo trimestre",
      periodCode = "P1",
      teacher = "Prof Rossi",
      color = "#FF0000",
    )
    val previous = GradeVersion(
      recordedAtEpochMillis = 1_000L,
      subject = " matematica ",
      valueLabel = "7.00",
      numericValue = 7.0,
      description = "Interrogazione",
      date = "2026-03-20",
      type = "Valutazione",
      weight = null,
      notes = null,
      period = null,
      periodCode = null,
      teacher = null,
      color = null,
    )

    assertFalse(current.hasMeaningfulChangeComparedTo(previous))
  }

  @Test
  fun gradeComparison_detectsRealValueChange() {
    val current = gradeEntity(valueLabel = "8", numericValue = 8.0)
    val previous = GradeVersion(
      recordedAtEpochMillis = 1_000L,
      subject = "Matematica",
      valueLabel = "7",
      numericValue = 7.0,
      description = "Interrogazione",
      date = "2026-03-20",
      type = "Orale",
    )

    assertTrue(current.hasMeaningfulChangeComparedTo(previous))
  }

  @Test
  fun gradeComparison_detectsOneSidedTextOnlyAfterBaseline() {
    val current = gradeEntity()
    val previous = GradeVersion(
      recordedAtEpochMillis = 1_000L,
      subject = "Matematica",
      valueLabel = "7",
      numericValue = 7.0,
      description = null,
      date = "2026-03-20",
      type = "Orale",
    )

    assertFalse(current.hasMeaningfulChangeComparedTo(previous))
    assertTrue(current.hasMeaningfulChangeComparedTo(previous, includeOneSidedText = true))
  }

  @Test
  fun gradeComparison_ignoresPeriodOnlyChanges() {
    val current = gradeEntity(period = "Pentamestre", periodCode = "P2")
    val previous = GradeVersion(
      recordedAtEpochMillis = 1_000L,
      subject = "Matematica",
      valueLabel = "7",
      numericValue = 7.0,
      description = "Interrogazione",
      date = "2026-03-20",
      type = "Orale",
      period = "Trimestre",
      periodCode = "P1",
    )

    assertFalse(current.hasMeaningfulChangeComparedTo(previous, includeOneSidedText = true))
  }

  @Test
  fun agendaComparison_detectsTeacherTextChange() {
    val current = agendaEntity(
      title = "Verifica capitoli 1-2",
      detail = "Ripassare capitoli 1 e 2",
      subject = null,
      subtitle = "",
      time = "10:00",
    )
    val previous = AgendaItemVersion(
      recordedAtEpochMillis = 1_000L,
      title = "Verifica capitolo 1",
      subtitle = "",
      date = "2026-04-10",
      time = "10:00",
      detail = "Ripassare capitolo 1",
      subject = null,
      teacher = "Prof Rossi",
      category = AgendaCategory.ASSESSMENT,
      sharePayload = "old payload",
      createdAt = "2026-04-01T10:00",
    )

    assertTrue(current.hasMeaningfulChangeComparedTo(previous))
  }

  @Test
  fun agendaComparison_detectsOneSidedTextOnlyAfterBaseline() {
    val current = agendaEntity(detail = "Ripassare capitolo 1")
    val previous = AgendaItemVersion(
      recordedAtEpochMillis = 1_000L,
      title = "Verifica capitolo 1",
      subtitle = "Storia",
      date = "2026-04-10",
      time = "10:00",
      detail = null,
      subject = "Storia",
      category = AgendaCategory.ASSESSMENT,
    )

    assertFalse(current.hasMeaningfulChangeComparedTo(previous))
    assertTrue(current.hasMeaningfulChangeComparedTo(previous, includeOneSidedText = true))
  }

  @Test
  fun agendaComparison_ignoresMetadataOnlyChanges() {
    val current = agendaEntity(
      teacher = "Prof Rossi",
      sharePayload = "new payload",
      createdAt = "2026-04-02T10:00",
    )
    val previous = AgendaItemVersion(
      recordedAtEpochMillis = 1_000L,
      title = "Verifica capitolo 1",
      subtitle = "Storia",
      date = "2026-04-10",
      time = "10:00",
      detail = "Ripassare capitolo 1",
      subject = "Storia",
      teacher = null,
      category = AgendaCategory.ASSESSMENT,
      sharePayload = "old payload",
      createdAt = "2026-04-01T10:00",
    )

    assertFalse(current.hasMeaningfulChangeComparedTo(previous))
  }

  private fun gradeEntity(
    valueLabel: String = "7",
    numericValue: Double? = 7.0,
    type: String = "Orale",
    period: String? = null,
    periodCode: String? = null,
    teacher: String? = null,
    color: String? = null,
  ) = GradeEntity(
    id = "g1",
    studentId = "55",
    schoolYearId = "2025-2026",
    subject = "Matematica",
    valueLabel = valueLabel,
    numericValue = numericValue,
    description = "Interrogazione",
    date = "2026-03-20",
    type = type,
    weight = null,
    notes = null,
    period = period,
    periodCode = periodCode,
    teacher = teacher,
    color = color,
  )

  private fun agendaEntity(
    title: String = "Verifica capitolo 1",
    subtitle: String = "Storia",
    date: String = "2026-04-10",
    time: String? = "10:00",
    detail: String? = "Ripassare capitolo 1",
    subject: String? = "Storia",
    teacher: String? = null,
    sharePayload: String? = null,
    createdAt: String? = null,
  ) = AgendaItemEntity(
    id = "a1",
    studentId = "55",
    schoolYearId = "2025-2026",
    title = title,
    subtitle = subtitle,
    date = date,
    time = time,
    detail = detail,
    subject = subject,
    teacher = teacher,
    category = AgendaCategory.ASSESSMENT.name,
    sharePayload = sharePayload,
    createdAt = createdAt,
    firstSeenAtMs = 1_000L,
  )
}
