package dev.antigravity.classevivaexpressive.core.data.repository

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.AgendaChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.AppNotificationChannels
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.GradesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.NotesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.TestChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.readNotificationRuntimeState
import dev.antigravity.classevivaexpressive.core.data.notifications.sendTestNotification
import dev.antigravity.classevivaexpressive.core.data.notifications.sendTestNotificationForChannel
import dev.antigravity.classevivaexpressive.core.database.database.AgendaDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.AbsenceDao
import dev.antigravity.classevivaexpressive.core.database.database.AttachmentCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.AttachmentCacheEntity
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationDao
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationEntity
import dev.antigravity.classevivaexpressive.core.database.database.MaterialDao
import dev.antigravity.classevivaexpressive.core.database.database.DocumentDao
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventDao
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventEntity
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordDao
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordEntity
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeDao
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SimulationDao
import dev.antigravity.classevivaexpressive.core.database.database.SimulatedGradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SubjectGoalDao
import dev.antigravity.classevivaexpressive.core.database.database.SubjectGoalEntity
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreDao
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreSnapshotEntity
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceJustificationRequest
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardStat
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeDistribution
import dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmission
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmissionReceipt
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingJoinLink
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SeenGradeState
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SimulatedGrade
import dev.antigravity.classevivaexpressive.core.domain.model.SimulationRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StatsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StatsSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComparison
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComponent
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectGoal
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectSummary
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.TimelinePoint
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import dev.antigravity.classevivaexpressive.core.network.client.ApiSessionManager

import dev.antigravity.classevivaexpressive.core.network.client.DevApiKey
import dev.antigravity.classevivaexpressive.core.network.client.UserAgent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SchoolAuthRepository @Inject constructor(
  private val sessionStore: SessionStore,
  private val syncCoordinator: SchoolSyncCoordinator,
  private val apiSessionManager: ApiSessionManager,
) : AuthRepository {
  override val session: StateFlow<UserSession?> = sessionStore.session

  override suspend fun restore(): UserSession? {
    val restored = apiSessionManager.restoreValidSession()
    syncCoordinator.attachSession(restored)
    return restored
  }

  override suspend fun login(username: String, password: String): Result<UserSession> = runCatching {
    syncCoordinator.login(username, password)
  }.onFailure {
    sessionStore.clear()
    syncCoordinator.attachSession(null)
  }

  override suspend fun logout() {
    sessionStore.clear()
    syncCoordinator.attachSession(null)
    syncCoordinator.clearPortalSession()
  }
}

