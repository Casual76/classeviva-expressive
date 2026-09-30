package dev.antigravity.classevivaexpressive.core.data.notifications

import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkNotificationFilterTest {
  @Test
  fun firstReadOfTheSectionAnnouncesNothingFromIt() {
    val toNotify = homeworksToNotify(
      before = emptyList(),
      after = listOf(dedicated("hw-1"), dedicated("hw-2")),
      dedicatedPrimed = false,
      agendaChannelOn = false,
    )

    assertTrue(toNotify.isEmpty())
  }

  @Test
  fun laterReadsAnnounceOnlyNewDedicatedHomework() {
    val toNotify = homeworksToNotify(
      before = listOf(dedicated("hw-1")),
      after = listOf(dedicated("hw-1"), dedicated("hw-2")),
      dedicatedPrimed = true,
      agendaChannelOn = false,
    )

    assertEquals(listOf("hw-2"), toNotify.map { it.id })
  }

  @Test
  fun homeworkThatAbsorbedAnAlreadyKnownAgendaRowIsNotNew() {
    val toNotify = homeworksToNotify(
      before = listOf(agenda("a1")),
      after = listOf(dedicated("hw-1", linkedAgendaIds = listOf("a1"))),
      dedicatedPrimed = true,
      agendaChannelOn = false,
    )

    assertTrue(toNotify.isEmpty())
  }

  @Test
  fun agendaHomeworkIsLeftToTheAgendaChannelWhenItIsOn() {
    val after = listOf(agenda("a1"))

    assertTrue(homeworksToNotify(emptyList(), after, dedicatedPrimed = true, agendaChannelOn = true).isEmpty())
    assertEquals(1, homeworksToNotify(emptyList(), after, dedicatedPrimed = true, agendaChannelOn = false).size)
  }

  @Test
  fun agendaRowsAbsorbedByDedicatedHomeworkAreCovered() {
    val covered = agendaIdsCoveredByDedicatedHomework(
      listOf(dedicated("hw-1", linkedAgendaIds = listOf("a1")), agenda("a2")),
    )

    assertEquals(setOf("a1"), covered)
  }

  private fun dedicated(id: String, linkedAgendaIds: List<String> = emptyList()) = Homework(
    id = id,
    subject = "MATEMATICA",
    description = "Esercizi $id",
    dueDate = "2026-09-30",
    source = HomeworkSource.DEDICATED,
    linkedAgendaIds = linkedAgendaIds,
  )

  private fun agenda(id: String) = Homework(
    id = "agenda-$id",
    subject = "MATEMATICA",
    description = "Esercizi $id",
    dueDate = "2026-09-30",
  )
}
