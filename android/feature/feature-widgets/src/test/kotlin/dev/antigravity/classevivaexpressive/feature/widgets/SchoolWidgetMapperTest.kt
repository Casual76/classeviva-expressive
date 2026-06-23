package dev.antigravity.classevivaexpressive.feature.widgets

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolWidgetMapperTest {
  private val today = LocalDate.parse("2026-06-23")

  @Test
  fun map_filtersHomeworkAndAssessmentsWithSeparateWindows() {
    val snapshot = DashboardSnapshot(
      upcomingItems = listOf(
        agenda("hw1", AgendaCategory.HOMEWORK, "2026-06-24", "Compito domani"),
        agenda("hw2", AgendaCategory.HOMEWORK, "2026-06-28", "Compito fuori finestra"),
        agenda("a1", AgendaCategory.ASSESSMENT, "2026-07-06", "Verifica entro 14"),
        agenda("a2", AgendaCategory.ASSESSMENT, "2026-07-12", "Verifica fuori finestra"),
      ),
      syncStatus = SyncStatus(lastSuccessfulSyncEpochMillis = 1_772_000_000_000L),
    )

    val model = SchoolWidgetMapper.map(
      snapshot = snapshot,
      preferences = SchoolWidgetPreferences(homeworkDays = 3, assessmentDays = 14),
      hasSession = true,
      today = today,
      nowEpochMillis = 1_772_000_100_000L,
    )

    assertEquals(WidgetStatus.CONTENT, model.status)
    assertEquals(listOf("hw1", "a1"), model.upcoming.map { it.id })
  }

  @Test
  fun map_privacyDiscreetMasksSensitiveLabelsButKeepsCounts() {
    val snapshot = DashboardSnapshot(
      upcomingItems = listOf(agenda("hw1", AgendaCategory.HOMEWORK, "2026-06-24", "Leggere capitolo 4")),
      unseenGrades = listOf(grade(id = "g1", subject = "Matematica", value = "8")),
      unreadCommunications = listOf(communication(id = "c1", title = "Circolare privacy", sender = "Segreteria")),
      syncStatus = SyncStatus(lastSuccessfulSyncEpochMillis = 1_772_000_000_000L),
    )

    val model = SchoolWidgetMapper.map(
      snapshot = snapshot,
      preferences = SchoolWidgetPreferences(privacyMode = WidgetPrivacyMode.DISCREET),
      hasSession = true,
      today = today,
      nowEpochMillis = 1_772_000_100_000L,
    )

    assertEquals("Compito in arrivo", model.upcoming.single().title)
    assertEquals("Voto da aprire", model.unseenGrades.single().subject)
    assertEquals("--", model.unseenGrades.single().valueLabel)
    assertEquals("Comunicazione da leggere", model.unreadCommunications.single().title)
    assertEquals(1, model.unseenGradeCount)
    assertEquals(1, model.unreadCommunicationCount)
  }

  @Test
  fun map_loggedOutShowsLoginState() {
    val model = SchoolWidgetMapper.map(
      snapshot = DashboardSnapshot(upcomingItems = listOf(agenda("hw1", AgendaCategory.HOMEWORK, "2026-06-24", "Compito"))),
      preferences = SchoolWidgetPreferences(),
      hasSession = false,
      today = today,
    )

    assertEquals(WidgetStatus.LOGGED_OUT, model.status)
    assertTrue(model.upcoming.isEmpty())
    assertTrue(model.emptyMessage.contains("Accedi"))
  }

  @Test
  fun map_emptyCacheShowsEmptyState() {
    val model = SchoolWidgetMapper.map(
      snapshot = DashboardSnapshot(syncStatus = SyncStatus(lastSuccessfulSyncEpochMillis = 1_772_000_000_000L)),
      preferences = SchoolWidgetPreferences(),
      hasSession = true,
      today = today,
      nowEpochMillis = 1_772_000_100_000L,
    )

    assertEquals(WidgetStatus.EMPTY, model.status)
    assertTrue(model.emptyMessage.contains("Nessun impegno"))
    assertFalse(model.syncWarning)
  }

  @Test
  fun map_offlineAndStaleSnapshotShowsNonBlockingWarning() {
    val model = SchoolWidgetMapper.map(
      snapshot = DashboardSnapshot(
        upcomingItems = listOf(agenda("hw1", AgendaCategory.HOMEWORK, "2026-06-24", "Compito")),
        syncStatus = SyncStatus(
          state = SyncState.OFFLINE,
          lastSuccessfulSyncEpochMillis = 1_771_000_000_000L,
        ),
      ),
      preferences = SchoolWidgetPreferences(),
      hasSession = true,
      today = today,
      nowEpochMillis = 1_772_000_100_000L,
    )

    assertEquals(WidgetStatus.CONTENT, model.status)
    assertEquals("Offline", model.syncLabel)
    assertTrue(model.syncWarning)
  }

  @Test
  fun map_sortsUpcomingItemsByDateThenTitle() {
    val snapshot = DashboardSnapshot(
      upcomingItems = listOf(
        agenda("later", AgendaCategory.ASSESSMENT, "2026-06-25", "Verifica"),
        agenda("beta", AgendaCategory.HOMEWORK, "2026-06-24", "Zaino"),
        agenda("alpha", AgendaCategory.HOMEWORK, "2026-06-24", "Analisi"),
      ),
      syncStatus = SyncStatus(lastSuccessfulSyncEpochMillis = 1_772_000_000_000L),
    )

    val model = SchoolWidgetMapper.map(
      snapshot = snapshot,
      preferences = SchoolWidgetPreferences(homeworkDays = 3, assessmentDays = 7),
      hasSession = true,
      today = today,
      nowEpochMillis = 1_772_000_100_000L,
    )

    assertEquals(listOf("alpha", "beta", "later"), model.upcoming.map { it.id })
  }

  @Test
  fun map_buildsDeepLinksForRowsAndCounters() {
    val snapshot = DashboardSnapshot(
      upcomingItems = listOf(agenda("agenda-1", AgendaCategory.ASSESSMENT, "2026-06-24", "Verifica")),
      unseenGrades = listOf(grade(id = "grade 1", subject = "Matematica", value = "8")),
      unreadCommunications = listOf(communication(id = "c1", title = "Circolare", sender = "Segreteria")),
      syncStatus = SyncStatus(lastSuccessfulSyncEpochMillis = 1_772_000_000_000L),
    )

    val model = SchoolWidgetMapper.map(
      snapshot = snapshot,
      preferences = SchoolWidgetPreferences(),
      hasSession = true,
      today = today,
      nowEpochMillis = 1_772_000_100_000L,
    )

    assertEquals(
      "classevivaexpressive://open/agenda?agendaId=agenda-1&date=2026-06-24",
      model.upcoming.single().deepLink,
    )
    assertEquals(
      "classevivaexpressive://open/grades?gradeId=grade%201",
      model.unseenGrades.single().deepLink,
    )
    assertEquals(
      "classevivaexpressive://open/communications?tab=board&pubId=c1&evtCode=CIR",
      model.unreadCommunications.single().deepLink,
    )
  }

  @Test
  fun map_sectionTogglesHideCountsAndLists() {
    val snapshot = DashboardSnapshot(
      unseenGrades = listOf(grade(id = "g1", subject = "Matematica", value = "8")),
      unreadCommunications = listOf(communication(id = "c1", title = "Circolare", sender = "Segreteria")),
    )

    val model = SchoolWidgetMapper.map(
      snapshot = snapshot,
      preferences = SchoolWidgetPreferences(showGrades = false, showCommunications = false),
      hasSession = true,
      today = today,
    )

    assertFalse(model.unseenGrades.isNotEmpty())
    assertFalse(model.unreadCommunications.isNotEmpty())
    assertEquals(0, model.unseenGradeCount)
    assertEquals(0, model.unreadCommunicationCount)
  }

  private fun agenda(
    id: String,
    category: AgendaCategory,
    date: String,
    title: String,
  ): AgendaItem = AgendaItem(
    id = id,
    title = title,
    subtitle = "Matematica",
    date = date,
    subject = "Matematica",
    category = category,
  )

  private fun grade(
    id: String,
    subject: String,
    value: String,
  ): Grade = Grade(
    id = id,
    subject = subject,
    valueLabel = value,
    date = "2026-06-20",
    type = "Scritto",
  )

  private fun communication(
    id: String,
    title: String,
    sender: String,
  ): Communication = Communication(
    id = id,
    pubId = id,
    evtCode = "CIR",
    title = title,
    contentPreview = "Preview",
    sender = sender,
    date = "2026-06-22",
    read = false,
  )
}
