package dev.antigravity.classevivaexpressive

import dev.antigravity.fluidengine.ui.fluid.FluidAmbientSurface
import dev.antigravity.fluidengine.ui.fluid.fluidReadingWidth
import dev.antigravity.fluidengine.ui.fluid.FluidColumnSection
import dev.antigravity.fluidengine.ui.fluid.FluidColumnsDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidColumns
import dev.antigravity.fluidengine.ui.fluid.rememberFluidScreenMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaDetailRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaViewModel
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsRoute
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsViewModel
import dev.antigravity.classevivaexpressive.feature.dashboard.DashboardRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DocumentsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DocumentDetailRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DocumentsViewModel
import dev.antigravity.classevivaexpressive.feature.dashboard.HomeworkRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.HomeworkDetailRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.HomeworkViewModel
import dev.antigravity.classevivaexpressive.feature.dashboard.MaterialsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.MaterialDetailRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.MaterialsViewModel
import dev.antigravity.classevivaexpressive.feature.dashboard.MeetingsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.StudentScoreRoute
import dev.antigravity.classevivaexpressive.feature.grades.GradesRoute
import dev.antigravity.classevivaexpressive.feature.grades.GradeDetailRoute
import dev.antigravity.classevivaexpressive.feature.grades.GradesViewModel
import dev.antigravity.classevivaexpressive.feature.lessons.LessonsRoute
import dev.antigravity.classevivaexpressive.feature.lessons.ProfessorsRoute
import dev.antigravity.classevivaexpressive.feature.lessons.ProfessorDetailRoute
import dev.antigravity.classevivaexpressive.feature.lessons.ProfessorsViewModel
import dev.antigravity.classevivaexpressive.feature.settings.SettingsRoute
import dev.antigravity.classevivaexpressive.feature.assistant.consent.AssistantConsentRoute
import dev.antigravity.classevivaexpressive.feature.assistant.history.AssistantConversationRoute
import dev.antigravity.classevivaexpressive.feature.assistant.history.AssistantHistoryRoute
import androidx.compose.material.icons.rounded.AutoAwesome
import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.classevivaexpressive.feature.assistant.bar.AssistantBarButton
import dev.antigravity.classevivaexpressive.feature.assistant.bar.AssistantBarButtonDefaults
import dev.antigravity.classevivaexpressive.feature.assistant.bar.AssistantBarState
import dev.antigravity.classevivaexpressive.feature.assistant.overlay.AssistantNavigator
import dev.antigravity.classevivaexpressive.feature.assistant.overlay.AssistantOverlay
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantOverlayState
import dev.antigravity.classevivaexpressive.feature.assistant.overlay.AssistantOverlayViewModel
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidIndeterminateBar
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationDelivery
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import androidx.lifecycle.viewModelScope
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateController
import dev.antigravity.fluidengine.ui.fluid.FluidUpdatePromptSheet
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidUpdateController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.navigateToPeer
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaHeroCard
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import dev.antigravity.fluidengine.ui.theme.FluidTone

private const val BugReportRepositoryOwner = "Casual76"
private const val BugReportRepositoryName = "classeviva-expressive"
private const val BugReportTemplateName = "app_bug_report.md"
internal const val BugReportRoute = "bugReport"
internal const val AssistantConsentRouteName = "assistant-consent"
internal const val AssistantHistoryRouteName = "assistant"
internal const val AssistantConversationRouteName = "assistant/{conversationId}"
private const val BugReportSourceRoute = "more"
private const val ConsumedAgendaRequestKey = "consumed-agenda-request"
private const val ConsumedGradeRequestKey = "consumed-grade-request"
private const val ConsumedHomeworkRequestKey = "consumed-homework-request"

private data class TopLevelDestination(
  val baseRoute: String,
  val navigateRoute: String,
  val label: String,
  val icon: ImageVector,
)

private val topLevelDestinations = listOf(
  TopLevelDestination("home", "home", "Home", Icons.Rounded.Home),
  TopLevelDestination("grades", "grades", "Voti", Icons.Rounded.Leaderboard),
  TopLevelDestination("agenda", "agenda", "Agenda", Icons.Rounded.CalendarMonth),
  TopLevelDestination("communications", "communications?tab=board", "Bacheca", Icons.Rounded.Campaign),
  TopLevelDestination("more", "more", "Altro", Icons.Rounded.Backpack),
)

internal val topLevelRouteOrder = topLevelDestinations.map { it.baseRoute }
internal val topLevelRoutes = topLevelRouteOrder.toSet()

@Composable
fun MainApp(
  viewModel: MainViewModel = hiltViewModel(),
  incomingIntents: Flow<Intent> = emptyFlow(),
  /** Le scorciatoie di una tastiera fisica: vedi [KeyboardShortcut]. */
  shortcuts: Flow<KeyboardShortcut> = emptyFlow(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // L'aggiornamento dall'app: un controllore solo, alla radice, cosi' il foglio (qui) e la sezione delle
  // impostazioni (piu' in basso) parlano dello stesso download. Lo scope e' quello del ViewModel: un
  // download non si ferma cambiando pagina.
  val update = rememberFluidUpdateController(
    updater = viewModel.updater,
    scope = viewModel.viewModelScope,
    currentVersion = BuildConfig.VERSION_NAME.substringBefore("-"),
    ignoredVersion = uiState.settings.ignoredStableUpdateVersion,
    checkOnStart = true,
  )
  // "Piu' tardi": il foglio si chiude per questa sessione, e torna con un controllo nuovo (a mano,
  // dalle impostazioni) che trova ancora una versione.
  var updatePromptDismissed by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(update.state) {
    if (update.state is FluidUpdateUiState.Checking) updatePromptDismissed = false
  }
  val notificationHostState = rememberFluidNotificationHostState()
  val glassModalHostState = rememberFluidGlassModalHostState()
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.onAppResumed()
  }

  LaunchedEffect(viewModel, notificationHostState) {
    viewModel.inAppNotifications.collect { notification ->
      // A queued item is still durable: rotation or process death can discard the in-memory FIFO.
      // Remove it from DataStore only after the card has really been laid out or consumed.
      val delivery = notificationHostState.show(notification)
      if (delivery != FluidNotificationDelivery.Rejected) {
        viewModel.acknowledgeInAppNotification(notification.id)
      }
    }
  }

  val context = LocalContext.current
  var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = viewModel::onNotificationPermissionResult,
  )
  LaunchedEffect(
    uiState.isLoading,
    uiState.session?.studentId,
    uiState.settings.notificationPreferences.enabled,
    notificationPermissionRequested,
  ) {
    val shouldRequestPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !uiState.isLoading &&
      uiState.session != null &&
      uiState.settings.notificationPreferences.enabled &&
      !notificationPermissionRequested &&
      context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
      android.content.pm.PackageManager.PERMISSION_GRANTED

    if (shouldRequestPermission) {
      notificationPermissionRequested = true
      notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  ClassevivaExpressiveTheme(settings = uiState.settings) {
    val chromeController = rememberFluidChromeController()
    LaunchedEffect(chromeController, shortcuts) {
      shortcuts.collect { if (it == KeyboardShortcut.Refresh) chromeController.refreshFront() }
    }
    CompositionLocalProvider(
      LocalFluidNotificationHostState provides notificationHostState,
      LocalFluidGlassModalHostState provides glassModalHostState,
    ) {
      FluidScreenSurface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
          // The page under an open glass modal stays visible — that is the point of the material —
          // so it has to be taken away from accessibility by hand, or TalkBack walks through the
          // scrim into content the eye can plainly see is behind something.
          Box(modifier = Modifier.fillMaxSize().fluidGlassModalObscured()) {
            when {
              uiState.isLoading -> LoadingScreen()
              uiState.session == null -> LoginScreen(
                isLoading = uiState.isAuthenticating,
                error = uiState.authError,
                onClearError = viewModel::clearAuthError,
                onLogin = viewModel::login,
              )
              else -> AuthenticatedApp(
                chromeController = chromeController,
                update = update,
                onSkipUpdate = { version ->
                  viewModel.ignoreUpdateVersion(version)
                  update.reset()
                },
                incomingIntents = incomingIntents,
                shortcuts = shortcuts,
              )
            }
          }
          // Above the tab bar — the reason the modal lives in-root at all — and below the
          // notifications, which outrank everything.
          FluidGlassModalHost(
            state = glassModalHostState,
            backdrop = chromeController.activeBackdrop.value,
          )
          FluidNotificationHost(
            state = notificationHostState,
            backdrop = chromeController.activeBackdrop.value,
            modifier = Modifier.align(Alignment.TopCenter),
          )
          FluidUpdatePromptSheet(
            state = update.state.takeUnless { updatePromptDismissed },
            onInstall = update::install,
            onLater = { updatePromptDismissed = true },
            onSkip = {
              update.available?.version?.let { version ->
                viewModel.ignoreUpdateVersion(version)
                update.reset()
              }
              updatePromptDismissed = true
            },
          )
        }
      }
    }
  }
}

