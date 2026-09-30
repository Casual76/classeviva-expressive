package dev.antigravity.classevivaexpressive.core.designsystem.theme

import dev.antigravity.classevivaexpressive.core.domain.change.ChangeEntry
import dev.antigravity.classevivaexpressive.core.domain.change.ChangeField
import dev.antigravity.classevivaexpressive.core.domain.change.FieldChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeTimelineTextTest {
  private val paragraph =
    "ORIENTAMENTO: STARTING FROM WHAT YOU WROTE IN CLASS LAST WEEK, SERF THE NET TO FIND OTHER " +
      "POSSIBLE THINGS YOU MIGHT WANT TO ADD TO YOUR ESSAY TAKE A PICTURE AND UPLOAD ON CLASSROOM"

  @Test
  fun unchangedTextBeforeAnAdditionKeepsOnlyItsEnd() {
    val trimmed = paragraph.trimmedAround(keepStart = false, keepEnd = true, contextChars = 48)

    assertTrue(trimmed.startsWith("… "))
    assertTrue(trimmed.endsWith("UPLOAD ON CLASSROOM"))
    assertTrue(trimmed.length < 60)
  }

  @Test
  fun unchangedTextBetweenTwoChangesKeepsBothEnds() {
    val trimmed = paragraph.trimmedAround(keepStart = true, keepEnd = true, contextChars = 20)

    assertTrue(trimmed.startsWith("ORIENTAMENTO:"))
    assertTrue(trimmed.contains(" … "))
    assertTrue(trimmed.endsWith("CLASSROOM"))
  }

  @Test
  fun shortTextIsLeftAlone() {
    assertEquals("Pag 1371 es 282, 283", "Pag 1371 es 282, 283".trimmedAround(true, true, 48))
  }

  @Test
  fun summaryNamesTheChangedFieldsInItalian() {
    val entry = ChangeEntry(
      detectedAtEpochMillis = 0L,
      changes = listOf(
        FieldChange(ChangeField.DATE, "2026-09-29", "2026-09-30"),
        FieldChange(ChangeField.TIME, "09:00", "10:00"),
        FieldChange(ChangeField.TITLE, "a", "b"),
      ),
    )

    assertEquals("data, orario e testo", changeSummary(entry))
  }

  @Test
  fun aMoveAloneSaysWhereItWasBefore() {
    val entry = ChangeEntry(0L, listOf(FieldChange(ChangeField.DATE, "2026-10-08", "2026-10-09")))

    assertTrue(changeMetaLabel(entry) { "" }.startsWith("Spostato dall'8 ott"))
  }
}
