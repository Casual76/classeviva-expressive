package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.network.BuildConfig
import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceJustificationRequest
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AttachmentPayload
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmission
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmissionReceipt
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingJoinLink
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

data class GatewayCredentialsDto(
  val username: String,
  val password: String,
)

data class GatewaySchoolYearDto(
  val startYear: Int,
  val endYear: Int,
)

data class GatewayEnvelope<T>(
  val credentials: GatewayCredentialsDto,
  val schoolYear: GatewaySchoolYearDto,
  val payload: T? = null,
)

data class GatewayNoticeboardReplyPayload(
  val detail: CommunicationDetail,
  val text: String? = null,
  val attachment: AttachmentPayload? = null,
)

data class GatewayMeetingBookPayload(
  val slotId: String,
)

data class GatewayMeetingCancelPayload(
  val bookingId: String,
)

data class GatewayMeetingsSnapshotDto(
  val teachers: List<MeetingTeacher> = emptyList(),
  val slots: List<MeetingSlot> = emptyList(),
  val bookings: List<MeetingBooking> = emptyList(),
)

interface ClassevivaGatewayService {
  @POST("gateway/profile")
  suspend fun getProfile(@Body body: GatewayEnvelope<Unit>): StudentProfile

  @POST("gateway/grades")
  suspend fun getGrades(@Body body: GatewayEnvelope<Unit>): List<Grade>

  @POST("gateway/periods")
  suspend fun getPeriods(@Body body: GatewayEnvelope<Unit>): List<Period>

  @POST("gateway/subjects")
  suspend fun getSubjects(@Body body: GatewayEnvelope<Unit>): List<Subject>

  @POST("gateway/agenda")
  suspend fun getAgenda(@Body body: GatewayEnvelope<Unit>): List<AgendaItem>

  @POST("gateway/lessons")
  suspend fun getLessons(@Body body: GatewayEnvelope<Unit>): List<Lesson>

  @POST("gateway/homeworks")
  suspend fun getHomeworks(@Body body: GatewayEnvelope<Unit>): List<Homework>

  @POST("gateway/homeworks/{id}")
  suspend fun getHomeworkDetail(
    @Path("id") homeworkId: String,
    @Body body: GatewayEnvelope<Unit>,
  ): HomeworkDetail

  @POST("gateway/homeworks/{id}/submit")
  suspend fun submitHomework(
    @Path("id") homeworkId: String,
    @Body body: GatewayEnvelope<HomeworkSubmission>,
  ): HomeworkSubmissionReceipt

  @POST("gateway/absences/{id}/justify")
  suspend fun justifyAbsence(
    @Path("id") absenceId: String,
    @Body body: GatewayEnvelope<AbsenceJustificationRequest>,
  ): List<AbsenceRecord>

  @POST("gateway/noticeboard")
  suspend fun getNoticeboard(@Body body: GatewayEnvelope<Unit>): List<Communication>

  @POST("gateway/noticeboard/{evtCode}/{pubId}/reply")
  suspend fun replyToNoticeboard(
    @Path("evtCode") evtCode: String,
    @Path("pubId") pubId: String,
    @Body body: GatewayEnvelope<GatewayNoticeboardReplyPayload>,
  ): CommunicationDetail

  @POST("gateway/noticeboard/{evtCode}/{pubId}/join")
  suspend fun joinNoticeboard(
    @Path("evtCode") evtCode: String,
    @Path("pubId") pubId: String,
    @Body body: GatewayEnvelope<GatewayNoticeboardReplyPayload>,
  ): CommunicationDetail

  @POST("gateway/noticeboard/{evtCode}/{pubId}/upload")
  suspend fun uploadNoticeboard(
    @Path("evtCode") evtCode: String,
    @Path("pubId") pubId: String,
    @Body body: GatewayEnvelope<GatewayNoticeboardReplyPayload>,
  ): CommunicationDetail

  @POST("gateway/materials")
  suspend fun getMaterials(@Body body: GatewayEnvelope<Unit>): List<MaterialItem>

  @POST("gateway/documents")
  suspend fun getDocuments(@Body body: GatewayEnvelope<Unit>): List<DocumentItem>

  @POST("gateway/meetings")
  suspend fun getMeetings(@Body body: GatewayEnvelope<Unit>): GatewayMeetingsSnapshotDto

  @POST("gateway/meetings/book")
  suspend fun bookMeeting(@Body body: GatewayEnvelope<GatewayMeetingBookPayload>): MeetingBooking

  @POST("gateway/meetings/{id}/cancel")
  suspend fun cancelMeeting(
    @Path("id") bookingId: String,
    @Body body: GatewayEnvelope<GatewayMeetingCancelPayload>,
  ): List<MeetingBooking>

  @POST("gateway/meetings/{id}/join")
  suspend fun joinMeeting(
    @Path("id") bookingId: String,
    @Body body: GatewayEnvelope<Unit>,
  ): MeetingJoinLink
}