private fun buildBugReportIssueUri(
  title: String,
  body: String,
): Uri {
  val issueTitle = "[Bug] ${title.trim().ifBlank { "Segnalazione app" }.take(90)}"
  return Uri.Builder()
    .scheme("https")
    .authority("github.com")
    .appendPath(BugReportRepositoryOwner)
    .appendPath(BugReportRepositoryName)
    .appendPath("issues")
    .appendPath("new")
    .appendQueryParameter("template", BugReportTemplateName)
    .appendQueryParameter("labels", "bug,app-report")
    .appendQueryParameter("title", issueTitle)
    .appendQueryParameter("body", body)
    .build()
}

private fun reportValue(value: String): String {
  return value.trim().ifBlank { "_Non specificato._" }
}

private fun Context.appVersionLabel(): String {
  return runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      info.longVersionCode
    } else {
      @Suppress("DEPRECATION")
      info.versionCode.toLong()
    }
    "${info.versionName ?: BuildConfig.VERSION_NAME} ($versionCode)"
  }.getOrElse {
    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
  }
}

private fun Context.copyBugReport(report: String) {
  val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(
    ClipData.newPlainText("Classeviva Expressive bug report", report),
  )
}

@Composable
private fun LoadingScreen() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    FluidLoading()
  }
}

