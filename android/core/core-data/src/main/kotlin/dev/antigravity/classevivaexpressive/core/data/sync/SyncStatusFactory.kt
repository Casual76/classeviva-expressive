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

  fun completed(
    errors: List<String>,
    previous: SyncStatus,
    completedAtEpochMillis: Long,
  ): SyncStatus {
    val failedSections = errors.distinct()
    return if (failedSections.isEmpty()) {
      SyncStatus(
        state = SyncState.IDLE,
        lastSuccessfulSyncEpochMillis = completedAtEpochMillis,
        message = null,
      )
    } else {
      SyncStatus(
        state = SyncState.PARTIAL,
        lastSuccessfulSyncEpochMillis = previous.lastSuccessfulSyncEpochMillis ?: completedAtEpochMillis,
        message = "Aggiornamento incompleto: ${failedSections.joinToString { it.syncSectionLabel() }}",
        failedSections = failedSections,
      )
    }
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
