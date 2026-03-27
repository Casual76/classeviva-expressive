package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import dev.antigravity.classevivaexpressive.core.domain.model.Schoolbook
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

private val IsoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private const val PortalBaseUrl = "https://web.spaggiari.eu"

internal fun extractArray(payload: JsonObject, vararg keys: String): List<JsonElement> {
  keys.forEach { key ->
    val candidate = payload[key]
    if (candidate is JsonArray) {
      return candidate
    }
  }
  return emptyList()
}

internal fun normalizeProfile(root: JsonObject, fallbackId: String): StudentProfile {
  val data = (root["card"] as? JsonObject) ?: root
  val school = listOf(
    data.string("schName"),
    data.string("schDedication"),
    data["scuola"].obj().string("desc"),
    data.string("school"),
  ).filterNotNull().joinToString(" ").replace(Regex("\\s+"), " ").trim()

  return StudentProfile(
    id = normalizeStudentId(data.string("usrId")) ?: normalizeStudentId(data.string("id")) ?: fallbackId,
    name = data.string("firstName", "name").orEmpty(),
    surname = data.string("lastName", "surname").orEmpty(),
    email = data.string("email").orEmpty(),
    schoolClass = data.string("classDesc", "class") ?: data["classe"].obj().string("desc").orEmpty(),
    section = data["classe"].obj().string("sezione") ?: data.string("section").orEmpty(),
    school = school,
    schoolYear = data.string("anno", "schoolYear") ?: schoolYearNow(),
  )
}

internal fun normalizeGrade(data: JsonElement): Grade {
  val obj = data.obj()
  val numeric = obj.double("decimalValue", "votoDecimale", "grade")
  return Grade(
    id = obj.string("id", "evtId").orEmpty(),
    subject = obj.string("subjectDesc", "subject") ?: obj["materia"].obj().string("desc").orEmpty(),
    valueLabel = numeric?.let(::trimZero) ?: obj.string("displayValue", "grade").orEmpty(),
    numericValue = numeric,
    description = obj.string("descrVoto", "description"),
    date = normalizeDate(obj.string("evtDate", "dataRegistrazione", "date")),
    type = obj.string("componentDesc", "type") ?: obj["tipo"].obj().string("desc").orEmpty().ifBlank { "Valutazione" },
    weight = obj.double("weightFactor", "peso", "weight"),
    notes = obj.string("notesForFamily", "note", "notes"),
    period = obj.string("periodDesc", "periodLabel", "period"),
    teacher = obj.string("teacherName", "teacher"),
    color = obj.string("color"),
  )
}

internal fun normalizeLesson(data: JsonElement): Lesson {
  val obj = data.obj()
  return Lesson(
    id = obj.string("id", "lessonId", "evtId").orEmpty(),
    subject = obj.string("subjectDesc", "subject") ?: obj["materia"].obj().string("desc").orEmpty(),
    date = normalizeDate(obj.string("data", "date", "evtDate")),
    time = normalizeTime(obj.string("lessonHour", "ora", "time", "startTime")) ?: "",
    durationMinutes = obj.int("duration", "durata", "evtDuration") ?: 60,
    topic = obj.string("argomento", "topic", "lessonArg"),
    teacher = obj.string("teacherName", "authorName", "teacher"),
    room = obj.string("classroom", "room"),
  )
}

internal fun normalizeHomework(data: JsonElement): Homework {
  val obj = data.obj()
  return Homework(
    id = obj.string("id", "hwId", "evtId", "homeworkId").orEmpty(),
    subject = obj.string("subjectDesc", "subject") ?: obj["materia"].obj().string("desc").orEmpty(),
    description = obj.string("contenuto", "description", "notes", "title").orEmpty(),
    dueDate = normalizeDate(obj.string("dataConsegna", "dueDate", "date", "evtDate", "evtDatetimeEnd")),
    notes = obj.string("note", "notesForFamily", "notes"),
    attachments = normalizeAttachments(obj["allegati"] ?: obj["attachments"]),
  )
}