@Composable
internal fun LoginScreen(
  isLoading: Boolean,
  error: String?,
  onClearError: () -> Unit,
  onLogin: (String, String) -> Unit,
) {
  var username by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }

  fun submit() {
    if (username.isNotBlank() && password.isNotBlank() && !isLoading) {
      onLogin(username.trim(), password)
    }
  }

  val systemBars = WindowInsets.systemBars.asPaddingValues()
  // La prima pagina che si vede ha lo stesso fondale di quella che viene dopo, invece del grigio
  // piatto; e su uno schermo largo il modulo e' una colonna in mezzo, non due campi lunghi quanto
  // la finestra.
  FluidAmbientSurface(ambient = FeatureIdentity.Overview.ambient()) {
  LazyColumn(
    modifier = Modifier.fillMaxSize().fluidReadingWidth(max = LoginMaxWidth),
    contentPadding = PaddingValues(
      start = 24.dp,
      end = 24.dp,
      top = systemBars.calculateTopPadding() + 28.dp,
      bottom = systemBars.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
  ) {
    item {
      ClassevivaHeroCard(
        title = "Classeviva Expressive",
        subtitle = "Accedi con le credenziali del registro Classeviva.",
        trailing = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
      )
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FluidTextField(
          value = username,
          onValueChange = {
            username = it
            if (error != null) onClearError()
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_username")
            .contentType(ContentType.Username + ContentType.EmailAddress),
          label = "Username o codice studente",
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
          ),
        )
        FluidTextField(
          value = password,
          onValueChange = {
            password = it
            if (error != null) onClearError()
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password")
            .contentType(ContentType.Password),
          label = "Password",
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(onDone = { submit() }),
          secret = true,
        )
        if (error != null) {
          Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        FluidButton(
          text = "Accedi",
          onClick = ::submit,
          modifier = Modifier.testTag("login_submit"),
          style = FluidButtonStyle.Filled,
          enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
          loading = isLoading,
          fillWidth = true,
        )
      }
    }
  }
  }
}

/** Quanto e' largo il modulo di accesso su uno schermo largo: quello di un telefono comodo. */
private val LoginMaxWidth = 480.dp

/**
 * The app shell: screen content edge to edge, with a floating tab bar over it.
 *
 * The bar no longer reserves layout space. It used to consume a hard 92dp strip at the bottom of
 * every screen, which is why text ran into an invisible wall near the bottom of a list. Now content
 * occupies the whole display and simply pads its *scroll* by the bar's height, so the last item can
 * be scrolled clear of the bar while everything in between passes underneath it through the glass.
 *
 * **La barra si ripiega, non sparisce.** Scivolare fuori dallo schermo toglie alla pagina il suo
 * unico punto fermo proprio mentre si muove, e per riaverla bisogna scorrere all'indietro: una
 * navigazione che si nasconde quando la stai usando. Ripiegata resta — si raccoglie sulla scheda in
 * cui sei, al centro, e un tocco la riapre.
 */
@Composable
internal fun TopLevelNavigationSuite(
  currentRoute: String?,
  showNavigationSuite: Boolean,
  onNavigateRoute: (String) -> Unit,
  onReselectRoute: (String) -> Unit = {},
  scrollToTop: FluidScrollToTopBus = remember { FluidScrollToTopBus() },
  chromeController: FluidChromeController = rememberFluidChromeController(),
  /**
   * Quanto la barra e' uscita con la pagina: 0 dove c'e', 1 dove non c'e' (una pagina di dettaglio),
   * in mezzo mentre le pagine si muovono, anche sotto il dito di un back. Si legge in disegno
   * (`FluidPageHostState.fractionToward`), mai in composizione.
   */
  barHidden: () -> Float = { if (showNavigationSuite) 0f else 1f },
  /** Il tasto "Chiedi all'AI" sopra la pillola; null quando l'assistente e' spento. */
  assistant: AssistantBarState? = null,
  /** L'overlay dell'assistente: sopra la pagina e la barra, sotto i modali della radice. */
  overlay: @Composable BoxScope.(GlassBackdropState) -> Unit = {},
  content: @Composable () -> Unit,
) {
  val fallbackBackdrop = rememberGlassBackdrop()
  // La barra si ripiega da sola quando la pagina scorre davvero (e si riapre a ogni cambio di pagina o
  // di scheda): la connessione e' quello che la pagina attraversa scorrendo. Il tasto dell'assistente
  // vuole un numero fra i due stati, e lo ricava qui.
  val barScroll = rememberFluidFloatingTabBarScrollConnection()
  val barFold = animateFloatAsState(
    targetValue = if (barScroll.isInline) 1f else 0f,
    animationSpec = FluidMotion.standard(),
    label = "barFold",
  )
  val chromeScrollConnection = rememberFluidChromeScrollConnection(
    controller = chromeController,
    enabled = showNavigationSuite,
  )
  val tabItems = remember {
    topLevelDestinations.map { FluidTabItem(it.baseRoute, it.label, it.icon) }
  }
  val routeOf = remember {
    topLevelDestinations.associate { it.baseRoute to it.navigateRoute }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .nestedScroll(chromeScrollConnection)
      .then(if (showNavigationSuite) Modifier.nestedScroll(barScroll) else Modifier),
  ) {
    // Una navigazione sola, in basso, su ogni formato. Il rail laterale del tablet e' stato
    // provato e tolto: era una colonna alta un pollice con quattro voci in cima, il suo fondale
    // non coincideva mai con quello della pagina, e soprattutto era una *seconda* navigazione da
    // imparare per la stessa app. La barra ripiegabile fa le stesse cose meglio, e sul tablet il
    // pollice sta comunque in basso.
    // Col tasto dell'assistente sopra la pillola, la pagina lascia libero anche il suo spazio.
    val bottomInset = when {
      !showNavigationSuite -> 0.dp
      assistant != null -> FluidFloatingTabBarDefaults.ContentInset + AssistantBarButtonDefaults.ContentInset
      else -> FluidFloatingTabBarDefaults.ContentInset
    }
    val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop

    ProvideFluidChrome(
      controller = chromeController,
      bottomInset = bottomInset,
      scrollToTop = scrollToTop,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        content()
      }
    }

    val onSelect: (FluidTabItem) -> Unit = { item ->
      onNavigateRoute(routeOf[item.route] ?: item.route)
    }
    val onReselect: (FluidTabItem) -> Unit = { item -> onReselectRoute(item.route) }

    Box(
      modifier = Modifier
        .align(if (maxWidth >= 600.dp) Alignment.BottomStart else Alignment.BottomCenter)
        // La barra non viaggia con la pagina: sta ferma e, dove la pagina che arriva non la vuole,
        // scende fuori dallo schermo al passo delle pagine (e sotto il dito di un back).
        .graphicsLayer { translationY = barHidden() * size.height }
        // Su uno schermo largo la barra non si stira da bordo a bordo: cinque schede spalmate su
        // ventisette centimetri sono bersagli a mezzo schermo l'uno dall'altro. Resta larga come
        // su un telefono e sta a sinistra, dalla parte del pollice — la stessa parte dove poi si
        // raccoglie ripiegandosi.
        .widthIn(max = 500.dp)
        .navigationBarsPadding()
        .padding(
          horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
          vertical = FluidFloatingTabBarDefaults.BottomMargin,
        ),
    ) {
      AssistantAboveBar(fold = { barFold.value }, assistant = assistant, backdrop = backdrop) {
        FluidFloatingTabBar(
          items = tabItems,
          selectedRoute = currentRoute,
          onSelect = onSelect,
          onReselect = onReselect,
          scrollConnection = barScroll,
          backdrop = backdrop,
          foldAlignment = Alignment.Start,
        )
      }
    }

    overlay(backdrop)
  }
}

/**
 * La pillola e, sopra di lei, il tasto dell'assistente: un solo numero (la piega) muove entrambi.
 * Aperta, il tasto e' una capsula centrata sopra la barra; mentre la barra si ripiega a sinistra
 * il tasto **sparisce** — sfuma e scende verso la pillola che si chiude — e ritorna con la
 * riapertura. Lo spazio che occupa resta lo stesso, cosi' la pagina non salta. La piega la legge
 * il tasto stesso in disegno: nessuna ricomposizione per fotogramma.
 */
@Composable
private fun AssistantAboveBar(
  fold: () -> Float,
  assistant: AssistantBarState?,
  backdrop: GlassBackdropState,
  bar: @Composable () -> Unit,
) {
  val spacingPx = with(LocalDensity.current) { AssistantBarButtonDefaults.Spacing.roundToPx() }
  Layout(
    content = {
      Box(modifier = Modifier.layoutId(SlotBar)) { bar() }
      if (assistant != null) {
        Box(modifier = Modifier.layoutId(SlotAssistant)) {
          AssistantBarButton(
            fold = fold,
            working = assistant.working,
            backdrop = backdrop,
            onTap = assistant.onTap,
            onLongPress = assistant.onLongPress,
            onBounds = assistant.onBounds,
          )
        }
      }
    },
  ) { measurables, constraints ->
    val barPlaceable = measurables.first { it.layoutId == SlotBar }.measure(constraints)
    val button = measurables.firstOrNull { it.layoutId == SlotAssistant }?.measure(Constraints())
    val gap = if (button != null) spacingPx else 0
    val height = barPlaceable.height + (button?.height?.plus(gap) ?: 0)
    layout(barPlaceable.width, height) {
      button?.let { it.place((barPlaceable.width - it.width) / 2, 0) }
      barPlaceable.place(0, height - barPlaceable.height)
    }
  }
}

