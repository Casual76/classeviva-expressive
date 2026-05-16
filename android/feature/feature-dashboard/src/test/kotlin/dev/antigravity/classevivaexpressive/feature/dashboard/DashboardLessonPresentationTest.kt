package dev.antigravity.classevivaexpressive.feature.dashboard

import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLessonPresentationTest {
  @Test
  fun signedLessonWithoutTopic_usesSuccessToneAndSignedBadge() {
    val lesson = Lesson(
      id = "lesson-1",
      subject = "Matematica",
      date = "2026-05-15",
      time = "08:00",
      durationMinutes = 60,
      topic = "",
      teacher = "Rossi",
      isSigned = true,
    )

    val presentation = lesson.toDashboardPresentation()

    assertEquals("Lezione firmata senza argomento", presentation.subtitle)
    assertEquals(ExpressiveTone.Success, presentation.tone)
    assertEquals("FIRMATA", presentation.badgeLabel)
    assertEquals(ExpressiveTone.Success, presentation.badgeTone)
  }

  @Test
  fun unsignedLessonWithoutTopic_staysNeutral() {
    val lesson = Lesson(
      id = "lesson-2",
      subject = "Italiano",
      date = "2026-05-15",
      time = "09:00",
      durationMinutes = 60,
    )

    val presentation = lesson.toDashboardPresentation()

    assertEquals("Argomento non disponibile", presentation.subtitle)
    assertEquals(ExpressiveTone.Neutral, presentation.tone)
    assertEquals("60 min", presentation.badgeLabel)
    assertEquals(ExpressiveTone.Info, presentation.badgeTone)
  }

  @Test
  fun lessonWithTeacherButUnsigned_staysNeutral() {
    val lesson = Lesson(
      id = "lesson-3",
      subject = "Fisica",
      date = "2026-05-15",
      time = "10:00",
      durationMinutes = 60,
      teacher = "Bianchi",
      isSigned = false,
    )

    val presentation = lesson.toDashboardPresentation()

    assertEquals("Argomento non disponibile", presentation.subtitle)
    assertEquals(ExpressiveTone.Neutral, presentation.tone)
    assertEquals("60 min", presentation.badgeLabel)
    assertEquals(ExpressiveTone.Info, presentation.badgeTone)
  }
}
