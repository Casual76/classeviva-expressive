package dev.antigravity.classevivaexpressive.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.TestChannelId
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.QuickAction
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.SectionTitle
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.designsystem.theme.expressiveAccentPresets
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationChannelStatus
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val settings: AppSettings = AppSettings(),
  val runtimeState: NotificationRuntimeState = NotificationRuntimeState(),
  val session: UserSession? = null,
  val selectedSchoolYear: SchoolYearRef = SchoolYearRef.current(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue),
  val availableSchoolYears: List<SchoolYearRef> = emptyList(),
  val capabilities: List<FeatureCapability> = emptyList(),
  val lastMessage: String? = null,
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
  private val authRepository: AuthRepository,
  private val schoolYearRepository: SchoolYearRepository,
  private val capabilityResolver: CapabilityResolver,
) : ViewModel() {
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)

  private val contentState = combine(
    settingsRepository.observeSettings(),
    settingsRepository.observeNotificationRuntimeState(),
    authRepository.session,
    schoolYearRepository.observeSelectedSchoolYear(),
  ) { settings, runtimeState, session, selectedSchoolYear ->
    SettingsUiState(
      settings = settings,
      runtimeState = runtimeState,
      session = session,
      selectedSchoolYear = selectedSchoolYear,
    )
  }

  private val registryState = combine(
    schoolYearRepository.observeAvailableSchoolYears(),
    capabilityResolver.observeCapabilityMatrix(),
  ) { availableSchoolYears, capabilities ->
    availableSchoolYears to capabilities
  }

  val state = combine(
    contentState,
    registryState,
    lastMessage,
    isRefreshing,
  ) { content, registry, message, refreshing ->
    SettingsUiState(
      settings = content.settings,
      runtimeState = content.runtimeState,
      session = content.session,
      selectedSchoolYear = content.selectedSchoolYear,
      availableSchoolYears = registry.first,
      capabilities = registry.second,
      lastMessage = message,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

  init {
    refresh(showIndicator = false)
  }

  fun refresh() {
    refresh(showIndicator = true)
  }

  private fun refresh(showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      settingsRepository.refreshNotificationRuntimeState()
      isRefreshing.value = false
    }
  }

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

  fun setNotificationCategoryEnabled(channelId: String, enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setNotificationCategoryEnabled(channelId, enabled) }
  }

  fun sendTestNotification() {
    viewModelScope.launch {
      settingsRepository.sendTestNotification()
        .onSuccess { lastMessage.value = "Notifica di test inviata." }
        .onFailure { lastMessage.value = it.message ?: "Invio notifica di test non riuscito." }
    }
  }

  fun clearMessage() {
    lastMessage.value = null
  }

  fun logout() {
    viewModelScope.launch { authRepository.logout() }
  }

  fun selectSchoolYear(year: SchoolYearRef) {
    viewModelScope.launch {
      schoolYearRepository.selectSchoolYear(year)
      lastMessage.value = "Anno scolastico impostato su ${year.label}."
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val enabledLocalChannels = listOf(
    state.settings.notificationPreferences.homework,
    state.settings.notificationPreferences.communications,
    state.settings.notificationPreferences.absences,
    state.settings.notificationPreferences.test,
  ).count { it }
  val enabledSystemChannels = state.runtimeState.channels.count { it.enabled }

  PullToRefreshBox(
    modifier = modifier.fillMaxSize(),
    isRefreshing = state.isRefreshing,
    onRefresh = { viewModel.refresh() },
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      item {
        ExpressiveTopHeader(
          title = "Impostazioni",
          subtitle = "Tema, notifiche Android e sync in background ogni 5 minuti quando l'account e attivo.",
          onBack = onBack,
          actions = {
            IconButton(onClick = { viewModel.refresh() }) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
            }
          },
        )
      }
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricTile(
            label = "Canali locali",
            value = enabledLocalChannels.toString(),
            detail = "Preferenze abilitate nell'app.",
            modifier = Modifier.fillMaxWidth(),
          )
          MetricTile(
            label = "Canali sistema",
            value = enabledSystemChannels.toString(),
            detail = "Canali Android realmente attivi.",
            modifier = Modifier.fillMaxWidth(),
            tone = if (state.runtimeState.appNotificationsEnabled) ExpressiveTone.Success else ExpressiveTone.Warning,
          )
          MetricTile(
            label = "Sync",
            value = if (state.settings.periodicSyncEnabled) "5 min" else "Off",
            detail = "WorkManager in background.",
            modifier = Modifier.fillMaxWidth(),
            tone = if (state.settings.periodicSyncEnabled) ExpressiveTone.Info else ExpressiveTone.Neutral,
          )
        }
      }
      item {
        ExpressiveHeroCard(
          title = state.session?.profile?.name?.ifBlank { "Studente" } ?: "Nessuna sessione attiva",
          subtitle = buildString {
            append(state.session?.username ?: "Login richiesto")
            state.session?.profile?.schoolClass?.takeIf(String::isNotBlank)?.let {
              append(" / ")
              append(it)
            }
            state.session?.profile?.school?.takeIf(String::isNotBlank)?.let {
              append(" / ")
              append(it)
            }
          },
        )
      }
      item {
        SectionTitle(
          eyebrow = "Registro",
          title = "Anno scolastico e capability",
        )
      }
      item {
        ExpressiveCard {
          Text("Anno scolastico attivo")
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            state.availableSchoolYears.forEach { year ->
              FilterChip(
                selected = state.selectedSchoolYear.id == year.id,
                onClick = { viewModel.selectSchoolYear(year) },
                label = { Text(year.label) },
              )
            }
          }
        }
      }
      if (state.capabilities.isNotEmpty()) {
        item {
          ExpressiveCard {
            Text("Matrice funzionale")
          }
        }
        items(state.capabilities, key = { it.feature.name }) { capability ->
          CapabilityRow(capability = capability)
        }
      }
      state.lastMessage?.let { message ->
        item {
          ExpressiveCard(highlighted = true) {
            Text(message)
            TextButton(onClick = viewModel::clearMessage) {
              Text("Nascondi messaggio")
            }
          }
        }
      }
      item {
        SectionTitle(
          eyebrow = "Tema",
          title = "Aspetto dell'app",
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
                label = { Text(mode.label()) },
              )
            }
          }
        }
      }
      item {
        ExpressiveCard {
          Text("Accento")
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
          eyebrow = "Comportamento",
          title = "Preferenze operative",
        )
      }
      item {
        SettingToggleRow(
          title = "Dynamic color",
          subtitle = "Usa i colori di sistema quando l'accento e impostato su Dynamic.",
          checked = state.settings.dynamicColorEnabled,
          onCheckedChange = viewModel::setDynamicColor,
        )
      }
      item {
        SettingToggleRow(
          title = "Tema AMOLED",
          subtitle = "Usa il nero pieno oltre al dark standard.",
          checked = state.settings.amoledEnabled,
          onCheckedChange = viewModel::setAmoled,
        )
      }
      item {
        SettingToggleRow(
          title = "Notifiche in app",
          subtitle = "Abilita o disabilita la gestione locale delle notifiche.",
          checked = state.settings.notificationsEnabled,
          onCheckedChange = viewModel::setNotifications,
        )
      }
      item {
        SettingToggleRow(
          title = "Sync periodica",
          subtitle = "Mantiene attivo il refresh in background ogni 5 minuti finche la sessione resta valida.",
          checked = state.settings.periodicSyncEnabled,
          onCheckedChange = viewModel::setPeriodicSync,
        )
      }
      item {
        SectionTitle(
          eyebrow = "Notifiche",
          title = "Permessi e canali",
        )
      }
      item {
        RuntimeStateCard(runtimeState = state.runtimeState)
      }
      item {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickAction(label = "Invia test", onClick = viewModel::sendTestNotification)
          QuickAction(label = "Rileggi stato", onClick = { viewModel.refresh() })
        }
      }
      state.runtimeState.channels.forEach { channel ->
        item(key = channel.id) {
          SettingToggleRow(
            title = channel.label.ifBlank { channel.id },
            subtitle = channelSubtitle(channel, state.settings),
            checked = channelEnabledInSettings(channel.id, state.settings),
            onCheckedChange = { enabled ->
              viewModel.setNotificationCategoryEnabled(channel.id, enabled)
            },
            badge = {
              StatusBadge(
                label = if (channel.enabled) "SISTEMA ON" else "SISTEMA OFF",
                tone = if (channel.enabled) ExpressiveTone.Success else ExpressiveTone.Warning,
              )
            },
          )
        }
      }
      item {
        SectionTitle(
          eyebrow = "Account",
          title = "Profilo attivo",
        )
      }
      item {
        ExpressiveCard {
          Text(state.session?.profile?.schoolYear?.ifBlank { "Anno scolastico non disponibile" } ?: "Sessione non presente")
          Text(state.session?.profile?.email?.ifBlank { "Email non disponibile" } ?: "Accedi per sincronizzare i dati")
          TextButton(onClick = viewModel::logout) {
            Text("Esci")
          }
        }
      }
    }
  }
}

