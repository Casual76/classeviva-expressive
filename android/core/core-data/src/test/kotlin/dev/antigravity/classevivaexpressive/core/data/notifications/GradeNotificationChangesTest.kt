package dev.antigravity.classevivaexpressive.core.data.notifications

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeNotificationChangesTest {
  private fun grade(id: String, value: Double = 7.0) = Grade(
    id = id, subject = "Matematica", valueLabel = value.toString(), numericValue = value,
    date = "2026-11-17", type = "Scritto",
  )

  @Test fun newGradeDoesNotHideSimultaneousCorrectionToKnownGrade() {
    val changes = gradeNotificationChanges(listOf(grade("known")), listOf(grade("known", 8.0), grade("new", 9.0)))
    assertEquals(listOf("new"), changes.newItems.map(Grade::id))
    assertEquals(listOf("known"), changes.updatedItems.map(Grade::id))
  }

  @Test fun cachedMetadataEnrichmentDoesNotAnnounceAnUnchangedGrade() {
    val before = grade("known")
    val changes = gradeNotificationChanges(listOf(before), listOf(before.copy(teacher = "Docente", period = "Primo periodo")))
    assertTrue(changes.newItems.isEmpty())
    assertTrue(changes.updatedItems.isEmpty())
  }

  @Test fun descriptionCorrectionStillNotifiesEvenWhenNumericValueIsUnchanged() {
    val before = grade("known").copy(description = "Compito")
    val changes = gradeNotificationChanges(listOf(before), listOf(before.copy(description = "Compito corretto")))
    assertEquals(listOf("known"), changes.updatedItems.map(Grade::id))
  }

  @Test fun unchangedRecordsAndFormattingChangesAreNotReannounced() {
    val before = grade("known")
    val changes = gradeNotificationChanges(listOf(before), listOf(before.copy(valueLabel = "7", subject = " MATEMATICA ")))
    assertTrue(changes.newItems.isEmpty())
    assertTrue(changes.updatedItems.isEmpty())
  }
}