@Singleton
class SchoolSettingsRepository @Inject constructor(
  private val settingsStore: SettingsStore,
  @ApplicationContext private val context: Context,
) : SettingsRepository {
  private val notificationRuntimeState = MutableStateFlow(readNotificationRuntimeState(context))

  init {
    AppNotificationChannels.create(context)
  }

  override fun observeSettings(): Flow<AppSettings> = settingsStore.settings
  override fun observeNotificationRuntimeState(): Flow<NotificationRuntimeState> = notificationRuntimeState
  override suspend fun updateThemeMode(mode: ThemeMode) = settingsStore.update { it.copy(themeMode = mode) }
  override suspend fun updateAccentMode(mode: AccentMode) = settingsStore.update { it.copy(accentMode = mode) }
  override suspend fun updateCustomAccent(name: String) = settingsStore.update { it.copy(customAccentName = name) }
  override suspend fun setDynamicColorEnabled(enabled: Boolean) = settingsStore.update { it.copy(dynamicColorEnabled = enabled) }
  override suspend fun setAmoledEnabled(enabled: Boolean) = settingsStore.update { it.copy(amoledEnabled = enabled) }
  override suspend fun setNotificationsEnabled(enabled: Boolean) = settingsStore.update {
    it.copy(notificationPreferences = it.notificationPreferences.copy(enabled = enabled))
  }
  override suspend fun setPeriodicSyncEnabled(enabled: Boolean) = settingsStore.update { it.copy(periodicSyncEnabled = enabled) }

  override suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
    settingsStore.update { it.copy(notificationPreferences = preferences) }
  }

  override suspend fun setNotificationCategoryEnabled(channelId: String, enabled: Boolean) {
    settingsStore.update { current ->
      current.copy(
        notificationPreferences = when (channelId) {
          HomeworkChannelId -> current.notificationPreferences.copy(homework = enabled)
          CommunicationsChannelId -> current.notificationPreferences.copy(communications = enabled)
          AbsencesChannelId -> current.notificationPreferences.copy(absences = enabled)
          GradesChannelId -> current.notificationPreferences.copy(grades = enabled)
          AgendaChannelId -> current.notificationPreferences.copy(agenda = enabled)
          NotesChannelId -> current.notificationPreferences.copy(notes = enabled)
          TestChannelId -> current.notificationPreferences.copy(test = enabled)
          else -> current.notificationPreferences
        },
      )
    }
  }

  override suspend fun refreshNotificationRuntimeState() {
    notificationRuntimeState.value = readNotificationRuntimeState(context)
  }

  override suspend fun sendTestNotification(): Result<Unit> {
    val result = sendTestNotification(context, settingsStore.settings.first().notificationPreferences)
    refreshNotificationRuntimeState()
    return result
  }

  override suspend fun sendTestNotificationForChannel(channelId: String): Result<Unit> {
    val result = sendTestNotificationForChannel(context, channelId, settingsStore.settings.first().notificationPreferences)
    refreshNotificationRuntimeState()
    return result
  }

  override suspend fun updateGatewayBaseUrl(url: String) {
    settingsStore.update { it.copy(networkConfig = it.networkConfig.copy(gatewayBaseUrl = url)) }
  }
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SchoolDataRepository @Inject constructor(
  private val json: Json,
  private val snapshotCacheDao: SnapshotCacheDao,
  private val customEventDao: CustomEventDao,
  private val simulationDao: SimulationDao,
  private val studentScoreDao: StudentScoreDao,
  private val downloadRecordDao: DownloadRecordDao,
  private val seenGradeDao: SeenGradeDao,
  private val subjectGoalDao: SubjectGoalDao,
  private val gradeDao: GradeDao,
  private val agendaDao: AgendaDao,
  private val absenceDao: AbsenceDao,
  private val communicationDao: CommunicationDao,
  private val attachmentCacheDao: AttachmentCacheDao,
  private val materialDao: MaterialDao,
  private val documentDao: DocumentDao,
  private val syncCoordinator: SchoolSyncCoordinator,
  private val downloadManager: DownloadManager,
  private val sessionStore: SessionStore,
  private val schoolYearStore: SchoolYearStore,
  private val timetableTemplateStore: TimetableTemplateStore,
  private val capabilityResolver: CapabilityResolver,
  private val predictiveTimetableUseCase: PredictiveTimetableUseCase,
  @ApplicationContext private val context: Context,
) : DashboardRepository,
  GradesRepository,
  AgendaRepository,
  LessonsRepository,
  HomeworkRepository,
  CommunicationsRepository,
  MaterialsRepository,
  DocumentsRepository,
  AbsencesRepository,
  MeetingsRepository,
  SchoolYearRepository,
  StatsRepository,
  StudentScoreRepository,
  SimulationRepository {

  override fun observeDashboard(): Flow<DashboardSnapshot> {
    val academicFlow = combine(
      observeGrades(),
      observeAgenda(),
      observeAbsences(),
      observeLessons(),
      observeSeenGradeStates(),
    ) { grades, agenda, absences, lessons, seenGradeStates ->
      DashboardAcademicState(
        grades = grades,
        agenda = agenda,
        absences = absences,
        lessons = lessons,
        seenGradeStates = seenGradeStates,
      )
    }
    val schoolFlow = combine(
      observeCommunications(),
      observeNotes(),
      observeDocuments(),
    ) { communications, notes, documents ->
      DashboardSchoolState(
        communications = communications,
        notes = notes,
        documents = documents,
      )
    }
    return combine(
      observeGlobalValue(ProfileSection, StudentProfile()),
      academicFlow,
      schoolFlow,
      syncCoordinator.syncStatus,
    ) { profile, academic, school, syncStatus ->
      val average = academic.grades.mapNotNull { it.numericValue }.takeIf { it.isNotEmpty() }?.average()
      val today = java.time.LocalDate.now()
      val scheduleForToday = predictiveTimetableUseCase.getScheduleForDate(today, academic.lessons)
      val todayLessonsList = scheduleForToday.map { slot ->
        dev.antigravity.classevivaexpressive.core.domain.model.Lesson(
            id = "${slot.time}_${slot.subject}",
            subject = slot.subject,
            date = today.toString(),
            time = slot.time.toString(),
            durationMinutes = slot.durationMinutes,
            topic = slot.topic,
            teacher = slot.teacher,
            room = slot.room,
            endTime = slot.endTime?.formatTime(),
        )
      }
      val unseenGradeIds = academic.seenGradeStates.map { it.gradeId }.toSet()
      val recentGrades = academic.grades.sortedByDescending { it.date }
      val unseenGrades = recentGrades.filterNot { unseenGradeIds.contains(it.id) }
      val unreadCommunications = school.communications.filter { !it.read }.sortedByDescending { it.date }
      val upcoming = academic.agenda
        .filter { it.category != AgendaCategory.LESSON && it.date >= todayIso() }
        .sortedBy { "${it.date}-${it.time.orEmpty()}" }
      DashboardSnapshot(
        profile = profile,
        headline = "",
        subheadline = "Lezioni, voti da aprire e comunicazioni da leggere restano al centro.",
        averageLabel = average?.let { "%.1f".format(it) } ?: "--",
        averageNumeric = average,
        stats = listOf(
          DashboardStat("lessons", "Lezioni", todayLessonsList.size.toString(), "Lezioni visibili oggi"),
          DashboardStat("grades", "Voti non visti", unseenGrades.size.toString(), "Valutazioni da aprire"),
          DashboardStat("messages", "Comunicazioni", unreadCommunications.size.toString(), "Messaggi non letti"),
        ),
        todayLessons = todayLessonsList,
        recentGrades = recentGrades.take(6),
        unseenGrades = unseenGrades.take(6),
        upcomingItems = upcoming.take(8),
        unreadCommunications = unreadCommunications,
        highlightedNotes = school.notes.filter { !it.read }.take(3),
        recentAbsences = academic.absences.sortedByDescending { it.date }.take(3),
        schoolDocuments = school.documents.take(2),
        syncStatus = syncStatus,
      )
    }
  }

  override suspend fun refreshDashboard(force: Boolean): Result<DashboardSnapshot> = runCatching {
    syncCoordinator.refreshAll(force)
    observeDashboard().firstValue()
  }

  override fun observeGrades(): Flow<List<Grade>> {
    return combine(
      sessionStore.session,
      schoolYearStore.observeSelectedSchoolYear(),
    ) { session, schoolYear ->
      session to schoolYear
    }.flatMapLatest { (session, schoolYear) ->
      val studentId = session?.studentId ?: return@flatMapLatest flowOf(emptyList<Grade>())
      gradeDao.observeByYear(studentId, schoolYear.id).map { entities ->
        entities.map { entity ->
          Grade(
            id = entity.id,
            subject = entity.subject,
            valueLabel = entity.valueLabel,
            numericValue = entity.numericValue,
            description = entity.description,
            date = entity.date,
            type = entity.type,
            weight = entity.weight,
            notes = entity.notes,
            period = entity.period,
            periodCode = entity.periodCode,
            teacher = entity.teacher,
            color = entity.color,
          )
        }
      }
    }
  }
  override fun observePeriods(): Flow<List<Period>> = observeYearScopedValue(PeriodsSection, emptyList())
  override fun observeSubjects(): Flow<List<Subject>> = observeYearScopedValue(SubjectsSection, emptyList())
  override fun observeSeenGradeStates(): Flow<List<SeenGradeState>> {
    return sessionStore.session.flatMapLatest { session ->
      val studentId = session?.studentId
      if (studentId.isNullOrBlank()) {
        flowOf(emptyList())
      } else {
        seenGradeDao.observeByStudent(studentId).map { entities ->
          entities.map { entity ->
            SeenGradeState(
              studentId = entity.studentId,
              gradeId = entity.gradeId,
              seenAtEpochMillis = entity.seenAtEpochMillis,
            )
          }
        }
      }
    }
  }

  override fun observeSubjectGoals(): Flow<List<SubjectGoal>> {
    return sessionStore.session.flatMapLatest { session ->
      val studentId = session?.studentId
      if (studentId.isNullOrBlank()) {
        flowOf(emptyList())
      } else {
        subjectGoalDao.observeByStudent(studentId).map { entities ->
          entities.map { entity ->
            SubjectGoal(
              studentId = entity.studentId,
              subject = entity.subject,
              periodCode = entity.periodCode,
              targetAverage = entity.targetAverage,
            )
          }
        }
      }
    }
  }

  override suspend fun refreshGrades(force: Boolean): Result<List<Grade>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeGrades().firstValue()
  }

  override suspend fun markGradeSeen(gradeId: String) {
    val session = sessionStore.readCurrentSession() ?: return
    seenGradeDao.upsert(
      SeenGradeEntity(
        id = "${session.studentId}::$gradeId",
        studentId = session.studentId,
        gradeId = gradeId,
        seenAtEpochMillis = System.currentTimeMillis(),
      ),
    )
  }

  override suspend fun saveSubjectGoal(subject: String, periodCode: String?, targetAverage: Double) {
    val session = sessionStore.readCurrentSession() ?: return
    subjectGoalDao.upsert(
      SubjectGoalEntity(
        id = "${session.studentId}::$subject::${periodCode.orEmpty()}",
        studentId = session.studentId,
        subject = subject,
        periodCode = periodCode,
        targetAverage = targetAverage,
        updatedAtEpochMillis = System.currentTimeMillis(),
      ),
    )
  }

  override suspend fun removeSubjectGoal(subject: String, periodCode: String?) {
    val session = sessionStore.readCurrentSession() ?: return
    subjectGoalDao.delete(session.studentId, subject, periodCode)
  }

  override fun observeStats(): Flow<StatsSnapshot> {
    return combine(observeGrades(), observeAbsences(), observeAgenda()) { grades, absences, agenda ->
      buildStats(grades, absences, agenda)
    }
  }

  override suspend fun refreshStats(force: Boolean): Result<StatsSnapshot> = runCatching {
    syncCoordinator.refreshAll(force)
    observeStats().firstValue()
  }

  override fun observeAgenda(): Flow<List<AgendaItem>> {
    return combine(
      observeDbAgenda(),
      observeHomeworks(),
      observeSelectedSchoolYear(),
      customEventDao.observeAll().map { entities ->
        entities.map { json.decodeFromString<CustomEvent>(it.payload) }
      },
    ) { agenda, homeworks, schoolYear, customEvents ->
      (agenda + homeworks.map { homework ->
        AgendaItem(
          id = "homework-${homework.id}",
          title = homework.description,
          subtitle = homework.subject,
          date = homework.dueDate,
          time = null,
          detail = homework.notes,
          subject = homework.subject,
          category = AgendaCategory.HOMEWORK,
          sharePayload = "${homework.subject} - ${homework.description} - ${homework.dueDate}",
        )
      } + customEvents.filter { event ->
        isInSchoolYear(event.date, schoolYear)
      }.map { event ->
        AgendaItem(
          id = event.id,
          title = event.title,
          subtitle = event.subject,
          date = event.date,
          time = event.time,
          detail = event.description,
          subject = event.subject,
          category = event.category,
          sharePayload = "${event.title} - ${event.date} ${event.time.orEmpty()}",
          createdAt = event.createdAt,
        )
      }).sortedBy { "${it.date}-${it.time.orEmpty()}" }
    }
  }

  private fun observeDbAgenda(): Flow<List<AgendaItem>> {
    return combine(
      sessionStore.session,
      schoolYearStore.observeSelectedSchoolYear(),
    ) { session, schoolYear ->
      session to schoolYear
    }.flatMapLatest { (session, schoolYear) ->
      val studentId = session?.studentId ?: return@flatMapLatest flowOf(emptyList<AgendaItem>())
      agendaDao.observeByYear(studentId, schoolYear.id).map { entities ->
        entities.map { entity ->
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
      }
    }
  }

  override fun observeCustomEvents(): Flow<List<CustomEvent>> = customEventDao.observeAll().map { entities ->
    entities.map { entity ->
      val decoded = json.decodeFromString<CustomEvent>(entity.payload)
      decoded.copy(createdAt = decoded.createdAt ?: entity.createdAt)
    }
  }

  override suspend fun addCustomEvent(event: CustomEvent) {
    val createdAt = event.createdAt ?: java.time.LocalDateTime.now().toString().take(16)
    customEventDao.upsert(
      CustomEventEntity(
        id = event.id,
        payload = json.encodeToString(event.copy(createdAt = createdAt)),
        date = event.date,
        time = event.time,
        createdAt = createdAt,
      ),
    )
  }

  override suspend fun removeCustomEvent(id: String) {
    customEventDao.deleteById(id)
  }

  override suspend fun refreshAgenda(force: Boolean): Result<List<AgendaItem>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeAgenda().firstValue()
  }

  override fun observeLessons(): Flow<List<Lesson>> {
    return combine(
      observeYearScopedValue(LessonsSection, emptyList<Lesson>()),
      observeDbAgenda(),
    ) { lessons, agenda ->
      buildLessonsWithFallback(lessons, agenda)
    }
  }

  override fun observeTimetableTemplate(): Flow<TimetableTemplate> {
    val storedFlow = schoolYearStore.observeSelectedSchoolYear().flatMapLatest { schoolYear ->
      timetableTemplateStore.observeTemplate(schoolYear.id)
    }
    return combine(storedFlow, observeLessons()) { stored, lessons ->
      if (stored.slots.isNotEmpty()) stored
      else predictiveTimetableUseCase.generateTimetableTemplate(lessons)
    }
  }
  override suspend fun refreshLessons(force: Boolean): Result<List<Lesson>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeLessons().firstValue()
  }

  override fun observeHomeworks(): Flow<List<Homework>> = observeYearScopedValue(HomeworkSection, emptyList())
  override suspend fun refreshHomeworks(force: Boolean): Result<List<Homework>> = runCatching {
    syncCoordinator.refreshHomeworks(force)
  }

  override suspend fun getHomeworkDetail(id: String): Result<HomeworkDetail> = runCatching {
    val homework = observeHomeworks().firstValue().firstOrNull { it.id == id }
      ?: syncCoordinator.refreshHomeworks(force = false).firstOrNull { it.id == id }
      ?: error("Compito non trovato.")
    HomeworkDetail(
      homework = homework,
      fullText = listOfNotNull(homework.description, homework.notes).joinToString("\n\n").ifBlank { homework.description },
      assignedDate = homework.dueDate,
      capability = capabilityResolver.observeCapability(RegistroFeature.HOMEWORKS).first(),
    )
  }

  override suspend fun submitHomework(submission: HomeworkSubmission): Result<HomeworkSubmissionReceipt> =
    syncCoordinator.submitHomework(submission)

  override suspend fun queueAttachmentDownload(attachment: RemoteAttachment): Result<Long> = queueDownload(attachment)

  override fun observeCommunications(): Flow<List<Communication>> {
    return combine(
      sessionStore.session,
      schoolYearStore.observeSelectedSchoolYear(),
    ) { session, schoolYear ->
      session to schoolYear
    }.flatMapLatest { (session, schoolYear) ->
      val studentId = session?.studentId ?: return@flatMapLatest flowOf(emptyList<Communication>())
      communicationDao.observeByYear(studentId, schoolYear.id).map { entities ->
        entities.map { entity -> entity.toCommunication(json) }
      }
    }
  }

  override fun observeNotes(): Flow<List<Note>> = observeYearScopedValue(NotesSection, emptyList())
  override suspend fun refreshCommunications(force: Boolean): Result<List<Communication>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeCommunications().firstValue()
  }

  override suspend fun markAllAsRead(): Result<Unit> = runCatching {
    if (sessionStore.session.value == null) return@runCatching
    syncCoordinator.markAllCommunicationsReadRemotely().getOrThrow()
  }

  override suspend fun markCommunicationRead(id: String): Result<Unit> = runCatching {
    val entity = communicationDao.getById(id)
    if (entity != null && entity.pubId.isNotBlank() && entity.evtCode.isNotBlank()) {
      // Best-effort remote mark — markNoticeboardRead now swallows 400 (already read).
      runCatching { syncCoordinator.markCommunicationReadRemotely(id, entity.pubId, entity.evtCode) }
    }
    // Always persist locally regardless of remote outcome.
    communicationDao.markRead(id)
  }

  override suspend fun getCommunicationDetail(pubId: String, evtCode: String): Result<CommunicationDetail> {
    return syncCoordinator.getCommunicationDetail(pubId, evtCode)
  }

  override suspend fun getNoteDetail(id: String, categoryCode: String): Result<NoteDetail> {
    return syncCoordinator.getNoteDetail(id, categoryCode)
  }

  override suspend fun queueDownload(attachment: RemoteAttachment): Result<Long> = runCatching {
    require(!attachment.url.isNullOrBlank()) { "Nessun URL disponibile per l'allegato." }
    queueDownloadInternal(attachment.url!!, attachment.name, attachment.mimeType)
  }

  override suspend fun resolveAttachmentLocalPath(attachment: RemoteAttachment): Result<String> = runCatching {
    require(!attachment.url.isNullOrBlank()) { "Nessun URL disponibile per l'allegato." }
    val url = attachment.url!!
    val urlKey = url.trim().take(250)
    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    val cacheDir = java.io.File(context.filesDir, "attachment_cache")

    // Return cached file if still valid.
    val cached = attachmentCacheDao.getByUrlKey(urlKey)
    if (cached != null) {
      val file = java.io.File(cached.localPath)
      if (file.exists() && System.currentTimeMillis() - cached.lastAccessedMs < thirtyDaysMs) {
        attachmentCacheDao.upsert(cached.copy(lastAccessedMs = System.currentTimeMillis()))
        cleanupExpiredAttachments(cacheDir, thirtyDaysMs)
        return@runCatching cached.localPath
      }
      // File missing or expired — evict and re-download.
      runCatching { java.io.File(cached.localPath).delete() }
      attachmentCacheDao.deleteByUrlKey(urlKey)
    }

    // Download with auth headers via the RestClient.
    val bytes = syncCoordinator.downloadAttachmentBytes(url)
    val safeBase = sanitizeFileName(attachment.name.ifBlank { "allegato" })
    val hashSuffix = url.hashCode().let { if (it < 0) "m${-it}" else "$it" }
    val fileName = run {
      val dot = safeBase.lastIndexOf('.')
      if (dot > 0) "${safeBase.substring(0, dot)}_$hashSuffix${safeBase.substring(dot)}"
      else "${safeBase}_$hashSuffix"
    }
    cacheDir.mkdirs()
    val file = java.io.File(cacheDir, fileName)
    file.writeBytes(bytes)

    val now = System.currentTimeMillis()
    attachmentCacheDao.upsert(
      AttachmentCacheEntity(
        urlKey = urlKey,
        sourceUrl = url,
        localPath = file.absolutePath,
        fileName = fileName,
        mimeType = attachment.mimeType,
        downloadedAtMs = now,
        lastAccessedMs = now,
      )
    )
    cleanupExpiredAttachments(cacheDir, thirtyDaysMs)
    file.absolutePath
  }

  private suspend fun cleanupExpiredAttachments(cacheDir: java.io.File, thirtyDaysMs: Long) {
    val cutoff = System.currentTimeMillis() - thirtyDaysMs
    val expired = attachmentCacheDao.getExpiredBefore(cutoff)
    for (entry in expired) {
      runCatching { java.io.File(entry.localPath).delete() }
      attachmentCacheDao.deleteByUrlKey(entry.urlKey)
    }
  }

  override suspend fun acknowledgeCommunication(detail: CommunicationDetail): Result<CommunicationDetail> {
    return syncCoordinator.acknowledgeCommunication(detail)
  }

  override suspend fun replyToCommunication(detail: CommunicationDetail, text: String): Result<CommunicationDetail> {
    return syncCoordinator.replyToCommunication(detail, text)
  }

  override suspend fun joinCommunication(detail: CommunicationDetail): Result<CommunicationDetail> {
    return syncCoordinator.joinCommunication(detail)
  }

  override suspend fun uploadCommunicationFile(
    detail: CommunicationDetail,
    fileName: String,
    mimeType: String?,
    bytes: ByteArray,
  ): Result<CommunicationDetail> {
    return syncCoordinator.uploadCommunicationFile(detail, fileName, mimeType, bytes)
  }

  override fun observeMaterials(): Flow<List<MaterialItem>> {
    return combine(
      sessionStore.session,
      schoolYearStore.observeSelectedSchoolYear(),
    ) { session, schoolYear ->
      session to schoolYear
    }.flatMapLatest { (session, schoolYear) ->
      val studentId = session?.studentId ?: return@flatMapLatest flowOf(emptyList<MaterialItem>())
      materialDao.observeByYear(studentId, schoolYear.id).map { entities ->
        entities.map { entity ->
          MaterialItem(
            id = entity.id,
            teacherId = entity.teacherId,
            teacherName = entity.teacherName,
            folderId = entity.folderId,
            folderName = entity.folderName,
            title = entity.title,
            objectId = entity.objectId,
            objectType = entity.objectType,
            sharedAt = entity.sharedAt,
            capabilityState = json.decodeFromString(entity.capabilityState),
            attachments = json.decodeFromString(entity.attachments),
          )
        }
      }
    }
  }

  override suspend fun refreshMaterials(force: Boolean): Result<List<MaterialItem>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeMaterials().firstValue()
  }

  override suspend fun openAsset(item: MaterialItem): Result<MaterialAsset> = syncCoordinator.openMaterial(item)

  override fun observeDocuments(): Flow<List<DocumentItem>> {
    return combine(
      sessionStore.session,
      schoolYearStore.observeSelectedSchoolYear(),
    ) { session, schoolYear ->
      session to schoolYear
    }.flatMapLatest { (session, schoolYear) ->
      val studentId = session?.studentId ?: return@flatMapLatest flowOf(emptyList<DocumentItem>())
      documentDao.observeByYear(studentId, schoolYear.id).map { entities ->
        entities.map { entity ->
          DocumentItem(
            id = entity.id,
            title = entity.title,
            detail = entity.detail,
            viewUrl = entity.viewUrl,
            confirmUrl = entity.confirmUrl,
            capabilityState = json.decodeFromString(entity.capabilityState),
          )
        }
      }
    }
  }

  override fun observeSchoolbooks(): Flow<List<SchoolbookCourse>> = observeYearScopedValue(SchoolbooksSection, emptyList())
  override suspend fun refreshDocuments(force: Boolean): Result<List<DocumentItem>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeDocuments().firstValue()
  }

  override suspend fun openDocument(document: DocumentItem): Result<DocumentAsset> = syncCoordinator.openDocument(document)

  override suspend fun queueDownload(document: DocumentItem): Result<Long> = runCatching {
    val url = document.viewUrl ?: document.confirmUrl ?: error("Il documento non ha un URL apribile.")
    queueDownloadInternal(url, document.title, null)
  }

  override fun observeAbsences(): Flow<List<AbsenceRecord>> {
    return combine(
      sessionStore.session,
      schoolYearStore.observeSelectedSchoolYear(),
    ) { session, schoolYear ->
      session to schoolYear
    }.flatMapLatest { (session, schoolYear) ->
      val studentId = session?.studentId ?: return@flatMapLatest flowOf(emptyList<AbsenceRecord>())
      absenceDao.observeByYear(studentId, schoolYear.id).map { entities ->
        entities.map { entity ->
          AbsenceRecord(
            id = entity.id,
            date = entity.date,
            type = AbsenceType.valueOf(entity.type),
            hours = entity.hours,
            justified = entity.justified,
            canJustify = entity.canJustify,
            justificationDate = entity.justificationDate,
            justificationReason = entity.justificationReason,
            justifyUrl = entity.justifyUrl,
            detailUrl = entity.detailUrl,
          )
        }
      }
    }
  }
  override suspend fun refreshAbsences(force: Boolean): Result<List<AbsenceRecord>> {
    return syncCoordinator.refreshAbsences(force)
  }

  override suspend fun justifyAbsence(
    record: AbsenceRecord,
    reason: String?,
    request: AbsenceJustificationRequest,
  ): Result<List<AbsenceRecord>> {
    return syncCoordinator.justifyAbsence(
      record = record,
      reason = reason,
      request = request.copy(
        absenceId = record.id,
        reasonText = request.reasonText ?: reason,
        justifyUrl = request.justifyUrl ?: record.justifyUrl,
        detailUrl = request.detailUrl ?: record.detailUrl,
      ),
    )
  }

  override fun observeMeetingTeachers(): Flow<List<MeetingTeacher>> = observeYearScopedValue(MeetingTeachersSection, emptyList())
  override fun observeMeetingSlots(): Flow<List<MeetingSlot>> = observeYearScopedValue(MeetingSlotsSection, emptyList())
  override fun observeMeetingBookings(): Flow<List<MeetingBooking>> = observeYearScopedValue(MeetingBookingsSection, emptyList())

  override suspend fun refreshMeetings(force: Boolean): Result<List<MeetingBooking>> = runCatching {
    syncCoordinator.refreshMeetings(force)
  }

  override suspend fun bookMeeting(slot: MeetingSlot): Result<MeetingBooking> {
    return syncCoordinator.bookMeeting(slot)
  }

  override suspend fun cancelMeeting(booking: MeetingBooking): Result<List<MeetingBooking>> {
    return syncCoordinator.cancelMeeting(booking)
  }

  override suspend fun joinMeeting(booking: MeetingBooking): Result<MeetingJoinLink> {
    return syncCoordinator.joinMeeting(booking)
  }

  override fun getPortalMeetingsUrl(): String = dev.antigravity.classevivaexpressive.core.network.client.PortalMeetingsUrl

  override suspend fun getPortalSessionCookies() = syncCoordinator.getPortalSessionCookies()

  override fun observeSelectedSchoolYear(): Flow<SchoolYearRef> = schoolYearStore.observeSelectedSchoolYear()
  override fun observeAvailableSchoolYears(): Flow<List<SchoolYearRef>> = schoolYearStore.observeAvailableSchoolYears()
  override suspend fun selectSchoolYear(year: SchoolYearRef) = schoolYearStore.selectSchoolYear(year)

  override fun observeCurrentScore(): Flow<StudentScoreSnapshot?> {
    return combine(observeStats(), observeAbsences(), studentScoreDao.observeLatest()) { stats, absences, cached ->
      cached?.let { json.decodeFromString<StudentScoreSnapshot>(it.payload) } ?: computeStudentScore(stats, absences)
    }
  }

  override fun observeSnapshots(): Flow<List<StudentScoreSnapshot>> = studentScoreDao.observeAll().map { entries ->
    entries.map { json.decodeFromString<StudentScoreSnapshot>(it.payload) }
  }

  override suspend fun refreshStudentScore(force: Boolean): Result<StudentScoreSnapshot> = runCatching {
    syncCoordinator.refreshAll(force)
    val snapshot = computeStudentScore(observeStats().firstValue(), observeAbsences().firstValue())
    val id = "score-${snapshot.computedAtEpochMillis}"
    studentScoreDao.upsert(StudentScoreSnapshotEntity(id, json.encodeToString(snapshot), snapshot.computedAtEpochMillis))
    snapshot
  }

  override suspend fun exportCurrentPayload(): Result<String> = runCatching {
    val current = observeCurrentScore().firstValue() ?: computeStudentScore(observeStats().firstValue(), observeAbsences().firstValue())
    Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(current).encodeToByteArray())
  }

  override suspend fun importPayload(payload: String): Result<StudentScoreComparison> = runCatching {
    val decoded = runCatching {
      val raw = Base64.getUrlDecoder().decode(payload).decodeToString()
      json.decodeFromString<StudentScoreSnapshot>(raw)
    }.getOrElse { json.decodeFromString<StudentScoreSnapshot>(payload) }
    val current = observeCurrentScore().firstValue() ?: computeStudentScore(observeStats().firstValue(), observeAbsences().firstValue())
    StudentScoreComparison(current = current, imported = decoded, difference = current.score - decoded.score)
  }

  override fun observeSimulation(): Flow<GradeSimulationSummary> {
    return combine(observeGrades(), simulationDao.observeAll()) { grades, simulations ->
      val simulated = simulations.map { json.decodeFromString<SimulatedGrade>(it.payload) }
      val realAverage = grades.mapNotNull { it.numericValue }.takeIf { it.isNotEmpty() }?.average()
      val mergedValues = grades.mapNotNull { it.numericValue } + simulated.map { it.value }
      val simulatedAverage = mergedValues.takeIf { it.isNotEmpty() }?.average()
      GradeSimulationSummary(
        realAverage = realAverage,
        simulatedAverage = simulatedAverage,
        delta = (simulatedAverage ?: realAverage ?: 0.0) - (realAverage ?: 0.0),
        grades = simulated,
      )
    }
  }

  override suspend fun addSimulatedGrade(grade: SimulatedGrade) {
    simulationDao.upsert(SimulatedGradeEntity(grade.id, json.encodeToString(grade), grade.subject, grade.date))
  }

  override suspend fun removeSimulatedGrade(id: String) {
    simulationDao.deleteById(id)
  }

  override suspend fun clearSimulation() {
    simulationDao.clearAll()
  }

  private suspend fun queueDownloadInternal(url: String, title: String, mimeType: String?): Long {
    val request = DownloadManager.Request(url.toUri())
      .setTitle(title)
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      .setMimeType(mimeType)
      .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, sanitizeFileName(title))
      .addRequestHeader("User-Agent", dev.antigravity.classevivaexpressive.core.network.client.UserAgent)
      .addRequestHeader("Z-Dev-ApiKey", dev.antigravity.classevivaexpressive.core.network.client.DevApiKey)
    syncCoordinator.authToken()?.let { token ->
      request.addRequestHeader("Z-Auth-Token", token)
    }
    val downloadId = downloadManager.enqueue(request)
    downloadRecordDao.upsert(
      DownloadRecordEntity(
        id = downloadId.toString(),
        sourceUrl = url,
        displayName = title,
        mimeType = mimeType,
        status = "QUEUED",
        updatedAtEpochMillis = System.currentTimeMillis(),
      ),
    )
    return downloadId
  }

  private inline fun <reified T> observeGlobalValue(key: String, default: T): Flow<T> {
    return snapshotCacheDao.observeByKey(key).map { entity ->
      entity?.payload?.let { runCatching { json.decodeFromString<T>(it) }.getOrDefault(default) } ?: default
    }
  }

  private inline fun <reified T> observeYearScopedValue(section: String, default: T): Flow<T> {
    return schoolYearStore.observeSelectedSchoolYear().flatMapLatest { schoolYear ->
      snapshotCacheDao.observeByKey(yearScopedCacheKey(section, schoolYear)).map { entity ->
        entity?.payload?.let { runCatching { json.decodeFromString<T>(it) }.getOrDefault(default) } ?: default
      }
    }
  }

  private fun todayIso(): String = java.time.LocalDate.now().toString()

  private fun isInSchoolYear(date: String, schoolYear: SchoolYearRef): Boolean {
    val parsed = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return false
    val start = java.time.LocalDate.of(schoolYear.startYear, 9, 1)
    val end = java.time.LocalDate.of(schoolYear.endYear, 8, 31)
    return !parsed.isBefore(start) && !parsed.isAfter(end)
  }

  private fun sanitizeFileName(source: String): String {
    return source.replace(Regex("[^a-zA-Z0-9._-]+"), "_").ifBlank { "download.bin" }
  }
}

