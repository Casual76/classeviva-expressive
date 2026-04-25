package dev.antigravity.classevivaexpressive.core.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

@Serializable
enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
  AMOLED,
}

@Serializable
enum class AccentMode {
  BRAND,
  DYNAMIC,
  CUSTOM_PRESET,
}

@Serializable
enum class SyncState {
  IDLE,
  SYNCING,
  PARTIAL,
  OFFLINE,
  ERROR,
}

@Serializable
enum class CapabilityStatus {
  AVAILABLE,
  EMPTY,
  EXTERNAL_ONLY,
  UNAVAILABLE,
}

@Serializable
enum class AgendaCategory {
  LESSON,
  HOMEWORK,
  ASSESSMENT,
  EVENT,
  CUSTOM,
}

@Serializable
enum class AbsenceType {
  ABSENCE,
  LATE,
  EXIT,
}

@Serializable
enum class NoticeboardActionType {
  ACKNOWLEDGE,
  REPLY,
  JOIN,
  UPLOAD,
  DOWNLOAD,
}

@Serializable
enum class FeatureCapabilityMode {
  DIRECT_REST,
  DIRECT_PORTAL,
  GATEWAY,
  TENANT_OPTIONAL,
  UNSUPPORTED,
}

@Serializable
enum class RegistroFeature {
  LOGIN_SESSION,
  PROFILE,
  GRADES,
  PERIODS,
  SUBJECTS,
  AGENDA,
  HOMEWORKS,
  LESSONS,
  ABSENCES,
  ABSENCE_JUSTIFICATIONS,
  NOTICEBOARD,
  NOTICEBOARD_REPLY,
  NOTICEBOARD_JOIN,
  NOTICEBOARD_UPLOAD,
  NOTES,
  MATERIALS,
  DOCUMENTS,
  SCHOOLBOOKS,
  MEETINGS,
  NOTIFICATIONS,
  PREVIOUS_SCHOOL_YEAR,
  SPORTELLO,
  QUESTIONNAIRES,
}

@Serializable
enum class GatewayActionState {
  IDLE,
  REQUIRES_CONFIGURATION,
  IN_PROGRESS,
  SUCCESS,
  FAILED,
}

@Serializable
enum class AbsenceJustificationMode {
  AUTO,
  TEXT_ONLY,
  ATTACHMENT_ONLY,
  TEXT_AND_ATTACHMENT,
}

@Serializable
data class CapabilityState(
  val status: CapabilityStatus = CapabilityStatus.UNAVAILABLE,
  val label: String = "",
  val detail: String? = null,
)

@Serializable
data class SchoolYearRef(
  val startYear: Int,
  val endYear: Int,
) {
  val id: String
    get() = "$startYear-$endYear"

  val label: String
    get() = "$startYear/${endYear.toString().takeLast(2)}"

  companion object {
    fun current(nowYear: Int, nowMonth: Int): SchoolYearRef {
      val start = if (nowMonth >= 8) nowYear else nowYear - 1
      return SchoolYearRef(start, start + 1)
    }
  }
}

@Serializable
data class FeatureCapability(
  val feature: RegistroFeature,
  val mode: FeatureCapabilityMode,
  val enabled: Boolean = true,
  val label: String = "",
  val detail: String? = null,
)

@Serializable
data class SyncStatus(
  val state: SyncState = SyncState.IDLE,
  val lastSuccessfulSyncEpochMillis: Long? = null,
  val message: String? = null,
)

@Serializable
data class NotificationPreferences(
  val enabled: Boolean = true,
  val homework: Boolean = true,
  val communications: Boolean = true,
  val absences: Boolean = true,
  val grades: Boolean = true,
  val test: Boolean = true,
)

@Serializable
data class NotificationChannelStatus(
  val id: String = "",
  val label: String = "",
  val enabled: Boolean = false,
)

@Serializable
data class NotificationRuntimeState(
  val permissionGranted: Boolean = false,
  val appNotificationsEnabled: Boolean = false,
  val channels: List<NotificationChannelStatus> = emptyList(),
)

@Serializable
data class StudentProfile(
  val id: String = "",
  val name: String = "",
  val surname: String = "",
  val email: String = "",
  val schoolClass: String = "",
  val section: String = "",
  val school: String = "",
  val schoolYear: String = "",
)

