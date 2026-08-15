package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.JsonElement as GsonJsonElement
import com.google.gson.JsonObject as GsonJsonObject
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardAction
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private val NoticeboardAttachmentIndexedUrl = Regex("""(.*/noticeboard/attach/[^/]+/[^/?#]+)/(\d+)([?#].*)?$""")

class ClassevivaNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class LoginResult(
  val token: String,
  val studentId: String,
  val profileHint: StudentProfile,
)

@Singleton
class ClassevivaRestClient @Inject constructor(
  private val json: Json,
  private val apiService: ClassevivaApiService,
  @param:Named("authService") private val authService: ClassevivaAuthService,
  private val apiSessionManager: ApiSessionManager,
) {
  private var activeSession: UserSession? = null

  fun currentToken(): String? = activeSession?.token ?: apiSessionManager.currentSession()?.token

  fun setSession(session: UserSession?) {
    activeSession = session
  }

  suspend fun restoreValidSession(): UserSession? = withContext(Dispatchers.IO) {
    apiSessionManager.restoreValidSession()?.also { activeSession = it }
  }

  suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
    val response = runCatching {
      authService.login(LoginRequestDto(uid = username, password = password)).execute()
    }.getOrElse { throwable ->
      throw ClassevivaNetworkException("Login Classeviva non riuscito.", throwable)
    }

    if (!response.isSuccessful) {
      throw httpError(response.code(), response.errorBody()?.string().orEmpty())
    }

    val body = response.body() ?: throw ClassevivaNetworkException("Classeviva non ha restituito un payload di login valido.")
    val token = body.token?.takeIf(String::isNotBlank)
      ?: throw ClassevivaNetworkException("Classeviva non ha restituito un token valido.")
    val studentId = normalizeStudentId(username)
      ?: normalizeStudentId(body.ident)
      ?: normalizeStudentId(body.userId)
      ?: normalizeStudentId(body.alternateUserId)
      ?: throw ClassevivaNetworkException("Studente non identificato dopo il login.")

    LoginResult(
      token = token,
      studentId = studentId,
      profileHint = StudentProfile(
        id = studentId,
        name = body.firstName.orEmpty(),
        surname = body.lastName.orEmpty(),
      ),
    )
  }

  suspend fun getProfile(): StudentProfile = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      normalizeProfile(apiService.getStudentCard(session.studentId).toPayload(), session.studentId)
    }
  }

  suspend fun getGrades(): List<Grade> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getGrades(session.studentId).toPayload(), "grades", "events", "items")
        .map(::normalizeGrade)
    }
  }

  suspend fun getLessons(startDate: String, endDate: String): List<Lesson> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(
        apiService.getLessonsInRange(
          studentId = session.studentId,
          start = toApiDateParam(startDate),
          end = toApiDateParam(endDate),
        ).toPayload(),
        "lessons",
        "agenda",
        "items",
      ).map(::normalizeLesson)
    }
  }

  suspend fun getHomeworks(): List<Homework> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getHomeworks(session.studentId).toPayload(), "homeworks", "items", "agenda")
        .map(::normalizeHomework)
    }
  }

  suspend fun getAbsences(): List<dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getAbsences(session.studentId).toPayload(), "events", "absences", "items")
        .map(::normalizeAbsence)
    }
  }

  suspend fun getAbsences(startDate: String, endDate: String): List<dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(
        apiService.getAbsencesInRange(
          studentId = session.studentId,
          begin = toApiDateParam(startDate),
          end = toApiDateParam(endDate),
        ).toPayload(),
        "events",
        "absences",
        "items",
      ).map(::normalizeAbsence)
    }
  }

  suspend fun getCommunications(): List<Communication> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getNoticeboard(session.studentId).toPayload(), "items", "noticeboard", "communications")
        .map(::normalizeCommunication)
        .map { communication -> communication.withOfficialAttachmentUrls(session.studentId) }
    }
  }

  suspend fun getCommunicationDetail(base: Communication): CommunicationDetail = withContext(Dispatchers.IO) {
    val session = requireSession()
    runCatching {
      readCommunicationDetail(session, base)
    }.getOrElse { firstError ->
      val refreshed = getCommunications().firstOrNull { it.pubId == base.pubId && it.evtCode == base.evtCode }
      if (refreshed == null || refreshed.id == base.id) throw firstError
      readCommunicationDetail(session, refreshed)
    }
  }

  suspend fun getCommunicationDetail(pubId: String, evtCode: String): CommunicationDetail = withContext(Dispatchers.IO) {
    val base = getCommunications().firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
      ?: throw ClassevivaNetworkException("Comunicazione non trovata.")
    getCommunicationDetail(base)
  }

  /**
   * Segna come letta una comunicazione di bacheca. Lancia eccezione quando
   * ClasseViva rifiuta la chiamata, tollerando solo risposte esplicite da
   * operazione gia' completata.
   */
  suspend fun markNoticeboardRead(pubId: String, evtCode: String): Unit = withContext(Dispatchers.IO) {
    val session = requireSession()
    val base = getCommunications().firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
      ?: fallbackCommunication(pubId = pubId, evtCode = evtCode)
    try {
      apiCall {
        apiService.readNoticeboard(
          studentId = session.studentId,
          evtCode = evtCode,
          pubId = pubId,
          body = noticeboardDetailPayload(base),
        )
        Unit
      }
    } catch (e: ClassevivaNetworkException) {
      val msg = e.message.orEmpty()
      // Solo gli errori espliciti "gia' letta" sono idempotenti; un payload
      // invalido deve restare visibile per non sporcare lo stato locale.
      val isAlreadyRead = msg.contains("already", ignoreCase = true) ||
        msg.contains("gia", ignoreCase = true) || msg.contains("read", ignoreCase = true)
      if (!isAlreadyRead) throw e
    }
  }

  /**
   * Conferma una comunicazione di bacheca ("Conferma lettura") richiamando direttamente
   * l'endpoint di lettura, che su Classeviva registra la ricezione ed è pienamente supportato.
   */
  suspend fun confirmNoticeboard(communication: Communication): Unit = withContext(Dispatchers.IO) {
    markNoticeboardRead(pubId = communication.pubId, evtCode = communication.evtCode)
  }

  suspend fun joinNoticeboard(pubId: String, evtCode: String): Unit = withContext(Dispatchers.IO) {
    val session = requireSession()
    val base = getCommunications().firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
      ?: fallbackCommunication(pubId = pubId, evtCode = evtCode)
    apiCall {
      apiService.joinNoticeboard(
        studentId = session.studentId,
        evtCode = evtCode,
        pubId = pubId,
        body = noticeboardDetailPayload(base),
      )
      Unit
    }
  }

  suspend fun downloadAttachmentBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
    apiCall { apiService.downloadByUrl(requireOfficialDownloadUrl(url)) }.use { it.bytes() }
  }

  suspend fun getAgenda(startDate: String, endDate: String): List<dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(
        apiService.getAgenda(
          studentId = session.studentId,
          begin = toApiDateParam(startDate),
          end = toApiDateParam(endDate),
        ).toPayload(),
        "agenda",
        "events",
        "items",
      ).map(::normalizeAgendaItem)
    }
  }

  suspend fun getNotes(): List<Note> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      val root = apiService.getNotes(session.studentId).toPayload()
      root.entries
        .flatMap { (categoryCode, items) ->
          items.array().map { normalizeNote(it.obj(), categoryCode) }
        }
        .sortedByDescending { it.date }
    }
  }

  suspend fun getNoteDetail(id: String, categoryCode: String): NoteDetail = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      val base = getNotes().firstOrNull { it.id == id && it.categoryCode == categoryCode }
        ?: throw ClassevivaNetworkException("Nota non trovata.")
      val root = apiService.readNote(
        studentId = session.studentId,
        type = categoryCode,
        noteId = id,
      ).toPayload()
      val event = root["event"].obj().takeIf { it.isNotEmpty() } ?: root
      val content = event.string("evtText", "text", "content") ?: base.contentPreview
      NoteDetail(note = base.copy(contentPreview = preview(content, base.contentPreview)), content = content)
    }
  }

  suspend fun getMaterials(): List<MaterialItem> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      normalizeMaterialsPayload(apiService.getDidactics(session.studentId).toPayloadElement())
    }
  }

  suspend fun openMaterialStream(item: MaterialItem): NetworkDocumentStream = withContext(Dispatchers.IO) {
    if (item.objectType.equals("link", ignoreCase = true)) {
      throw ClassevivaNetworkException("Il link didattico deve essere aperto come URL esterno.")
    }
    val session = requireSession()
    val responseBody = runCatching {
      apiCall { apiService.getDidacticsItem(session.studentId, item.id) }
    }.getOrElse {
      val fallbackUrl = item.attachments.firstNotNullOfOrNull { attachment -> attachment.url }
      if (fallbackUrl.isNullOrBlank()) {
        throw it
      }
      apiCall { apiService.downloadByUrl(requireOfficialDownloadUrl(fallbackUrl)) }
    }
    NetworkDocumentStream(
      body = responseBody,
      fileName = item.attachments.firstOrNull()?.name ?: item.title,
      mimeType = responseBody.contentType()?.toString()?.substringBefore(";"),
    )
  }

  fun resolveMaterialExternalUrl(item: MaterialItem): String? {
    if (!item.objectType.equals("link", ignoreCase = true)) return null
    return item.attachments.firstNotNullOfOrNull { attachment ->
      attachment.url?.takeIf(::isSafeExternalMaterialUrl)
    }
  }

  suspend fun getDocuments(): List<DocumentItem> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      normalizeDocumentsPayload(fetchDocumentsPayload(session.studentId).toPayloadElement())
        .map { document ->
          val remoteHash = document.remoteHash?.takeIf(String::isNotBlank)
          document.copy(
            restReadUrl = document.restReadUrl
              ?: remoteHash?.let { buildDocumentReadUrl(session.studentId, it) },
            viewUrl = document.viewUrl
              ?: remoteHash?.let { buildDocumentReadUrl(session.studentId, it) },
            confirmUrl = remoteHash?.let { buildDocumentCheckUrl(session.studentId, it) }
              ?: document.confirmUrl,
          )
        }
    }
  }

  suspend fun openDocumentStream(document: DocumentItem): NetworkDocumentStream = withContext(Dispatchers.IO) {
    val session = requireSession()
    val responseBody = when {
      !document.remoteHash.isNullOrBlank() -> apiCall {
        apiService.readDocument(session.studentId, document.remoteHash!!)
      }
      !document.restReadUrl.isNullOrBlank() -> apiCall {
        apiService.downloadByUrl(requireOfficialDownloadUrl(document.restReadUrl!!))
      }
      !document.viewUrl.isNullOrBlank() -> apiCall {
        apiService.downloadByUrl(requireOfficialDownloadUrl(document.viewUrl!!))
      }
      else -> throw ClassevivaNetworkException("Documento non leggibile tramite API ufficiali.")
    }
    NetworkDocumentStream(
      body = responseBody,
      fileName = document.title.takeIf(String::isNotBlank),
      mimeType = responseBody.contentType()?.toString()?.substringBefore(";"),
    )
  }

  suspend fun checkDocument(document: DocumentItem): Unit = withContext(Dispatchers.IO) {
    val session = requireSession()
    val remoteHash = document.remoteHash?.takeIf(String::isNotBlank)
      ?: throw ClassevivaNetworkException("Documento non verificabile senza hash remoto.")
    apiCall {
      apiService.checkDocument(session.studentId, remoteHash)
      Unit
    }
  }

  suspend fun getSchoolbooks(): List<SchoolbookCourse> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      normalizeSchoolbooksPayload(apiService.getSchoolbooks(session.studentId).toPayloadElement())
    }
  }

  suspend fun getPeriods(): List<Period> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getPeriods(session.studentId).toPayload(), "periods")
        .map(::normalizePeriod)
    }
  }

  suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getSubjects(session.studentId).toPayload(), "subjects")
        .map(::normalizeSubject)
    }
  }

  private suspend fun requireSession(): UserSession {
    val session = activeSession ?: apiSessionManager.restoreValidSession()
    if (session == null) {
      throw ClassevivaNetworkException("Sessione assente. Effettua di nuovo il login.")
    }
    activeSession = session
    return session
  }

  private suspend fun <T> apiCall(block: suspend () -> T): T {
    return try {
      block()
    } catch (exception: HttpException) {
      val payload = runCatching { exception.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
      throw httpError(exception.code(), payload, exception)
    } catch (exception: IOException) {
      throw ClassevivaNetworkException("Errore di rete durante la chiamata a Classeviva.", exception)
    }
  }

  private fun httpError(
    code: Int,
    payload: String,
    cause: Throwable? = null,
  ): ClassevivaNetworkException {
    val message = when (code) {
      401 -> "Sessione scaduta o credenziali non più valide."
      404 -> "Risorsa Classeviva non trovata."
      500 -> "Classeviva ha restituito un errore server."
      else -> "Classeviva ha risposto con $code${payload.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""}"
    }
    return ClassevivaNetworkException(message, cause)
  }

  private fun GsonJsonObject.toPayload(): kotlinx.serialization.json.JsonObject {
    return json.parseToJsonElement(toString()).obj()
  }

  private fun GsonJsonElement.toPayloadElement(): kotlinx.serialization.json.JsonElement {
    return json.parseToJsonElement(toString())
  }

  private suspend fun fetchDocumentsPayload(studentId: String): GsonJsonElement {
    return try {
      apiService.getDocumentsPost(studentId)
    } catch (exception: HttpException) {
      val errorPayload = runCatching {
        exception.response()?.errorBody()?.string().orEmpty()
      }.getOrDefault("")
      if (exception.code() == 400 && errorPayload.isInvalidPayloadError()) {
        apiService.getDocumentsGet(studentId)
      } else {
        throw exception
      }
    }
  }

  private fun buildDocumentReadUrl(studentId: String, documentId: String): String? {
    return documentId.takeIf(String::isNotBlank)?.let {
      "${ClassevivaRestBaseUrl}v1/students/$studentId/documents/read/$it"
    }
  }

  private fun buildDocumentCheckUrl(studentId: String, documentId: String): String? {
    return documentId.takeIf(String::isNotBlank)?.let {
      "${ClassevivaRestBaseUrl}v1/students/$studentId/documents/check/$it"
    }
  }

  private suspend fun readCommunicationDetail(
    session: UserSession,
    communication: Communication,
  ): CommunicationDetail {
    return apiCall {
      val root = apiService.readNoticeboard(
        studentId = session.studentId,
        evtCode = communication.evtCode,
        pubId = communication.pubId,
        body = noticeboardDetailPayload(communication),
      ).toPayload()
      normalizeCommunicationDetail(
        root,
        communication,
      ).withOfficialAttachmentUrls(session.studentId)
    }
  }

  private fun noticeboardDetailPayload(communication: Communication): GsonJsonObject {
    return GsonJsonObject().apply {
      addNumberOrString("pubId", communication.pubId)
      addNumberOrString("cntId", communication.id)
      addProperty("evtCode", communication.evtCode)
    }
  }

  private fun fallbackCommunication(pubId: String, evtCode: String): Communication {
    return Communication(
      id = pubId,
      pubId = pubId,
      evtCode = evtCode,
      title = "",
      contentPreview = "",
      sender = "",
      date = "",
      read = false,
    )
  }

  private fun GsonJsonObject.addNumberOrString(name: String, value: String) {
    val trimmed = value.trim()
    val numeric = trimmed.toLongOrNull()
    if (numeric != null) {
      addProperty(name, numeric)
    } else {
      addProperty(name, trimmed)
    }
  }
}

