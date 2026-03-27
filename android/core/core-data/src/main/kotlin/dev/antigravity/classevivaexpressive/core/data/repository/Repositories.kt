package dev.antigravity.classevivaexpressive.core.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventDao
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventEntity
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordDao
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordEntity
import dev.antigravity.classevivaexpressive.core.database.database.SimulationDao
import dev.antigravity.classevivaexpressive.core.database.database.SimulatedGradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreDao
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreSnapshotEntity
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
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
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
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
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectSummary
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.TimelinePoint
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
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
class SchoolAuthRepository @Inject constructor(
  private val sessionStore: SessionStore,
  private val syncCoordinator: SchoolSyncCoordinator,
) : AuthRepository {
  override val session: StateFlow<UserSession?> = sessionStore.session

  override suspend fun restore(): UserSession? {
    val restored = sessionStore.readCurrentSession()
    syncCoordinator.attachSession(restored)
    restored?.let { syncCoordinator.bootstrapPortal(it) }
    return restored
  }

  override suspend fun login(username: String, password: String): Result<UserSession> = runCatching {
    val session = syncCoordinator.login(username, password)
    sessionStore.writeSession(session)
    session
  }

  override suspend fun logout() {
    sessionStore.clear()
    syncCoordinator.attachSession(null)
  }
}

@Singleton
class SchoolSettingsRepository @Inject constructor(
  private val settingsStore: SettingsStore,
) : SettingsRepository {
  override fun observeSettings(): Flow<AppSettings> = settingsStore.settings
  override suspend fun updateThemeMode(mode: ThemeMode) = settingsStore.update { it.copy(themeMode = mode) }
  override suspend fun updateAccentMode(mode: AccentMode) = settingsStore.update { it.copy(accentMode = mode) }
  override suspend fun updateCustomAccent(name: String) = settingsStore.update { it.copy(customAccentName = name) }
  override suspend fun setDynamicColorEnabled(enabled: Boolean) = settingsStore.update { it.copy(dynamicColorEnabled = enabled) }
  override suspend fun setAmoledEnabled(enabled: Boolean) = settingsStore.update { it.copy(amoledEnabled = enabled) }
  override suspend fun setNotificationsEnabled(enabled: Boolean) = settingsStore.update { it.copy(notificationsEnabled = enabled) }
  override suspend fun setPeriodicSyncEnabled(enabled: Boolean) = settingsStore.update { it.copy(periodicSyncEnabled = enabled) }
}

