package dev.antigravity.classevivaexpressive

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {
  @Test fun itemLinksRetainAllIdentityFields() {
    val cases = mapOf(
      "grades?gradeId=42" to "grades?gradeId=42",
      "agenda?agendaId=evt-42&date=2026-11-17" to "agenda?agendaId=evt-42&date=2026-11-17",
      "homework?homeworkId=hw-42" to "homework?homeworkId=hw-42",
      "communications?tab=board&pubId=pub-42&evtCode=evt-9" to "communications?tab=board&pubId=pub-42&evtCode=evt-9",
      "notes?noteId=note-42&categoryCode=NTTE" to "communications?tab=notes&noteId=note-42&categoryCode=NTTE",
      "absences?absenceId=absence-42" to "absences?absenceId=absence-42",
      "assistant?conversationId=conversation-42" to "assistant/conversation-42",
    )
    cases.forEach { (link, route) -> assertEquals(link, route, resolveAppLink("classevivaexpressive://open/$link")?.route) }
  }

  @Test fun summariesOpenTheirOwnSectionIncludingNotesWithoutAnId() {
    listOf("home", "grades", "agenda", "homework", "absences", "lessons", "meetings", "settings", "assistant")
      .forEach { assertEquals(it, resolveAppLink("classevivaexpressive://open/$it")?.route) }
    assertEquals("communications?tab=notes", resolveAppLink("classevivaexpressive://open/notes")?.route)
    assertEquals("communications?tab=board", resolveAppLink("classevivaexpressive://open/communications?tab=board")?.route)
  }

  @Test fun reservedCharactersAndUnicodeAreDecodedExactlyOnce() {
    val link = resolveAppLink("classevivaexpressive://open/communications?pubId=nota%20%C3%A8%2F%3F%26%2B%252F&evtCode=a%2Bb")!!
    val query = URI(link.route).rawQuery.split('&').associate {
      it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
    assertEquals("nota è/?&+%2F", query["pubId"])
    assertEquals("a+b", query["evtCode"])
  }

  @Test fun queryOrderAndAdditionalScopeDoNotChangeTheDestination() {
    val link = resolveAppLink("classevivaexpressive://open/grades?schoolYearId=2026-2027&studentId=student-1&gradeId=42")!!
    assertEquals("grades?gradeId=42", link.route)
    assertNull(link.scopeMismatch("student-1", "2026-2027"))
    assertEquals(AppLinkScopeMismatch.STUDENT, link.scopeMismatch("student-2", "2026-2027"))
    assertEquals(AppLinkScopeMismatch.SCHOOL_YEAR, link.scopeMismatch("student-1", "2025-2026"))
  }

  @Test fun legacyUnscopedLinksRemainUsable() {
    assertNull(resolveAppLink("classevivaexpressive://open/grades?gradeId=42")!!.scopeMismatch("student-1", "2026-2027"))
  }

  @Test fun malformedOrAmbiguousCommandsDoNotOpenAnArbitraryItem() {
    listOf(
      "https://open/grades?gradeId=42",
      "classevivaexpressive://other/grades?gradeId=42",
      "classevivaexpressive://open/unknown",
      "classevivaexpressive://open/grades/42",
      "classevivaexpressive://open/grades?gradeId=1&gradeId=2",
      "classevivaexpressive://open/grades?gradeId=%xx",
    ).forEach { assertNull(it, resolveAppLink(it)) }
  }
}
