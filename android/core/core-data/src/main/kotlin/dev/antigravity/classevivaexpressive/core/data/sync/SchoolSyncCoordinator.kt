package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.AbsencesSection
import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.DocumentsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
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
import dev.antigravity.classevivaexpressive.core.data.repository.buildLessonsWithFallback
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.AbsenceDao
import dev.antigravity.classevivaexpressive.core.database.database.AbsenceEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
  private val absenceDao: AbsenceDao,
  private val communicationDao: CommunicationDao,
  private val materialDao: MaterialDao,
  private val documentDao: DocumentDao,
  private val predictiveTimetableUseCase: PredictiveTimetableUseCase,
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
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    return refreshSchoolYear(selectedYear = selectedYear, force = force, refreshProfile = true)
  }

  suspend fun refreshCurrentSchoolYearForNotifications(force: Boolean = false): SyncStatus {
    return refreshSchoolYear(
      selectedYear = schoolYearStore.currentSchoolYearRef(),
      force = force,
      refreshProfile = false,
    )
  }

  suspend fun refreshAbsences(force: Boolean = false): Result<List<AbsenceRecord>> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val status = refreshSchoolYear(
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
    refreshSchoolYear(
      selectedYear = selectedYear,
      force = force,
      refreshProfile = false,
      sections = setOf(HomeworkSection),
    )
    return readYearScopedValue(HomeworkSection, selectedYear, emptyList())
  }

  suspend fun refreshMeetings(force: Boolean = false): List<MeetingBooking> {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    refreshSchoolYear(
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
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    restClient.getCommunicationDetail(pubId, evtCode).also {
      refreshCommunicationsSnapshot(selectedYear)
    }
  }

  suspend fun acknowledgeCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val updated = restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
    refreshCommunicationsSnapshot(selectedYear)
    updated
  }

  suspend fun replyToCommunication(detail: CommunicationDetail, text: String): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val replyUrl = detail.replyUrl
      ?: detail.portalDetailUrl
      ?: throw ClassevivaNetworkException("URL risposta bacheca non disponibile.")
    portalClient.replyNoticeboard(replyUrl = replyUrl, text = text)
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching {
      restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
    }.getOrDefault(detail)
  }

  suspend fun joinCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val joinUrl = detail.joinUrl
      ?: detail.portalDetailUrl
      ?: throw ClassevivaNetworkException("URL adesione bacheca non disponibile.")
    portalClient.joinNoticeboard(joinUrl = joinUrl)
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching {
      restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
    }.getOrDefault(detail)
  }

  suspend fun uploadCommunicationFile(
    detail: CommunicationDetail,
    fileName: String,
    mimeType: String?,
    bytes: ByteArray,
  ): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val uploadUrl = detail.fileUploadUrl
      ?: detail.portalDetailUrl
      ?: throw ClassevivaNetworkException("URL upload bacheca non disponibile.")
    portalClient.uploadNoticeboard(
      uploadUrl = uploadUrl,
      attachment = AttachmentPayload(
        fileName = fileName,
        mimeType = mimeType,
        base64Content = Base64.getEncoder().encodeToString(bytes),
      ),
    )
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching {
      restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode)
    }.getOrDefault(detail)
  }

  suspend fun getNoteDetail(id: String, categoryCode: String): Result<NoteDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    restClient.getNoteDetail(id, categoryCode).also {
      refreshNotesSnapshot(selectedYear)
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
    android.util.Log.i("SyncCoordinator", "Avvio sincronizzazione per anno: ${selectedYear.id}")
    attachSession(session)
    syncStatus.value = SyncStatus(
      state = SyncState.SYNCING,
      lastSuccessfulSyncEpochMillis = syncStatus.value.lastSuccessfulSyncEpochMillis,
      message = "Sincronizzazione in corso",
    )

    val errors = mutableListOf<String>()
    val currentYear = schoolYearStore.currentSchoolYearRef()
    val yearStart = schoolYearStart(selectedYear)
    val yearEnd = schoolYearEnd(selectedYear)
    val isCurrentYear = selectedYear.id == currentYear.id
    val selectedSections = sections ?: allSections()

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
        filterHomeworksForYear(restClient.getHomeworks(), selectedYear)
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

    val now = System.currentTimeMillis()
    val next = if (errors.isEmpty()) {
      SyncStatus(
        state = SyncState.IDLE,
        lastSuccessfulSyncEpochMillis = now,
        message = if (force) "Sincronizzazione completa." else null,
      )
    } else {
      SyncStatus(
        state = SyncState.PARTIAL,
        lastSuccessfulSyncEpochMillis = syncStatus.value.lastSuccessfulSyncEpochMillis ?: now,
        message = "Sincronizzazione parziale: ${errors.distinct().joinToString()}",
      )
    }
    syncStatus.value = next
    return next
  }

  private suspend fun syncGrades(
    schoolYear: SchoolYearRef,
    errors: MutableList<String>,
    session: UserSession,
    fetch: suspend () -> List<Grade>,
  ) {
    runCatching {
      val grades = fetch()
      val entities = grades.map { grade ->
        GradeEntity(
          id = grade.id,
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
        )
      }
      gradeDao.deleteByYear(session.studentId, schoolYear.id)
      gradeDao.upsertAll(entities)
      storeYearScopedValue(GradesSection, schoolYear, grades)
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
      val existingById = agendaDao.getByYearOnce(session.studentId, schoolYear.id).associateBy { it.id }
      val now = System.currentTimeMillis()
      val entities = agenda.map { item ->
        val existing = existingById[item.id]
        AgendaItemEntity(
          id = item.id,
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
      agendaDao.deleteByYear(session.studentId, schoolYear.id)
      agendaDao.upsertAll(entities)
      storeYearScopedValue(AgendaSection, schoolYear, agenda)
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
      val localReadIds = communicationDao.getReadIds(session.studentId, schoolYear.id).toSet()
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
          read = comm.read || localReadIds.contains(comm.id),
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
    timetableTemplateStore.writeTemplate(
      schoolYear.id,
      predictiveTimetableUseCase.generateTimetableTemplate(preparedLessons),
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

  private fun filterGradesForYear(grades: List<Grade>, schoolYear: SchoolYearRef): List<Grade> {
    val start = schoolYearStart(schoolYear)
    val end = schoolYearEnd(schoolYear)
    return grades.filter { grade ->
      runCatching { LocalDate.parse(grade.date) }.getOrNull()?.let { date ->
        !date.isBefore(start) && !date.isAfter(end)
      } ?: true
    }
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
