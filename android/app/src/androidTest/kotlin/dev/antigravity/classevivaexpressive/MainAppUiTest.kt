package dev.antigravity.classevivaexpressive

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveScreenSurface
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainAppUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun loginScreen_enablesSubmitAndInvokesLogin() {
    var submittedCredentials: Pair<String, String>? = null

    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        ExpressiveScreenSurface {
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
  fun topLevelNavigationSuite_rendersDestinationsAndContent() {
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        ExpressiveScreenSurface {
          TopLevelNavigationSuite(
            currentRoute = "home",
            showNavigationSuite = true,
            onNavigateRoute = {},
          ) {
            Text("Contenuto shell")
          }
        }
      }
    }

    composeRule.onNodeWithText("Home").assertExists()
    composeRule.onNodeWithText("Voti").assertExists()
    composeRule.onNodeWithText("Agenda").assertExists()
    composeRule.onNodeWithText("Bacheca").assertExists()
    composeRule.onNodeWithText("Altro").assertExists()
    composeRule.onNodeWithText("Contenuto shell").assertExists()
  }
}
