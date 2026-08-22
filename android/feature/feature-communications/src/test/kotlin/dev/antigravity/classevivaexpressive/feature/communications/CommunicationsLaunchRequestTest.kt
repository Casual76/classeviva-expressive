package dev.antigravity.classevivaexpressive.feature.communications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationsLaunchRequestTest {

  @Test
  fun `plain route selects requested tab without opening a detail`() {
    val request = communicationsLaunchRequest(
      initialTab = "notes",
      communicationPubId = null,
      communicationEvtCode = null,
      noteId = null,
      noteCategoryCode = null,
    )

    assertTrue(request is CommunicationsLaunchRequest.Tab)
    assertEquals("Note", request.tab)
    assertEquals("tab:Note", request.stableKey)
  }

  @Test
  fun `communication deep link wins and has a stable one shot identity`() {
    val request = communicationsLaunchRequest(
      initialTab = "notes",
      communicationPubId = "pub-42",
      communicationEvtCode = "evt-9",
      noteId = "note-1",
      noteCategoryCode = "disciplinary",
    )

    assertEquals(
      CommunicationsLaunchRequest.Communication("pub-42", "evt-9"),
      request,
    )
    assertEquals("communication:pub-42:evt-9", request.stableKey)
    assertEquals("Comunicazioni", request.tab)
  }

  @Test
  fun `incomplete deep link never opens a stale detail`() {
    val request = communicationsLaunchRequest(
      initialTab = "board",
      communicationPubId = "pub-without-event",
      communicationEvtCode = null,
      noteId = null,
      noteCategoryCode = null,
    )

    assertTrue(request is CommunicationsLaunchRequest.Tab)
    assertEquals("tab:Comunicazioni", request.stableKey)
  }
}
