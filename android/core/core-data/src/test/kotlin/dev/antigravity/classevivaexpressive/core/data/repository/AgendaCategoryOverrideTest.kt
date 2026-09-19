package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.database.database.AgendaCategoryOverrideEntity
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaCategoryOverrideTest {
  @Test
  fun overrideChangesOnlyMatchingItemAndKeepsOrder() {
    val items = listOf(
      agendaItem("a", AgendaCategory.ASSESSMENT),
      agendaItem("b", AgendaCategory.EVENT),
    )

    val result = applyAgendaCategoryOverrides(
      items,
      listOf(override("a", AgendaCategory.HOMEWORK.name)),
    )

    assertEquals(listOf("a", "b"), result.map { it.id })
    assertEquals(AgendaCategory.HOMEWORK, result[0].category)
    assertEquals(AgendaCategory.EVENT, result[1].category)
  }

  @Test
  fun invalidOrNonSelectableCategoriesAreIgnored() {
    val item = agendaItem("a", AgendaCategory.EVENT)

    assertEquals(
      AgendaCategory.EVENT,
      applyAgendaCategoryOverrides(listOf(item), listOf(override("a", "BROKEN"))).single().category,
    )
    assertEquals(
      AgendaCategory.EVENT,
      applyAgendaCategoryOverrides(listOf(item), listOf(override("a", AgendaCategory.LESSON.name))).single().category,
    )
  }

  private fun agendaItem(id: String, category: AgendaCategory) = AgendaItem(
    id = id,
    title = id,
    subtitle = "Materia",
    date = "2026-09-19",
    category = category,
  )

  private fun override(itemId: String, category: String) = AgendaCategoryOverrideEntity(
    studentId = "student",
    schoolYearId = "2026-2027",
    agendaItemId = itemId,
    category = category,
    updatedAtEpochMillis = 1L,
  )
}