private fun buildStats(grades: List<Grade>, absences: List<AbsenceRecord>, agenda: List<AgendaItem>): StatsSnapshot {
  val numericValues = grades.mapNotNull { it.numericValue }
  val subjectSummaries = grades.groupBy { it.subject }.map { (subject, items) ->
    val values = items.mapNotNull { it.numericValue }
    SubjectSummary(
      subject = subject,
      average = values.takeIf { it.isNotEmpty() }?.average(),
      teacherLabel = items.firstOrNull()?.teacher.orEmpty(),
      count = items.size,
      recentValues = values.takeLast(4),
      typeBreakdown = items.groupBy { it.type }.entries.joinToString(" - ") { "${it.value.size} ${it.key}" },
    )
  }.sortedBy { it.subject }
  val timeline = grades.sortedBy { it.date }.mapNotNull { grade ->
    grade.numericValue?.let { TimelinePoint(label = grade.subject.take(3).uppercase(), value = it) }
  }.takeLast(8)
  return StatsSnapshot(
    overallAverage = numericValues.takeIf { it.isNotEmpty() }?.average(),
    subjectSummaries = subjectSummaries,
    gradeDistribution = GradeDistribution(
      insufficient = numericValues.count { it < 6.0 },
      sufficient = numericValues.count { it in 6.0..<7.0 },
      good = numericValues.count { it in 7.0..<8.0 },
      veryGood = numericValues.count { it in 8.0..<9.0 },
      excellent = numericValues.count { it >= 9.0 },
    ),
    gradeTrend = timeline,
    absenceBreakdown = mapOf(
      "Assenze" to absences.count { it.type.name == "ABSENCE" },
      "Ritardi" to absences.count { it.type.name == "LATE" },
      "Uscite" to absences.count { it.type.name == "EXIT" },
    ),
    workloadBreakdown = agenda.groupingBy { it.category.name }.eachCount(),
  )
}

