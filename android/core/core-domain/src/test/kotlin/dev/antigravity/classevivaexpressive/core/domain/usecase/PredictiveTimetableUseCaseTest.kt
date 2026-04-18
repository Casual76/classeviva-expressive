package dev.antigravity.classevivaexpressive.core.domain.usecase

import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PredictiveTimetableUseCaseTest {

  private val useCase = PredictiveTimetableUseCase()

  @Test
  fun generateTimetableTemplate_prefersRecentRecurringPattern() {
    val lessons = listOf(
      lesson("old-1", "Matematica", "2025-09-01"),
      lesson("old-2", "Matematica", "2025-11-03"),
      lesson("old-3", "Matematica", "2026-01-05"),
      lesson("recent-1", "Fisica", "2026-04-06"),
      lesson("recent-2", "Fisica", "2026-04-13"),
    )

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlot = template.slots.first { it.dayOfWeek == java.time.DayOfWeek.MONDAY.value }

    assertEquals("Fisica", mondaySlot.subject)
    assertEquals("08:00", mondaySlot.time)
    assertEquals(5, template.sampledWeeks)
    assertTrue(mondaySlot.confidence >= 0.4f)
  }

  @Test
  fun getScheduleForDate_keepsActualLessonOverPredictedDuplicate() {
    val targetDate = LocalDate.of(2026, 4, 14)
    val history = listOf(
      lesson("p1", "Matematica", "2026-03-31"),
      lesson("p2", "Matematica", "2026-04-07"),
      lesson("actual", "Chimica", targetDate.toString(), topic = "Titolazioni"),
    )

    val schedule = useCase.getScheduleForDate(targetDate, history)

    assertEquals(1, schedule.count { it.time.toString() == "08:00" })
    assertEquals("Chimica", schedule.first().subject)
    assertFalse(schedule.first().isPredicted)
  }

  private fun lesson(
    id: String,
    subject: String,
    date: String,
    topic: String? = null,
  ): Lesson {
    return Lesson(
      id = id,
      subject = subject,
      date = date,
      time = "08:00",
      durationMinutes = 60,
      topic = topic,
      endTime = "09:00",
    )
  }
}
