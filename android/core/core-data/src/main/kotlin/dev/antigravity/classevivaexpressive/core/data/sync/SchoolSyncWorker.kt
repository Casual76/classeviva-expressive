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
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import java.util.concurrent.TimeUnit

@HiltWorker
class SchoolSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val sessionStore: SessionStore,
  private val syncCoordinator: SchoolSyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val session = sessionStore.readCurrentSession() ?: return Result.success()
    syncCoordinator.attachSession(session)
    val status = syncCoordinator.refreshAll(force = true)
    return when (status.state) {
      SyncState.ERROR,
      SyncState.OFFLINE,
      -> Result.retry()
      else -> Result.success()
    }
  }

  companion object {
    const val UniquePeriodicWorkName = "classeviva-expressive-periodic-sync"
  }
}

object SyncWorkScheduler {
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<SchoolSyncWorker>(6, TimeUnit.HOURS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      SchoolSyncWorker.UniquePeriodicWorkName,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(SchoolSyncWorker.UniquePeriodicWorkName)
  }
}
