package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryHelpersTest {
  @Test
  fun buildLessonsWithFallback_usesAgendaLessonsWhenNativeLessonsAreMissing() {
    val agenda = listOf(
      AgendaItem(
        id = "a1",
        title = "Lezione di chimica",
        subtitle = "Chimica",
        date = "2026-03-31",
        time = "09:00",
        detail = "Acidi e basi",
        subject = "Chimica",
        category = AgendaCategory.LESSON,
      ),
    )

    val lessons = buildLessonsWithFallback(emptyList(), agenda)

    assertEquals(1, lessons.size)
    assertEquals("Chimica", lessons.first().subject)
    assertEquals("09:00", lessons.first().time)
    assertEquals("Acidi e basi", lessons.first().topic)
  }

  @Test
  fun buildLessonsWithFallback_assignsDerivedSlotsWhenTimeIsMissing() {
    val lessons = listOf(
      Lesson(
        id = "native-1",
        subject = "Italiano",
        date = "2026-03-30",
        time = "08:00",
        durationMinutes = 60,
      ),
      Lesson(
        id = "native-2",
        subject = "Matematica",
        date = "2026-03-31",
        time = "",
        durationMinutes = 60,
      ),
    )

    val rebuilt = buildLessonsWithFallback(lessons, emptyList())

    assertEquals("08:00", rebuilt.first { it.id == "native-2" }.time)
  }

  @Test
  fun buildLessonsWithFallback_usesMorningDefaultWhenDatasetHasNoTimes() {
    val lessons = listOf(
      Lesson(
        id = "native-3",
        subject = "Fisica",
        date = "2026-04-01",
        time = "",
        durationMinutes = 0,
      ),
    )

    val rebuilt = buildLessonsWithFallback(lessons, emptyList())

    assertEquals("08:00", rebuilt.first().time)
    assertEquals(60, rebuilt.first().durationMinutes)
  }
}
