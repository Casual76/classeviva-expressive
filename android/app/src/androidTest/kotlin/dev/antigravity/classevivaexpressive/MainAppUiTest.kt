package dev.antigravity.classevivaexpressive

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import dev.antigravity.fluidengine.ui.fluid.FluidListDetailScaffold
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.antigravity.fluidengine.ui.navigation.navigateToPeer
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.assertTextEquals
import dev.antigravity.classevivaexpressive.feature.settings.CieContributionCredit
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface

class MainAppUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun editableTagged(tag: String) = composeRule.onNode(
    hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true,
  )

  // Expanded Fluid tabs merge their label and Selected into the clickable parent. The icon has
  // no content description, and matching its text alone would also find the current page title.
  private fun navigationTab(label: String) = composeRule.onNode(hasText(label) and hasClickAction())

  @Test fun reauthenticationCanBeDismissedWithoutRemovingTheOfflinePage() {
    var visible by mutableStateOf(true)
    var cieLaunches = 0
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          FluidScreenSurface {
            Box(Modifier.fillMaxSize()) {
              Text("Dati salvati offline")
              AuthenticationSheet(visible, "qa-student", false, null, { visible = false }, {}, { _, _ -> }, { cieLaunches++ })
              FluidGlassModalHost(host, backdrop = null)
            }
          }
        }
      }
    }
    composeRule.onNodeWithTag("reauth_cie").assertIsEnabled().performClick()
    assertEquals(1, cieLaunches)
    composeRule.onNodeWithText("Continua offline").performClick()
    composeRule.onNodeWithTag("reauth_cie").assertDoesNotExist()
    composeRule.onNodeWithText("Dati salvati offline").assertIsDisplayed()
  }

  @Test fun reauthenticationImeSubmissionOwnsBothAuthenticationActions() {
    var busy by mutableStateOf(false)
    var submitted: Pair<String, String>? = null
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          FluidScreenSurface {
            AuthenticationSheet(true, "qa-student", busy, null, {}, {}, { username, password ->
              submitted = username to password
              busy = true
            }, {})
            FluidGlassModalHost(host, backdrop = null)
          }
        }
      }
    }
    editableTagged("reauth_password").performTextInput("synthetic-secret")
    editableTagged("reauth_password").performImeAction()
    assertEquals("qa-student" to "synthetic-secret", submitted)
    composeRule.onNodeWithTag("reauth_submit").assertIsNotEnabled()
    composeRule.onNodeWithTag("reauth_cie").assertIsNotEnabled()
    composeRule.onNodeWithText("Continua offline").assertIsNotEnabled()
  }

  @Test fun cieIsAvailableWithoutTypingCredentialsAndSharesTheBusyState() {
    var launches = 0
    var busy by mutableStateOf(false)
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        FluidScreenSurface {
          LoginScreen(busy, null, {}, { _, _ -> }, onCieLogin = { launches++ })
        }
      }
    }
    composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
    composeRule.onNodeWithTag("login_cie").assertIsEnabled().performClick()
    assertEquals(1, launches)
    composeRule.runOnIdle { busy = true }
    composeRule.onNodeWithTag("login_cie").assertIsNotEnabled()
    composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
  }

  @Test fun loginPasswordNeverSurvivesSavedStateRestoration() {
    val restoration = StateRestorationTester(composeRule)
    restoration.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        FluidScreenSurface { LoginScreen(false, null, {}, { _, _ -> }) }
      }
    }
    editableTagged("login_username").performTextInput("qa-student")
    editableTagged("login_password").performTextInput("synthetic-secret")
    restoration.emulateSavedInstanceStateRestore()
    editableTagged("login_username").assertTextContains("qa-student")
    editableTagged("login_password").assertTextEquals("")
  }

  @Test fun cieContributionIsVisibleWithItsAnonymousCredit() {
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) { FluidScreenSurface { CieContributionCredit() } }
    }
    composeRule.onNodeWithText("Leone").assertIsDisplayed()
    composeRule.onNodeWithText("Metodo [omissis] nella fossa dei leoni: è entrato fra le API di Spaggiari ed è uscito con il token.").assertIsDisplayed()
  }

  @Test
  fun loginScreen_enablesSubmitAndInvokesLogin() {
    var submittedCredentials: Pair<String, String>? = null

    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        FluidScreenSurface {
          LoginScreen(
            isLoading = false,
            error = null,
            onClearError = {},
            onLogin = { username, password ->
              submittedCredentials = username to password
            },
          )
        }
      }
    }

    composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
    editableTagged("login_username").performTextInput("studente")
    composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
    editableTagged("login_password").performTextInput("password123")
    composeRule.onNodeWithTag("login_submit").assertIsEnabled()
    composeRule.onNodeWithTag("login_submit").performClick()

    assertEquals("studente" to "password123", submittedCredentials)
  }

  @Test
  fun realNavHost_movesHomeBoardHome_reselectionDoesNotBuildStack() {
    lateinit var navController: NavHostController
    composeRule.setContent {
      NavigationTestHarness(
        width = 360.dp,
        height = 800.dp,
        fontScale = 2f,
        onController = { navController = it },
      )
    }

    navigationTab("Home").assertIsSelected()
    navigationTab("Bacheca").assertIsNotSelected()
    composeRule.onNodeWithTag("route_home").assertIsDisplayed()

    val homeBounds = navigationTab("Home").fetchSemanticsNode().boundsInRoot
    val boardBounds = navigationTab("Bacheca").fetchSemanticsNode().boundsInRoot
    val moreBounds = navigationTab("Altro").fetchSemanticsNode().boundsInRoot
    val viewportBounds = composeRule.onNodeWithTag("theme_light").fetchSemanticsNode().boundsInRoot
    assertTrue(
      "La pillola compact deve disporre i tab in ordine orizzontale",
      homeBounds.center.x < boardBounds.center.x && boardBounds.center.x < moreBounds.center.x,
    )
    assertTrue("Il primo tab deve restare dentro il viewport", homeBounds.left >= viewportBounds.left)
    assertTrue("L'ultimo tab deve restare dentro il viewport", moreBounds.right <= viewportBounds.right)

    navigationTab("Home").performClick()
    composeRule.runOnIdle {
      assertEquals("home", navController.currentDestination?.route)
    }

    navigationTab("Voti").assertExists()
    navigationTab("Agenda").assertExists()
    navigationTab("Bacheca").assertExists().performClick()
    navigationTab("Bacheca").assertIsSelected()
    composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
    lateinit var stackBeforeReselection: List<String>
    composeRule.runOnIdle {
      stackBeforeReselection = navController.currentBackStack.value.mapNotNull { it.destination.route }
    }
    navigationTab("Bacheca").performClick()
    composeRule.runOnIdle {
      assertEquals("communications?tab={tab}", navController.currentDestination?.route)
      assertEquals(
        "La reselezione non deve aggiungere entry allo stack",
        stackBeforeReselection,
        navController.currentBackStack.value.mapNotNull { it.destination.route },
      )
    }
    navigationTab("Bacheca").assertIsSelected()

    navigationTab("Home").performClick()
    navigationTab("Home").assertIsSelected()
    composeRule.onNodeWithTag("route_home").assertIsDisplayed()
    composeRule.runOnIdle {
      val routeBeforeBack = navController.currentDestination?.route
      val previousRouteBeforeBack = navController.previousBackStackEntry?.destination?.route
      assertFalse(
        "Back da Home non deve riaprire Bacheca (current=$routeBeforeBack, previous=$previousRouteBeforeBack)",
        navController.popBackStack(),
      )
    }
  }

  @Test
  fun realNavHost_handlesDeepLink_andRestoresDestinationAfterSavedStateRecreation() {
    lateinit var navController: NavHostController
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent {
      NavigationTestHarness(onController = { navController = it })
    }

    composeRule.runOnIdle {
      assertTrue(
        navController.handleIncomingIntent(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse("classevivaexpressive://open/communications?tab=board"),
          ),
        ),
      )
    }
    composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
    navigationTab("Bacheca").assertIsSelected()

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
    navigationTab("Bacheca").assertIsSelected()
    composeRule.runOnIdle {
      assertEquals("communications?tab={tab}", navController.currentDestination?.route)
    }
  }

  @Test
  fun bugReport_isARealDestination_andRestoresFormState() {
    lateinit var navController: NavHostController
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings(dynamicColorEnabled = false)) {
        FluidScreenSurface {
          BugReportNavigationTestGraph(onController = { navController = it })
        }
      }
    }

    composeRule.onNodeWithTag("open_bug_report").performClick()
    editableTagged("bug_report_title").performTextInput("Animazione interrotta")
    editableTagged("bug_report_description").performTextInput("La pagina si sovrappone")

    composeRule.runOnIdle {
      assertEquals(BugReportRoute, navController.currentDestination?.route)
      assertEquals("more", navController.previousBackStackEntry?.destination?.route)
    }

    restorationTester.emulateSavedInstanceStateRestore()

    editableTagged("bug_report_title")
      .assertTextContains("Animazione interrotta")
    editableTagged("bug_report_description")
      .assertTextContains("La pagina si sovrappone")
    composeRule.runOnIdle {
      assertEquals(BugReportRoute, navController.currentDestination?.route)
      assertTrue(navController.navigateUp())
    }
    composeRule.onNodeWithTag("bug_report_origin").assertIsDisplayed()
  }

  @Test
  fun bugReport_keepsTheOpeningRouteInEditableDiagnostics() {
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings(dynamicColorEnabled = false)) {
        FluidScreenSurface {
          BugReportNavigationTestGraph(onController = {})
        }
      }
    }

    composeRule.onNodeWithTag("open_bug_report").performClick()
    composeRule.onNodeWithText("Aggiungi passaggi e diagnostica").performScrollTo().performClick()
    composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("bug_report_diagnostics"))
    editableTagged("bug_report_diagnostics")
      .performScrollTo()
      .assertTextContains("Schermata: more", substring = true)
  }

  @Test
  fun bottomBarAt600dp_darkTheme_fontScale13_andDisabledMotion_remainsUsable() {
    withSystemAnimatorScale(0f) {
      composeRule.setContent {
        NavigationTestHarness(
          width = 600.dp,
          height = 800.dp,
          fontScale = 1.3f,
          settings = AppSettings(
            themeMode = ThemeMode.DARK,
            dynamicColorEnabled = false,
          ),
        )
      }

      composeRule.onNodeWithTag("theme_dark").assertExists()
      composeRule.onNodeWithTag("motion_zero").assertExists()
      navigationTab("Home").assertIsSelected()

      val homeBounds = navigationTab("Home").fetchSemanticsNode().boundsInRoot
      val boardBounds = navigationTab("Bacheca").fetchSemanticsNode().boundsInRoot
      // Il rail del tablet non esiste piu': a 600 dp la navigazione e' la stessa pillola del
      // telefono, in orizzontale e raccolta a sinistra, dalla parte del pollice.
      assertTrue("A 600 dp i tab stanno in fila, non in colonna", boardBounds.left > homeBounds.right)
      assertTrue("A 600 dp la pillola sta a sinistra", homeBounds.left < 300.dp.value * composeRule.density.density)

      navigationTab("Bacheca").performClick()
      composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
      navigationTab("Bacheca").assertIsSelected()
    }
  }

  @Test
  fun listDetail_twoPanesOnlyWhenBothFit() {
    var width by mutableStateOf(1280.dp)
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings(dynamicColorEnabled = false)) {
        Box(modifier = Modifier.requiredWidth(width).requiredHeight(800.dp)) {
          FluidListDetailScaffold(
            ambient = null,
            list = { twoPane -> TestRoute(tag = if (twoPane) "list_two" else "list_one", label = "Elenco") },
            detail = { TestRoute(tag = "detail", label = "Dettaglio") },
          )
        }
      }
    }

    // Tablet in orizzontale: elenco e dettaglio affiancati.
    composeRule.onNodeWithTag("list_two").assertIsDisplayed()
    composeRule.onNodeWithTag("detail").assertIsDisplayed()

    // Tablet in verticale o finestra divisa: una pagina sola, e il dettaglio non esiste.
    width = 800.dp
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("list_one").assertIsDisplayed()
    composeRule.onNodeWithTag("detail").assertDoesNotExist()
  }

  @Test
  fun sectionIndex_rtlFontScale2_exposesAdjustableProgress() {
    val sections = (0 until 5).map { index ->
      FluidSectionAnchor(
        key = "section-$index",
        label = "Sezione ${index + 1}",
        itemIndex = index * 10,
      )
    }
    var selectedKey = sections.first().key

    composeRule.setContent {
      val systemDensity = LocalDensity.current
      CompositionLocalProvider(
        LocalDensity provides Density(systemDensity.density, 2f),
        LocalLayoutDirection provides LayoutDirection.Rtl,
      ) {
        ClassevivaExpressiveTheme(settings = AppSettings(dynamicColorEnabled = false)) {
          Box(modifier = Modifier.fillMaxSize()) {
            FluidSectionIndex(
              sections = sections,
              activeSectionKey = selectedKey,
              onSelectSection = { anchor, _ -> selectedKey = anchor.key },
            )
          }
        }
      }
    }

    composeRule.onNodeWithContentDescription("Indice sezioni")
      .assertIsDisplayed()
      .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
        assertTrue(setProgress(3f))
      }
    composeRule.runOnIdle { assertEquals("section-3", selectedKey) }
  }

  private fun withSystemAnimatorScale(scale: Float, block: () -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val resolver = instrumentation.targetContext.contentResolver
    val previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    try {
      instrumentation.uiAutomation
        .executeShellCommand("settings put global animator_duration_scale $scale")
        .close()
      SystemClock.sleep(250)
      assertEquals(scale, Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
      block()
    } finally {
      instrumentation.uiAutomation
        .executeShellCommand("settings put global animator_duration_scale $previousScale")
        .close()
      SystemClock.sleep(250)
    }
  }
}

