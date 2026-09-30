package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaHomeworkMergeTest {
  @Test
  fun linkedAgendaRowIsReplacedByTheHomeworkEvenWithDifferentText() {
    val merged = mergeHomeworksIntoAgenda(
      agenda = listOf(
        agendaRow(id = "a1", title = "Compiti: pag 1371"),
        agendaRow(id = "a2", title = "Verifica", category = AgendaCategory.ASSESSMENT),
      ),
      homeworks = listOf(dedicated(linkedAgendaIds = listOf("a1"))),
    )

    assertEquals(listOf("a2", "homework-hw-1"), merged.map { it.id })
    assertEquals("MUCCI SILVIA", merged.last().teacher)
  }

  @Test
  fun oldSnapshotsWithoutLinksStillHideTheDuplicateByKey() {
    val merged = mergeHomeworksIntoAgenda(
      agenda = listOf(agendaRow(id = "a1", title = "Pag 1371 es 282", subject = "MATEMATICA")),
      homeworks = listOf(dedicated(description = "Pag 1371 es 282").copy(source = HomeworkSource.AGENDA)),
    )

    assertEquals(listOf("homework-hw-1"), merged.map { it.id })
  }

  @Test
  fun dedicatedHomeworkPrefersItsOwnHistoryOverTheAgendaTwin() {
    val own = AgendaItemVersion(recordedAtEpochMillis = 2L, title = "Pag 1371", subtitle = "", date = "2026-09-30", category = AgendaCategory.HOMEWORK)
    val twinVersion = own.copy(recordedAtEpochMillis = 1L, title = "vecchio")
    val enriched = enrichHomeworksFromAgenda(
      homeworks = listOf(dedicated(linkedAgendaIds = listOf("a1"))),
      agenda = listOf(agendaRow(id = "a1", title = "altro testo", history = listOf(twinVersion), createdAt = "2026-09-26T10:00")),
      homeworkHistory = mapOf("hw-1" to listOf(own)),
    )

    val homework = enriched.single()
    assertEquals(listOf(own), homework.history)
    assertEquals("2026-09-26T10:00", homework.createdAt)
  }

  @Test
  fun dedicatedHomeworkWithoutHistoryInheritsTheTwinOne() {
    val twinVersion = AgendaItemVersion(recordedAtEpochMillis = 1L, title = "vecchio", subtitle = "", date = "2026-09-30", category = AgendaCategory.HOMEWORK)
    val enriched = enrichHomeworksFromAgenda(
      homeworks = listOf(dedicated(linkedAgendaIds = listOf("a1"))),
      agenda = listOf(agendaRow(id = "a1", title = "altro testo", history = listOf(twinVersion))),
      homeworkHistory = emptyMap(),
    )

    assertEquals(listOf(twinVersion), enriched.single().history)
  }

  private fun dedicated(
    description: String = "Pag 1371 es 282, 283, 311",
    linkedAgendaIds: List<String> = emptyList(),
  ) = Homework(
    id = "hw-1",
    subject = "MATEMATICA",
    description = description,
    dueDate = "2026-09-30",
    teacher = "MUCCI SILVIA",
    source = HomeworkSource.DEDICATED,
    linkedAgendaIds = linkedAgendaIds,
  )

  private fun agendaRow(
    id: String,
    title: String,
    subject: String? = null,
    category: AgendaCategory = AgendaCategory.HOMEWORK,
    history: List<AgendaItemVersion> = emptyList(),
    createdAt: String? = null,
  ) = AgendaItem(
    id = id,
    title = title,
    subtitle = subject.orEmpty(),
    date = "2026-09-30",
    subject = subject,
    category = category,
    history = history,
    createdAt = createdAt,
  )
}
