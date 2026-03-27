package dev.antigravity.classevivaexpressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveSimpleListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveScreenSurface
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesRoute
import dev.antigravity.classevivaexpressive.feature.agenda.AgendaRoute
import dev.antigravity.classevivaexpressive.feature.communications.CommunicationsRoute
import dev.antigravity.classevivaexpressive.feature.dashboard.DashboardRoute
import dev.antigravity.classevivaexpressive.feature.documents.DocumentsRoute
import dev.antigravity.classevivaexpressive.feature.grades.GradesRoute
import dev.antigravity.classevivaexpressive.feature.lessons.LessonsRoute
import dev.antigravity.classevivaexpressive.feature.materials.MaterialsRoute
import dev.antigravity.classevivaexpressive.feature.settings.SettingsRoute
import dev.antigravity.classevivaexpressive.feature.studentscore.StudentScoreRoute

private data class TopLevelDestination(
  val route: String,
  val label: String,
  val icon: @Composable () -> Unit,
)

private val topLevelDestinations = listOf(
  TopLevelDestination("home", "Home", { Icon(Icons.Rounded.Home, contentDescription = null) }),
  TopLevelDestination("agenda", "Agenda", { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) }),
  TopLevelDestination("grades", "Voti", { Icon(Icons.Rounded.Grade, contentDescription = null) }),
  TopLevelDestination("lessons", "Orario", { Icon(Icons.Rounded.Schedule, contentDescription = null) }),
  TopLevelDestination("more", "Altro", { Icon(Icons.Rounded.Menu, contentDescription = null) }),
)

private val topLevelRoutes = topLevelDestinations.map { it.route }.toSet()

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
private fun LoginScreen(
  isLoading: Boolean,
  error: String?,
  onClearError: () -> Unit,
  onLogin: (String, String) -> Unit,
) {
  var username by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveHeroCard(
        title = "Classeviva Expressive",
        subtitle = "Registro quotidiano Android nativo, piu veloce, moderno e coerente in ogni sezione.",
        trailing = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
      )
    }
    item {
      OutlinedTextField(
        value = username,
        onValueChange = {
          username = it
          if (error != null) onClearError()
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Username studente") },
        singleLine = true,
      )
    }
    item {
      OutlinedTextField(
        value = password,
        onValueChange = {
          password = it
          if (error != null) onClearError()
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Password") },
        singleLine = true,
      )
    }
    if (error != null) {
      item {
        Text(
          text = error,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
    item {
      Button(
        onClick = { onLogin(username, password) },
        modifier = Modifier.fillMaxWidth(),
        enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
      ) {
        if (isLoading) {
          CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        } else {
          Text("Accedi")
        }
      }
    }
    item {
      EmptyState(
        title = "Single account, login studente",
        detail = "La nuova base nativa parte da un account singolo e ripristina la sessione in modo sicuro.",
      )
    }
  }
}

@Composable
private fun AuthenticatedApp() {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val currentRoute = currentDestination?.route?.substringBefore("?")
  val showBottomBar = currentRoute in topLevelRoutes

  Scaffold(
    bottomBar = {
      if (showBottomBar) {
        NavigationBar {
          topLevelDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(destination.route) {
                  popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                  }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = destination.icon,
              label = { Text(destination.label) },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = "home",
      modifier = Modifier.padding(innerPadding),
    ) {
      composable("home") {
        DashboardRoute(
          onNavigateGrades = { navController.navigate("grades") },
          onNavigateAgenda = { navController.navigate("agenda") },
          onNavigateLessons = { navController.navigate("lessons") },
          onNavigateMore = { navController.navigate("more") },
        )
      }
      composable("agenda") { AgendaRoute() }
      composable("grades") { GradesRoute() }
      composable("lessons") { LessonsRoute() }
      composable("more") {
        MoreHubScreen(
          onOpenLessons = { navController.navigate("lessons") },
          onOpenAbsences = { navController.navigate("absences") },
          onOpenCommunications = { navController.navigate("communications") },
          onOpenMaterials = { navController.navigate("materials") },
          onOpenDocuments = { navController.navigate("documents") },
          onOpenStudentScore = { navController.navigate("studentScore") },
          onOpenSettings = { navController.navigate("settings") },
        )
      }
      composable("communications") { CommunicationsRoute(onBack = navController::navigateUp) }
      composable("materials") { MaterialsRoute(onBack = navController::navigateUp) }
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
      composable("settings") { SettingsRoute() }
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
  onOpenLessons: () -> Unit,
  onOpenAbsences: () -> Unit,
  onOpenCommunications: () -> Unit,
  onOpenMaterials: () -> Unit,
  onOpenDocuments: () -> Unit,
  onOpenStudentScore: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      ExpressiveTopHeader(title = "More")
    }
    item {
      ExpressiveAccentLabel("General")
    }
    item {
      ExpressiveSimpleListRow(
        title = "Lessons",
        subtitle = "Topics and classroom details",
        onClick = onOpenLessons,
        trailing = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
      )
    }
    item {
      ExpressiveSimpleListRow(
        title = "School material",
        subtitle = "Files and folders shared by teachers",
        onClick = onOpenMaterials,
        trailing = { Icon(Icons.Rounded.Folder, contentDescription = null) },
      )
    }
    item {
      ExpressiveSimpleListRow(
        title = "Absences",
        subtitle = "Assenze, ritardi e uscite",
        onClick = onOpenAbsences,
        trailing = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
      )
    }
    item {
      ExpressiveSimpleListRow(
        title = "Notes and notice board",
        subtitle = "Comunicazioni, circolari e richiami",
        onClick = onOpenCommunications,
        trailing = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
      )
    }
    item {
      ExpressiveSimpleListRow(
        title = "Final grades and documents",
        subtitle = "Pagelle, documenti e libri del corso",
        onClick = onOpenDocuments,
        trailing = { Icon(Icons.Rounded.Description, contentDescription = null) },
      )
    }
    item {
      ExpressiveSimpleListRow(
        title = "Student Score",
        subtitle = "Confronto, export e import tramite deep link",
        onClick = onOpenStudentScore,
        trailing = { Icon(Icons.Rounded.SportsScore, contentDescription = null) },
      )
    }
    item {
      EmptyState(
        title = "Altro ma ordinato",
        detail = "Questa schermata ora fa da indice rapido, mentre ogni sezione interna ha un layout dedicato molto più vicino al registro di riferimento.",
      )
    }
    item {
      ExpressiveAccentLabel("Other")
    }
    item {
      ExpressiveSimpleListRow(
        title = "Settings",
        subtitle = "Tema, AMOLED, accenti e sincronizzazione",
        onClick = onOpenSettings,
        trailing = { Icon(Icons.Rounded.Settings, contentDescription = null) },
      )
    }
  }
}
