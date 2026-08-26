package dev.antigravity.classevivaexpressive.feature.settings

import dev.antigravity.classevivaexpressive.core.domain.model.AppBackupImportSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSummaryTest {

  @Test
  fun describe_namesTheSchoolYearsOfTheImportedGrades() {
    val text = AppBackupImportSummary(
      settingsImported = true,
      grades = 487,
      gradeSchoolYears = listOf("2024-2025", "2025-2026"),
    ).describe()

    assertTrue(text, text.contains("487 voti (2024-2025, 2025-2026)"))
  }

  @Test
  fun describe_omitsEverythingThatIsZero() {
    val text = AppBackupImportSummary(settingsImported = true, grades = 3).describe()

    assertFalse("uno zero in un elenco di successi si legge come un errore", text.contains("0 "))
    assertTrue(text, text.contains("3 voti"))
  }

  @Test
  fun describe_usesTheSingularWhenThereIsOne() {
    val text = AppBackupImportSummary(settingsImported = true, grades = 1).describe()
    assertTrue(text, text.contains("1 voto"))
  }

  @Test
  fun describe_saysWhenGradesBelongToAnotherProfile() {
    val text = AppBackupImportSummary(
      settingsImported = true,
      grades = 10,
      skippedForeignStudentGrades = 4,
    ).describe()

    assertTrue(text, text.contains("4 voti appartengono a un altro profilo"))
  }

  @Test
  fun describe_saysSoWhenThereWasNothingToRestore() {
    assertEquals(
      "Backup importato, ma non conteneva dati da ripristinare.",
      AppBackupImportSummary().describe(),
    )
  }
}
