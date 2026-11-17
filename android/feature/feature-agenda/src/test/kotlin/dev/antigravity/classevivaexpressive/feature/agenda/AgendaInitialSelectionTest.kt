package dev.antigravity.classevivaexpressive.feature.agenda

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaInitialSelectionTest {
  private val date = LocalDate.of(2026, 11, 17)

  private fun item(id: String = "a", dateText: String = date.toString()) = AgendaItem(
    id = id, title = "Verifica", subtitle = "Matematica", date = dateText,
    category = AgendaCategory.ASSESSMENT,
  )

  @Test
  fun datePickerUsesMidnightUtcInEveryDeviceTimeZone() {
    val original = TimeZone.getDefault()
    try {
      listOf("Europe/Rome", "America/Los_Angeles", "Pacific/Kiritimati").forEach { zone ->
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val pickerMillis = date.toEpochMillis()
        assertEquals(zone, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), pickerMillis)
        assertEquals(zone, date, millisToLocalDate(pickerMillis))
      }
    } finally {
      TimeZone.setDefault(original)
    }
  }

  @Test
  fun datePickerSelectionDoesNotMoveOneDayInWesternTimeZonesOrDuringDst() {
    val original = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
      listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25)).forEach { selected ->
        val pickerMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(selected, millisToLocalDate(pickerMillis))
      }
    } finally {
      TimeZone.setDefault(original)
    }
  }

  @Test
  fun initialDateIsAppliedOnceSoARefreshCannotResetTheUsersCalendarSelection() {
    assertEquals(date, initialAgendaDateToSelect(date.toString(), consumedDate = null))
    assertNull(initialAgendaDateToSelect(date.toString(), consumedDate = date.toString()))
    assertEquals(date.plusDays(1), initialAgendaDateToSelect(date.plusDays(1).toString(), date.toString()))
    assertNull(initialAgendaDateToSelect("invalid", consumedDate = null))
  }

  @Test
  fun initialEntryWaitsForSyncThenDoesNotReopenAfterRefreshOrCategoryEdit() {
    assertNull(initialAgendaRequestToOpen("a", null, emptyList()))
    assertEquals("a", initialAgendaRequestToOpen("a", null, listOf(item())))
    assertNull(initialAgendaRequestToOpen("a", "a", listOf(item().copy(category = AgendaCategory.EVENT))))
    assertEquals("b", initialAgendaRequestToOpen("b", "a", listOf(item("b"))))
  }

  @Test
  fun invalidEntryDatesDoNotConsumeThePendingRequest() {
    assertNull(initialAgendaRequestToOpen("a", null, listOf(item(dateText = "invalid"))))
    assertEquals("a", initialAgendaRequestToOpen("a", null, listOf(item())))
  }
}
