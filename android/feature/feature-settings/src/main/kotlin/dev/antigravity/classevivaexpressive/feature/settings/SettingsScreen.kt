package dev.antigravity.classevivaexpressive.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidColorDot
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidHeroCard
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

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
  val isChangingSchoolYear: Boolean = false,
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
  private val isChangingSchoolYear = MutableStateFlow(false)

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
    isChangingSchoolYear,
  ) { content, registry, message, refreshing, changingSchoolYear ->
    SettingsUiState(
      settings = content.settings,
      runtimeState = content.runtimeState,
      session = content.session,
      selectedSchoolYear = content.selectedSchoolYear,
      availableSchoolYears = registry.first,
      capabilities = registry.second,
      lastMessage = message,
      isRefreshing = refreshing,
      isChangingSchoolYear = changingSchoolYear,
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
    viewModelScope.launch { settingsRepository.updateCustomAccent(name) }
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
    if (isChangingSchoolYear.value) return
    isChangingSchoolYear.value = true
    lastMessage.value = null
    viewModelScope.launch {
      try {
        schoolYearRepository.selectSchoolYear(year)
        val effectiveYear = schoolYearRepository.observeSelectedSchoolYear().first()
        lastMessage.value = "Anno scolastico impostato su ${effectiveYear.label}."
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        val detail = error.message?.trim()?.takeIf(String::isNotEmpty)
        lastMessage.value = if (detail == null) {
          "Non è stato possibile cambiare anno scolastico. Riprova tra poco."
        } else {
          "Non è stato possibile cambiare anno scolastico: $detail"
        }
      } finally {
        isChangingSchoolYear.value = false
      }
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

  // AnimatedContent normally starts its pop only after BackHandler fires. A seekable transition
  // instead lets the child pane track Android's predictive-back progress while the root pane is
  // already present behind it. The logical section is cleared only after the gesture commits, so a
  // cancellation can settle back to the child without reconstructing either screen or jumping.
  val sectionTransitionState = remember {
    SeekableTransitionState<SettingsSection?>(section)
  }
  val sectionTransition = rememberTransition(
    transitionState = sectionTransitionState,
    label = "settings section transition",
  )
  val sectionSettleSpec = tween<Float>(
    durationMillis = SettingsPaneMotionDurationMillis,
    easing = FluidMotion.EaseOut,
  )

  LaunchedEffect(section) {
    if (sectionTransitionState.targetState != section) {
      sectionTransitionState.animateTo(section, animationSpec = sectionSettleSpec)
    }
  }

  PredictiveBackHandler(enabled = section != null) { progress ->
    val activeSection = section ?: return@PredictiveBackHandler
    try {
      progress.collect { backEvent ->
        sectionTransitionState.seekTo(
          fraction = clampSettingsBackProgress(backEvent.progress),
          targetState = null,
        )
      }
      sectionTransitionState.animateTo(
        null,
        animationSpec = tween(
          durationMillis = settingsPaneSettleDurationMillis(
            progress = sectionTransitionState.fraction,
            completing = true,
          ),
          easing = FluidMotion.EaseOut,
        ),
      )
      sectionName = null
    } catch (cancelled: CancellationException) {
      // The handler's coroutine is already cancelled here. NonCancellable is intentionally scoped
      // only to the short visual settle, then the cancellation is propagated as required by the
      // Activity Compose contract.
      withContext(NonCancellable) {
        sectionTransitionState.animateTo(
          activeSection,
          animationSpec = tween(
            durationMillis = settingsPaneSettleDurationMillis(
              progress = sectionTransitionState.fraction,
              completing = false,
            ),
            easing = FluidMotion.EaseOut,
          ),
        )
      }
      throw cancelled
    }
  }

  // A settings section is a real child pane. It uses the same opaque lateral stack as route
  // navigation, so a paused transition never leaves two readable pages blended together.
  sectionTransition.AnimatedContent(
    modifier = modifier.fillMaxSize(),
    transitionSpec = {
      val opening = targetState != null
      val transform = if (opening) {
        slideInHorizontally(
          animationSpec = tween(durationMillis = SettingsPaneMotionDurationMillis, easing = LinearEasing),
          initialOffsetX = { width -> settingsPaneEnterOffset(width, opening = true) },
        ) togetherWith slideOutHorizontally(
          animationSpec = tween(durationMillis = SettingsPaneMotionDurationMillis, easing = LinearEasing),
          targetOffsetX = { width -> settingsPaneExitOffset(width, opening = true) },
        )
      } else {
        slideInHorizontally(
          animationSpec = tween(durationMillis = SettingsPaneMotionDurationMillis, easing = LinearEasing),
          initialOffsetX = { width -> settingsPaneEnterOffset(width, opening = false) },
        ) togetherWith slideOutHorizontally(
          animationSpec = tween(durationMillis = SettingsPaneMotionDurationMillis, easing = LinearEasing),
          targetOffsetX = { width -> settingsPaneExitOffset(width, opening = false) },
        )
      }
      // While popping, the root must remain physically behind the travelling opaque child. Giving
      // the target a negative z-index also covers restored-process cases where the child did not
      // previously acquire the opening transition's positive z-index.
      transform.targetContentZIndex = settingsPaneTargetZIndex(opening)
      transform.using(SizeTransform(clip = true))
    },
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
          FluidHeroCard(
            title = state.session?.profile?.name?.ifBlank { "Studente" } ?: "Profilo locale",
            subtitle = listOfNotNull(
              state.session?.profile?.schoolClass?.takeIf(String::isNotBlank),
              state.selectedSchoolYear.label,
            ).joinToString(" · "),
          )
        }
        item {
          FluidListGroup(glass = true) {
            val destinations = SettingsSection.entries.filterNot { it == SettingsSection.Diagnostics }
            destinations.forEachIndexed { index, destination ->
              FluidListRow(
                title = destination.title,
                subtitle = destination.subtitle,
                tone = if (destination == SettingsSection.Notifications &&
                  state.settings.notificationsEnabled &&
                  (!state.runtimeState.permissionGranted || !state.runtimeState.appNotificationsEnabled)
                ) FluidTone.Warning else FluidTone.Neutral,
                onClick = { sectionName = destination.name },
                badge = {
                  if (destination == SettingsSection.Notifications) {
                    FluidStatusBadge(
                      if (state.settings.notificationsEnabled) "ON" else "OFF",
                      tone = if (state.settings.notificationsEnabled) FluidTone.Success else FluidTone.Neutral,
                    )
                  }
                },
              )
              if (index != destinations.lastIndex) FluidListDivider()
            }
          }
        }
      }

      if (section == SettingsSection.Account) {
        item {
          FluidHeroCard(
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
          // Gli anni si escludono a vicenda: e' un controllo segmentato, non una nuvola di filtri.
          FluidSegmentedControl(
            options = state.availableSchoolYears,
            selected = state.selectedSchoolYear,
            onSelect = viewModel::selectSchoolYear,
            enabled = !state.isRefreshing && !state.isChangingSchoolYear,
            label = { it.label },
          )
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
        val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val dynamicColorActive = dynamicColorSupported &&
          state.settings.dynamicColorEnabled &&
          state.settings.accentMode == AccentMode.DYNAMIC
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
            onSelectDynamic = { viewModel.setDynamicColor(true) },
            onSelectPreset = viewModel::setAccentPreset,
          )
        }
        item {
          SettingToggleRow(
            title = "Dynamic Color nativo",
            subtitle = if (dynamicColorSupported) {
              "Usa subito la palette del sistema; disattivandolo torna Classeviva."
            } else {
              "Richiede Android 12 o versioni successive."
            },
            checked = dynamicColorActive,
            onCheckedChange = viewModel::setDynamicColor,
            enabled = dynamicColorSupported,
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
            FluidInlineMessage(
              title = "Serve il tuo intervento",
              message = "Android sta bloccando almeno una parte delle notifiche. Apri Diagnostica avanzata per correggere lo stato.",
              tone = FluidTone.Warning,
            )
          }
        }
        item {
          FluidListGroup(glass = true) {
            FluidListRow(
              title = SettingsSection.Diagnostics.title,
              subtitle = SettingsSection.Diagnostics.subtitle,
              tone = FluidTone.Info,
              onClick = { sectionName = SettingsSection.Diagnostics.name },
            )
          }
        }
      }

      if (section == SettingsSection.Data) {
        item {
          FluidHeroCard(
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
        // Le opere di terze parti che il Fluid Engine porta dentro l'APK. L'Apache-2.0 del vetro e
        // la OFL di Inter chiedono che l'avviso viaggi con la distribuzione: un file di licenza in
        // un repository non e' la distribuzione. L'elenco vive nell'engine, quindi non puo' restare
        // indietro qui mentre e' aggiornato altrove.
        fluidLicensesSection()
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
          FluidInlineMessage(message = message, title = "Impostazioni", onDismiss = viewModel::clearMessage)
        }
      }
    }
  }
}

