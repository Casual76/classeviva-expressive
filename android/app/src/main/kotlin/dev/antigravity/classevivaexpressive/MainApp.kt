package dev.antigravity.classevivaexpressive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaDetailRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaViewModel
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationDetailRoute
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsRoute
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsViewModel
import dev.antigravity.classevivaexpressive.feature.communications.NoteDetailRoute
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
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationDelivery
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidHeroCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import dev.antigravity.fluidengine.ui.theme.FluidRouteMotion
import dev.antigravity.fluidengine.ui.theme.FluidRouteMotionHost
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.LocalRouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.MotionOrigin
import dev.antigravity.fluidengine.ui.theme.RouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.fluidTouchOriginTracker
import dev.antigravity.fluidengine.ui.theme.rememberFluidTouchOrigin
import dev.antigravity.fluidengine.ui.theme.rememberRouteMotionSignals

private const val BugReportRepositoryOwner = "Casual76"
private const val BugReportRepositoryName = "classeviva-expressive"
private const val BugReportTemplateName = "app_bug_report.md"
internal const val BugReportRoute = "bugReport"
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

/**
 * The tab bar's own timing.
 *
 * Faster than the page transition on purpose. The bar is chrome: it should already be out of the way
 * by the time the new screen has finished arriving, otherwise the two movements read as one confused
 * gesture rather than as a screen opening over a bar that stepped aside.
 */
private fun barSlideSpec() = FluidMotion.intOffset(
  dampingRatio = FluidMotion.DampingChrome,
  stiffness = FluidMotion.ResponseSnappy,
)

/**
 * One hierarchical movement for the whole app: the destination grows out of the point that was
 * touched, while the page it covers recedes a little underneath it.
 *
 * Everything here is duration-based, and that is not a stylistic preference. The predictive-back
 * gesture drives these transitions by *seeking* into them, and only a finite, monotonic curve can
 * be seeked; a spring has no duration to scrub within, which is precisely why dragging back used to
 * feel unrelated to what was on screen.
 *
 * All of it settles on [FluidMotion.EaseEmphasized] — decisive start, long calm settle, and no
 * overshoot anywhere. An earlier pass used a curve that carried three percent of overshoot on the
 * theory that it would read as springiness; on a surface the size of the display it read as the
 * page arriving, going slightly too far, and coming back, which is the "strange bounce" it was
 * meant to avoid. Spring belongs to things small enough to carry it.
 */
private fun expandSpec() = tween<Float>(
  durationMillis = FluidMotion.DurationExpand,
  easing = FluidMotion.EaseEmphasized,
)

private fun collapseSpec() = tween<Float>(
  durationMillis = FluidMotion.DurationCollapse,
  easing = FluidMotion.EaseEmphasized,
)

/** Peers travel on the same curve as everything else, only sideways and only a little. */
private fun peerSlideSpec() = tween<IntOffset>(
  durationMillis = FluidMotion.DurationPeer,
  easing = FluidMotion.EaseEmphasized,
)

/** The page being covered: it gives way while the new one is still becoming opaque. */
private fun coveredFadeOut() = tween<Float>(
  durationMillis = FluidMotion.DurationRouteFadeOut,
  easing = FluidMotion.EaseIn,
)

/**
 * The page being dismissed: it stays solid while it shrinks and only disappears at the end.
 *
 * A detail that starts dissolving the instant the back gesture begins reads as a page falling
 * apart. Holding its opacity while it contracts is what makes it read as *closing* — and it is the
 * half of the gesture the finger is actually scrubbing, so it has to be the legible half.
 */
private fun dismissedFadeOut() = tween<Float>(
  durationMillis = 170,
  delayMillis = 90,
  easing = FluidMotion.EaseIn,
)

/** How far a peer page slides, in pixels, given the width it is sliding across. */
private fun peerSlidePx(width: Int, direction: Int): Int =
  (width * FluidMotion.PeerSlideFraction * direction).roundToInt()