internal fun normalizeAbsence(data: JsonElement): AbsenceRecord {
  val obj = data.obj()
  val rawType = obj.string("tipo", "evtCode").orEmpty().lowercase()
  val type = when {
    rawType.contains("rit") -> AbsenceType.LATE
    rawType.contains("usc") || rawType.contains("exit") -> AbsenceType.EXIT
    else -> AbsenceType.ABSENCE
  }
  return AbsenceRecord(
    id = obj.string("id", "evtId", "absenceId").orEmpty(),
    date = normalizeDate(obj.string("evtDate", "data", "date")),
    type = type,
    hours = obj.int("hoursAbsence", "ore", "hours"),
    justified = obj.bool("isJustified", "giustificata", "justified") ?: false,
    justificationDate = obj.string("dataGiustificazione", "justificationDate"),
    justificationReason = obj.string("justifReasonDesc", "motivoGiustificazione", "justificationReason"),
    justifyUrl = findActionUrl(data, "justify", "giust", "justif"),
    detailUrl = normalizeUrlCandidate(obj.string("detailUrl", "detailLink"))
      ?: findActionUrl(data, "detail", "event", "scheda"),
  )
}

internal fun normalizeCommunication(data: JsonElement): Communication {
  val obj = data.obj()
  val attachments = normalizeAttachments(obj["attachments"] ?: obj["allegati"])
  val requiresAction = (obj.bool("needSign") ?: false) ||
    (obj.bool("needReply") ?: false) ||
    (obj.bool("needJoin") ?: false) ||
    (obj.bool("needFile") ?: false)
  return Communication(
    id = obj.string("id", "pubId", "commId", "evento_id").orEmpty(),
    pubId = obj.string("pubId", "id", "commId", "evento_id").orEmpty(),
    evtCode = obj.string("evtCode", "code").orEmpty(),
    title = obj.string("cntTitle", "title", "titolo", "evtTitle") ?: "Comunicazione",
    contentPreview = obj.string("itemText", "texto", "content", "description", "notes", "cntCategory").orEmpty(),
    sender = obj.string("authorName", "mittente", "sender") ?: "Scuola",
    date = normalizeDate(obj.string("data", "date", "pubDT")),
    read = obj.bool("read", "letto", "isRead", "readStatus") ?: false,
    attachments = attachments,
    category = obj.string("cntCategory", "category"),
    needsAck = obj.bool("needSign") ?: false,
    needsReply = obj.bool("needReply") ?: false,
    needsJoin = obj.bool("needJoin") ?: false,
    needsFile = obj.bool("needFile") ?: false,
    capabilityState = if (attachments.isNotEmpty() || requiresAction) {
      CapabilityState(
        CapabilityStatus.EXTERNAL_ONLY,
        "Azioni e allegati disponibili",
        "La comunicazione puo richiedere conferma, risposta, adesione o file tramite portale.",
      )
    } else {
      CapabilityState(CapabilityStatus.AVAILABLE, "Dettaglio disponibile", "Contenuto testuale leggibile in app.")
    },
  )
}

internal fun normalizeCommunicationDetail(root: JsonObject, base: Communication): CommunicationDetail {
  val item = (root["item"] ?: root["event"]).obj().takeIf { it.isNotEmpty() } ?: root
  val reply = root["reply"].obj()
  val content = item.string("text", "evtText", "content", "description") ?: base.contentPreview
  return CommunicationDetail(
    communication = base.copy(
      attachments = normalizeAttachments(root["attachments"] ?: root["allegati"]).ifEmpty { base.attachments },
    ),
    content = content,
    replyText = reply.string("text", "replyText", "description"),
    portalDetailUrl = findActionUrl(root, "detail", "view", "href", "link"),
    acknowledgeUrl = findActionUrl(root, "sign", "ack", "presa", "visione", "confirm"),
    replyUrl = findActionUrl(root, "reply", "response", "risposta"),
    joinUrl = findActionUrl(root, "join", "ades", "particip"),
    fileUploadUrl = findActionUrl(root, "upload", "file", "attach", "alleg"),
  )
}

internal fun normalizeAgendaItem(data: JsonElement): AgendaItem {
  val obj = data.obj()
  val title = obj.string("title", "evtTitle", "notes", "description", "content")
    ?: obj.string("subjectDesc", "subject")
    ?: obj["materia"].obj().string("desc")
    ?: "Evento"
  val subject = obj.string("subjectDesc", "subject", "classDesc") ?: obj["materia"].obj().string("desc")
  val typeSource = listOf(obj.string("type", "eventTypeDesc", "evtCode", "eventCode"), title)
    .filterNotNull()
    .joinToString(" ")
    .lowercase()
  val category = when {
    typeSource.contains("compit") || typeSource.contains("homework") -> AgendaCategory.HOMEWORK
    typeSource.contains("verific") || typeSource.contains("test") || typeSource.contains("interrog") -> AgendaCategory.ASSESSMENT
    typeSource.contains("lezion") || typeSource.contains("lesson") -> AgendaCategory.LESSON
    else -> AgendaCategory.EVENT
  }
  val date = normalizeDate(obj.string("date", "evtDate", "data", "evtDatetimeBegin"))
  val time = normalizeTime(obj.string("time", "startTime", "lessonHour", "ora", "evtDatetimeBegin"))
  return AgendaItem(
    id = obj.string("id", "evtId").orEmpty(),
    title = title,
    subtitle = subject.orEmpty(),
    date = date,
    time = time,
    detail = obj.string("description", "notes", "content"),
    subject = subject,
    category = category,
    sharePayload = listOf(title, date, time, subject).filterNotNull().joinToString(" - "),
  )
}