private data class DashboardAcademicState(
  val grades: List<Grade>,
  val agenda: List<AgendaItem>,
  val absences: List<AbsenceRecord>,
  val lessons: List<Lesson>,
  val seenGradeStates: List<SeenGradeState>,
)

private data class DashboardSchoolState(
  val communications: List<Communication>,
  val notes: List<Note>,
  val documents: List<DocumentItem>,
)

internal fun buildLessonsWithFallback(
  lessons: List<Lesson>,
  agenda: List<AgendaItem>,
): List<Lesson> {
  val agendaLessons = agenda
    .filter { it.category == AgendaCategory.LESSON }
    .map { item ->
      Lesson(
        id = "agenda-${item.id}",
        subject = item.subject ?: item.subtitle.ifBlank { item.title },
        date = item.date,
        time = item.time.orEmpty(),
        durationMinutes = 60,
        topic = item.detail ?: item.title,
        teacher = item.teacher,
        endTime = item.time?.takeIf(String::isNotBlank)?.let { start ->
          runCatching { LocalTime.parse(start).plusMinutes(60).formatTime() }.getOrNull()
        },
      )
    }

  val commonTimes = lessons
    .mapNotNull { it.time.takeIf(::isClockTime) }
    .distinct()
    .sorted()

  val dates = (lessons.map { it.date } + agendaLessons.map { it.date })
    .distinct()
    .sorted()

  return dates.flatMap { date ->
    val dayLessons = lessons.filter { it.date == date }
    val source = dayLessons.ifEmpty { agendaLessons.filter { it.date == date } }
    assignLessonSlots(source, commonTimes)
  }.sortedBy { "${it.date}-${it.time}" }
}

