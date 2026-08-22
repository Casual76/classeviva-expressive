package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.AbsencesSection
import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.DocumentsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.HomeworkSection
import dev.antigravity.classevivaexpressive.core.data.repository.LessonsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MaterialsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MeetingBookingsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MeetingSlotsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MeetingTeachersSection
import dev.antigravity.classevivaexpressive.core.data.repository.NotesSection
import dev.antigravity.classevivaexpressive.core.data.repository.PeriodsSection
import dev.antigravity.classevivaexpressive.core.data.repository.ProfileSection
import dev.antigravity.classevivaexpressive.core.data.repository.SchoolbooksSection
import dev.antigravity.classevivaexpressive.core.data.repository.SubjectsSection
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus

internal object SyncStatusFactory {
  fun syncing(previous: SyncStatus): SyncStatus {
    return SyncStatus(
      state = SyncState.SYNCING,
      lastSuccessfulSyncEpochMillis = previous.lastSuccessfulSyncEpochMillis,
      message = "Sincronizzazione in corso",
    )
  }

  /**
   * @param failures each section that failed, mapped to the reason it gave. The network layer
   *   already writes a specific sentence for every failure it can name; listing only the sections
   *   threw that away and left the report as a list of things that did not work with no way to tell
   *   an expired session from a dropped connection — or to know which of the two to go and fix.
   * @param unavailableSections sections the registro simply does not publish for the selected year.
   *   Not failures — nothing went wrong and retrying cannot help — but the difference between "no
   *   data" and "no data *available*" is the whole reason a screen can look broken when it is not.
   */
  fun completed(
    failures: Map<String, String>,
    previous: SyncStatus,
    completedAtEpochMillis: Long,
    unavailableSections: List<String> = emptyList(),
  ): SyncStatus {
    val unavailableNote = unavailableSections.distinct()
      .takeIf { it.isNotEmpty() }
      ?.let { sections ->
        "Il registro non pubblica ${sections.joinToString { it.syncSectionLabel() }} " +
          "per l'anno scolastico selezionato."
      }
    if (failures.isEmpty()) {
      return SyncStatus(
        state = SyncState.IDLE,
        lastSuccessfulSyncEpochMillis = completedAtEpochMillis,
        message = unavailableNote,
      )
    }
    // Grouped by reason rather than listed by section: when one thing goes wrong it usually takes
    // several sections down with it, and five copies of the same sentence is not five facts.
    val failureNote = failures.entries
      .groupBy({ it.value }, { it.key })
      .entries
      .joinToString(" ") { (reason, sections) -> failureSentence(reason, sections) }
    return SyncStatus(
      state = SyncState.PARTIAL,
      lastSuccessfulSyncEpochMillis = previous.lastSuccessfulSyncEpochMillis ?: completedAtEpochMillis,
      message = listOfNotNull(failureNote, unavailableNote).joinToString(" "),
      failedSections = failures.keys.toList(),
    )
  }
}

private fun failureSentence(reason: String, sections: List<String>): String {
  val listed = sections.joinToString { it.syncSectionLabel() }
  val trimmed = reason.trim().trimEnd('.')
  return if (trimmed.isEmpty() || trimmed == GenericSyncFailure.trimEnd('.')) {
    "Aggiornamento non riuscito: $listed."
  } else {
    "$trimmed: $listed."
  }
}

private fun String.syncSectionLabel(): String = when (this) {
  ProfileSection -> "profilo"
  GradesSection -> "voti"
  PeriodsSection -> "periodi"
  SubjectsSection -> "materie"
  LessonsSection -> "lezioni"
  HomeworkSection -> "compiti"
  AgendaSection -> "agenda"
  AbsencesSection -> "assenze"
  CommunicationsSection -> "bacheca"
  NotesSection -> "note"
  MaterialsSection -> "materiali"
  DocumentsSection -> "documenti"
  SchoolbooksSection -> "libri"
  MeetingTeachersSection,
  MeetingSlotsSection,
  MeetingBookingsSection -> "colloqui"
  else -> this
}
