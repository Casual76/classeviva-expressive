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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveLoading
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveScreenSurface
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MotionTokens
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.designsystem.theme.expressiveSharedBounds
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaRoute
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DashboardRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DocumentsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.HomeworkRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.MaterialsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.MeetingsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.StudentScoreRoute
import dev.antigravity.classevivaexpressive.feature.grades.GradesRoute
import dev.antigravity.classevivaexpressive.feature.lessons.LessonsRoute
import dev.antigravity.classevivaexpressive.feature.lessons.ProfessorsRoute
import dev.antigravity.classevivaexpressive.feature.settings.SettingsRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val BugReportRepositoryOwner = "Casual76"
private const val BugReportRepositoryName = "classeviva-expressive"
private const val BugReportTemplateName = "app_bug_report.md"

private data class TopLevelDestination(
  val baseRoute: String,
  val navigateRoute: String,
  val label: String,
  val icon: @Composable () -> Unit,
)

private val topLevelDestinations = listOf(
  TopLevelDestination("home", "home", "Home", { Icon(Icons.Rounded.Home, contentDescription = null) }),
  TopLevelDestination("grades", "grades", "Voti", { Icon(Icons.Rounded.Grade, contentDescription = null) }),
  TopLevelDestination("agenda", "agenda", "Agenda", { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) }),
  TopLevelDestination("communications", "communications?tab=board", "Bacheca", { Icon(Icons.Rounded.Notifications, contentDescription = null) }),
  TopLevelDestination("more", "more", "Altro", { Icon(Icons.Rounded.Menu, contentDescription = null) }),
)

internal val topLevelRoutes = topLevelDestinations.map { it.baseRoute }.toSet()

private fun routeEnterTransition(
  decision: RouteMotionDecision,
  isPop: Boolean,
): EnterTransition = when (decision.kind) {
  RouteMotionKind.TopLevelSwitch -> {
    fadeIn(animationSpec = MotionTokens.routeEffects()) +
      scaleIn(initialScale = 0.997f, animationSpec = MotionTokens.routeSpatial())
  }

  RouteMotionKind.SharedContainer -> {
    fadeIn(animationSpec = MotionTokens.routeEffects())
  }

  RouteMotionKind.FallbackScale -> {
    fadeIn(animationSpec = MotionTokens.routeEffects()) +
      scaleIn(initialScale = if (isPop) 1.006f else 0.992f, animationSpec = MotionTokens.routeSpatial()) +
      slideInVertically(
        initialOffsetY = { fullHeight -> if (isPop) -fullHeight / 42 else fullHeight / 42 },
        animationSpec = MotionTokens.routeSpatial(),
      )
  }
}

private fun routeExitTransition(
  decision: RouteMotionDecision,
  isPop: Boolean,
): ExitTransition = when (decision.kind) {
  RouteMotionKind.TopLevelSwitch -> {
    fadeOut(animationSpec = MotionTokens.routeEffects()) +
      scaleOut(targetScale = 1.002f, animationSpec = MotionTokens.routeSpatial())
  }

  RouteMotionKind.SharedContainer -> {
    fadeOut(animationSpec = MotionTokens.routeEffects())
  }

  RouteMotionKind.FallbackScale -> {
    fadeOut(animationSpec = MotionTokens.routeEffects()) +
      scaleOut(targetScale = if (isPop) 0.992f else 1.006f, animationSpec = MotionTokens.routeSpatial()) +
      slideOutVertically(
        targetOffsetY = { fullHeight -> if (isPop) fullHeight / 42 else -fullHeight / 42 },
        animationSpec = MotionTokens.routeSpatial(),
      )
  }
}

