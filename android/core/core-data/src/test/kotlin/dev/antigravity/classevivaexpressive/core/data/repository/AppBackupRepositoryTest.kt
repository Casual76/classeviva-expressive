package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.data.preview.PrivateSessionDataCleaner
import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.data.sync.captureAccountWork
import dev.antigravity.classevivaexpressive.core.database.database.BackupTransactionRunner
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryDao
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryEntity
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreDao
import dev.antigravity.classevivaexpressive.core.database.database.SubjectGoalDao
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.Collections
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppBackupRepositoryTest {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

  private val settingsStore = mockk<SettingsStore>(relaxed = true)
  private val schoolYearStore = mockk<SchoolYearStore>(relaxed = true)
  private val timetableTemplateStore = mockk<TimetableTemplateStore>(relaxed = true)
  private val customEventDao = mockk<CustomEventDao>(relaxed = true)
  private val seenGradeDao = mockk<SeenGradeDao>(relaxed = true)
  private val subjectGoalDao = mockk<SubjectGoalDao>(relaxed = true)
  private val studentScoreDao = mockk<StudentScoreDao>(relaxed = true)
  private val gradeDao = mockk<GradeDao>(relaxed = true)
  private val changeHistoryDao = mockk<ChangeHistoryDao>(relaxed = true)
  private val snapshotCacheDao = mockk<SnapshotCacheDao>(relaxed = true)

  /** Esegue il blocco e registra che c'e' stata una transazione: basta a provare l'atomicita'. */
  private class RecordingTransactionRunner : BackupTransactionRunner {
    var started = 0
    var inside = false
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
      started++
      inside = true
      try {
        return block()
      } finally {
        inside = false
      }
    }
  }

  private val transactionRunner = RecordingTransactionRunner()

  private fun repository(sessionStore: SessionStore = testSessionStore()) = DefaultAppBackupRepository(
    json = json,
    settingsStore = settingsStore,
    schoolYearStore = schoolYearStore,
    timetableTemplateStore = timetableTemplateStore,
    customEventDao = customEventDao,
    seenGradeDao = seenGradeDao,
    subjectGoalDao = subjectGoalDao,
    studentScoreDao = studentScoreDao,
    gradeDao = gradeDao,
    changeHistoryDao = changeHistoryDao,
    snapshotCacheDao = snapshotCacheDao,
    transactionRunner = transactionRunner,
    sessionStore = sessionStore,
  )

  private fun grade(
    id: String,
    year: String = "2024-2025",
    studentId: String = "student-1",
    numericValue: Double? = 8.5,
  ) = GradeEntity(
    id = id,
    studentId = studentId,
    schoolYearId = year,
    subject = "FILOSOFIA",
    valueLabel = numericValue?.toString() ?: "N.C.",
    numericValue = numericValue,
    description = "Interrogazione",
    date = "2025-03-14",
    type = "Orale",
    weight = 1.0,
    notes = "note",
    period = "Pentamestre",
    periodCode = "P2",
    teacher = "ROSSI",
    color = "green",
    firstSeenAtMs = 1_700_000_000_000L,
  )

  private fun stubEmptyStores() {
    coEvery { settingsStore.readSettings() } returns AppSettings()
    coEvery { schoolYearStore.selectedSchoolYear() } returns SchoolYearRef(2024, 2025)
    coEvery { timetableTemplateStore.readAllTemplates() } returns emptyMap()
    coEvery { customEventDao.getAll() } returns emptyList()
    coEvery { seenGradeDao.getAll() } returns emptyList()
    coEvery { subjectGoalDao.getAll() } returns emptyList()
    coEvery { studentScoreDao.getAll() } returns emptyList()
    coEvery { changeHistoryDao.getAllByKind(any()) } returns emptyList()
    coEvery { snapshotCacheDao.getByKeys(any()) } returns emptyList()
  }

  @Test
  fun gradesRoundTripThroughExportAndImport() = runTest {
    val original = listOf(
      grade("g1"),
      grade("g2", year = "2025-2026", numericValue = null),
    )
    stubEmptyStores()
    coEvery { gradeDao.getAll() } returns original

    val payload = repository().exportBackup().getOrThrow()

    val restored = slot<List<GradeEntity>>()
    coEvery { gradeDao.upsertAll(capture(restored)) } returns Unit

    val summary = repository().importBackup(payload).getOrThrow()

    assertEquals(2, summary.grades)
    assertEquals(listOf("2024-2025", "2025-2026"), summary.gradeSchoolYears)
    // Campo per campo: e' il test centrale del lotto, e l'unico che prende una mappatura sbagliata.
    assertEquals(original, restored.captured)
  }

  @Test
  fun gradeHistoryRoundTrips() = runTest {
    val history = listOf(
      ChangeHistoryEntity(
        id = "h1",
        studentId = "student-1",
        schoolYearId = "2024-2025",
        itemKind = HistoryKindGrade,
        itemId = "g1",
        recordedAtEpochMillis = 1_700_000_000_000L,
        payload = """{"valueLabel":"7"}""",
      ),
    )
    stubEmptyStores()
    coEvery { gradeDao.getAll() } returns listOf(grade("g1"))
    coEvery { changeHistoryDao.getAllByKind(HistoryKindGrade) } returns history

    val payload = repository().exportBackup().getOrThrow()
    val restored = mutableListOf<List<ChangeHistoryEntity>>()
    coEvery { changeHistoryDao.upsertAll(capture(restored)) } returns Unit

    val summary = repository().importBackup(payload).getOrThrow()

    assertEquals(1, summary.gradeHistory)
    assertEquals(history, restored.flatten())
  }

  @Test
  fun agendaAndHomeworkHistoryRoundTripWithTheirKind() = runTest {
    fun entry(id: String, kind: String) = ChangeHistoryEntity(
      id = id,
      studentId = "student-1",
      schoolYearId = "2026-2027",
      itemKind = kind,
      itemId = "item-$id",
      recordedAtEpochMillis = 1_790_000_000_000L,
      payload = """{"title":"Pag 1371"}""",
    )
    val agenda = listOf(entry("a1", HistoryKindAgenda))
    val homework = listOf(entry("h1", HistoryKindHomework))
    stubEmptyStores()
    coEvery { changeHistoryDao.getAllByKind(HistoryKindAgenda) } returns agenda
    coEvery { changeHistoryDao.getAllByKind(HistoryKindHomework) } returns homework

    val payload = repository().exportBackup().getOrThrow()
    val restored = mutableListOf<List<ChangeHistoryEntity>>()
    coEvery { changeHistoryDao.upsertAll(capture(restored)) } returns Unit
    repository().importBackup(payload).getOrThrow()

    assertEquals(agenda + homework, restored.flatten())
  }

  @Test
  fun v1PayloadStillImports() = runTest {
    // Una stringa v1 letterale, come l'avrebbe scritta la versione precedente. E' l'unico modo di
    // rendere verificabile la promessa di retro-compatibilita' invece che sperata.
    val v1 = """
      {
        "version": 1,
        "exportedAtEpochMillis": 1700000000000,
        "settings": {},
        "selectedSchoolYearId": "2024-2025",
        "timetableTemplates": {},
        "customEvents": [],
        "seenGrades": [],
        "subjectGoals": [],
        "scoreSnapshots": []
      }
    """.trimIndent()

    val summary = repository().importBackup(v1).getOrThrow()

    assertEquals(0, summary.grades)
    assertEquals(0, summary.gradeHistory)
    assertTrue(summary.settingsImported)
    assertTrue(summary.gradeSchoolYears.isEmpty())
  }

  @Test
  fun futureVersionIsRejected() = runTest {
    val result = repository().importBackup("""{"version": 99}""")
    assertTrue(result.isFailure)
    assertTrue(
      result.exceptionOrNull()?.message.orEmpty().contains("versione non supportata"),
    )
  }

  @Test
  fun zeroOrNegativeVersionIsRejected() = runTest {
    assertTrue(repository().importBackup("""{"version": 0}""").isFailure)
    assertTrue(repository().importBackup("""{"version": -1}""").isFailure)
  }

  @Test
  fun gradeEntryDecodesWithOnlyItsRequiredFields() {
    // Se qualcuno toglie un `= null` da GradeBackupEntry, questo test cade. E' la difesa del
    // disaccoppiamento fra il file e lo schema del database.
    val minimal = """
      {
        "id": "g1",
        "studentId": "student-1",
        "schoolYearId": "2024-2025",
        "subject": "STORIA",
        "valueLabel": "7",
        "date": "2025-03-14",
        "type": "Orale"
      }
    """.trimIndent()

    val entry = json.decodeFromString<GradeBackupEntry>(minimal)

    assertEquals("g1", entry.id)
    assertEquals(null, entry.numericValue)
    assertEquals(null, entry.firstSeenAtMs)
  }

  @Test
  fun importRebuildsTheGradesSnapshot() = runTest {
    stubEmptyStores()
    coEvery { gradeDao.getAll() } returns listOf(grade("g1"), grade("g2"))
    val payload = repository().exportBackup().getOrThrow()

    val written = mutableListOf<SnapshotCacheEntity>()
    coEvery { snapshotCacheDao.upsert(capture(written)) } returns Unit

    repository().importBackup(payload).getOrThrow()

    val snapshot = written.firstOrNull { it.cacheKey == "student-1::2024-2025::grades" }
    assertNotNull("lo snapshot dei voti va ricostruito al restore", snapshot)
    val decoded = json.decodeFromString<List<Grade>>(snapshot!!.payload)
    assertEquals(listOf("g1", "g2"), decoded.map { it.id })
  }

  @Test
  fun importRunsEveryRoomWriteInOneTransaction() = runTest {
    stubEmptyStores()
    coEvery { gradeDao.getAll() } returns listOf(grade("g1"))
    val payload = repository().exportBackup().getOrThrow()

    var gradesWrittenInsideTransaction = false
    coEvery { gradeDao.upsertAll(any()) } answers { gradesWrittenInsideTransaction = transactionRunner.inside }

    repository().importBackup(payload).getOrThrow()

    assertEquals(1, transactionRunner.started)
    assertTrue("i voti vanno scritti dentro la transazione", gradesWrittenInsideTransaction)
    coVerify(exactly = 1) { gradeDao.upsertAll(any()) }
  }

  @Test
  fun importReportsGradesFromAnotherStudent() = runTest {
    stubEmptyStores()
    coEvery { gradeDao.getAll() } returns listOf(
      grade("g1", studentId = "student-1"),
      grade("g2", studentId = "student-2"),
      grade("g3", studentId = "student-2"),
    )
    val payload = repository().exportBackup().getOrThrow()

    val summary = repository().importBackup(payload).getOrThrow()

    assertEquals(3, summary.grades)
    assertEquals(2, summary.skippedForeignStudentGrades)
  }

  @Test
  fun exportCarriesPeriodsAndSubjectsOfEveryYearWithGrades() = runTest {
    stubEmptyStores()
    coEvery { gradeDao.getAll() } returns listOf(grade("g1"), grade("g2", year = "2025-2026"))
    coEvery { snapshotCacheDao.getByKeys(any()) } returns listOf(
      SnapshotCacheEntity("student-1::2024-2025::periods", """[{"code":"P2"}]""", 1L),
      SnapshotCacheEntity("student-1::2025-2026::subjects", """[{"name":"STORIA"}]""", 2L),
    )

    val payload = repository().exportBackup().getOrThrow()

    // Le chiavi richieste sono le due sezioni per ognuno dei due anni.
    val requested = slot<List<String>>()
    coVerify { snapshotCacheDao.getByKeys(capture(requested)) }
    assertEquals(
      listOf(
        "student-1::2024-2025::periods",
        "student-1::2024-2025::subjects",
        "student-1::2025-2026::periods",
        "student-1::2025-2026::subjects",
      ),
      requested.captured,
    )
    assertTrue(payload.contains("gradeMetadata"))

    val restored = mutableListOf<SnapshotCacheEntity>()
    coEvery { snapshotCacheDao.upsert(capture(restored)) } returns Unit
    repository().importBackup(payload).getOrThrow()

    assertTrue(restored.any { it.cacheKey == "student-1::2024-2025::periods" })
    assertTrue(restored.any { it.cacheKey == "student-1::2025-2026::subjects" })
  }

  @Test
  fun admittedBlockingSafWriteKeepsLogoutWaitingEvenWhenExportCallerIsCancelled() = runTest {
    stubEmptyStores()
    val sessionStore = testSessionStore()
    val writeStarted = CompletableDeferred<Unit>()
    val releaseWrite = CountDownLatch(1)
    val logoutRequested = CompletableDeferred<Unit>()
    val order = Collections.synchronizedList(mutableListOf<String>())
    val export = async {
      repository(sessionStore).exportBackupTo(sessionStore.accountGuard.authenticationEpoch()) {
        writeStarted.complete(Unit)
        // Like OutputStream.write: ordinary coroutine cancellation cannot interrupt it.
        releaseWrite.await()
        order += "write completed"
      }
    }
    try {
      writeStarted.await()
      export.cancel()
      val logout = async {
        logoutRequested.complete(Unit)
        sessionStore.clearAndRun { order += "logout cleanup" }
      }
      logoutRequested.await()
      assertFalse(logout.isCompleted)
      assertEquals("student-1", sessionStore.readCurrentSession()!!.studentId)
      releaseWrite.countDown()
      export.join()
      logout.await()
      assertEquals(listOf("write completed", "logout cleanup"), order.toList())
      assertEquals(null, sessionStore.readCurrentSession())
    } finally { releaseWrite.countDown() }
  }

  @Test
  fun exportSerializationThatLosesOwnershipCannotOpenTheSafDestination() = runTest {
    stubEmptyStores()
    val sessionStore = testSessionStore()
    val original = sessionStore.readAuthSnapshot()!!
    val readStarted = CompletableDeferred<Unit>()
    val releaseRead = CompletableDeferred<Unit>()
    coEvery { gradeDao.getAll() } coAnswers {
      readStarted.complete(Unit)
      releaseRead.await()
      emptyList()
    }
    var destinationOpened = false
    val export = async { repository(sessionStore).exportBackupTo(sessionStore.accountGuard.authenticationEpoch()) { destinationOpened = true } }
    readStarted.await()
    sessionStore.clearAndRun {}
    sessionStore.writeAuthSnapshot(original.copy(session = original.session!!.copy(studentId = "student-b")))
    releaseRead.complete(Unit)

    assertTrue(export.await().isFailure)
    assertFalse(destinationOpened)
    assertEquals("student-b", sessionStore.readCurrentSession()!!.studentId)
  }

  @Test
  fun importReaderFromPreviousAccountCannotCommitSettingsTemplatesOrRowsToReplacement() = runTest {
    assertSuspendedReaderCannotAdoptReplacement(sameStudent = false)
  }

  @Test
  fun importReaderFromPreviousLoginCannotCommitAfterSameStudentReentry() = runTest {
    assertSuspendedReaderCannotAdoptReplacement(sameStudent = true)
  }

  private suspend fun assertSuspendedReaderCannotAdoptReplacement(sameStudent: Boolean) = kotlinx.coroutines.coroutineScope {
    stubEmptyStores()
    val sessionStore = testSessionStore()
    val original = sessionStore.readAuthSnapshot()!!
    val readStarted = CompletableDeferred<Unit>()
    val releaseRead = CompletableDeferred<Unit>()
    val pendingImport = async {
      repository(sessionStore).importBackupFrom(sessionStore.accountGuard.authenticationEpoch()) {
        readStarted.complete(Unit)
        releaseRead.await()
        """{"version":3,"settings":{},"selectedSchoolYearId":"2024-2025","timetableTemplates":{"2024-2025":{}}}"""
      }
    }
    readStarted.await()
    sessionStore.clearAndRun {}
    val replacement = if (sameStudent) original else original.copy(session = original.session!!.copy(studentId = "student-b"))
    sessionStore.writeAuthSnapshot(replacement)
    releaseRead.complete(Unit)

    assertTrue(pendingImport.await().isFailure)
    coVerify(exactly = 0) { settingsStore.writeSettings(any()) }
    coVerify(exactly = 0) { schoolYearStore.selectSchoolYear(any()) }
    coVerify(exactly = 0) { timetableTemplateStore.writeAllTemplates(any()) }
    coVerify(exactly = 0) { customEventDao.upsertAll(any()) }
    coVerify(exactly = 0) { seenGradeDao.upsertAll(any()) }
    coVerify(exactly = 0) { subjectGoalDao.upsertAll(any()) }
    coVerify(exactly = 0) { studentScoreDao.upsertAll(any()) }
    coVerify(exactly = 0) { gradeDao.upsertAll(any()) }
    assertEquals(0, transactionRunner.started)
    assertEquals(replacement, sessionStore.readAuthSnapshot())
  }

  @Test
  fun safCallbacksPropagateCancellationAndDoNotTurnItIntoFailureResults() = runTest {
    stubEmptyStores()
    val store = testSessionStore()
    val epoch = store.accountGuard.authenticationEpoch()
    val exporterCancellation = runCatching {
      repository(store).exportBackupTo(epoch) { throw CancellationException("cancelled") }
    }.exceptionOrNull()
    val readerCancellation = runCatching {
      repository(store).importBackupFrom(epoch) { throw CancellationException("cancelled") }
    }.exceptionOrNull()
    assertTrue(exporterCancellation is CancellationException)
    assertTrue(readerCancellation is CancellationException)
    coVerify(exactly = 0) { settingsStore.writeSettings(any()) }
  }

  @Test
  fun cancelledBlockingSafReadCannotCommitItsLatePayload() = runTest {
    stubEmptyStores()
    val readStarted = CompletableDeferred<Unit>()
    val releaseRead = CountDownLatch(1)
    val store = testSessionStore()
    val epoch = store.accountGuard.authenticationEpoch()
    val pending = async {
      repository(store).importBackupFrom(epoch) {
        readStarted.complete(Unit)
        releaseRead.await()
        """{"version":3,"settings":{}}"""
      }
    }
    try {
      readStarted.await()
      pending.cancel()
      releaseRead.countDown()
      pending.join()
      assertTrue(pending.isCancelled)
      coVerify(exactly = 0) { settingsStore.writeSettings(any()) }
      coVerify(exactly = 0) { timetableTemplateStore.writeAllTemplates(any()) }
      coVerify(exactly = 0) { customEventDao.upsertAll(any()) }
      assertEquals(0, transactionRunner.started)
    } finally { releaseRead.countDown() }
  }

  @Test
  fun safAndMalformedPayloadFailuresDoNotExposeProviderPathsOrPayloadText() = runTest {
    stubEmptyStores()
    val store = testSessionStore()
    val epoch = store.accountGuard.authenticationEpoch()
    val exported = repository(store).exportBackupTo(epoch) { throw java.io.IOException("content://private/file personal-data") }
    val imported = repository(store).importBackupFrom(epoch) { "personal-data secret malformed payload" }
    assertEquals("Esportazione backup non riuscita.", exported.exceptionOrNull()?.message)
    assertEquals("Importazione backup non riuscita.", imported.exceptionOrNull()?.message)
    assertEquals(null, exported.exceptionOrNull()?.cause)
    assertEquals(null, imported.exceptionOrNull()?.cause)
  }

  @Test
  fun pickerResultFromAnotherAccountCannotOpenAnySafReaderOrWriter() = runTest {
    assertStalePickerDoesNotOpenSaf(sameStudent = false)
  }

  @Test
  fun pickerResultFromPreviousLoginOfSameStudentCannotOpenAnySafReaderOrWriter() = runTest {
    assertStalePickerDoesNotOpenSaf(sameStudent = true)
  }

  private suspend fun assertStalePickerDoesNotOpenSaf(sameStudent: Boolean) {
    val sessionStore = testSessionStore()
    val pickerEpoch = sessionStore.accountGuard.authenticationEpoch()
    val original = sessionStore.readAuthSnapshot()!!
    sessionStore.clearAndRun {}
    val replacement = if (sameStudent) original else original.copy(session = original.session!!.copy(studentId = "student-b"))
    sessionStore.writeAuthSnapshot(replacement)
    var readerOpened = false
    var writerOpened = false

    val export = repository(sessionStore).exportBackupTo(pickerEpoch) { writerOpened = true }
    val imported = repository(sessionStore).importBackupFrom(pickerEpoch) {
      readerOpened = true
      """{"version":3,"settings":{}}"""
    }

    assertTrue(export.isFailure)
    assertTrue(imported.isFailure)
    assertFalse(readerOpened)
    assertFalse(writerOpened)
    coVerify(exactly = 0) { gradeDao.getAll() }
    coVerify(exactly = 0) { settingsStore.writeSettings(any()) }
    coVerify(exactly = 0) { timetableTemplateStore.writeAllTemplates(any()) }
    coVerify(exactly = 0) { customEventDao.upsertAll(any()) }
    assertEquals(replacement, sessionStore.readAuthSnapshot())
  }

  @Test
  fun realMainLogoutKeepsHeartbeatRunningWhileAnAdmittedSafWriterOwnsTheGate() = runTest {
    withDedicatedMain {
      withBlockedSafWriter { store, _, releaseWrite, order ->
        val sync = mockk<SchoolSyncCoordinator>(relaxed = true)
        val cleaner = mockk<PrivateSessionDataCleaner>(relaxed = true)
        coEvery { cleaner.clear("student-1") } coAnswers { order += "logout cleanup" }
        val auth = SchoolAuthRepository(store, sync, mockk(relaxed = true), cleaner)
        val logoutEntered = CompletableDeferred<Unit>()
        val logout = async(Dispatchers.Main) {
          logoutEntered.complete(Unit)
          auth.logout()
        }
        // This timeout runs on a real clock, independently of the Main thread being tested.
        withContext(Dispatchers.Default) { withTimeout(5_000) { logoutEntered.await() } }
        val heartbeat = async(Dispatchers.Main) { "Main advanced" }
        assertEquals("Main advanced", withContext(Dispatchers.Default) { withTimeout(5_000) { heartbeat.await() } })
        assertFalse(logout.isCompleted)
        assertEquals("student-1", store.readCurrentSession()!!.studentId)

        releaseWrite.countDown()
        logout.await()
        assertEquals(listOf("write completed", "logout cleanup"), order.toList())
        assertEquals(null, store.readCurrentSession())
      }
    }
  }

  @Test
  fun backupAndCoherentSnapshotCaptureOnRealMainNeverWaitSynchronouslyForSafGate() = runTest {
    withDedicatedMain {
      withBlockedSafWriter { store, epoch, releaseWrite, _ ->
        val secondExport = async(Dispatchers.Main) { repository(store).exportBackupTo(epoch) {} }
        val imported = async(Dispatchers.Main) { repository(store).importBackupFrom(epoch) { """{"version":3,"settings":{}}""" } }
        val captured = async(Dispatchers.Main) {
          val snapshot = store.captureAuthSnapshot()
          val work = store.captureAccountWork(allowOffline = true)
          assertEquals(snapshot.snapshot!!.session, work.session)
          assertEquals(snapshot.lease, work.lease)
          snapshot
        }
        val heartbeat = async(Dispatchers.Main) { "Main advanced" }
        assertEquals("Main advanced", withContext(Dispatchers.Default) { withTimeout(5_000) { heartbeat.await() } })
        assertEquals("student-1", captured.await().snapshot!!.session!!.studentId)
        assertFalse(secondExport.isCompleted)
        assertFalse(imported.isCompleted)

        releaseWrite.countDown()
        assertTrue(secondExport.await().isSuccess)
        assertTrue(imported.await().isSuccess)
        coVerify(exactly = 1) { settingsStore.writeSettings(any()) }
      }
    }
  }

  private suspend fun withDedicatedMain(block: suspend CoroutineScope.() -> Unit) {
    val main = Executors.newSingleThreadExecutor { Thread(it, "cie-backup-test-main") }.asCoroutineDispatcher()
    Dispatchers.setMain(main)
    try { coroutineScope { block() } }
    finally { Dispatchers.resetMain(); main.close() }
  }

  private suspend fun withBlockedSafWriter(
    block: suspend CoroutineScope.(SessionStore, Long, CountDownLatch, MutableList<String>) -> Unit,
  ) = coroutineScope {
    stubEmptyStores()
    val store = testSessionStore()
    val epoch = store.captureAuthSnapshot().authenticationEpoch
    val writeStarted = CompletableDeferred<Unit>()
    val releaseWrite = CountDownLatch(1)
    val order = Collections.synchronizedList(mutableListOf<String>())
    val export = async {
      repository(store).exportBackupTo(epoch) {
        writeStarted.complete(Unit)
        releaseWrite.await()
        order += "write completed"
      }
    }
    try {
      writeStarted.await()
      block(this, store, epoch, releaseWrite, order)
    } finally {
      releaseWrite.countDown()
      withContext(NonCancellable) { export.join() }
    }
  }
}
