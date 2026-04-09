package dev.antigravity.classevivaexpressive.feature.studentscore

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComparison
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreSnapshot
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentScoreViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val studentScoreRepository = mockk<StudentScoreRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = StudentScoreViewModel(studentScoreRepository)

  private fun buildSnapshot(score: Double = 85.0, label: String = "Ottimo") = StudentScoreSnapshot(
    score = score, label = label,
    computedAtEpochMillis = 1_700_000_000_000L,
    components = emptyList(),
    sharePayload = "payload-$score",
  )

  // ─── Caricamento punteggio corrente ───────────────────────────────────────

  @Test
  fun currentScoreFromRepository_isExposedInState() = runTest {
    val snapshot = buildSnapshot(score = 92.5, label = "Eccellente")
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(snapshot)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertNotNull(state.current)
      assertEquals(92.5, state.current?.score)
      assertEquals("Eccellente", state.current?.label)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun noCurrentScore_isNullInState() = runTest {
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(null)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertNull(state.current)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Storico snapshot ─────────────────────────────────────────────────────

  @Test
  fun snapshotsFromRepository_areExposedInState() = runTest {
    val snapshots = listOf(buildSnapshot(80.0, "Buono"), buildSnapshot(85.0, "Ottimo"))
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(null)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(snapshots)

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(2, state.snapshots.size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Import payload ───────────────────────────────────────────────────────

  @Test
  fun importPayload_setsComparisonOnSuccess() = runTest {
    val current = buildSnapshot(85.0, "Ottimo")
    val imported = buildSnapshot(80.0, "Buono")
    val comparison = StudentScoreComparison(current = current, imported = imported, difference = 5.0)
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(current)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())
    coEvery { studentScoreRepository.importPayload("payload-buono") } returns Result.success(comparison)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.importPayload("payload-buono")
      val updated = awaitItem()
      assertNotNull(updated.comparison)
      assertEquals(5.0, updated.comparison?.difference)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun importPayload_setsErrorMessageOnFailure() = runTest {
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(null)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())
    coEvery { studentScoreRepository.importPayload(any()) } returns Result.failure(Exception("Payload non valido"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.importPayload("payload-errato")
      val error = awaitItem()
      assertEquals("Payload non valido", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Clear comparison ─────────────────────────────────────────────────────

  @Test
  fun clearComparison_removesComparisonFromState() = runTest {
    val current = buildSnapshot()
    val imported = buildSnapshot(70.0, "Sufficiente")
    val comparison = StudentScoreComparison(current = current, imported = imported, difference = 15.0)
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(current)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())
    coEvery { studentScoreRepository.importPayload(any()) } returns Result.success(comparison)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.importPayload("payload-test")
      awaitItem() // comparison set
      vm.clearComparison()
      val cleared = awaitItem()
      assertNull(cleared.comparison)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(null)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())
    coEvery { studentScoreRepository.refreshStudentScore(any()) } returns Result.success(buildSnapshot())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { studentScoreRepository.refreshStudentScore(force = true) }
  }

  @Test
  fun refresh_setsIsRefreshingTrueThenFalse() = runTest {
    every { studentScoreRepository.observeCurrentScore() } returns flowOf(null)
    every { studentScoreRepository.observeSnapshots() } returns flowOf(emptyList())
    coEvery { studentScoreRepository.refreshStudentScore(any()) } returns Result.success(buildSnapshot())

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.refresh()
      val refreshing = awaitItem()
      assertTrue(refreshing.isRefreshing)
      val done = awaitItem()
      assertFalse(done.isRefreshing)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