private const val SlotBar = "shell:bar"
private const val SlotAssistant = "shell:assistant"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BugReportScreen(
  currentRoute: String,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var steps by rememberSaveable { mutableStateOf("") }
  var expected by rememberSaveable { mutableStateOf("") }
  var actual by rememberSaveable { mutableStateOf("") }
  var showAdvanced by rememberSaveable { mutableStateOf(false) }
  var copied by rememberSaveable { mutableStateOf(false) }
  var diagnostics by rememberSaveable(currentRoute) {
    mutableStateOf(
      "App: ${context.appVersionLabel()}\nSDK: ${Build.VERSION.SDK_INT}\nSchermata: ${normalizeRoute(currentRoute) ?: "sconosciuta"}",
    )
  }
  val reportBody = remember(description, steps, expected, actual, diagnostics) {
    buildMinimalBugReportBody(description, steps, expected, actual, diagnostics)
  }
  val issueUri = remember(title, reportBody) { buildBugReportIssueUri(title, reportBody) }

  FluidScreen(
    title = "Segnala un problema",
    subtitle = "Controlla cosa verrà condiviso prima di aprire GitHub.",
    // Il fondale di "Altro", da cui si arriva: era l'unica pagina grigia dell'app insieme al login.
    ambient = FeatureIdentity.Settings.ambient(),
    onBack = onBack,
    itemSpacing = 12.dp,
  ) {
    item {
      FluidInlineMessage(
        message = "La segnalazione sarà pubblica su GitHub e attribuita all'account GitHub con cui la invii. Non è anonima: non inserire credenziali o dati scolastici personali.",
        tone = FluidTone.Warning,
        leading = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
      )
    }
    item {
      FluidTextField(
        value = title,
        onValueChange = { title = it; copied = false },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("bug_report_title"),
        label = "Titolo",
        singleLine = true,
      )
    }
    item {
      FluidTextField(
        value = description,
        onValueChange = { description = it; copied = false },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("bug_report_description"),
        label = "Cosa non ha funzionato?",
        minLines = 4,
      )
    }
    item {
      FluidButton(
        text = if (showAdvanced) "Nascondi dettagli" else "Aggiungi passaggi e diagnostica",
        onClick = { showAdvanced = !showAdvanced },
        style = FluidButtonStyle.Plain,
      )
    }
    if (showAdvanced) {
      item { BugReportField("Passaggi per riprodurre", steps) { steps = it; copied = false } }
      item { BugReportField("Comportamento atteso", expected) { expected = it; copied = false } }
      item { BugReportField("Comportamento ottenuto", actual) { actual = it; copied = false } }
      item {
        BugReportField(
          label = "Diagnostica inclusa (modificabile)",
          value = diagnostics,
          onValueChange = { diagnostics = it; copied = false },
          modifier = Modifier.testTag("bug_report_diagnostics"),
        )
      }
    }
    if (copied) {
      item { Text("Segnalazione copiata negli appunti.", color = MaterialTheme.colorScheme.primary) }
    }
    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = "Copia",
          onClick = { context.copyBugReport(reportBody); copied = true },
          modifier = Modifier.weight(1f),
          style = FluidButtonStyle.Tinted,
          leading = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
        )
        FluidButton(
          text = "Apri GitHub",
          onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, issueUri))
            onBack()
          },
          modifier = Modifier.weight(1f),
          style = FluidButtonStyle.Filled,
          enabled = title.isNotBlank() && description.isNotBlank(),
          leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
        )
      }
    }
  }
}

@Composable
private fun BugReportField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  onValueChange: (String) -> Unit,
) {
  FluidTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = label,
    minLines = 3,
  )
}

private fun buildMinimalBugReportBody(
  description: String,
  steps: String,
  expected: String,
  actual: String,
  diagnostics: String,
): String = """
  ## Descrizione
  ${reportValue(description)}

  ## Passaggi per riprodurre
  ${reportValue(steps)}

  ## Comportamento atteso
  ${reportValue(expected)}

  ## Comportamento ottenuto
  ${reportValue(actual)}

  ## Diagnostica inclusa
  ${reportValue(diagnostics)}
""".trimIndent()

