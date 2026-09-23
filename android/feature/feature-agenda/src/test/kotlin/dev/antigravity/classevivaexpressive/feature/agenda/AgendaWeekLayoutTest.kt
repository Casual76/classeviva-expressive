package dev.antigravity.classevivaexpressive.feature.agenda

import dev.antigravity.classevivaexpressive.core.designsystem.theme.MinuteSpan
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaWeekLayoutTest {

  private fun slot(time: String, end: String, subject: String, teacher: String? = null) = TemplateSlot(
    dayOfWeek = 2,
    time = time,
    endTime = end,
    durationMinutes = 60,
    subject = subject,
    teacher = teacher,
  )

  private val tuesday = lessonBands(
    listOf(
      slot("08:00", "09:00", "LINGUA E LETTERATURA ITALIANA", "BIANCHI ANNA"),
      slot("09:00", "10:00", "STORIA", "ROSSI MARIO"),
      slot("10:00", "11:00", "STORIA", "ROSSI MARIO"),
      slot("11:10", "12:00", "FISICA", "VERDI LUCA"),
    ),
  )

  private fun place(subject: String? = null, teacher: String? = null, time: String? = null) =
    placeAgendaDay(listOf(AgendaPlacementInput("x", subject, teacher, time)), tuesday).single()

  @Test
  fun consecutiveHoursOfTheSameSubject_makeOneBand() {
    assertEquals(3, tuesday.size)
    assertEquals(MinuteSpan(9 * 60, 11 * 60), tuesday[1].span)
  }

  @Test
  fun ownTime_winsAndTakesTheLessonAroundIt() {
    val placed = place(subject = "Fisica", time = "09:30")
    assertEquals(AgendaAnchor.OwnTime, placed.anchor)
    assertEquals(MinuteSpan(9 * 60, 11 * 60), placed.span)
  }

  @Test
  fun midnight_isNotATime() {
    val placed = place(subject = "Storia", time = "00:00")
    assertEquals(AgendaAnchor.SubjectLesson, placed.anchor)
  }

  @Test
  fun subject_matchesAcrossSpellings() {
    val placed = place(subject = "Italiano")
    assertEquals(AgendaAnchor.SubjectLesson, placed.anchor)
    assertEquals(MinuteSpan(8 * 60, 9 * 60), placed.span)
  }

  @Test
  fun withoutSubject_theTeacherFindsTheLesson() {
    val placed = place(teacher = "Luca Verdi")
    assertEquals(AgendaAnchor.TeacherLesson, placed.anchor)
    assertEquals(MinuteSpan(11 * 60 + 10, 12 * 60), placed.span)
  }

  @Test
  fun nothingToHoldOnTo_goesToTheAllDayStrip() {
    val placed = place(subject = "Inglese")
    assertEquals(AgendaAnchor.AllDay, placed.anchor)
    assertNull(placed.span)
  }

  @Test
  fun ownTimeOutsideTheDay_goesToTheAllDayStrip() {
    assertEquals(AgendaAnchor.AllDay, place(time = "22:30").anchor)
  }
}
