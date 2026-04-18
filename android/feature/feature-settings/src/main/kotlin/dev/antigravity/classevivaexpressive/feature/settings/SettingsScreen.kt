package dev.antigravity.classevivaexpressive.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.GradesChannelId
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: SettingsViewModel = hiltViewModel(),
  sharedTransitionScope: SharedTransitionScope? = null,
  animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val lazyListState = rememberLazyListState()

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.refresh()
  }

  val requestNotificationPermission = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val context = LocalContext.current
  val requestBatteryOptimizationExemption = {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
  }

  val enabledLocalChannels = listOf(
    state.settings.notificationPreferences.homework,
    state.settings.notificationPreferences.communications,
    state.settings.notificationPreferences.absences,
    state.settings.notificationPreferences.grades,
    state.settings.notificationPreferences.test,
  ).count { it }
  val enabledSystemChannels = state.runtimeState.channels.count { it.enabled }

  Scaffold(
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Impostazioni",
        subtitle = "Gestisci aspetto, notifiche e sincronizzazione account.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = { viewModel.refresh() }) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    }
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize(),
      isRefreshing = state.isRefreshing,
      onRefresh = { viewModel.refresh() },
    ) {
      LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item {
          val parallaxOffset = remember {
            derivedStateOf {
              val firstVisibleItemIndex = lazyListState.firstVisibleItemIndex
              if (firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                -offset.toFloat() * 0.2f
              } else {
                0f
              }
            }
          }

          ExpressiveHeroCard(
            modifier = Modifier
              .fillMaxWidth()
              .graphicsLayer {
                translationY = parallaxOffset.value
              },
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
            eyebrow = "Comunicazione",
            title = "Notifiche e Sincronizzazione",
          )
        }
        item {
          RuntimeStateCard(
            runtimeState = state.runtimeState,
            onRequestPermission = requestNotificationPermission
          )
        }
        item {
          SettingToggleRow(
            title = "Notifiche in app",
            subtitle = "Abilita o disabilita globalmente le notifiche inviate dall'app.",
            checked = state.settings.notificationsEnabled,
            onCheckedChange = { enabled ->
              viewModel.setNotifications(enabled)
              if (enabled && !state.runtimeState.permissionGranted) {
                requestNotificationPermission()
              }
            },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
          )
        }
        item {
          SettingToggleRow(
            title = "Sincronizzazione periodica",
            subtitle = "Mantiene aggiornati i dati in background ogni 5 minuti.",
            checked = state.settings.periodicSyncEnabled,
            onCheckedChange = viewModel::setPeriodicSync,
            icon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
          )
        }
        item {
          ExpressiveCard {
            Text("Ottimizzazione Batteria", style = MaterialTheme.typography.titleMedium)
            Text(
              "Per garantire che le notifiche e la sincronizzazione funzionino in background, l'app non deve essere soggetta a ottimizzazioni batteria aggressive.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = requestBatteryOptimizationExemption,
              modifier = Modifier.padding(top = 8.dp)
            ) {
              Text("Esonera da ottimizzazioni batteria")
            }
          }
        }

        if (state.settings.notificationsEnabled && state.runtimeState.permissionGranted) {
          item {
            ExpressiveCard(highlighted = false) {
              Text(
                "Canali Notifiche",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                "Configura quali eventi generano notifiche push.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.runtimeState.channels.forEach { channel ->
                  SettingToggleRow(
                    title = channel.label.ifBlank { channel.id },
                    subtitle = channelSubtitle(channel, state.settings),
                    checked = channelEnabledInSettings(channel.id, state.settings),
                    onCheckedChange = { enabled ->
                      viewModel.setNotificationCategoryEnabled(channel.id, enabled)
                    },
                    badge = {
                      StatusBadge(
                        label = if (channel.enabled) "ON" else "OFF",
                        tone = if (channel.enabled) ExpressiveTone.Success else ExpressiveTone.Warning,
                      )
                    },
                  )
                }
              }
            }
          }
          item {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(onClick = viewModel::sendTestNotification) {
                Text("Invia notifica di test")
              }
              TextButton(onClick = { viewModel.refresh() }) {
                Text("Rileggi stato di sistema")
              }
            }
          }
        }

        item {
          SectionTitle(
            eyebrow = "Interfaccia",
            title = "Personalizzazione Aspetto",
          )
        }
        item {
          ExpressiveCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text("Modalita Colore", style = MaterialTheme.typography.titleMedium)
            }
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
            
            Text("Colore Accento", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              FilterChip(
                selected = state.settings.accentMode == AccentMode.BRAND,
                onClick = { viewModel.setAccentMode(AccentMode.BRAND) },
                label = { Text("Brand Originale") },
              )
              FilterChip(
                selected = state.settings.accentMode == AccentMode.DYNAMIC,
                onClick = { viewModel.setAccentMode(AccentMode.DYNAMIC) },
                label = { Text("Dynamic Color") },
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
          SettingToggleRow(
            title = "Dynamic Color Nativo",
            subtitle = "Forza i colori monet estratti dal sistema se l'accento e impostato su Dynamic.",
            checked = state.settings.dynamicColorEnabled,
            onCheckedChange = viewModel::setDynamicColor,
          )
        }
        item {
          SettingToggleRow(
            title = "Contrasto AMOLED",
            subtitle = "Sostituisce il grigio scuro con il nero profondo per risparmiare batteria.",
            checked = state.settings.amoledEnabled,
            onCheckedChange = viewModel::setAmoled,
          )
        }

        item {
          SectionTitle(
            eyebrow = "Configurazione",
            title = "Connettivita e Registro",
          )
        }
        item {
          ExpressiveCard {
            Text("Anno Scolastico Attivo", style = MaterialTheme.typography.titleMedium)
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
              Text("Capability Sbloccate", style = MaterialTheme.typography.titleMedium)
            }
          }
          items(state.capabilities, key = { it.feature.name }) { capability ->
            CapabilityRow(capability = capability)
          }
        }

        item {
          SectionTitle(
            eyebrow = "Account",
            title = "Sessione e Sicurezza",
          )
        }
        item {
          ExpressiveCard {
            Text(
              text = state.session?.profile?.schoolYear?.ifBlank { "Nessun anno selezionato" } ?: "Non sei autenticato",
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = state.session?.profile?.email?.ifBlank { "Email non presente" } ?: "Effettua l'accesso per sincronizzare.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
              onClick = viewModel::logout,
              modifier = Modifier.padding(top = 8.dp)
            ) {
              Text("Disconnetti Dispositivo")
            }
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
      }
    }
  }
}

