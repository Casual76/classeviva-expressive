package dev.antigravity.classevivaexpressive

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import dev.antigravity.classevivaexpressive.core.data.preview.PrivateSessionDataCleaner
import dev.antigravity.classevivaexpressive.core.data.repository.SchoolAuthRepository
import dev.antigravity.classevivaexpressive.core.data.sync.AccountSyncScheduler
import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.datastore.SecureSessionPersistence
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.network.client.ApiSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainLogoutBarrierIntegrationTest {
  @Test(timeout = 20_000)
  fun realRepositoryLogoutKeepsMainResponsiveWhileAnAdmittedWriterOwnsTheBarrier() = runBlocking {
    val mainThread = AtomicReference<Thread>()
    val mainExecutor = Executors.newSingleThreadExecutor { task ->
      Thread(task, "cie-main-logout-test").apply { isDaemon = true; mainThread.set(this) }
    }
    val mainDispatcher = mainExecutor.asCoroutineDispatcher()
    val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
      Thread(task, "cie-main-logout-watchdog").apply { isDaemon = true }
    }
    val releaseWriter = CountDownLatch(1)
    var writer: Job? = null
    var uiObserver: Job? = null
    var model: MainViewModel? = null
    Dispatchers.setMain(mainDispatcher)
    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } returns 1_000L
    try {
      val persistence = MemoryPersistence()
      val store = SessionStore(persistence)
      val original = UserSession("synthetic-token", "student-1", "student", StudentProfile(id = "student-1"))
      store.writeAuthSnapshot(StoredAuthSnapshot(session = original, credentials = StoredCredentials("student", "synthetic-password")))
      val sync = mockk<SchoolSyncCoordinator>(relaxed = true)
      val cleaner = mockk<PrivateSessionDataCleaner>()
      val cleaned = AtomicBoolean()
      coEvery { cleaner.clear("student-1") } coAnswers { cleaned.set(true) }
      val networkSessions = mockk<ApiSessionManager>()
      coEvery { networkSessions.restoreValidSession() } returns original
      val auth = SchoolAuthRepository(store, sync, networkSessions, cleaner)
      val settings = mockk<SettingsRepository>(relaxed = true)
      every { settings.observeSettings() } returns MutableStateFlow(AppSettings(periodicSyncEnabled = false))
      val years = mockk<SchoolYearRepository>()
      every { years.observeFallbackEvents() } returns emptyFlow()
      every { years.observeSelectedSchoolYear() } returns flowOf(SchoolYearRef(2026, 2027))
      val dashboard = mockk<DashboardRepository>()
      every { dashboard.observeDashboard() } returns MutableStateFlow(DashboardSnapshot())
      val scheduler = mockk<AccountSyncScheduler>(relaxed = true)
      val viewModel = withContext(Dispatchers.Main) {
        MainViewModel(auth, settings, mockk(relaxed = true), years, dashboard, scheduler)
      }
      model = viewModel
      uiObserver = launch(mainDispatcher) { viewModel.uiState.collect() }
      withTimeout(5_000) { viewModel.uiState.first { !it.isLoading && it.session == original } }
      coVerify(exactly = 1) { networkSessions.restoreValidSession() }

      // This is the same admission barrier used by a final SAF/cache write. The blocking I/O is
      // deliberately on IO; only entering the real UI->repository logout path runs on Main.
      val captured = store.captureAuthSnapshot()
      val writerEntered = CountDownLatch(1)
      val admittedWriter = launch(Dispatchers.IO) {
        store.accountGuard.withCurrentAccount(captured.lease, requireReady = false) {
          writerEntered.countDown()
          check(releaseWriter.await(10, TimeUnit.SECONDS)) { "The test writer was not released" }
        }
      }
      writer = admittedWriter
      assertTrue("The simulated writer must hold the actual account barrier", writerEntered.await(3, TimeUnit.SECONDS))
      val watchdogReleased = AtomicBoolean()
      val safetyRelease = watchdog.schedule({
        watchdogReleased.set(true)
        releaseWriter.countDown()
      }, 5, TimeUnit.SECONDS)
      val heartbeat = CountDownLatch(1)
      val heartbeatBeforeRelease = AtomicBoolean()
      val heartbeatOnMainThread = AtomicBoolean()
      try {
        withContext(Dispatchers.Main) {
          viewModel.logout()
          // FIFO on the same real single-thread executor: the logout coroutine is admitted first.
          // With the old blocking snapshot capture, neither this nor the queued heartbeat can
          // complete until the independent watchdog opens the writer's gate.
          mainExecutor.execute {
            heartbeatBeforeRelease.set(releaseWriter.count == 1L)
            heartbeatOnMainThread.set(Thread.currentThread() === mainThread.get())
            heartbeat.countDown()
          }
        }
        assertTrue("Main must process a heartbeat while the writer is still blocked", heartbeat.await(6, TimeUnit.SECONDS))
        assertTrue("The heartbeat must run before release, not after watchdog recovery", heartbeatBeforeRelease.get())
        assertTrue(heartbeatOnMainThread.get())
        assertFalse("The watchdog must not be needed for a responsive logout", watchdogReleased.get())
        val pending = withTimeout(2_000) { viewModel.uiState.first { it.isAuthenticating } }
        assertEquals(original, pending.session)
        assertEquals(original, store.readCurrentSession())
        assertFalse("Cleanup must wait for the admitted writer", cleaned.get())
        assertEquals(1L, releaseWriter.count)
        coVerify(exactly = 0) { cleaner.clear(any()) }

        releaseWriter.countDown()
        admittedWriter.join()
        val finished = withTimeout(3_000) { viewModel.uiState.first { it.session == null && !it.isAuthenticating } }
        assertNull(finished.authError)
        assertNull(store.readCurrentSession())
        assertNull(persistence.readSnapshot())
        assertTrue(cleaned.get())
        verify(exactly = 1) { sync.attachSession(null) }
        verify(exactly = 1) { sync.clearPortalSession() }
        coVerify(exactly = 1) { sync.clearAccountDerivedState() }
        coVerify(exactly = 1) { cleaner.clear("student-1") }
      } finally {
        releaseWriter.countDown()
        safetyRelease.cancel(false)
      }
    } finally {
      // This release is independent of Main/coroutine cancellation, so the regression fails
      // without deadlocking even when tested against the original runBlocking capture.
      releaseWriter.countDown()
      try {
        withContext(NonCancellable) {
          withTimeout(3_000) {
            writer?.join()
            uiObserver?.cancelAndJoin()
            model?.let { current ->
              withContext(mainDispatcher) { current.viewModelScope.cancel() }
              current.viewModelScope.coroutineContext[Job]?.join()
            }
          }
        }
      } finally {
        watchdog.shutdownNow()
        Dispatchers.resetMain()
        mainDispatcher.close()
        mainExecutor.shutdownNow()
        unmockkStatic(SystemClock::class)
      }
    }
  }

  private class MemoryPersistence : SecureSessionPersistence {
    @Volatile private var raw: String? = null
    override fun readSnapshot(): String? = raw
    override fun readLegacySession(): String? = null
    override fun readLegacyCredentials(): StoredCredentials? = null
    override fun commit(snapshot: String?): Boolean { raw = snapshot; return true }
  }
}
