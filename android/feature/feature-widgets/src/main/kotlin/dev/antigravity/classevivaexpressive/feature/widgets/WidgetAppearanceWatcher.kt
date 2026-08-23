package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Repaints the placed widgets when the app's appearance changes.
 *
 * A widget resolves its palette once, at the moment it is built, and then sits on the home screen
 * until something asks it to rebuild. Data changes already do that through
 * [SchoolWidgetInvalidator]; a change of accent or theme did not, so switching to AMOLED left the
 * widget on the previous colours until the next sync — an hour later, at which point it reads as
 * the setting not having worked rather than as a delay.
 */
@Singleton
class WidgetAppearanceWatcher @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val settingsRepository: SettingsRepository,
) {
  fun start(scope: CoroutineScope) {
    scope.launch {
      settingsRepository.observeSettings()
        .map { settings ->
          WidgetAppearance(
            themeMode = settings.themeMode,
            accentMode = settings.accentMode,
            customAccentName = settings.customAccentName,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            amoledEnabled = settings.amoledEnabled,
          )
        }
        .distinctUntilChanged()
        // The first value is whatever the widget was already drawn with; only a change is news.
        .drop(1)
        .collect { SchoolOverviewWidget().updateAll(context) }
    }
  }
}

private data class WidgetAppearance(
  val themeMode: ThemeMode,
  val accentMode: AccentMode,
  val customAccentName: String,
  val dynamicColorEnabled: Boolean,
  val amoledEnabled: Boolean,
)