@Composable
private fun RuntimeStateCard(
  runtimeState: NotificationRuntimeState,
  onRequestPermission: () -> Unit
) {
  val isError = !runtimeState.permissionGranted || !runtimeState.appNotificationsEnabled
  ExpressiveCard(
    highlighted = isError,
  ) {
    Text(
      "Stato Permessi Android",
      style = MaterialTheme.typography.titleMedium,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
    Text(
      "Permesso di sistema: ${if (runtimeState.permissionGranted) "Concesso" else "Negato"}",
      style = MaterialTheme.typography.bodyMedium
    )
    Text(
      "Impostazioni App (OS): ${if (runtimeState.appNotificationsEnabled) "Abilitate" else "Disabilitate"}",
      style = MaterialTheme.typography.bodyMedium
    )
    
    if (!runtimeState.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      OutlinedButton(
        onClick = onRequestPermission,
        modifier = Modifier.padding(top = 8.dp)
      ) {
        Text("Richiedi Permesso Notifiche")
      }
    } else if (!runtimeState.appNotificationsEnabled) {
      Text(
        "Vai nelle impostazioni di Android per riabilitare le notifiche dell'app.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  icon: @Composable (() -> Unit)? = null,
  badge: @Composable (() -> Unit)? = null,
) {
  ExpressiveCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      icon?.invoke()
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    GradesChannelId -> settings.notificationPreferences.grades
    TestChannelId -> settings.notificationPreferences.test
    else -> false
  }
}

private fun channelSubtitle(
  channel: NotificationChannelStatus,
  settings: AppSettings,
): String {
  val system = if (channel.enabled) "abilitato" else "disabilitato"
  return "Canale Android $system."
}

@Composable
private fun CapabilityRow(capability: FeatureCapability) {
  val tone = when {
    !capability.enabled -> ExpressiveTone.Warning
    capability.mode == FeatureCapabilityMode.DIRECT_PORTAL -> ExpressiveTone.Info
    capability.mode == FeatureCapabilityMode.GATEWAY -> ExpressiveTone.Warning
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
