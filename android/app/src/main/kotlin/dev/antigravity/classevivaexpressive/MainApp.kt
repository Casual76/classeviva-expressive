package dev.antigravity.classevivaexpressive

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doAfterTextChanged
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
private fun LoginScreen(
  isLoading: Boolean,
  error: String?,
  onClearError: () -> Unit,
  onLogin: (String, String) -> Unit,
) {
  var username by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var passwordField by remember { mutableStateOf<AppCompatEditText?>(null) }
  val focusManager = LocalFocusManager.current

  fun submit() {
    if (username.isNotBlank() && password.isNotBlank() && !isLoading) {
      onLogin(username, password)
      focusManager.clearFocus()
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveHeroCard(
        title = "Classeviva Expressive",
        subtitle = "Versione Android nativa centrata su oggi, voti, agenda e bacheca.",
        trailing = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
      )
    }
    item {
      CredentialInputField(
        value = username,
        onValueChange = {
          username = it
          if (error != null) onClearError()
        },
        label = "Username o codice studente",
        autofillHints = arrayOf(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS),
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next,
        onImeAction = { passwordField?.requestFocus() },
      )
    }
    item {
      CredentialInputField(
        value = password,
        onValueChange = {
          password = it
          if (error != null) onClearError()
        },
        label = "Password",
        autofillHints = arrayOf(View.AUTOFILL_HINT_PASSWORD),
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        isPassword = true,
        onImeAction = ::submit,
        onViewReady = { passwordField = it },
      )
    }
    if (error != null) {
      item {
        Text(
          text = error,
          modifier = Modifier.padding(top = 4.dp),
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
    item {
      Button(
        onClick = ::submit,
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
        title = "Autofill compatibile",
        detail = "I campi credenziali sono configurati per username e password correnti, così password manager e autofill Android possono agganciarsi meglio.",
      )
    }
  }
}

@Composable
private fun CredentialInputField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  autofillHints: Array<String>,
  keyboardType: KeyboardType,
  imeAction: ImeAction,
  modifier: Modifier = Modifier,
  isPassword: Boolean = false,
  onImeAction: () -> Unit = {},
  onViewReady: (AppCompatEditText) -> Unit = {},
) {
  val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
  val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
  val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AndroidView(
      modifier = Modifier.fillMaxWidth(),
      factory = { context ->
        AppCompatEditText(context).apply {
          importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
          setAutofillHints(*autofillHints)
          setSingleLine()
          setHint(label)
          setText(value)
          setTextColor(textColor)
          setHintTextColor(hintColor)
          setPadding(40, 32, 40, 32)
          setBackgroundColor(containerColor)
          imeOptions = editorInfoFor(imeAction)
          inputType = inputTypeFor(keyboardType, isPassword)
          transformationMethod = if (isPassword) PasswordTransformationMethod.getInstance() else null
          doAfterTextChanged { onValueChange(it?.toString().orEmpty()) }
          setOnEditorActionListener { _, actionId, _ ->
            if (actionId == editorInfoFor(imeAction)) {
              onImeAction()
              true
            } else {
              false
            }
          }
          onViewReady(this)
        }
      },
      update = { editText ->
        if (editText.text?.toString().orEmpty() != value) {
          editText.setText(value)
          editText.setSelection(value.length)
        }
        editText.setTextColor(textColor)
        editText.setHintTextColor(hintColor)
        editText.setBackgroundColor(containerColor)
      },
    )
  }
}

private fun editorInfoFor(imeAction: ImeAction): Int {
  return when (imeAction) {
    ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
    ImeAction.Done -> EditorInfo.IME_ACTION_DONE
    else -> EditorInfo.IME_ACTION_UNSPECIFIED
  }
}

private fun inputTypeFor(keyboardType: KeyboardType, isPassword: Boolean): Int {
  return when {
    isPassword -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    keyboardType == KeyboardType.Email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
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
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          tonalElevation = 0.dp,
        ) {
          topLevelDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route?.substringBefore("?") == destination.baseRoute } == true
            val scale by animateFloatAsState(
              targetValue = if (selected) 1.08f else 1f,
              animationSpec = tween(durationMillis = 220),
              label = "nav-item-scale",
            )
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(destination.navigateRoute) {
                  popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                  }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Box(
                  modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                  },
                ) {
                  destination.icon()
                }
              },
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
      enterTransition = {
        fadeIn(animationSpec = tween(220)) + slideInHorizontally(
          initialOffsetX = { it / 8 },
          animationSpec = tween(260),
        )
      },
      exitTransition = {
        fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
          targetOffsetX = { -it / 10 },
          animationSpec = tween(220),
        )
      },
      popEnterTransition = {
        fadeIn(animationSpec = tween(220)) + slideInHorizontally(
          initialOffsetX = { -it / 8 },
          animationSpec = tween(260),
        )
      },
      popExitTransition = {
        fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
          targetOffsetX = { it / 10 },
          animationSpec = tween(220),
        )
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
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "Altro",
        subtitle = "Le sezioni secondarie restano ordinate per compito, con Note in evidenza ma fuori dalla barra principale.",
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
        subtitle = "Apri direttamente annotazioni, richiami e note disciplinari.",
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
