package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.database.database.*
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the real repository/DAO mapping boundary; presentation must not truncate source data. */
class SchoolDashboardRepositoryTest {
  private val store = testSessionStore()
  private val currentYear = SchoolYearRef(2026, 2027)
  private val schoolYears = mockk<SchoolYearStore>()
  private val cache = mockk<SnapshotCacheDao>()
  private val grades = mockk<GradeDao>()
  private val agenda = mockk<AgendaDao>()
  private val absences = mockk<AbsenceDao>()
  private val communications = mockk<CommunicationDao>()
  private val documents = mockk<DocumentDao>()
  private val history = mockk<ChangeHistoryDao>()
  private val seen = mockk<SeenGradeDao>()
  private val customEvents = mockk<CustomEventDao>()
  private val overrides = mockk<AgendaCategoryOverrideDao>()
  private val sync = mockk<SchoolSyncCoordinator>()
  private val timetable = mockk<PredictiveTimetableUseCase>()

  init {
    every { schoolYears.observeSelectedSchoolYear() } returns flowOf(currentYear)
    every { cache.observeByKey(any()) } returns flowOf(null)
    every { grades.observeByYear(any(), any()) } returns flowOf(emptyList())
    every { agenda.observeByYear(any(), any()) } returns flowOf(emptyList())
    every { absences.observeByYear(any(), any()) } returns flowOf(emptyList())
    every { communications.observeByYear(any(), any()) } returns flowOf(emptyList())
    every { documents.observeByYear(any(), any()) } returns flowOf(emptyList())
    every { history.observeByYearAndKind(any(), any(), any()) } returns flowOf(emptyList())
    every { seen.observeByStudent(any()) } returns flowOf(emptyList())
    every { customEvents.observeAll() } returns flowOf(emptyList())
    every { overrides.observeByYear(any(), any()) } returns flowOf(emptyList())
    every { sync.syncStatus } returns MutableStateFlow(SyncStatus())
    every { timetable.generateTimetableTemplate(any()) } returns TimetableTemplate()
    every { timetable.getScheduleForDate(any(), any()) } returns emptyList()
  }

  private fun repository() = SchoolDataRepository(
    json = Json, snapshotCacheDao = cache, syncMetadataDao = mockk(), customEventDao = customEvents,
    studentScoreDao = mockk(), downloadRecordDao = mockk(), seenGradeDao = seen, subjectGoalDao = mockk(),
    changeHistoryDao = history, gradeDao = grades, agendaDao = agenda, agendaCategoryOverrideDao = overrides,
    absenceDao = absences, communicationDao = communications, attachmentCacheDao = mockk(), materialDao = mockk(),
    documentDao = documents, syncCoordinator = sync, downloadManager = mockk(), sessionStore = store,
    schoolYearStore = schoolYears, timetableTemplateStore = mockk(), capabilityResolver = mockk(),
    predictiveTimetableUseCase = timetable, externalDashboardInvalidators = emptySet(), context = mockk(),
  )

  @Test
  fun snapshotKeepsEveryUnseenGradeForHomeAndWidgetCounters() = runTest {
    every { grades.observeByYear(any(), any()) } returns flowOf((1..11).map { grade("grade-$it") })
    val snapshot = repository().observeDashboard().first()
    assertEquals(11, snapshot.unseenGrades.size)
    assertEquals("11", snapshot.stats.single { it.id == "grades" }.value)
    assertEquals(6, snapshot.recentGrades.size)
  }

  @Test
  fun snapshotKeepsLaterCategoriesAfterEightEarlierHomeworkItems() = runTest {
    val today = LocalDate.now()
    val items = (1..8).map { agendaItem("homework-$it", AgendaCategory.HOMEWORK, today) } +
      agendaItem("assessment", AgendaCategory.ASSESSMENT, today.plusDays(1))
    every { agenda.observeByYear(any(), any()) } returns flowOf(items)

    val snapshot = repository().observeDashboard().first()
    assertEquals(9, snapshot.upcomingItems.size)
    assertTrue(snapshot.upcomingItems.any { it.id == "assessment" })
  }

  @Test
  fun homeAverageUsesTheSameWeightedSubjectPolicyAsGrades() = runTest {
    every { grades.observeByYear(any(), any()) } returns flowOf(listOf(
      grade("math-1").copy(subject = "Matematica", numericValue = 4.0, weight = 100.0),
      grade("math-2").copy(subject = "Matematica", numericValue = 10.0, weight = 50.0),
      grade("history").copy(subject = "Storia", numericValue = 8.0, weight = 1.0),
    ))
    assertEquals(7.0, repository().observeDashboard().first().averageNumeric!!, 0.0001)
  }

  private fun grade(id: String) = GradeEntity(
    id = id, studentId = "student-1", schoolYearId = currentYear.id, subject = "Matematica", valueLabel = "8",
    numericValue = 8.0, description = null, date = LocalDate.now().toString(), type = "Scritto", weight = null,
    notes = null, period = null, periodCode = null, teacher = null, color = null,
  )

  private fun agendaItem(id: String, category: AgendaCategory, date: LocalDate) = AgendaItemEntity(
    id = id, studentId = "student-1", schoolYearId = currentYear.id, title = id, subtitle = "Matematica",
    date = date.toString(), time = null, detail = null, subject = "Matematica", category = category.name,
    sharePayload = null,
  )
}
