package dev.antigravity.classevivaexpressive.feature.widgets

import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal data class WidgetAccountContent(
  val accountEpoch: Long,
  val snapshot: DashboardSnapshot = DashboardSnapshot(),
  val hasSession: Boolean = false,
  val loaded: Boolean = true,
)

internal data class WidgetPresentation(
  val content: WidgetAccountContent,
  val preferences: SchoolWidgetPreferences,
  val settings: AppSettings,
  val today: LocalDate,
) {
  /** Final admission also covers an old emission still queued when the account changed. */
  fun model(auth: AuthRepository): SchoolWidgetModel {
    val admitted = content.hasSession && auth.dataEpoch() == content.accountEpoch
    return SchoolWidgetMapper.map(
      snapshot = if (admitted) content.snapshot else DashboardSnapshot(),
      preferences = if (admitted) preferences else SchoolWidgetPreferences(),
      hasSession = admitted,
      today = today,
    )
  }
}

/** Collected by Glance's composition for its entire active session, rather than once before it. */
internal fun observeWidgetPresentation(
  auth: AuthRepository,
  dashboard: DashboardRepository,
  settings: SettingsRepository,
  preferences: Flow<SchoolWidgetPreferences>,
  dates: Flow<LocalDate>,
): Flow<WidgetPresentation> = combine(
  observeWidgetAccountContent(auth, dashboard),
  settings.observeSettings().catch { emit(AppSettings()) },
  preferences,
  dates,
) { content, appearance, prefs, today ->
  WidgetPresentation(content, prefs, appearance, today)
}

/** A fresh collection per generation cannot inherit a previous account's cached dashboard. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeWidgetAccountContent(auth: AuthRepository, dashboard: DashboardRepository): Flow<WidgetAccountContent> =
  auth.observeDataEpoch().flatMapLatest { epoch ->
    val hasSession = auth.session.value != null && auth.dataEpoch() == epoch
    if (!hasSession) {
      flowOf(WidgetAccountContent(epoch))
    } else {
      dashboard.observeDashboard()
        .map { WidgetAccountContent(epoch, it, hasSession = true) }
        // Clear old data immediately while the new account's database reads are suspended.
        .onStart { emit(WidgetAccountContent(epoch, hasSession = true, loaded = false)) }
        .catch { error ->
          emit(WidgetAccountContent(epoch, DashboardSnapshot(
            syncStatus = SyncStatus(state = SyncState.ERROR, message = error.message),
          ), hasSession = true))
        }
    }
  }.buffer(0)
