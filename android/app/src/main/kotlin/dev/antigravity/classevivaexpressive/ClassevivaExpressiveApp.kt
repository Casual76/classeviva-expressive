package dev.antigravity.classevivaexpressive

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.antigravity.classevivaexpressive.core.data.notifications.AppNotificationChannels
import javax.inject.Inject

@HiltAndroidApp
class ClassevivaExpressiveApp : Application(), Configuration.Provider {
  @Inject lateinit var workerFactory: HiltWorkerFactory

  override fun onCreate() {
    super.onCreate()
    AppNotificationChannels.create(this)
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .build()
}