internal fun normalizeNote(data: JsonObject, categoryCode: String): Note {
  val content = data.string("evtText", "content", "description").orEmpty()
  val label = when (categoryCode) {
    "NTTE" -> "Nota docente"
    "NTCL" -> "Nota di classe"
    "NTWN" -> "Richiamo"
    "NTST" -> "Nota disciplinare"
    else -> "Nota"
  }
  return Note(
    id = data.string("evtId", "id").orEmpty(),
    categoryCode = categoryCode,
    categoryLabel = label,
    title = preview(content, label),
    contentPreview = preview(content, "Nessun dettaglio disponibile"),
    date = normalizeDate(data.string("evtDate", "date")),
    author = data.string("authorName", "author") ?: "Docente",
    read = data.bool("readStatus", "read") ?: false,
    severity = when (categoryCode) {
      "NTWN" -> "critical"
      "NTTE" -> "warning"
      else -> "info"
    },
  )
}

internal fun normalizeMaterialFolder(folder: JsonObject, teacherId: String, teacherName: String): List<MaterialItem> {
  val folderId = folder.string("folderId", "id").orEmpty()
  val folderName = folder.string("folderName", "title") ?: "Materiali"
  return extractArray(folder["contents"].obj(), "contents").map { contentElement ->
    val content = contentElement.obj()
    val objectType = content.string("objectType", "type").orEmpty()
    val capability = if (objectType == "file") {
      CapabilityState(CapabilityStatus.EXTERNAL_ONLY, "Apribile come documento", "Preview e download nativi disponibili.")
    } else {
      CapabilityState(CapabilityStatus.AVAILABLE, "Contenuto disponibile", "Materiale leggibile direttamente o scaricabile.")
    }
    MaterialItem(
      id = content.string("id", "contentId", "itemId").orEmpty(),
      teacherId = teacherId,
      teacherName = teacherName,
      folderId = folderId,
      folderName = folderName,
      title = content.string("title", "name").orEmpty(),
      objectId = content.string("objectId", "oid").orEmpty(),
      objectType = objectType,
      sharedAt = normalizeDate(content.string("shareDT", "sharedAt", "lastShareDT")),
      capabilityState = capability,
    )
  }
}

internal fun normalizeMaterialAsset(
  item: MaterialItem,
  mimeType: String?,
  base64Content: String?,
  textPreview: String?,
): MaterialAsset {
  return MaterialAsset(
    id = item.id,
    title = item.title,
    objectType = item.objectType,
    fileName = item.title,
    mimeType = mimeType,
    base64Content = base64Content,
    textPreview = textPreview,
    capabilityState = if (base64Content != null) {
      CapabilityState(CapabilityStatus.EXTERNAL_ONLY, "Documento pronto", "Il contenuto e pronto per preview o download nativo.")
    } else {
      CapabilityState(CapabilityStatus.UNAVAILABLE, "Preview non disponibile", "Il portale non ha restituito un contenuto apribile.")
    },
  )
}

fun normalizeDocumentAsset(
  item: DocumentItem,
  fileName: String?,
  mimeType: String?,
  base64Content: String?,
  textPreview: String?,
  sourceUrl: String?,
): DocumentAsset {
  return DocumentAsset(
    id = item.id,
    title = item.title,
    fileName = fileName ?: item.title,
    mimeType = mimeType,
    base64Content = base64Content,
    textPreview = textPreview,
    sourceUrl = sourceUrl,
    capabilityState = when {
      mimeType?.startsWith("image/") == true ->
        CapabilityState(CapabilityStatus.AVAILABLE, "Anteprima immagine", "Apri, visualizza o esporta direttamente.")
      mimeType == "text/html" ->
        CapabilityState(CapabilityStatus.AVAILABLE, "Anteprima web", "Il contenuto puo essere letto nel viewer autenticato.")
      mimeType?.startsWith("text/") == true ->
        CapabilityState(CapabilityStatus.AVAILABLE, "Anteprima testuale", "Il contenuto testuale e disponibile in app.")
      mimeType == "application/pdf" ->
        CapabilityState(CapabilityStatus.EXTERNAL_ONLY, "PDF pronto", "Apribile tramite app esterna o download locale.")
      else ->
        CapabilityState(CapabilityStatus.EXTERNAL_ONLY, "File pronto", "Usa apertura file o download locale.")
    },
  )
}