@Singleton
class ClassevivaGatewayClient @Inject constructor(
  private val sessionStorage: SessionStorage,
  private val settingsStore: SettingsStore,
  private val gatewayService: ClassevivaGatewayService,
) {
  suspend fun isConfigured(): Boolean = settingsStore.settings.first().networkConfig.gatewayBaseUrl.isNotBlank()

  suspend fun getProfile(schoolYear: SchoolYearRef): StudentProfile = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getProfile(envelope(schoolYear)) }
  }

  suspend fun getGrades(schoolYear: SchoolYearRef): List<Grade> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getGrades(envelope(schoolYear)) }
  }

  suspend fun getPeriods(schoolYear: SchoolYearRef): List<Period> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getPeriods(envelope(schoolYear)) }
  }

  suspend fun getSubjects(schoolYear: SchoolYearRef): List<Subject> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getSubjects(envelope(schoolYear)) }
  }

  suspend fun getAgenda(schoolYear: SchoolYearRef): List<AgendaItem> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getAgenda(envelope(schoolYear)) }
  }

  suspend fun getLessons(schoolYear: SchoolYearRef): List<Lesson> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getLessons(envelope(schoolYear)) }
  }

  suspend fun getHomeworks(schoolYear: SchoolYearRef): List<Homework> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.getHomeworks(envelope(schoolYear))
    }
  }

  suspend fun getHomeworkDetail(homeworkId: String, schoolYear: SchoolYearRef): HomeworkDetail = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.getHomeworkDetail(homeworkId, envelope(schoolYear))
    }
  }

  suspend fun submitHomework(submission: HomeworkSubmission, schoolYear: SchoolYearRef): HomeworkSubmissionReceipt = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.submitHomework(submission.homeworkId, envelope(schoolYear, submission))
    }
  }

  suspend fun justifyAbsence(request: AbsenceJustificationRequest, schoolYear: SchoolYearRef): List<AbsenceRecord> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.justifyAbsence(request.absenceId, envelope(schoolYear, request))
    }
  }

  suspend fun getCommunications(schoolYear: SchoolYearRef): List<Communication> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getNoticeboard(envelope(schoolYear)) }
  }

  suspend fun replyToNoticeboard(
    detail: CommunicationDetail,
    text: String,
    schoolYear: SchoolYearRef,
  ): CommunicationDetail = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.replyToNoticeboard(
        evtCode = detail.communication.evtCode,
        pubId = detail.communication.pubId,
        body = envelope(
          schoolYear,
          GatewayNoticeboardReplyPayload(
            detail = detail,
            text = text,
          ),
        ),
      )
    }
  }

  suspend fun joinNoticeboard(
    detail: CommunicationDetail,
    schoolYear: SchoolYearRef,
  ): CommunicationDetail = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.joinNoticeboard(
        evtCode = detail.communication.evtCode,
        pubId = detail.communication.pubId,
        body = envelope(
          schoolYear,
          GatewayNoticeboardReplyPayload(detail = detail),
        ),
      )
    }
  }

  suspend fun uploadNoticeboard(
    detail: CommunicationDetail,
    attachment: AttachmentPayload,
    schoolYear: SchoolYearRef,
  ): CommunicationDetail = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.uploadNoticeboard(
        evtCode = detail.communication.evtCode,
        pubId = detail.communication.pubId,
        body = envelope(
          schoolYear,
          GatewayNoticeboardReplyPayload(
            detail = detail,
            attachment = attachment,
          ),
        ),
      )
    }
  }

  suspend fun getMaterials(schoolYear: SchoolYearRef): List<MaterialItem> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getMaterials(envelope(schoolYear)) }
  }

  suspend fun getDocuments(schoolYear: SchoolYearRef): List<DocumentItem> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall { gatewayService.getDocuments(envelope(schoolYear)) }
  }

  suspend fun getMeetings(schoolYear: SchoolYearRef): GatewayMeetingsSnapshotDto = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.getMeetings(envelope(schoolYear))
    }
  }

  suspend fun bookMeeting(slot: MeetingSlot, schoolYear: SchoolYearRef): MeetingBooking = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.bookMeeting(envelope(schoolYear, GatewayMeetingBookPayload(slotId = slot.id)))
    }
  }

  suspend fun cancelMeeting(bookingId: String, schoolYear: SchoolYearRef): List<MeetingBooking> = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.cancelMeeting(
        bookingId = bookingId,
        body = envelope(schoolYear, GatewayMeetingCancelPayload(bookingId = bookingId)),
      )
    }
  }

  suspend fun joinMeeting(bookingId: String, schoolYear: SchoolYearRef): MeetingJoinLink = withContext(Dispatchers.IO) {
    requireConfigured()
    apiCall {
      gatewayService.joinMeeting(bookingId = bookingId, body = envelope(schoolYear))
    }
  }

  private suspend fun requireConfigured() {
    if (!isConfigured()) {
      throw ClassevivaNetworkException("Gateway non configurato. Imposta gatewayBaseUrl per abilitare le funzioni avanzate.")
    }
  }

  private fun <T> envelope(schoolYear: SchoolYearRef, payload: T? = null): GatewayEnvelope<T> {
    val credentials = sessionStorage.readStoredCredentials()
      ?: throw ClassevivaNetworkException("Credenziali protette non disponibili per il gateway.")
    return GatewayEnvelope(
      credentials = GatewayCredentialsDto(
        username = credentials.username,
        password = credentials.password,
      ),
      schoolYear = GatewaySchoolYearDto(
        startYear = schoolYear.startYear,
        endYear = schoolYear.endYear,
      ),
      payload = payload,
    )
  }

  private suspend fun <T> apiCall(block: suspend () -> T): T {
    return try {
      block()
    } catch (exception: HttpException) {
      val payload = runCatching { exception.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
      val message = when (exception.code()) {
        404 -> "Gateway Classeviva non ha trovato la risorsa richiesta."
        409 -> "Gateway Classeviva ha rilevato un conflitto nell'operazione richiesta."
        501 -> "Gateway Classeviva non supporta ancora questa funzione per il tenant corrente."
        else -> "Gateway Classeviva ha risposto con ${exception.code()}${payload.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""}"
      }
      throw ClassevivaNetworkException(message, exception)
    } catch (exception: Exception) {
      if (exception is ClassevivaNetworkException) throw exception
      throw ClassevivaNetworkException("Errore di rete durante la chiamata al gateway Classeviva.", exception)
    }
  }
}
