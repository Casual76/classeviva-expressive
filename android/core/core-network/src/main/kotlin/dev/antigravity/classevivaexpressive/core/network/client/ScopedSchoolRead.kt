package dev.antigravity.classevivaexpressive.core.network.client

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private val strictSchoolRead = ThreadLocal<Boolean>()
/** Opt-in validation for assistant reads; legacy parsing remains compatible. */
suspend fun <T> withStrictSchoolRead(block: suspend () -> T): T =
  withContext(strictSchoolRead.asContextElement(true)) { block() }

internal fun isStrictSchoolRead(): Boolean = strictSchoolRead.get() == true

private fun invalid(): Nothing = throw ClassevivaNetworkException("source_payload_invalid")
private fun JsonObject.required(vararg keys: String): String =
  string(*keys)?.takeIf { !sanitizeRegisterText(it).isNullOrBlank() } ?: invalid()

/** Same primitive admission used by strict validation and the actual normalizers. */
internal fun strictTextPrimitive(value: JsonElement?): JsonPrimitive? {
  if (value == null || value is JsonNull) return null
  val primitive = value as? JsonPrimitive ?: invalid()
  if (!primitive.isString && primitive.doubleOrNull?.isFinite() != true) invalid()
  return primitive
}
private fun JsonObject.date(vararg keys: String) {
  val raw = required(*keys)
  if (runCatching { java.time.LocalDate.parse(raw.take(10)) }.isFailure) invalid()
}
private fun checkRow(value: JsonElement, section: String) {
  val r = value as? JsonObject ?: invalid()
  if (r.keys.any { it.lowercase() in setOf("error", "errors", "exception") }) invalid()
  when (section) {
    "grades" -> {
      r.required("id", "evtId", "gradeId"); r.date("evtDate", "dataRegistrazione", "date")
      val grade = normalizeGrade(r)
      if (grade.valueLabel.isBlank() || grade.numericValue?.isFinite() == false) invalid()
      if ((r.string("subjectDesc", "subject") ?: r["materia"].obj().string("desc")).isNullOrBlank()) invalid()
    }
    "agenda" -> {
      r.required("id", "evtId"); r.date("date", "evtDate", "data", "evtDatetimeBegin")
      r.required("title", "evtTitle", "notes", "description", "content", "subjectDesc", "subject")
    }
    "lessons" -> {
      r.required("id", "lessonId", "evtId"); r.date("data", "date", "evtDate", "evtDatetimeBegin")
      if ((r.string("subjectDesc", "subject") ?: r["materia"].obj().string("desc")).isNullOrBlank()) invalid()
    }
    "events" -> {
      r.required("id", "evtId", "absenceId"); r.date("evtDate", "data", "date")
      r.required("evtCode", "code", "eventCode", "type", "tipo", "eventType", "title", "description")
    }
    "items" -> {
      r.required("cntId", "evento_id", "id", "commId", "pubId"); r.date("evtDate", "data", "date", "pubDT")
      r.required("cntTitle", "title", "titolo", "evtTitle")
    }
    "notes" -> {
      r.required("evtId", "id"); r.date("evtDate", "date"); r.required("evtText", "content", "description")
    }
    "documents" -> {
      r.required("id", "documentId", "docId", "reportId", "hash", "documentHash", "docHash", "fileHash")
      r.required("desc", "description", "title", "name")
    }
    "material" -> {
      r.required("id", "contentId", "itemId", "objectId", "oid")
      r.required("contentName", "title", "name"); r.date("shareDT", "sharedAt", "lastShareDT")
    }
  }
  for (key in listOf("attachments", "allegati")) r[key]?.let { attachments ->
    if (attachments !is JsonArray) invalid()
    attachments.forEach { a ->
      val attachment = a as? JsonObject ?: invalid()
      attachment.required("id", "attachId", "name", "fileName", "url", "link")
    }
  }
}
internal fun validateSchoolNotes(payload: JsonObject) {
  if (!isStrictSchoolRead()) return
  if (payload.isEmpty()) invalid()
  payload.forEach { (key, value) ->
    if (key !in setOf("NTTE", "NTCL", "NTWN", "NTST") || value !is JsonArray) invalid()
    value.forEach { checkRow(it, "notes") }
  }
}
/** Legacy callers retain the original payload; strict callers normalize this selected payload only. */
internal fun validatedSchoolPayload(payload: JsonElement, vararg keys: String): JsonElement {
  if (!isStrictSchoolRead()) return payload
  val section = keys.first()
  fun objectWithoutErrors(value: JsonElement): JsonObject {
    val obj = value as? JsonObject ?: invalid()
    if (obj.keys.any { it.lowercase() in setOf("error", "errors", "exception") }) invalid()
    return obj
  }
  // No union/dedup semantics for alternative envelopes. Empty siblings cannot hide data.
  fun select(branches: List<JsonElement>): JsonElement {
    if (branches.isEmpty()) invalid()
    val nonempty = branches.filter { it !is JsonArray || it.isNotEmpty() }
    if (nonempty.size > 1) invalid()
    return nonempty.singleOrNull() ?: JsonArray(emptyList())
  }
  fun materialNode(value: JsonElement, folder: Boolean = false): JsonObject {
    val obj = objectWithoutErrors(value)
    val names = if (folder) setOf("contents", "items") else setOf("folders", "contents")
    val children = obj.filterKeys { it in names }
    if (children.isEmpty()) invalid()
    children.values.forEach { if (it !is JsonArray) invalid() }
    val selected = select(children.values.toList()) as JsonArray
    val key = children.entries.firstOrNull { it.value === selected }?.key ?: "contents"
    if (selected.isNotEmpty()) {
      if (folder) obj.required("folderId", "id") else obj.required("teacherId", "idDocente", "teacherCode")
    }
    val canonical = JsonArray(selected.map {
      if (key == "folders") materialNode(it, true) else it.also { checkRow(it, "material") }
    })
    return JsonObject(obj.filterKeys { it !in names } + ((if (key == "folders") "folders" else "contents") to canonical))
  }
  fun walk(value: JsonElement, depth: Int): JsonElement {
    if (depth > 8) invalid()
    if (value is JsonArray) return JsonArray(value.map {
      if (section == "didacticts") materialNode(it) else it.also { checkRow(it, section) }
    })
    val obj = objectWithoutErrors(value)
    if (section == "didacticts" && ("folders" in obj || "contents" in obj)) {
      // A direct teacher and an outer alternative envelope have no defined merge semantics.
      if (obj.keys.any { it in (keys.toSet() + setOf("events")) - setOf("folders", "contents") }) invalid()
      return JsonArray(listOf(materialNode(obj)))
    }
    val names = keys.toSet() + setOf("items", "events", "data")
    val branches = obj.filterKeys { it in names }
    if (branches.isEmpty()) invalid()
    // Existing document/report combination is intentional; preserve their kinds.
    if (section == "documents" && branches.keys.any { it in setOf("documents", "schoolReports") }) {
      val canonical = branches.mapValues { walk(it.value, depth + 1) }
      if (canonical.values.any { it !is JsonArray }) invalid()
      if (canonical.filterKeys { it !in setOf("documents", "schoolReports") }.values.any { (it as JsonArray).isNotEmpty() }) invalid()
      return JsonObject(canonical.filterKeys { it in setOf("documents", "schoolReports") })
    }
    return select(branches.values.map { walk(it, depth + 1) })
  }
  return walk(payload, 0)
}
internal fun validateSchoolCollection(payload: JsonElement, vararg keys: String) {
  validatedSchoolPayload(payload, *keys)
}