@Serializable
data class UserSession(
  val token: String,
  val studentId: String,
  val username: String,
  val profile: StudentProfile,
)

@Serializable
data class Grade(
  val id: String,
  val subject: String,
  val valueLabel: String,
  val numericValue: Double? = null,
  val description: String? = null,
  val date: String,
  val type: String,
  val weight: Double? = null,
  val notes: String? = null,
  val period: String? = null,
  val periodCode: String? = null,
  val teacher: String? = null,
  val color: String? = null,
)

@Serializable
data class Lesson(
  val id: String,
  val subject: String,
  val date: String,
  val time: String,
  val durationMinutes: Int,
  val topic: String? = null,
  val teacher: String? = null,
  val room: String? = null,
  val endTime: String? = null,
)

@Serializable
data class Homework(
  val id: String,
  val subject: String,
  val description: String,
  val dueDate: String,
  val notes: String? = null,
  val attachments: List<RemoteAttachment> = emptyList(),
)

@Serializable
data class AttachmentPayload(
  val fileName: String,
  val mimeType: String? = null,
  val base64Content: String,
)

@Serializable
data class HomeworkDetail(
  val homework: Homework,
  val fullText: String,
  val assignedDate: String? = null,
  val teacher: String? = null,
  val capability: FeatureCapability? = null,
)

@Serializable
data class HomeworkSubmission(
  val homeworkId: String,
  val text: String? = null,
  val attachments: List<AttachmentPayload> = emptyList(),
  val submitUrl: String? = null,
)

@Serializable
data class HomeworkSubmissionReceipt(
  val homeworkId: String,
  val state: String,
  val submittedAt: String? = null,
  val message: String? = null,
  val remoteReference: String? = null,
)

@Serializable
data class AgendaItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val date: String,
  val time: String? = null,
  val detail: String? = null,
  val subject: String? = null,
  val teacher: String? = null,
  val category: AgendaCategory,
  val sharePayload: String? = null,
  val createdAt: String? = null,
)

@Serializable
data class CustomEvent(
  val id: String,
  val title: String,
  val description: String,
  val subject: String,
  val date: String,
  val time: String? = null,
  val category: AgendaCategory = AgendaCategory.CUSTOM,
  val createdAt: String? = null,
)

@Serializable
data class TemplateSlot(
  val dayOfWeek: Int,
  val time: String,
  val endTime: String? = null,
  val durationMinutes: Int,
  val subject: String,
  val teacher: String? = null,
  val room: String? = null,
  val confidence: Float = 0f,
  val sampleCount: Int = 0,
) {
  val weekday: DayOfWeek
    get() = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
}

@Serializable
data class TimetableTemplate(
  val slots: List<TemplateSlot> = emptyList(),
  val sampledWeeks: Int = 0,
  val lastComputedAt: Long = 0L,
) {
  fun slotsByDay(): Map<DayOfWeek, List<TemplateSlot>> {
    return slots
      .groupBy(TemplateSlot::weekday)
      .mapValues { (_, items) -> items.sortedBy { it.time } }
  }

  fun hasLessonsOn(dayOfWeek: DayOfWeek): Boolean = slots.any { it.weekday == dayOfWeek }
}

@Serializable
data class AbsenceRecord(
  val id: String,
  val date: String,
  val type: AbsenceType,
  val hours: Int? = null,
  val justified: Boolean,
  val canJustify: Boolean = false,
  val justificationDate: String? = null,
  val justificationReason: String? = null,
  val justifyUrl: String? = null,
  val detailUrl: String? = null,
)

@Serializable
data class AbsenceJustificationRequest(
  val absenceId: String,
  val reasonText: String? = null,
  val attachment: AttachmentPayload? = null,
  val justifyUrl: String? = null,
  val detailUrl: String? = null,
  val submissionMode: AbsenceJustificationMode = AbsenceJustificationMode.AUTO,
)

@Serializable
data class RemoteAttachment(
  val id: String,
  val name: String,
  val url: String? = null,
  val mimeType: String? = null,
  val portalOnly: Boolean = false,
)

