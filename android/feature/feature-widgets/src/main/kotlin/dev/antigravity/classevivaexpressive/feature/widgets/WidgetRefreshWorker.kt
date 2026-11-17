package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val dashboardRepository: DashboardRepository,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    completeWidgetRefresh(
      start = {
        updateWidgetRefreshState(context = applicationContext, refreshing = true, error = "")
        SchoolOverviewWidget().updateAll(applicationContext)
      },
      refresh = { dashboardRepository.refreshDashboard(force = true) },
      finish = { error ->
        updateWidgetRefreshState(context = applicationContext, refreshing = false, error = error)
        SchoolOverviewWidget().updateAll(applicationContext)
      },
    )
    return Result.success()
  }

  companion object {
    fun enqueue(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        WidgetRefreshUniqueWorkName,
        WidgetRefreshExistingWorkPolicy,
        createWidgetRefreshWorkRequest(),
      )
    }
  }
}

internal const val WidgetRefreshUniqueWorkName = "classeviva-expressive-widget-refresh"
internal val WidgetRefreshExistingWorkPolicy = ExistingWorkPolicy.KEEP

internal fun createWidgetRefreshWorkRequest() = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()

/** Cleanup survives cancellation; repeated taps share the running refresh instead of cancelling it. */
internal suspend fun completeWidgetRefresh(
  start: suspend () -> Unit = {},
  refresh: suspend () -> kotlin.Result<*>,
  finish: suspend (String) -> Unit,
) {
  var error = ""
  try {
    start()
    val result = refresh()
    val failure = result.exceptionOrNull()
    if (failure is CancellationException) throw failure
    error = failure?.message.orEmpty()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (failure: Exception) {
    error = failure.message.orEmpty()
  } finally {
    withContext(NonCancellable) { finish(error) }
  }
}

private suspend fun updateWidgetRefreshState(
  context: Context,
  refreshing: Boolean,
  error: String,
) {
  val now = System.currentTimeMillis()
  val manager = GlanceAppWidgetManager(context)
  manager.getGlanceIds(SchoolOverviewWidget::class.java).forEach { glanceId ->
    updateAppWidgetState(context, glanceId) { prefs ->
      prefs[SchoolWidgetPreferenceKeys.Refreshing] = refreshing
      prefs[SchoolWidgetPreferenceKeys.LastRefreshError] = error.take(120)
      prefs[SchoolWidgetPreferenceKeys.LastManualRefreshEpochMillis] = now
    }
  }
}
