package dev.antigravity.classevivaexpressive.core.domain.model

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SchoolDataPagingTest {
  private val query = SchoolDataQuery("grades", SchoolYearRef(2026, 2027), limit = 1)
  private fun row(id: String, value: Double = 6.0, subject: String = "Math") = buildJsonObject {
    put("id", id); put("date", "2026-11-01"); put("subject", subject); put("periodCode", "1"); put("numericValue", value)
  }
  @Test fun tiedDatesUseStableIdsAndCursorCanBeRepeated() {
    val rows = listOf(row("b"), row("a"), row("c"))
    val first = SchoolDataPaging.page(query, rows, "v1")
    assertEquals("a", first.first.single()["id"]!!.jsonPrimitive.content)
    val second = SchoolDataPaging.page(query.copy(cursor = first.third), rows.reversed(), "v1")
    assertEquals(second, SchoolDataPaging.page(query.copy(cursor = first.third), rows, "v1"))
    assertEquals("b", second.first.single()["id"]!!.jsonPrimitive.content)
    assertNull(SchoolDataPaging.page(query.copy(cursor = second.third), rows, "v1").third)
  }
  @Test fun emptyIsKnownAndHasNoCursor() {
    assertEquals(Triple(emptyList<JsonObject>(), 0, null), SchoolDataPaging.page(query, emptyList(), "v1"))
  }
  @Test fun filtersAndAggregatesUseAllMatchingRows() {
    val rows = listOf(row("a", 4.0), row("b", 8.0), row("c", 10.0, "Italian"))
    val filtered = SchoolDataPaging.matching(query.copy(subject = "Math", period = "1", from = "2026-10-01", to = "2026-12-01"), rows)
    assertEquals(2, filtered.size)
    assertEquals(6.0, SchoolDataPaging.aggregates(filtered)["weightedAverage"]!!.jsonPrimitive.double, 0.0)
    assertTrue(SchoolDataPaging.matching(query.copy(period = "2"), rows).isEmpty())
  }
  @Test fun changedScopeSnapshotOrFiltersRejectCursor() {
    val rows = listOf(row("a"), row("b")); val cursor = SchoolDataPaging.page(query, rows, "v1").third
    for (changed in listOf(query.copy(account = "other"), query.copy(student = "other"), query.copy(year = SchoolYearRef(2025, 2026)), query.copy(subject = "Math"))) {
      assertThrows(IllegalArgumentException::class.java) { SchoolDataPaging.page(changed.copy(cursor = cursor), rows, "v1") }
    }
    assertThrows(IllegalArgumentException::class.java) { SchoolDataPaging.page(query.copy(cursor = cursor), rows, "v2") }
  }
  @Test fun malformedCursorAndRangeFail() {
    for (cursor in listOf("oops", "x:-1", "x:999999999999999")) assertThrows(IllegalArgumentException::class.java) { SchoolDataPaging.page(query.copy(cursor = cursor), listOf(row("a")), "v1") }
    assertThrows(IllegalArgumentException::class.java) { query.copy(from = "2026-12-01", to = "2026-11-01").validate() }
  }
  @Test fun changedContentsAtSameTimestampRejectCursor() {
    val rows = listOf(row("a"), row("b"))
    val cursor = SchoolDataPaging.page(query, rows, "same-timestamp").third
    assertThrows(IllegalArgumentException::class.java) {
      SchoolDataPaging.page(query.copy(cursor = cursor), listOf(row("a", 10.0), row("b")), "same-timestamp")
    }
  }
}
