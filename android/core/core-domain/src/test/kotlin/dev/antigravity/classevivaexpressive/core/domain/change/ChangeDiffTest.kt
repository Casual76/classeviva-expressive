package dev.antigravity.classevivaexpressive.core.domain.change

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeDiffTest {
  @Test
  fun versionChainKeepsAVersionThatWasLaterRestored() {
    // A -> B -> A: oggi e' di nuovo A. La A di prima va tenuta, perche' e' diversa da B.
    val a = version(title = "Verifica di algebra", recordedAt = 1L)
    val b = version(title = "Verifica di geometria", recordedAt = 2L)
    val current = version(title = "Verifica di algebra", recordedAt = 0L)

    val kept = meaningfulVersionChain(current, listOf(b, a)) { older, newer -> older.fieldChangesTo(newer).isNotEmpty() }

    assertEquals(listOf(b, a), kept)
  }

  @Test
  fun versionChainDropsAVersionIdenticalToTheOneAfterIt() {
    val duplicate = version(title = "Verifica", recordedAt = 3L)
    val older = version(title = "Interrogazione", recordedAt = 1L)
    val current = version(title = "Verifica", recordedAt = 0L)

    val kept = meaningfulVersionChain(current, listOf(duplicate, older)) { o, n -> o.fieldChangesTo(n).isNotEmpty() }

    assertEquals(listOf(older), kept)
  }

  @Test
  fun oneSidedTextCountsOnlyWhenAllowed() {
    val before = version(detail = null)
    val after = version(detail = "Portare il compasso")

    assertTrue(before.fieldChangesTo(after, includeOneSidedText = false).isEmpty())
    assertEquals(listOf(ChangeField.DETAIL), before.fieldChangesTo(after, includeOneSidedText = true).map { it.field })
  }

  @Test
  fun aMovedAssessmentReportsTheDateWithItsValues() {
    val change = version(date = "2026-09-29").fieldChangesTo(version(date = "2026-09-30")).single()

    assertEquals(FieldChange(ChangeField.DATE, "2026-09-29", "2026-09-30"), change)
  }

  @Test
  fun timelinePairsEachVersionWithTheOneThatFollowedNewestFirst() {
    val item = AgendaItem(
      id = "a1",
      title = "Pag 1371 es 282, 283, 311",
      subtitle = "MATEMATICA",
      date = "2026-09-30",
      category = AgendaCategory.HOMEWORK,
      history = listOf(
        version(title = "Pag 1371 es 282, 283", date = "2026-09-30", recordedAt = 20L),
        version(title = "Pag 1371 es 282, 283", date = "2026-09-29", recordedAt = 10L),
      ),
    )

    val timeline = item.changeTimeline()

    assertEquals(listOf(20L, 10L), timeline.map { it.detectedAtEpochMillis })
    assertEquals(listOf(ChangeField.TITLE), timeline[0].changes.map { it.field })
    assertEquals(listOf(ChangeField.DATE), timeline[1].changes.map { it.field })
    assertEquals(
      listOf(DiffSegment(DiffKind.SAME, "Pag 1371 es 282, 283"), DiffSegment(DiffKind.ADDED, ", 311")),
      item.latestTitleDiff(),
    )
  }

  @Test
  fun latestTitleDiffIsNullWhenOnlyTheDateMoved() {
    val item = AgendaItem(
      id = "a1",
      title = "Verifica",
      subtitle = "",
      date = "2026-09-30",
      category = AgendaCategory.ASSESSMENT,
      history = listOf(version(title = "Verifica", date = "2026-09-29", recordedAt = 5L)),
    )

    assertNull(item.latestTitleDiff())
  }

  @Test
  fun gradeTimelineReportsTheValue() {
    val grade = Grade(
      id = "g1",
      subject = "Matematica",
      valueLabel = "7",
      numericValue = 7.0,
      date = "2026-09-20",
      type = "Scritto",
      history = listOf(
        GradeVersion(recordedAtEpochMillis = 5L, subject = "Matematica", valueLabel = "6½", numericValue = 6.5, date = "2026-09-20", type = "Scritto"),
      ),
    )

    val change = grade.changeTimeline().single().changes.single()

    assertEquals(FieldChange(ChangeField.VALUE, "6½", "7"), change)
  }

  private fun version(
    title: String = "Compito",
    date: String = "2026-09-30",
    detail: String? = null,
    recordedAt: Long = 0L,
  ) = AgendaItemVersion(
    recordedAtEpochMillis = recordedAt,
    title = title,
    subtitle = "MATEMATICA",
    date = date,
    detail = detail,
    category = AgendaCategory.HOMEWORK,
  )
}
