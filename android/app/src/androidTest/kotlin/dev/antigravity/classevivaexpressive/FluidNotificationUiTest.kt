package dev.antigravity.classevivaexpressive

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationDelivery
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState

class FluidNotificationUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun deliveryCompletesOnlyAfterTheCardHasARealLayout() {
    lateinit var state: FluidNotificationHostState
    lateinit var scope: CoroutineScope
    val delivery = AtomicReference<FluidNotificationDelivery?>(null)

    composeRule.setContent {
      ClassevivaExpressiveTheme(settings = AppSettings(dynamicColorEnabled = false)) {
        state = rememberFluidNotificationHostState()
        scope = rememberCoroutineScope()
        FluidNotificationHost(state = state)
      }
    }

    composeRule.runOnIdle {
      scope.launch {
        delivery.set(
          state.show(
            FluidNotification(
              id = "durable-layout",
              title = "Anno scolastico aggiornato",
              message = "Mostro temporaneamente l'anno precedente.",
            ),
          ),
        )
      }
    }

    composeRule.onNodeWithText("Anno scolastico aggiornato").assertIsDisplayed()
    composeRule.waitUntil(timeoutMillis = 2_000L) { delivery.get() != null }
    assertEquals(FluidNotificationDelivery.Presented, delivery.get())
  }
}
