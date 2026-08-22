package dev.antigravity.classevivaexpressive.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.AgendaChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.GradesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.LiveTimetableChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.NotesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.TestChannelId
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidBarAction
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidButton
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidButtonStyle
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidChip
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidColorDot
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidMotion
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidScreen
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSectionHeader
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSegmentedControl
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSwitch
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.LocalFluidOriginTracker
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveListDivider
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveListGroup
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveLoading
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.InlineMessageCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.designsystem.theme.classevivaBrandAccent
import dev.antigravity.classevivaexpressive.core.designsystem.theme.expressiveAccentPresets
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppBackupRepository
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsSection(val title: String, val subtitle: String) {
  Account("Account", "Profilo, anno scolastico e sessione"),
  Appearance("Aspetto", "Tema, contrasto e colore accento"),
  Notifications("Notifiche e sync", "Preferenze essenziali e stato"),
  Data("Dati e backup", "Esporta o ripristina i dati locali"),
  About("Informazioni e aggiornamenti", "Versione, update e funzionalità"),
  Diagnostics("Diagnostica avanzata", "Canali Android, test e stato runtime"),
}

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
  private val appBackupRepository: AppBackupRepository,
  @param:ApplicationContext private val applicationContext: Context,
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

  fun sendTestNotificationForChannel(channelId: String) {
    viewModelScope.launch {
      settingsRepository.sendTestNotificationForChannel(channelId)
        .onSuccess { lastMessage.value = "Test notifica «${channelId}» inviato." }
        .onFailure { lastMessage.value = it.message ?: "Invio test non riuscito." }
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

  fun backupFileName(): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "classeviva-expressive-backup-$timestamp.json"
  }

  fun exportBackup(uri: Uri) {
    viewModelScope.launch {
      appBackupRepository.exportBackup()
        .onSuccess { payload ->
          runCatching {
            withContext(Dispatchers.IO) {
              applicationContext.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(payload.encodeToByteArray())
              } ?: error("Impossibile aprire il file di destinazione.")
            }
          }.onSuccess {
            lastMessage.value = "Backup esportato correttamente."
          }.onFailure {
            lastMessage.value = it.message ?: "Esportazione backup non riuscita."
          }
        }
        .onFailure {
          lastMessage.value = it.message ?: "Creazione backup non riuscita."
        }
    }
  }

  fun importBackup(uri: Uri) {
    viewModelScope.launch {
      val payload = runCatching {
        withContext(Dispatchers.IO) {
          applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().decodeToString()
          } ?: error("Impossibile aprire il file selezionato.")
        }
      }.getOrElse {
        lastMessage.value = it.message ?: "Lettura backup non riuscita."
        return@launch
      }

      appBackupRepository.importBackup(payload)
        .onSuccess { summary ->
          lastMessage.value = buildString {
            append("Backup importato: impostazioni")
            append(", ${summary.timetableTemplates} orari")
            append(", ${summary.subjectGoals} obiettivi")
            append(", ${summary.simulatedGrades} voti simulati")
            append(", ${summary.customEvents} eventi.")
          }
        }
        .onFailure {
          lastMessage.value = it.message ?: "Importazione backup non riuscita."
        }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  isCheckingForUpdates: Boolean = false,
  updateCheckMessage: String? = null,
  onCheckForUpdates: () -> Unit = {},
  onClearUpdateCheckMessage: () -> Unit = {},
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
  val section = sectionName?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }
  val originTracker = LocalFluidOriginTracker.current
  var paneOrigin by remember { mutableStateOf(TransformOrigin.Center) }
  LaunchedEffect(sectionName) {
    // Read once, as the section changes: the tracker holds the tap that caused this change, and by
    // the time the pane closes that tap is long gone — so the anchor has to be kept here.
    if (sectionName != null) {
      paneOrigin = originTracker?.consumePending() ?: TransformOrigin.Center
    }
  }
  val context = LocalContext.current
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    viewModel.refresh()
  }
  val exportBackupLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
  ) { uri -> uri?.let(viewModel::exportBackup) }
  val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::importBackup)
  }
  val navigateBack: () -> Unit = {
    if (section != null) {
      sectionName = null
    } else {
      onBack?.invoke()
      Unit
    }
  }
  BackHandler(enabled = section != null, onBack = navigateBack)

  // Sections used to appear and disappear inside a single list with no transition at all, which is
  // why moving around settings felt inert next to the rest of the app. Each section is now its own
  // pane, pushed and popped on the same motion the navigator uses for real destinations.
  AnimatedContent(
    targetState = section,
    modifier = modifier.fillMaxSize(),
    transitionSpec = {
      val opening = targetState != null
      val spring = spring<Float>(dampingRatio = 0.86f, stiffness = FluidMotion.ResponseStandard)
      val collapse = spring<Float>(
        dampingRatio = FluidMotion.DampingChrome,
        stiffness = FluidMotion.ResponseSnappy,
      )
      // The same motion the route transitions use, for the same reason: a settings section is
      // opened *out of* the row you tapped, so it grows from there and collapses back into it.
      // Anchoring both panes to one point is what makes the pair read as a single movement.
      val anchor = paneOrigin
      val transform = if (opening) {
        (
          fadeIn(animationSpec = tween(70, easing = FluidMotion.EaseOut)) +
            scaleIn(initialScale = 0.80f, transformOrigin = anchor, animationSpec = spring)
          ) togetherWith scaleOut(
          targetScale = 0.94f,
          transformOrigin = anchor,
          animationSpec = spring,
        )
      } else {
        scaleIn(
          initialScale = 0.94f,
          transformOrigin = anchor,
          animationSpec = spring,
        ) togetherWith (
          fadeOut(animationSpec = tween(130, delayMillis = 110, easing = FluidMotion.EaseIn)) +
            scaleOut(targetScale = 0.80f, transformOrigin = anchor, animationSpec = collapse)
          )
      }
      // Opening puts the arriving pane on top; closing leaves the departing one there, so the pane
      // doing the travelling is always the one in front.
      transform.targetContentZIndex = if (opening) 1f else 0f
      transform.using(SizeTransform(clip = false))
    },
    label = "settings section",
  ) { section ->
    FluidScreen(
      title = section?.title ?: "Impostazioni",
      subtitle = section?.subtitle ?: "Tutto ciò che serve, senza il muro di opzioni.",
      onBack = if (section != null || onBack != null) navigateBack else null,
      actions = {
        if (section == SettingsSection.Diagnostics) {
          FluidBarAction(
            icon = Icons.Rounded.Refresh,
            contentDescription = "Aggiorna diagnostica",
            onClick = viewModel::refresh,
          )
        }
      },
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
      itemSpacing = 12.dp,
    ) {
      if (section == null) {
        item {
          ExpressiveHeroCard(
            title = state.session?.profile?.name?.ifBlank { "Studente" } ?: "Profilo locale",
            subtitle = listOfNotNull(
              state.session?.profile?.schoolClass?.takeIf(String::isNotBlank),
              state.selectedSchoolYear.label,
            ).joinToString(" · "),
          )
        }
        item {
          ExpressiveListGroup {
            val destinations = SettingsSection.entries.filterNot { it == SettingsSection.Diagnostics }
            destinations.forEachIndexed { index, destination ->
              RegisterListRow(
                title = destination.title,
                subtitle = destination.subtitle,
                tone = if (destination == SettingsSection.Notifications &&
                  state.settings.notificationsEnabled &&
                  (!state.runtimeState.permissionGranted || !state.runtimeState.appNotificationsEnabled)
                ) ExpressiveTone.Warning else ExpressiveTone.Neutral,
                onClick = { sectionName = destination.name },
                badge = {
                  if (destination == SettingsSection.Notifications) {
                    StatusBadge(
                      if (state.settings.notificationsEnabled) "ON" else "OFF",
                      tone = if (state.settings.notificationsEnabled) ExpressiveTone.Success else ExpressiveTone.Neutral,
                    )
                  }
                },
              )
              if (index != destinations.lastIndex) ExpressiveListDivider()
            }
          }
        }
      }

      if (section == SettingsSection.Account) {
        item {
          ExpressiveHeroCard(
            title = state.session?.profile?.name?.ifBlank { "Studente" } ?: "Nessuna sessione",
            subtitle = listOfNotNull(
              state.session?.username,
              state.session?.profile?.school,
              state.session?.profile?.schoolClass,
            ).filter(String::isNotBlank).joinToString(" · ").ifBlank { "Accedi per sincronizzare il registro" },
          )
        }
        item {
          FluidSectionHeader(title = "Anno scolastico")
        }
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableSchoolYears.forEach { year ->
              FluidChip(
                label = year.label,
                selected = state.selectedSchoolYear.id == year.id,
                onClick = { viewModel.selectSchoolYear(year) },
              )
            }
          }
        }
        item {
          FluidButton(
            text = "Disconnetti questo dispositivo",
            onClick = viewModel::logout,
            style = FluidButtonStyle.Filled,
            fillWidth = true,
          )
        }
      }

      if (section == SettingsSection.Appearance) {
        item { FluidSectionHeader(title = "Tema") }
        item {
          // Four mutually exclusive options that all fit on one row: exactly what a segmented
          // control is for. As a row of chips it read as four independent toggles.
          FluidSegmentedControl(
            options = ThemeMode.entries,
            selected = state.settings.themeMode,
            onSelect = viewModel::setThemeMode,
            label = { it.label() },
          )
        }
        item { FluidSectionHeader(title = "Accento") }
        item {
          // Colour is the whole point of this control, so the swatch *is* the control. A row of
          // named chips made the reader map a word onto a colour they could not see.
          AccentPicker(
            settings = state.settings,
            onSelectBrand = { viewModel.setAccentMode(AccentMode.BRAND) },
            onSelectDynamic = { viewModel.setAccentMode(AccentMode.DYNAMIC) },
            onSelectPreset = viewModel::setAccentPreset,
          )
        }
        item {
          SettingToggleRow(
            title = "Dynamic Color nativo",
            subtitle = "Usa i colori del sistema quando l'accento è Dynamic.",
            checked = state.settings.dynamicColorEnabled,
            onCheckedChange = viewModel::setDynamicColor,
          )
        }
        item {
          SettingToggleRow(
            title = "Nero AMOLED",
            subtitle = "Usa superfici nere nel tema scuro.",
            checked = state.settings.amoledEnabled,
            onCheckedChange = viewModel::setAmoled,
          )
        }
      }

      if (section == SettingsSection.Notifications) {
        item {
          SettingToggleRow(
            title = "Notifiche",
            subtitle = "Attiva gli aggiornamenti importanti del registro.",
            checked = state.settings.notificationsEnabled,
            onCheckedChange = { enabled ->
              viewModel.setNotifications(enabled)
              if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.runtimeState.permissionGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
          )
        }
        item {
          SettingToggleRow(
            title = "Sincronizzazione periodica",
            subtitle = "Aggiorna i dati in background con frequenza adattiva.",
            checked = state.settings.periodicSyncEnabled,
            onCheckedChange = viewModel::setPeriodicSync,
            icon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
          )
        }
        if (state.settings.notificationsEnabled &&
          (!state.runtimeState.permissionGranted || !state.runtimeState.appNotificationsEnabled)
        ) {
          item {
            InlineMessageCard(
              title = "Serve il tuo intervento",
              message = "Android sta bloccando almeno una parte delle notifiche. Apri Diagnostica avanzata per correggere lo stato.",
              tone = ExpressiveTone.Warning,
            )
          }
        }
        item {
          RegisterListRow(
            title = SettingsSection.Diagnostics.title,
            subtitle = SettingsSection.Diagnostics.subtitle,
            tone = ExpressiveTone.Info,
            onClick = { sectionName = SettingsSection.Diagnostics.name },
          )
        }
      }

      if (section == SettingsSection.Data) {
        item {
          ExpressiveHeroCard(
            title = "I tuoi dati restano sotto il tuo controllo",
            subtitle = "Il backup viene creato solo quando scegli esplicitamente dove salvarlo.",
          )
        }
        item {
          FluidButton(
            text = "Esporta backup",
            onClick = { exportBackupLauncher.launch(viewModel.backupFileName()) },
            style = FluidButtonStyle.Tinted,
            fillWidth = true,
            leading = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
          )
        }
        item {
          FluidButton(
            text = "Importa backup",
            onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
            style = FluidButtonStyle.Filled,
            fillWidth = true,
            leading = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
          )
        }
      }

      if (section == SettingsSection.About) {
        item {
          AppUpdateSettingsCard(
            isChecking = isCheckingForUpdates,
            message = updateCheckMessage,
            onCheckForUpdates = onCheckForUpdates,
            onClearMessage = onClearUpdateCheckMessage,
          )
        }
        if (state.capabilities.isNotEmpty()) {
          item { FluidSectionHeader(title = "Funzionalità disponibili") }
          items(state.capabilities, key = { it.feature.name }) { capability -> CapabilityRow(capability) }
        }
      }

      if (section == SettingsSection.Diagnostics) {
        val localChannelStates = listOf(
          state.settings.notificationPreferences.homework,
          state.settings.notificationPreferences.communications,
          state.settings.notificationPreferences.absences,
          state.settings.notificationPreferences.grades,
          state.settings.notificationPreferences.agenda,
          state.settings.notificationPreferences.notes,
          state.settings.notificationPreferences.test,
          state.settings.notificationPreferences.liveTimetable,
        )
        item {
          RuntimeStateCard(
            runtimeState = state.runtimeState,
            notificationsEnabled = state.settings.notificationsEnabled,
            periodicSyncEnabled = state.settings.periodicSyncEnabled,
            enabledLocalChannels = localChannelStates.count { it },
            totalLocalChannels = localChannelStates.size,
            enabledSystemChannels = state.runtimeState.channels.count { it.enabled },
            totalSystemChannels = state.runtimeState.channels.size,
            onRequestPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onOpenNotificationSettings = { context.openAppNotificationSettings() },
          )
        }
        items(state.runtimeState.channels, key = { it.id }) { channel ->
          SettingToggleRow(
            title = channel.label.ifBlank { channel.id },
            subtitle = channelSubtitle(channel, state.settings),
            checked = channelEnabledInSettings(channel.id, state.settings),
            onCheckedChange = { viewModel.setNotificationCategoryEnabled(channel.id, it) },
            badge = {
              FluidButton(
                text = "Android",
                onClick = { context.openChannelNotificationSettings(channel.id) },
                style = FluidButtonStyle.Plain,
              )
            },
          )
        }
        item {
          FluidButton(
            text = "Invia notifica di test",
            onClick = viewModel::sendTestNotification,
            style = FluidButtonStyle.Tinted,
            fillWidth = true,
          )
        }
      }

      state.lastMessage?.let { message ->
        item {
          InlineMessageCard(message = message, title = "Impostazioni", onDismiss = viewModel::clearMessage)
        }
      }
    }
  }
}

