package dev.antigravity.classevivaexpressive

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.data.sync.SyncWorkScheduler
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val LiveTimetableRefreshMinIntervalMillis = 60_000L

data class MainUiState(
  val isLoading: Boolean = true,
  val isAuthenticating: Boolean = false,
  val session: UserSession? = null,
  val settings: AppSettings = AppSettings(),
  val authError: String? = null,
  val availableUpdate: AvailableAppUpdate? = null,
  val updateInstallState: AppUpdateInstallState = AppUpdateInstallState.Idle,
  val isCheckingUpdate: Boolean = false,
  val isUpdateDismissedForSession: Boolean = false,
  val updateCheckMessage: String? = null,
)

private data class UpdatePromptState(
  val availableUpdate: AvailableAppUpdate? = null,
  val installState: AppUpdateInstallState = AppUpdateInstallState.Idle,
  val isChecking: Boolean = false,
  val dismissedForSession: Boolean = false,
  val message: String? = null,
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
  private val appUpdateRepository: AppUpdateRepository,
  @ApplicationContext private val context: Context,
) : ViewModel() {
  private val isRestoring = MutableStateFlow(true)
  private val isAuthenticating = MutableStateFlow(false)
  private val authError = MutableStateFlow<String?>(null)
  private val availableUpdate = MutableStateFlow<AvailableAppUpdate?>(null)
  private val updateInstallState = MutableStateFlow<AppUpdateInstallState>(AppUpdateInstallState.Idle)
  private val isCheckingUpdate = MutableStateFlow(false)
  private val isUpdateDismissedForSession = MutableStateFlow(false)
  private val updateCheckMessage = MutableStateFlow<String?>(null)
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

  private val updatePromptState = combine(
    availableUpdate,
    updateInstallState,
    isCheckingUpdate,
    isUpdateDismissedForSession,
    updateCheckMessage,
  ) { update, installState, checking, dismissed, message ->
    UpdatePromptState(
      availableUpdate = update,
      installState = installState,
      isChecking = checking,
      dismissedForSession = dismissed,
      message = message,
    )
  }

  val uiState = combine(baseState, updatePromptState) { base, update ->
    base.copy(
      availableUpdate = update.availableUpdate,
      updateInstallState = update.installState,
      isCheckingUpdate = update.isChecking,
      isUpdateDismissedForSession = update.dismissedForSession,
      updateCheckMessage = update.message,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

  init {
    viewModelScope.launch {
      runCatching { authRepository.restore() }
      isRestoring.value = false
      checkUpdate(showNoUpdateMessage = false)
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

  fun checkUpdate(showNoUpdateMessage: Boolean = true) {
    if (isCheckingUpdate.value) return
    viewModelScope.launch {
      isCheckingUpdate.value = true
      if (showNoUpdateMessage) updateCheckMessage.value = null
      try {
        val ignoredVersion = if (showNoUpdateMessage) {
          ""
        } else {
          settingsRepository.observeSettings().first().ignoredStableUpdateVersion
        }
        appUpdateRepository.checkForStableUpdate(
          currentVersionName = BuildConfig.VERSION_NAME,
          ignoredVersion = ignoredVersion,
        ).onSuccess { update ->
          availableUpdate.value = update
          updateInstallState.value = AppUpdateInstallState.Idle
          if (update != null) {
            isUpdateDismissedForSession.value = false
            if (showNoUpdateMessage) {
              updateCheckMessage.value = "Aggiornamento ${update.version} disponibile."
            }
          } else if (showNoUpdateMessage) {
            updateCheckMessage.value = "Nessun aggiornamento disponibile."
          }
        }.onFailure { error ->
          if (showNoUpdateMessage) {
            updateCheckMessage.value = error.message ?: "Controllo aggiornamenti non riuscito."
          }
        }
      } finally {
        isCheckingUpdate.value = false
      }
    }
  }

  fun startUpdateInstall() {
    val update = availableUpdate.value ?: return
    if (updateInstallState.value.isBusy()) return
    viewModelScope.launch {
      runCatching {
        appUpdateRepository.install(update).collect { state ->
          updateInstallState.value = state
        }
      }.onFailure { error ->
        updateInstallState.value = AppUpdateInstallState.Error(
          error.message ?: "Aggiornamento non riuscito.",
        )
      }
    }
  }

  fun dismissUpdate() {
    isUpdateDismissedForSession.value = true
  }

  fun clearUpdateCheckMessage() {
    updateCheckMessage.value = null
  }

  fun ignoreUpdateVersion() {
    val update = availableUpdate.value ?: return
    viewModelScope.launch {
      settingsRepository.ignoreStableUpdateVersion(update.version)
      availableUpdate.value = null
      updateInstallState.value = AppUpdateInstallState.Idle
      isUpdateDismissedForSession.value = true
    }
  }

  fun login(username: String, password: String) {
    viewModelScope.launch {
      isAuthenticating.value = true
      authError.value = null
      authRepository.login(username.trim(), password)
        .onFailure {
          authError.value = it.message ?: "Login fallito. Controlla le credenziali."
        }
      isAuthenticating.value = false
    }
  }

  fun clearAuthError() {
    authError.value = null
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

private fun AppUpdateInstallState.isBusy(): Boolean = when (this) {
  is AppUpdateInstallState.Downloading,
  is AppUpdateInstallState.Verifying,
  is AppUpdateInstallState.Installing,
  is AppUpdateInstallState.AwaitingUserAction -> true
  AppUpdateInstallState.Idle,
  is AppUpdateInstallState.Installed,
  is AppUpdateInstallState.Error -> false
}