private val testTopLevelRoutes = setOf("home", "grades", "agenda", "communications", "more")

@Composable
private fun NavigationTestHarness(
  width: Dp = 360.dp,
  height: Dp = 800.dp,
  fontScale: Float = 1f,
  settings: AppSettings = AppSettings(dynamicColorEnabled = false),
  onController: (NavHostController) -> Unit = {},
) {
  val systemDensity = LocalDensity.current
  CompositionLocalProvider(
    LocalDensity provides Density(systemDensity.density, fontScale),
  ) {
    ClassevivaExpressiveTheme(settings = settings) {
      val themeTag = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "theme_dark" else "theme_light"
      val animatorScale = Settings.Global.getFloat(
        LocalContext.current.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
      Box(
        modifier = Modifier
          .requiredWidth(width)
          .requiredHeight(height)
          .testTag(themeTag),
      ) {
        FluidScreenSurface {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .testTag(if (animatorScale == 0f) "motion_zero" else "motion_enabled"),
          ) {
            NavigationTestGraph(onController = onController)
          }
        }
      }
    }
  }
}

@Composable
private fun NavigationTestGraph(
  onController: (NavHostController) -> Unit,
) {
  val navController = rememberNavController()
  val entry by navController.currentBackStackEntryAsState()
  val currentTopLevelRoute = entry?.destination?.hierarchy
    ?.mapNotNull { destination -> destination.route?.substringBefore('?') }
    ?.firstOrNull { route -> route in testTopLevelRoutes }

  SideEffect { onController(navController) }

  TopLevelNavigationSuite(
    currentRoute = currentTopLevelRoute,
    showNavigationSuite = currentTopLevelRoute != null,
    onNavigateRoute = { route -> navController.navigateToPeer(route) },
  ) {
    NavHost(
      navController = navController,
      startDestination = "home",
      modifier = Modifier.fillMaxSize(),
    ) {
      composable("home") { TestRoute("route_home", "Home") }
      composable("grades") { TestRoute("route_grades", "Voti") }
      composable("agenda") { TestRoute("route_agenda", "Agenda") }
      composable(
        route = "communications?tab={tab}",
        arguments = listOf(
          navArgument("tab") {
            defaultValue = "board"
            type = NavType.StringType
          },
        ),
        deepLinks = listOf(
          navDeepLink { uriPattern = "classevivaexpressive://open/communications?tab={tab}" },
        ),
      ) {
        TestRoute("route_communications", "Bacheca")
      }
      composable("more") { TestRoute("route_more", "Altro") }
    }
  }
}

@Composable
private fun BugReportNavigationTestGraph(
  onController: (NavHostController) -> Unit,
) {
  val navController = rememberNavController()
  SideEffect { onController(navController) }

  NavHost(
    navController = navController,
    startDestination = "more",
    modifier = Modifier.fillMaxSize(),
  ) {
    composable("more") {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .testTag("bug_report_origin"),
        contentAlignment = androidx.compose.ui.Alignment.Center,
      ) {
        Button(
          onClick = { navController.navigate(BugReportRoute) },
          modifier = Modifier.testTag("open_bug_report"),
        ) {
          Text("Segnala un problema")
        }
      }
    }
    composable(BugReportRoute) {
      BugReportScreen(
        currentRoute = "more",
        onBack = navController::navigateUp,
      )
    }
  }

}

@Composable
private fun TestRoute(tag: String, label: String) {
  Box(modifier = Modifier.fillMaxSize().testTag(tag)) {
    Text(label)
  }
}
