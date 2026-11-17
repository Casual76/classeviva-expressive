package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class SchoolOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = SchoolOverviewWidget()

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (isWidgetCalendarChange(intent.action)) WidgetCalendarWorker.enqueue(context)
  }
}

internal fun isWidgetCalendarChange(action: String?): Boolean = action in setOf(
  Intent.ACTION_DATE_CHANGED,
  Intent.ACTION_TIME_CHANGED,
  Intent.ACTION_TIMEZONE_CHANGED,
)