@Singleton
class SchoolDataRepository @Inject constructor(
  private val json: Json,
  private val snapshotCacheDao: SnapshotCacheDao,
  private val customEventDao: CustomEventDao,
  private val simulationDao: SimulationDao,
  private val studentScoreDao: StudentScoreDao,
  private val downloadRecordDao: DownloadRecordDao,
  private val syncCoordinator: SchoolSyncCoordinator,
  private val downloadManager: DownloadManager,
  @ApplicationContext private val context: Context,
) : DashboardRepository,
  GradesRepository,
  AgendaRepository,
  LessonsRepository,
  CommunicationsRepository,
  MaterialsRepository,
  DocumentsRepository,
  AbsencesRepository,
  StatsRepository,
  StudentScoreRepository,
  SimulationRepository {

  override fun observeDashboard(): Flow<DashboardSnapshot> {
    val academicFlow = combine(
      observeGrades(),
      observeAgenda(),
      observeAbsences(),
    ) { grades, agenda, absences ->
      Triple(grades, agenda, absences)
    }
    val schoolFlow = combine(
      observeCommunications(),
      observeNotes(),
      observeDocuments(),
    ) { communications, notes, documents ->
      Triple(communications, notes, documents)
    }
    return combine(
      observeValue(ProfileKey, StudentProfile()),
      academicFlow,
      schoolFlow,
      syncCoordinator.syncStatus,
    ) { profile, academic, school, syncStatus ->
      val (grades, agenda, absences) = academic
      val (communications, notes, documents) = school
      val average = grades.mapNotNull { it.numericValue }.takeIf { it.isNotEmpty() }?.average()
      val upcoming = agenda.filter { it.date >= todayIso() }.sortedBy { "${it.date}-${it.time.orEmpty()}" }
      DashboardSnapshot(
        profile = profile,
        headline = "Ciao, ${profile.name.ifBlank { "studente" }}",
        subheadline = "Registro quotidiano offline-first, rapido da leggere e coerente in ogni sezione.",
        averageLabel = average?.let { "%.1f".format(it) } ?: "--",
        averageNumeric = average,
        stats = listOf(
          DashboardStat("avg", "Media", average?.let { "%.1f".format(it) } ?: "--", "${grades.size} valutazioni sincronizzate"),
          DashboardStat("agenda", "Scadenze", upcoming.count { it.category != AgendaCategory.LESSON }.toString(), "Compiti, verifiche ed eventi in arrivo"),
          DashboardStat("absences", "Assenze", absences.count { it.type.name == "ABSENCE" }.toString(), "${absences.count { !it.justified }} da giustificare"),
          DashboardStat("messages", "Novita", communications.count { !it.read }.toString(), "${documents.count()} documenti e circolari recenti"),
        ),
        todayLessons = upcoming.filter { it.category == AgendaCategory.LESSON && it.date == todayIso() },
        recentGrades = grades.sortedByDescending { it.date }.take(4),
        upcomingItems = upcoming.take(6),
        unreadCommunications = communications.filter { !it.read }.take(3),
        highlightedNotes = notes.take(2),
        recentAbsences = absences.sortedByDescending { it.date }.take(3),
        schoolDocuments = documents.take(2),
        syncStatus = syncStatus,
      )
    }
  }

  override suspend fun refreshDashboard(force: Boolean): Result<DashboardSnapshot> = runCatching {
    syncCoordinator.refreshAll(force)
    observeDashboard().firstValue()
  }

  override fun observeGrades(): Flow<List<Grade>> = observeValue(GradesKey, emptyList())
  override fun observePeriods(): Flow<List<Period>> = observeValue(PeriodsKey, emptyList())
  override fun observeSubjects(): Flow<List<Subject>> = observeValue(SubjectsKey, emptyList())
  override suspend fun refreshGrades(force: Boolean): Result<List<Grade>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeGrades().firstValue()
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
      observeValue(AgendaKey, emptyList<AgendaItem>()),
      observeValue(HomeworkKey, emptyList<Homework>()),
      customEventDao.observeAll().map { entities ->
        entities.map { json.decodeFromString<CustomEvent>(it.payload) }
      },
    ) { agenda, homeworks, customEvents ->
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
      } + customEvents.map { event ->
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
        )
      }).sortedBy { "${it.date}-${it.time.orEmpty()}" }
    }
  }

  override fun observeCustomEvents(): Flow<List<CustomEvent>> = customEventDao.observeAll().map { entities ->
    entities.map { json.decodeFromString<CustomEvent>(it.payload) }
  }

  override suspend fun addCustomEvent(event: CustomEvent) {
    customEventDao.upsert(CustomEventEntity(event.id, json.encodeToString(event), event.date, event.time))
  }

  override suspend fun removeCustomEvent(id: String) {
    customEventDao.deleteById(id)
  }

  override suspend fun refreshAgenda(force: Boolean): Result<List<AgendaItem>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeAgenda().firstValue()
  }

  override fun observeLessons(): Flow<List<Lesson>> = observeValue(LessonsKey, emptyList())
  override suspend fun refreshLessons(force: Boolean): Result<List<Lesson>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeLessons().firstValue()
  }

  override fun observeCommunications(): Flow<List<Communication>> = observeValue(CommunicationsKey, emptyList())
  override fun observeNotes(): Flow<List<Note>> = observeValue(NotesKey, emptyList())
  override suspend fun refreshCommunications(force: Boolean): Result<List<Communication>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeCommunications().firstValue()
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

  override fun observeMaterials(): Flow<List<MaterialItem>> = observeValue(MaterialsKey, emptyList())
  override suspend fun refreshMaterials(force: Boolean): Result<List<MaterialItem>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeMaterials().firstValue()
  }

  override suspend fun openAsset(item: MaterialItem): Result<MaterialAsset> = syncCoordinator.openMaterial(item)

  override fun observeDocuments(): Flow<List<DocumentItem>> = observeValue(DocumentsKey, emptyList())
  override fun observeSchoolbooks(): Flow<List<SchoolbookCourse>> = observeValue(SchoolbooksKey, emptyList())
  override suspend fun refreshDocuments(force: Boolean): Result<List<DocumentItem>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeDocuments().firstValue()
  }

  override suspend fun openDocument(document: DocumentItem): Result<DocumentAsset> = syncCoordinator.openDocument(document)

  override suspend fun queueDownload(document: DocumentItem): Result<Long> = runCatching {
    val url = document.viewUrl ?: document.confirmUrl ?: error("Il documento non ha un URL apribile.")
    queueDownloadInternal(url, document.title, null)
  }

  override fun observeAbsences(): Flow<List<AbsenceRecord>> = observeValue(AbsencesKey, emptyList())
  override suspend fun refreshAbsences(force: Boolean): Result<List<AbsenceRecord>> = runCatching {
    syncCoordinator.refreshAll(force)
    observeAbsences().firstValue()
  }

  override suspend fun justifyAbsence(record: AbsenceRecord, reason: String?): Result<List<AbsenceRecord>> {
    return syncCoordinator.justifyAbsence(record, reason)
  }

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
    val request = DownloadManager.Request(Uri.parse(url))
      .setTitle(title)
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      .setMimeType(mimeType)
      .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, sanitizeFileName(title))
      .addRequestHeader("User-Agent", "Classeviva Expressive Native")
    syncCoordinator.portalCookieHeader(url)?.let { cookie ->
      request.addRequestHeader("Cookie", cookie)
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

  private inline fun <reified T> observeValue(key: String, default: T): Flow<T> {
    return snapshotCacheDao.observeByKey(key).map { entity ->
      entity?.payload?.let { runCatching { json.decodeFromString<T>(it) }.getOrDefault(default) } ?: default
    }
  }

  private fun todayIso(): String = java.time.LocalDate.now().toString()

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
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds abstract fun bindAuthRepository(impl: SchoolAuthRepository): AuthRepository
  @Binds abstract fun bindDashboardRepository(impl: SchoolDataRepository): DashboardRepository
  @Binds abstract fun bindGradesRepository(impl: SchoolDataRepository): GradesRepository
  @Binds abstract fun bindAgendaRepository(impl: SchoolDataRepository): AgendaRepository
  @Binds abstract fun bindLessonsRepository(impl: SchoolDataRepository): LessonsRepository
  @Binds abstract fun bindCommunicationsRepository(impl: SchoolDataRepository): CommunicationsRepository
  @Binds abstract fun bindMaterialsRepository(impl: SchoolDataRepository): MaterialsRepository
  @Binds abstract fun bindDocumentsRepository(impl: SchoolDataRepository): DocumentsRepository
  @Binds abstract fun bindAbsencesRepository(impl: SchoolDataRepository): AbsencesRepository
  @Binds abstract fun bindStatsRepository(impl: SchoolDataRepository): StatsRepository
  @Binds abstract fun bindStudentScoreRepository(impl: SchoolDataRepository): StudentScoreRepository
  @Binds abstract fun bindSimulationRepository(impl: SchoolDataRepository): SimulationRepository
  @Binds abstract fun bindSettingsRepository(impl: SchoolSettingsRepository): SettingsRepository
}
