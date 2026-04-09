package dev.antigravity.classevivaexpressive

import android.content.res.Configuration
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveScreenSurface
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaRoute
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DashboardRoute
import dev.antigravity.classevivaexpressive.feature.documents.DocumentsRoute
import dev.antigravity.classevivaexpressive.feature.grades.GradesRoute
import dev.antigravity.classevivaexpressive.feature.homework.HomeworkRoute
import dev.antigravity.classevivaexpressive.feature.lessons.LessonsRoute
import dev.antigravity.classevivaexpressive.feature.materials.MaterialsRoute
import dev.antigravity.classevivaexpressive.feature.meetings.MeetingsRoute
import dev.antigravity.classevivaexpressive.feature.settings.SettingsRoute
import dev.antigravity.classevivaexpressive.feature.studentscore.StudentScoreRoute

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

private val topLevelRoutes = topLevelDestinations.map { it.baseRoute }.toSet()

@Composable
fun MainApp(
  viewModel: MainViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  ClassevivaExpressiveTheme(settings = uiState.settings) {
    ExpressiveScreenSurface(modifier = Modifier.fillMaxSize()) {
      when {
        uiState.isLoading -> LoadingScreen()
        uiState.session == null -> LoginScreen(
          isLoading = uiState.isAuthenticating,
          error = uiState.authError,
          onClearError = viewModel::clearAuthError,
          onLogin = viewModel::login,
        )
        else -> AuthenticatedApp()
      }
    }
  }
}

@Composable
private fun LoadingScreen() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator()
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
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
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
        item(
          selected = currentRoute == destination.baseRoute,
          onClick = { onNavigateRoute(destination.navigateRoute) },
          icon = destination.icon,
          label = { Text(destination.label) },
        )
      }
    },
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
          onOpenNotes = {},
          onOpenHomework = {},
          onOpenMeetings = {},
          onOpenLessons = {},
          onOpenAbsences = {},
          onOpenMaterials = {},
          onOpenDocuments = {},
          onOpenStudentScore = {},
          onOpenSettings = {},
        )
      }
    }
  }
}

