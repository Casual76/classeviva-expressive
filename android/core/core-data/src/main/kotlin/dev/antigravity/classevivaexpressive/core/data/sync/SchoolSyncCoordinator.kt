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
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.AgendaDao
import dev.antigravity.classevivaexpressive.core.database.database.AgendaItemEntity
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceJustificationRequest
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmission
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmissionReceipt
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingJoinLink
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaGatewayClient
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.LoginResult
import dev.antigravity.classevivaexpressive.core.network.client.normalizeDocumentAsset
import java.time.LocalDate
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
  private val gatewayClient: ClassevivaGatewayClient,
  private val sessionStore: SessionStore,
  private val settingsStore: SettingsStore,
  private val schoolYearStore: SchoolYearStore,
  private val snapshotCacheDao: SnapshotCacheDao,
  private val gradeDao: GradeDao,
  private val agendaDao: AgendaDao,
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
    val receipt = gatewayClient.submitHomework(submission, selectedYear)
    refreshSchoolYear(
      selectedYear = selectedYear,
      force = true,
      refreshProfile = false,
      sections = setOf(HomeworkSection),
    )
    receipt
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
    val updated = gatewayClient.replyToNoticeboard(detail = detail, text = text, schoolYear = selectedYear)
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching { restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode) }.getOrDefault(updated)
  }

  suspend fun joinCommunication(detail: CommunicationDetail): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val updated = gatewayClient.joinNoticeboard(detail = detail, schoolYear = selectedYear)
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching { restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode) }.getOrDefault(updated)
  }

  suspend fun uploadCommunicationFile(
    detail: CommunicationDetail,
    fileName: String,
    mimeType: String?,
    bytes: ByteArray,
  ): Result<CommunicationDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val updated = gatewayClient.uploadNoticeboard(
      detail = detail,
      attachment = dev.antigravity.classevivaexpressive.core.domain.model.AttachmentPayload(
        fileName = fileName,
        mimeType = mimeType,
        base64Content = Base64.getEncoder().encodeToString(bytes),
      ),
      schoolYear = selectedYear,
    )
    runCatching { refreshCommunicationsSnapshot(selectedYear) }
    runCatching { restClient.getCommunicationDetail(detail.communication.pubId, detail.communication.evtCode) }.getOrDefault(updated)
  }

  suspend fun getNoteDetail(id: String, categoryCode: String): Result<NoteDetail> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    restClient.getNoteDetail(id, categoryCode).also {
      refreshNotesSnapshot(selectedYear)
    }
  }

  suspend fun openMaterial(item: dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem): Result<dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset> = runCatching {
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
    val updated = gatewayClient.justifyAbsence(effectiveRequest, selectedYear)
    storeYearScopedValue(AbsencesSection, selectedYear, updated)
    updated
  }

  suspend fun bookMeeting(slot: MeetingSlot): Result<MeetingBooking> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val booking = gatewayClient.bookMeeting(slot, selectedYear)
    refreshMeetingsSnapshot(selectedYear)
    booking
  }

  suspend fun cancelMeeting(booking: MeetingBooking): Result<List<MeetingBooking>> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    val updated = gatewayClient.cancelMeeting(booking.id, selectedYear)
    storeYearScopedValue(MeetingBookingsSection, selectedYear, updated)
    updated
  }

  suspend fun joinMeeting(booking: MeetingBooking): Result<MeetingJoinLink> = runCatching {
    val selectedYear = schoolYearStore.observeSelectedSchoolYear().first()
    gatewayClient.joinMeeting(booking.id, selectedYear)
  }

  private suspend fun refreshSchoolYear(
    selectedYear: SchoolYearRef,
    force: Boolean,
    refreshProfile: Boolean,
    sections: Set<String>? = null,
  ): SyncStatus {
    val session = activeSession ?: return SyncStatus(
      state = SyncState.ERROR,
      message = "Sessione assente.",
    )
    val settings = settingsStore.settings.first()
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
      syncGlobal(ProfileSection, errors) {
        if (settings.getFeatureMode(RegistroFeature.PROFILE) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getProfile(selectedYear)
        } else {
          restClient.getProfile()
        }
      }
    }

    if (selectedSections.contains(GradesSection)) {
      syncGrades(selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.GRADES) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getGrades(selectedYear)
        } else if (isCurrentYear) {
          restClient.getGrades()
        } else {
          emptyList<Grade>()
        }
      }
    }
    if (selectedSections.contains(PeriodsSection)) {
      syncYearScoped(PeriodsSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.PERIODS) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getPeriods(selectedYear)
        } else if (isCurrentYear) {
          restClient.getPeriods()
        } else {
          emptyList<Period>()
        }
      }
    }
    if (selectedSections.contains(SubjectsSection)) {
      syncYearScoped(SubjectsSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.SUBJECTS) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getSubjects(selectedYear)
        } else if (isCurrentYear) {
          restClient.getSubjects()
        } else {
          emptyList<Subject>()
        }
      }
    }
    if (selectedSections.contains(LessonsSection)) {
      syncYearScoped(LessonsSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.LESSONS) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getLessons(selectedYear)
        } else {
          restClient.getLessons(yearStart.toString(), yearEnd.toString())
        }
      }
    }
    if (selectedSections.contains(HomeworkSection)) {
      syncYearScoped(HomeworkSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.HOMEWORKS) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getHomeworks(selectedYear)
        } else if (isCurrentYear) {
          filterHomeworksForYear(restClient.getHomeworks(), selectedYear)
        } else {
          emptyList<Homework>()
        }
      }
    }
    if (selectedSections.contains(AgendaSection)) {
      syncAgenda(selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.AGENDA) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getAgenda(selectedYear)
        } else {
          restClient.getAgenda(yearStart.toString(), yearEnd.toString())
        }
      }
    }
    if (selectedSections.contains(AbsencesSection)) {
      syncYearScoped(AbsencesSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.ABSENCES) == FeatureCapabilityMode.GATEWAY) {
          restClient.getAbsences(yearStart.toString(), yearEnd.toString())
        } else {
          restClient.getAbsences(yearStart.toString(), yearEnd.toString())
        }
      }
    }
    if (selectedSections.contains(CommunicationsSection)) {
      syncYearScoped(CommunicationsSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.NOTICEBOARD) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getCommunications(selectedYear)
        } else if (isCurrentYear) {
          restClient.getCommunications()
        } else {
          emptyList<Communication>()
        }
      }
    }
    if (selectedSections.contains(NotesSection)) {
      syncYearScoped(NotesSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.NOTES) == FeatureCapabilityMode.GATEWAY) {
          emptyList<Note>()
        } else if (isCurrentYear) {
          restClient.getNotes()
        } else {
          emptyList<Note>()
        }
      }
    }
    if (selectedSections.contains(MaterialsSection)) {
      syncYearScoped(MaterialsSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.MATERIALS) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getMaterials(selectedYear)
        } else if (isCurrentYear) {
          restClient.getMaterials()
        } else {
          emptyList()
        }
      }
    }
    if (selectedSections.contains(DocumentsSection)) {
      syncYearScoped(DocumentsSection, selectedYear, errors) {
        if (settings.getFeatureMode(RegistroFeature.DOCUMENTS) == FeatureCapabilityMode.GATEWAY) {
          gatewayClient.getDocuments(selectedYear)
        } else if (isCurrentYear) {
          restClient.getDocuments()
        } else {
          emptyList<DocumentItem>()
        }
      }
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
      if (gatewayClient.isConfigured()) {
        runCatching { refreshMeetingsSnapshot(selectedYear) }.onFailure {
          errors += MeetingBookingsSection
          clearMeetings(selectedYear)
        }
      } else {
        clearMeetings(selectedYear)
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
    fetch: suspend () -> List<Grade>,
  ) {
    val session = activeSession ?: return
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
    fetch: suspend () -> List<AgendaItem>,
  ) {
    val session = activeSession ?: return
    runCatching {
      val agenda = fetch()
      val entities = agenda.map { item ->
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
          category = item.category.name,
          sharePayload = item.sharePayload,
        )
      }
      agendaDao.deleteByYear(session.studentId, schoolYear.id)
      agendaDao.upsertAll(entities)
      storeYearScopedValue(AgendaSection, schoolYear, agenda)
    }.onFailure {
      errors += AgendaSection
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
    storeYearScopedValue(
      CommunicationsSection,
      schoolYear,
      restClient.getCommunications(),
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
    val snapshot = gatewayClient.getMeetings(schoolYear)
    storeYearScopedValue(MeetingTeachersSection, schoolYear, snapshot.teachers)
    storeYearScopedValue(MeetingSlotsSection, schoolYear, snapshot.slots)
    storeYearScopedValue(MeetingBookingsSection, schoolYear, snapshot.bookings)
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
}
