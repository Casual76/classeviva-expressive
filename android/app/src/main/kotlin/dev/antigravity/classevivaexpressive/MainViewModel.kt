package dev.antigravity.classevivaexpressive

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.fluidengine.foundation.AppUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.data.sync.SyncWorkScheduler
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearSelectionPolicy
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone

private const val LiveTimetableRefreshMinIntervalMillis = 60_000L

/** Marks the notices that a repository is holding on to until the user has actually seen them. */
private const val DurableNotificationPrefix = "durable:"

/** Distinguishes one failure from the next one that reads the same. Never persisted, never parsed. */
private val syncNoticeSequence = AtomicLong()

private fun SyncStatus.toFailureNotification(): FluidNotification? {
  val tone = when {
    // A year the school has not opened yet is not a fault: nothing is broken and nobody can fix it.
    // In the weeks either side of September it is the ordinary state of the registro, and dressing
    // it as a failure is how a colour stops meaning anything.
    schoolYearNotStarted -> FluidNotificationTone.Info
    state == SyncState.OFFLINE || state == SyncState.ERROR -> FluidNotificationTone.Error
    state == SyncState.PARTIAL -> FluidNotificationTone.Warning
    else -> return null
  }
  val detail = message?.takeIf(String::isNotBlank) ?: return null
  val title = when {
    schoolYearNotStarted -> "Anno scolastico non ancora aperto"
    state == SyncState.OFFLINE -> "Nessuna connessione"
    else -> "Aggiornamento non riuscito"
  }
  return FluidNotification(
    // Unique per occurrence: the same failure happening again is news again, and an id that
    // repeated would be swallowed as a duplicate of the banner the user already dismissed.
    id = "sync:${state.name}:${syncNoticeSequence.incrementAndGet()}",
    title = title,
    message = detail,
    tone = tone,
    durationMillis = 7_000L,
  )
}

data class MainUiState(
  val isLoading: Boolean = true,
  val isAuthenticating: Boolean = false,
  val session: UserSession? = null,
  val settings: AppSettings = AppSettings(),
  val authError: String? = null,
)

private data class BackgroundSyncState(
  val hasSession: Boolean,
  val periodicSyncEnabled: Boolean,
)

private data class LiveTimetableConfig(
  val hasSession: Boolean,
  val notificationsEnabled: Boolean,
  val liveTimetableEnabled: Boolean,
)