@Composable
private fun RuntimeStateCard(
  runtimeState: NotificationRuntimeState,
) {
  ExpressiveCard(highlighted = !runtimeState.permissionGranted || !runtimeState.appNotificationsEnabled) {
    Text("Stato notifiche")
    Text("Permesso: ${if (runtimeState.permissionGranted) "concesso" else "negato"}")
    Text("App: ${if (runtimeState.appNotificationsEnabled) "abilitata" else "disabilitata nelle impostazioni di sistema"}")
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  badge: @Composable (() -> Unit)? = null,
) {
  ExpressiveCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(title)
        Text(subtitle)
      }
      badge?.invoke()
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    }
  }
}

private fun ThemeMode.label(): String {
  return when (this) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Chiaro"
    ThemeMode.DARK -> "Scuro"
    ThemeMode.AMOLED -> "AMOLED"
  }
}

private fun channelEnabledInSettings(
  channelId: String,
  settings: AppSettings,
): Boolean {
  return when (channelId) {
    HomeworkChannelId -> settings.notificationPreferences.homework
    CommunicationsChannelId -> settings.notificationPreferences.communications
    AbsencesChannelId -> settings.notificationPreferences.absences
    TestChannelId -> settings.notificationPreferences.test
    else -> false
  }
}

private fun channelSubtitle(
  channel: NotificationChannelStatus,
  settings: AppSettings,
): String {
  val local = if (channelEnabledInSettings(channel.id, settings)) "attivo" else "disattivo"
  val system = if (channel.enabled) "abilitato" else "disabilitato"
  return "Canale locale $local, canale Android $system."
}

@Composable
private fun CapabilityRow(capability: FeatureCapability) {
  val tone = when {
    !capability.enabled -> ExpressiveTone.Warning
    capability.mode == FeatureCapabilityMode.GATEWAY -> ExpressiveTone.Info
    capability.mode == FeatureCapabilityMode.TENANT_OPTIONAL -> ExpressiveTone.Neutral
    else -> ExpressiveTone.Success
  }
  RegisterListRow(
    title = capability.feature.name.replace('_', ' '),
    subtitle = capability.detail ?: "Nessun dettaglio disponibile.",
    eyebrow = capability.label.ifBlank { "Capability" },
    tone = tone,
    badge = { StatusBadge(capability.mode.name.replace('_', ' '), tone = tone) },
  )
}