internal fun normalizeDocument(data: JsonElement): DocumentItem {
  val obj = data.obj()
  val viewUrl = obj.string("viewLink")
  return DocumentItem(
    id = obj.string("viewLink", "confirmLink", "desc").orEmpty().ifBlank { obj.string("desc").orEmpty() },
    title = obj.string("desc", "title") ?: "Documento",
    detail = if (viewUrl != null) "Documento apribile dal portale autenticato." else "Il portale non ha fornito un link diretto.",
    viewUrl = viewUrl,
    confirmUrl = obj.string("confirmLink"),
    capabilityState = if (viewUrl != null) {
      CapabilityState(CapabilityStatus.EXTERNAL_ONLY, "Apri documento", "Apertura nativa o via portale autenticato.")
    } else {
      CapabilityState(CapabilityStatus.UNAVAILABLE, "Documento non disponibile", "Il link diretto non e disponibile.")
    },
  )
}

internal fun normalizeSchoolbookCourse(data: JsonElement): SchoolbookCourse {
  val obj = data.obj()
  val books = extractArray(obj["books"].obj(), "books").map { bookElement ->
    val book = bookElement.obj()
    Schoolbook(
      id = book.string("bookId", "id").orEmpty(),
      isbn = book.string("isbnCode", "isbn").orEmpty(),
      title = book.string("title") ?: "Libro",
      subtitle = book.string("subheading"),
      volume = book.string("volume"),
      author = book.string("author"),
      publisher = book.string("publisher"),
      subject = book.string("subjectDesc", "subject") ?: "Materia",
      price = book.double("price"),
      coverUrl = book.string("coverUrl")?.replace("//", "https://"),
      toBuy = book.bool("toBuy") ?: false,
      alreadyOwned = book.bool("alreadyOwned") ?: false,
      alreadyInUse = book.bool("alreadyInUse") ?: false,
      recommended = book.bool("recommended") ?: false,
      recommendedFor = book.string("recommendedFor"),
      newAdoption = book.bool("newAdoption") ?: false,
    )
  }
  return SchoolbookCourse(
    id = obj.string("courseId", "id").orEmpty(),
    title = obj.string("courseDesc", "description") ?: "Corso",
    books = books,
  )
}

internal fun normalizePeriod(data: JsonElement): Period {
  val obj = data.obj()
  return Period(
    code = obj.string("periodCode", "code").orEmpty(),
    order = obj.int("periodPos", "order") ?: 0,
    description = obj.string("periodDesc", "description") ?: "Periodo",
    label = obj.string("periodLabel", "label", "periodDesc") ?: "Periodo",
    isFinal = obj.bool("isFinal") ?: false,
    startDate = normalizeDate(obj.string("dateStart", "startDate")),
    endDate = normalizeDate(obj.string("dateEnd", "endDate")),
  )
}

internal fun normalizeSubject(data: JsonElement): Subject {
  val obj = data.obj()
  val teachers = extractArray(obj["teachers"].obj(), "teachers").mapNotNull { teacher ->
    teacher.obj().string("teacherName", "name")
  }
  return Subject(
    id = obj.string("id", "subjectId").orEmpty(),
    description = obj.string("description", "subjectDesc") ?: "Materia",
    order = obj.int("order", "ord") ?: 0,
    teachers = teachers,
  )
}

internal fun normalizeAttachments(data: JsonElement?): List<RemoteAttachment> {
  return when (data) {
    is JsonArray -> data.mapNotNull { item ->
      when (item) {
        is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }?.let {
          RemoteAttachment(id = it, name = it, portalOnly = true)
        }
        is JsonObject -> {
          val name = item.string("name", "fileName", "attachName", "title") ?: return@mapNotNull null
          RemoteAttachment(
            id = item.string("id", "attachId", "uuid") ?: name,
            name = name,
            url = normalizeUrlCandidate(item.string("url", "link", "href")),
            mimeType = item.string("mimeType", "contentType"),
            portalOnly = item.string("url", "link", "href") == null,
          )
        }
        else -> null
      }
    }
    else -> emptyList()
  }
}