/**
 * Resolves the movement between two entries, and records for the layers about to draw whether the
 * departing page should lose focus on its way out.
 *
 * The entry that is joining or leaving the stack is the one that knows how it got there: on the way
 * forward that is the arriving destination, on the way back the one being dismissed.
 */
private fun RouteMotionSignals.resolveRouteMotion(
  initialState: NavBackStackEntry,
  targetState: NavBackStackEntry,
  isPop: Boolean,
): RouteMotionDecision {
  val carrier = if (isPop) initialState else targetState
  val decision = decideRouteMotion(
    fromRoute = initialState.destination.route,
    toRoute = targetState.destination.route,
    requestedKind = carrier.savedStateHandle.readMotionKind(),
  )
  hierarchical = decision.kind == RouteMotionKind.Expand
  return decision
}

private fun routeEnterTransition(
  decision: RouteMotionDecision,
  origin: MotionOrigin,
  isPop: Boolean,
): EnterTransition = when (decision.kind) {
  // Peers step sideways together. A full-width wipe between two readable pages says "these are
  // nested", which is the one thing tabs are not; no movement at all said nothing whatsoever, which
  // is how a tab change ends up feeling like the app skipped a frame.
  RouteMotionKind.TopLevelSwitch -> {
    // Opaque from the first frame here too, for the same reason. It matters most under a scrubbed
    // predictive back, where the transition can be *held* at any point: a cross-fade paused halfway
    // is two tabs printed on top of each other, and the finger can keep it there indefinitely.
    if (decision.direction == 0) {
      fadeIn(tween(FluidMotion.DurationPeerFadeIn, easing = FluidMotion.EaseOut))
    } else {
      slideInHorizontally(peerSlideSpec()) { width -> peerSlidePx(width, decision.direction) }
    }
  }

  // No fade on either side of a hierarchical move, and that is the whole invariant: exactly one
  // readable page at every pixel of every frame. An arriving page carries a rounded edge for as
  // long as it is smaller than the screen, so it has no hard rectangular boundary left to disguise
  // — and every millisecond a page spends translucent is a millisecond where two screens can be
  // read through each other, which is what made opening something look like a cross-fade.
  RouteMotionKind.Expand -> if (isPop) {
    // Coming back, the page underneath is uncovered: it returns from slightly too close, which is
    // exactly where the forward transition left it.
    scaleIn(
      animationSpec = collapseSpec(),
      initialScale = FluidRouteMotion.ExpandParentScale,
      transformOrigin = origin.toTransformOrigin(),
    )
  } else {
    scaleIn(
      animationSpec = expandSpec(),
      initialScale = FluidRouteMotion.ExpandInitialScale,
      transformOrigin = origin.toTransformOrigin(),
    )
  }
}

private fun routeExitTransition(
  decision: RouteMotionDecision,
  origin: MotionOrigin,
  isPop: Boolean,
): ExitTransition = when (decision.kind) {
  RouteMotionKind.TopLevelSwitch -> {
    // The one that leaves still fades, and quickly: both pages travel the same way by the same
    // amount, so the only place the old one is ever uncovered is the narrow strip the new one has
    // not reached yet, and that strip should resolve to the page's own background rather than to a
    // sliver of the tab you just left.
    val fade = fadeOut(tween(FluidMotion.DurationPeerFadeOut, easing = FluidMotion.EaseIn))
    if (decision.direction == 0) {
      fade
    } else {
      fade + slideOutHorizontally(peerSlideSpec()) { width -> peerSlidePx(width, -decision.direction) }
    }
  }

  RouteMotionKind.Expand -> if (isPop) {
    // The detail collapses back into the control it came out of.
    fadeOut(dismissedFadeOut()) + scaleOut(
      animationSpec = collapseSpec(),
      targetScale = FluidRouteMotion.ExpandInitialScale,
      transformOrigin = origin.toTransformOrigin(),
    )
  } else {
    fadeOut(coveredFadeOut()) + scaleOut(
      animationSpec = expandSpec(),
      targetScale = FluidRouteMotion.ExpandParentScale,
      transformOrigin = origin.toTransformOrigin(),
    )
  }
}

