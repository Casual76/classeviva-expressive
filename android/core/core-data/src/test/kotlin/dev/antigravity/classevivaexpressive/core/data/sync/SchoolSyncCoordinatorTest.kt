package dev.antigravity.classevivaexpressive.core.data.sync

import android.util.Log
import dev.antigravity.classevivaexpressive.core.database.database.AbsenceDao
import dev.antigravity.classevivaexpressive.core.database.database.AgendaDao
import dev.antigravity.classevivaexpressive.core.database.database.AgendaItemEntity
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryDao
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationDao
import dev.antigravity.classevivaexpressive.core.database.database.CommunicationEntity
import dev.antigravity.classevivaexpressive.core.database.database.DocumentDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.MaterialDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.PortalClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  private val changeHistoryDao = mockk<ChangeHistoryDao>(relaxed = true)
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
    every { timetableTemplateStore.observeTemplate(currentYear.id) } returns flowOf(TimetableTemplate())
    every { predictiveTimetableUseCase.generateTimetableTemplate(any()) } returns TimetableTemplate()

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
      changeHistoryDao = changeHistoryDao,
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

  @Test
  fun acknowledgeCommunication_success() = runTest {
    val coordinator = buildCoordinator()
    val detail = CommunicationDetail(
      communication = Communication(
        id = "9873375",
        pubId = "25836170",
        evtCode = "CF",
        title = "Circolare",
        contentPreview = "Preview",
        sender = "Scuola",
        date = "20260520",
        read = false,
      ),
      content = "Dettaglio",
      acknowledgeUrl = "https://web.spaggiari.eu/sif/app/default/bacheca_personale.php",
    )

    coEvery { restClient.confirmNoticeboard(any()) } returns Unit
    coEvery { restClient.getCommunicationDetail(any()) } returns detail

    val result = coordinator.acknowledgeCommunication(detail)

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { restClient.confirmNoticeboard(detail.communication) }
    coVerify(exactly = 1) { communicationDao.markRead(detail.communication.id) }
  }

  @Test
  fun refreshHomeworks_usesAgendaHomeworkItemsWhenHomeworkEndpointIsEmpty() = runTest {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    val cache = mutableMapOf<String, SnapshotCacheEntity>()
    coEvery { snapshotCacheDao.upsert(any()) } answers {
      (invocation.args[0] as SnapshotCacheEntity).also { cache[it.cacheKey] = it }
    }
    coEvery { snapshotCacheDao.getByKey(any()) } answers { cache[invocation.args[0] as String] }
    coEvery { restClient.getHomeworks() } returns emptyList()
    coEvery { restClient.getAgenda("2025-09-01", "2026-08-31") } returns listOf(
      AgendaItem(
        id = "a1",
        title = "Portare matematica -- Studiare la dimostrazione ... pag 1274",
        subtitle = "MATEMATICA",
        date = "2026-05-07",
        subject = "MATEMATICA",
        category = AgendaCategory.HOMEWORK,
        createdAt = "2026-05-01T12:30",
      ),
      AgendaItem(
        id = "a2",
        title = "Rousseau pp. 518-521",
        subtitle = "FILOSOFIA",
        date = "2026-05-07",
        subject = "FILOSOFIA",
        category = AgendaCategory.HOMEWORK,
      ),
    )
    val coordinator = buildCoordinator()

    val result = coordinator.refreshHomeworks(force = true)

    assertEquals(2, result.size)
    assertTrue(result.any { it.description == "Portare matematica -- Studiare la dimostrazione ... pag 1274" })
    assertTrue(result.any { it.description == "Portare matematica -- Studiare la dimostrazione ... pag 1274" && it.createdAt == "2026-05-01T12:30" })
    assertTrue(result.any { it.description == "Rousseau pp. 518-521" })
  }

  @Test
  fun refreshHomeworks_keepsCreatedAtFromAgendaWhenRestHomeworkIsDuplicate() = runTest {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    val cache = mutableMapOf<String, SnapshotCacheEntity>()
    coEvery { snapshotCacheDao.upsert(any()) } answers {
      (invocation.args[0] as SnapshotCacheEntity).also { cache[it.cacheKey] = it }
    }
    coEvery { snapshotCacheDao.getByKey(any()) } answers { cache[invocation.args[0] as String] }
    coEvery { restClient.getHomeworks() } returns listOf(
      Homework(
        id = "hw1",
        subject = "MATEMATICA",
        description = "Esercizi pag. 45-47",
        dueDate = "2026-03-25",
      ),
    )
    coEvery { restClient.getAgenda("2025-09-01", "2026-08-31") } returns listOf(
      AgendaItem(
        id = "a1",
        title = "Esercizi pag. 45-47",
        subtitle = "MATEMATICA",
        date = "2026-03-25",
        subject = "MATEMATICA",
        category = AgendaCategory.HOMEWORK,
        createdAt = "2026-03-20T18:45",
        history = listOf(
          AgendaItemVersion(
            recordedAtEpochMillis = 1_776_000_000_000,
            title = "Esercizi pag. 45",
            subtitle = "MATEMATICA",
            date = "2026-03-25",
            subject = "MATEMATICA",
            category = AgendaCategory.HOMEWORK,
          ),
        ),
      ),
    )
    val coordinator = buildCoordinator()

    val result = coordinator.refreshHomeworks(force = true)

    assertEquals(1, result.size)
    assertEquals("2026-03-20T18:45", result.single().createdAt)
    assertEquals("Esercizi pag. 45", result.single().history.single().title)
  }

  @Test
  fun refreshAll_recordsPreviousGradeVersionWhenExistingGradeChanges() = runTest {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      GradeEntity(
        id = "g1",
        studentId = session.studentId,
        schoolYearId = currentYear.id,
        subject = "Matematica",
        valueLabel = "7",
        numericValue = 7.0,
        description = "Prima versione",
        date = "2026-03-20",
        type = "Scritto",
        weight = 1.0,
        notes = null,
        period = null,
        periodCode = null,
        teacher = "Prof Rossi",
        color = null,
      ),
    )
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    coEvery { restClient.getProfile() } returns session.profile
    coEvery { restClient.getGrades() } returns listOf(
      Grade(
        id = "g1",
        subject = "Matematica",
        valueLabel = "8",
        numericValue = 8.0,
        description = "Versione corretta",
        date = "2026-03-20",
        type = "Scritto",
        weight = 1.0,
        teacher = "Prof Rossi",
      ),
    )
    coEvery { restClient.getPeriods() } returns emptyList()
    coEvery { restClient.getSubjects() } returns emptyList()
    coEvery { restClient.getLessons(any(), any()) } returns emptyList()
    coEvery { restClient.getHomeworks() } returns emptyList()
    coEvery { restClient.getAgenda(any(), any()) } returns emptyList()
    coEvery { restClient.getAbsences(any(), any()) } returns emptyList()
    coEvery { restClient.getCommunications() } returns emptyList()
    coEvery { restClient.getNotes() } returns emptyList()
    coEvery { restClient.getMaterials() } returns emptyList()
    coEvery { restClient.getDocuments() } returns emptyList()
    coEvery { restClient.getSchoolbooks() } returns emptyList()
    coEvery { portalClient.getMeetingsPageHtml() } returns null
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify {
      changeHistoryDao.upsertAll(
        match { entries ->
          val version = Json.decodeFromString<GradeVersion>(entries.single().payload)
          entries.single().itemKind == "grade" &&
            entries.single().itemId == "g1" &&
            version.valueLabel == "7" &&
            version.description == "Prima versione"
        },
      )
    }
  }

  @Test
  fun refreshAll_preservesLocalGradeIdWhenRemoteIdChangesAndContentChanges() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      gradeEntity(
        id = "local-g1",
        subject = "Matematica",
        valueLabel = "7",
        numericValue = 7.0,
        description = "Prima versione",
        date = "2026-03-20",
        type = "Scritto",
      ),
    )
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    stubFullRefresh(
      grades = listOf(
        Grade(
          id = "remote-g1-new",
          subject = "Matematica",
          valueLabel = "8",
          numericValue = 8.0,
          description = "Versione corretta",
          date = "2026-03-20",
          type = "Scritto",
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify {
      gradeDao.upsertAll(
        match { entities ->
          entities.single().id == "local-g1" &&
            entities.single().valueLabel == "8"
        },
      )
    }
    coVerify {
      changeHistoryDao.upsertAll(
        match { entries ->
          val version = Json.decodeFromString<GradeVersion>(entries.single().payload)
          entries.single().itemKind == "grade" &&
            entries.single().itemId == "local-g1" &&
            version.valueLabel == "7" &&
            version.description == "Prima versione"
        },
      )
    }
  }

  @Test
  fun refreshAll_ignoresDerivedGradeChangesWhenCoreContentIsSame() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      gradeEntity(
        id = "g1",
        subject = "Matematica",
        valueLabel = "7",
        numericValue = 7.0,
        description = "Versione",
        date = "2026-03-20",
        type = "Scritto",
        teacher = null,
        color = null,
      ),
    )
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    stubFullRefresh(
      grades = listOf(
        Grade(
          id = "g1",
          subject = "Matematica",
          valueLabel = "7",
          numericValue = 7.0,
          description = "Versione",
          date = "2026-03-20",
          type = "Scritto",
          teacher = "Prof Rossi",
          color = "#FF0000",
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify(exactly = 0) { changeHistoryDao.upsertAll(any()) }
  }

  @Test
  fun refreshAll_ignoresGradeParserBaselineChangesAfterAppUpdate() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      gradeEntity(
        id = "g1",
        subject = "Matematica",
        valueLabel = "7.00",
        numericValue = 7.0,
        description = "Interrogazione",
        date = "2026-03-20",
        type = "Valutazione",
        weight = null,
        notes = null,
        period = null,
        periodCode = null,
      ),
    )
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    stubFullRefresh(
      grades = listOf(
        Grade(
          id = "g1",
          subject = "matematica",
          valueLabel = "7",
          numericValue = 7.0,
          description = "Interrogazione",
          date = "2026-03-20",
          type = "Orale",
          weight = 1.0,
          period = "Primo trimestre",
          periodCode = "P1",
          teacher = "Prof Rossi",
          color = "#FF0000",
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify(exactly = 0) { changeHistoryDao.upsertAll(any()) }
  }

  @Test
  fun refreshAll_recordsAgendaHistoryWithStableLocalIdWhenRemoteIdChanges() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      agendaEntity(
        id = "local-a1",
        title = "Verifica capitolo 1",
        date = "2026-04-10",
        detail = "Ripassare capitolo 1",
        subject = "Storia",
        category = AgendaCategory.ASSESSMENT,
      ),
    )
    stubFullRefresh(
      agenda = listOf(
        AgendaItem(
          id = "remote-a1-new",
          title = "Verifica capitoli 1-2",
          subtitle = "Storia",
          date = "2026-04-10",
          detail = "Ripassare capitoli 1 e 2",
          subject = "Storia",
          category = AgendaCategory.ASSESSMENT,
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify {
      agendaDao.upsertAll(
        match { entities ->
          entities.single().id == "local-a1" &&
            entities.single().title == "Verifica capitoli 1-2"
        },
      )
    }
    coVerify {
      changeHistoryDao.upsertAll(
        match { entries ->
          val version = Json.decodeFromString<AgendaItemVersion>(entries.single().payload)
          entries.single().itemKind == "agenda" &&
            entries.single().itemId == "local-a1" &&
            version.title == "Verifica capitolo 1" &&
            version.detail == "Ripassare capitolo 1"
        },
      )
    }
  }

  @Test
  fun refreshAll_recordsAgendaHistoryWhenTeacherAddsTextAfterBaseline() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      agendaEntity(
        id = "a1",
        title = "Verifica",
        subtitle = "Storia",
        date = "2026-04-10",
        detail = null,
        subject = "Storia",
        category = AgendaCategory.ASSESSMENT,
        firstSeenAtMs = 1_000L,
      ),
    )
    stubFullRefresh(
      agenda = listOf(
        AgendaItem(
          id = "a1",
          title = "Verifica",
          subtitle = "Storia",
          date = "2026-04-10",
          detail = "Ripassare capitolo 1",
          subject = "Storia",
          category = AgendaCategory.ASSESSMENT,
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify {
      changeHistoryDao.upsertAll(
        match { entries ->
          val version = Json.decodeFromString<AgendaItemVersion>(entries.single().payload)
          entries.single().itemKind == "agenda" &&
            entries.single().itemId == "a1" &&
            version.detail == null
        },
      )
    }
  }

  @Test
  fun refreshAll_matchesAgendaByDateCategoryAndTimeWhenSubjectIsMissing() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      agendaEntity(
        id = "local-a1",
        title = "Verifica capitolo 1",
        subtitle = "",
        date = "2026-04-10",
        time = "10:00",
        detail = "Ripassare capitolo 1",
        subject = null,
        category = AgendaCategory.ASSESSMENT,
      ),
    )
    stubFullRefresh(
      agenda = listOf(
        AgendaItem(
          id = "remote-a1-new",
          title = "Verifica capitoli 1-2",
          subtitle = "",
          date = "2026-04-10",
          time = "10:00",
          detail = "Ripassare capitoli 1 e 2",
          subject = null,
          category = AgendaCategory.ASSESSMENT,
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify {
      agendaDao.upsertAll(
        match { entities ->
          entities.single().id == "local-a1" &&
            entities.single().title == "Verifica capitoli 1-2"
        },
      )
    }
    coVerify {
      changeHistoryDao.upsertAll(
        match { entries ->
          val version = Json.decodeFromString<AgendaItemVersion>(entries.single().payload)
          entries.single().itemKind == "agenda" &&
            entries.single().itemId == "local-a1" &&
            version.title == "Verifica capitolo 1"
        },
      )
    }
  }

  @Test
  fun refreshAll_doesNotUseSemanticMatchWhenGradeIdentityIsAmbiguous() = runTest {
    stubLog()
    coEvery { gradeDao.getByYearOnce(session.studentId, currentYear.id) } returns listOf(
      gradeEntity(id = "g1", subject = "Matematica", valueLabel = "7", numericValue = 7.0, date = "2026-03-20", type = "Scritto"),
      gradeEntity(id = "g2", subject = "Matematica", valueLabel = "8", numericValue = 8.0, date = "2026-03-20", type = "Scritto"),
    )
    coEvery { agendaDao.getByYearOnce(session.studentId, currentYear.id) } returns emptyList()
    stubFullRefresh(
      grades = listOf(
        Grade(
          id = "remote-ambiguous",
          subject = "Matematica",
          valueLabel = "9",
          numericValue = 9.0,
          date = "2026-03-20",
          type = "Scritto",
        ),
      ),
    )
    val coordinator = buildCoordinator()

    coordinator.refreshAll(force = true)

    coVerify {
      gradeDao.upsertAll(
        match { entities ->
          entities.single().id == "remote-ambiguous"
        },
      )
    }
    coVerify(exactly = 0) { changeHistoryDao.upsertAll(any()) }
  }

  @Test
  fun getCommunicationDetail_marksReadWhenOpeningDetail() = runTest {
    val coordinator = buildCoordinator()
    val entity = communicationEntity(id = "2690", pubId = "2690", evtCode = "CIR")
    val base = Communication(
      id = "2690",
      pubId = "2690",
      evtCode = "CIR",
      title = "4F - entrata posticipata l'8/5",
      contentPreview = "Preview",
      sender = "Scuola",
      date = "2026-05-07",
      read = false,
    )
    val detail = CommunicationDetail(
      communication = base,
      content = "La classe 4F entrerà alle ore 9:10 il giorno 8/5.",
    )
    coEvery { communicationDao.getByPubIdAndEvtCode("2690", "CIR") } returns entity
    coEvery { restClient.getCommunicationDetail(any<Communication>()) } returns detail

    val result = coordinator.getCommunicationDetail(pubId = "2690", evtCode = "CIR")

    assertTrue(result.isSuccess)
    assertFalse(result.getOrThrow().communication.read)
    coVerify(exactly = 1) { communicationDao.markRead("2690") }
  }

  private fun stubLog() {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
  }

  private fun stubFullRefresh(
    grades: List<Grade> = emptyList(),
    agenda: List<AgendaItem> = emptyList(),
  ) {
    coEvery { restClient.getProfile() } returns session.profile
    coEvery { restClient.getGrades() } returns grades
    coEvery { restClient.getPeriods() } returns emptyList()
    coEvery { restClient.getSubjects() } returns emptyList()
    coEvery { restClient.getLessons(any(), any()) } returns emptyList()
    coEvery { restClient.getHomeworks() } returns emptyList()
    coEvery { restClient.getAgenda(any(), any()) } returns agenda
    coEvery { restClient.getAbsences(any(), any()) } returns emptyList()
    coEvery { restClient.getCommunications() } returns emptyList()
    coEvery { restClient.getNotes() } returns emptyList()
    coEvery { restClient.getMaterials() } returns emptyList()
    coEvery { restClient.getDocuments() } returns emptyList()
    coEvery { restClient.getSchoolbooks() } returns emptyList()
    coEvery { portalClient.getMeetingsPageHtml() } returns null
  }

  private fun gradeEntity(
    id: String,
    subject: String,
    valueLabel: String,
    numericValue: Double?,
    date: String,
    type: String,
    description: String? = null,
    weight: Double? = null,
    notes: String? = null,
    period: String? = null,
    periodCode: String? = null,
    teacher: String? = null,
    color: String? = null,
  ) = GradeEntity(
    id = id,
    studentId = session.studentId,
    schoolYearId = currentYear.id,
    subject = subject,
    valueLabel = valueLabel,
    numericValue = numericValue,
    description = description,
    date = date,
    type = type,
    weight = weight,
    notes = notes,
    period = period,
    periodCode = periodCode,
    teacher = teacher,
    color = color,
  )

  private fun agendaEntity(
    id: String,
    title: String,
    date: String,
    category: AgendaCategory,
    subtitle: String = "",
    time: String? = null,
    detail: String? = null,
    subject: String? = null,
    teacher: String? = null,
    sharePayload: String? = null,
    createdAt: String? = null,
    firstSeenAtMs: Long? = null,
  ) = AgendaItemEntity(
    id = id,
    studentId = session.studentId,
    schoolYearId = currentYear.id,
    title = title,
    subtitle = subtitle,
    date = date,
    time = time,
    detail = detail,
    subject = subject,
    teacher = teacher,
    category = category.name,
    sharePayload = sharePayload,
    createdAt = createdAt,
    firstSeenAtMs = firstSeenAtMs,
  )

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