private fun assignLessonSlots(
  lessons: List<Lesson>,
  commonTimes: List<String>,
): List<Lesson> {
  if (lessons.isEmpty()) return emptyList()
  val sorted = lessons.sortedWith(compareBy<Lesson> { !isClockTime(it.time) }.thenBy { it.time })
  val usedTimes = sorted.mapNotNull { it.time.takeIf(::isClockTime) }.toMutableSet()
  val fallbackTimes = commonTimes.filterNot { usedTimes.contains(it) }.toMutableList()
  var generated = LocalTime.of(8, 0)

  fun nextTime(): String {
    val preset = fallbackTimes.firstOrNull()
    if (preset != null) {
      fallbackTimes.removeAt(0)
      usedTimes += preset
      return preset
    }
    while (generated.formatTime() in usedTimes) {
      generated = generated.plusHours(1)
    }
    return generated.formatTime().also {
      usedTimes += it
      generated = generated.plusHours(1)
    }
  }

  return sorted.map { lesson ->
    val resolvedTime = lesson.time.takeIf(::isClockTime) ?: nextTime()
    val resolvedDuration = lesson.durationMinutes.takeIf { it > 0 } ?: 60
    lesson.copy(
      time = resolvedTime,
      durationMinutes = resolvedDuration,
      endTime = lesson.endTime?.takeIf(String::isNotBlank) ?: runCatching {
        LocalTime.parse(resolvedTime).plusMinutes(resolvedDuration.toLong()).formatTime()
      }.getOrNull(),
    )
  }
}

