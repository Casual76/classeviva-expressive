package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.change.hasMeaningfulChangeComparedTo
import dev.antigravity.classevivaexpressive.core.data.repository.AbsencesSection
import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.DocumentsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.HistoryKindAgenda
import dev.antigravity.classevivaexpressive.core.data.repository.HistoryKindGrade
import dev.antigravity.classevivaexpressive.core.data.repository.HomeworkSection
import dev.antigravity.classevivaexpressive.core.data.repository.LessonsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MaterialsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MeetingBookingsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MeetingSlotsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MeetingTeachersSection
import dev.antigravity.classevivaexpressive.core.data.repository.NotesSection
import dev.antigravity.classevivaexpressive.core.data.repository.PeriodsSection
import dev.antigravity.classevivaexpressive.core.data.repository.ProfileSection
import dev.antigravity.classevivaexpressive.core.data.repository.SchoolbooksSection
import dev.antigravity.classevivaexpressive.core.data.repository.SubjectsSection
import dev.antigravity.classevivaexpressive.core.data.repository.toAgendaItemVersion
import dev.antigravity.classevivaexpressive.core.data.repository.buildLessonsWithFallback
import dev.antigravity.classevivaexpressive.core.data.repository.toCommunication
import dev.antigravity.classevivaexpressive.core.data.repository.toGradeVersion
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.AbsenceDao
import dev.antigravity.classevivaexpressive.core.database.database.AbsenceEntity
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryDao
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryEntity
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationDao
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationEntity
import dev.antigravity.classevivaexpressive.core.database.database.MaterialDao
import dev.antigravity.classevivaexpressive.core.database.database.MaterialEntity
import dev.antigravity.classevivaexpressive.core.database.database.DocumentDao
import dev.antigravity.classevivaexpressive.core.database.database.DocumentEntity
import dev.antigravity.classevivaexpressive.core.database.database.AgendaDao
import dev.antigravity.classevivaexpressive.core.database.database.AgendaItemEntity
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceJustificationRequest
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.AttachmentPayload
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmission
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmissionReceipt
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingJoinLink
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardAction
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.LoginResult
import dev.antigravity.classevivaexpressive.core.network.client.PortalClient
import dev.antigravity.classevivaexpressive.core.network.client.normalizeDocumentAsset
import dev.antigravity.classevivaexpressive.core.network.client.parseMeetingsSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val InteractiveRefreshCooldownMillis = 5L * 60L * 1000L
private const val InteractiveRefreshCacheMaxAgeMillis = 5L * 60L * 1000L

