package dev.antigravity.classevivaexpressive.core.data.repository

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

  private fun repository() = DefaultAppBackupRepository(
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
    teacher = "MONTI",
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
    val restored = slot<List<ChangeHistoryEntity>>()
    coEvery { changeHistoryDao.upsertAll(capture(restored)) } returns Unit

    val summary = repository().importBackup(payload).getOrThrow()

    assertEquals(1, summary.gradeHistory)
    assertEquals(history, restored.captured)
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
}
