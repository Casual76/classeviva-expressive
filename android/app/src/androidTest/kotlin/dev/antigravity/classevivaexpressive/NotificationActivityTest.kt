package dev.antigravity.classevivaexpressive

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearSelectionPolicy
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.qa.OfflineNavigationQaEntryPoint
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises the real Activity, Hilt graph, Room reads, Fluid graph and grade modal offline. */
class NotificationActivityTest {
  @get:Rule val composeRule = createEmptyComposeRule()
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var fixtures: OfflineNavigationQaEntryPoint
  private val year = SchoolYearSelectionPolicy.current(LocalDate.now())
  private val studentId = "qa-offline-notification-student"
  private val firstId = "qa-grade/A +?"
  private val secondId = "qa-grade-B"
  private val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Dettaglio voto")

  @Before fun seedIsolatedOfflineData() = runBlocking {
    // This test may never overwrite the installed user's normal account or database.
    assumeTrue("Requires the separate .qa applicationId", context.packageName.endsWith(".qa"))
    fixtures = EntryPointAccessors.fromApplication(context, OfflineNavigationQaEntryPoint::class.java)
    fixtures.sessionStore().clear()
    fixtures.settingsStore().update {
      it.copy(periodicSyncEnabled = false, notificationPreferences = it.notificationPreferences.copy(enabled = false))
    }
    fixtures.schoolYearStore().selectSchoolYear(year)
    fixtures.gradeDao().replaceByYear(studentId, year.id, listOf(
      grade(firstId, "Regressione notifica A", 8.0),
      grade(secondId, "Regressione notifica B", 7.0),
    ))
    // REAUTHENTICATION_REQUIRED preserves local reads and prevents REST token validation/refresh.
    fixtures.sessionStore().writeAuthSnapshot(StoredAuthSnapshot(
      session = UserSession("offline-fixture", studentId, "qa-offline", StudentProfile(studentId, "QA", "Offline")),
      authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED),
    ))
  }

  @After fun removeOfflineFixtures() = runBlocking {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { resumedActivityOnMain()?.finish() }
    if (::fixtures.isInitialized) {
      fixtures.sessionStore().clear()
      fixtures.gradeDao().deleteByYear(studentId, year.id)
    }
  }

  @Test fun coldAndWarmItemTapsOpenTheRequestedGradeAndConsumedTapDoesNotReplayAfterRecreation() {
    launch(tap(firstId))
    awaitGradeModal("Regressione notifica A")

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      checkNotNull(resumedActivityOnMain()).onBackPressedDispatcher.onBackPressed()
    }
    composeRule.waitUntil(15_000) {
      composeRule.onAllNodes(pane).fetchSemanticsNodes().isEmpty()
    }
    var previousActivity: MainActivity? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      previousActivity = checkNotNull(resumedActivityOnMain()).also { it.recreate() }
    }
    composeRule.waitUntil(15_000) {
      var recreated = false
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        recreated = resumedActivityOnMain()?.let { it !== previousActivity } == true
      }
      recreated
    }
    composeRule.waitForIdle()
    composeRule.onNode(pane).assertDoesNotExist()

    context.startActivity(tap(secondId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    awaitGradeModal("Regressione notifica B")
  }

  @Test fun aNotificationForAnotherStudentCannotOpenTheSameIdInTheCurrentAccount() {
    val intent = tap(firstId)
    intent.data = intent.data!!.buildUpon().clearQuery()
      .appendQueryParameter("gradeId", firstId)
      .appendQueryParameter("studentId", "other-student")
      .appendQueryParameter("schoolYearId", year.id).build()
    launch(intent)
    val explanation = "Questa notifica appartiene a un altro account. Accedi con l’account che l’ha ricevuta."
    awaitExplanation(explanation)
    composeRule.onNodeWithText(explanation).assertIsDisplayed()
    composeRule.onNode(pane).assertDoesNotExist()
    context.startActivity(tap(firstId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    awaitGradeModal("Regressione notifica A")
    composeRule.onNodeWithText("Collegamento non disponibile").assertDoesNotExist()
  }

  @Test fun aNotificationForAnotherSchoolYearDoesNotResolveItsIdInTheCurrentYear() {
    val intent = tap(firstId)
    intent.data = intent.data!!.buildUpon().clearQuery()
      .appendQueryParameter("gradeId", firstId)
      .appendQueryParameter("studentId", studentId)
      .appendQueryParameter("schoolYearId", "2020-2021").build()
    launch(intent)
    val explanation = "Questa notifica riguarda un altro anno scolastico. Seleziona quell’anno nelle impostazioni per consultare i suoi dati."
    awaitExplanation(explanation)
    composeRule.onNodeWithText(explanation).assertIsDisplayed()
    composeRule.onNode(pane).assertDoesNotExist()
  }

  private fun awaitGradeModal(description: String) {
    val target = hasText(description) and hasAnyAncestor(pane)
    composeRule.waitUntil(20_000) { composeRule.onAllNodes(target, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    composeRule.onNode(target, useUnmergedTree = true).assertIsDisplayed()
  }

  private fun awaitExplanation(explanation: String) {
    try {
      composeRule.waitUntil(20_000) { composeRule.onAllNodesWithText(explanation).fetchSemanticsNodes().isNotEmpty() }
    } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
      var state = "Activity absent"
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        resumedActivityOnMain()?.let { activity ->
          val vm = androidx.lifecycle.ViewModelProvider(activity)[MainViewModel::class.java]
          state = "loading=${vm.uiState.value.isLoading}, student=${vm.uiState.value.session?.studentId}, year=${vm.selectedSchoolYear.value?.id}"
        }
      }
      throw AssertionError("$state\n${composeRule.onRoot(useUnmergedTree = true).printToString()}", failure)
    }
  }

  private fun launch(intent: Intent) {
    // The production Activity consumes and clears its launch intent. ActivityScenario uses that
    // mutable Intent to identify lifecycle events and cannot track this legitimate transition.
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  }

  private fun resumedActivityOnMain(): MainActivity? = ActivityLifecycleMonitorRegistry.getInstance()
    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()

  private fun tap(id: String) = Intent(
    Intent.ACTION_VIEW,
    Uri.parse("classevivaexpressive://open/grades?gradeId=${Uri.encode(id)}&studentId=$studentId&schoolYearId=${year.id}"),
    context,
    MainActivity::class.java,
  )

  private fun grade(id: String, description: String, value: Double) = GradeEntity(
    id = id, studentId = studentId, schoolYearId = year.id, subject = "Matematica", valueLabel = value.toInt().toString(),
    numericValue = value, description = description, date = LocalDate.now().toString(), type = "Scritto", weight = 1.0,
    notes = null, period = null, periodCode = null, teacher = null, color = null,
  )
}