internal fun JsonObject.string(vararg keys: String): String? {
  keys.forEach { key ->
    val candidate = (this[key] as? JsonPrimitive)?.primitiveContentOrNull()
    if (!candidate.isNullOrBlank()) return candidate.trim()
  }
  return null
}

internal fun JsonObject.double(vararg keys: String): Double? {
  keys.forEach { key ->
    val primitive = this[key] as? JsonPrimitive ?: return@forEach
    primitive.doubleOrNull?.let { return it }
    primitive.contentOrNull?.replace(",", ".")?.toDoubleOrNull()?.let { return it }
  }
  return null
}

internal fun JsonObject.int(vararg keys: String): Int? {
  keys.forEach { key ->
    val primitive = this[key] as? JsonPrimitive ?: return@forEach
    primitive.intOrNull?.let { return it }
    primitive.contentOrNull?.toIntOrNull()?.let { return it }
  }
  return null
}

internal fun JsonObject.bool(vararg keys: String): Boolean? {
  keys.forEach { key ->
    val primitive = this[key] as? JsonPrimitive ?: return@forEach
    primitive.booleanOrNull?.let { return it }
    when (primitive.contentOrNull?.lowercase()) {
      "1", "true", "yes", "read", "letto", "done" -> return true
      "0", "false", "no", "unread", "nonletto" -> return false
    }
  }
  return null
}

internal fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonElement?.array(): List<JsonElement> = this as? JsonArray ?: emptyList()

private fun JsonPrimitive.primitiveContentOrNull(): String? = if (isString) content else contentOrNull

private fun findActionUrl(root: JsonElement?, vararg keywords: String): String? {
  val loweredKeywords = keywords.map(String::lowercase)

  fun visit(element: JsonElement?): String? {
    return when (element) {
      is JsonObject -> {
        element.entries.forEach { (key, value) ->
          val loweredKey = key.lowercase()
          if (loweredKeywords.any { loweredKey.contains(it) }) {
            extractNestedUrl(value)?.let { return it }
          }
          visit(value)?.let { return it }
        }
        null
      }
      is JsonArray -> element.firstNotNullOfOrNull(::visit)
      else -> null
    }
  }

  return visit(root)
}

private fun extractNestedUrl(element: JsonElement?): String? {
  return when (element) {
    is JsonPrimitive -> normalizeUrlCandidate(element.contentOrNull)
    is JsonObject -> {
      element.string("url", "href", "link", "action", "path")?.let(::normalizeUrlCandidate)
        ?: element.entries.firstNotNullOfOrNull { (_, value) -> extractNestedUrl(value) }
    }
    is JsonArray -> element.firstNotNullOfOrNull(::extractNestedUrl)
    else -> null
  }
}

private fun normalizeUrlCandidate(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
  return when {
    raw.startsWith("http://") || raw.startsWith("https://") -> raw
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("/") -> "$PortalBaseUrl$raw"
    raw.startsWith("home/") || raw.startsWith("sgv/") || raw.contains(".php") -> "$PortalBaseUrl/$raw"
    else -> null
  }
}

internal fun normalizeStudentId(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val match = Regex("^[SG](\\d+)(?:[A-Z]+)?$", RegexOption.IGNORE_CASE).find(raw)
  return match?.groupValues?.get(1) ?: raw.filter(Char::isDigit).ifBlank { raw }
}

internal fun normalizeDate(value: String?): String {
  val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return LocalDate.now().format(IsoDateFormatter)
  return runCatching {
    when {
      Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw) -> raw
      Regex("^\\d{8}$").matches(raw) -> LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).format(IsoDateFormatter)
      else -> Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDate().format(IsoDateFormatter)
    }
  }.getOrElse { raw }
}

internal fun normalizeTime(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return when {
    Regex("^\\d{2}:\\d{2}$").matches(raw) -> raw
    Regex("^\\d{1,2}:\\d{2}$").matches(raw) -> raw.padStart(5, '0')
    else -> raw
  }
}

internal fun toApiDateParam(value: String): String = value.replace("-", "")

internal fun preview(value: String, fallback: String): String {
  val source = value.ifBlank { fallback }
  return if (source.length > 150) "${source.take(147)}..." else source
}

private fun trimZero(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun schoolYearNow(): String {
  val now = LocalDate.now()
  val startYear = if (now.monthValue >= 8) now.year else now.year - 1
  return "$startYear-${startYear + 1}"
}
