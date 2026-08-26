package dev.antigravity.classevivaexpressive.feature.lessons

import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonsLiveSlotTest {

  private fun slot(
    time: String,
    endTime: String? = null,
    durationMinutes: Int = 60,
    subject: String = "STORIA",
    confidence: Float = 0.9f,
    confirmed: Boolean = false,
  ) = TemplateSlot(
    dayOfWeek = 1,
    time = time,
    endTime = endTime,
    durationMinutes = durationMinutes,
    subject = subject,
    confidence = confidence,
    confirmed = confirmed,
  )

  private fun blocks(vararg slots: TemplateSlot) = slots.map { SlotBlock(primary = it) }

  @Test
  fun theLessonIsLiveFromItsFirstInstant() {
    val found = liveSlot(blocks(slot("08:00", "09:00")), LocalTime.of(8, 0))
    assertEquals("08:00", found?.block?.primary?.time)
  }

  @Test
  fun theLessonIsOverAtItsLastInstant() {
    // Intervallo chiuso a sinistra e aperto a destra: alle 9 in punto la lezione delle 8 e' finita
    // e quella delle 9 e' cominciata. Senza questa regola, per un minuto ne risultano due in corso.
    assertNull(liveSlot(blocks(slot("08:00", "09:00")), LocalTime.of(9, 0)))
  }

  @Test
  fun aMinuteBeforeTheEndItIsStillLive() {
    val found = liveSlot(blocks(slot("08:00", "09:00")), LocalTime.of(8, 59))
    assertEquals(1L, found?.minutesRemaining)
  }

  @Test
  fun betweenTwoLessonsThereIsNothing() {
    val timetable = blocks(slot("08:00", "09:00"), slot("10:00", "11:00"))
    assertNull(liveSlot(timetable, LocalTime.of(9, 30)))
  }

  @Test
  fun withoutAnEndTimeTheDurationsAreSummed() {
    val block = SlotBlock(
      primary = slot("08:00", endTime = null, durationMinutes = 55),
      extra = listOf(slot("09:00", endTime = null, durationMinutes = 55)),
    )
    val found = liveSlot(listOf(block), LocalTime.of(9, 30))
    assertEquals(20L, found?.minutesRemaining)
  }

  @Test
  fun aBrokenTimeDoesNotBlowUp() {
    assertNull(liveSlot(blocks(slot("non-un-orario")), LocalTime.of(9, 0)))
  }

  @Test
  fun toneAndBadgeAgreeAtEveryConfidence() {
    // Il bug che questa unificazione chiude: a 0.78 il tono diceva "incerto" (soglia 0.8) e il
    // badge diceva "STABILE" (soglia 0.75). Sono la stessa espressione, ora non possono divergere.
    val timetable = TimetableTemplate()
    listOf(0.0f, 0.5f, 0.6f, 0.74f, 0.75f, 0.78f, 0.8f, 1.0f).forEach { confidence ->
      val block = SlotBlock(primary = slot("08:00", confidence = confidence))
      val kind = slotKind(block, timetable)
      val expectedStable = confidence >= SlotStableConfidence
      assertEquals(
        "confidenza $confidence",
        if (expectedStable) "STABILE" else "DINAMICO",
        kind.badgeLabel(block),
      )
    }
  }

  @Test
  fun confirmedWinsOverEverythingElse() {
    val block = SlotBlock(primary = slot("08:00", confidence = 0.1f, confirmed = true))
    assertEquals(SlotKind.Confirmed, slotKind(block, TimetableTemplate(isOfficial = true)))
  }
}
