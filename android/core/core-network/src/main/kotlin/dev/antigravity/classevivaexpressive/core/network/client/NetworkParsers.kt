package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.domain.util.parseDecimal

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
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardAction
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardAttachment
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import dev.antigravity.classevivaexpressive.core.domain.model.Schoolbook
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup

private val IsoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private const val PortalBaseUrl = "https://web.spaggiari.eu"

internal fun extractArray(payload: JsonObject, vararg keys: String): List<JsonElement> {
  keys.forEach { key ->
    when (val candidate = payload[key]) {
      is JsonArray -> return candidate
      is JsonObject -> {
        val nested = extractArray(candidate, key, "items", "events", "data")
        if (nested.isNotEmpty()) return nested
      }
      else -> Unit
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
    name = sanitizeRegisterText(data.string("firstName", "name")).orEmpty(),
    surname = sanitizeRegisterText(data.string("lastName", "surname")).orEmpty(),
    email = data.string("email").orEmpty(),
    schoolClass = sanitizeRegisterText(data.string("classDesc", "class"))
      ?: sanitizeRegisterText(data["classe"].obj().string("desc")).orEmpty(),
    section = sanitizeRegisterText(data["classe"].obj().string("sezione"))
      ?: sanitizeRegisterText(data.string("section")).orEmpty(),
    school = school,
    schoolYear = sanitizeRegisterText(data.string("anno", "schoolYear")) ?: schoolYearNow(),
  )
}

internal fun normalizeGrade(data: JsonElement): Grade {
  val obj = data.obj()
  val numeric = obj.double("decimalValue", "votoDecimale", "grade", "value")
  return Grade(
    id = obj.string("id", "evtId", "gradeId").orEmpty(),
    subject = sanitizeRegisterText(obj.string("subjectDesc", "subject"))
      ?: sanitizeRegisterText(obj["materia"].obj().string("desc")).orEmpty(),
    valueLabel = numeric?.let(::trimZero) ?: obj.string("displayValue", "grade", "value", "voto").orEmpty(),
    numericValue = numeric,
    description = sanitizeRegisterText(obj.string("descrVoto", "description")),
    date = normalizeDate(obj.string("evtDate", "dataRegistrazione", "date")),
    type = sanitizeRegisterText(
      obj.string("componentDesc", "type", "tipoVoto")
        ?: obj["tipo"].obj().string("desc").orEmpty().ifBlank { "Valutazione" },
    ).orEmpty(),
    weight = obj.double("weightFactor", "peso", "weight"),
    notes = sanitizeRegisterText(obj.string("notesForFamily", "note", "notes")),
    period = sanitizeRegisterText(obj.string("periodDesc", "periodLabel", "period")),
    periodCode = obj.string("periodCode", "periodCd", "periodId"),
    teacher = sanitizeRegisterText(obj.string("teacherName", "teacher")),
    color = obj.string("color"),
  )
}

internal fun normalizeLesson(data: JsonElement): Lesson {
  val obj = data.obj()
  return Lesson(
    id = obj.string("id", "lessonId", "evtId").orEmpty(),
    subject = sanitizeRegisterText(obj.string("subjectDesc", "subject"))
      ?: sanitizeRegisterText(obj["materia"].obj().string("desc")).orEmpty(),
    date = normalizeDate(obj.string("data", "date", "evtDate", "evtDatetimeBegin")),
    time = normalizeTime(obj.string("lessonHour", "ora", "time", "startTime", "evtDatetimeBegin")).orEmpty(),
    durationMinutes = obj.int("duration", "durata", "evtDuration") ?: 60,
    topic = sanitizeRegisterText(obj.string("argomento", "topic", "lessonArg")),
    teacher = sanitizeRegisterText(obj.string("teacherName", "authorName", "teacher")),
    room = sanitizeRegisterText(obj.string("classroom", "room")),
  )
}

internal fun normalizeHomework(data: JsonElement): Homework {
  val obj = data.obj()
  return Homework(
    id = obj.string("id", "hwId", "evtId", "homeworkId").orEmpty(),
    subject = sanitizeRegisterText(obj.string("subjectDesc", "subject"))
      ?: sanitizeRegisterText(obj["materia"].obj().string("desc")).orEmpty(),
    description = sanitizeRegisterText(obj.string("contenuto", "description", "notes", "title")).orEmpty(),
    dueDate = normalizeDate(obj.string("dataConsegna", "dueDate", "date", "evtDate", "evtDatetimeEnd")),
    notes = sanitizeRegisterText(obj.string("note", "notesForFamily", "notes")),
    attachments = normalizeAttachments(obj["allegati"] ?: obj["attachments"]),
  )
}

internal fun normalizeAbsence(data: JsonElement): AbsenceRecord {
  val obj = data.obj()
  val evtCode = obj.string("evtCode", "code", "eventCode", "type").orEmpty().uppercase()
  val typeSource = listOfNotNull(
    obj.string("tipo", "eventType"),
    obj.string("title", "description", "evtText", "notes"),
    obj["tipo"].obj().string("desc"),
  ).joinToString(" ").lowercase()
  val type = when {
    evtCode == "RTD" -> AbsenceType.LATE
    evtCode in setOf("UXC", "USC") -> AbsenceType.EXIT
    evtCode in setOf("ABA", "AB0") -> AbsenceType.ABSENCE
    typeSource.contains("ritard") || typeSource.contains("late") || typeSource.contains("ingresso post") -> AbsenceType.LATE
    typeSource.contains("uscita") || typeSource.contains("exit") || typeSource.contains("permesso") -> AbsenceType.EXIT
    else -> AbsenceType.ABSENCE
  }
  val justifyUrl = findActionUrl(data, "justify", "giust", "justif")
  val detailUrl = (
    normalizeUrlCandidate(obj.string("detailUrl", "detailLink", "detailHref"))
      ?: findActionUrl(data, "detail", "event", "scheda", "notice")
    )
  return AbsenceRecord(
    id = obj.string("id", "evtId", "absenceId").orEmpty(),
    date = normalizeDate(obj.string("evtDate", "data", "date")),
    type = type,
    hours = obj.int("hoursAbsence", "ore", "hours", "hour", "evtHPos"),
    justified = obj.bool("isJustified", "giustificata", "justified") ?: false,
    canJustify = justifyUrl != null,
    justificationDate = normalizeDateOrNull(obj.string("dataGiustificazione", "justificationDate")),
    justificationReason = sanitizeRegisterText(obj.string("justifReasonDesc", "motivoGiustificazione", "justificationReason")),
    justifyUrl = justifyUrl,
    detailUrl = detailUrl,
  )
}

internal fun normalizeCommunication(data: JsonElement): Communication {
  val obj = data.obj()
  val needsAck = obj.bool("needSign", "needsAck") ?: false
  val needsReply = obj.bool("needReply", "needsReply") ?: false
  val needsJoin = obj.bool("needJoin", "needsJoin") ?: false
  val needsFile = obj.bool("needFile", "needsFile") ?: false
  val noticeboardAttachments = normalizeNoticeboardAttachments(obj["attachments"] ?: obj["allegati"])
  val attachments = (
    noticeboardAttachments.map(::toRemoteAttachment) +
      normalizeAttachments(obj["attachments"] ?: obj["allegati"])
    ).distinctBy { "${it.id}:${it.url.orEmpty()}" }
  val actions = buildNoticeboardActions(
    needsAck = needsAck,
    needsReply = needsReply,
    needsJoin = needsJoin,
    needsFile = needsFile,
    acknowledgeUrl = findActionUrl(data, "sign", "ack", "presa", "visione", "confirm"),
    replyUrl = findActionUrl(data, "reply", "response", "risposta"),
    joinUrl = findActionUrl(data, "join", "ades", "particip"),
    fileUploadUrl = findActionUrl(data, "upload", "file", "attach", "alleg"),
  )
  return Communication(
    id = obj.string("id", "pubId", "commId", "evento_id").orEmpty(),
    pubId = obj.string("pubId", "id", "commId", "evento_id").orEmpty(),
    evtCode = obj.string("evtCode", "code").orEmpty(),
    title = sanitizeRegisterText(obj.string("cntTitle", "title", "titolo", "evtTitle")) ?: "Comunicazione",
    contentPreview = preview(
      sanitizeRegisterText(obj.string("itemText", "texto", "content", "description", "notes", "cntCategory")).orEmpty(),
      "",
    ),
    sender = sanitizeRegisterText(obj.string("authorName", "mittente", "sender")) ?: "Scuola",
    date = normalizeDate(obj.string("evtDate", "data", "date", "pubDT")),
    read = obj.bool("read", "letto", "isRead", "readStatus") ?: false,
    attachments = attachments,
    category = sanitizeRegisterText(obj.string("cntCategory", "category")),
    needsAck = needsAck,
    needsReply = needsReply,
    needsJoin = needsJoin,
    needsFile = needsFile,
    actions = actions,
    noticeboardAttachments = noticeboardAttachments,
    capabilityState = communicationCapability(
      actions = actions,
      noticeboardAttachments = noticeboardAttachments,
      attachments = attachments,
    ),
  )
}

internal fun normalizeCommunicationDetail(root: JsonObject, base: Communication): CommunicationDetail {
  val item = (root["item"] ?: root["event"]).obj().takeIf { it.isNotEmpty() } ?: root
  val reply = root["reply"].obj()
  val portalDetailUrl = findActionUrl(root, "detail", "notice", "view", "href", "link")
  val acknowledgeUrl = findActionUrl(root, "sign", "ack", "presa", "visione", "confirm")
  val replyUrl = findActionUrl(root, "reply", "response", "risposta")
  val joinUrl = findActionUrl(root, "join", "ades", "particip")
  val fileUploadUrl = findActionUrl(root, "upload", "file", "attach", "alleg")
  val actions = buildNoticeboardActions(
    needsAck = base.needsAck,
    needsReply = base.needsReply || replyUrl != null,
    needsJoin = base.needsJoin || joinUrl != null,
    needsFile = base.needsFile || fileUploadUrl != null,
    acknowledgeUrl = acknowledgeUrl,
    replyUrl = replyUrl,
    joinUrl = joinUrl,
    fileUploadUrl = fileUploadUrl,
  )
  val detailAttachments = normalizeNoticeboardAttachments(root["attachments"] ?: root["allegati"])
  val mergedAttachments = (detailAttachments.map(::toRemoteAttachment) + base.attachments)
    .distinctBy { "${it.id}:${it.url.orEmpty()}" }
  val mergedNoticeboardAttachments = detailAttachments.ifEmpty { base.noticeboardAttachments }
  val mergedActions = actions.ifEmpty { base.actions }
  val communication = base.copy(
    contentPreview = preview(
      sanitizeRegisterText(item.string("text", "evtText", "content", "description")) ?: base.contentPreview,
      base.contentPreview,
    ),
    attachments = mergedAttachments,
    actions = mergedActions,
    noticeboardAttachments = mergedNoticeboardAttachments,
    capabilityState = communicationCapability(
      actions = mergedActions,
      noticeboardAttachments = mergedNoticeboardAttachments,
      attachments = mergedAttachments,
    ),
  )
  return CommunicationDetail(
    communication = communication,
    content = sanitizeRegisterText(item.string("text", "evtText", "content", "description"))
      ?: base.contentPreview,
    replyText = sanitizeRegisterText(reply.string("text", "replyText", "description")),
    portalDetailUrl = portalDetailUrl,
    acknowledgeUrl = acknowledgeUrl,
    replyUrl = replyUrl,
    joinUrl = joinUrl,
    fileUploadUrl = fileUploadUrl,
    actions = communication.actions,
  )
}

internal fun normalizeAgendaItem(data: JsonElement): AgendaItem {
  val obj = data.obj()
  val rawTitle = obj.string("title", "evtTitle", "notes", "description", "content")
    ?: obj.string("subjectDesc", "subject")
    ?: obj["materia"].obj().string("desc")
    ?: "Evento"
  val title = sanitizeRegisterText(rawTitle) ?: "Evento"
  val subject = sanitizeRegisterText(obj.string("subjectDesc", "subject", "classDesc"))
    ?: sanitizeRegisterText(obj["materia"].obj().string("desc"))
  val typeSource = listOfNotNull(
    obj.string("type", "eventTypeDesc", "evtCode", "eventCode"),
    obj.string("category", "cntCategory"),
    title,
  ).joinToString(" ").lowercase()
  val category = when {
    typeSource.contains("compit") || typeSource.contains("homework") -> AgendaCategory.HOMEWORK
    typeSource.contains("verific") || typeSource.contains("test") || typeSource.contains("interrog") -> AgendaCategory.ASSESSMENT
    typeSource.contains("lezion") || typeSource.contains("lesson") -> AgendaCategory.LESSON
    else -> AgendaCategory.EVENT
  }
  val detail = sanitizeRegisterText(obj.string("description", "notes", "content"))
    ?.takeUnless { it.equals(title, ignoreCase = true) }
  val date = normalizeDate(obj.string("date", "evtDate", "data", "evtDatetimeBegin"))
  val time = normalizeTime(obj.string("time", "startTime", "lessonHour", "ora", "evtDatetimeBegin"))
  return AgendaItem(
    id = obj.string("id", "evtId").orEmpty(),
    title = title,
    subtitle = subject.orEmpty(),
    date = date,
    time = time,
    detail = detail,
    subject = subject,
    category = category,
    sharePayload = listOf(title, subject, date, time, detail).filterNotNull().joinToString(" - "),
  )
}

internal fun normalizeNote(data: JsonObject, categoryCode: String): Note {
  val content = sanitizeRegisterText(data.string("evtText", "content", "description")).orEmpty()
  val label = when (categoryCode) {
    "NTTE" -> "Nota docente"
    "NTCL" -> "Annotazione"
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
    author = sanitizeRegisterText(data.string("authorName", "author")) ?: "Docente",
    read = data.bool("readStatus", "read") ?: false,
    severity = when (categoryCode) {
      "NTWN", "NTTE" -> "warning"
      else -> "info"
    },
  )
}

internal fun normalizeMaterialFolder(folder: JsonObject, teacherId: String, teacherName: String): List<MaterialItem> {
  val folderId = folder.string("folderId", "id").orEmpty()
  val folderName = sanitizeRegisterText(folder.string("folderName", "title")) ?: "Materiali"
  return extractArray(folder["contents"].obj(), "contents").map { contentElement ->
    val content = contentElement.obj()
    val objectType = content.string("objectType", "type").orEmpty()
    val sourceUrl = normalizeUrlCandidate(content.string("url", "link", "href", "path"))
    val attachments = buildList {
      addAll(normalizeAttachments(content["attachments"] ?: content["allegati"]))
      if (!sourceUrl.isNullOrBlank()) {
        add(
          RemoteAttachment(
            id = content.string("id", "contentId", "itemId") ?: sourceUrl,
            name = sanitizeRegisterText(content.string("title", "name")) ?: "Link",
            url = sourceUrl.takeIf(::isOfficialApiUrl),
            mimeType = content.string("mimeType", "contentType"),
            portalOnly = !isOfficialApiUrl(sourceUrl),
          ),
        )
      }
    }.distinctBy { "${it.id}:${it.url.orEmpty()}" }
    val capability = when {
      !sourceUrl.isNullOrBlank() && !isOfficialApiUrl(sourceUrl) ->
        CapabilityState(
          status = CapabilityStatus.UNAVAILABLE,
          label = "Endpoint non ufficiale",
          detail = "Questa risorsa richiede ancora un URL non REST e viene esclusa dal flusso nativo.",
        )
      attachments.any { !it.portalOnly } ->
        CapabilityState(
          status = CapabilityStatus.AVAILABLE,
          label = "Contenuto disponibile",
          detail = "Il materiale contiene file o link recuperabili.",
        )
      else ->
        CapabilityState(
          status = CapabilityStatus.AVAILABLE,
          label = "Contenuto disponibile",
          detail = "Il materiale puo essere aperto con il dettaglio nativo.",
        )
    }
    MaterialItem(
      id = content.string("id", "contentId", "itemId").orEmpty(),
      teacherId = teacherId,
      teacherName = teacherName,
      folderId = folderId,
      folderName = folderName,
      title = sanitizeRegisterText(content.string("title", "name")).orEmpty(),
      objectId = content.string("objectId", "oid").orEmpty(),
      objectType = objectType,
      sharedAt = normalizeDate(content.string("shareDT", "sharedAt", "lastShareDT")),
      capabilityState = capability,
      attachments = attachments,
    )
  }.sortedByDescending { it.sharedAt }
}

internal fun normalizeMaterialAsset(
  item: MaterialItem,
  mimeType: String?,
  base64Content: String?,
  textPreview: String?,
  sourceUrl: String?,
): MaterialAsset {
  val effectiveSourceUrl = sourceUrl ?: item.attachments.firstOrNull { !it.url.isNullOrBlank() }?.url
  val capability = when {
    !base64Content.isNullOrBlank() || !textPreview.isNullOrBlank() -> CapabilityState(
      status = CapabilityStatus.AVAILABLE,
      label = when {
        mimeType == "text/html" -> "Anteprima web"
        mimeType?.startsWith("text/") == true -> "Anteprima testuale"
        mimeType?.startsWith("image/") == true -> "Anteprima immagine"
        else -> "File pronto"
      },
      detail = "Il contenuto e disponibile per anteprima o apertura locale.",
    )
    !effectiveSourceUrl.isNullOrBlank() && isOfficialApiUrl(effectiveSourceUrl) -> CapabilityState(
      status = CapabilityStatus.EXTERNAL_ONLY,
      label = "Download disponibile",
      detail = "Il materiale e disponibile tramite endpoint REST ufficiale.",
    )
    !effectiveSourceUrl.isNullOrBlank() -> CapabilityState(
      status = CapabilityStatus.UNAVAILABLE,
      label = "Endpoint non ufficiale",
      detail = "La preview via endpoint non ufficiale non viene usata nel flusso nativo.",
    )
    else -> CapabilityState(
      status = CapabilityStatus.UNAVAILABLE,
      label = "Preview non disponibile",
      detail = "L'API ufficiale non ha restituito un contenuto apribile.",
    )
  }
  return MaterialAsset(
    id = item.id,
    title = item.title,
    objectType = item.objectType,
    fileName = item.title,
    mimeType = mimeType,
    base64Content = base64Content,
    textPreview = textPreview,
    sourceUrl = effectiveSourceUrl,
    capabilityState = capability,
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
  val viewUrl = normalizeUrlCandidate(obj.string("viewLink", "viewUrl", "url")).takeIf(::isOfficialApiUrl)
  val confirmUrl = normalizeUrlCandidate(obj.string("confirmLink", "confirmUrl", "confirmHref")).takeIf(::isOfficialApiUrl)
  return DocumentItem(
    id = obj.string("id", "viewLink", "confirmLink", "desc").orEmpty().ifBlank {
      obj.string("desc", "title").orEmpty()
    },
    title = sanitizeRegisterText(obj.string("desc", "title")) ?: "Documento",
    detail = when {
      viewUrl != null -> "Documento disponibile tramite endpoint REST ufficiale."
      confirmUrl != null -> "Documento verificabile tramite endpoint REST ufficiale."
      else -> "Nessun link REST diretto esposto nella lista documenti."
    },
    viewUrl = viewUrl,
    confirmUrl = confirmUrl,
    capabilityState = when {
      viewUrl != null || confirmUrl != null -> CapabilityState(
        CapabilityStatus.AVAILABLE,
        "Apri documento",
        "Apertura e download gestiti dal client nativo.",
      )
      else -> CapabilityState(
        CapabilityStatus.UNAVAILABLE,
        "Documento non disponibile",
        "Il link diretto non e disponibile.",
      )
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
      title = sanitizeRegisterText(book.string("title")) ?: "Libro",
      subtitle = sanitizeRegisterText(book.string("subheading")),
      volume = sanitizeRegisterText(book.string("volume")),
      author = sanitizeRegisterText(book.string("author")),
      publisher = sanitizeRegisterText(book.string("publisher")),
      subject = sanitizeRegisterText(book.string("subjectDesc", "subject")) ?: "Materia",
      price = book.double("price"),
      coverUrl = normalizeUrlCandidate(book.string("coverUrl")),
      toBuy = book.bool("toBuy") ?: false,
      alreadyOwned = book.bool("alreadyOwned") ?: false,
      alreadyInUse = book.bool("alreadyInUse") ?: false,
      recommended = book.bool("recommended") ?: false,
      recommendedFor = sanitizeRegisterText(book.string("recommendedFor")),
      newAdoption = book.bool("newAdoption") ?: false,
    )
  }
  return SchoolbookCourse(
    id = obj.string("courseId", "id").orEmpty(),
    title = sanitizeRegisterText(obj.string("courseDesc", "description")) ?: "Corso",
    books = books,
  )
}

internal fun normalizePeriod(data: JsonElement): Period {
  val obj = data.obj()
  return Period(
    code = obj.string("periodCode", "code").orEmpty(),
    order = obj.int("periodPos", "order") ?: 0,
    description = sanitizeRegisterText(obj.string("periodDesc", "description")) ?: "Periodo",
    label = sanitizeRegisterText(obj.string("periodLabel", "label", "periodDesc")) ?: "Periodo",
    isFinal = obj.bool("isFinal") ?: false,
    startDate = normalizeDate(obj.string("dateStart", "startDate")),
    endDate = normalizeDate(obj.string("dateEnd", "endDate")),
  )
}

internal fun normalizeSubject(data: JsonElement): Subject {
  val obj = data.obj()
  val teachers = extractArray(obj["teachers"].obj(), "teachers").mapNotNull { teacher ->
    sanitizeRegisterText(teacher.obj().string("teacherName", "name"))
  }
  return Subject(
    id = obj.string("id", "subjectId").orEmpty(),
    description = sanitizeRegisterText(obj.string("description", "subjectDesc")) ?: "Materia",
    order = obj.int("order", "ord") ?: 0,
    teachers = teachers,
  )
}

internal fun normalizeAttachments(data: JsonElement?): List<RemoteAttachment> {
  val items = when (data) {
    is JsonArray -> data
    is JsonObject -> extractArray(data, "attachments", "allegati", "files", "items")
    else -> emptyList()
  }
  return items.mapNotNull { item ->
    when (item) {
      is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }?.let {
        RemoteAttachment(id = it, name = it, portalOnly = true)
      }
      is JsonObject -> {
        val name = sanitizeRegisterText(item.string("name", "fileName", "attachName", "title", "desc"))
          ?: return@mapNotNull null
        val url = normalizeUrlCandidate(item.string("url", "link", "href", "path"))
          ?: findActionUrl(item, "download", "attach", "alleg", "file")
        RemoteAttachment(
          id = item.string("id", "attachId", "uuid") ?: name,
          name = name,
          url = url?.takeIf(::isOfficialApiUrl),
          mimeType = item.string("mimeType", "contentType"),
          portalOnly = item.bool("portalOnly") ?: !isOfficialApiUrl(url),
        )
      }
      else -> null
    }
  }
}

internal fun JsonObject.string(vararg keys: String): String? {
  keys.forEach { key ->
    val primitive = this[key] as? JsonPrimitive ?: return@forEach
    val candidate = primitive.contentOrNull?.trim()
    if (!candidate.isNullOrBlank()) return candidate
  }
  return null
}

internal fun JsonObject.double(vararg keys: String): Double? {
  keys.forEach { key ->
    val primitive = this[key] as? JsonPrimitive ?: return@forEach
    primitive.doubleOrNull?.let { return it }
    primitive.contentOrNull?.parseDecimal()?.let { return it }
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

private fun normalizeNoticeboardAttachments(data: JsonElement?): List<NoticeboardAttachment> {
  val items = when (data) {
    is JsonArray -> data
    is JsonObject -> extractArray(data, "attachments", "allegati", "files", "items")
    else -> emptyList()
  }
  return items.mapNotNull { element ->
    when (element) {
      is JsonPrimitive -> {
        val name = sanitizeRegisterText(element.contentOrNull) ?: return@mapNotNull null
        NoticeboardAttachment(
          id = name,
          name = name,
          portalOnly = true,
        )
      }
      is JsonObject -> {
        val name = sanitizeRegisterText(element.string("name", "fileName", "attachName", "title", "desc"))
          ?: return@mapNotNull null
        val url = normalizeUrlCandidate(element.string("url", "link", "href", "path"))
          ?: findActionUrl(element, "download", "attach", "alleg", "file")
        val action = url?.takeIf(::isOfficialApiUrl)?.let {
          NoticeboardAction(
            type = NoticeboardActionType.DOWNLOAD,
            label = "Scarica allegato",
            url = it,
          )
        }
        NoticeboardAttachment(
          id = element.string("id", "attachId", "uuid") ?: name,
          name = name,
          url = url?.takeIf(::isOfficialApiUrl),
          mimeType = element.string("mimeType", "contentType"),
          portalOnly = element.bool("portalOnly") ?: !isOfficialApiUrl(url),
          action = action,
        )
      }
      else -> null
    }
  }
}

private fun toRemoteAttachment(attachment: NoticeboardAttachment): RemoteAttachment {
  return RemoteAttachment(
    id = attachment.id,
    name = attachment.name,
    url = attachment.url,
    mimeType = attachment.mimeType,
    portalOnly = attachment.portalOnly,
  )
}

private fun buildNoticeboardActions(
  needsAck: Boolean = false,
  needsReply: Boolean = false,
  needsJoin: Boolean = false,
  needsFile: Boolean = false,
  acknowledgeUrl: String? = null,
  replyUrl: String? = null,
  joinUrl: String? = null,
  fileUploadUrl: String? = null,
): List<NoticeboardAction> {
  return buildList {
    if (needsAck || acknowledgeUrl != null) {
      add(
        NoticeboardAction(
          type = NoticeboardActionType.ACKNOWLEDGE,
          label = "Conferma",
          url = acknowledgeUrl,
        ),
      )
    }
    if (needsReply || !replyUrl.isNullOrBlank()) {
      add(NoticeboardAction(type = NoticeboardActionType.REPLY, label = "Rispondi", url = replyUrl))
    }
    if (needsJoin || !joinUrl.isNullOrBlank()) {
      add(NoticeboardAction(type = NoticeboardActionType.JOIN, label = "Aderisci", url = joinUrl))
    }
    if (needsFile || !fileUploadUrl.isNullOrBlank()) {
      add(NoticeboardAction(type = NoticeboardActionType.UPLOAD, label = "Carica file", url = fileUploadUrl))
    }
  }
}

private fun communicationCapability(
  actions: List<NoticeboardAction>,
  noticeboardAttachments: List<NoticeboardAttachment>,
  attachments: List<RemoteAttachment>,
): CapabilityState {
  return when {
    actions.any { requiresGatewayUrl(it.url) } -> CapabilityState(
      CapabilityStatus.AVAILABLE,
      "Gateway richiesto",
      "Le azioni avanzate della comunicazione passano dal gateway controllato.",
    )
    noticeboardAttachments.any { it.portalOnly } || attachments.any { it.portalOnly } -> CapabilityState(
      CapabilityStatus.UNAVAILABLE,
      "Endpoint non ufficiale",
      "Alcuni allegati richiedono ancora URL non REST e vengono esclusi dal flusso nativo.",
    )
    actions.isNotEmpty() -> CapabilityState(
      CapabilityStatus.AVAILABLE,
      "Azioni disponibili",
      "La comunicazione espone azioni gestibili dal client nativo.",
    )
    noticeboardAttachments.isNotEmpty() || attachments.isNotEmpty() -> CapabilityState(
      CapabilityStatus.AVAILABLE,
      "Allegati disponibili",
      "Il dettaglio e gli allegati ufficiali sono disponibili in app.",
    )
    else -> CapabilityState(
      CapabilityStatus.AVAILABLE,
      "Dettaglio disponibile",
      "Contenuto testuale leggibile in app.",
    )
  }
}

private fun requiresGatewayUrl(value: String?): Boolean {
  return !value.isNullOrBlank() && !isOfficialApiUrl(value)
}

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

private fun isOfficialApiUrl(value: String?): Boolean {
  return value?.contains("/rest/", ignoreCase = true) == true
}

internal fun normalizeStudentId(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val match = Regex("^[SG](\\d+)(?:[A-Z]+)?$", RegexOption.IGNORE_CASE).find(raw)
  return match?.groupValues?.get(1) ?: raw.filter(Char::isDigit).ifBlank { raw }
}

internal fun normalizeDate(value: String?): String {
  val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return LocalDate.now().format(IsoDateFormatter)
  return when {
    Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw) -> raw
    Regex("^\\d{8}$").matches(raw) -> LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).format(IsoDateFormatter)
    Regex("^\\d{4}-\\d{2}-\\d{2}T").containsMatchIn(raw) -> raw.take(10)
    else -> runCatching { OffsetDateTime.parse(raw).toLocalDate().format(IsoDateFormatter) }.getOrElse { raw }
  }
}

private fun normalizeDateOrNull(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
  return normalizeDate(raw)
}

internal fun normalizeTime(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return when {
    Regex("^\\d{2}:\\d{2}$").matches(raw) -> raw
    Regex("^\\d{1,2}:\\d{2}$").matches(raw) -> raw.padStart(5, '0')
    Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}").containsMatchIn(raw) -> raw.substring(11, 16)
    else -> raw
  }
}

internal fun toApiDateParam(value: String): String = value.replace("-", "")

internal fun preview(value: String, fallback: String): String {
  val source = value.ifBlank { fallback }
  return if (source.length > 150) "${source.take(147)}..." else source
}

internal fun sanitizeRegisterText(value: String?): String? {
  val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
  val withoutHtml = Jsoup.parse(raw).text()
    .replace('\u00A0', ' ')
    .replace(Regex("\\s+"), " ")
    .trim()
  val withoutIsoPrefix = withoutHtml
    .replace(
      Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?\\s*"),
      "",
    )
    .replace(Regex("^\\d{4}-\\d{2}-\\d{2}\\s+"), "")
    .trimStart('-', ':', '|', ' ')
    .trim()
  return withoutIsoPrefix.takeIf { it.isNotBlank() }
}

private fun trimZero(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun schoolYearNow(): String {
  val now = LocalDate.now()
  val startYear = if (now.monthValue >= 8) now.year else now.year - 1
  return "$startYear-${startYear + 1}"
}
