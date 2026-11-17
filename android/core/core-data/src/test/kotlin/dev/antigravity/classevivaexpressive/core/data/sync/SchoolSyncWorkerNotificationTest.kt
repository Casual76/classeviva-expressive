package dev.antigravity.classevivaexpressive.core.data.sync

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.antigravity.classevivaexpressive.core.data.notifications.GradesCacheSection
import dev.antigravity.classevivaexpressive.core.data.notifications.NotificationScope
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncNotificationDispatcher
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncSnapshotPayloads
import dev.antigravity.classevivaexpressive.core.data.notifications.decodeNotificationCheckpoint
import dev.antigravity.classevivaexpressive.core.data.notifications.encodeNotificationCheckpoint
import dev.antigravity.classevivaexpressive.core.data.notifications.notificationCheckpointCacheKey
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchoolSyncWorkerNotificationTest {
  @Test fun foregroundCacheRefreshDoesNotConsumePendingNotificationsEvenWhenNextNetworkAttemptFails() = runTest {
    val fixture = Fixture()
    fixture.cacheGrades("new-cache")
    fixture.checkpoint("previously-notified")
    fixture.run()
    assertEquals("previously-notified", fixture.dispatches.single().first.grades)
    assertEquals("new-cache", fixture.dispatches.single().second.grades)
    assertEquals("new-cache", fixture.savedCheckpoint()?.grades)
  }

  @Test fun upgradeWithoutCheckpointUsesPreSyncCacheInsteadOfAnnouncingAllHistoricalRecords() = runTest {
    val fixture = Fixture()
    fixture.cacheGrades("known-cache")
    coEvery { fixture.coordinator.refreshCurrentSchoolYearForNotifications(any(), any(), any()) } coAnswers {
      fixture.cacheGrades("fresh-cache")
      SyncStatus()
    }
    fixture.run()
    assertEquals("known-cache", fixture.dispatches.single().first.grades)
    assertEquals("fresh-cache", fixture.dispatches.single().second.grades)
    assertEquals(fixture.scope, fixture.dispatches.single().second.scope)
  }

  @Test fun firstFetchWithoutAnyCacheHasUnknownBaselineForSilentPriming() = runTest {
    val fixture = Fixture()
    coEvery { fixture.coordinator.refreshCurrentSchoolYearForNotifications(any(), any(), any()) } coAnswers {
      fixture.cacheGrades("historical-records")
      SyncStatus()
    }
    fixture.run()
    assertNull(fixture.dispatches.single().first.grades)
    assertEquals("historical-records", fixture.dispatches.single().second.grades)
  }

  @Test fun blockedOrFailedNotificationCheckpointSurvivesUntilTheNextWorker() = runTest {
    val fixture = Fixture()
    fixture.cacheGrades("fresh-cache")
    fixture.checkpoint("pending-checkpoint")
    fixture.acknowledge = false
    fixture.run()
    assertEquals("pending-checkpoint", fixture.savedCheckpoint()?.grades)
    fixture.acknowledge = true
    fixture.run()
    assertEquals("pending-checkpoint", fixture.dispatches.last().first.grades)
    assertEquals("fresh-cache", fixture.savedCheckpoint()?.grades)
  }

  @Test fun maintenanceCacheWritesAreDeliveredOnNextWorkerInsteadOfSilentlyBecomingTheBaseline() = runTest {
    val fixture = Fixture(maintenanceDone = false)
    fixture.cacheGrades("known-cache")
    fixture.checkpoint("known-cache")
    coEvery { fixture.coordinator.refreshCurrentSchoolYearMaintenance(any()) } coAnswers {
      fixture.cacheGrades("maintenance-refresh")
      SyncStatus(state = SyncState.PARTIAL, failedSections = listOf("profile"))
    }
    fixture.run()
    assertEquals("known-cache", fixture.savedCheckpoint()?.grades)
    fixture.run()
    assertEquals("known-cache", fixture.dispatches.last().first.grades)
    assertEquals("maintenance-refresh", fixture.dispatches.last().second.grades)
  }

  @Test fun corruptOrForeignCheckpointCannotUseAnotherAccountsRecordsAsTheBaseline() = runTest {
    val fixture = Fixture()
    fixture.cacheGrades("own-cache")
    fixture.checkpoint("foreign-cache", fixture.scope.copy(studentId = "student-2"))
    fixture.run()
    assertEquals("own-cache", fixture.dispatches.single().first.grades)
    assertEquals(fixture.scope, fixture.savedCheckpoint()?.scope)
  }

  private class Fixture(maintenanceDone: Boolean = true) {
    val year = SchoolYearRef(2026, 2027)
    val scope = NotificationScope("student-1", year.id)
    val coordinator = mockk<SchoolSyncCoordinator>(relaxed = true)
    val rows = mutableMapOf<String, SnapshotCacheEntity>()
    val dispatches = mutableListOf<Pair<SyncSnapshotPayloads, SyncSnapshotPayloads>>()
    var acknowledge = true
    private val dao = mockk<SnapshotCacheDao>()
    private val dispatcher = mockk<SyncNotificationDispatcher>()
    private val context = mockk<Context>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true)
    private val settings = mockk<SettingsStore>()
    private val years = mockk<SchoolYearStore>()
    private val store = testSessionStore()

    init {
      every { params.inputData } returns Data.EMPTY
      every { settings.settings } returns MutableStateFlow(AppSettings(periodicSyncEnabled = true))
      every { years.currentSchoolYearRef() } returns year
      coEvery { dao.getByKey(any()) } coAnswers { rows[firstArg()] }
      coEvery { dao.upsert(any()) } coAnswers {
        val row = firstArg<SnapshotCacheEntity>()
        rows[row.cacheKey] = row
      }
      coEvery { coordinator.refreshCurrentSchoolYearForNotifications(any(), any(), any()) } returns SyncStatus(state = SyncState.ERROR)
      coEvery { dispatcher.dispatch(any(), any()) } coAnswers {
        val before = firstArg<SyncSnapshotPayloads>()
        val after = secondArg<SyncSnapshotPayloads>()
        dispatches += before to after
        if (acknowledge) after else before
      }
      if (maintenanceDone) {
        val key = "${scope.studentId}::${year.id}::background_maintenance"
        rows[key] = SnapshotCacheEntity(key, "{}", System.currentTimeMillis())
      }
    }

    fun cacheGrades(payload: String) {
      val key = yearScopedCacheKey(scope.studentId, GradesCacheSection, year)
      rows[key] = SnapshotCacheEntity(key, payload, System.currentTimeMillis())
    }

    fun checkpoint(payload: String, checkpointScope: NotificationScope = scope) {
      val key = notificationCheckpointCacheKey(scope)
      rows[key] = SnapshotCacheEntity(key, encodeNotificationCheckpoint(SyncSnapshotPayloads(grades = payload, scope = checkpointScope)), 1L)
    }

    fun savedCheckpoint(): SyncSnapshotPayloads? =
      decodeNotificationCheckpoint(rows[notificationCheckpointCacheKey(scope)]?.payload)

    suspend fun run() {
      mockkObject(SyncWorkScheduler)
      every { SyncWorkScheduler.scheduleNext(any(), any()) } just Runs
      every { SyncWorkScheduler.cancel(any()) } just Runs
      try {
        SchoolSyncWorker(context, params, store, settings, years, dao, coordinator, dispatcher,
          mockk(relaxed = true), mockk(relaxed = true), emptySet()).doWork()
      } finally {
        unmockkObject(SyncWorkScheduler)
      }
    }
  }
}
