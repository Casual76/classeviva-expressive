package dev.antigravity.classevivaexpressive.feature.agenda

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaUpcomingAssessmentTest {

  private val today = LocalDate.of(2026, 3, 10)

  private fun item(
    id: String,
    date: LocalDate,
    category: AgendaCategory = AgendaCategory.ASSESSMENT,
    subject: String? = "STORIA",
  ) = AgendaItem(
    id = id,
    title = "Verifica di $subject",
    subtitle = "",
    date = date.toString(),
    subject = subject,
    category = category,
  )

  @Test
  fun noItems_meansNoCard() {
    assertNull(upcomingAssessment(emptyList(), today))
  }

  @Test
  fun anAssessmentTodayIsZeroDaysAway() {
    val found = upcomingAssessment(listOf(item("a", today)), today)
    assertEquals(0L, found?.daysAway)
  }

  @Test
  fun theNearestAssessmentWins() {
    val items = listOf(
      item("lontana", today.plusDays(5)),
      item("vicina", today.plusDays(2)),
      item("domani", today.plusDays(1)),
    )
    assertEquals("domani", upcomingAssessment(items, today)?.id)
  }

  @Test
  fun beyondTheWindowItDoesNotCount() {
    // Otto giorni: fuori dalla finestra di sette che la barra gia' usa. Le due cose devono
    // raccontare la stessa settimana, o sono due verita' diverse sulla stessa pagina.
    assertNull(upcomingAssessment(listOf(item("a", today.plusDays(8))), today))
    assertEquals("a", upcomingAssessment(listOf(item("a", today.plusDays(7))), today)?.id)
  }

  @Test
  fun onlyAssessmentsCount() {
    val items = listOf(
      item("compito", today.plusDays(1), category = AgendaCategory.HOMEWORK),
      item("lezione", today.plusDays(1), category = AgendaCategory.LESSON),
      item("evento", today.plusDays(1), category = AgendaCategory.EVENT),
      item("personale", today.plusDays(1), category = AgendaCategory.CUSTOM),
    )
    assertNull(upcomingAssessment(items, today))
  }

  @Test
  fun anUnparsableDateDoesNotBlowUp() {
    val broken = AgendaItem(
      id = "rotta",
      title = "?",
      subtitle = "",
      date = "non-una-data",
      category = AgendaCategory.ASSESSMENT,
    )
    assertNull(upcomingAssessment(listOf(broken), today))
  }

  @Test
  fun theWaitIsSaidTheWayAPersonWouldSayIt() {
    assertEquals("oggi", upcomingAssessmentWhen(0L))
    assertEquals("domani", upcomingAssessmentWhen(1L))
    assertEquals("fra 3 giorni", upcomingAssessmentWhen(3L))
  }
}