@Serializable
data class NoticeboardAction(
  val type: NoticeboardActionType,
  val label: String,
  val url: String? = null,
)

@Serializable
data class NoticeboardAttachment(
  val id: String,
  val name: String,
  val url: String? = null,
  val mimeType: String? = null,
  val portalOnly: Boolean = false,
  val action: NoticeboardAction? = null,
)

@Serializable
data class Communication(
  val id: String,
  val pubId: String,
  val evtCode: String,
  val title: String,
  val contentPreview: String,
  val sender: String,
  val date: String,
  val read: Boolean,
  val attachments: List<RemoteAttachment> = emptyList(),
  val category: String? = null,
  val needsAck: Boolean = false,
  val needsReply: Boolean = false,
  val needsJoin: Boolean = false,
  val needsFile: Boolean = false,
  val actions: List<NoticeboardAction> = emptyList(),
  val noticeboardAttachments: List<NoticeboardAttachment> = emptyList(),
  val capabilityState: CapabilityState = CapabilityState(),
)

@Serializable
data class CommunicationDetail(
  val communication: Communication,
  val content: String,
  val replyText: String? = null,
  val portalDetailUrl: String? = null,
  val acknowledgeUrl: String? = null,
  val replyUrl: String? = null,
  val joinUrl: String? = null,
  val fileUploadUrl: String? = null,
  val actions: List<NoticeboardAction> = emptyList(),
)

@Serializable
data class Note(
  val id: String,
  val categoryCode: String,
  val categoryLabel: String,
  val title: String,
  val contentPreview: String,
  val date: String,
  val author: String,
  val read: Boolean,
  val severity: String,
)

@Serializable
data class NoteDetail(
  val note: Note,
  val content: String,
)

@Serializable
data class MaterialItem(
  val id: String,
  val teacherId: String,
  val teacherName: String,
  val folderId: String,
  val folderName: String,
  val title: String,
  val objectId: String,
  val objectType: String,
  val sharedAt: String,
  val capabilityState: CapabilityState,
  val attachments: List<RemoteAttachment> = emptyList(),
)

@Serializable
data class MaterialAsset(
  val id: String,
  val title: String,
  val objectType: String,
  val fileName: String? = null,
  val mimeType: String? = null,
  val base64Content: String? = null,
  val textPreview: String? = null,
  val sourceUrl: String? = null,
  val capabilityState: CapabilityState = CapabilityState(),
)

@Serializable
data class DocumentItem(
  val id: String,
  val title: String,
  val detail: String,
  val viewUrl: String? = null,
  val confirmUrl: String? = null,
  val capabilityState: CapabilityState = CapabilityState(),
)

@Serializable
data class DocumentAsset(
  val id: String,
  val title: String,
  val fileName: String? = null,
  val mimeType: String? = null,
  val base64Content: String? = null,
  val textPreview: String? = null,
  val sourceUrl: String? = null,
  val capabilityState: CapabilityState = CapabilityState(),
)

@Serializable
data class Schoolbook(
  val id: String,
  val isbn: String,
  val title: String,
  val subtitle: String? = null,
  val volume: String? = null,
  val author: String? = null,
  val publisher: String? = null,
  val subject: String,
  val price: Double? = null,
  val coverUrl: String? = null,
  val toBuy: Boolean = false,
  val alreadyOwned: Boolean = false,
  val alreadyInUse: Boolean = false,
  val recommended: Boolean = false,
  val recommendedFor: String? = null,
  val newAdoption: Boolean = false,
)

@Serializable
data class SchoolbookCourse(
  val id: String,
  val title: String,
  val books: List<Schoolbook> = emptyList(),
)

@Serializable
data class MeetingTeacher(
  val id: String,
  val name: String,
  val subject: String? = null,
)

@Serializable
data class MeetingSlot(
  val id: String,
  val teacherId: String,
  val date: String,
  val startTime: String,
  val endTime: String? = null,
  val location: String? = null,
  val available: Boolean = true,
  val joinUrl: String? = null,
)

@Serializable
data class MeetingBooking(
  val id: String,
  val teacher: MeetingTeacher,
  val slot: MeetingSlot,
  val status: String,
  val bookingPosition: String? = null,
)

