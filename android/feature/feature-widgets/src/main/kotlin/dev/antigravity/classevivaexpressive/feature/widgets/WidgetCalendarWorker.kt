package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Local repaint only: calendar changes never require a school sync or a network connection. */
class WidgetCalendarWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    SchoolOverviewWidget().updateAll(applicationContext)
    return Result.success()
  }

  companion object {
    fun enqueue(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        "classeviva-expressive-widget-calendar",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<WidgetCalendarWorker>().build(),
      )
    }
  }
}