private fun Communication.withOfficialAttachmentUrls(studentId: String): Communication {
  if (attachments.isEmpty() && noticeboardAttachments.isEmpty()) return this

  val updatedAttachments = attachments.mapIndexed { index, attachment ->
    val officialUrl = buildNoticeboardAttachmentUrl(studentId, evtCode, pubId, index + 1)
    when {
      attachment.url.isNullOrBlank() -> attachment.copy(url = officialUrl, portalOnly = false)
      attachment.url.isLegacyNoticeboardAttachmentUrl() -> attachment.copy(
        url = normalizeNoticeboardAttachmentUrl(attachment.url.orEmpty()),
        portalOnly = false,
      )
      else -> attachment
    }
  }
  val updatedNoticeboardAttachments = noticeboardAttachments.mapIndexed { index, attachment ->
    val officialUrl = buildNoticeboardAttachmentUrl(studentId, evtCode, pubId, index + 1)
    when {
      attachment.url.isNullOrBlank() -> {
        attachment.copy(
          url = officialUrl,
          portalOnly = false,
          action = attachment.action?.copy(url = officialUrl) ?: NoticeboardAction(
            type = NoticeboardActionType.DOWNLOAD,
            label = "Scarica allegato",
            url = officialUrl,
          ),
        )
      }
      attachment.url.isLegacyNoticeboardAttachmentUrl() -> {
        val normalizedUrl = normalizeNoticeboardAttachmentUrl(attachment.url.orEmpty())
        attachment.copy(
          url = normalizedUrl,
          portalOnly = false,
          action = attachment.action?.copy(url = normalizedUrl) ?: NoticeboardAction(
            type = NoticeboardActionType.DOWNLOAD,
            label = "Scarica allegato",
            url = normalizedUrl,
          ),
        )
      }
      else -> attachment
    }
  }
  return copy(
    attachments = updatedAttachments,
    noticeboardAttachments = updatedNoticeboardAttachments,
    capabilityState = when {
      actions.isNotEmpty() -> CapabilityState(
        status = CapabilityStatus.AVAILABLE,
        label = "Azioni disponibili",
        detail = "La comunicazione espone azioni gestibili dal client nativo.",
      )
      updatedNoticeboardAttachments.isNotEmpty() || updatedAttachments.isNotEmpty() -> CapabilityState(
        status = CapabilityStatus.AVAILABLE,
        label = "Allegati disponibili",
        detail = "Il dettaglio e gli allegati ufficiali sono disponibili in app.",
      )
      else -> capabilityState
    }
  )
}