@Preview(name = "Login Light", showBackground = true)
@Preview(
  name = "Login Dark",
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LoginScreenPreview() {
  ClassevivaExpressiveTheme(settings = AppSettings()) {
    FluidScreenSurface {
      LoginScreen(
        isLoading = false,
        error = null,
        onClearError = {},
        onLogin = { _, _ -> },
      )
    }
  }
}

@Preview(name = "Compact 360, font 2x", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Adaptive Shell", widthDp = 900, heightDp = 700, showBackground = true)
@Composable
private fun TopLevelNavigationSuitePreview() {
  ClassevivaExpressiveTheme(settings = AppSettings()) {
    FluidScreenSurface {
      val chromeController = rememberFluidChromeController()
      TopLevelNavigationSuite(
        currentRoute = "more",
        showNavigationSuite = true,
        onNavigateRoute = {},
        chromeController = chromeController,
      ) {
        MoreHubScreen(
          onOpenBugReport = {},
          onOpenLessons = {},
          onOpenAbsences = {},
          onOpenMaterials = {},
          onOpenSettings = {},
          onOpenNotes = {},
          onOpenHomework = {},
          onOpenDocuments = {},
          onOpenProfessors = {},
          onOpenMeetings = {},
        )
      }
    }
  }
}

internal fun NavHostController.handleIncomingIntent(intent: Intent): Boolean = handleDeepLink(intent)

internal fun pendingGradeRequest(
  requestedGradeId: String?,
  consumedGradeId: String?,
): String? = requestedGradeId
  ?.takeIf(String::isNotBlank)
  ?.takeUnless { it == consumedGradeId }

internal fun pendingAgendaRequest(
  requestedAgendaId: String?,
  consumedAgendaId: String?,
): String? = requestedAgendaId
  ?.takeIf(String::isNotBlank)
  ?.takeUnless { it == consumedAgendaId }

internal fun pendingHomeworkRequest(
  requestedHomeworkId: String?,
  consumedHomeworkId: String?,
): String? = requestedHomeworkId
  ?.takeIf(String::isNotBlank)
  ?.takeUnless { it == consumedHomeworkId }

@Composable
private fun AuthenticatedApp(
  chromeController: FluidChromeController,
  update: FluidUpdateController,
  onSkipUpdate: (String) -> Unit,
  incomingIntents: Flow<Intent>,
  shortcuts: Flow<KeyboardShortcut> = emptyFlow(),
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val currentRoute = currentDestination?.route?.substringBefore("?")
  val showNavigationSuite = currentRoute in topLevelRoutes

  val scrollToTop = remember { FluidScrollToTopBus() }
  // Lo stato dell'host delle pagine: la barra lo legge per uscire con la pagina di dettaglio.
  val pages = rememberFluidPageHostState()
  val barHidden: () -> Float = {
    pages.fractionToward { key ->
      val route = (key as? NavBackStackEntry)?.destination?.route?.substringBefore("?")
      route != null && route !in topLevelRoutes
    }
  }
  val peerMove = remember {
    FluidPeerMove<NavBackStackEntry> { from, to ->
      peerDirection(normalizeRoute(from.destination.route), normalizeRoute(to.destination.route))
    }
  }

  fun navigateRoute(route: String) {
    navController.navigate(route)
  }

  /**
   * Passare a una sezione della barra, che sia dalla barra o toccando qualcosa sulla pagina (la
   * media di Home che apre Voti): una pila sola, il passo laterale delle schede.
   */
  fun navigateTopLevelRoute(targetRoute: String) {
    navController.navigateToPeer(targetRoute)
  }

  // Indietro da una sezione riporta a Home, come ha sempre fatto l'app; da Home esce. La pila delle
  // schede e' una sola (`navigateToPeer`): senza questo un indietro da Voti uscirebbe dall'app.
  BackHandler(enabled = currentRoute in topLevelRoutes && currentRoute != "home") {
    navController.navigateToPeer("home")
  }

  // Ctrl+1…5: le cinque sezioni della barra, nell'ordine in cui stanno — come le schede di un
  // browser. Sulla sezione in cui si e' gia', come toccare di nuovo la sua scheda: torna in cima.
  LaunchedEffect(navController, shortcuts) {
    shortcuts.collect { shortcut ->
      val index = (shortcut as? KeyboardShortcut.Section)?.index ?: return@collect
      val destination = topLevelDestinations.getOrNull(index) ?: return@collect
      val current = navController.currentDestination?.route?.substringBefore("?")?.takeIf { it in topLevelRoutes }
      if (current == destination.baseRoute) scrollToTop.request() else navigateTopLevelRoute(destination.navigateRoute)
    }
  }

  LaunchedEffect(navController, incomingIntents) {
    incomingIntents.collect { intent ->
      navController.handleIncomingIntent(intent)
    }
  }

  // L'assistente: lo stato dell'overlay vive qui, sopra la navigazione, cosi' il tasto nella
  // barra e la card che ne nasce parlano dello stesso rettangolo.
  val assistantOverlay = remember { FluidAssistantOverlayState() }
  val assistantViewModel: AssistantOverlayViewModel = hiltViewModel()
  val assistantEnabled by assistantViewModel.enabled.collectAsStateWithLifecycle()
  val assistantState by assistantViewModel.state.collectAsStateWithLifecycle()

  /** Dove porta una pagina chiesta dall'assistente: le sezioni principali si aprono, le altre si spingono. */
  fun openAssistantPage(page: AppPage, itemId: String?) {
    when (page) {
      AppPage.HOME -> navigateTopLevelRoute("home")
      AppPage.VOTI -> if (itemId != null) navigateRoute("grades?gradeId=${Uri.encode(itemId)}") else navigateTopLevelRoute("grades")
      AppPage.AGENDA -> navigateTopLevelRoute("agenda")
      AppPage.BACHECA -> navigateTopLevelRoute("communications?tab=board")
      AppPage.ORARIO -> navigateRoute("lessons")
      AppPage.COMPITI -> navigateRoute(if (itemId != null) "homework?homeworkId=${Uri.encode(itemId)}" else "homework")
      AppPage.ASSENZE -> navigateRoute("absences")
      AppPage.NOTE -> navigateRoute("notes")
      AppPage.DIDATTICA -> navigateRoute("materials")
      AppPage.DOCUMENTI -> navigateRoute("documents")
      AppPage.PROFESSORI -> navigateRoute("professors")
      AppPage.PUNTEGGIO -> navigateRoute("studentScore")
      AppPage.IMPOSTAZIONI -> navigateRoute("settings")
      AppPage.ASSISTENTE -> navigateRoute(AssistantHistoryRouteName)
    }
  }

  // The policy wraps the whole shell: top-level chrome is motion too, not only route content.
  // Keeping the provider inside the NavHost left the floating tab indicator animating at 0x.
  FluidMotionPolicyProvider {
    TopLevelNavigationSuite(
      currentRoute = currentRoute?.takeIf { it in topLevelRoutes },
      showNavigationSuite = showNavigationSuite,
      barHidden = barHidden,
      onNavigateRoute = { targetRoute ->
        navigateTopLevelRoute(targetRoute)
      },
      onReselectRoute = { scrollToTop.request() },
      scrollToTop = scrollToTop,
      chromeController = chromeController,
      assistant = if (assistantEnabled) {
        AssistantBarState(
          working = assistantState.isBusy,
          onTap = { assistantOverlay.openVoice() },
          onLongPress = { assistantOverlay.openText() },
          onBounds = { assistantOverlay.originBounds = it },
        )
      } else {
        null
      },
      overlay = { backdrop ->
        if (assistantEnabled) {
          AssistantOverlay(
            overlay = assistantOverlay,
            viewModel = assistantViewModel,
            backdrop = backdrop,
            navigator = AssistantNavigator { page, itemId -> openAssistantPage(page, itemId) },
          )
        }
      },
    ) {
      FluidNavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize(),
        peer = peerMove,
        state = pages,
      ) {
        composable("home") { entry ->
          DashboardRoute(
            onNavigateGrades = { navigateTopLevelRoute("grades") },
            onNavigateAgenda = { navigateTopLevelRoute("agenda") },
            onNavigateLessons = { navigateRoute("lessons") },
            onNavigateCommunications = {
              navigateTopLevelRoute("communications?tab=board")
            },
            onOpenGrade = { gradeId -> navigateTopLevelRoute("grades?gradeId=$gradeId") },
          )
        }
        composable(
          route = "agenda?agendaId={agendaId}&date={date}",
          arguments = listOf(
            navArgument("agendaId") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
            navArgument("date") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
          ),
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/agenda?agendaId={agendaId}&date={date}" },
            navDeepLink { uriPattern = "classevivaexpressive://open/agenda?date={date}" },
            navDeepLink { uriPattern = "classevivaexpressive://open/agenda" },
          ),
        ) { entry ->
          val requestedEntryId = entry.arguments?.getString("agendaId")
          val consumedEntryId by entry.savedStateHandle
            .getStateFlow<String?>(ConsumedAgendaRequestKey, null)
            .collectAsStateWithLifecycle()
          val pendingEntryId = pendingAgendaRequest(requestedEntryId, consumedEntryId)
          LaunchedEffect(pendingEntryId) {
            val entryId = pendingEntryId ?: return@LaunchedEffect
            entry.savedStateHandle[ConsumedAgendaRequestKey] = entryId
            // A cold/deep-link request has no composed source element: use the normal Push policy.
            navigateRoute(route = "agenda-detail/${Uri.encode(entryId)}")
          }
          AgendaRoute(
            initialAgendaId = null,
            initialDate = entry.arguments?.getString("date"),
            onOpenEntry = { entryId ->
              navigateRoute("agenda-detail/${Uri.encode(entryId)}")
            },
          )
        }
        composable(
          route = "agenda-detail/{entryId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val agendaViewModel: AgendaViewModel = hiltViewModel(parentEntry)
          AgendaDetailRoute(
            entryId = entry.arguments?.getString("entryId").orEmpty(),
            onBack = { navController.popBackStack() },
            viewModel = agendaViewModel,
          )
        }
        composable(
          route = "grades?gradeId={gradeId}",
          arguments = listOf(
            navArgument("gradeId") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
          ),
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/grades?gradeId={gradeId}" },
            navDeepLink { uriPattern = "classevivaexpressive://open/grades" },
          ),
        ) { entry ->
          val requestedGradeId = entry.arguments?.getString("gradeId")
          val consumedGradeId by entry.savedStateHandle
            .getStateFlow<String?>(ConsumedGradeRequestKey, null)
            .collectAsStateWithLifecycle()
          // Niente `onOpenGrade`: senza, la schermata apre il proprio pop-up invece di
          // navigare a una pagina intera. Per guardare un numero e tornare indietro, un viaggio
          // di rotta era sproporzionato — e il pop-up esisteva gia', semplicemente non veniva
          // mai raggiunto. La rotta `grade/{gradeId}` resta per i collegamenti dalle notifiche.
          GradesRoute(
            initialGradeId = pendingGradeRequest(requestedGradeId, consumedGradeId),
            onInitialGradeConsumed = { gradeId ->
              entry.savedStateHandle[ConsumedGradeRequestKey] = gradeId
            },
          )
        }
        composable(
          route = "grade/{gradeId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(
            navArgument("gradeId") { type = NavType.StringType },
          ),
        ) { entry ->
          val gradesBackStackEntry = remember(entry) {
            navController.getBackStackEntry("grades?gradeId={gradeId}")
          }
          val gradesViewModel: GradesViewModel = hiltViewModel(gradesBackStackEntry)
          GradeDetailRoute(
            gradeId = entry.arguments?.getString("gradeId").orEmpty(),
            onBack = { navController.popBackStack() },
            viewModel = gradesViewModel,
          )
        }
        composable(
          route = "communications?tab={tab}&pubId={pubId}&evtCode={evtCode}&noteId={noteId}&categoryCode={categoryCode}",
          arguments = listOf(
            navArgument("tab") {
              defaultValue = "board"
              type = NavType.StringType
            },
            navArgument("pubId") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
            navArgument("evtCode") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
            navArgument("noteId") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
            navArgument("categoryCode") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
          ),
          deepLinks = listOf(
            navDeepLink {
              uriPattern = "classevivaexpressive://open/communications?tab={tab}&pubId={pubId}&evtCode={evtCode}"
            },
            navDeepLink { uriPattern = "classevivaexpressive://open/communications?tab={tab}" },
            navDeepLink {
              uriPattern = "classevivaexpressive://open/notes?noteId={noteId}&categoryCode={categoryCode}"
            },
            navDeepLink { uriPattern = "classevivaexpressive://open/notes" },
          ),
        ) { entry ->
          // Il dettaglio non e' piu' una rotta: e' il pop-up di vetro dichiarato dentro
          // CommunicationsRoute e disegnato alla radice. I deep link continuano ad arrivare qui
          // con gli stessi argomenti, che ora aprono la tab e poi il modale.
          CommunicationsRoute(
            initialTab = entry.arguments?.getString("tab") ?: "board",
            initialCommunicationPubId = entry.arguments?.getString("pubId"),
            initialCommunicationEvtCode = entry.arguments?.getString("evtCode"),
            initialNoteId = entry.arguments?.getString("noteId"),
            initialNoteCategoryCode = entry.arguments?.getString("categoryCode"),
            onBack = if (currentRoute == "communications") null else { { navController.navigateUp() } },
          )
        }
        composable("notes", transition = FluidPageTransition.Expand) { entry ->
          CommunicationsRoute(
            initialTab = "notes",
            onBack = navController::navigateUp,
          )
        }
        composable("more") { entry ->
          MoreHubScreen(
            onOpenBugReport = { navigateRoute(BugReportRoute) },
            onOpenNotes = { navigateRoute("notes") },
            onOpenLessons = { navigateRoute("lessons") },
            onOpenAbsences = { navigateRoute("absences") },
            onOpenMaterials = { navigateRoute("materials") },
            onOpenSettings = { navigateRoute("settings") },
            onOpenHomework = { navigateRoute("homework") },
            onOpenDocuments = { navigateRoute("documents") },
            onOpenProfessors = { navigateRoute("professors") },
            onOpenMeetings = { navigateRoute("meetings") },
            onOpenAssistant = if (assistantEnabled) {
              { navigateRoute(AssistantHistoryRouteName) }
            } else {
              null
            },
          )
        }
        composable(BugReportRoute, transition = FluidPageTransition.Expand) { entry ->
          // Keep the route that opened the form in the diagnostics. Reading currentRoute here would
          // report "bugReport", because this is now (correctly) its own back-stack destination.
          BugReportScreen(
            currentRoute = BugReportSourceRoute,
            onBack = navController::navigateUp,
          )
        }
        composable("materials", transition = FluidPageTransition.Expand) { entry ->
          val materialsViewModel: MaterialsViewModel = hiltViewModel()
          val paneRequest by entry.savedStateHandle
            .getStateFlow<String?>(PaneOpenRequestKey, null)
            .collectAsStateWithLifecycle()
          AdaptiveListDetail(
            openRequest = paneRequest,
            onOpenRequestConsumed = { entry.savedStateHandle[PaneOpenRequestKey] = null },
            ambient = FeatureIdentity.Materials.ambient(),
            emptyTitle = "Nessun materiale aperto",
            emptyMessage = "Scegli un file o un link dall'elenco per vederlo qui.",
            emptyIcon = Icons.Rounded.FolderCopy,
            onOpenPage = { itemId -> navigateRoute("material-detail/${Uri.encode(itemId)}") },
            list = { inPane, selectedId, onOpen ->
              MaterialsRoute(
                onBack = navController::navigateUp,
                onOpenMaterial = onOpen,
                selectedId = selectedId,
                inPane = inPane,
                viewModel = materialsViewModel,
              )
            },
            detail = { itemId, onClose ->
              MaterialDetailRoute(itemId = itemId, onBack = null, viewModel = materialsViewModel)
            },
          )
        }
        composable(
          route = "material-detail/{itemId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val materialsViewModel: MaterialsViewModel = hiltViewModel(parentEntry)
          val detailId = entry.arguments?.getString("itemId").orEmpty()
          PaneAwareDetail(
            id = detailId,
            onWide = { id ->
              navController.previousBackStackEntry?.savedStateHandle?.set(PaneOpenRequestKey, id)
              navController.popBackStack()
            },
          ) {
            MaterialDetailRoute(
              itemId = entry.arguments?.getString("itemId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = materialsViewModel,
            )
          }
        }
        composable(
          route = "homework?homeworkId={homeworkId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(
            navArgument("homeworkId") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
          ),
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/homework?homeworkId={homeworkId}" },
            navDeepLink { uriPattern = "classevivaexpressive://open/homework" },
          ),
        ) { entry ->
          val requestedHomeworkId = entry.arguments?.getString("homeworkId")
          val consumedHomeworkId by entry.savedStateHandle
            .getStateFlow<String?>(ConsumedHomeworkRequestKey, null)
            .collectAsStateWithLifecycle()
          val pendingHomeworkId = pendingHomeworkRequest(requestedHomeworkId, consumedHomeworkId)
          val homeworkViewModel: HomeworkViewModel = hiltViewModel()
          val paneRequest by entry.savedStateHandle
            .getStateFlow<String?>(PaneOpenRequestKey, null)
            .collectAsStateWithLifecycle()
          AdaptiveListDetail(
            ambient = FeatureIdentity.Homework.ambient(),
            emptyTitle = "Nessun compito aperto",
            emptyMessage = "Scegli un compito dall'elenco per leggerne consegna e scadenza qui.",
            emptyIcon = Icons.AutoMirrored.Rounded.Assignment,
            onOpenPage = { homeworkId -> navigateRoute("homework-detail/${Uri.encode(homeworkId)}") },
            openRequest = paneRequest ?: pendingHomeworkId,
            onOpenRequestConsumed = { homeworkId ->
              if (homeworkId == paneRequest) {
                entry.savedStateHandle[PaneOpenRequestKey] = null
              } else {
                entry.savedStateHandle[ConsumedHomeworkRequestKey] = homeworkId
              }
            },
            list = { inPane, selectedId, onOpen ->
              HomeworkRoute(
                initialHomeworkId = null,
                onBack = navController::navigateUp,
                onOpenHomework = onOpen,
                selectedId = selectedId,
                inPane = inPane,
                viewModel = homeworkViewModel,
              )
            },
            detail = { homeworkId, onClose ->
              HomeworkDetailRoute(homeworkId = homeworkId, onBack = null, viewModel = homeworkViewModel)
            },
          )
        }
        composable(
          route = "homework-detail/{homeworkId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(navArgument("homeworkId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val homeworkViewModel: HomeworkViewModel = hiltViewModel(parentEntry)
          val detailId = entry.arguments?.getString("homeworkId").orEmpty()
          PaneAwareDetail(
            id = detailId,
            onWide = { id ->
              navController.previousBackStackEntry?.savedStateHandle?.set(PaneOpenRequestKey, id)
              navController.popBackStack()
            },
          ) {
            HomeworkDetailRoute(
              homeworkId = entry.arguments?.getString("homeworkId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = homeworkViewModel,
            )
          }
        }
        composable("documents", transition = FluidPageTransition.Expand) { entry ->
          val documentsViewModel: DocumentsViewModel = hiltViewModel()
          val paneRequest by entry.savedStateHandle
            .getStateFlow<String?>(PaneOpenRequestKey, null)
            .collectAsStateWithLifecycle()
          AdaptiveListDetail(
            openRequest = paneRequest,
            onOpenRequestConsumed = { entry.savedStateHandle[PaneOpenRequestKey] = null },
            ambient = FeatureIdentity.Documents.ambient(),
            emptyTitle = "Nessun documento aperto",
            emptyMessage = "Scegli una pagella o un documento dall'elenco per vederlo qui.",
            emptyIcon = Icons.AutoMirrored.Rounded.LibraryBooks,
            onOpenPage = { documentId -> navigateRoute("document-detail/${Uri.encode(documentId)}") },
            list = { inPane, selectedId, onOpen ->
              DocumentsRoute(
                onBack = navController::navigateUp,
                onOpenDocument = onOpen,
                selectedId = selectedId,
                inPane = inPane,
                viewModel = documentsViewModel,
              )
            },
            detail = { documentId, onClose ->
              DocumentDetailRoute(documentId = documentId, onBack = null, viewModel = documentsViewModel)
            },
          )
        }
        composable(
          route = "document-detail/{documentId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val documentsViewModel: DocumentsViewModel = hiltViewModel(parentEntry)
          val detailId = entry.arguments?.getString("documentId").orEmpty()
          PaneAwareDetail(
            id = detailId,
            onWide = { id ->
              navController.previousBackStackEntry?.savedStateHandle?.set(PaneOpenRequestKey, id)
              navController.popBackStack()
            },
          ) {
            DocumentDetailRoute(
              documentId = entry.arguments?.getString("documentId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = documentsViewModel,
            )
          }
        }
        composable(
          route = "lessons",
          transition = FluidPageTransition.Expand,
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/lessons" },
          ),
        ) { entry ->
          LessonsRoute(onBack = navController::navigateUp)
        }
        composable(
          route = "absences?absenceId={absenceId}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(
            navArgument("absenceId") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
          ),
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/absences?absenceId={absenceId}" },
            navDeepLink { uriPattern = "classevivaexpressive://open/absences" },
          ),
        ) { entry ->
          AbsencesRoute(
            initialAbsenceId = entry.arguments?.getString("absenceId"),
            onBack = navController::navigateUp,
          )
        }
        composable(
          route = "meetings",
          transition = FluidPageTransition.Expand,
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/meetings" },
          ),
        ) { entry ->
          MeetingsRoute(onBack = navController::navigateUp)
        }
        composable("professors", transition = FluidPageTransition.Expand) { entry ->
          val professorsViewModel: ProfessorsViewModel = hiltViewModel()
          val paneRequest by entry.savedStateHandle
            .getStateFlow<String?>(PaneOpenRequestKey, null)
            .collectAsStateWithLifecycle()
          AdaptiveListDetail(
            openRequest = paneRequest,
            onOpenRequestConsumed = { entry.savedStateHandle[PaneOpenRequestKey] = null },
            ambient = FeatureIdentity.People.ambient(),
            emptyTitle = "Nessun docente aperto",
            emptyMessage = "Scegli un docente dall'elenco per vederne materie, voti e presenza qui.",
            emptyIcon = Icons.Rounded.CoPresent,
            onOpenPage = { teacherName -> navigateRoute("professor-detail/${Uri.encode(teacherName)}") },
            list = { inPane, selectedId, onOpen ->
              ProfessorsRoute(
                onBack = navController::navigateUp,
                onOpenProfessor = onOpen,
                selectedId = selectedId,
                inPane = inPane,
                viewModel = professorsViewModel,
              )
            },
            detail = { teacherName, onClose ->
              ProfessorDetailRoute(teacherName = teacherName, onBack = null, viewModel = professorsViewModel)
            },
          )
        }
        composable(
          route = "professor-detail/{teacherName}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(navArgument("teacherName") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val professorsViewModel: ProfessorsViewModel = hiltViewModel(parentEntry)
          val detailId = entry.arguments?.getString("teacherName").orEmpty()
          PaneAwareDetail(
            id = detailId,
            onWide = { id ->
              navController.previousBackStackEntry?.savedStateHandle?.set(PaneOpenRequestKey, id)
              navController.popBackStack()
            },
          ) {
            ProfessorDetailRoute(
              teacherName = entry.arguments?.getString("teacherName").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = professorsViewModel,
            )
          }
        }
        composable(
          route = "settings",
          transition = FluidPageTransition.Expand,
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/settings" },
          ),
        ) { entry ->
          SettingsRoute(
            onBack = navController::navigateUp,
            update = update,
            currentVersion = BuildConfig.VERSION_NAME,
            onSkipUpdate = onSkipUpdate,
            onOpenAssistantConsent = { navigateRoute(AssistantConsentRouteName) },
            onOpenAssistantHistory = if (assistantEnabled) {
              { navigateRoute(AssistantHistoryRouteName) }
            } else {
              null
            },
          )
        }
        composable(AssistantConsentRouteName, transition = FluidPageTransition.Expand) { entry ->
          AssistantConsentRoute(
            onBack = navController::navigateUp,
            onAccepted = navController::navigateUp,
          )
        }
        composable(
          route = AssistantHistoryRouteName,
          transition = FluidPageTransition.Expand,
          deepLinks = listOf(navDeepLink { uriPattern = "classevivaexpressive://open/assistant" }),
        ) { entry ->
          AssistantHistoryRoute(
            onBack = navController::navigateUp,
            onOpenConversation = { id -> navigateRoute("assistant/$id") },
            onNewConversation = { navigateRoute("assistant/new") },
          )
        }
        composable(
          route = AssistantConversationRouteName,
          transition = FluidPageTransition.Expand,
          arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
          deepLinks = listOf(navDeepLink { uriPattern = "classevivaexpressive://open/assistant?conversationId={conversationId}" }),
        ) { entry ->
          AssistantConversationRoute(
            onBack = navController::navigateUp,
            onOpenPage = { page, itemId -> openAssistantPage(page, itemId) },
            onOverlaySuppressed = { assistantOverlay.suppressed = it },
          )
        }
        composable(
          route = "studentScore?payload={payload}",
          transition = FluidPageTransition.Expand,
          arguments = listOf(
            navArgument("payload") {
              nullable = true
              defaultValue = null
              type = NavType.StringType
            },
          ),
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://student-score/import?payload={payload}" },
          ),
        ) { entry ->
        StudentScoreRoute(initialImportPayload = entry.arguments?.getString("payload"))
      }
      }
    }
  }
}

