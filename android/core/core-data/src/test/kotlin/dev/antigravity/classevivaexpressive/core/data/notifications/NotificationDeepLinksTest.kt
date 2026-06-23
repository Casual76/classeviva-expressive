package dev.antigravity.classevivaexpressive.core.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationDeepLinksTest {
  @Test
  fun gradeLinkIncludesGradeId() {
    assertEquals(
      "classevivaexpressive://open/grades?gradeId=grade-42",
      NotificationDeepLinks.grades("grade-42"),
    )
  }

  @Test
  fun communicationLinkTargetsBoardItem() {
    assertEquals(
      "classevivaexpressive://open/communications?tab=board&pubId=pub-1&evtCode=evt-2",
      NotificationDeepLinks.communications(pubId = "pub-1", evtCode = "evt-2"),
    )
  }

  @Test
  fun blankParametersAreNotSerialized() {
    val uri = NotificationDeepLinks.agenda(agendaId = "", date = "2026-06-03")

    assertEquals("classevivaexpressive://open/agenda?date=2026-06-03", uri)
    assertFalse(uri.contains("agendaId="))
  }

  @Test
  fun queryValuesAreEncoded() {
    assertEquals(
      "classevivaexpressive://open/notes?noteId=nota%201&categoryCode=cat%2F2",
      NotificationDeepLinks.notes(noteId = "nota 1", categoryCode = "cat/2"),
    )
  }
}
