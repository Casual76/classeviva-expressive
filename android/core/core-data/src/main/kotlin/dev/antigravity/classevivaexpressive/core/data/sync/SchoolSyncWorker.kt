package dev.antigravity.classevivaexpressive.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.antigravity.classevivaexpressive.core.data.external.ExternalDashboardInvalidator
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.AgendaCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.GradesCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.LessonsCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.NotesCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncNotificationDispatcher
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncSnapshotPayloads
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val ConsecutiveFailuresKey = "consecutive_failures"
private const val MaintenanceIntervalMillis: Long = 24L * 60L * 60L * 1000L
private const val TimetableMaxAgeMillis: Long = 24L * 60L * 60L * 1000L

@HiltWorker
class SchoolSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val sessionStore: SessionStore,
  private val settingsStore: SettingsStore,
  private val schoolYearStore: SchoolYearStore,
  private val snapshotCacheDao: SnapshotCacheDao,
  private val syncCoordinator: SchoolSyncCoordinator,
  private val notificationDispatcher: SyncNotificationDispatcher,
  private val timetableTemplateStore: TimetableTemplateStore,
  private val lessonsRepository: LessonsRepository,
  private val externalDashboardInvalidators: Set<@JvmSuppressWildcards ExternalDashboardInvalidator>,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val session = sessionStore.readCurrentSession() ?: return Result.success()
    val settings = settingsStore.settings.first()
    if (!settings.periodicSyncEnabled) {
      SyncWorkScheduler.cancel(applicationContext)
      return Result.success()
    }

    val before = capturePayloads()
    var consecutiveFailures = inputData.getInt(ConsecutiveFailuresKey, 0)
    runCatching {
      syncCoordinator.attachSession(session)
      val fastSections = BackgroundSyncPolicy.fastSections(settings.notificationPreferences)
      if (fastSections.isNotEmpty()) {
        val status = syncCoordinator.refreshCurrentSchoolYearForNotifications(
          force = true,
          sections = fastSections,
          mode = BackgroundSyncMode.FAST,
        )
        consecutiveFailures = status.nextFastFailureCount(consecutiveFailures, fastSections)
        if (status.canDispatchNotifications()) {
          notificationDispatcher.dispatch(previous = before, current = capturePayloads())
        }
      } else {
        consecutiveFailures = 0
      }

      if (maintenanceDue()) {
        val maintenanceSections = BackgroundSyncPolicy.maintenanceSections()
        val status = syncCoordinator.refreshCurrentSchoolYearMaintenance(force = true)
        if (status.shouldAdvanceMaintenanceWindow(maintenanceSections)) {
          markMaintenanceCompleted()
        }
        if (status.state == SyncState.IDLE) {
          // Rigenera il template orario al massimo una volta ogni 24h, in background.
          runCatching { regenerateTemplateIfStale() }
        }
      }
    }.onFailure {
      consecutiveFailures += 1
    }
    notifyExternalDashboardInvalidators()
    if (settingsStore.settings.first().periodicSyncEnabled) {
      SyncWorkScheduler.scheduleNext(applicationContext, consecutiveFailures)
    }
    return Result.success()
  }

  private suspend fun maintenanceDue(): Boolean {
    val currentYear = schoolYearStore.currentSchoolYearRef()
    val lastCompletedAt = snapshotCacheDao.getByKey(maintenanceCacheKey(currentYear.id))?.updatedAtEpochMillis
      ?: return true
    return System.currentTimeMillis() - lastCompletedAt > MaintenanceIntervalMillis
  }

  private suspend fun markMaintenanceCompleted() {
    val currentYear = schoolYearStore.currentSchoolYearRef()
    snapshotCacheDao.upsert(
      SnapshotCacheEntity(
        cacheKey = maintenanceCacheKey(currentYear.id),
        payload = "{}",
        updatedAtEpochMillis = System.currentTimeMillis(),
      ),
    )
  }

  private suspend fun regenerateTemplateIfStale() {
    val schoolYearId = schoolYearStore.currentSchoolYearRef().id
    val stored = timetableTemplateStore.observeTemplate(schoolYearId).first()
    val ageMs = System.currentTimeMillis() - stored.lastComputedAt
    val isStale = stored.lastComputedAt == 0L || ageMs > TimetableMaxAgeMillis
    if (isStale) {
      lessonsRepository.regenerateTimetableTemplate()
    }
  }

  private suspend fun capturePayloads(): SyncSnapshotPayloads {
    val currentYear = schoolYearStore.currentSchoolYearRef()
    return SyncSnapshotPayloads(
      homeworks = snapshotCacheDao.getByKey(yearScopedCacheKey(HomeworkCacheSection, currentYear))?.payload,
      communications = snapshotCacheDao.getByKey(yearScopedCacheKey(CommunicationsCacheSection, currentYear))?.payload,
      absences = snapshotCacheDao.getByKey(yearScopedCacheKey(AbsencesCacheSection, currentYear))?.payload,
      grades = snapshotCacheDao.getByKey(yearScopedCacheKey(GradesCacheSection, currentYear))?.payload,
      agenda = snapshotCacheDao.getByKey(yearScopedCacheKey(AgendaCacheSection, currentYear))?.payload,
      notes = snapshotCacheDao.getByKey(yearScopedCacheKey(NotesCacheSection, currentYear))?.payload,
      lessons = snapshotCacheDao.getByKey(yearScopedCacheKey(LessonsCacheSection, currentYear))?.payload,
    )
  }

  companion object {
    const val UniqueWorkName = "classeviva-expressive-periodic-sync"
  }

  private suspend fun notifyExternalDashboardInvalidators() {
    externalDashboardInvalidators.forEach { invalidator ->
      runCatching { invalidator.invalidateDashboard() }
    }
  }
}

object SyncWorkScheduler {
  fun schedule(context: Context) {
    enqueue(context, ExistingWorkPolicy.REPLACE, consecutiveFailures = 0)
  }

  internal fun scheduleNext(context: Context, consecutiveFailures: Int) {
    enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE, consecutiveFailures)
  }

  private fun enqueue(
    context: Context,
    policy: ExistingWorkPolicy,
    consecutiveFailures: Int,
  ) {
    val delayMinutes = BackgroundSyncPolicy.delayMinutes(consecutiveFailures = consecutiveFailures)
    val request = OneTimeWorkRequestBuilder<SchoolSyncWorker>()
      .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
      .setInputData(workDataOf(ConsecutiveFailuresKey to consecutiveFailures))
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      SchoolSyncWorker.UniqueWorkName,
      policy,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(SchoolSyncWorker.UniqueWorkName)
  }
}

private fun maintenanceCacheKey(schoolYearId: String): String = "background_maintenance:$schoolYearId"

private fun SyncStatus.canDispatchNotifications(): Boolean {
  return state != SyncState.ERROR && state != SyncState.OFFLINE
}

private fun SyncStatus.nextFastFailureCount(current: Int, attemptedSections: Set<String>): Int {
  if (state == SyncState.IDLE) return 0
  val hasSuccessfulSection = attemptedSections.any { section -> section !in failedSections }
  if (state == SyncState.PARTIAL && hasSuccessfulSection) return 0
  return current + 1
}

private fun SyncStatus.shouldAdvanceMaintenanceWindow(attemptedSections: Set<String>): Boolean {
  if (state == SyncState.IDLE) return true
  return state == SyncState.PARTIAL && attemptedSections.any { section -> section !in failedSections }
}