private data class MoreHubAction(
  val title: String,
  val subtitle: String,
  val eyebrow: String,
  val tone: FluidTone,
  val icon: ImageVector,
  val onClick: () -> Unit,
)

@Composable
private fun MoreHubScreen(
  onOpenBugReport: () -> Unit,
  onOpenLessons: () -> Unit,
  onOpenAbsences: () -> Unit,
  onOpenMaterials: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenNotes: () -> Unit,
  onOpenHomework: () -> Unit,
  onOpenDocuments: () -> Unit,
  onOpenProfessors: () -> Unit,
  onOpenMeetings: () -> Unit,
  /** Le conversazioni con l'assistente; null quando l'assistente e' spento, e la voce non c'e'. */
  onOpenAssistant: (() -> Unit)? = null,
) {
  val registerActions = listOf(
    MoreHubAction("Orario", "Lezioni di oggi e della settimana.", "Lezioni", FluidTone.Info, Icons.Rounded.Schedule, onOpenLessons),
    MoreHubAction("Compiti", "Attività assegnate e scadenze.", "Agenda", FluidTone.Warning, Icons.AutoMirrored.Rounded.Assignment, onOpenHomework),
    MoreHubAction("Didattica", "File, link e cartelle dei docenti.", "Materiali", FluidTone.Info, Icons.Rounded.FolderCopy, onOpenMaterials),
    MoreHubAction("Documenti e libri", "Pagelle, documenti e testi adottati.", "Archivio", FluidTone.Info, Icons.AutoMirrored.Rounded.LibraryBooks, onOpenDocuments),
  )
  val peopleActions = listOf(
    MoreHubAction("Note disciplinari", "Note e sanzioni del registro.", "Bacheca", FluidTone.Danger, Icons.Rounded.Report, onOpenNotes),
    MoreHubAction("Assenze", "Assenze, ritardi e uscite.", "Presenze", FluidTone.Warning, Icons.Rounded.EventBusy, onOpenAbsences),
    MoreHubAction("Colloqui", "Disponibilità e prenotazioni.", "Incontri", FluidTone.Info, Icons.Rounded.Forum, onOpenMeetings),
    MoreHubAction("Docenti", "Presenza, valutazioni e materie di ognuno.", "Classe", FluidTone.Neutral, Icons.Rounded.CoPresent, onOpenProfessors),
  )

  val metrics = rememberFluidScreenMetrics()
  FluidScreen(
    title = "Altro",
    subtitle = "Strumenti del registro, raccolti per ciò che devi fare.",
    // Era l'unica scheda senza fondale: le altre quattro hanno la loro lavata di colore e il loro
    // motivo, e passando ad "Altro" la pagina diventava grigia come un'altra app. Il tono e' quello
    // della casa, perche' qui non c'e' una sezione: c'e' l'indice di tutte.
    ambient = FeatureIdentity.Settings.ambient(),
    contentMaxWidth = FluidColumnsDefaults.WideContentMaxWidth,
    metrics = metrics,
  ) {
    fluidColumns(
      key = "more:columns",
      columns = metrics.columns(),
      sections = listOf(
        FluidColumnSection(key = "more:register") {
          MoreHubSection("Registro") { MoreHubActionGroup(registerActions) }
        },
        FluidColumnSection(key = "more:people") {
          MoreHubSection("Persone e presenza") { MoreHubActionGroup(peopleActions) }
        },
        FluidColumnSection(key = "more:app") {
          MoreHubSection("App") {
            FluidListGroup(glass = true) {
              if (onOpenAssistant != null) {
                FluidListRow(
                  title = "Assistente",
                  subtitle = "Le conversazioni salvate, da rileggere o continuare.",
                  eyebrow = "IA",
                  tone = FluidTone.Primary,
                  leading = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                  onClick = onOpenAssistant,
                )
                FluidListDivider()
              }
              FluidListRow(
                title = "Segnala un problema",
                subtitle = "Una segnalazione pubblica su GitHub, con una diagnostica che puoi rivedere.",
                eyebrow = "Feedback",
                tone = FluidTone.Info,
                leading = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                onClick = onOpenBugReport,
              )
              FluidListDivider()
              FluidListRow(
                title = "Impostazioni",
                subtitle = "Account, aspetto, notifiche, dati e aggiornamenti.",
                eyebrow = "Profilo",
                leading = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                onClick = onOpenSettings,
              )
            }
          }
        },
      ),
    )
  }
}

