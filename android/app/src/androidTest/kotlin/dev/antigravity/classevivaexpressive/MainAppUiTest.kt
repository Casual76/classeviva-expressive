package dev.antigravity.classevivaexpressive

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
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface

class MainAppUiTest {

  @get:Rule
  val composeRule = createComposeRule()

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
    composeRule.onNodeWithTag("login_username").performTextInput("studente")
    composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
    composeRule.onNodeWithTag("login_password").performTextInput("password123")
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

    composeRule.onNodeWithTag("top_level_home").assertIsSelected()
    composeRule.onNodeWithTag("top_level_communications").assertIsNotSelected()
    composeRule.onNodeWithTag("route_home").assertIsDisplayed()

    val homeBounds = composeRule.onNodeWithTag("top_level_home").fetchSemanticsNode().boundsInRoot
    val boardBounds = composeRule.onNodeWithTag("top_level_communications").fetchSemanticsNode().boundsInRoot
    val moreBounds = composeRule.onNodeWithTag("top_level_more").fetchSemanticsNode().boundsInRoot
    val viewportBounds = composeRule.onNodeWithTag("theme_light").fetchSemanticsNode().boundsInRoot
    assertTrue(
      "La pillola compact deve disporre i tab in ordine orizzontale",
      homeBounds.center.x < boardBounds.center.x && boardBounds.center.x < moreBounds.center.x,
    )
    assertTrue("Il primo tab deve restare dentro il viewport", homeBounds.left >= viewportBounds.left)
    assertTrue("L'ultimo tab deve restare dentro il viewport", moreBounds.right <= viewportBounds.right)

    composeRule.onNodeWithTag("top_level_home").performClick()
    composeRule.runOnIdle {
      assertEquals("home", navController.currentDestination?.route)
    }

    composeRule.onNodeWithContentDescription("Voti").assertExists()
    composeRule.onNodeWithContentDescription("Agenda").assertExists()
    composeRule.onNodeWithContentDescription("Bacheca").assertExists().performClick()
    composeRule.onNodeWithTag("top_level_communications").assertIsSelected()
    composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
    lateinit var stackBeforeReselection: List<String>
    composeRule.runOnIdle {
      stackBeforeReselection = navController.currentBackStack.value.mapNotNull { it.destination.route }
    }
    composeRule.onNodeWithTag("top_level_communications").performClick()
    composeRule.runOnIdle {
      assertEquals("communications?tab={tab}", navController.currentDestination?.route)
      assertEquals(
        "La reselezione non deve aggiungere entry allo stack",
        stackBeforeReselection,
        navController.currentBackStack.value.mapNotNull { it.destination.route },
      )
    }
    composeRule.onNodeWithTag("top_level_communications").assertIsSelected()

    composeRule.onNodeWithContentDescription("Home").performClick()
    composeRule.onNodeWithTag("top_level_home").assertIsSelected()
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
    composeRule.onNodeWithTag("top_level_communications").assertIsSelected()

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
    composeRule.onNodeWithTag("top_level_communications").assertIsSelected()
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
    composeRule.onNodeWithTag("bug_report_title").performTextInput("Animazione interrotta")
    composeRule.onNodeWithTag("bug_report_description").performTextInput("La pagina si sovrappone")

    composeRule.runOnIdle {
      assertEquals(BugReportRoute, navController.currentDestination?.route)
      assertEquals("more", navController.previousBackStackEntry?.destination?.route)
    }

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag("bug_report_title")
      .assertTextContains("Animazione interrotta")
    composeRule.onNodeWithTag("bug_report_description")
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
    composeRule.onNodeWithTag("bug_report_diagnostics")
      .performScrollTo()
      .assertTextContains("Schermata: more", substring = true)
  }

  @Test
  fun railAt600dp_darkTheme_fontScale13_andDisabledMotion_remainsUsable() {
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
      composeRule.onNodeWithTag("top_level_home").assertIsSelected()

      val homeBounds = composeRule.onNodeWithTag("top_level_home").fetchSemanticsNode().boundsInRoot
      val boardBounds = composeRule.onNodeWithTag("top_level_communications").fetchSemanticsNode().boundsInRoot
      assertTrue("La rail a 600 dp deve disporre i tab in verticale", boardBounds.top > homeBounds.bottom)

      composeRule.onNodeWithContentDescription("Bacheca").performClick()
      composeRule.onNodeWithTag("route_communications").assertIsDisplayed()
      composeRule.onNodeWithTag("top_level_communications").assertIsSelected()
    }
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
    onNavigateRoute = { route -> navController.navigateTopLevel(route) },
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
