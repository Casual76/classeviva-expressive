package dev.antigravity.classevivaexpressive.core.domain.usecase

import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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
    val mondaySlot = template.slots.first { it.dayOfWeek == DayOfWeek.MONDAY.value }

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

  @Test
  fun substituteTeacher_doesNotOverrideRegularTeacher() {
    // 6 settimane di Matematica con prof Rossi, 1 settimana con supplente Bianchi.
    val lessons = (0 until 6).map { week ->
      val date = LocalDate.of(2026, 3, 2).plusWeeks(week.toLong()).toString()
      lesson("reg-$week", "Matematica", date, teacher = "Rossi")
    } + listOf(
      // Supplenza isolata in una settimana
      lesson("sub-1", "Matematica", "2026-04-13", teacher = "Bianchi"),
    )

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlot = template.slots.first { it.dayOfWeek == DayOfWeek.MONDAY.value }

    assertEquals("Rossi", mondaySlot.teacher)
    assertEquals("Matematica", mondaySlot.subject)
  }

  @Test
  fun bellGridDiscovery_findsMultipleSlotsAndSnaps() {
    // Creo 4 settimane di lezioni con campanelle 8:00, 9:00, 10:00 — alcune
    // logged a 8:02 (rumore dentro la tolleranza).
    val lessons = mutableListOf<Lesson>()
    val baseDates = listOf("2026-03-23", "2026-03-30", "2026-04-06", "2026-04-13")
    baseDates.forEachIndexed { idx, date ->
      lessons += lesson("a-$idx", "Italiano", date, time = if (idx == 1) "08:02" else "08:00", endTime = "09:00", teacher = "Verdi")
      lessons += lesson("b-$idx", "Storia", date, time = "09:00", endTime = "10:00", teacher = "Neri")
      lessons += lesson("c-$idx", "Geografia", date, time = "10:00", endTime = "11:00", teacher = "Bruni")
    }

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlots = template.slots.filter { it.dayOfWeek == DayOfWeek.MONDAY.value }.sortedBy { it.time }

    // Mi aspetto 3 ore canoniche, tutte snap-pate alla griglia 08:00/09:00/10:00.
    assertEquals(3, mondaySlots.size)
    assertEquals(listOf("08:00", "09:00", "10:00"), mondaySlots.map { it.time })
    assertEquals(listOf("Italiano", "Storia", "Geografia"), mondaySlots.map { it.subject })
  }

  @Test
  fun changeDetection_recentTeacherChangeBeatsHistory() {
    // 8 settimane di prof Vecchio, poi 3 settimane di prof Nuovo nella stessa cella.
    val lessons = (0 until 8).map { week ->
      val date = LocalDate.of(2026, 1, 5).plusWeeks(week.toLong()).toString()
      lesson("hist-$week", "Inglese", date, teacher = "Vecchio")
    } + (0 until 3).map { week ->
      val date = LocalDate.of(2026, 4, 6).plusWeeks(week.toLong()).toString()
      lesson("new-$week", "Inglese", date, teacher = "Nuovo")
    }

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlot = template.slots.first { it.dayOfWeek == DayOfWeek.MONDAY.value }

    assertEquals("Nuovo", mondaySlot.teacher)
  }

  @Test
  fun emptyOrInvalidLessons_returnEmptyTemplate() {
    val template = useCase.generateTimetableTemplate(emptyList())
    assertEquals(0, template.slots.size)
    assertEquals(0, template.sampledWeeks)
  }

  @Test
  fun isolatedSpecialEvent_doesNotPolluteTemplate() {
    // 6 settimane normali, 1 evento speciale isolato a 14:30 il martedi'.
    val lessons = (0 until 6).map { week ->
      val date = LocalDate.of(2026, 3, 3).plusWeeks(week.toLong()).toString() // martedi'
      lesson("reg-$week", "Matematica", date, teacher = "Rossi")
    } + listOf(
      lesson("special", "Conferenza", "2026-04-21", time = "14:30", endTime = "15:30", teacher = "Ospite"),
    )

    val template = useCase.generateTimetableTemplate(lessons)
    val tuesdaySlots = template.slots.filter { it.dayOfWeek == DayOfWeek.TUESDAY.value }

    // Solo lo slot regolare delle 08:00, niente conferenza isolata.
    assertEquals(1, tuesdaySlots.size)
    assertEquals("08:00", tuesdaySlots.first().time)
    assertEquals("Matematica", tuesdaySlots.first().subject)
  }

  @Test
  fun multiHourLesson_spreadsAcrossBellSlots() {
    // Una lezione "spalmata" 2 ore (08:00-10:00) per 4 settimane, piu' altri
    // record orari per definire la griglia 9-10.
    val lessons = mutableListOf<Lesson>()
    val baseDates = listOf("2026-03-23", "2026-03-30", "2026-04-06", "2026-04-13")
    baseDates.forEachIndexed { idx, date ->
      // Lezione singola 2 ore
      lessons += lesson("lab-$idx", "Laboratorio", date, time = "08:00", endTime = "10:00", teacher = "Lab")
      // Riferimento per la campanella 9:00-10:00 (es: prof aggiuntivo a meta')
      lessons += lesson("ref-$idx", "Laboratorio", date, time = "09:00", endTime = "10:00", teacher = "Lab")
    }

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlots = template.slots.filter { it.dayOfWeek == DayOfWeek.MONDAY.value }.sortedBy { it.time }

    // Mi aspetto 2 slot — uno a 08:00 e uno a 09:00, entrambi del Laboratorio.
    assertTrue(mondaySlots.size >= 2)
    assertEquals("08:00", mondaySlots[0].time)
    assertEquals("Laboratorio", mondaySlots[0].subject)
    assertEquals("09:00", mondaySlots[1].time)
  }

  @Test
  fun crossSubjectTeacher_normalizesEducazioneCivica() {
    // Prof Mancini insegna Italiano per 5 settimane, Educazione civica per 2.
    val lessons = (0 until 5).map { week ->
      val date = LocalDate.of(2026, 3, 2).plusWeeks(week.toLong()).toString()
      lesson("ita-$week", "Italiano", date, teacher = "Mancini")
    } + (0 until 2).map { week ->
      val date = LocalDate.of(2026, 4, 6).plusWeeks(week.toLong()).toString()
      lesson("civ-$week", "Educazione civica", date, teacher = "Mancini")
    }

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlot = template.slots.first { it.dayOfWeek == DayOfWeek.MONDAY.value }

    // Anche le settimane di Educazione civica vengono normalizzate a Italiano,
    // quindi il profilo dominante e' uniforme.
    assertEquals("Italiano", mondaySlot.subject)
    assertEquals("Mancini", mondaySlot.teacher)
  }

  @Test
  fun roomMode_picksMostFrequentRoom() {
    val lessons = (0 until 4).map { week ->
      val date = LocalDate.of(2026, 3, 2).plusWeeks(week.toLong()).toString()
      lesson("a-$week", "Matematica", date, teacher = "Rossi", room = "12A")
    } + listOf(
      lesson("alt", "Matematica", "2026-04-06", teacher = "Rossi", room = "Aula magna"),
    )

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlot = template.slots.first { it.dayOfWeek == DayOfWeek.MONDAY.value }

    assertEquals("12A", mondaySlot.room)
  }

  @Test
  fun getScheduleForDate_returnsEmpty_whenNoData() {
    val schedule = useCase.getScheduleForDate(LocalDate.of(2026, 5, 4), emptyList())
    assertTrue(schedule.isEmpty())
  }

  @Test
  fun confidence_reflectsBothFrequencyAndShare() {
    // 5 settimane di Matematica con Rossi → frequenza 1.0, share 1.0.
    val lessons = (0 until 5).map { week ->
      val date = LocalDate.of(2026, 3, 2).plusWeeks(week.toLong()).toString()
      lesson("a-$week", "Matematica", date, teacher = "Rossi")
    }

    val template = useCase.generateTimetableTemplate(lessons)
    val mondaySlot = template.slots.first { it.dayOfWeek == DayOfWeek.MONDAY.value }

    assertTrue("Confidence dovrebbe essere alta: ${mondaySlot.confidence}", mondaySlot.confidence >= 0.95f)
  }

  // --- Helpers ----------------------------------------------------------

  private fun lesson(
    id: String,
    subject: String,
    date: String,
    topic: String? = null,
    time: String = "08:00",
    endTime: String? = "09:00",
    teacher: String? = null,
    room: String? = null,
  ): Lesson {
    return Lesson(
      id = id,
      subject = subject,
      date = date,
      time = time,
      durationMinutes = 60,
      topic = topic,
      teacher = teacher,
      room = room,
      endTime = endTime,
    )
  }

}
