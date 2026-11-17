package dev.antigravity.classevivaexpressive

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs the production intent handler against a real Navigation graph, with lightweight screens. */
class NotificationNavigationTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun everyItemTapKeepsItsArgumentsAndBuildsOneReturnPath() {
    lateinit var controller: NavHostController
    composeRule.setContent {
      val nav = rememberNavController()
      SideEffect { controller = nav }
      NavHost(nav, startDestination = "home") {
        composable("home") { Text("Home") }
        listOf(
          "grades" to listOf("gradeId"),
          "agenda" to listOf("agendaId", "date"),
          "homework" to listOf("homeworkId"),
          "communications" to listOf("tab", "pubId", "evtCode", "noteId", "categoryCode"),
          "absences" to listOf("absenceId"),
        ).forEach { (base, keys) ->
          composable(
            base + keys.joinToString("&", prefix = "?") { "$it={$it}" },
            arguments = keys.map { key -> navArgument(key) { type = NavType.StringType; nullable = true; defaultValue = null } },
          ) { Text(base) }
        }
      }
    }
    val cases = listOf(
      "grades?gradeId=voto%20%2B%2F42" to mapOf("gradeId" to "voto +/42"),
      "agenda?agendaId=agenda-42&date=2026-11-17" to mapOf("agendaId" to "agenda-42", "date" to "2026-11-17"),
      "homework?homeworkId=hw-42" to mapOf("homeworkId" to "hw-42"),
      "communications?tab=board&pubId=pub-42&evtCode=evt-42" to mapOf("pubId" to "pub-42", "evtCode" to "evt-42"),
      "notes?noteId=note-42&categoryCode=NTTE" to mapOf("tab" to "notes", "noteId" to "note-42", "categoryCode" to "NTTE"),
      "notes" to mapOf("tab" to "notes"),
      "absences?absenceId=absence-42" to mapOf("absenceId" to "absence-42"),
    )
    cases.forEach { (link, arguments) ->
      composeRule.runOnIdle {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("classevivaexpressive://open/$link"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(controller.handleIncomingIntent(intent))
        arguments.forEach { (key, value) -> assertEquals(link, value, controller.currentBackStackEntry?.arguments?.getString(key)) }
        assertEquals("home", controller.previousBackStackEntry?.destination?.route)
      }
      composeRule.waitForIdle()
    }
    composeRule.runOnIdle {
      assertTrue(controller.popBackStack())
      assertEquals("home", controller.currentDestination?.route)
      assertFalse(controller.handleIncomingIntent(Intent(Intent.ACTION_VIEW, Uri.parse("classevivaexpressive://open/grades?gradeId=1&gradeId=2"))))
      assertEquals("home", controller.currentDestination?.route)
    }
  }
}
