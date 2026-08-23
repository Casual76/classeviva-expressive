package dev.antigravity.classevivaexpressive

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.antigravity.classevivaexpressive.core.data.notifications.AppNotificationChannels
import dev.antigravity.classevivaexpressive.feature.widgets.WidgetAppearanceWatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class ClassevivaExpressiveApp : Application(), Configuration.Provider {
  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var widgetAppearanceWatcher: WidgetAppearanceWatcher

  /** Lives as long as the process does: nothing started here has anything to be cancelled by. */
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    AppNotificationChannels.create(this)
    widgetAppearanceWatcher.start(applicationScope)
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .build()
}
