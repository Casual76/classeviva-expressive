package dev.antigravity.classevivaexpressive.core.network.client

import android.util.Base64
import com.google.gson.JsonObject as GsonJsonObject
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
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

private const val RestBaseUrl = "https://web.spaggiari.eu/rest/"

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
  @Named("authService") private val authService: ClassevivaAuthService,
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
    apiCall {
      normalizeCommunicationDetail(
        apiService.readNoticeboard(
          studentId = session.studentId,
          evtCode = base.evtCode,
          pubId = base.pubId,
        ).toPayload(),
        base,
      ).withOfficialAttachmentUrls(session.studentId)
    }
  }

  suspend fun getCommunicationDetail(pubId: String, evtCode: String): CommunicationDetail = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      val base = getCommunications().firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
        ?: throw ClassevivaNetworkException("Comunicazione non trovata.")
      normalizeCommunicationDetail(
        apiService.readNoticeboard(
          studentId = session.studentId,
          evtCode = evtCode,
          pubId = pubId,
        ).toPayload(),
        base,
      ).withOfficialAttachmentUrls(session.studentId)
    }
  }

  suspend fun markNoticeboardRead(pubId: String, evtCode: String): Unit = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      apiService.readNoticeboard(
        studentId = session.studentId,
        evtCode = evtCode,
        pubId = pubId,
      )
      Unit
    }
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
      val teachers = extractArray(apiService.getDidactics(session.studentId).toPayload(), "didacticts")
      teachers.flatMap { teacherElement ->
        val teacher = teacherElement.obj()
        val teacherId = teacher.string("teacherId").orEmpty()
        val teacherName = teacher.string("teacherName", "teacherLastName") ?: "Docente"
        extractArray(teacher["folders"].obj(), "folders").flatMap { folderElement ->
          normalizeMaterialFolder(folderElement.obj(), teacherId, teacherName)
        }
      }.sortedByDescending { it.sharedAt }
    }
  }

  suspend fun getMaterialAsset(item: MaterialItem): MaterialAsset = withContext(Dispatchers.IO) {
    val session = requireSession()
    runCatching {
      apiCall { apiService.getDidacticsItem(session.studentId, item.id) }
    }.getOrElse {
      val fallbackUrl = item.attachments.firstNotNullOfOrNull { attachment -> attachment.url }
      if (fallbackUrl.isNullOrBlank()) {
        throw it
      }
      apiCall { apiService.downloadByUrl(fallbackUrl) }
    }.use { responseBody ->
      val bytes = responseBody.bytes()
      val mimeType = responseBody.contentType()?.toString()?.substringBefore(";")
      val base64Content = if (bytes.isNotEmpty()) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
      val textPreview = mimeType?.takeIf { it.startsWith("text/") }?.let {
        bytes.decodeToString().trim().takeIf(String::isNotBlank)?.take(512)
      }
      normalizeMaterialAsset(
        item = item,
        mimeType = mimeType,
        base64Content = base64Content,
        textPreview = textPreview,
        sourceUrl = item.attachments.firstNotNullOfOrNull { attachment -> attachment.url },
      )
    }
  }

  suspend fun getDocuments(): List<DocumentItem> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(
        apiService.getDocuments(session.studentId).toPayload(),
        "schoolReports",
        "documents",
      ).map(::normalizeDocument)
        .map { document ->
          document.copy(
            viewUrl = document.viewUrl ?: buildDocumentReadUrl(session.studentId, document.id),
            confirmUrl = document.confirmUrl ?: buildDocumentCheckUrl(session.studentId, document.id),
          )
        }
    }
  }

  suspend fun readDocument(document: DocumentItem): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
    val session = requireSession()
    val responseBody = when {
      document.id.isNotBlank() -> apiCall { apiService.readDocument(session.studentId, document.id) }
      !document.viewUrl.isNullOrBlank() -> apiCall { apiService.downloadByUrl(document.viewUrl!!) }
      else -> throw ClassevivaNetworkException("Documento non leggibile tramite API ufficiali.")
    }

    responseBody.use { body ->
      body.bytes() to body.contentType()?.toString()?.substringBefore(";")
    }
  }

  suspend fun getSchoolbooks(): List<SchoolbookCourse> = withContext(Dispatchers.IO) {
    val session = requireSession()
    apiCall {
      extractArray(apiService.getSchoolbooks(session.studentId).toPayload(), "schoolbooks")
        .map(::normalizeSchoolbookCourse)
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
      401 -> "Sessione scaduta o credenziali non piu valide."
      404 -> "Risorsa Classeviva non trovata."
      500 -> "Classeviva ha restituito un errore server."
      else -> "Classeviva ha risposto con $code${payload.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""}"
    }
    return ClassevivaNetworkException(message, cause)
  }

  private fun GsonJsonObject.toPayload(): kotlinx.serialization.json.JsonObject {
    return json.parseToJsonElement(toString()).obj()
  }

  private fun buildDocumentReadUrl(studentId: String, documentId: String): String? {
    return documentId.takeIf(String::isNotBlank)?.let {
      "${RestBaseUrl}v1/students/$studentId/documents/read/$it"
    }
  }

  private fun buildDocumentCheckUrl(studentId: String, documentId: String): String? {
    return documentId.takeIf(String::isNotBlank)?.let {
      "${RestBaseUrl}v1/students/$studentId/documents/check/$it"
    }
  }
}

private fun Communication.withOfficialAttachmentUrls(studentId: String): Communication {
  if (attachments.isEmpty() && noticeboardAttachments.isEmpty()) return this
  val officialUrl = "${RestBaseUrl}v1/students/$studentId/noticeboard/attach/$evtCode/$pubId/101"

  val updatedAttachments = attachments.map { attachment ->
    if (attachment.url.isNullOrBlank()) attachment.copy(url = officialUrl, portalOnly = false) else attachment
  }
  val updatedNoticeboardAttachments = noticeboardAttachments.map { attachment ->
    if (attachment.url.isNullOrBlank()) {
      attachment.copy(
        url = officialUrl,
        portalOnly = false,
        action = attachment.action?.copy(url = officialUrl),
      )
    } else {
      attachment
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
    },
  )
}

private fun CommunicationDetail.withOfficialAttachmentUrls(studentId: String): CommunicationDetail {
  val updatedCommunication = communication.withOfficialAttachmentUrls(studentId)
  return copy(communication = updatedCommunication)
}
