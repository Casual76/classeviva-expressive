package dev.antigravity.classevivaexpressive.core.assistant.prompt

import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreRouterTest {

  @Test
  fun `una domanda che nomina un solo gruppo salta il router`() {
    val verdict = PreRouter.decide("Che voto ho preso in matematica?", actionsEnabled = false)
    assertTrue(verdict.confident)
    assertEquals(setOf(RegistroToolGroup.VOTI), verdict.groups)
    assertFalse(verdict.deep)
  }

  @Test
  fun `una domanda ambigua suggerisce i candidati senza decidere`() {
    val verdict = PreRouter.decide("Domani ho verifica di storia, che voto avevo preso l'ultima volta?", actionsEnabled = false)
    assertFalse(verdict.confident)
    assertTrue(verdict.groups.containsAll(setOf(RegistroToolGroup.AGENDA, RegistroToolGroup.VOTI)))
  }

  @Test
  fun `gli allegati della bacheca chiedono il livello profondo`() {
    val verdict = PreRouter.decide("Cosa dice l'allegato della circolare sulla gita?", actionsEnabled = false)
    assertTrue(verdict.deep)
    assertTrue(RegistroToolGroup.BACHECA in verdict.groups)
  }

  @Test
  fun `le azioni contano solo se sono attive, e non decidono mai da sole`() {
    val off = PreRouter.decide("Metti il tema scuro", actionsEnabled = false)
    assertFalse(RegistroToolGroup.APP in off.groups)
    val on = PreRouter.decide("Metti il tema scuro", actionsEnabled = true)
    assertTrue(RegistroToolGroup.APP in on.groups)
    assertFalse(on.confident)
  }

  @Test
  fun `senza parole note non si decide niente`() {
    val verdict = PreRouter.decide("Ciao, come stai?", actionsEnabled = true)
    assertTrue(verdict.groups.isEmpty())
    assertFalse(verdict.confident)
  }
}