private fun Lesson.toDashboardAgendaItem(): AgendaItem {
  return AgendaItem(
    id = id,
    title = subject,
    subtitle = topic ?: "Lezione del giorno",
    date = date,
    time = time.takeIf { it.isNotBlank() },
    detail = listOfNotNull(teacher, room).joinToString(" / ").ifBlank { null },
    subject = subject,
    teacher = teacher,
    category = AgendaCategory.LESSON,
    sharePayload = listOf(subject, date, time, topic).filterNotNull().joinToString(" - "),
  )
}

private fun LocalTime.formatTime(): String = "%02d:%02d".format(hour, minute)
private fun isClockTime(value: String): Boolean = runCatching { LocalTime.parse(value) }.isSuccess

private fun epochMillisToCreatedAt(value: Long): String {
  return java.time.Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()
    .toString()
    .take(16)
}

internal fun computeStudentScore(stats: StatsSnapshot, absences: List<AbsenceRecord>): StudentScoreSnapshot {
  val averageScore = ((stats.overallAverage ?: 6.0) / 10.0) * 60.0
  val attendancePenalty = absences.size.coerceAtMost(20) * 1.5
  val attendanceScore = (25.0 - attendancePenalty).coerceAtLeast(0.0)
  val consistencyScore = ((stats.gradeDistribution.good + stats.gradeDistribution.veryGood + stats.gradeDistribution.excellent) * 2.5).coerceAtMost(15.0)
  val total = (averageScore + attendanceScore + consistencyScore).coerceIn(0.0, 100.0)
  val snapshot = StudentScoreSnapshot(
    score = (total * 10.0).roundToInt() / 10.0,
    label = when {
      total >= 85 -> "Elite"
      total >= 70 -> "Solido"
      total >= 55 -> "In crescita"
      else -> "Da rilanciare"
    },
    computedAtEpochMillis = System.currentTimeMillis(),
    components = listOf(
      StudentScoreComponent("Media", averageScore, 60.0, 0.6),
      StudentScoreComponent("Frequenza", attendanceScore, 25.0, 0.25),
      StudentScoreComponent("Costanza", consistencyScore, 15.0, 0.15),
    ),
    sharePayload = "",
  )
  val payload = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(kotlinx.serialization.json.Json.encodeToString(snapshot.copy(sharePayload = "")).encodeToByteArray())
  return snapshot.copy(sharePayload = payload)
}