/**
 * Una sezione dell'indice: la testata e il suo gruppo, in un blocco solo.
 *
 * Sul telefono sono un item ciascuna, come prima; in colonne stanno insieme, e la testata non puo'
 * finire in cima a una colonna col suo gruppo in fondo a un'altra.
 */
@Composable
private fun MoreHubSection(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(FluidScreenDefaults.ItemSpacing)) {
    FluidSectionHeader(title)
    content()
  }
}

@Composable
private fun MoreHubActionGroup(actions: List<MoreHubAction>) {
  FluidListGroup(glass = true) {
    actions.forEachIndexed { index, action ->
      FluidListRow(
        title = action.title,
        subtitle = action.subtitle,
        eyebrow = action.eyebrow,
        tone = action.tone,
        leading = { Icon(action.icon, contentDescription = null) },
        onClick = action.onClick,
      )
      if (index != actions.lastIndex) FluidListDivider()
    }
  }
}

/** Cosa chiede una tastiera fisica, dall'Activity che riceve i tasti alla navigazione che risponde. */
sealed interface KeyboardShortcut {
  /** Ctrl+R, F5: aggiorna la pagina davanti. */
  data object Refresh : KeyboardShortcut

  /** Ctrl+1…5: la sezione della barra in quella posizione, da zero. */
  data class Section(val index: Int) : KeyboardShortcut
}