private const val SettingsPaneMotionDurationMillis = 360
private const val SettingsPaneMinSettleDurationMillis = 90

internal fun clampSettingsBackProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

internal fun settingsPaneEnterOffset(width: Int, opening: Boolean): Int =
  if (opening) width else -width / 4

internal fun settingsPaneExitOffset(width: Int, opening: Boolean): Int =
  if (opening) -width / 4 else width

internal fun settingsPaneTargetZIndex(opening: Boolean): Float = if (opening) 1f else -1f

internal fun settingsPaneSettleDurationMillis(progress: Float, completing: Boolean): Int {
  val boundedProgress = clampSettingsBackProgress(progress)
  val remainingFraction = if (completing) 1f - boundedProgress else boundedProgress
  return (SettingsPaneMotionDurationMillis * remainingFraction).toInt()
    .coerceIn(SettingsPaneMinSettleDurationMillis, SettingsPaneMotionDurationMillis)
}

@Composable
private fun AppUpdateSettingsCard(
  isChecking: Boolean,
  message: String?,
  onCheckForUpdates: () -> Unit,
  onClearMessage: () -> Unit,
) {
  FluidCard {
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
  FluidCard(
    highlighted = isError,
    glass = true,
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
      FluidStatusBadge(
        label = if (notificationsEnabled) "APP ON" else "APP OFF",
        tone = if (notificationsEnabled) FluidTone.Success else FluidTone.Warning,
      )
      FluidStatusBadge(
        label = if (runtimeState.permissionGranted) "PERMESSO OK" else "PERMESSO KO",
        tone = if (runtimeState.permissionGranted) FluidTone.Success else FluidTone.Warning,
      )
      FluidStatusBadge(
        label = if (runtimeState.appNotificationsEnabled) "OS ON" else "OS OFF",
        tone = if (runtimeState.appNotificationsEnabled) FluidTone.Success else FluidTone.Warning,
      )
      FluidStatusBadge(
        label = if (periodicSyncEnabled) "SYNC ON" else "SYNC OFF",
        tone = if (periodicSyncEnabled) FluidTone.Success else FluidTone.Warning,
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
  enabled: Boolean = true,
  icon: @Composable (() -> Unit)? = null,
  badge: @Composable (() -> Unit)? = null,
) {
  FluidCard(
    modifier = Modifier
      .semantics(mergeDescendants = true) {}
      .toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onCheckedChange,
      ),
    glass = true,
  ) {
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
        // The whole labelled card owns the single switch semantic/action and 48dp+ target.
        onCheckedChange = null,
        enabled = enabled,
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
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val dynamicColor = dynamicAccentColor(isDark)
  val resolvedAccentMode = effectiveAccentMode(
    settings = settings,
    dynamicColorSupported = dynamicColor != null,
  )
  FlowRow(
    modifier = Modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    FluidColorDot(
      color = classevivaBrandAccent(isDark),
      selected = resolvedAccentMode == AccentMode.BRAND,
      onClick = onSelectBrand,
      label = "Classeviva",
    )
    if (dynamicColor != null) {
      FluidColorDot(
        color = dynamicColor,
        selected = resolvedAccentMode == AccentMode.DYNAMIC,
        onClick = onSelectDynamic,
        label = "Dynamic Color",
      )
    }
    expressiveAccentPresets.forEach { preset ->
      FluidColorDot(
        color = preset.resolve(isDark),
        selected = resolvedAccentMode == AccentMode.CUSTOM_PRESET &&
          settings.customAccentName == preset.name,
        onClick = { onSelectPreset(preset.name) },
        label = preset.label,
      )
    }
  }
}

internal fun effectiveAccentMode(
  settings: AppSettings,
  dynamicColorSupported: Boolean,
): AccentMode = when {
  settings.accentMode == AccentMode.DYNAMIC &&
    (!dynamicColorSupported || !settings.dynamicColorEnabled) -> AccentMode.BRAND
  else -> settings.accentMode
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
    !capability.enabled -> FluidTone.Warning
    capability.mode == FeatureCapabilityMode.DIRECT_PORTAL -> FluidTone.Info
    capability.mode == FeatureCapabilityMode.GATEWAY -> FluidTone.Warning
    capability.mode == FeatureCapabilityMode.TENANT_OPTIONAL -> FluidTone.Neutral
    else -> FluidTone.Success
  }
  FluidListRow(
    title = capability.feature.name.replace('_', ' '),
    subtitle = capability.detail ?: "Nessun dettaglio disponibile.",
    eyebrow = capability.label.ifBlank { "Capability" },
    tone = tone,
    badge = { FluidStatusBadge(capability.mode.name.replace('_', ' '), tone = tone) },
  )
}
