package dev.antigravity.classevivaexpressive.core.assistant

import dev.antigravity.classevivaexpressive.core.assistant.di.assistantOrchestratorConfig
import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import dev.antigravity.fluidengine.ai.orchestrator.AiConfirmationGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I tempi dell'assistente dopo l'engine 3.0: il tetto dei tool e' quello di lavoro, e l'attesa del
 * si' dell'utente la somma l'orchestratore ai tool che la aspettano. Prima il tetto era 90 s scritti
 * a mano, sopra il minuto del cancello.
 */
class AssistantConfigTest {

  @Test
  fun toolCeilingIsTheWorkTimeNotTheConfirmationWindow() {
    val config = assistantOrchestratorConfig()

    assertEquals(30_000L, config.toolTimeoutMillis)
  }

  @Test
  fun theGateTakesItsWindowFromTheSameConfig() {
    val config = assistantOrchestratorConfig()

    assertEquals(config.confirmationTimeoutMillis, AiConfirmationGate(config).timeoutMillis)
  }

  @Test
  fun onlyTheAppGroupWaitsForConfirmations() {
    assertTrue(RegistroToolGroup.APP.awaitsConfirmation)
    RegistroToolGroup.entries.filter { it != RegistroToolGroup.APP }.forEach { group ->
      assertFalse("${group.id} non chiede conferme", group.awaitsConfirmation)
    }
  }

  @Test
  fun theWholeQuestionStillFitsTheForegroundService() {
    val config = assistantOrchestratorConfig()

    // Il service ha quattro minuti pieni piu' il margine: il budget totale resta sotto i cinque.
    assertTrue(config.totalBudgetMillis <= 300_000L)
    assertTrue(config.finalReserveMillis < config.totalBudgetMillis)
  }
}
