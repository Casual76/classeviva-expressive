package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class RefreshWidgetAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    // The running worker owns the flag. A KEEP-rejected late tap must not revive a flag
    // that the previous worker has already cleared while finishing its local repaint.
    WidgetRefreshWorker.enqueue(context)
  }
}
