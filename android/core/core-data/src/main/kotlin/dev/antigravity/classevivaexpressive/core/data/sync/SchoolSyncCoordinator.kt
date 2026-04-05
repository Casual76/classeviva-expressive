package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaPortalClient
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.LoginResult
import dev.antigravity.classevivaexpressive.core.network.client.PortalActionType
import dev.antigravity.classevivaexpressive.core.network.client.PortalUpload
import dev.antigravity.classevivaexpressive.core.network.client.normalizeDocumentAsset
import java.time.LocalDate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ProfileKey = "profile"
private const val GradesKey = "grades"
private const val PeriodsKey = "periods"
private const val SubjectsKey = "subjects"
private const val LessonsKey = "lessons"
private const val HomeworkKey = "homeworks"
private const val AgendaKey = "agenda"
private const val AbsencesKey = "absences"
private const val CommunicationsKey = "communications"
private const val NotesKey = "notes"
private const val MaterialsKey = "materials"
private const val DocumentsKey = "documents"
private const val SchoolbooksKey = "schoolbooks"

@Singleton
class SchoolSyncCoordinator @Inject constructor(
  private val json: Json,
  private val restClient: ClassevivaRestClient,
  private val portalClient: ClassevivaPortalClient,
  private val snapshotCacheDao: SnapshotCacheDao,
) {
  private var activeSession: UserSession? = null
  val syncStatus = MutableStateFlow(SyncStatus())

  fun attachSession(session: UserSession?) {
    activeSession = session
    restClient.setSession(session)
  }

  suspend fun login(username: String, password: String): UserSession {
    val loginResult: LoginResult = restClient.login(username, password)
    val provisional = UserSession(
      token = loginResult.token,
      studentId = loginResult.studentId,
      username = username,
      password = password,
      profile = loginResult.profileHint,
    )
    attachSession(provisional)
    val profile = restClient.getProfile()
    val session = provisional.copy(profile = profile)
    attachSession(session)
    bootstrapPortal(session)
    refreshAll(force = true)
    return session
  }

  suspend fun bootstrapPortal(session: UserSession) {
    portalClient.bootstrapSession(session.username, session.password)
  }

  fun portalCookieHeader(url: String): String? = portalClient.cookieHeader(url)

  suspend fun refreshAll(force: Boolean = false): SyncStatus {
    val session = activeSession ?: return SyncStatus(
      state = SyncState.ERROR,
      message = "Sessione assente.",
    )
    attachSession(session)
    syncStatus.value = SyncStatus(SyncState.SYNCING, syncStatus.value.lastSuccessfulSyncEpochMillis, "Sincronizzazione in corso")

    val errors = mutableListOf<String>()
    val schoolYearStart = schoolYearStart()
    val schoolYearEnd = schoolYearStart.plusYears(1).minusDays(1)

    bootstrapPortal(session)
    sync(ProfileKey, errors) { restClient.getProfile() }
    sync(GradesKey, errors) { restClient.getGrades() }
    sync(PeriodsKey, errors) { restClient.getPeriods() }
    sync(SubjectsKey, errors) { restClient.getSubjects() }
    sync(LessonsKey, errors) { restClient.getLessons(schoolYearStart.toString(), schoolYearEnd.toString()) }
    sync(HomeworkKey, errors) { restClient.getHomeworks() }
    sync(AgendaKey, errors) { restClient.getAgenda(schoolYearStart.toString(), schoolYearEnd.toString()) }
    sync(AbsencesKey, errors) { restClient.getAbsences() }
    sync(CommunicationsKey, errors) { restClient.getCommunications() }
    sync(NotesKey, errors) { restClient.getNotes() }
    sync(MaterialsKey, errors) { restClient.getMaterials() }
    sync(DocumentsKey, errors) { restClient.getDocuments() }
    sync(SchoolbooksKey, errors) { restClient.getSchoolbooks() }

    val now = System.currentTimeMillis()
    val next = if (errors.isEmpty()) {
      SyncStatus(SyncState.IDLE, now, if (force) "Sincronizzazione completa." else null)
    } else {
      SyncStatus(SyncState.PARTIAL, syncStatus.value.lastSuccessfulSyncEpochMillis ?: now, "Sincronizzazione parziale: ${errors.joinToString()}")
    }
    syncStatus.value = next
    return next
  }

  suspend fun getCommunicationDetail(pubId: String, evtCode: String) = runCatching {
    restClient.getCommunicationDetail(pubId, evtCode).also {
      refreshCommunicationsSnapshot()
    }
  }

  suspend fun acknowledgeCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    ensurePortalSession()
    portalClient.submitCommunicationAction(detail, PortalActionType.ACKNOWLEDGE).getOrThrow()
    refreshCommunicationsSnapshot()
    restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
  }

  suspend fun replyToCommunication(detail: CommunicationDetail, text: String): Result<CommunicationDetail> = runCatching {
    ensurePortalSession()
    portalClient.submitCommunicationAction(detail, PortalActionType.REPLY, replyText = text).getOrThrow()
    refreshCommunicationsSnapshot()
    restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
  }

  suspend fun joinCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    ensurePortalSession()
    portalClient.submitCommunicationAction(detail, PortalActionType.JOIN).getOrThrow()
    refreshCommunicationsSnapshot()
    restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
  }

  suspend fun uploadCommunicationFile(
    detail: CommunicationDetail,
    fileName: String,
    mimeType: String?,
    bytes: ByteArray,
  ): Result<CommunicationDetail> = runCatching {
    ensurePortalSession()
    portalClient.submitCommunicationAction(
      detail = detail,
      actionType = PortalActionType.FILE_UPLOAD,
      upload = PortalUpload(fileName = fileName, mimeType = mimeType, bytes = bytes),
    ).getOrThrow()
    refreshCommunicationsSnapshot()
    restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
  }

  suspend fun getNoteDetail(id: String, categoryCode: String): Result<NoteDetail> = runCatching {
    restClient.getNoteDetail(id, categoryCode).also {
      refreshNotesSnapshot()
    }
  }

  suspend fun openMaterial(item: MaterialItem): Result<MaterialAsset> = runCatching {
    restClient.getMaterialAsset(item)
  }

  suspend fun openDocument(item: DocumentItem): Result<DocumentAsset> = runCatching {
    ensurePortalSession()
    val url = item.viewUrl ?: item.confirmUrl ?: error("Il documento non ha un URL apribile.")
    val file = portalClient.downloadAuthenticated(url).getOrThrow()
    val mimeType = guessMimeType(item.title, file.fileName, file.mimeType, file.bytes)
    val textPreview = if (mimeType?.startsWith("text/") == true || mimeType == "application/json") {
      file.bytes.decodeToString().trim().takeIf(String::isNotBlank)?.take(12_000)
    } else {
      null
    }
    val base64Content = if (
      file.bytes.isNotEmpty() &&
      (
        mimeType == "application/pdf" ||
          mimeType == "text/html" ||
          mimeType?.startsWith("text/") == true ||
          mimeType?.startsWith("image/") == true
        )
    ) {
      Base64.getEncoder().encodeToString(file.bytes)
    } else {
      null
    }
    normalizeDocumentAsset(
      item = item,
      fileName = file.fileName ?: item.title,
      mimeType = mimeType,
      base64Content = base64Content,
      textPreview = textPreview,
      sourceUrl = url,
    )
  }

  suspend fun justifyAbsence(record: AbsenceRecord, reason: String? = null): Result<List<AbsenceRecord>> = runCatching {
    ensurePortalSession()
    portalClient.justifyAbsence(record, reason).getOrThrow()
    refreshAbsencesSnapshot()
  }

  private suspend inline fun <reified T> sync(
    key: String,
    errors: MutableList<String>,
    crossinline fetch: suspend () -> T,
  ) {
    runCatching {
      val value = fetch()
      snapshotCacheDao.upsert(SnapshotCacheEntity(key, json.encodeToString(value), System.currentTimeMillis()))
    }.onFailure {
      errors += key
    }
  }

  private suspend fun refreshCommunicationsSnapshot() {
    snapshotCacheDao.upsert(
      SnapshotCacheEntity(
        CommunicationsKey,
        json.encodeToString(restClient.getCommunications()),
        System.currentTimeMillis(),
      ),
    )
  }

  private suspend fun refreshNotesSnapshot() {
    snapshotCacheDao.upsert(
      SnapshotCacheEntity(
        NotesKey,
        json.encodeToString(restClient.getNotes()),
        System.currentTimeMillis(),
      ),
    )
  }

  private suspend fun refreshAbsencesSnapshot(): List<AbsenceRecord> {
    val absences = restClient.getAbsences()
    snapshotCacheDao.upsert(
      SnapshotCacheEntity(
        AbsencesKey,
        json.encodeToString(absences),
        System.currentTimeMillis(),
      ),
    )
    return absences
  }

  private fun schoolYearStart(): LocalDate {
    val now = LocalDate.now()
    val year = if (now.monthValue >= 8) now.year else now.year - 1
    return LocalDate.of(year, 9, 1)
  }

  private suspend fun ensurePortalSession() {
    val session = activeSession ?: return
    bootstrapPortal(session)
  }

  private fun guessMimeType(
    title: String,
    fileName: String?,
    declaredMimeType: String?,
    bytes: ByteArray,
  ): String? {
    return declaredMimeType
      ?: java.net.URLConnection.guessContentTypeFromName(fileName ?: title)
      ?: java.net.URLConnection.guessContentTypeFromStream(bytes.inputStream())
  }
}
