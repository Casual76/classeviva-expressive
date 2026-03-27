package dev.antigravity.classevivaexpressive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.data.sync.SyncWorkScheduler
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
  val isLoading: Boolean = true,
  val isAuthenticating: Boolean = false,
  val session: UserSession? = null,
  val settings: AppSettings = AppSettings(),
  val authError: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val settingsRepository: SettingsRepository,
  @ApplicationContext private val context: Context,
) : ViewModel() {
  private val isRestoring = MutableStateFlow(true)
  private val isAuthenticating = MutableStateFlow(false)
  private val authError = MutableStateFlow<String?>(null)

  val uiState = combine(
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
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

  init {
    viewModelScope.launch {
      runCatching { authRepository.restore() }
      isRestoring.value = false
    }
    viewModelScope.launch {
      combine(authRepository.session, settingsRepository.observeSettings()) { session, settings ->
        session to settings
      }.collect { (session, settings) ->
        if (session != null && settings.periodicSyncEnabled) {
          SyncWorkScheduler.schedule(context)
        } else {
          SyncWorkScheduler.cancel(context)
        }
      }
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
}