@Composable
fun MainApp(
  viewModel: MainViewModel = hiltViewModel(),
  incomingIntents: Flow<Intent> = emptyFlow(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val notificationHostState = rememberFluidNotificationHostState()
  val routeMotionSignals = rememberRouteMotionSignals()
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
    CompositionLocalProvider(
      LocalFluidNotificationHostState provides notificationHostState,
      LocalRouteMotionSignals provides routeMotionSignals,
    ) {
      FluidScreenSurface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
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
              isCheckingForUpdates = uiState.isCheckingUpdate,
              updateCheckMessage = uiState.updateCheckMessage,
              onCheckForUpdates = { viewModel.checkUpdate() },
              onClearUpdateCheckMessage = viewModel::clearUpdateCheckMessage,
              incomingIntents = incomingIntents,
            )
          }
          FluidNotificationHost(
            state = notificationHostState,
            backdrop = chromeController.activeBackdrop.value,
            modifier = Modifier.align(Alignment.TopCenter),
          )
          val update = uiState.availableUpdate
          if (update != null && !uiState.isUpdateDismissedForSession) {
            AppUpdateDialog(
              update = update,
              installState = uiState.updateInstallState,
              onInstall = viewModel::startUpdateInstall,
              onLater = viewModel::dismissUpdate,
              onIgnore = viewModel::ignoreUpdateVersion,
            )
          }
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
private fun AppUpdateDialog(
  update: AvailableAppUpdate,
  installState: AppUpdateInstallState,
  onInstall: () -> Unit,
  onLater: () -> Unit,
  onIgnore: () -> Unit,
) {
  val busy = installState.isBusy()
  val statusText = when (installState) {
    AppUpdateInstallState.Idle -> update.changelog.ifBlank { "Nuova versione disponibile." }
    is AppUpdateInstallState.Downloading -> {
      val percent = (installState.progress * 100).toInt().coerceIn(0, 100)
      "Download aggiornamento: $percent%"
    }
    is AppUpdateInstallState.Verifying -> installState.message
    is AppUpdateInstallState.AwaitingUserAction -> installState.message
    is AppUpdateInstallState.Installing -> installState.message
    is AppUpdateInstallState.Installed -> "Aggiornamento installato."
    is AppUpdateInstallState.Error -> installState.message
  }

  FluidAlert(
    onDismissRequest = { if (!busy) onLater() },
    title = "Aggiornamento ${update.version}",
    actions = listOf(
      FluidAlertAction("Ignora", onIgnore, FluidAlertAction.Emphasis.Normal, enabled = !busy),
      FluidAlertAction("Più tardi", onLater, FluidAlertAction.Emphasis.Normal, enabled = !busy),
      FluidAlertAction(if (installState is AppUpdateInstallState.Error) "Riprova" else "Aggiorna", onInstall, FluidAlertAction.Emphasis.Preferred, enabled = !busy && installState !is AppUpdateInstallState.Installed),
    ),
    content = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(statusText)
        if (busy) {
          FluidIndeterminateBar(modifier = Modifier.fillMaxWidth())
        }
      }
    },
  )
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
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  fun submit() {
    if (username.isNotBlank() && password.isNotBlank() && !isLoading) {
      onLogin(username.trim(), password)
    }
  }

  val systemBars = WindowInsets.systemBars.asPaddingValues()
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(
      start = 24.dp,
      end = 24.dp,
      top = systemBars.calculateTopPadding() + 28.dp,
      bottom = systemBars.calculateBottomPadding() + 28.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      FluidHeroCard(
        title = "Classeviva Expressive",
        subtitle = "Material 3 ufficiale per registro, agenda, voti e bacheca, tutta in Kotlin e Compose.",
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
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailing = {
            FluidButton(
              text = if (passwordVisible) "Nascondi" else "Mostra",
              onClick = { passwordVisible = !passwordVisible },
              style = FluidButtonStyle.Plain,
            )
          },
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
    item {
      FluidEmptyState(
        title = "Autofill Compose",
        detail = "I campi credenziali espongono i content type ufficiali di Compose per username, email e password.",
      )
    }
  }
}