@Serializable
data class MeetingJoinLink(
  val bookingId: String,
  val url: String,
)

@Serializable
data class MeetingCancellation(
  val bookingId: String,
  val cancelledAt: String,
  val message: String? = null,
)

@Serializable
data class Period(
  val code: String,
  val order: Int,
  val description: String,
  val label: String,
  val isFinal: Boolean,
  val startDate: String,
  val endDate: String,
)

@Serializable
data class Subject(
  val id: String,
  val description: String,
  val order: Int,
  val teachers: List<String> = emptyList(),
)

@Serializable
data class TimelinePoint(
  val label: String,
  val value: Double,
)

@Serializable
data class SubjectSummary(
  val subject: String,
  val average: Double? = null,
  val teacherLabel: String = "",
  val count: Int = 0,
  val recentValues: List<Double> = emptyList(),
  val typeBreakdown: String = "",
)

@Serializable
data class GradeDistribution(
  val insufficient: Int = 0,
  val sufficient: Int = 0,
  val good: Int = 0,
  val veryGood: Int = 0,
  val excellent: Int = 0,
)

@Serializable
data class DashboardStat(
  val id: String,
  val label: String,
  val value: String,
  val detail: String,
)

@Serializable
data class DashboardSnapshot(
  val profile: StudentProfile = StudentProfile(),
  val headline: String = "",
  val subheadline: String = "",
  val averageLabel: String = "--",
  val averageNumeric: Double? = null,
  val stats: List<DashboardStat> = emptyList(),
  val todayLessons: List<Lesson> = emptyList(),
  val recentGrades: List<Grade> = emptyList(),
  val unseenGrades: List<Grade> = emptyList(),
  val upcomingItems: List<AgendaItem> = emptyList(),
  val unreadCommunications: List<Communication> = emptyList(),
  val highlightedNotes: List<Note> = emptyList(),
  val recentAbsences: List<AbsenceRecord> = emptyList(),
  val schoolDocuments: List<DocumentItem> = emptyList(),
  val syncStatus: SyncStatus = SyncStatus(),
)

@Serializable
data class StatsSnapshot(
  val overallAverage: Double? = null,
  val subjectSummaries: List<SubjectSummary> = emptyList(),
  val gradeDistribution: GradeDistribution = GradeDistribution(),
  val gradeTrend: List<TimelinePoint> = emptyList(),
  val absenceBreakdown: Map<String, Int> = emptyMap(),
  val workloadBreakdown: Map<String, Int> = emptyMap(),
)

@Serializable
data class SeenGradeState(
  val studentId: String,
  val gradeId: String,
  val seenAtEpochMillis: Long,
)

@Serializable
data class SubjectGoal(
  val studentId: String,
  val subject: String,
  val periodCode: String? = null,
  val targetAverage: Double,
)

@Serializable
data class SimulatedGrade(
  val id: String,
  val subject: String,
  val value: Double,
  val type: String,
  val date: String,
  val weight: Double = 1.0,
  val note: String? = null,
)

@Serializable
data class GradeSimulationSummary(
  val realAverage: Double? = null,
  val simulatedAverage: Double? = null,
  val delta: Double = 0.0,
  val grades: List<SimulatedGrade> = emptyList(),
)

@Serializable
data class StudentScoreComponent(
  val title: String,
  val value: Double,
  val maxValue: Double,
  val weight: Double,
)

@Serializable
data class StudentScoreSnapshot(
  val score: Double,
  val label: String,
  val computedAtEpochMillis: Long,
  val components: List<StudentScoreComponent>,
  val sharePayload: String,
)

@Serializable
data class StudentScoreComparison(
  val current: StudentScoreSnapshot,
  val imported: StudentScoreSnapshot,
  val difference: Double,
)

@Serializable
data class NetworkConfig(
  val featureModes: Map<RegistroFeature, FeatureCapabilityMode> = RegistroFeature.entries.associateWith {
    FeatureCapabilityMode.DIRECT_REST
  },
  val gatewayBaseUrl: String = "",
)

