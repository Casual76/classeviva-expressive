package dev.antigravity.classevivaexpressive.feature.dashboard

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val dashboardRepository = mockk<DashboardRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = DashboardViewModel(dashboardRepository)

  // ─── Caricamento snapshot dashboard ──────────────────────────────────────

  @Test
  fun dashboardSnapshotFromRepository_isExposedInState() = runTest {
    val profile = StudentProfile(id = "55", name = "Ada", surname = "Lovelace")
    val snapshot = DashboardSnapshot(profile = profile, averageLabel = "8.5")
    every { dashboardRepository.observeDashboard() } returns flowOf(snapshot)

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals("Ada", state.snapshot.profile.name)
      assertEquals("8.5", state.snapshot.averageLabel)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun initialState_hasDefaultEmptySnapshot() = runTest {
    every { dashboardRepository.observeDashboard() } returns flowOf(DashboardSnapshot())
    coEvery { dashboardRepository.refreshDashboard(any()) } returns Result.success(DashboardSnapshot())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertFalse(state.isRefreshing)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Stato di sync ────────────────────────────────────────────────────────

  @Test
  fun syncStatusIdleInSnapshot_noRefreshingIndicator() = runTest {
    val snapshot = DashboardSnapshot(syncStatus = SyncStatus(state = SyncState.IDLE))
    every { dashboardRepository.observeDashboard() } returns flowOf(snapshot)
    coEvery { dashboardRepository.refreshDashboard(any()) } returns Result.success(snapshot)

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertFalse(state.isRefreshing)
      assertEquals(SyncState.IDLE, state.snapshot.syncStatus.state)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { dashboardRepository.observeDashboard() } returns flowOf(DashboardSnapshot())
    coEvery { dashboardRepository.refreshDashboard(any()) } returns Result.success(DashboardSnapshot())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { dashboardRepository.refreshDashboard(force = true) }
  }

  @Test
  fun refresh_setsIsRefreshingTrueThenFalse() = runTest {
    every { dashboardRepository.observeDashboard() } returns flowOf(DashboardSnapshot())
    coEvery { dashboardRepository.refreshDashboard(any()) } returns Result.success(DashboardSnapshot())

    val vm = buildViewModel()

    vm.state.test {
      awaitItem() // initial
      vm.refresh()
      val refreshing = awaitItem()
      assertTrue(refreshing.isRefreshing)
      val done = awaitItem()
      assertFalse(done.isRefreshing)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
