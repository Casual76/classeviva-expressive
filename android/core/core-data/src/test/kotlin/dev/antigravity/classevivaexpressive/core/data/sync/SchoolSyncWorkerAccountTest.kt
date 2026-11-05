package dev.antigravity.classevivaexpressive.core.data.sync

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.antigravity.classevivaexpressive.core.data.external.ExternalDashboardInvalidator
import dev.antigravity.classevivaexpressive.core.data.notifications.SyncNotificationDispatcher
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSyncWorkerAccountTest {
  @Test fun logoutDuringPayloadCapturePreventsOldWorkerNotificationsRescheduleAndNewAttachment() = runTest {
    val store = testSessionStore()
    val captured = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val dao = mockk<SnapshotCacheDao>(relaxed = true)
    var firstRead = true
    coEvery { dao.getByKey(any()) } coAnswers {
      if (firstRead) {
        firstRead = false
        captured.complete(Unit)
        release.await()
      }
      null
    }
    val settings = mockk<SettingsStore>()
    every { settings.settings } returns MutableStateFlow(AppSettings(periodicSyncEnabled = true))
    val year = mockk<SchoolYearStore>()
    every { year.currentSchoolYearRef() } returns SchoolYearRef(2026, 2027)
    val sync = mockk<SchoolSyncCoordinator>(relaxed = true)
    coEvery { sync.refreshCurrentSchoolYearForNotifications(any(), any(), any()) } returns SyncStatus()
    val notices = mockk<SyncNotificationDispatcher>(relaxed = true)
    val invalidator = mockk<ExternalDashboardInvalidator>(relaxed = true)
    val context = mockk<Context>(relaxed = true)
    val params = mockk<WorkerParameters>(relaxed = true)
    every { params.inputData } returns Data.EMPTY
    val worker = SchoolSyncWorker(context, params, store, settings, year, dao, sync, notices, mockk(relaxed = true), mockk(relaxed = true), setOf(invalidator))
    mockkObject(SyncWorkScheduler)
    every { SyncWorkScheduler.scheduleNext(any(), any()) } just Runs
    every { SyncWorkScheduler.cancel(any()) } just Runs
    try {
      val pending = async { worker.doWork() }
      captured.await()
      val old = store.readAuthSnapshot()!!
      store.clear()
      store.writeAuthSnapshot(old.copy(session = old.session!!.copy(studentId = "student-b", token = "b")))
      release.complete(Unit)
      pending.await()

      coVerify(exactly = 0) { notices.dispatch(any(), any()) }
      coVerify(exactly = 0) { invalidator.invalidateDashboard() }
      verify(exactly = 0) { SyncWorkScheduler.scheduleNext(any(), any()) }
      verify(exactly = 0) { sync.attachSession(any()) }
      assertTrue(store.readCurrentSession()!!.studentId == "student-b")
    } finally { unmockkObject(SyncWorkScheduler) }
  }
}