private suspend fun <T> Flow<T>.firstValue(): T = first()

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
  @Provides
  fun provideDownloadManager(@ApplicationContext context: Context): DownloadManager {
    return context.getSystemService(DownloadManager::class.java)
  }

  @Provides
  fun providePredictiveTimetableUseCase(): PredictiveTimetableUseCase = PredictiveTimetableUseCase()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds abstract fun bindAuthRepository(impl: SchoolAuthRepository): AuthRepository
  @Binds abstract fun bindDashboardRepository(impl: SchoolDataRepository): DashboardRepository
  @Binds abstract fun bindGradesRepository(impl: SchoolDataRepository): GradesRepository
  @Binds abstract fun bindAgendaRepository(impl: SchoolDataRepository): AgendaRepository
  @Binds abstract fun bindLessonsRepository(impl: SchoolDataRepository): LessonsRepository
  @Binds abstract fun bindHomeworkRepository(impl: SchoolDataRepository): HomeworkRepository
  @Binds abstract fun bindCommunicationsRepository(impl: SchoolDataRepository): CommunicationsRepository
  @Binds abstract fun bindMaterialsRepository(impl: SchoolDataRepository): MaterialsRepository
  @Binds abstract fun bindDocumentsRepository(impl: SchoolDataRepository): DocumentsRepository
  @Binds abstract fun bindAbsencesRepository(impl: SchoolDataRepository): AbsencesRepository
  @Binds abstract fun bindMeetingsRepository(impl: SchoolDataRepository): MeetingsRepository
  @Binds abstract fun bindStatsRepository(impl: SchoolDataRepository): StatsRepository
  @Binds abstract fun bindStudentScoreRepository(impl: SchoolDataRepository): StudentScoreRepository
  @Binds abstract fun bindSimulationRepository(impl: SchoolDataRepository): SimulationRepository
  @Binds abstract fun bindSettingsRepository(impl: SchoolSettingsRepository): SettingsRepository
  @Binds abstract fun bindCapabilityResolver(impl: DefaultCapabilityResolver): CapabilityResolver
}

internal fun CommunicationEntity.toCommunication(json: Json): Communication = Communication(
  id = id,
  pubId = pubId,
  evtCode = evtCode,
  title = title,
  contentPreview = contentPreview,
  sender = sender,
  date = date,
  read = read,
  attachments = json.decodeFromString(attachments),
  category = category,
  needsAck = needsAck,
  needsReply = needsReply,
  needsJoin = needsJoin,
  needsFile = needsFile,
  actions = json.decodeFromString(actions),
  noticeboardAttachments = json.decodeFromString(noticeboardAttachments),
  capabilityState = json.decodeFromString(capabilityState),
)