@Composable
private fun AuthenticatedApp() {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val currentRoute = currentDestination?.route?.substringBefore("?")
  val showNavigationSuite = currentRoute in topLevelRoutes

  TopLevelNavigationSuite(
    currentRoute = currentDestination?.hierarchy
      ?.mapNotNull { it.route?.substringBefore("?") }
      ?.firstOrNull { it in topLevelRoutes },
    showNavigationSuite = showNavigationSuite,
    onNavigateRoute = { targetRoute ->
      navController.navigate(targetRoute) {
        popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    },
  ) {
    NavHost(
      navController = navController,
      startDestination = "home",
      modifier = Modifier.fillMaxSize(),
      enterTransition = {
        fadeIn() + slideInHorizontally(initialOffsetX = { it / 8 })
      },
      exitTransition = {
        fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 10 })
      },
      popEnterTransition = {
        fadeIn() + slideInHorizontally(initialOffsetX = { -it / 8 })
      },
      popExitTransition = {
        fadeOut() + slideOutHorizontally(targetOffsetX = { it / 10 })
      },
    ) {
      composable("home") {
        DashboardRoute(
          onNavigateGrades = { navController.navigate("grades") },
          onNavigateAgenda = { navController.navigate("agenda") },
          onNavigateLessons = { navController.navigate("lessons") },
          onNavigateCommunications = { navController.navigate("communications?tab=board") },
          onOpenGrade = { gradeId -> navController.navigate("grades?gradeId=$gradeId") },
        )
      }
      composable("agenda") { AgendaRoute() }
      composable(
        route = "grades?gradeId={gradeId}",
        arguments = listOf(
          navArgument("gradeId") {
            nullable = true
            defaultValue = null
            type = NavType.StringType
          },
        ),
      ) { entry ->
        GradesRoute(initialGradeId = entry.arguments?.getString("gradeId"))
      }
      composable(
        route = "communications?tab={tab}",
        arguments = listOf(
          navArgument("tab") {
            defaultValue = "board"
            type = NavType.StringType
          },
        ),
      ) { entry ->
        CommunicationsRoute(
          initialTab = entry.arguments?.getString("tab") ?: "board",
          onBack = if (currentRoute == "communications") null else { { navController.navigateUp() } },
        )
      }
      composable("more") {
        MoreHubScreen(
          onOpenNotes = { navController.navigate("communications?tab=notes") },
          onOpenHomework = { navController.navigate("homework") },
          onOpenMeetings = { navController.navigate("meetings") },
          onOpenLessons = { navController.navigate("lessons") },
          onOpenAbsences = { navController.navigate("absences") },
          onOpenMaterials = { navController.navigate("materials") },
          onOpenDocuments = { navController.navigate("documents") },
          onOpenStudentScore = { navController.navigate("studentScore") },
          onOpenSettings = { navController.navigate("settings") },
        )
      }
      composable("homework") { HomeworkRoute(onBack = navController::navigateUp) }
      composable("meetings") { MeetingsRoute(onBack = navController::navigateUp) }
      composable("materials") { MaterialsRoute(onBack = navController::navigateUp) }
      composable("lessons") { LessonsRoute(onBack = navController::navigateUp) }
      composable(
        route = "documents?documentId={documentId}",
        arguments = listOf(
          navArgument("documentId") {
            nullable = true
            defaultValue = null
            type = NavType.StringType
          },
        ),
        deepLinks = listOf(
          navDeepLink { uriPattern = "classevivaexpressive://documents/open?documentId={documentId}" },
        ),
      ) {
        DocumentsRoute(onBack = navController::navigateUp)
      }
      composable("absences") { AbsencesRoute(onBack = navController::navigateUp) }
      composable("settings") { SettingsRoute(onBack = navController::navigateUp) }
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

@Composable
private fun MoreHubScreen(
  onOpenNotes: () -> Unit,
  onOpenHomework: () -> Unit,
  onOpenMeetings: () -> Unit,
  onOpenLessons: () -> Unit,
  onOpenAbsences: () -> Unit,
  onOpenMaterials: () -> Unit,
  onOpenDocuments: () -> Unit,
  onOpenStudentScore: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "Altro",
        subtitle = "Le sezioni secondarie restano ordinate per compito, con note, didattica e profilo in una vista unica.",
      )
    }
    item { ExpressiveAccentLabel("Registro") }
    item {
      RegisterListRow(
        title = "Compiti",
        subtitle = "Vista dedicata ai compiti con consegna nativa e allegati.",
        eyebrow = "Compiti",
        tone = ExpressiveTone.Primary,
        onClick = onOpenHomework,
        badge = { StatusBadge("COMPITI", tone = ExpressiveTone.Primary) },
        leading = { Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item {
      RegisterListRow(
        title = "Note e richiami",
        subtitle = "Apri annotazioni, richiami e note disciplinari in un unico flusso.",
        eyebrow = "Note",
        tone = ExpressiveTone.Warning,
        onClick = onOpenNotes,
        badge = { StatusBadge("NOTE", tone = ExpressiveTone.Warning) },
        leading = { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item {
      RegisterListRow(
        title = "Colloqui",
        subtitle = "Disponibilita, prenotazioni e link dei colloqui in app.",
        eyebrow = "Colloqui",
        tone = ExpressiveTone.Info,
        onClick = onOpenMeetings,
        badge = { StatusBadge("COLLOQUI", tone = ExpressiveTone.Info) },
        leading = { Icon(Icons.Rounded.PeopleAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item {
      RegisterListRow(
        title = "Orario",
        subtitle = "Lezioni del giorno e settimana corrente, anche con fallback dagli eventi agenda.",
        eyebrow = "Lezioni",
        tone = ExpressiveTone.Info,
        onClick = onOpenLessons,
        badge = { StatusBadge("ORARIO", tone = ExpressiveTone.Info) },
        leading = { Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item {
      RegisterListRow(
        title = "Assenze",
        subtitle = "Storico di assenze, ritardi e uscite anticipate separati correttamente.",
        eyebrow = "Presenze",
        tone = ExpressiveTone.Warning,
        onClick = onOpenAbsences,
        badge = { StatusBadge("PRESENZE", tone = ExpressiveTone.Warning) },
        leading = { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item { ExpressiveAccentLabel("Didattica") }
    item {
      RegisterListRow(
        title = "Materiale didattico",
        subtitle = "Cartelle, file, link e aperture esterne gestite in modo robusto.",
        eyebrow = "Materiali",
        tone = ExpressiveTone.Primary,
        onClick = onOpenMaterials,
        badge = { StatusBadge("MATERIALI", tone = ExpressiveTone.Primary) },
        leading = { Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item {
      RegisterListRow(
        title = "Documenti e libri",
        subtitle = "Pagelle, documenti scaricabili e libri di testo del corso.",
        eyebrow = "Documenti",
        tone = ExpressiveTone.Success,
        onClick = onOpenDocuments,
        badge = { StatusBadge("DOCUMENTI", tone = ExpressiveTone.Success) },
        leading = { Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item {
      RegisterListRow(
        title = "Student Score",
        subtitle = "Confronto, importazione ed export del riepilogo studente.",
        eyebrow = "Extra",
        tone = ExpressiveTone.Success,
        onClick = onOpenStudentScore,
        badge = { StatusBadge("SCORE", tone = ExpressiveTone.Success) },
        leading = { Icon(Icons.Rounded.SportsScore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
    item { ExpressiveAccentLabel("Profilo") }
    item {
      RegisterListRow(
        title = "Profilo e impostazioni",
        subtitle = "Tema, notifiche, canali Android, sync periodica e account attivo.",
        eyebrow = "Profilo",
        tone = ExpressiveTone.Neutral,
        onClick = onOpenSettings,
        badge = { StatusBadge("PROFILO") },
        leading = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      )
    }
  }
}