@Composable
private fun AppUpdateSettingsCard(
  isChecking: Boolean,
  message: String?,
  onCheckForUpdates: () -> Unit,
  onClearMessage: () -> Unit,
) {
  ExpressiveCard {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text("Aggiornamenti app", style = MaterialTheme.typography.titleMedium)
    }
    Text(
      "Controlla manualmente la versione stabile pubblicata su Pampa Store.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidButton(
        text = if (isChecking) "Controllo..." else "Controlla aggiornamenti",
        onClick = onCheckForUpdates,
        style = FluidButtonStyle.Tinted,
        enabled = !isChecking,
        loading = isChecking,
        leading = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
      )
      message?.let {
        FluidButton(
          text = "Nascondi",
          onClick = onClearMessage,
          style = FluidButtonStyle.Plain,
        )
      }
    }
    message?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuntimeStateCard(
  runtimeState: NotificationRuntimeState,
  notificationsEnabled: Boolean,
  periodicSyncEnabled: Boolean,
  enabledLocalChannels: Int,
  totalLocalChannels: Int,
  enabledSystemChannels: Int,
  totalSystemChannels: Int,
  onRequestPermission: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
) {
  val isError = notificationsEnabled && (!runtimeState.permissionGranted || !runtimeState.appNotificationsEnabled)
  ExpressiveCard(
    highlighted = isError,
  ) {
    Text(
      "Diagnostica notifiche",
      style = MaterialTheme.typography.titleMedium,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      StatusBadge(
        label = if (notificationsEnabled) "APP ON" else "APP OFF",
        tone = if (notificationsEnabled) ExpressiveTone.Success else ExpressiveTone.Warning,
      )
      StatusBadge(
        label = if (runtimeState.permissionGranted) "PERMESSO OK" else "PERMESSO KO",
        tone = if (runtimeState.permissionGranted) ExpressiveTone.Success else ExpressiveTone.Warning,
      )
      StatusBadge(
        label = if (runtimeState.appNotificationsEnabled) "OS ON" else "OS OFF",
        tone = if (runtimeState.appNotificationsEnabled) ExpressiveTone.Success else ExpressiveTone.Warning,
      )
      StatusBadge(
        label = if (periodicSyncEnabled) "SYNC ON" else "SYNC OFF",
        tone = if (periodicSyncEnabled) ExpressiveTone.Success else ExpressiveTone.Warning,
      )
    }
    Text(
      "Permesso di sistema: ${if (runtimeState.permissionGranted) "Concesso" else "Negato"}",
      style = MaterialTheme.typography.bodyMedium
    )
    Text(
      "Impostazioni App (OS): ${if (runtimeState.appNotificationsEnabled) "Abilitate" else "Disabilitate"}",
      style = MaterialTheme.typography.bodyMedium
    )
    Text(
      "Categorie app attive: $enabledLocalChannels/$totalLocalChannels",
      style = MaterialTheme.typography.bodyMedium
    )
    Text(
      "Canali Android attivi: $enabledSystemChannels/$totalSystemChannels",
      style = MaterialTheme.typography.bodyMedium
    )
    
    FlowRow(
      modifier = Modifier.padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (notificationsEnabled && !runtimeState.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        FluidButton(
          text = "Richiedi permesso",
          onClick = onRequestPermission,
          style = FluidButtonStyle.Tinted,
        )
      }
      FluidButton(
        text = "Impostazioni Android",
        onClick = onOpenNotificationSettings,
        style = FluidButtonStyle.Tinted,
      )
    }
    if (notificationsEnabled && !runtimeState.appNotificationsEnabled) {
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
      FluidSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    }
  }
}

