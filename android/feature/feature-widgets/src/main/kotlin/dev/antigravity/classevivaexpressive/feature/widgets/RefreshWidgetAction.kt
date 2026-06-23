package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

class RefreshWidgetAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    updateAppWidgetState(context, glanceId) { prefs ->
      prefs[SchoolWidgetPreferenceKeys.Refreshing] = true
      prefs[SchoolWidgetPreferenceKeys.LastRefreshError] = ""
    }
    SchoolOverviewWidget().update(context, glanceId)
    WidgetRefreshWorker.enqueue(context)
  }
}