@HiltViewModel
class MainViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val settingsRepository: SettingsRepository,
  /** L'aggiornamento dall'app (engine-update): il controllore dell'interfaccia lo prende da qui. */
  val updater: AppUpdater,
  private val schoolYearRepository: SchoolYearRepository,
  dashboardRepository: DashboardRepository,
  @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val isRestoring = MutableStateFlow(true)
  private val isAuthenticating = MutableStateFlow(false)
  private val authError = MutableStateFlow<String?>(null)
  private var liveTimetableRefreshJob: Job? = null
  private var lastLiveTimetableRefreshAtMillis = 0L

  private val baseState = combine(
    authRepository.session,
    settingsRepository.observeSettings(),
    isRestoring,
    isAuthenticating,
    authError,
  ) { session, settings, restoring, authenticating, error ->
    MainUiState(
      isLoading = restoring,
      isAuthenticating = authenticating,
      session = session,
      settings = settings,
      authError = error,
    )
  }

  val uiState = baseState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

  /** Durable repository events translated once into the app-wide visual notice vocabulary. */
  private val fallbackNotifications = schoolYearRepository.observeFallbackEvents().map { event ->
    FluidNotification(
      id = DurableNotificationPrefix + event.id,
      title = "Anno scolastico aggiornato",
      message = "Il ${event.requested.label} non è ancora disponibile. " +
        "Per ora mostro il ${event.selected.label}.",
      tone = FluidNotificationTone.Info,
      durationMillis = 8_000L,
    )
  }

  /**
   * A refresh that fails is an event, and events have to be delivered.
   *
   * The failure was already known — the message, and which sections it applied to — and the only
   * place it appeared was the colour of a glyph in the corner of the bar. Someone who has just
   * pulled the page down to refresh it is looking at the page, not auditing the chrome, so the
   * explanation goes to them rather than waiting to be found.
   *
   * The first status is dropped: on a cold start it describes a sync that finished before the app
   * was open, which is history rather than news. The page itself carries that one, permanently.
   */
  private val syncFailureNotifications = dashboardRepository.observeDashboard()
    .map { it.syncStatus }
    .distinctUntilChangedBy { it.state to it.message }
    .drop(1)
    .mapNotNull(SyncStatus::toFailureNotification)

  /**
   * Switching to a year the registro only half publishes.
   *
   * Showing last year's noticeboard beside an empty Voti with nothing said about it is the app
   * looking broken in the one situation where it is working exactly as the registro allows. Said
   * once, at the moment the choice is made; the pages that are actually affected say it again, in
   * place, for as long as the choice stands.
   */
  private val archivedYearNotifications = schoolYearRepository.observeSelectedSchoolYear()
    .distinctUntilChanged()
    .drop(1)
    .mapNotNull { selected ->
      val current = SchoolYearSelectionPolicy.current(LocalDate.now())
      if (selected.startYear >= current.startYear) return@mapNotNull null
      FluidNotification(
        id = "archived-year:${selected.id}",
        title = "Anno ${selected.label}",
        message = "Il registro non pubblica i voti e i libri di testo degli anni passati: " +
          "quelle sezioni restano su quanto già salvato.",
        tone = FluidNotificationTone.Warning,
        durationMillis = 9_000L,
      )
    }

  val inAppNotifications: Flow<FluidNotification> = merge(
    fallbackNotifications,
    syncFailureNotifications,
    archivedYearNotifications,
  )

  init {
    viewModelScope.launch {
      runCatching { authRepository.restore() }
      isRestoring.value = false
    }
    viewModelScope.launch {
      combine(
        authRepository.session.map { it != null }.distinctUntilChanged(),
        settingsRepository.observeSettings().map { it.periodicSyncEnabled }.distinctUntilChanged(),
      ) { hasSession, periodicSyncEnabled ->
        BackgroundSyncState(hasSession, periodicSyncEnabled)
      }.distinctUntilChanged().collect { state ->
        if (state.hasSession && state.periodicSyncEnabled) {
          SyncWorkScheduler.schedule(context)
        } else {
          SyncWorkScheduler.cancel(context)
        }
      }
    }
    viewModelScope.launch {
      combine(
        authRepository.session.map { it != null }.distinctUntilChanged(),
        settingsRepository.observeSettings()
          .map { it.notificationPreferences.enabled to it.notificationPreferences.liveTimetable }
          .distinctUntilChanged(),
      ) { hasSession, prefs ->
        LiveTimetableConfig(
          hasSession = hasSession,
          notificationsEnabled = prefs.first,
          liveTimetableEnabled = prefs.second,
        )
      }.distinctUntilChanged().collect { config ->
        if (config.hasSession) refreshLiveTimetableIfNeeded(config, force = true, allowDisabled = true)
      }
    }
  }

  fun onAppResumed() {
    val state = uiState.value
    refreshLiveTimetableIfNeeded(
      config = LiveTimetableConfig(
        hasSession = state.session != null,
        notificationsEnabled = state.settings.notificationPreferences.enabled,
        liveTimetableEnabled = state.settings.notificationPreferences.liveTimetable,
      ),
      force = false,
      allowDisabled = false,
    )
  }

  /** Called only after the root host has accepted the notice into its in-memory queue. */
  /**
   * Only the durable half of the stream has anything to acknowledge; the rest is narration of
   * state the app can recompute at any time, and handing those ids to the store would be asking it
   * to forget events it never recorded.
   */
  fun acknowledgeInAppNotification(id: String) {
    if (!id.startsWith(DurableNotificationPrefix)) return
    val eventId = id.removePrefix(DurableNotificationPrefix)
    viewModelScope.launch { schoolYearRepository.acknowledgeFallbackEvent(eventId) }
  }

  /** "Salta questa versione": il controllo successivo non la propone piu'. */
  fun ignoreUpdateVersion(version: String) {
    viewModelScope.launch { settingsRepository.ignoreStableUpdateVersion(version) }
  }

  fun login(username: String, password: String) {
    viewModelScope.launch {
      isAuthenticating.value = true
      authError.value = null
      authRepository.login(username.trim(), password)
        .onFailure {
          authError.value = it.message ?: "Accesso non riuscito. Controlla le credenziali."
        }
      isAuthenticating.value = false
    }
  }

  fun clearAuthError() {
    authError.value = null
  }

  fun onNotificationPermissionResult(granted: Boolean) {
    viewModelScope.launch {
      settingsRepository.refreshNotificationRuntimeState()
      if (granted) {
        refreshLiveTimetableIfNeeded(
          config = LiveTimetableConfig(
            hasSession = authRepository.session.value != null,
            notificationsEnabled = uiState.value.settings.notificationPreferences.enabled,
            liveTimetableEnabled = uiState.value.settings.notificationPreferences.liveTimetable,
          ),
          force = true,
          allowDisabled = false,
        )
      }
    }
  }

  private fun refreshLiveTimetableIfNeeded(
    config: LiveTimetableConfig,
    force: Boolean,
    allowDisabled: Boolean,
  ) {
    if (!config.hasSession) return
    if (!allowDisabled && (!config.notificationsEnabled || !config.liveTimetableEnabled)) return
    val now = SystemClock.elapsedRealtime()
    if (!force && now - lastLiveTimetableRefreshAtMillis < LiveTimetableRefreshMinIntervalMillis) return
    if (liveTimetableRefreshJob?.isActive == true) return
    lastLiveTimetableRefreshAtMillis = now
    liveTimetableRefreshJob = viewModelScope.launch {
      runCatching { settingsRepository.refreshLiveTimetable() }
    }
  }
}