/**
 * The accent row.
 *
 * "Dynamic" is shown as the colour the system would actually hand over rather than as a word, and
 * the brand entry keeps its own swatch so choosing it is a visible choice like any other.
 */
@Composable
private fun AccentPicker(
  settings: AppSettings,
  onSelectBrand: () -> Unit,
  onSelectDynamic: () -> Unit,
  onSelectPreset: (String) -> Unit,
) {
  val isDark = isSystemInDarkTheme()
  val dynamicColor = dynamicAccentColor(isDark)
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    FluidColorDot(
      color = classevivaBrandAccent(isDark),
      selected = settings.accentMode == AccentMode.BRAND,
      onClick = onSelectBrand,
    )
    if (dynamicColor != null) {
      FluidColorDot(
        color = dynamicColor,
        selected = settings.accentMode == AccentMode.DYNAMIC,
        onClick = onSelectDynamic,
      )
    }
    expressiveAccentPresets.forEach { preset ->
      FluidColorDot(
        color = preset.resolve(isDark),
        selected = settings.accentMode == AccentMode.CUSTOM_PRESET &&
          settings.customAccentName == preset.name,
        onClick = { onSelectPreset(preset.name) },
      )
    }
  }
}

@Composable
private fun dynamicAccentColor(isDark: Boolean): Color? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
  val context = LocalContext.current
  return if (isDark) {
    dynamicDarkColorScheme(context).primary
  } else {
    dynamicLightColorScheme(context).primary
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
    AgendaChannelId -> settings.notificationPreferences.agenda
    NotesChannelId -> settings.notificationPreferences.notes
    TestChannelId -> settings.notificationPreferences.test
    LiveTimetableChannelId -> settings.notificationPreferences.liveTimetable
    else -> false
  }
}

private fun channelSubtitle(
  channel: NotificationChannelStatus,
  settings: AppSettings,
): String {
  val system = if (channel.enabled) "abilitato" else "disabilitato"
  val app = if (channelEnabledInSettings(channel.id, settings)) "categoria app attiva" else "categoria app disattivata"
  return "Canale Android $system / $app."
}

private fun Context.openAppNotificationSettings() {
  val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
      .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
  } else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      .setData(Uri.parse("package:$packageName"))
  }
  startSettingsActivity(intent)
}

private fun Context.openChannelNotificationSettings(channelId: String) {
  val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
      .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
      .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
  } else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      .setData(Uri.parse("package:$packageName"))
  }
  startSettingsActivity(intent)
}

private fun Context.startSettingsActivity(intent: Intent) {
  val safeIntent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    startActivity(safeIntent)
  } catch (_: ActivityNotFoundException) {
    startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }
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