@Composable
fun MainApp(
  viewModel: MainViewModel = hiltViewModel(),
  incomingIntents: Flow<Intent> = emptyFlow(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.onAppResumed()
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
    ExpressiveScreenSurface(modifier = Modifier.fillMaxSize()) {
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
            isCheckingForUpdates = uiState.isCheckingUpdate,
            updateCheckMessage = uiState.updateCheckMessage,
            onCheckForUpdates = { viewModel.checkUpdate() },
            onClearUpdateCheckMessage = viewModel::clearUpdateCheckMessage,
            incomingIntents = incomingIntents,
          )
        }
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

@Composable
private fun BugReportDialog(
  currentRoute: String,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var steps by rememberSaveable { mutableStateOf("") }
  var expected by rememberSaveable { mutableStateOf("") }
  var actual by rememberSaveable { mutableStateOf("") }
  var copied by rememberSaveable { mutableStateOf(false) }

  val reportBody = remember(title, description, steps, expected, actual, currentRoute) {
    buildBugReportBody(
      context = context,
      currentRoute = currentRoute,
      description = description,
      steps = steps,
      expected = expected,
      actual = actual,
    )
  }
  val issueUri = remember(title, reportBody) {
    buildBugReportIssueUri(title = title, body = reportBody)
  }
  val canOpenIssue = title.isNotBlank() && description.isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Segnala bug") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Rounded.WarningAmber,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Il report aprirà una issue GitHub pubblica, visibile da chiunque. Non inserire token, credenziali, nomi di studenti, nomi di docenti, screenshot con dati personali o dettagli scolastici sensibili.",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        OutlinedTextField(
          value = title,
          onValueChange = {
            title = it
            copied = false
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Titolo") },
          singleLine = true,
        )
        OutlinedTextField(
          value = description,
          onValueChange = {
            description = it
            copied = false
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Descrizione") },
          minLines = 3,
        )
        OutlinedTextField(
          value = steps,
          onValueChange = {
            steps = it
            copied = false
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Passaggi per riprodurre") },
          minLines = 3,
        )
        OutlinedTextField(
          value = expected,
          onValueChange = {
            expected = it
            copied = false
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Comportamento atteso") },
          minLines = 2,
        )
        OutlinedTextField(
          value = actual,
          onValueChange = {
            actual = it
            copied = false
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Comportamento ottenuto") },
          minLines = 2,
        )
        if (copied) {
          Text(
            text = "Report copiato negli appunti.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          context.startActivity(Intent(Intent.ACTION_VIEW, issueUri))
          onDismiss()
        },
        enabled = canOpenIssue,
      ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
        Text("Apri GitHub")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
          onClick = {
            context.copyBugReport(reportBody)
            copied = true
          },
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null)
          Text("Copia report")
        }
        TextButton(onClick = onDismiss) {
          Text("Chiudi")
        }
      }
    },
  )
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

private fun buildBugReportBody(
  context: Context,
  currentRoute: String,
  description: String,
  steps: String,
  expected: String,
  actual: String,
): String {
  return """
    ## Descrizione
    ${reportValue(description)}

    ## Passaggi per riprodurre
    ${reportValue(steps)}

    ## Comportamento atteso
    ${reportValue(expected)}

    ## Comportamento ottenuto
    ${reportValue(actual)}

    ## Diagnostica anonima
    - App: ${context.appVersionLabel()}
    - Package: ${context.packageName}
    - Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
    - Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
    - Schermata: $currentRoute
    - Timestamp: ${java.time.OffsetDateTime.now()}
  """.trimIndent()
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

  AlertDialog(
    onDismissRequest = { if (!busy) onLater() },
    title = { Text("Aggiornamento ${update.version}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(statusText)
        if (busy) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onInstall,
        enabled = !busy && installState !is AppUpdateInstallState.Installed,
      ) {
        Text(if (installState is AppUpdateInstallState.Error) "Riprova" else "Aggiorna")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onIgnore, enabled = !busy) {
          Text("Ignora")
        }
        TextButton(onClick = onLater, enabled = !busy) {
          Text("Più tardi")
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
    ExpressiveLoading()
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

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      ExpressiveHeroCard(
        title = "Classeviva Expressive",
        subtitle = "Material 3 ufficiale per registro, agenda, voti e bacheca, tutta in Kotlin e Compose.",
        trailing = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
      )
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it
            if (error != null) onClearError()
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_username")
            .contentType(ContentType.Username + ContentType.EmailAddress),
          label = { Text("Username o codice studente") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
          ),
        )
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            if (error != null) onClearError()
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password")
            .contentType(ContentType.Password),
          label = { Text("Password") },
          singleLine = true,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
              Text(if (passwordVisible) "Nascondi" else "Mostra")
            }
          },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (error != null) {
          Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Button(
          onClick = ::submit,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_submit"),
          enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
        ) {
          if (isLoading) {
            ExpressiveLoading(
              modifier = Modifier.size(18.dp),
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Text("Accedi")
          }
        }
      }
    }
    item {
      EmptyState(
        title = "Autofill Compose",
        detail = "I campi credenziali espongono i content type ufficiali di Compose per username, email e password.",
      )
    }
  }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
internal fun TopLevelNavigationSuite(
  currentRoute: String?,
  showNavigationSuite: Boolean,
  onNavigateRoute: (String) -> Unit,
  content: @Composable () -> Unit,
) {
  val navigationSuiteState = rememberNavigationSuiteScaffoldState(NavigationSuiteScaffoldValue.Visible)

  LaunchedEffect(showNavigationSuite) {
    if (showNavigationSuite) {
      navigationSuiteState.show()
    } else {
      navigationSuiteState.hide()
    }
  }

  NavigationSuiteScaffold(
    state = navigationSuiteState,
    navigationSuiteItems = {
      topLevelDestinations.forEach { destination ->
        val selected = currentRoute == destination.baseRoute
        item(
          selected = selected,
          onClick = {
            if (!selected) {
              onNavigateRoute(destination.navigateRoute)
            }
          },
          icon = destination.icon,
          label = { Text(destination.label) },
        )
      }
    },
  ) {
    content()
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedRouteContainer(
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  sharedKey: String?,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .expressiveSharedBounds(
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedKey = sharedKey,
      ),
  ) {
    content()
  }
}

@Preview(name = "Login Light", showBackground = true)
@Preview(
  name = "Login Dark",
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LoginScreenPreview() {
  ClassevivaExpressiveTheme(settings = AppSettings()) {
    ExpressiveScreenSurface {
      LoginScreen(
        isLoading = false,
        error = null,
        onClearError = {},
        onLogin = { _, _ -> },
      )
    }
  }
}

@Preview(name = "Adaptive Shell", widthDp = 900, heightDp = 700, showBackground = true)
@Composable
private fun TopLevelNavigationSuitePreview() {
  ClassevivaExpressiveTheme(settings = AppSettings()) {
    ExpressiveScreenSurface {
      TopLevelNavigationSuite(
        currentRoute = "home",
        showNavigationSuite = true,
        onNavigateRoute = {},
      ) {
        MoreHubScreen(
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

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AuthenticatedApp(
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
  var activeSharedTransitionKey by rememberSaveable { mutableStateOf<String?>(null) }

  fun navigateRoute(route: String, sharedKey: String? = null) {
    activeSharedTransitionKey = sharedKey
    navController.navigate(route)
  }

  fun navigateTopLevelRoute(targetRoute: String) {
    val targetBaseRoute = normalizeRoute(targetRoute)
    if (targetBaseRoute == currentRoute) return
    activeSharedTransitionKey = null
    navController.navigate(targetRoute) {
      val currentDestinationId = currentDestination?.id
      if (currentRoute != "home" && targetBaseRoute != "home" && currentDestinationId != null) {
        popUpTo(currentDestinationId) {
          inclusive = true
          saveState = true
        }
      } else {
        popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
        }
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(navController, incomingIntents) {
    incomingIntents.collect { intent ->
      activeSharedTransitionKey = null
      navController.handleDeepLink(intent)
    }
  }

  TopLevelNavigationSuite(
    currentRoute = currentDestination?.hierarchy
      ?.mapNotNull { it.route?.substringBefore("?") }
      ?.firstOrNull { it in topLevelRoutes },
    showNavigationSuite = showNavigationSuite,
    onNavigateRoute = { targetRoute ->
      navigateTopLevelRoute(targetRoute)
    },
  ) {
    SharedTransitionLayout {
      NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
          routeEnterTransition(
            decision = decideRouteMotion(
              fromRoute = initialState.destination.route,
              toRoute = targetState.destination.route,
              requestedSharedKey = activeSharedTransitionKey,
            ),
            isPop = false,
          )
        },
        exitTransition = {
          routeExitTransition(
            decision = decideRouteMotion(
              fromRoute = initialState.destination.route,
              toRoute = targetState.destination.route,
              requestedSharedKey = activeSharedTransitionKey,
            ),
            isPop = false,
          )
        },
        popEnterTransition = {
          routeEnterTransition(
            decision = decideRouteMotion(
              fromRoute = initialState.destination.route,
              toRoute = targetState.destination.route,
              requestedSharedKey = activeSharedTransitionKey,
            ),
            isPop = true,
          )
        },
        popExitTransition = {
          routeExitTransition(
            decision = decideRouteMotion(
              fromRoute = initialState.destination.route,
              toRoute = targetState.destination.route,
              requestedSharedKey = activeSharedTransitionKey,
            ),
            isPop = true,
          )
        },
      ) {
        composable("home") {
          DashboardRoute(
            onNavigateGrades = { navigateRoute("grades", RouteSharedKeys.DashboardGrades) },
            onNavigateAgenda = { navigateRoute("agenda") },
            onNavigateLessons = { navigateRoute("lessons") },
            onNavigateCommunications = {
              navigateRoute("communications?tab=board", RouteSharedKeys.DashboardCommunications)
            },
            onOpenGrade = { gradeId -> navigateRoute("grades?gradeId=$gradeId", RouteSharedKeys.DashboardGrades) },
            gradesSharedTransitionKey = RouteSharedKeys.DashboardGrades,
            communicationsSharedTransitionKey = RouteSharedKeys.DashboardCommunications,
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
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
          AgendaRoute(
            initialAgendaId = entry.arguments?.getString("agendaId"),
            initialDate = entry.arguments?.getString("date"),
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
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
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.DashboardGrades },
          ) {
            GradesRoute(
              initialGradeId = entry.arguments?.getString("gradeId"),
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedVisibilityScope = this@composable,
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
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.DashboardCommunications },
          ) {
            CommunicationsRoute(
              initialTab = entry.arguments?.getString("tab") ?: "board",
              initialCommunicationPubId = entry.arguments?.getString("pubId"),
              initialCommunicationEvtCode = entry.arguments?.getString("evtCode"),
              initialNoteId = entry.arguments?.getString("noteId"),
              initialNoteCategoryCode = entry.arguments?.getString("categoryCode"),
              onBack = if (currentRoute == "communications") null else { { navController.navigateUp() } },
            )
          }
        }
        composable("notes") {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubNotes },
          ) {
            CommunicationsRoute(
              initialTab = "notes",
              onBack = navController::navigateUp,
            )
          }
        }
        composable("more") {
          MoreHubScreen(
            currentRoute = currentRoute ?: "more",
            onOpenNotes = { navigateRoute("notes", RouteSharedKeys.HubNotes) },
            onOpenLessons = { navigateRoute("lessons", RouteSharedKeys.HubLessons) },
            onOpenAbsences = { navigateRoute("absences", RouteSharedKeys.HubAbsences) },
            onOpenMaterials = { navigateRoute("materials", RouteSharedKeys.HubMaterials) },
            onOpenSettings = { navigateRoute("settings", RouteSharedKeys.HubSettings) },
            onOpenHomework = { navigateRoute("homework", RouteSharedKeys.HubHomework) },
            onOpenDocuments = { navigateRoute("documents", RouteSharedKeys.HubDocuments) },
            onOpenProfessors = { navigateRoute("professors", RouteSharedKeys.HubProfessors) },
            onOpenMeetings = { navigateRoute("meetings", RouteSharedKeys.HubMeetings) },
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
          )
        }
        composable("materials") {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubMaterials },
          ) {
            MaterialsRoute(onBack = navController::navigateUp)
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
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubHomework },
          ) {
            HomeworkRoute(
              initialHomeworkId = entry.arguments?.getString("homeworkId"),
              onBack = navController::navigateUp,
            )
          }
        }
        composable("documents") {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubDocuments },
          ) {
            DocumentsRoute(onBack = navController::navigateUp)
          }
        }
        composable(
          route = "lessons",
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/lessons" },
          ),
        ) {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubLessons },
          ) {
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
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubAbsences },
          ) {
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
        ) {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubMeetings },
          ) {
            MeetingsRoute(onBack = navController::navigateUp)
          }
        }
        composable("professors") {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubProfessors },
          ) {
            ProfessorsRoute(onBack = navController::navigateUp)
          }
        }
        composable(
          route = "settings",
          deepLinks = listOf(
            navDeepLink { uriPattern = "classevivaexpressive://open/settings" },
          ),
        ) {
          SharedRouteContainer(
            sharedTransitionScope = this@SharedTransitionLayout,
            animatedVisibilityScope = this@composable,
            sharedKey = activeSharedTransitionKey.takeIf { it == RouteSharedKeys.HubSettings },
          ) {
            SettingsRoute(
              onBack = navController::navigateUp,
              isCheckingForUpdates = isCheckingForUpdates,
              updateCheckMessage = updateCheckMessage,
              onCheckForUpdates = onCheckForUpdates,
              onClearUpdateCheckMessage = onClearUpdateCheckMessage,
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedVisibilityScope = this@composable,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreHubScreen(
  currentRoute: String = "more",
  onOpenLessons: () -> Unit,
  onOpenAbsences: () -> Unit,
  onOpenMaterials: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenNotes: () -> Unit,
  onOpenHomework: () -> Unit,
  onOpenDocuments: () -> Unit,
  onOpenProfessors: () -> Unit,
  onOpenMeetings: () -> Unit,
  sharedTransitionScope: SharedTransitionScope? = null,
  animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  var showBugReport by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Altro",
        subtitle = "Le sezioni secondarie restano ordinate per compito, con orario, assenze e profilo in una vista unica.",
        scrollBehavior = scrollBehavior,
      )
    },
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = paddingValues.calculateTopPadding() + 24.dp,
        bottom = paddingValues.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      item { ExpressiveAccentLabel("Registro") }
      item {
        RegisterListRow(
          title = "Orario",
          subtitle = "Lezioni del giorno e settimana corrente, generato automaticamente.",
          eyebrow = "Lezioni",
          tone = ExpressiveTone.Info,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubLessons,
          ),
          onClick = onOpenLessons,
          badge = { StatusBadge("ORARIO", tone = ExpressiveTone.Info) },
          leading = { Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Didattica",
          subtitle = "Materiali condivisi, link e file dei docenti.",
          eyebrow = "Didattica",
          tone = ExpressiveTone.Info,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubMaterials,
          ),
          onClick = onOpenMaterials,
          badge = { StatusBadge("DIDATTICA", tone = ExpressiveTone.Info) },
          leading = { Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Compiti",
          subtitle = "Compiti assegnati dai docenti con data di consegna.",
          eyebrow = "Agenda",
          tone = ExpressiveTone.Warning,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubHomework,
          ),
          onClick = onOpenHomework,
          badge = { StatusBadge("COMPITI", tone = ExpressiveTone.Warning) },
          leading = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Documenti e libri",
          subtitle = "Documenti della scuola, pagelle e libri scolastici adottati.",
          eyebrow = "Documenti",
          tone = ExpressiveTone.Info,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubDocuments,
          ),
          onClick = onOpenDocuments,
          badge = { StatusBadge("DOCUMENTI", tone = ExpressiveTone.Info) },
          leading = { Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Note disciplinari",
          subtitle = "Storico delle note e sanzioni disciplinari.",
          eyebrow = "Comunicazioni",
          tone = ExpressiveTone.Danger,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubNotes,
          ),
          onClick = onOpenNotes,
          badge = { StatusBadge("NOTE", tone = ExpressiveTone.Danger) },
          leading = { Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Assenze",
          subtitle = "Storico di assenze, ritardi e uscite anticipate.",
          eyebrow = "Presenze",
          tone = ExpressiveTone.Warning,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubAbsences,
          ),
          onClick = onOpenAbsences,
          badge = { StatusBadge("PRESENZE", tone = ExpressiveTone.Warning) },
          leading = { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Colloqui",
          subtitle = "Prenotazioni, disponibilita dei docenti e link per partecipare.",
          eyebrow = "Docenti",
          tone = ExpressiveTone.Info,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubMeetings,
          ),
          onClick = onOpenMeetings,
          badge = { StatusBadge("COLLOQUI", tone = ExpressiveTone.Info) },
          leading = { Icon(Icons.Rounded.PeopleAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Professori",
          subtitle = "Statistiche di presenza, voti assegnati e rigore per ogni docente.",
          eyebrow = "Docenti",
          tone = ExpressiveTone.Neutral,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubProfessors,
          ),
          onClick = onOpenProfessors,
          badge = { StatusBadge("PROF") },
          leading = { Icon(Icons.Rounded.PeopleAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item { ExpressiveAccentLabel("Profilo") }
      item {
        RegisterListRow(
          title = "Segnala bug",
          subtitle = "Prepara una issue GitHub con descrizione e diagnostica anonima.",
          eyebrow = "Feedback",
          tone = ExpressiveTone.Info,
          onClick = { showBugReport = true },
          badge = { StatusBadge("BUG", tone = ExpressiveTone.Info) },
          leading = { Icon(Icons.Rounded.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Profilo e impostazioni",
          subtitle = "Tema, notifiche, canali Android, sync periodica e account attivo.",
          eyebrow = "Profilo",
          tone = ExpressiveTone.Neutral,
          modifier = Modifier.expressiveSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = RouteSharedKeys.HubSettings,
          ),
          onClick = onOpenSettings,
          badge = { StatusBadge("PROFILO") },
          leading = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
      }
    }
  }

  if (showBugReport) {
    BugReportDialog(
      currentRoute = currentRoute,
      onDismiss = { showBugReport = false },
    )
  }
}
