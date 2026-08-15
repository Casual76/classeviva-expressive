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
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal const val SyncStatusSection = "sync_status:last_successful"

internal fun syncStatusCacheKey(studentId: String): String = "$studentId::$SyncStatusSection"

internal suspend fun SnapshotCacheDao.recordSuccessfulSync(studentId: String, completedAtEpochMillis: Long) {
  val key = syncStatusCacheKey(studentId)
  upsert(
    SnapshotCacheEntity(
      cacheKey = key,
      payload = completedAtEpochMillis.toString(),
      updatedAtEpochMillis = completedAtEpochMillis,
    ),
  )
}

internal fun observePersistedSyncStatus(
  snapshotCacheDao: SnapshotCacheDao,
  studentId: String,
  schoolYear: SchoolYearRef,
): Flow<SyncStatus> {
  val syncKey = syncStatusCacheKey(studentId)
  val flows = syncStatusCacheKeys(studentId, schoolYear).map(snapshotCacheDao::observeByKey)
  return combine(flows) { entries ->
    SyncStatus(
      lastSuccessfulSyncEpochMillis = entries
        .filterNotNull()
        .map { entry ->
          if (entry.cacheKey == syncKey) {
            entry.payload.toLongOrNull() ?: entry.updatedAtEpochMillis
          } else {
            entry.updatedAtEpochMillis
          }
        }
        .maxOrNull(),
    )
  }
}

internal fun SyncStatus.withPersistedLastSuccess(persisted: SyncStatus): SyncStatus {
  val persistedLast = persisted.lastSuccessfulSyncEpochMillis ?: return this
  val currentLast = lastSuccessfulSyncEpochMillis
  return if (currentLast == null || persistedLast > currentLast) {
    copy(lastSuccessfulSyncEpochMillis = persistedLast)
  } else {
    this
  }
}

private fun syncStatusCacheKeys(studentId: String, schoolYear: SchoolYearRef): List<String> {
  return listOf(syncStatusCacheKey(studentId), "$studentId::$ProfileSection") +
    syncStatusYearScopedSections.map { section -> yearScopedCacheKey(studentId, section, schoolYear) }
}

private val syncStatusYearScopedSections = listOf(
  GradesSection,
  PeriodsSection,
  SubjectsSection,
  LessonsSection,
  HomeworkSection,
  AgendaSection,
  AbsencesSection,
  CommunicationsSection,
  NotesSection,
  MaterialsSection,
  DocumentsSection,
  SchoolbooksSection,
  MeetingTeachersSection,
  MeetingSlotsSection,
  MeetingBookingsSection,
)