@Serializable
data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val accentMode: AccentMode = AccentMode.BRAND,
  val customAccentName: String = "expressive",
  val dynamicColorEnabled: Boolean = true,
  val amoledEnabled: Boolean = false,
  val notificationPreferences: NotificationPreferences = NotificationPreferences(),
  val periodicSyncEnabled: Boolean = true,
  val networkConfig: NetworkConfig = NetworkConfig(),
) {
  val notificationsEnabled: Boolean
    get() = notificationPreferences.enabled

  fun getFeatureMode(feature: RegistroFeature): FeatureCapabilityMode {
    return networkConfig.featureModes[feature] ?: FeatureCapabilityMode.DIRECT_REST
  }
}

interface AuthRepository {
  val session: StateFlow<UserSession?>
  suspend fun restore(): UserSession?
  suspend fun login(username: String, password: String): Result<UserSession>
  suspend fun logout()
}

interface DashboardRepository {
  fun observeDashboard(): Flow<DashboardSnapshot>
  suspend fun refreshDashboard(force: Boolean = false): Result<DashboardSnapshot>
}

interface GradesRepository {
  fun observeGrades(): Flow<List<Grade>>
  fun observePeriods(): Flow<List<Period>>
  fun observeSubjects(): Flow<List<Subject>>
  fun observeSeenGradeStates(): Flow<List<SeenGradeState>>
  fun observeSubjectGoals(): Flow<List<SubjectGoal>>
  suspend fun refreshGrades(force: Boolean = false): Result<List<Grade>>
  suspend fun markGradeSeen(gradeId: String)
  suspend fun saveSubjectGoal(subject: String, periodCode: String?, targetAverage: Double)
  suspend fun removeSubjectGoal(subject: String, periodCode: String?)
}

interface AgendaRepository {
  fun observeAgenda(): Flow<List<AgendaItem>>
  fun observeCustomEvents(): Flow<List<CustomEvent>>
  suspend fun addCustomEvent(event: CustomEvent)
  suspend fun removeCustomEvent(id: String)
  suspend fun refreshAgenda(force: Boolean = false): Result<List<AgendaItem>>
}

interface LessonsRepository {
  fun observeLessons(): Flow<List<Lesson>>
  fun observeTimetableTemplate(): Flow<TimetableTemplate>
  suspend fun refreshLessons(force: Boolean = false): Result<List<Lesson>>
}

interface HomeworkRepository {
  fun observeHomeworks(): Flow<List<Homework>>
  suspend fun refreshHomeworks(force: Boolean = false): Result<List<Homework>>
  suspend fun getHomeworkDetail(id: String): Result<HomeworkDetail>
  suspend fun submitHomework(submission: HomeworkSubmission): Result<HomeworkSubmissionReceipt>
  suspend fun queueAttachmentDownload(attachment: RemoteAttachment): Result<Long>
}

interface CommunicationsRepository {
  fun observeCommunications(): Flow<List<Communication>>
  fun observeNotes(): Flow<List<Note>>
  suspend fun refreshCommunications(force: Boolean = false): Result<List<Communication>>
  suspend fun markAllAsRead(): Result<Unit>
  suspend fun markCommunicationRead(id: String): Result<Unit>
  suspend fun getCommunicationDetail(pubId: String, evtCode: String): Result<CommunicationDetail>
  suspend fun getNoteDetail(id: String, categoryCode: String): Result<NoteDetail>
  suspend fun queueDownload(attachment: RemoteAttachment): Result<Long>
  /** Downloads the attachment if not locally cached (or if expired), returns its absolute local path. */
  suspend fun resolveAttachmentLocalPath(attachment: RemoteAttachment): Result<String>
  suspend fun acknowledgeCommunication(detail: CommunicationDetail): Result<CommunicationDetail>
  suspend fun replyToCommunication(detail: CommunicationDetail, text: String): Result<CommunicationDetail>
  suspend fun joinCommunication(detail: CommunicationDetail): Result<CommunicationDetail>
  suspend fun uploadCommunicationFile(
    detail: CommunicationDetail,
    fileName: String,
    mimeType: String?,
    bytes: ByteArray,
  ): Result<CommunicationDetail>
}

interface MaterialsRepository {
  fun observeMaterials(): Flow<List<MaterialItem>>
  suspend fun refreshMaterials(force: Boolean = false): Result<List<MaterialItem>>
  suspend fun openAsset(item: MaterialItem): Result<MaterialAsset>
  suspend fun queueDownload(attachment: RemoteAttachment): Result<Long>
}

