package dev.antigravity.classevivaexpressive.feature.communications

import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunicationsSectionsTest {

  private data class Entry(val id: String, val date: String)

  @Test
  fun `groups months with stable keys and preserves repository order`() {
    val entries = listOf(
      Entry("a", "2026-08-22"),
      Entry("b", "2026-08-03T09:15:00"),
      Entry("c", "2026-07-31"),
      Entry("d", "not-a-date"),
    )

    val result = archiveMonthSections(entries, Entry::date, Locale.ITALIAN)

    assertEquals(listOf("2026-08", "2026-07", "unknown-date"), result.map { it.key })
    assertEquals(listOf("a", "b"), result.first().items.map { it.id })
    assertEquals("Agosto 2026", result.first().label)
    assertEquals("Senza data", result.last().label)
  }

  @Test
  fun `empty archive has no phantom section`() {
    assertEquals(emptyList<ArchiveMonthSection<Entry>>(), archiveMonthSections(emptyList(), Entry::date))
  }

  @Test
  fun `board anchors include unread and exact lazy item indices`() {
    val sections = archiveMonthSections(
      listOf(
        communication("a", "2026-08-22", read = true),
        communication("b", "2026-08-10", read = false),
        communication("c", "2026-07-02", read = true),
      ),
      Communication::date,
    )

    val anchors = communicationSectionAnchors(
      sections = sections,
      includeMarkAllReadAction = true,
      includeUnreadAnchor = true,
    )

    assertEquals(listOf("month:2026-08", "unread", "month:2026-07"), anchors.map { it.key })
    assertEquals(listOf(5, 7, 8), anchors.map { it.itemIndex })
  }

  @Test
  fun `note month indices account for every preceding header and row`() {
    val sections = archiveMonthSections(
      listOf(
        note("a", "2026-08-22"),
        note("b", "2026-08-10"),
        note("c", "2026-07-02"),
      ),
      Note::date,
    )

    assertEquals(listOf(3, 6), noteSectionAnchors(sections).map { it.itemIndex })
  }

  private fun communication(id: String, date: String, read: Boolean) = Communication(
    id = id,
    pubId = "pub-$id",
    evtCode = "evt-$id",
    title = id,
    contentPreview = "",
    sender = "",
    date = date,
    read = read,
  )

  private fun note(id: String, date: String) = Note(
    id = id,
    categoryCode = "category",
    categoryLabel = "Categoria",
    title = id,
    contentPreview = "",
    date = date,
    author = "Docente",
    read = true,
    severity = "",
  )
}
