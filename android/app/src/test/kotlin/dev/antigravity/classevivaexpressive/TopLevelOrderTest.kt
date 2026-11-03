package dev.antigravity.classevivaexpressive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelOrderTest {

  @Test
  fun peerDirection_followsTheOrderOfTheBar() {
    assertEquals(1, peerDirection("home", "grades"))
    assertEquals(-1, peerDirection("more", "agenda"))
    // Letta dall'ordine e non registrata: il passo che disfa un altro e' il suo specchio.
    assertEquals(-peerDirection("home", "more"), peerDirection("more", "home"))
  }

  @Test
  fun peerDirection_isZeroWhenThePairIsNotPeers() {
    // Dettaglio, stessa sezione, rotta sconosciuta: valgono le regole delle pagine, non quelle delle schede.
    assertEquals(0, peerDirection("grades", "grade"))
    assertEquals(0, peerDirection("lessons", "more"))
    assertEquals(0, peerDirection("home", "home"))
    assertEquals(0, peerDirection(null, "home"))
  }

  @Test
  fun normalizeRoute_dropsArgumentsAndSubpaths() {
    assertEquals("communications", normalizeRoute("communications?tab=board"))
    assertEquals("grade", normalizeRoute("grade/42"))
    assertEquals("agenda", normalizeRoute("agenda?agendaId={agendaId}&date={date}"))
    assertNull(normalizeRoute(null))
    assertNull(normalizeRoute(""))
  }

  @Test
  fun barSections_peerWithArgumentsInTheirRoute() {
    // La barra apre `communications?tab=board`: il passo laterale deve riconoscerla comunque.
    assertEquals(
      1,
      peerDirection(normalizeRoute("agenda?agendaId={agendaId}&date={date}"), normalizeRoute("communications?tab={tab}&pubId={pubId}")),
    )
  }
}
