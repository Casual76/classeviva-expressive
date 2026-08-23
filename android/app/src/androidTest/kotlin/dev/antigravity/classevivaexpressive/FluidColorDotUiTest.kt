package dev.antigravity.classevivaexpressive

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidColorDot

@RunWith(AndroidJUnit4::class)
class FluidColorDotUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun accentDotsExposeLabelSelectionRoleAndMinimumTouchTarget() {
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        Row {
          FluidColorDot(
            color = Color.Blue,
            selected = true,
            onClick = {},
            label = "Dynamic Color",
          )
          FluidColorDot(
            color = Color.Green,
            selected = false,
            onClick = {},
            label = "Verde",
          )
        }
      }
    }

    composeRule.onNodeWithContentDescription("Dynamic Color")
      .assertIsSelected()
      .assertHasClickAction()
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    composeRule.onNodeWithContentDescription("Verde").assertIsNotSelected()
  }

  @Test
  fun chipsExposeSelectionAndMinimumTouchTarget() {
    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings()) {
        Row {
          FluidChip(
            label = "2025/26",
            selected = true,
            onClick = {},
          )
          FluidChip(
            label = "2024/25",
            selected = false,
            onClick = {},
          )
        }
      }
    }

    composeRule.onNodeWithText("2025/26")
      .assertIsSelected()
      .assertHasClickAction()
      .assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithText("2024/25")
      .assertIsNotSelected()
      .assertHasClickAction()
      .assertHeightIsAtLeast(48.dp)
  }
}
