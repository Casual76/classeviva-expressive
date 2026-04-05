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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesCacheKey
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsCacheKey
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkCacheKey
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncNotificationDispatcher
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncSnapshotPayloads
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val RefreshIntervalMinutes = 5L

@HiltWorker
class SchoolSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val sessionStore: SessionStore,
  private val settingsStore: SettingsStore,
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
      val status = syncCoordinator.refreshAll(force = true)
      if (status.state != dev.antigravity.classevivaexpressive.core.domain.model.SyncState.ERROR &&
        status.state != dev.antigravity.classevivaexpressive.core.domain.model.SyncState.OFFLINE
      ) {
        notificationDispatcher.dispatch(previous = before, current = capturePayloads())
      }
    }

    if (sessionStore.readCurrentSession() != null && settingsStore.settings.first().periodicSyncEnabled) {
      SyncWorkScheduler.scheduleNext(applicationContext)
    }
    return Result.success()
  }

  private suspend fun capturePayloads(): SyncSnapshotPayloads {
    return SyncSnapshotPayloads(
      homeworks = snapshotCacheDao.getByKey(HomeworkCacheKey)?.payload,
      communications = snapshotCacheDao.getByKey(CommunicationsCacheKey)?.payload,
      absences = snapshotCacheDao.getByKey(AbsencesCacheKey)?.payload,
    )
  }

  companion object {
    const val UniquePeriodicWorkName = "classeviva-expressive-periodic-sync"
  }
}

object SyncWorkScheduler {
  fun schedule(context: Context) {
    enqueue(context = context, delayMinutes = RefreshIntervalMinutes, replace = false)
  }

  internal fun scheduleNext(context: Context) {
    enqueue(context = context, delayMinutes = RefreshIntervalMinutes, replace = true)
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(SchoolSyncWorker.UniquePeriodicWorkName)
  }

  private fun enqueue(
    context: Context,
    delayMinutes: Long,
    replace: Boolean,
  ) {
    val request = OneTimeWorkRequestBuilder<SchoolSyncWorker>()
      .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
      SchoolSyncWorker.UniquePeriodicWorkName,
      if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
      request,
    )
  }
}
