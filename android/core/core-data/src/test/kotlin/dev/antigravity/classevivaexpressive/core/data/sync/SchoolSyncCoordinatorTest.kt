package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.database.database.AbsenceDao
import dev.antigravity.classevivaexpressive.core.database.database.AgendaDao
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationDao
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationEntity
import dev.antigravity.classevivaexpressive.core.database.database.DocumentDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.MaterialDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.PortalClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSyncCoordinatorTest {

  private val restClient = mockk<ClassevivaRestClient>(relaxed = true)
  private val portalClient = mockk<PortalClient>(relaxed = true)
  private val sessionStore = mockk<SessionStore>(relaxed = true)
  private val schoolYearStore = mockk<SchoolYearStore>(relaxed = true)
  private val timetableTemplateStore = mockk<TimetableTemplateStore>(relaxed = true)
  private val snapshotCacheDao = mockk<SnapshotCacheDao>(relaxed = true)
  private val gradeDao = mockk<GradeDao>(relaxed = true)
  private val agendaDao = mockk<AgendaDao>(relaxed = true)
  private val absenceDao = mockk<AbsenceDao>(relaxed = true)
  private val communicationDao = mockk<CommunicationDao>(relaxed = true)
  private val materialDao = mockk<MaterialDao>(relaxed = true)
  private val documentDao = mockk<DocumentDao>(relaxed = true)
  private val predictiveTimetableUseCase = mockk<PredictiveTimetableUseCase>(relaxed = true)

  private val currentYear = SchoolYearRef(startYear = 2025, endYear = 2026)
  private val session = UserSession(
    token = "token",
    studentId = "55",
    username = "student",
    profile = StudentProfile(id = "55", name = "Nome", surname = "Cognome"),
  )

  private fun buildCoordinator(): SchoolSyncCoordinator {
    every { schoolYearStore.observeSelectedSchoolYear() } returns flowOf(currentYear)
    every { schoolYearStore.currentSchoolYearRef() } returns currentYear

    return SchoolSyncCoordinator(
      json = Json,
      restClient = restClient,
      portalClient = portalClient,
      sessionStore = sessionStore,
      schoolYearStore = schoolYearStore,
      timetableTemplateStore = timetableTemplateStore,
      snapshotCacheDao = snapshotCacheDao,
      gradeDao = gradeDao,
      agendaDao = agendaDao,
      absenceDao = absenceDao,
      communicationDao = communicationDao,
      materialDao = materialDao,
      documentDao = documentDao,
      predictiveTimetableUseCase = predictiveTimetableUseCase,
    ).also {
      it.attachSession(session)
    }
  }

  @Test
  fun markAllCommunicationsReadRemotely_marksOnlySuccessfulItemsAndFailsWhenSomeCallsFail() = runTest {
    val coordinator = buildCoordinator()
    val first = communicationEntity(id = "ok-1", pubId = "11", evtCode = "CIR")
    val second = communicationEntity(id = "ko-2", pubId = "22", evtCode = "CIR")

    coEvery { communicationDao.getUnread(session.studentId, currentYear.id) } returns listOf(first, second)
    coEvery { restClient.markNoticeboardRead(pubId = "11", evtCode = "CIR") } returns Unit
    coEvery { restClient.markNoticeboardRead(pubId = "22", evtCode = "CIR") } throws
      ClassevivaNetworkException("Errore remoto")

    val result = coordinator.markAllCommunicationsReadRemotely()

    assertTrue(result.isFailure)
    coVerify(exactly = 1) { communicationDao.markRead("ok-1") }
    coVerify(exactly = 0) { communicationDao.markRead("ko-2") }
    coVerify(exactly = 0) { communicationDao.markAllRead(any(), any()) }
  }

  @Test
  fun markAllCommunicationsReadRemotely_marksLocalOnlyEntriesWithoutCallingRest() = runTest {
    val coordinator = buildCoordinator()
    val localOnly = communicationEntity(id = "local-1", pubId = "", evtCode = "")

    coEvery { communicationDao.getUnread(session.studentId, currentYear.id) } returns listOf(localOnly)

    val result = coordinator.markAllCommunicationsReadRemotely()

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { communicationDao.markRead("local-1") }
    coVerify(exactly = 0) { restClient.markNoticeboardRead(any(), any()) }
  }

  private fun communicationEntity(
    id: String,
    pubId: String,
    evtCode: String,
  ) = CommunicationEntity(
    id = id,
    studentId = session.studentId,
    schoolYearId = currentYear.id,
    pubId = pubId,
    evtCode = evtCode,
    title = "Circolare $id",
    contentPreview = "Preview",
    sender = "Scuola",
    date = "2026-03-20",
    read = false,
    attachments = "[]",
    category = null,
    needsAck = false,
    needsReply = false,
    needsJoin = false,
    needsFile = false,
    actions = "[]",
    noticeboardAttachments = "[]",
    capabilityState = """{"status":"AVAILABLE","label":"Dettaglio disponibile"}""",
  )
}
