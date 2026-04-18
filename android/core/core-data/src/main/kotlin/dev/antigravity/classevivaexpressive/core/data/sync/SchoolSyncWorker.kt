package dev.antigravity.classevivaexpressive.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncNotificationDispatcher
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncSnapshotPayloads
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val RefreshIntervalMinutes = 15L

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
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val session = sessionStore.readCurrentSession() ?: return Result.success()
    if (!settingsStore.settings.first().periodicSyncEnabled) {
      SyncWorkScheduler.cancel(applicationContext)
      return Result.success()
    }

    val before = capturePayloads()
    runCatching {
      syncCoordinator.attachSession(session)
      val status = syncCoordinator.refreshCurrentSchoolYearForNotifications(force = true)
      if (status.state != dev.antigravity.classevivaexpressive.core.domain.model.SyncState.ERROR &&
        status.state != dev.antigravity.classevivaexpressive.core.domain.model.SyncState.OFFLINE
      ) {
        notificationDispatcher.dispatch(previous = before, current = capturePayloads())
      }
    }
    return Result.success()
  }

  private suspend fun capturePayloads(): SyncSnapshotPayloads {
    val currentYear = schoolYearStore.currentSchoolYearRef()
    return SyncSnapshotPayloads(
      homeworks = snapshotCacheDao.getByKey(yearScopedCacheKey(HomeworkCacheSection, currentYear))?.payload,
      communications = snapshotCacheDao.getByKey(yearScopedCacheKey(CommunicationsCacheSection, currentYear))?.payload,
      absences = snapshotCacheDao.getByKey(yearScopedCacheKey(AbsencesCacheSection, currentYear))?.payload,
    )
  }

  companion object {
    const val UniquePeriodicWorkName = "classeviva-expressive-periodic-sync"
  }
}

object SyncWorkScheduler {
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<SchoolSyncWorker>(RefreshIntervalMinutes, TimeUnit.MINUTES)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      SchoolSyncWorker.UniquePeriodicWorkName,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(SchoolSyncWorker.UniquePeriodicWorkName)
  }
}