interface DocumentsRepository {
  fun observeDocuments(): Flow<List<DocumentItem>>
  fun observeSchoolbooks(): Flow<List<SchoolbookCourse>>
  suspend fun refreshDocuments(force: Boolean = false): Result<List<DocumentItem>>
  suspend fun openDocument(document: DocumentItem): Result<DocumentAsset>
  suspend fun queueDownload(document: DocumentItem): Result<Long>
}

interface AbsencesRepository {
  fun observeAbsences(): Flow<List<AbsenceRecord>>
  suspend fun refreshAbsences(force: Boolean = false): Result<List<AbsenceRecord>>
  suspend fun justifyAbsence(
    record: AbsenceRecord,
    reason: String? = null,
    request: AbsenceJustificationRequest = AbsenceJustificationRequest(absenceId = record.id, reasonText = reason),
  ): Result<List<AbsenceRecord>>
}

data class PortalCookieDto(val name: String, val value: String)

interface MeetingsRepository {
  fun observeMeetingTeachers(): Flow<List<MeetingTeacher>>
  fun observeMeetingSlots(): Flow<List<MeetingSlot>>
  fun observeMeetingBookings(): Flow<List<MeetingBooking>>
  suspend fun refreshMeetings(force: Boolean = false): Result<List<MeetingBooking>>
  suspend fun bookMeeting(slot: MeetingSlot): Result<MeetingBooking>
  suspend fun cancelMeeting(booking: MeetingBooking): Result<List<MeetingBooking>>
  suspend fun joinMeeting(booking: MeetingBooking): Result<MeetingJoinLink>
  fun getPortalMeetingsUrl(): String
  suspend fun getPortalSessionCookies(): List<PortalCookieDto>
}

interface SchoolYearRepository {
  fun observeSelectedSchoolYear(): Flow<SchoolYearRef>
  fun observeAvailableSchoolYears(): Flow<List<SchoolYearRef>>
  suspend fun selectSchoolYear(year: SchoolYearRef)
}

interface CapabilityResolver {
  fun observeCapabilityMatrix(): Flow<List<FeatureCapability>>
  fun observeCapability(feature: RegistroFeature): Flow<FeatureCapability>
}

interface StatsRepository {
  fun observeStats(): Flow<StatsSnapshot>
  suspend fun refreshStats(force: Boolean = false): Result<StatsSnapshot>
}

interface StudentScoreRepository {
  fun observeCurrentScore(): Flow<StudentScoreSnapshot?>
  fun observeSnapshots(): Flow<List<StudentScoreSnapshot>>
  suspend fun refreshStudentScore(force: Boolean = false): Result<StudentScoreSnapshot>
  suspend fun exportCurrentPayload(): Result<String>
  suspend fun importPayload(payload: String): Result<StudentScoreComparison>
}

interface SimulationRepository {
  fun observeSimulation(): Flow<GradeSimulationSummary>
  suspend fun addSimulatedGrade(grade: SimulatedGrade)
  suspend fun removeSimulatedGrade(id: String)
  suspend fun clearSimulation()
}

interface SettingsRepository {
  fun observeSettings(): Flow<AppSettings>
  fun observeNotificationRuntimeState(): Flow<NotificationRuntimeState>
  suspend fun updateThemeMode(mode: ThemeMode)
  suspend fun updateAccentMode(mode: AccentMode)
  suspend fun updateCustomAccent(name: String)
  suspend fun setDynamicColorEnabled(enabled: Boolean)
  suspend fun setAmoledEnabled(enabled: Boolean)
  suspend fun setNotificationsEnabled(enabled: Boolean)
  suspend fun setPeriodicSyncEnabled(enabled: Boolean)
  suspend fun updateNotificationPreferences(preferences: NotificationPreferences)
  suspend fun setNotificationCategoryEnabled(channelId: String, enabled: Boolean)
  suspend fun refreshNotificationRuntimeState()
  suspend fun sendTestNotification(): Result<Unit>
  suspend fun updateGatewayBaseUrl(url: String)
}