@Singleton
class SchoolSyncCoordinator @Inject constructor(
  private val json: Json,
  private val restClient: ClassevivaRestClient,
  private val portalClient: PortalClient,
  private val sessionStore: SessionStore,
  private val schoolYearStore: SchoolYearStore,
  private val timetableTemplateStore: TimetableTemplateStore,
  private val snapshotCacheDao: SnapshotCacheDao,
  private val gradeDao: GradeDao,
  private val agendaDao: AgendaDao,
  private val changeHistoryDao: ChangeHistoryDao,
  private val absenceDao: AbsenceDao,
  private val communicationDao: CommunicationDao,
  private val materialDao: MaterialDao,
  private val documentDao: DocumentDao,
  private val predictiveTimetableUseCase: PredictiveTimetableUseCase,
) {
  private var activeSession: UserSession? = null
  val syncStatus = MutableStateFlow(SyncStatus())
  private val syncMutex = Mutex()
  private val lastInteractiveRefreshAttempts = mutableMapOf<String, Long>()

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
      profile = loginResult.profileHint,
    )

    sessionStore.writeCredentials(username, password)
    sessionStore.writeSessionSilently(provisional)
    attachSession(provisional)

    val profile = restClient.getProfile()
    val session = provisional.copy(profile = profile)
    sessionStore.writeSessionSilently(session)
    attachSession(session)
    refreshAll(force = true)
    sessionStore.writeSession(session)
    return session
  }

  fun authToken(): String? = restClient.currentToken()

  fun clearPortalSession() = portalClient.clearSession()

  suspend fun refreshAll(force: Boolean = false): SyncStatus {
    if (!force && syncMutex.isLocked) return syncStatus.value
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    return refreshSelectedSections(selectedYear = selectedYear, force = force, refreshProfile = true)
  }

  suspend fun refreshCurrentSchoolYearForNotifications(force: Boolean = false): SyncStatus {
    if (!force && syncMutex.isLocked) return syncStatus.value
    return refreshSelectedSections(
      selectedYear = schoolYearStore.currentSchoolYearRef(),
      force = force,
      refreshProfile = false,
    )
  }

  suspend fun refreshAbsences(force: Boolean = false): Result<List<AbsenceRecord>> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val status = refreshSelectedSections(
      selectedYear = selectedYear,
      force = force,
      refreshProfile = false,
      sections = setOf(AbsencesSection),
    )
    if (status.state == SyncState.PARTIAL) {
      throw ClassevivaNetworkException("Impossibile aggiornare le assenze. Verificare la connessione.")
    }
    readYearScopedValue(AbsencesSection, selectedYear, emptyList<AbsenceRecord>())
  }

  suspend fun refreshHomeworks(force: Boolean = false): List<Homework> {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    refreshSelectedSections(
      selectedYear = selectedYear,
      force = force,
      refreshProfile = false,
      sections = setOf(HomeworkSection),
    )
    return readYearScopedValue(HomeworkSection, selectedYear, emptyList())
  }

  suspend fun refreshMeetings(force: Boolean = false): List<MeetingBooking> {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    refreshSelectedSections(
      selectedYear = selectedYear,
      force = force,
      refreshProfile = false,
      sections = setOf(MeetingTeachersSection, MeetingSlotsSection, MeetingBookingsSection),
    )
    return readYearScopedValue(MeetingBookingsSection, selectedYear, emptyList())
  }

  suspend fun submitHomework(submission: HomeworkSubmission): Result<HomeworkSubmissionReceipt> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val submitUrl = submission.submitUrl
      ?: throw ClassevivaNetworkException("URL consegna compito non disponibile per questo tenant.")
    portalClient.submitHomework(
      submitUrl = submitUrl,
      text = submission.text,
      attachment = submission.attachments.firstOrNull(),
    )
    refreshSchoolYear(
      selectedYear = selectedYear,
      force = true,
      refreshProfile = false,
      sections = setOf(HomeworkSection),
    )
    HomeworkSubmissionReceipt(
      homeworkId = submission.homeworkId,
      state = "SUCCESS",
      submittedAt = java.time.LocalDateTime.now().toString().take(16),
      message = "Compito inviato tramite portale on-device.",
    )
  }

  suspend fun getCommunicationDetail(pubId: String, evtCode: String) = runCatching {
    val entity = communicationDao.getByPubIdAndEvtCode(pubId, evtCode)
    val base = entity?.toCommunication(json)
    val restDetail = if (base != null) {
      runCatching { restClient.getCommunicationDetail(base) }
        .getOrElse {
          // API rejected the read (e.g. 400 for already-acknowledged or 404) —
          // serve the cached version so the sheet still opens with the stored content.
          CommunicationDetail(
            communication = base,
            content = base.contentPreview.ifBlank { base.title },
            actions = base.actions,
          )
        }
    } else {
      restClient.getCommunicationDetail(pubId, evtCode)
    }
    val detail = enrichCommunicationDetailFromPortalIfNeeded(restDetail)
    runCatching { communicationDao.markRead(detail.communication.id) }
    detail
  }

  suspend fun downloadAttachmentBytes(url: String): ByteArray = restClient.downloadAttachmentBytes(url)

  suspend fun markCommunicationReadRemotely(id: String, pubId: String, evtCode: String): Result<Unit> = runCatching {
    restClient.markNoticeboardRead(pubId = pubId, evtCode = evtCode)
    communicationDao.markRead(id)
  }

  suspend fun markAllCommunicationsReadRemotely(): Result<Unit> = runCatching {
    val session = activeSession ?: throw ClassevivaNetworkException("Sessione assente.")
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val unread = communicationDao.getUnread(session.studentId, selectedYear.id)
    val failures = coroutineScope {
      unread.map { entity ->
        async {
          if (entity.pubId.isBlank() || entity.evtCode.isBlank()) {
            communicationDao.markRead(entity.id)
            null
          } else {
            runCatching {
              restClient.markNoticeboardRead(pubId = entity.pubId, evtCode = entity.evtCode)
              communicationDao.markRead(entity.id)
            }.exceptionOrNull()?.let { entity.title.ifBlank { entity.id } }
          }
        }
      }.map { it.await() }.filterNotNull()
    }

    if (failures.isNotEmpty()) {
      val message = when (failures.size) {
        1 -> "Una comunicazione non è stata segnata come letta sul registro."
        else -> "${failures.size} comunicazioni non sono state segnate come lette sul registro."
      }
      throw ClassevivaNetworkException(message)
    }
  }

  suspend fun acknowledgeCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val pubId = detail.communication.pubId
    val evtCode = detail.communication.evtCode

    System.out.println("ACKNOWLEDGE COMMUNICATION: starting for pubId=$pubId, evtCode=$evtCode")

    val restResult = runCatching {
      restClient.confirmNoticeboard(detail.communication)
    }.onFailure { e ->
      System.err.println("ACKNOWLEDGE COMMUNICATION - REST confirmation failed: ${e.message}")
      e.printStackTrace()
    }
    val ackUrl = detail.acknowledgeUrl
    val portalResult: Result<Unit>? = if (!ackUrl.isNullOrBlank()) {
      System.out.println("ACKNOWLEDGE COMMUNICATION - Portal action URL found: $ackUrl")
      runCatching {
        portalClient.submitPortalAction(
          pageUrl = ackUrl,
          formKeywords = listOf("conferma", "presa", "visione", "sign", "firma", "ack"),
        )
        Unit
      }.onFailure { e ->
        System.err.println("ACKNOWLEDGE COMMUNICATION - Portal action failed: ${e.message}")
        e.printStackTrace()
      }
    } else {
      System.out.println("ACKNOWLEDGE COMMUNICATION - No portal action URL found.")
      null
    }

    val anyOk = restResult.isSuccess || portalResult?.isSuccess == true
    if (!anyOk) {
      val restErr = restResult.exceptionOrNull()?.message
      val portalErr = portalResult?.exceptionOrNull()?.message
      val composed = listOfNotNull(
        restErr?.let { "REST: $it" },
        portalErr?.let { "PORTALE: $it" }
      ).joinToString(" | ")
      throw ClassevivaNetworkException("Nessuna delle due modalità di conferma è riuscita: $composed")
    }

    System.out.println("ACKNOWLEDGE COMMUNICATION - Confirmation succeeded (anyOk=true). Marking read locally and refetching snapshot...")
    runCatching { communicationDao.markRead(detail.communication.id) }
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    val updated = runCatching {
      restClient.getCommunicationDetail(detail.communication)
    }.getOrDefault(detail)
    updated
  }

  suspend fun replyToCommunication(detail: CommunicationDetail, text: String): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val replyUrl = detail.replyUrl ?: detail.portalDetailUrl
    if (replyUrl.isNullOrBlank()) {
      throw ClassevivaNetworkException(
        "Questa comunicazione non espone un canale di risposta gestito dal registro.",
      )
    }
    portalClient.replyNoticeboard(replyUrl = replyUrl, text = text)
    runCatching { restClient.markNoticeboardRead(detail.communication.pubId, detail.communication.evtCode) }
    runCatching { communicationDao.markRead(detail.communication.id) }
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    val updated = runCatching {
      restClient.getCommunicationDetail(detail.communication)
    }.getOrDefault(detail)
    updated.copy(replyText = text)
  }

  suspend fun joinCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val joinUrl = detail.joinUrl
    if (joinUrl.isNullOrBlank()) {
      restClient.joinNoticeboard(
        pubId = detail.communication.pubId,
        evtCode = detail.communication.evtCode,
      )
    } else {
      portalClient.joinNoticeboard(joinUrl = joinUrl)
    }
    runCatching { restClient.markNoticeboardRead(detail.communication.pubId, detail.communication.evtCode) }
    runCatching { communicationDao.markRead(detail.communication.id) }
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching {
      restClient.getCommunicationDetail(detail.communication)
    }.getOrDefault(detail)
  }

  suspend fun uploadCommunicationFile(
    detail: CommunicationDetail,
    fileName: String,
    mimeType: String?,
    bytes: ByteArray,
  ): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val uploadUrl = detail.fileUploadUrl ?: detail.portalDetailUrl
    if (uploadUrl.isNullOrBlank()) {
      throw ClassevivaNetworkException(
        "Questa comunicazione non espone un canale di upload gestito dal registro.",
      )
    }
    portalClient.uploadNoticeboard(
      uploadUrl = uploadUrl,
      attachment = AttachmentPayload(
        fileName = fileName,
        mimeType = mimeType,
        base64Content = Base64.getEncoder().encodeToString(bytes),
      ),
    )
    runCatching { restClient.markNoticeboardRead(detail.communication.pubId, detail.communication.evtCode) }
    runCatching { communicationDao.markRead(detail.communication.id) }
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching {
      restClient.getCommunicationDetail(detail.communication)
    }.getOrDefault(detail)
  }

  suspend fun getNoteDetail(id: String, categoryCode: String): Result<NoteDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    runCatching {
      restClient.getNoteDetail(id, categoryCode)
    }.getOrElse { error ->
      val cached = readYearScopedValue(NotesSection, selectedYear, emptyList<Note>())
        .firstOrNull { note -> note.id == id && note.categoryCode == categoryCode }
        ?: throw error
      NoteDetail(
        note = cached,
        content = cached.contentPreview
          .takeIf(String::isNotBlank)
          ?: cached.title.ifBlank { cached.categoryLabel },
      )
    }.also {
      runCatching { refreshNotesSnapshot(selectedYear) }
    }
  }

  suspend fun openMaterial(item: MaterialItem): Result<MaterialAsset> = runCatching {
    restClient.getMaterialAsset(item)
  }

  suspend fun openDocument(item: DocumentItem): Result<DocumentAsset> = runCatching {
    val (bytes, declaredMimeType) = restClient.readDocument(item)
    val mimeType = guessMimeType(item.title, item.title, declaredMimeType, bytes)
    val textPreview = if (mimeType?.startsWith("text/") == true || mimeType == "application/json") {
      bytes.decodeToString().trim().takeIf(String::isNotBlank)?.take(12_000)
    } else {
      null
    }
    val base64Content = if (
      bytes.isNotEmpty() &&
      (
        mimeType == "application/pdf" ||
          mimeType == "text/html" ||
          mimeType?.startsWith("text/") == true ||
          mimeType?.startsWith("image/") == true
        )
    ) {
      Base64.getEncoder().encodeToString(bytes)
    } else {
      null
    }
    normalizeDocumentAsset(
      item = item,
      fileName = item.title,
      mimeType = mimeType,
      base64Content = base64Content,
      textPreview = textPreview,
      sourceUrl = item.viewUrl,
    )
  }

  suspend fun justifyAbsence(
    record: AbsenceRecord,
    reason: String? = null,
    request: AbsenceJustificationRequest = AbsenceJustificationRequest(
      absenceId = record.id,
      reasonText = reason,
      justifyUrl = record.justifyUrl,
      detailUrl = record.detailUrl,
    ),
  ): Result<List<AbsenceRecord>> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val effectiveRequest = request.copy(
      absenceId = record.id,
      reasonText = request.reasonText ?: reason,
      justifyUrl = request.justifyUrl ?: record.justifyUrl,
      detailUrl = request.detailUrl ?: record.detailUrl,
    )
    val session = activeSession ?: return Result.failure(ClassevivaNetworkException("Sessione assente."))
    portalClient.justifyAbsence(
      justifyUrl = effectiveRequest.justifyUrl,
      detailUrl = effectiveRequest.detailUrl,
      reasonText = effectiveRequest.reasonText,
      fileAttachment = effectiveRequest.attachment,
    )
    val updated = restClient.getAbsences(
      schoolYearStart(selectedYear).toString(),
      schoolYearEnd(selectedYear).toString(),
    )
    syncAbsences(selectedYear, mutableListOf(), session) { updated }
    updated
  }

  suspend fun bookMeeting(slot: MeetingSlot): Result<MeetingBooking> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    portalClient.submitPortalAction(
      pageUrl = slot.id,
      formKeywords = listOf("prenota", "book", "conferma"),
    )
    refreshMeetingsSnapshot(selectedYear)
    readYearScopedValue(MeetingBookingsSection, selectedYear, emptyList<MeetingBooking>())
      .firstOrNull()
      ?: MeetingBooking(
        id = slot.id,
        teacher = MeetingTeacher(id = slot.teacherId, name = "Docente"),
        slot = slot,
        status = "BOOKED",
      )
  }

  suspend fun cancelMeeting(booking: MeetingBooking): Result<List<MeetingBooking>> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    portalClient.submitPortalAction(
      pageUrl = booking.id,
      formKeywords = listOf("annulla", "cancel", "rimuovi", "disdici"),
    )
    refreshMeetingsSnapshot(selectedYear)
    readYearScopedValue(MeetingBookingsSection, selectedYear, emptyList())
  }

  suspend fun joinMeeting(booking: MeetingBooking): Result<MeetingJoinLink> = runCatching {
    val joinUrl = booking.slot.joinUrl
      ?: throw ClassevivaNetworkException("Link colloquio non disponibile.")
    MeetingJoinLink(bookingId = booking.id, url = joinUrl)
  }

  fun getPortalSessionCookies() = portalClient.getSessionCookies()

  private suspend fun refreshSelectedSections(
    selectedYear: SchoolYearRef,
    force: Boolean,
    refreshProfile: Boolean,
    sections: Set<String>? = null,
  ): SyncStatus {
    if (!force && syncMutex.isLocked) return syncStatus.value
    return syncMutex.withLock {
      refreshSchoolYear(
        selectedYear = selectedYear,
        force = force,
        refreshProfile = refreshProfile,
        sections = sections,
      )
    }
  }

  private fun CommunicationDetail.requiresVerifiedRestAcknowledgement(): Boolean {
    return communication.needsAck ||
      !acknowledgeUrl.isNullOrBlank() ||
      actions.any { action -> action.type == NoticeboardActionType.ACKNOWLEDGE }
  }

  private suspend fun refreshSchoolYear(
    selectedYear: SchoolYearRef,
    force: Boolean,
    refreshProfile: Boolean,
    sections: Set<String>? = null,
  ): SyncStatus {
    val session = activeSession ?: run {
        android.util.Log.e("SyncCoordinator", "Sincronizzazione abortita: activeSession è NULL")
        return SyncStatus(
            state = SyncState.ERROR,
            message = "Sessione assente (Coordinator).",
        )
    }
    val currentYear = schoolYearStore.currentSchoolYearRef()
    val yearStart = schoolYearStart(selectedYear)
    val yearEnd = schoolYearEnd(selectedYear)
    val isCurrentYear = selectedYear.id == currentYear.id
    val selectedSections = sections ?: allSections()
    val now = System.currentTimeMillis()

    if (!force && shouldSkipInteractiveRefresh(selectedYear, selectedSections, now)) {
      return syncStatus.value
    }
    recordInteractiveRefreshAttempt(selectedYear, selectedSections, now)

    android.util.Log.i("SyncCoordinator", "Avvio sincronizzazione per anno: ${selectedYear.id}")
    attachSession(session)
    syncStatus.value = SyncStatus(
      state = SyncState.SYNCING,
      lastSuccessfulSyncEpochMillis = syncStatus.value.lastSuccessfulSyncEpochMillis,
      message = "Sincronizzazione in corso",
    )

    val errors = mutableListOf<String>()

    if (refreshProfile && selectedSections.contains(ProfileSection)) {
      syncGlobal(ProfileSection, errors) { restClient.getProfile() }
    }

    if (selectedSections.contains(GradesSection)) {
      syncGrades(selectedYear, errors, session) {
        filterGradesForYear(restClient.getGrades(), selectedYear)
      }
    }
    if (selectedSections.contains(PeriodsSection)) {
      syncYearScoped(PeriodsSection, selectedYear, errors) { restClient.getPeriods() }
    }
    if (selectedSections.contains(SubjectsSection)) {
      syncYearScoped(SubjectsSection, selectedYear, errors) { restClient.getSubjects() }
    }
    if (selectedSections.contains(LessonsSection)) {
      syncYearScoped(LessonsSection, selectedYear, errors) {
        restClient.getLessons(yearStart.toString(), yearEnd.toString())
      }
    }
    if (selectedSections.contains(HomeworkSection)) {
      syncYearScoped(HomeworkSection, selectedYear, errors) {
        val homeworks = filterHomeworksForYear(restClient.getHomeworks(), selectedYear)
        val agendaHomeworks = restClient.getAgenda(yearStart.toString(), yearEnd.toString())
          .filter { it.category == AgendaCategory.HOMEWORK }
          .map(::agendaItemToHomework)
        mergeHomeworks(homeworks + agendaHomeworks)
      }
    }
    if (selectedSections.contains(AgendaSection)) {
      syncAgenda(selectedYear, errors, session) {
        restClient.getAgenda(yearStart.toString(), yearEnd.toString())
      }
    }
    if (selectedSections.contains(AbsencesSection)) {
      syncAbsences(selectedYear, errors, session) {
        restClient.getAbsences(yearStart.toString(), yearEnd.toString())
      }
    }
    if (selectedSections.contains(CommunicationsSection)) {
      syncCommunications(selectedYear, errors, session) {
        restClient.getCommunications().filter { comm ->
          filterByDate(comm.date, yearStart, yearEnd)
        }
      }
    }
    if (selectedSections.contains(NotesSection)) {
      syncYearScoped(NotesSection, selectedYear, errors) {
        restClient.getNotes().filter { note -> filterByDate(note.date, yearStart, yearEnd) }
      }
    }
    if (selectedSections.contains(MaterialsSection)) {
      syncMaterials(selectedYear, errors, session) {
        restClient.getMaterials().filter { mat -> filterByDate(mat.sharedAt, yearStart, yearEnd) }
      }
    }
    if (selectedSections.contains(DocumentsSection)) {
      syncDocuments(selectedYear, errors, session) { restClient.getDocuments() }
    }
    if (selectedSections.contains(SchoolbooksSection)) {
      if (isCurrentYear) syncYearScoped(SchoolbooksSection, selectedYear, errors) { restClient.getSchoolbooks() }
      else clearYearScoped(SchoolbooksSection, selectedYear, emptyList<SchoolbookCourse>())
    }

    if (
      selectedSections.contains(MeetingTeachersSection) ||
      selectedSections.contains(MeetingSlotsSection) ||
      selectedSections.contains(MeetingBookingsSection)
    ) {
      runCatching { refreshMeetingsSnapshot(selectedYear) }.onFailure {
        errors += MeetingBookingsSection
        clearMeetings(selectedYear)
      }
    }

    if (selectedSections.contains(LessonsSection) || selectedSections.contains(AgendaSection)) {
      runCatching { persistTimetableTemplate(selectedYear, session) }.onFailure {
        errors += LessonsSection
      }
    }

    val completedAt = System.currentTimeMillis()
    val next = if (errors.isEmpty()) {
      SyncStatus(
        state = SyncState.IDLE,
        lastSuccessfulSyncEpochMillis = completedAt,
        message = if (force) "Sincronizzazione completa." else null,
      )
    } else {
      SyncStatus(
        state = SyncState.PARTIAL,
        lastSuccessfulSyncEpochMillis = syncStatus.value.lastSuccessfulSyncEpochMillis ?: completedAt,
        message = "Sincronizzazione parziale: ${errors.distinct().joinToString()}",
      )
    }
    syncStatus.value = next
    return next
  }

  private suspend fun shouldSkipInteractiveRefresh(
    schoolYear: SchoolYearRef,
    sections: Set<String>,
    now: Long,
  ): Boolean {
    val key = interactiveRefreshKey(schoolYear, sections)
    val lastAttempt = lastInteractiveRefreshAttempts[key]
    if (lastAttempt != null && now - lastAttempt in 0 until InteractiveRefreshCooldownMillis) {
      return true
    }
    return sections.all { section -> isSectionCacheFresh(section, schoolYear, now) }
  }

  private fun recordInteractiveRefreshAttempt(
    schoolYear: SchoolYearRef,
    sections: Set<String>,
    now: Long,
  ) {
    lastInteractiveRefreshAttempts[interactiveRefreshKey(schoolYear, sections)] = now
  }

  private suspend fun isSectionCacheFresh(
    section: String,
    schoolYear: SchoolYearRef,
    now: Long,
  ): Boolean {
    val cacheKey = if (section == ProfileSection) {
      ProfileSection
    } else {
      yearScopedCacheKey(section, schoolYear)
    }
    val updatedAt = snapshotCacheDao.getByKey(cacheKey)?.updatedAtEpochMillis ?: return false
    return now - updatedAt in 0..InteractiveRefreshCacheMaxAgeMillis
  }

  private fun interactiveRefreshKey(
    schoolYear: SchoolYearRef,
    sections: Set<String>,
  ): String = "${schoolYear.id}:${sections.sorted().joinToString("|")}"

  private suspend fun syncGrades(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<Grade>,
  ) {
    runCatching {
      val grades = fetch()
      val existingGrades = gradeDao.getByYearOnce(session.studentId, schoolYear.id)
      val existingById = existingGrades.associateBy { it.id }
      val existingByIdentity = uniqueByIdentityKeys(existingGrades) { it.gradeIdentityKeys() }
      val incomingIdentityCounts = identityKeyCounts(grades) { it.gradeIdentityKeys() }
      val now = System.currentTimeMillis()
      val resolvedGrades = grades.map { grade ->
        val existing = resolveStableMatch(
          incomingId = grade.id,
          incomingKeys = grade.gradeIdentityKeys(),
          incomingIdentityCounts = incomingIdentityCounts,
          existingById = existingById,
          existingByIdentity = existingByIdentity,
        )
        ResolvedGrade(
          grade = grade,
          existing = existing,
          localId = existing?.id ?: grade.fallbackLocalId(),
        )
      }
      val historyEntries = resolvedGrades.mapNotNull { resolved ->
        val existing = resolved.existing ?: return@mapNotNull null
        if (!existing.hasMeaningfulChangeComparedTo(resolved.grade, includeOneSidedText = existing.firstSeenAtMs != null)) {
          null
        } else {
          val version = existing.toGradeVersion(now)
          val payload = json.encodeToString(version)
          ChangeHistoryEntity(
            id = historyEntryId(HistoryKindGrade, session.studentId, schoolYear.id, resolved.localId, now, payload),
            studentId = session.studentId,
            schoolYearId = schoolYear.id,
            itemKind = HistoryKindGrade,
            itemId = resolved.localId,
            recordedAtEpochMillis = now,
            payload = payload,
          )
        }
      }
      val entities = resolvedGrades.map { resolved ->
        val grade = resolved.grade
        GradeEntity(
          id = resolved.localId,
          studentId = session.studentId,
          schoolYearId = schoolYear.id,
          subject = grade.subject,
          valueLabel = grade.valueLabel,
          numericValue = grade.numericValue,
          description = grade.description,
          date = grade.date,
          type = grade.type,
          weight = grade.weight,
          notes = grade.notes,
          period = grade.period,
          periodCode = grade.periodCode,
          teacher = grade.teacher,
          color = grade.color,
          firstSeenAtMs = resolved.existing?.firstSeenAtMs ?: now,
        )
      }
      if (historyEntries.isNotEmpty()) {
        changeHistoryDao.upsertAll(historyEntries)
      }
      gradeDao.deleteByYear(session.studentId, schoolYear.id)
      gradeDao.upsertAll(entities)
      storeYearScopedValue(
        GradesSection,
        schoolYear,
        resolvedGrades.map { it.grade.copy(id = it.localId) },
      )
    }.onFailure {
      errors += GradesSection
    }
  }

  private suspend fun syncAgenda(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<AgendaItem>,
  ) {
    runCatching {
      val agenda = fetch()
      val existingAgenda = agendaDao.getByYearOnce(session.studentId, schoolYear.id)
      val existingById = existingAgenda.associateBy { it.id }
      val existingByIdentity = uniqueByIdentityKeys(existingAgenda) { it.agendaIdentityKeys() }
      val incomingIdentityCounts = identityKeyCounts(agenda) { it.agendaIdentityKeys() }
      val now = System.currentTimeMillis()
      val resolvedAgenda = agenda.map { item ->
        val existing = resolveStableMatch(
          incomingId = item.id,
          incomingKeys = item.agendaIdentityKeys(),
          incomingIdentityCounts = incomingIdentityCounts,
          existingById = existingById,
          existingByIdentity = existingByIdentity,
        )
        ResolvedAgendaItem(
          item = item,
          existing = existing,
          localId = existing?.id ?: item.fallbackLocalId(),
        )
      }
      val historyEntries = resolvedAgenda.mapNotNull { resolved ->
        val existing = resolved.existing ?: return@mapNotNull null
        if (!existing.hasMeaningfulChangeComparedTo(resolved.item, includeOneSidedText = existing.firstSeenAtMs != null)) {
          null
        } else {
          val version = existing.toAgendaItemVersion(now)
          val payload = json.encodeToString(version)
          ChangeHistoryEntity(
            id = historyEntryId(HistoryKindAgenda, session.studentId, schoolYear.id, resolved.localId, now, payload),
            studentId = session.studentId,
            schoolYearId = schoolYear.id,
            itemKind = HistoryKindAgenda,
            itemId = resolved.localId,
            recordedAtEpochMillis = now,
            payload = payload,
          )
        }
      }
      val entities = resolvedAgenda.map { resolved ->
        val item = resolved.item
        val existing = resolved.existing
        AgendaItemEntity(
          id = resolved.localId,
          studentId = session.studentId,
          schoolYearId = schoolYear.id,
          title = item.title,
          subtitle = item.subtitle,
          date = item.date,
          time = item.time,
          detail = item.detail,
          subject = item.subject,
          teacher = item.teacher,
          category = item.category.name,
          sharePayload = item.sharePayload,
          createdAt = item.createdAt ?: existing?.createdAt,
          firstSeenAtMs = existing?.firstSeenAtMs ?: now,
        )
      }
      if (historyEntries.isNotEmpty()) {
        changeHistoryDao.upsertAll(historyEntries)
      }
      agendaDao.deleteByYear(session.studentId, schoolYear.id)
      agendaDao.upsertAll(entities)
      storeYearScopedValue(
        AgendaSection,
        schoolYear,
        resolvedAgenda.map { resolved ->
          val existing = resolved.existing
          resolved.item.copy(
            id = resolved.localId,
            createdAt = resolved.item.createdAt
              ?: existing?.createdAt
              ?: existing?.firstSeenAtMs?.let(::epochMillisToCreatedAt),
          )
        },
      )
    }.onFailure {
      errors += AgendaSection
    }
  }

  private suspend fun syncAbsences(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<AbsenceRecord>,
  ) {
    runCatching {
      val absences = fetch()
      val entities = absences.map { record ->
        AbsenceEntity(
          id = record.id,
          studentId = session.studentId,
          schoolYearId = schoolYear.id,
          date = record.date,
          type = record.type.name,
          hours = record.hours,
          justified = record.justified,
          canJustify = record.canJustify,
          justificationDate = record.justificationDate,
          justificationReason = record.justificationReason,
          justifyUrl = record.justifyUrl,
          detailUrl = record.detailUrl,
        )
      }
      absenceDao.deleteByYear(session.studentId, schoolYear.id)
      absenceDao.upsertAll(entities)
      storeYearScopedValue(AbsencesSection, schoolYear, absences)
    }.onFailure {
      errors += AbsencesSection
    }
  }

  private suspend fun syncCommunications(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<Communication>,
  ) {
    runCatching {
      val communications = fetch()
      val entities = communications.map { comm ->
        CommunicationEntity(
          id = comm.id,
          studentId = session.studentId,
          schoolYearId = schoolYear.id,
          pubId = comm.pubId,
          evtCode = comm.evtCode,
          title = comm.title,
          contentPreview = comm.contentPreview,
          sender = comm.sender,
          date = comm.date,
          read = comm.read,
          attachments = json.encodeToString(comm.attachments),
          category = comm.category,
          needsAck = comm.needsAck,
          needsReply = comm.needsReply,
          needsJoin = comm.needsJoin,
          needsFile = comm.needsFile,
          actions = json.encodeToString(comm.actions),
          noticeboardAttachments = json.encodeToString(comm.noticeboardAttachments),
          capabilityState = json.encodeToString(comm.capabilityState),
        )
      }
      communicationDao.deleteByYear(session.studentId, schoolYear.id)
      communicationDao.upsertAll(entities)
      storeYearScopedValue(CommunicationsSection, schoolYear, communications)
    }.onFailure {
      errors += CommunicationsSection
    }
  }

  private suspend fun syncMaterials(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<MaterialItem>,
  ) {
    runCatching {
      val materials = fetch()
      val entities = materials.map { mat ->
        MaterialEntity(
          id = mat.id,
          studentId = session.studentId,
          schoolYearId = schoolYear.id,
          teacherId = mat.teacherId,
          teacherName = mat.teacherName,
          folderId = mat.folderId,
          folderName = mat.folderName,
          title = mat.title,
          objectId = mat.objectId,
          objectType = mat.objectType,
          sharedAt = mat.sharedAt,
          capabilityState = json.encodeToString(mat.capabilityState),
          attachments = json.encodeToString(mat.attachments),
        )
      }
      materialDao.deleteByYear(session.studentId, schoolYear.id)
      materialDao.upsertAll(entities)
      storeYearScopedValue(MaterialsSection, schoolYear, materials)
    }.onFailure {
      errors += MaterialsSection
    }
  }

  private suspend fun syncDocuments(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<DocumentItem>,
  ) {
    runCatching {
      val documents = fetch()
      val entities = documents.map { doc ->
        DocumentEntity(
          id = doc.id,
          studentId = session.studentId,
          schoolYearId = schoolYear.id,
          title = doc.title,
          detail = doc.detail,
          viewUrl = doc.viewUrl,
          confirmUrl = doc.confirmUrl,
          capabilityState = json.encodeToString(doc.capabilityState),
        )
      }
      documentDao.deleteByYear(session.studentId, schoolYear.id)
      documentDao.upsertAll(entities)
      storeYearScopedValue(DocumentsSection, schoolYear, documents)
    }.onFailure {
      errors += DocumentsSection
    }
  }

  private suspend inline fun <reified T> syncGlobal(
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

  private suspend inline fun <reified T> syncYearScoped(
    section: String,
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    crossinline fetch: suspend () -> T,
  ) {
    runCatching {
      storeYearScopedValue(section, schoolYear, fetch())
    }.onFailure {
      errors += section
    }
  }

  private suspend inline fun <reified T> storeYearScopedValue(
    section: String,
    schoolYear: SchoolYearRef,
    value: T,
  ) {
    snapshotCacheDao.upsert(
      SnapshotCacheEntity(
        cacheKey = yearScopedCacheKey(section, schoolYear),
        payload = json.encodeToString(value),
        updatedAtEpochMillis = System.currentTimeMillis(),
      ),
    )
  }

  private suspend inline fun <reified T> clearYearScoped(
    section: String,
    schoolYear: SchoolYearRef,
    emptyValue: T,
  ) {
    storeYearScopedValue(section, schoolYear, emptyValue)
  }

  private suspend inline fun <reified T> readYearScopedValue(
    section: String,
    schoolYear: SchoolYearRef,
    default: T,
  ): T {
    val entity = snapshotCacheDao.getByKey(yearScopedCacheKey(section, schoolYear)) ?: return default
    return runCatching { json.decodeFromString<T>(entity.payload) }.getOrDefault(default)
  }

  private suspend fun refreshCommunicationsSnapshot(schoolYear: SchoolYearRef) {
    if (schoolYear.id != schoolYearStore.currentSchoolYearRef().id) return
    val session = activeSession ?: return
    syncCommunications(schoolYear, mutableListOf(), session) {
      restClient.getCommunications()
    }
  }

  private suspend fun enrichCommunicationDetailFromPortalIfNeeded(
    detail: CommunicationDetail,
  ): CommunicationDetail {
    val portalUrl = detail.portalDetailUrl?.takeIf(String::isNotBlank) ?: return detail
    val hasUsefulContent = detail.content.isNotBlank() &&
      detail.content != detail.communication.contentPreview &&
      detail.content != detail.communication.title
    val needsPortalActionDiscovery =
      (detail.communication.needsAck && detail.acknowledgeUrl.isNullOrBlank()) ||
        (detail.communication.needsReply && detail.replyUrl.isNullOrBlank()) ||
        (detail.communication.needsJoin && detail.joinUrl.isNullOrBlank()) ||
        (detail.communication.needsFile && detail.fileUploadUrl.isNullOrBlank())
    if (hasUsefulContent && !needsPortalActionDiscovery) return detail
    val portal = runCatching { portalClient.getNoticeboardDetail(portalUrl) }.getOrNull() ?: return detail
    val content = portal.content?.takeIf(String::isNotBlank) ?: detail.content
    val actions = (
      detail.actions +
        listOfNotNull(
          portal.acknowledgeUrl?.let {
            NoticeboardAction(NoticeboardActionType.ACKNOWLEDGE, "Conferma", it)
          },
          portal.replyUrl?.let {
            NoticeboardAction(NoticeboardActionType.REPLY, "Rispondi", it)
          },
          portal.joinUrl?.let {
            NoticeboardAction(NoticeboardActionType.JOIN, "Aderisci", it)
          },
          portal.fileUploadUrl?.let {
            NoticeboardAction(NoticeboardActionType.UPLOAD, "Carica file", it)
          },
        )
      ).distinctBy { "${it.type}:${it.url.orEmpty()}" }
    return detail.copy(
      communication = detail.communication.copy(
        contentPreview = content.take(150),
        actions = actions,
        needsAck = detail.communication.needsAck || portal.acknowledgeUrl != null,
        needsReply = detail.communication.needsReply || portal.replyUrl != null,
        needsJoin = detail.communication.needsJoin || portal.joinUrl != null,
        needsFile = detail.communication.needsFile || portal.fileUploadUrl != null,
      ),
      content = content,
      acknowledgeUrl = detail.acknowledgeUrl ?: portal.acknowledgeUrl,
      replyUrl = detail.replyUrl ?: portal.replyUrl,
      joinUrl = detail.joinUrl ?: portal.joinUrl,
      fileUploadUrl = detail.fileUploadUrl ?: portal.fileUploadUrl,
      actions = actions,
    )
  }

  private suspend fun refreshNotesSnapshot(schoolYear: SchoolYearRef) {
    if (schoolYear.id != schoolYearStore.currentSchoolYearRef().id) return
    storeYearScopedValue(
      NotesSection,
      schoolYear,
      restClient.getNotes(),
    )
  }

  private suspend fun refreshMeetingsSnapshot(schoolYear: SchoolYearRef) {
    val page = portalClient.getMeetingsPageHtml()
    if (page == null) {
      clearMeetings(schoolYear)
      return
    }
    val snapshot = parseMeetingsSnapshot(page.first, page.second)
    storeYearScopedValue(MeetingTeachersSection, schoolYear, snapshot.teachers)
    storeYearScopedValue(MeetingSlotsSection, schoolYear, snapshot.slots)
    storeYearScopedValue(MeetingBookingsSection, schoolYear, snapshot.bookings)
  }

  private suspend fun persistTimetableTemplate(
    schoolYear: SchoolYearRef,
    session: UserSession,
  ) {
    val currentTemplate = timetableTemplateStore.observeTemplate(schoolYear.id).first()
    if (currentTemplate.isOfficial == true) {
      // Do not overwrite official imported template
      return
    }
    
    val lessons = readYearScopedValue(LessonsSection, schoolYear, emptyList<Lesson>())
    val agenda = agendaDao.getByYearOnce(session.studentId, schoolYear.id).map { entity ->
      AgendaItem(
        id = entity.id,
        title = entity.title,
        subtitle = entity.subtitle,
        date = entity.date,
        time = entity.time,
        detail = entity.detail,
        subject = entity.subject,
        teacher = entity.teacher,
        category = AgendaCategory.valueOf(entity.category),
        sharePayload = entity.sharePayload,
        createdAt = entity.createdAt ?: entity.firstSeenAtMs?.let(::epochMillisToCreatedAt),
      )
    }
    val preparedLessons = buildLessonsWithFallback(lessons, agenda)
    
    val newTemplate = predictiveTimetableUseCase.generateTimetableTemplate(preparedLessons)
    val merged = newTemplate.copy(manualOverrides = currentTemplate?.manualOverrides ?: emptyMap())
    
    timetableTemplateStore.writeTemplate(
      schoolYear.id,
      merged,
    )
  }

  private suspend fun clearMeetings(schoolYear: SchoolYearRef) {
    storeYearScopedValue(MeetingTeachersSection, schoolYear, emptyList<MeetingTeacher>())
    storeYearScopedValue(MeetingSlotsSection, schoolYear, emptyList<MeetingSlot>())
    storeYearScopedValue(MeetingBookingsSection, schoolYear, emptyList<MeetingBooking>())
  }

  private fun schoolYearStart(schoolYear: SchoolYearRef): LocalDate {
    return LocalDate.of(schoolYear.startYear, 9, 1)
  }

  private fun schoolYearEnd(schoolYear: SchoolYearRef): LocalDate {
    return LocalDate.of(schoolYear.endYear, 8, 31)
  }

  private fun filterHomeworksForYear(homeworks: List<Homework>, schoolYear: SchoolYearRef): List<Homework> {
    val start = schoolYearStart(schoolYear)
    val end = schoolYearEnd(schoolYear)
    return homeworks.filter { homework ->
      runCatching { LocalDate.parse(homework.dueDate) }.getOrNull()?.let { dueDate ->
        !dueDate.isBefore(start) && !dueDate.isAfter(end)
      } ?: false
    }
  }

  private fun agendaItemToHomework(item: AgendaItem): Homework {
    val id = item.id.takeIf(String::isNotBlank)?.let { "agenda-$it" }
      ?: "agenda-${homeworkKey(item.date, item.subject, item.title)}"
    return Homework(
      id = id,
      subject = item.subject ?: item.subtitle,
      description = item.title,
      dueDate = item.date,
      createdAt = item.createdAt,
      history = item.history,
      notes = item.detail,
      attachments = emptyList(),
    )
  }

  private fun mergeHomeworks(homeworks: List<Homework>): List<Homework> {
    return homeworks
      .filter { it.description.isNotBlank() && it.dueDate.isNotBlank() }
      .groupBy { homeworkKey(it.dueDate, it.subject, it.description) }
      .values
      .map { duplicates ->
        duplicates.reduce { accumulated, candidate ->
          accumulated.copy(
            createdAt = accumulated.createdAt ?: candidate.createdAt,
            history = mergeHomeworkHistory(accumulated.history, candidate.history),
            notes = accumulated.notes ?: candidate.notes,
            attachments = accumulated.attachments.ifEmpty { candidate.attachments },
          )
        }
      }
      .sortedWith(compareBy<Homework> { it.dueDate }.thenBy { it.subject }.thenBy { it.description })
  }

  private fun mergeHomeworkHistory(
    first: List<AgendaItemVersion>,
    second: List<AgendaItemVersion>,
  ): List<AgendaItemVersion> {
    return (first + second)
      .distinctBy { version ->
        listOf(
          version.recordedAtEpochMillis.toString(),
          version.title,
          version.subtitle,
          version.date,
          version.time.orEmpty(),
          version.detail.orEmpty(),
        ).joinToString("|")
      }
      .sortedByDescending { it.recordedAtEpochMillis }
  }

  private fun homeworkKey(date: String, subject: String?, text: String): String {
    return listOf(date, subject.orEmpty(), text)
      .joinToString("|")
      .lowercase()
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private fun filterGradesForYear(grades: List<Grade>, schoolYear: SchoolYearRef): List<Grade> {
    val start = schoolYearStart(schoolYear)
    val end = schoolYearEnd(schoolYear)
    return grades.filter { grade ->
      runCatching { LocalDate.parse(grade.date) }.getOrNull()?.let { date ->
        !date.isBefore(start) && !date.isAfter(end)
      } ?: true
    }
  }

  private data class ResolvedGrade(
    val grade: Grade,
    val existing: GradeEntity?,
    val localId: String,
  )

  private data class ResolvedAgendaItem(
    val item: AgendaItem,
    val existing: AgendaItemEntity?,
    val localId: String,
  )

  private fun GradeEntity.gradeIdentityKeys(): List<String> {
    return gradeIdentityKeys(
      date = date,
      subject = subject,
      type = type,
      periodCode = periodCode,
      period = period,
    )
  }

  private fun Grade.gradeIdentityKeys(): List<String> {
    return gradeIdentityKeys(
      date = date,
      subject = subject,
      type = type,
      periodCode = periodCode,
      period = period,
    )
  }

  private fun gradeIdentityKeys(
    date: String,
    subject: String?,
    type: String?,
    periodCode: String?,
    period: String?,
  ): List<String> {
    val periodLabel = periodCode?.takeIf(String::isNotBlank) ?: period
    return listOfNotNull(
      identityKey("grade:v2", required = listOf(date, subject), optional = listOf(type, periodLabel)),
      identityKey("grade:v1", required = listOf(date, subject), optional = listOf(type)),
      identityKey("grade:v0", required = listOf(date, subject)),
    ).distinct()
  }

  private fun AgendaItemEntity.agendaIdentityKeys(): List<String> {
    return agendaIdentityKeys(
      date = date,
      time = time,
      category = category,
      subject = agendaSubjectLabel(),
      createdAt = createdAt,
    )
  }

  private fun AgendaItem.agendaIdentityKeys(): List<String> {
    return agendaIdentityKeys(
      date = date,
      time = time,
      category = category.name,
      subject = subject ?: subtitle,
      createdAt = createdAt,
    )
  }

  private fun agendaIdentityKeys(
    date: String,
    time: String?,
    category: String,
    subject: String?,
    createdAt: String?,
  ): List<String> {
    return listOfNotNull(
      identityKey("agenda:v2", required = listOf(date, category, subject), optional = listOf(time, createdAt)),
      identityKey("agenda:v1", required = listOf(date, category, subject), optional = listOf(time)),
      identityKey("agenda:v0", required = listOf(date, category, subject)),
      identityKey("agenda:time", required = listOf(date, category, time)),
      identityKey("agenda:created", required = listOf(date, category, createdAt)),
      identityKey("agenda:day", required = listOf(date, category)),
    ).distinct()
  }

  private fun AgendaItemEntity.agendaSubjectLabel(): String {
    return subject?.takeIf(String::isNotBlank) ?: subtitle
  }

  private fun Grade.fallbackLocalId(): String {
    return id.takeIf(String::isNotBlank)
      ?: generatedLocalId("grade", gradeIdentityKeys().firstOrNull() ?: gradeCoreFingerprint())
  }

  private fun AgendaItem.fallbackLocalId(): String {
    return id.takeIf(String::isNotBlank)
      ?: generatedLocalId("agenda", agendaIdentityKeys().firstOrNull() ?: agendaCoreFingerprint())
  }

  private fun Grade.gradeCoreFingerprint(): String {
    return listOf(
      "grade:core",
      identityPart(date),
      identityPart(subject),
      identityPart(valueLabel),
      numericValue?.toString().orEmpty(),
      identityPart(description),
      identityPart(type),
      weight?.toString().orEmpty(),
      identityPart(notes),
      identityPart(period),
      identityPart(periodCode),
    ).joinToString("|")
  }

  private fun AgendaItem.agendaCoreFingerprint(): String {
    return listOf(
      "agenda:core",
      identityPart(date),
      identityPart(time),
      identityPart(category.name),
      identityPart(subject ?: subtitle),
      identityPart(title),
      identityPart(detail),
    ).joinToString("|")
  }

  private fun <Existing> resolveStableMatch(
    incomingId: String,
    incomingKeys: List<String>,
    incomingIdentityCounts: Map<String, Int>,
    existingById: Map<String, Existing>,
    existingByIdentity: Map<String, Existing>,
  ): Existing? {
    incomingId.takeIf(String::isNotBlank)?.let { id ->
      existingById[id]?.let { return it }
    }
    return incomingKeys.firstNotNullOfOrNull { key ->
      if (incomingIdentityCounts[key] == 1) existingByIdentity[key] else null
    }
  }

  private fun <T> identityKeyCounts(
    items: List<T>,
    keySelector: (T) -> List<String>,
  ): Map<String, Int> {
    return items
      .flatMap { keySelector(it).distinct() }
      .groupingBy { it }
      .eachCount()
  }

  private fun <T> uniqueByIdentityKeys(
    items: List<T>,
    keySelector: (T) -> List<String>,
  ): Map<String, T> {
    return items
      .flatMap { item -> keySelector(item).distinct().map { key -> key to item } }
      .groupBy(keySelector = { it.first }, valueTransform = { it.second })
      .mapNotNull { (key, matchedItems) ->
        matchedItems.distinct().singleOrNull()?.let { key to it }
      }
      .toMap()
  }

  private fun identityKey(
    prefix: String,
    required: List<String?>,
    optional: List<String?> = emptyList(),
  ): String? {
    val requiredParts = required.map(::identityPart)
    if (requiredParts.any(String::isBlank)) return null
    val optionalParts = optional.map { identityPart(it).ifBlank { "-" } }
    return (listOf(prefix) + requiredParts + optionalParts).joinToString("|")
  }

  private fun identityPart(value: String?): String {
    return value.orEmpty()
      .trim()
      .lowercase()
      .replace(Regex("\\s+"), " ")
  }

  private fun generatedLocalId(prefix: String, seed: String): String {
    return "$prefix-${Integer.toUnsignedString(seed.hashCode(), 36)}"
  }

  private fun historyEntryId(
    kind: String,
    studentId: String,
    schoolYearId: String,
    itemId: String,
    recordedAtEpochMillis: Long,
    payload: String,
  ): String {
    return "$kind::$studentId::$schoolYearId::$itemId::$recordedAtEpochMillis::${payload.hashCode()}"
  }

  private fun filterByDate(date: String?, start: LocalDate, end: LocalDate): Boolean {
    if (date.isNullOrBlank()) return true
    return runCatching { LocalDate.parse(date) }.getOrNull()?.let { d ->
      !d.isBefore(start) && !d.isAfter(end)
    } ?: true
  }

  private fun allSections(): Set<String> {
    return setOf(
      ProfileSection,
      GradesSection,
      PeriodsSection,
      SubjectsSection,
      LessonsSection,
      HomeworkSection,
      AgendaSection,
      AbsencesSection,
      CommunicationsSection,
      NotesSection,
      MaterialsSection,
      DocumentsSection,
      SchoolbooksSection,
      MeetingTeachersSection,
      MeetingSlotsSection,
      MeetingBookingsSection,
    )
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

  private fun epochMillisToCreatedAt(value: Long): String {
    return java.time.Instant.ofEpochMilli(value)
      .atZone(ZoneId.systemDefault())
      .toLocalDateTime()
      .toString()
      .take(16)
  }

}
