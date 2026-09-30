package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkSourceMergeTest {
  @Test
  fun dedicatedWinsOverAgendaDuplicateWithoutSubject() {
    val merged = mergeHomeworkSources(
      dedicated = listOf(dedicated()),
      agendaDerived = listOf(agenda(id = "a1", subject = "", description = "pag 1371 es 282,  283, 311")),
    )

    val homework = merged.single()
    assertEquals("hw-1", homework.id)
    assertEquals(listOf("a1"), homework.linkedAgendaIds)
  }

  @Test
  fun agendaTextWithAPrefixStillFindsItsTwin() {
    val merged = mergeHomeworkSources(
      dedicated = listOf(dedicated()),
      agendaDerived = listOf(agenda(id = "a1", description = "Compiti: Pag 1371 es 282, 283, 311")),
    )

    assertEquals(listOf("hw-1"), merged.map { it.id })
  }

  @Test
  fun keepsAgendaOnlyHomeworkWithItsStableId() {
    val merged = mergeHomeworkSources(
      dedicated = listOf(dedicated()),
      agendaDerived = listOf(agenda(id = "a2", subject = "FILOSOFIA", description = "Rousseau pp. 518-521")),
    )

    assertEquals(setOf("hw-1", "agenda-a2"), merged.map { it.id }.toSet())
  }

  @Test
  fun differentDueDatesOrSubjectsAreNotMerged() {
    val merged = mergeHomeworkSources(
      dedicated = listOf(dedicated()),
      agendaDerived = listOf(
        agenda(id = "a1", dueDate = "2026-10-01"),
        agenda(id = "a2", subject = "FISICA"),
      ),
    )

    assertEquals(3, merged.size)
  }

  @Test
  fun shortTextsMustMatchExactly() {
    val merged = mergeHomeworkSources(
      dedicated = listOf(dedicated(description = "Studiare")),
      agendaDerived = listOf(agenda(id = "a1", description = "Studiare pag. 40")),
    )

    assertEquals(2, merged.size)
  }

  @Test
  fun dropsHomeworkWithoutDueDateOrText() {
    val merged = mergeHomeworkSources(
      dedicated = listOf(dedicated(dueDate = "")),
      agendaDerived = listOf(agenda(id = "a1", description = " ")),
    )

    assertTrue(merged.isEmpty())
  }

  @Test
  fun revisionsCompareReadableFieldsButNotTheDoneFlag() {
    val before = listOf(dedicated(), dedicated(id = "hw-2", description = "Leggere il capitolo 3"))
    val after = listOf(
      dedicated().copy(done = true),
      dedicated(id = "hw-2", description = "Leggere i capitoli 3 e 4"),
      dedicated(id = "hw-3"),
    )

    val revisions = dedicatedHomeworkRevisions(before, after, recordedAtEpochMillis = 42L)

    val (id, version) = revisions.single()
    assertEquals("hw-2", id)
    assertEquals("Leggere il capitolo 3", version.title)
    assertEquals(42L, version.recordedAtEpochMillis)
  }

  private fun dedicated(
    id: String = "hw-1",
    description: String = "Pag 1371 es 282, 283, 311",
    dueDate: String = "2026-09-30",
  ) = Homework(
    id = id,
    subject = "MATEMATICA",
    description = description,
    dueDate = dueDate,
    teacher = "MUCCI SILVIA",
    source = HomeworkSource.DEDICATED,
  )

  private fun agenda(
    id: String,
    subject: String = "MATEMATICA",
    description: String = "Pag 1371 es 282, 283, 311",
    dueDate: String = "2026-09-30",
  ) = Homework(
    id = "agenda-$id",
    subject = subject,
    description = description,
    dueDate = dueDate,
  )
}
