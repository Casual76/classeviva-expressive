package dev.antigravity.classevivaexpressive.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.SectionTitle
import dev.antigravity.classevivaexpressive.core.designsystem.theme.expressiveAccentPresets
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val settings: AppSettings = AppSettings(),
  val session: UserSession? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
  private val authRepository: AuthRepository,
) : ViewModel() {
  val state = combine(
    settingsRepository.observeSettings(),
    authRepository.session,
  ) { settings, session ->
    SettingsUiState(settings = settings, session = session)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
  }

  fun setAccentMode(mode: AccentMode) {
    viewModelScope.launch { settingsRepository.updateAccentMode(mode) }
  }

  fun setAccentPreset(name: String) {
    viewModelScope.launch {
      settingsRepository.updateAccentMode(AccentMode.CUSTOM_PRESET)
      settingsRepository.updateCustomAccent(name)
    }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setDynamicColorEnabled(enabled) }
  }

  fun setAmoled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setAmoledEnabled(enabled) }
  }

  fun setNotifications(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
  }

  fun setPeriodicSync(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setPeriodicSyncEnabled(enabled) }
  }

  fun logout() {
    viewModelScope.launch { authRepository.logout() }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveHeroCard(
        title = "Impostazioni e profilo",
        subtitle = "Tema automatico, dark, AMOLED e sync periodica integrati con l'identita visiva dell'app.",
      )
    }
    item {
      SectionTitle(
        eyebrow = "Tema",
        title = "Aspetto",
      )
    }
    item {
      ExpressiveCard {
        Text("Modalita colore")
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = state.settings.themeMode == mode,
              onClick = { viewModel.setThemeMode(mode) },
              label = { Text(mode.name) },
            )
          }
        }
      }
    }
    item {
      ExpressiveCard {
        Text("Accenti")
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FilterChip(
            selected = state.settings.accentMode == AccentMode.BRAND,
            onClick = { viewModel.setAccentMode(AccentMode.BRAND) },
            label = { Text("Brand") },
          )
          FilterChip(
            selected = state.settings.accentMode == AccentMode.DYNAMIC,
            onClick = { viewModel.setAccentMode(AccentMode.DYNAMIC) },
            label = { Text("Dynamic") },
          )
          expressiveAccentPresets.forEach { preset ->
            FilterChip(
              selected = state.settings.accentMode == AccentMode.CUSTOM_PRESET &&
                state.settings.customAccentName == preset.name,
              onClick = { viewModel.setAccentPreset(preset.name) },
              label = { Text(preset.name.replaceFirstChar { it.uppercase() }) },
            )
          }
        }
      }
    }
    item {
      SectionTitle(
        eyebrow = "Sistema",
        title = "Comportamento dell'app",
      )
    }
    item {
      SettingToggleRow(
        title = "Dynamic color",
        subtitle = "Usa i colori di Android 12+ quando disponibile",
        checked = state.settings.dynamicColorEnabled,
        onCheckedChange = viewModel::setDynamicColor,
      )
    }
    item {
      SettingToggleRow(
        title = "Modalita AMOLED",
        subtitle = "Usa nero puro invece del dark standard",
        checked = state.settings.amoledEnabled,
        onCheckedChange = viewModel::setAmoled,
      )
    }
    item {
      SettingToggleRow(
        title = "Notifiche",
        subtitle = "Tieni attive le notifiche legate a sync e novita",
        checked = state.settings.notificationsEnabled,
        onCheckedChange = viewModel::setNotifications,
      )
    }
    item {
      SettingToggleRow(
        title = "Sync periodica",
        subtitle = "Aggiorna i dati in background tramite WorkManager",
        checked = state.settings.periodicSyncEnabled,
        onCheckedChange = viewModel::setPeriodicSync,
      )
    }
    item {
      SectionTitle(
        eyebrow = "Profilo",
        title = "Account attivo",
      )
    }
    item {
      ExpressiveCard {
        Text(state.session?.profile?.name?.ifBlank { "Studente" } ?: "Nessuna sessione attiva")
        Text(state.session?.username ?: "Login richiesto")
        TextButton(onClick = viewModel::logout) {
          Text("Esci")
        }
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  ExpressiveCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(title)
        Text(subtitle)
      }
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    }
  }
}