/**
 * The app shell: screen content edge to edge, with a floating tab bar over it.
 *
 * The bar no longer reserves layout space. It used to consume a hard 92dp strip at the bottom of
 * every screen, which is why text ran into an invisible wall near the bottom of a list. Now content
 * occupies the whole display and simply pads its *scroll* by the bar's height, so the last item can
 * be scrolled clear of the bar while everything in between passes underneath it through the glass.
 */
@Composable
internal fun TopLevelNavigationSuite(
  currentRoute: String?,
  showNavigationSuite: Boolean,
  onNavigateRoute: (String) -> Unit,
  onReselectRoute: (String) -> Unit = {},
  scrollToTop: FluidScrollToTopBus = remember { FluidScrollToTopBus() },
  chromeController: FluidChromeController = rememberFluidChromeController(),
  content: @Composable () -> Unit,
) {
  val fallbackBackdrop = rememberGlassBackdrop()
  val bottomBarTravel = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
    FluidTabBarDefaults.Height + FluidTabBarDefaults.BottomMargin
  val bottomBarTravelPx = with(LocalDensity.current) { bottomBarTravel.toPx() }
  LaunchedEffect(chromeController, bottomBarTravelPx) {
    chromeController.updateBottomBarTravel(bottomBarTravelPx)
  }
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
      .nestedScroll(chromeScrollConnection),
  ) {
    val useRail = maxWidth >= 600.dp
    val bottomInset = if (showNavigationSuite && !useRail) FluidTabBarDefaults.ContentInset else 0.dp
    val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop

    ProvideFluidChrome(
      controller = chromeController,
      bottomInset = bottomInset,
      scrollToTop = scrollToTop,
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(start = if (showNavigationSuite && useRail) 100.dp else 0.dp),
      ) {
        content()
      }
    }

    val onSelect: (FluidTabItem) -> Unit = { item ->
      onNavigateRoute(routeOf[item.route] ?: item.route)
    }
    val onReselect: (FluidTabItem) -> Unit = { item -> onReselectRoute(item.route) }

    if (useRail) {
      AnimatedVisibility(
        visible = showNavigationSuite,
        enter = slideInHorizontally(animationSpec = barSlideSpec()) { -it },
        exit = slideOutHorizontally(animationSpec = barSlideSpec()) { -it },
        modifier = Modifier
          .align(Alignment.CenterStart)
          .systemBarsPadding()
          .padding(start = 14.dp),
      ) {
        FluidTabRail(
          items = tabItems,
          selectedRoute = currentRoute,
          onSelect = onSelect,
          onReselect = onReselect,
          backdrop = backdrop,
        )
      }
    } else {
      AnimatedVisibility(
        visible = showNavigationSuite,
        enter = slideInVertically(animationSpec = barSlideSpec()) { it },
        exit = slideOutVertically(animationSpec = barSlideSpec()) { it },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .graphicsLayer {
            translationY = chromeController.bottomBarOffsetPx.value
          }
          .navigationBarsPadding()
          .padding(
            horizontal = FluidTabBarDefaults.HorizontalMargin,
            vertical = FluidTabBarDefaults.BottomMargin,
          ),
      ) {
        FluidTabBar(
          items = tabItems,
          selectedRoute = currentRoute,
          onSelect = onSelect,
          onReselect = onReselect,
          backdrop = backdrop,
        )
      }
    }
  }
}

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
    onBack = onBack,
    itemSpacing = 12.dp,
  ) {
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(Icons.Rounded.WarningAmber, contentDescription = null)
          Text(
            "La segnalazione sarà una issue GitHub pubblica e attribuita all'account GitHub con cui la invii. Non è anonima: non inserire credenziali o dati scolastici personali.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
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
      item { Text("Report copiato negli appunti.", color = MaterialTheme.colorScheme.primary) }
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

internal fun NavHostController.navigateTopLevel(targetRoute: String) {
  val startDestination = graph.findStartDestination()
  val targetBaseRoute = targetRoute.substringBefore('?')
  val targetsStartDestination = targetBaseRoute == startDestination.route?.substringBefore('?')
  navigate(targetRoute) {
    popUpTo(startDestination.id) {
      saveState = true
    }
    launchSingleTop = true
    // Restoring the start route here also restores the tab that was just popped beneath it,
    // so Back from Home would reopen that tab. Its state remains saved for later tab selection.
    restoreState = !targetsStartDestination
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
  isCheckingForUpdates: Boolean,
  updateCheckMessage: String?,
  onCheckForUpdates: () -> Unit,
  onClearUpdateCheckMessage: () -> Unit,
  incomingIntents: Flow<Intent>,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val currentRoute = currentDestination?.route?.substringBefore("?")
  val showNavigationSuite = currentRoute in topLevelRoutes

  val scrollToTop = remember { FluidScrollToTopBus() }
  val touchOrigin = rememberFluidTouchOrigin()
  // Owned by the shell above: one navigation host, one place that says what the leaving page does.
  val motionSignals = LocalRouteMotionSignals.current

  // The destination carries both the point it was opened from and the fact that it *was* opened
  // rather than switched to, so going back undoes the same movement however the user chose to
  // leave — the system gesture, the chevron, or a nested pop.
  fun navigateRoute(route: String) {
    val origin = touchOrigin.origin
    navController.navigate(route)
    navController.currentBackStackEntry?.savedStateHandle?.writeExpandMotion(origin)
  }

  /** Stepping along the bar. The bar is the gesture, so the pages only have to answer it. */
  fun navigateTopLevelRoute(targetRoute: String) {
    navController.navigateTopLevel(targetRoute)
    navController.currentBackStackEntry?.savedStateHandle?.writePeerMotion()
  }

  /**
   * Opening a top-level section by touching something on the page.
   *
   * The routes are peers but the gesture is not: tapping the average on Home is that card opening
   * into the page behind it, and answering it with a tab bar's sideways shrug is what made the most
   * direct route in the app look like nothing had happened.
   */
  fun expandToTopLevelRoute(targetRoute: String) {
    val origin = touchOrigin.origin
    navController.navigateTopLevel(targetRoute)
    navController.currentBackStackEntry?.savedStateHandle?.writeExpandMotion(origin)
  }

  LaunchedEffect(navController, incomingIntents) {
    incomingIntents.collect { intent ->
      navController.handleIncomingIntent(intent)
    }
  }

  // The policy wraps the whole shell: top-level chrome is motion too, not only route content.
  // Keeping the provider inside the NavHost left the floating tab indicator animating at 0x.
  FluidMotionPolicyProvider {
    TopLevelNavigationSuite(
      currentRoute = currentDestination?.hierarchy
        ?.mapNotNull { it.route?.substringBefore("?") }
        ?.firstOrNull { it in topLevelRoutes },
      showNavigationSuite = showNavigationSuite,
      onNavigateRoute = { targetRoute ->
        navigateTopLevelRoute(targetRoute)
      },
      onReselectRoute = { scrollToTop.request() },
      scrollToTop = scrollToTop,
      chromeController = chromeController,
    ) {
      NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier
          .fillMaxSize()
          .fluidTouchOriginTracker(touchOrigin),
        // One spatial model drives both ordinary navigation and predictive-back progress. The
        // origin always comes from whichever entry is *leaving the stack*: forward that is the
        // arriving one, on a pop it is the one being dismissed.
        enterTransition = {
          routeEnterTransition(
            decision = motionSignals.resolveRouteMotion(initialState, targetState, isPop = false),
            origin = targetState.savedStateHandle.readMotionOrigin(),
            isPop = false,
          )
        },
        exitTransition = {
          routeExitTransition(
            decision = motionSignals.resolveRouteMotion(initialState, targetState, isPop = false),
            origin = targetState.savedStateHandle.readMotionOrigin(),
            isPop = false,
          )
        },
        popEnterTransition = {
          routeEnterTransition(
            decision = motionSignals.resolveRouteMotion(initialState, targetState, isPop = true),
            origin = initialState.savedStateHandle.readMotionOrigin(),
            isPop = true,
          )
        },
        popExitTransition = {
          routeExitTransition(
            decision = motionSignals.resolveRouteMotion(initialState, targetState, isPop = true),
            origin = initialState.savedStateHandle.readMotionOrigin(),
            isPop = true,
          )
        },
      ) {
        composable("home") { entry ->
          FluidRouteMotionHost(this@composable) {
            DashboardRoute(
              onNavigateGrades = { expandToTopLevelRoute("grades") },
              onNavigateAgenda = { expandToTopLevelRoute("agenda") },
              onNavigateLessons = { navigateRoute("lessons") },
              onNavigateCommunications = {
                expandToTopLevelRoute("communications?tab=board")
              },
              onOpenGrade = { gradeId -> expandToTopLevelRoute("grades?gradeId=$gradeId") },
            )
          }
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
          FluidRouteMotionHost(this@composable) {
            AgendaRoute(
              initialAgendaId = null,
              initialDate = entry.arguments?.getString("date"),
              onOpenEntry = { entryId ->
                navigateRoute("agenda-detail/${Uri.encode(entryId)}")
              },
            )
          }
        }
        composable(
          route = "agenda-detail/{entryId}",
          arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val agendaViewModel: AgendaViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            AgendaDetailRoute(
              entryId = entry.arguments?.getString("entryId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = agendaViewModel,
            )
          }
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
          FluidRouteMotionHost(this@composable) {
            GradesRoute(
              initialGradeId = pendingGradeRequest(requestedGradeId, consumedGradeId),
              onInitialGradeConsumed = { gradeId ->
                entry.savedStateHandle[ConsumedGradeRequestKey] = gradeId
              },
              onOpenGrade = { gradeId ->
                navigateRoute("grade/${Uri.encode(gradeId)}")
              },
            )
          }
        }
        composable(
          route = "grade/{gradeId}",
          arguments = listOf(
            navArgument("gradeId") { type = NavType.StringType },
          ),
        ) { entry ->
          val gradesBackStackEntry = remember(entry) {
            navController.getBackStackEntry("grades?gradeId={gradeId}")
          }
          val gradesViewModel: GradesViewModel = hiltViewModel(gradesBackStackEntry)
          FluidRouteMotionHost(this@composable) {
            GradeDetailRoute(
              gradeId = entry.arguments?.getString("gradeId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = gradesViewModel,
            )
          }
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
          val requestedPubId = entry.arguments?.getString("pubId")
          val requestedEvtCode = entry.arguments?.getString("evtCode")
          val requestedNoteId = entry.arguments?.getString("noteId")
          val requestedCategoryCode = entry.arguments?.getString("categoryCode")
          val isDirectDetail = (!requestedPubId.isNullOrBlank() && !requestedEvtCode.isNullOrBlank()) ||
            (!requestedNoteId.isNullOrBlank() && !requestedCategoryCode.isNullOrBlank())
          FluidRouteMotionHost(this@composable) {
            CommunicationsRoute(
              initialTab = entry.arguments?.getString("tab") ?: "board",
              initialCommunicationPubId = requestedPubId,
              initialCommunicationEvtCode = requestedEvtCode,
              initialNoteId = requestedNoteId,
              initialNoteCategoryCode = requestedCategoryCode,
              onBack = if (currentRoute == "communications") null else { { navController.navigateUp() } },
              onOpenCommunication = if (isDirectDetail) null else { pubId, evtCode ->
                navigateRoute("communication-detail/${Uri.encode(pubId)}/${Uri.encode(evtCode)}")
              },
              onOpenNote = if (isDirectDetail) null else { noteId, categoryCode ->
                navigateRoute("note-detail/${Uri.encode(noteId)}/${Uri.encode(categoryCode)}")
              },
            )
          }
        }
        composable("notes") { entry ->
          FluidRouteMotionHost(this@composable) {
            CommunicationsRoute(
              initialTab = "notes",
              onBack = navController::navigateUp,
              onOpenCommunication = { pubId, evtCode ->
                navigateRoute("communication-detail/${Uri.encode(pubId)}/${Uri.encode(evtCode)}")
              },
              onOpenNote = { noteId, categoryCode ->
                navigateRoute("note-detail/${Uri.encode(noteId)}/${Uri.encode(categoryCode)}")
              },
            )
          }
        }
        composable(
          route = "communication-detail/{pubId}/{evtCode}",
          arguments = listOf(
            navArgument("pubId") { type = NavType.StringType },
            navArgument("evtCode") { type = NavType.StringType },
          ),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val communicationsViewModel: CommunicationsViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            CommunicationDetailRoute(
              pubId = entry.arguments?.getString("pubId").orEmpty(),
              evtCode = entry.arguments?.getString("evtCode").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = communicationsViewModel,
            )
          }
        }
        composable(
          route = "note-detail/{id}/{categoryCode}",
          arguments = listOf(
            navArgument("id") { type = NavType.StringType },
            navArgument("categoryCode") { type = NavType.StringType },
          ),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val communicationsViewModel: CommunicationsViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            NoteDetailRoute(
              id = entry.arguments?.getString("id").orEmpty(),
              categoryCode = entry.arguments?.getString("categoryCode").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = communicationsViewModel,
            )
          }
        }
        composable("more") { entry ->
          FluidRouteMotionHost(this@composable) {
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
            )
          }
        }
        composable(BugReportRoute) { entry ->
          FluidRouteMotionHost(this@composable) {
            // Keep the route that opened the form in the diagnostics. Reading currentRoute here would
            // report "bugReport", because this is now (correctly) its own back-stack destination.
            BugReportScreen(
              currentRoute = BugReportSourceRoute,
              onBack = navController::navigateUp,
            )
          }
        }
        composable("materials") { entry ->
          FluidRouteMotionHost(this@composable) {
            MaterialsRoute(
              onBack = navController::navigateUp,
              onOpenMaterial = { itemId ->
                navigateRoute("material-detail/${Uri.encode(itemId)}")
              },
            )
          }
        }
        composable(
          route = "material-detail/{itemId}",
          arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val materialsViewModel: MaterialsViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            MaterialDetailRoute(
              itemId = entry.arguments?.getString("itemId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = materialsViewModel,
            )
          }
        }
        composable(
          route = "homework?homeworkId={homeworkId}",
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
          LaunchedEffect(pendingHomeworkId) {
            val homeworkId = pendingHomeworkId ?: return@LaunchedEffect
            entry.savedStateHandle[ConsumedHomeworkRequestKey] = homeworkId
            navigateRoute(route = "homework-detail/${Uri.encode(homeworkId)}")
          }
          FluidRouteMotionHost(this@composable) {
            HomeworkRoute(
              initialHomeworkId = null,
              onBack = navController::navigateUp,
              onOpenHomework = { homeworkId ->
                navigateRoute("homework-detail/${Uri.encode(homeworkId)}")
              },
            )
          }
        }
        composable(
          route = "homework-detail/{homeworkId}",
          arguments = listOf(navArgument("homeworkId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val homeworkViewModel: HomeworkViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            HomeworkDetailRoute(
              homeworkId = entry.arguments?.getString("homeworkId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = homeworkViewModel,
            )
          }
        }
        composable("documents") { entry ->
          FluidRouteMotionHost(this@composable) {
            DocumentsRoute(
              onBack = navController::navigateUp,
              onOpenDocument = { documentId ->
                navigateRoute("document-detail/${Uri.encode(documentId)}")
              },
            )
          }
        }
        composable(
          route = "document-detail/{documentId}",
          arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val documentsViewModel: DocumentsViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            DocumentDetailRoute(
              documentId = entry.arguments?.getString("documentId").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = documentsViewModel,
            )
          }
        }
        composable(
          route = "lessons",
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/lessons" },
          ),
        ) { entry ->
          FluidRouteMotionHost(this@composable) {
            LessonsRoute(onBack = navController::navigateUp)
          }
        }
        composable(
          route = "absences?absenceId={absenceId}",
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
          FluidRouteMotionHost(this@composable) {
            AbsencesRoute(
              initialAbsenceId = entry.arguments?.getString("absenceId"),
              onBack = navController::navigateUp,
            )
          }
        }
        composable(
          route = "meetings",
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/meetings" },
          ),
        ) { entry ->
          FluidRouteMotionHost(this@composable) {
            MeetingsRoute(onBack = navController::navigateUp)
          }
        }
        composable("professors") { entry ->
          FluidRouteMotionHost(this@composable) {
            ProfessorsRoute(
              onBack = navController::navigateUp,
              onOpenProfessor = { teacherName ->
                navigateRoute("professor-detail/${Uri.encode(teacherName)}")
              },
            )
          }
        }
        composable(
          route = "professor-detail/{teacherName}",
          arguments = listOf(navArgument("teacherName") { type = NavType.StringType }),
        ) { entry ->
          val parentEntry = remember(entry) { navController.previousBackStackEntry ?: entry }
          val professorsViewModel: ProfessorsViewModel = hiltViewModel(parentEntry)
          FluidRouteMotionHost(this@composable) {
            ProfessorDetailRoute(
              teacherName = entry.arguments?.getString("teacherName").orEmpty(),
              onBack = { navController.popBackStack() },
              viewModel = professorsViewModel,
            )
          }
        }
        composable(
          route = "settings",
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/settings" },
          ),
        ) { entry ->
          FluidRouteMotionHost(this@composable) {
            SettingsRoute(
              onBack = navController::navigateUp,
              isCheckingForUpdates = isCheckingForUpdates,
              updateCheckMessage = updateCheckMessage,
              onCheckForUpdates = onCheckForUpdates,
              onClearUpdateCheckMessage = onClearUpdateCheckMessage,
            )
          }
        }
        composable(
          route = "studentScore?payload={payload}",
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
) {
  val registerActions = listOf(
    MoreHubAction("Orario", "Lezioni di oggi e della settimana.", "Lezioni", FluidTone.Info, Icons.Rounded.Schedule, onOpenLessons),
    MoreHubAction("Compiti", "Attività assegnate e scadenze.", "Agenda", FluidTone.Warning, Icons.AutoMirrored.Rounded.Assignment, onOpenHomework),
    MoreHubAction("Didattica", "File, link e cartelle dei docenti.", "Materiali", FluidTone.Info, Icons.Rounded.FolderCopy, onOpenMaterials),
    MoreHubAction("Documenti e libri", "Pagelle, documenti e testi adottati.", "Archivio", FluidTone.Info, Icons.AutoMirrored.Rounded.LibraryBooks, onOpenDocuments),
  )
  val peopleActions = listOf(
    MoreHubAction("Note disciplinari", "Note e sanzioni del registro.", "Comunicazioni", FluidTone.Danger, Icons.Rounded.Report, onOpenNotes),
    MoreHubAction("Assenze", "Assenze, ritardi e uscite.", "Presenze", FluidTone.Warning, Icons.Rounded.EventBusy, onOpenAbsences),
    MoreHubAction("Colloqui", "Disponibilità e prenotazioni.", "Docenti", FluidTone.Info, Icons.Rounded.Forum, onOpenMeetings),
    MoreHubAction("Professori", "Contatti e andamento per docente.", "Docenti", FluidTone.Neutral, Icons.Rounded.CoPresent, onOpenProfessors),
  )

  FluidScreen(
    title = "Altro",
    subtitle = "Strumenti del registro, raccolti per ciò che devi fare.",
  ) {
    item { FluidSectionHeader("Registro") }
    item { MoreHubActionGroup(registerActions) }
    item { FluidSectionHeader("Persone e presenza") }
    item { MoreHubActionGroup(peopleActions) }
    item { FluidSectionHeader("App") }
    item {
      FluidListGroup {
        FluidListRow(
          title = "Segnala un problema",
          subtitle = "Issue GitHub pubblica con diagnostica minima modificabile.",
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
  }
}

@Composable
private fun MoreHubActionGroup(actions: List<MoreHubAction>) {
  FluidListGroup {
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
