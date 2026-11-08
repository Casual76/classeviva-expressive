package dev.antigravity.classevivaexpressive.core.domain.model

import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.serialization.json.*

/** Explicit source scope. Account is the authenticated username, never an impersonation request. */
data class SchoolDataQuery(
  val section: String, val year: SchoolYearRef, val account: String? = null,
  val student: String? = null, val from: String? = null, val to: String? = null,
  val subject: String? = null, val period: String? = null, val cursor: String? = null,
  val limit: Int = 20, val refresh: Boolean = false, val search: String? = null,
) {
  fun validate() {
    require(section in Sections) { "unsupported_section" }
    require(limit in 1..100) { "invalid_limit" }
    require(year.endYear == year.startYear + 1 && year.startYear in 2000..2100) { "invalid_year" }
    val start = from?.let(LocalDate::parse); val end = to?.let(LocalDate::parse)
    require(start == null || end == null || start <= end) { "invalid_range" }
    val acquiredFrom = LocalDate.of(year.startYear, 9, 1)
    val acquiredTo = LocalDate.of(year.endYear, 8, 31)
    require((start == null || start in acquiredFrom..acquiredTo) &&
      (end == null || end in acquiredFrom..acquiredTo)) { "range_outside_school_year" }
    require(period == null || section == "grades") { "unsupported_period_filter" }
  }
  companion object { val Sections = setOf("grades", "agenda", "lessons", "communications", "absences", "notes", "materials", "documents") }
}
data class SchoolDataPage(
  val records: List<JsonObject>, val total: Int, val nextCursor: String?, val aggregate: JsonObject,
  val account: String, val student: String, val year: String, val section: String,
  val syncedAt: Long?, val observedAt: Long, val stale: Boolean, val refreshed: Boolean,
  val complete: Boolean?, val authValid: Boolean?, val from: String?, val to: String?,
)
class SchoolDataUnavailable(val code: String, val retryable: Boolean = false) : Exception(code)
interface SchoolDataAccess { suspend fun read(query: SchoolDataQuery): SchoolDataPage }

/** Stable snapshot cursors reject mutation/filter changes instead of skipping or repeating rows. */
object SchoolDataPaging {
  private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }.take(24)
  fun matching(query: SchoolDataQuery, records: List<JsonObject>): List<JsonObject> {
    query.validate()
    val dateKey = when(query.section) { "materials" -> "sharedAt"; else -> "date" }
    fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    return records.filter { r ->
      val date = r.text(dateKey)?.take(10)
      (query.from == null || (date != null && date >= query.from)) &&
      (query.to == null || (date != null && date <= query.to)) &&
      (query.search == null || r.toString().contains(query.search, ignoreCase = true)) &&
      (query.subject == null || r.text("subject").orEmpty().equals(query.subject, true)) &&
      (query.period == null || r.text("periodCode") == query.period || r.text("period") == query.period)
    }.sortedWith(compareByDescending<JsonObject> { it.text(dateKey).orEmpty() }.thenBy { it.text("id").orEmpty() }.thenBy { it.toString() })
  }
  fun page(query: SchoolDataQuery, records: List<JsonObject>, snapshot: String): Triple<List<JsonObject>, Int, String?> {
    val matching = matching(query, records)
    val token = digest(listOf(query.section, query.year.id, query.account, query.student, query.from, query.to, query.subject, query.period, query.search, snapshot, matching.toString()).toString())
    val offset = query.cursor?.let {
      val pieces = it.split(':'); require(pieces.size == 2 && pieces[0] == token) { "invalid_or_expired_cursor" }
      pieces[1].toIntOrNull()?.also { n -> require(n in 1 until matching.size) { "invalid_cursor" } }
        ?: throw IllegalArgumentException("invalid_cursor")
    } ?: 0
    val end = minOf(offset + query.limit, matching.size)
    return Triple(matching.subList(offset, end), matching.size, if (end < matching.size) "$token:$end" else null)
  }
  fun aggregates(records: List<JsonObject>): JsonObject = buildJsonObject {
    put("count", records.size)
    val grades = records.mapNotNull { r ->
      val value = (r["numericValue"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
      val raw = (r["weight"] as? JsonPrimitive)?.doubleOrNull ?: 1.0
      value to when { raw <= 0 -> 0.0; raw > 5 -> raw / 100; else -> raw }
    }
    val weight = grades.sumOf { it.second }
    if (weight > 0) put("weightedAverage", grades.sumOf { it.first * it.second } / weight)
    put("numericCount", grades.size)
  }
}
