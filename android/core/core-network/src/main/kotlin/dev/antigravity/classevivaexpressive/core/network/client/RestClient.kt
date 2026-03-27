package dev.antigravity.classevivaexpressive.core.network.client

import android.util.Base64
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
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
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val ApiBaseUrl = "https://web.spaggiari.eu/rest/v1"
private const val ApiKey = "Tg1NWEwNGIgIC0K"
internal const val UserAgent = "CVVS/std/4.2.3 Android/12"
private val JsonContentType = "application/json; charset=utf-8".toMediaType()

class ClassevivaNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class LoginResult(
  val token: String,
  val studentId: String,
  val profileHint: StudentProfile,
)

@Singleton
class ClassevivaRestClient @Inject constructor(
  private val json: Json,
  private val httpClient: OkHttpClient,
) {
  private var activeSession: UserSession? = null

  fun setSession(session: UserSession?) {
    activeSession = session
  }

  suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
    val payload = """
      {"ident":null,"pass":${json.encodeToString(String.serializer(), password)},"uid":${json.encodeToString(String.serializer(), username)}}
    """.trimIndent()
    val response = executeJsonRequest(
      path = "/auth/login",
      method = "POST",
      body = payload,
      includeAuth = false,
    )

    val token = response.string("token")
      ?: throw ClassevivaNetworkException("Classeviva non ha restituito un token valido.")
    val studentId = normalizeStudentId(username)
      ?: normalizeStudentId(response.string("ident"))
      ?: normalizeStudentId(response.string("usrId"))
      ?: normalizeStudentId(response.string("userId"))
      ?: throw ClassevivaNetworkException("Studente non identificato dopo il login.")

    LoginResult(
      token = token,
      studentId = studentId,
      profileHint = normalizeProfile(response, studentId),
    )
  }

  suspend fun getProfile(): StudentProfile = withContext(Dispatchers.IO) {
    ensureSession()
    normalizeProfile(
      executeJsonRequest("/students/${activeSession!!.studentId}/card"),
      activeSession!!.studentId,
    )
  }

  suspend fun getGrades(): List<Grade> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/grades"), "grades", "events", "items")
      .map(::normalizeGrade)
  }

  suspend fun getLessons(startDate: String, endDate: String): List<Lesson> = withContext(Dispatchers.IO) {
    ensureSession()
    val path = "/students/${activeSession!!.studentId}/lessons/${toApiDateParam(startDate)}/${toApiDateParam(endDate)}"
    extractArray(executeJsonRequest(path), "lessons", "agenda", "items").map(::normalizeLesson)
  }

  suspend fun getHomeworks(): List<Homework> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/homeworks"), "homeworks", "items", "agenda")
      .map(::normalizeHomework)
  }

  suspend fun getAbsences() = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/absences/details"), "events", "absences", "items")
      .map(::normalizeAbsence)
  }

  suspend fun getCommunications(): List<Communication> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/noticeboard"), "items", "noticeboard", "communications")
      .map(::normalizeCommunication)
  }

  suspend fun getCommunicationDetail(pubId: String, evtCode: String): CommunicationDetail = withContext(Dispatchers.IO) {
    ensureSession()
    val base = getCommunications().firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
      ?: throw ClassevivaNetworkException("Comunicazione non trovata.")
    val detail = executeJsonRequest(
      path = "/students/${activeSession!!.studentId}/noticeboard/read/$evtCode/$pubId/101",
      method = "POST",
      body = "",
    )
    normalizeCommunicationDetail(detail, base)
  }

  suspend fun getAgenda(startDate: String, endDate: String) = withContext(Dispatchers.IO) {
    ensureSession()
    val path = "/students/${activeSession!!.studentId}/agenda/all/${toApiDateParam(startDate)}/${toApiDateParam(endDate)}"
    extractArray(executeJsonRequest(path), "agenda", "events", "items").map(::normalizeAgendaItem)
  }

  suspend fun getNotes(): List<Note> = withContext(Dispatchers.IO) {
    ensureSession()
    val root = executeJsonRequest("/students/${activeSession!!.studentId}/notes/all")
    root.entries
      .flatMap { (categoryCode, items) ->
        items.array().map { normalizeNote(it.obj(), categoryCode) }
      }
      .sortedByDescending { it.date }
  }

  suspend fun getNoteDetail(id: String, categoryCode: String): NoteDetail = withContext(Dispatchers.IO) {
    ensureSession()
    val base = getNotes().firstOrNull { it.id == id && it.categoryCode == categoryCode }
      ?: throw ClassevivaNetworkException("Nota non trovata.")
    val root = executeJsonRequest(
      path = "/students/${activeSession!!.studentId}/notes/$categoryCode/read/$id",
      method = "POST",
      body = "",
    )
    val event = root["event"].obj().takeIf { it.isNotEmpty() } ?: root
    val content = event.string("evtText", "text", "content") ?: base.contentPreview
    NoteDetail(note = base.copy(contentPreview = preview(content, base.contentPreview)), content = content)
  }

  suspend fun getMaterials(): List<MaterialItem> = withContext(Dispatchers.IO) {
    ensureSession()
    val teachers = extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/didactics"), "didacticts")
    teachers.flatMap { teacherElement ->
      val teacher = teacherElement.obj()
      val teacherId = teacher.string("teacherId").orEmpty()
      val teacherName = teacher.string("teacherName", "teacherLastName") ?: "Docente"
      extractArray(teacher["folders"].obj(), "folders").flatMap { folderElement ->
        normalizeMaterialFolder(folderElement.obj(), teacherId, teacherName)
      }
    }.sortedByDescending { it.sharedAt }
  }

  suspend fun getMaterialAsset(item: MaterialItem): MaterialAsset = withContext(Dispatchers.IO) {
    ensureSession()
    val request = Request.Builder()
      .url("$ApiBaseUrl/students/${activeSession!!.studentId}/didactics/item/${item.id}")
      .headers(defaultHeaders(includeAuth = true))
      .get()
      .build()
    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw ClassevivaNetworkException("Impossibile aprire il materiale: ${response.code}")
      }
      val bytes = response.body?.bytes() ?: ByteArray(0)
      val mimeType = response.header("Content-Type")?.substringBefore(";")
      val base64Content = if (bytes.isNotEmpty()) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
      val textPreview = mimeType?.takeIf { it.startsWith("text/") }?.let {
        bytes.copyOfRange(0, bytes.size.coerceAtMost(512)).decodeToString().trim().takeIf(String::isNotBlank)
      }
      normalizeMaterialAsset(item, mimeType, base64Content, textPreview)
    }
  }

  suspend fun getDocuments(): List<DocumentItem> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(
      executeJsonRequest("/students/${activeSession!!.studentId}/documents", method = "POST", body = ""),
      "schoolReports",
    ).map(::normalizeDocument)
  }

  suspend fun getSchoolbooks(): List<SchoolbookCourse> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/schoolbooks"), "schoolbooks")
      .map(::normalizeSchoolbookCourse)
  }

  suspend fun getPeriods(): List<Period> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/periods"), "periods").map(::normalizePeriod)
  }

  suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
    ensureSession()
    extractArray(executeJsonRequest("/students/${activeSession!!.studentId}/subjects"), "subjects").map(::normalizeSubject)
  }

  private fun ensureSession() {
    if (activeSession == null) {
      throw ClassevivaNetworkException("Sessione assente. Effettua di nuovo il login.")
    }
  }

  private fun defaultHeaders(includeAuth: Boolean): okhttp3.Headers {
    val builder = okhttp3.Headers.Builder()
      .add("Content-Type", "application/json")
      .add("User-Agent", UserAgent)
      .add("Z-Dev-ApiKey", ApiKey)
    if (includeAuth) {
      activeSession?.token?.let { builder.add("Z-Auth-Token", it) }
    }
    return builder.build()
  }

  private fun executeJsonRequest(
    path: String,
    method: String = "GET",
    body: String? = null,
    includeAuth: Boolean = true,
  ): JsonObject {
    val builder = Request.Builder()
      .url("$ApiBaseUrl$path")
      .headers(defaultHeaders(includeAuth))

    when (method.uppercase()) {
      "POST" -> builder.post((body ?: "").toRequestBody(JsonContentType))
      else -> builder.get()
    }

    return httpClient.newCall(builder.build()).execute().use { response ->
      val payload = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw ClassevivaNetworkException("Classeviva ha risposto con ${response.code}: $payload")
      }
      when {
        payload.isBlank() -> JsonObject(emptyMap())
        else -> json.parseToJsonElement(payload).obj()
      }
    }
  }
}