private fun CommunicationDetail.withOfficialAttachmentUrls(studentId: String): CommunicationDetail {
  val updatedCommunication = communication.withOfficialAttachmentUrls(studentId)
  return copy(communication = updatedCommunication)
}

private fun Communication.requiresAdvancedNoticeboardAnswer(): Boolean {
  return needsAck || needsReply || needsJoin || needsFile
}

fun buildNoticeboardAttachmentUrl(
  studentId: String,
  evtCode: String,
  pubId: String,
  oneBasedAttachmentIndex: Int,
): String {
  val safeIndex = oneBasedAttachmentIndex.coerceAtLeast(1)
  return "${ClassevivaRestBaseUrl}v1/students/$studentId/noticeboard/attach/$evtCode/$pubId/$safeIndex"
}

private fun requireOfficialDownloadUrl(url: String): String {
  if (!isOfficialRestUrl(url)) {
    throw ClassevivaNetworkException("URL di download esterno all'API REST ufficiale.")
  }
  return url
}

fun normalizeNoticeboardAttachmentUrl(url: String): String {
  val match = NoticeboardAttachmentIndexedUrl.matchEntire(url) ?: return url
  return if (match.groupValues[2] == "101") {
    "${match.groupValues[1]}/1${match.groupValues[3]}"
  } else {
    url
  }
}

fun noticeboardAttachmentDownloadCandidates(url: String): List<String> {
  val normalized = normalizeNoticeboardAttachmentUrl(url)
  val zeroBased = NoticeboardAttachmentIndexedUrl.matchEntire(url)
    ?.takeIf { it.groupValues[2] == "101" }
    ?.let { "${it.groupValues[1]}/0${it.groupValues[3]}" }
  return listOfNotNull(normalized, url, zeroBased).distinct()
}

private fun String?.isLegacyNoticeboardAttachmentUrl(): Boolean {
  if (this.isNullOrBlank()) return false
  val match = NoticeboardAttachmentIndexedUrl.matchEntire(this) ?: return false
  return match.groupValues[2] == "101"
}

private fun String.isInvalidPayloadError(): Boolean {
  val normalized = lowercase().replace('_', ' ').replace('-', ' ')
  return normalized.contains("invalid") && normalized.contains("payload")
}
