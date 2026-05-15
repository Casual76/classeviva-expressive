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
        teacher = "Rossi",
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
        teacher = "Bianchi",
      ),
      Lesson(
        id = "native-2",
        subject = "Matematica",
        date = "2026-03-31",
        time = "",
        durationMinutes = 60,
        teacher = "Verdi",
      ),
    )

    val rebuilt = buildLessonsWithFallback(lessons, emptyList())

    assertEquals("08:00", rebuilt.first { it.id == "native-2" }.time)
  }

  @Test
  fun buildLessonsWithFallback_preservesMiddleHoleWhenSignedLessonIsMissing() {
    val lessons = listOf(
      Lesson(
        id = "lesson-1",
        subject = "Italiano",
        date = "2026-04-06",
        time = "08:00",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-2",
        subject = "Matematica",
        date = "2026-04-06",
        time = "09:00",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-4",
        subject = "Inglese",
        date = "2026-04-06",
        time = "11:00",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-5",
        subject = "Storia",
        date = "2026-04-06",
        time = "12:00",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-1-next-day",
        subject = "Scienze",
        date = "2026-04-07",
        time = "",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-2-next-day",
        subject = "Arte",
        date = "2026-04-07",
        time = "",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-4-next-day",
        subject = "Fisica",
        date = "2026-04-07",
        time = "",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
      Lesson(
        id = "lesson-5-next-day",
        subject = "Filosofia",
        date = "2026-04-07",
        time = "",
        durationMinutes = 60,
        teacher = "Rossi",
      ),
    )

    val rebuilt = buildLessonsWithFallback(lessons, emptyList())

    assertEquals("08:00", rebuilt.first { it.id == "lesson-1-next-day" }.time)
    assertEquals("09:00", rebuilt.first { it.id == "lesson-2-next-day" }.time)
    assertEquals("11:00", rebuilt.first { it.id == "lesson-4-next-day" }.time)
    assertEquals("12:00", rebuilt.first { it.id == "lesson-5-next-day" }.time)
  }

  @Test
  fun buildLessonsWithFallback_ignoresTeacherlessDaysAsSchoolClosed() {
    val lessons = listOf(
      Lesson(
        id = "closed-1",
        subject = "Matematica",
        date = "2026-04-08",
        time = "",
        durationMinutes = 60,
      ),
      Lesson(
        id = "closed-2",
        subject = "Italiano",
        date = "2026-04-08",
        time = "",
        durationMinutes = 60,
      ),
      Lesson(
        id = "open-1",
        subject = "Inglese",
        date = "2026-04-09",
        time = "",
        durationMinutes = 60,
        teacher = "Neri",
      ),
    )

    val rebuilt = buildLessonsWithFallback(lessons, emptyList())

    assertEquals(listOf("open-1"), rebuilt.map { it.id })
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
        teacher = "Gialli",
      ),
    )

    val rebuilt = buildLessonsWithFallback(lessons, emptyList())

    assertEquals("08:00", rebuilt.first().time)
    assertEquals(60, rebuilt.first().durationMinutes)
  }
}
