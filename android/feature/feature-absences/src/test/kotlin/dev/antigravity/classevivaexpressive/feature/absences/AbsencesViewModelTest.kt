package dev.antigravity.classevivaexpressive.feature.absences

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AbsencesViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val absencesRepository = mockk<AbsencesRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = AbsencesViewModel(absencesRepository)

  // ─── Caricamento assenze ──────────────────────────────────────────────────

  @Test
  fun absencesFromRepository_areExposedInState() = runTest {
    val absence = AbsenceRecord(id = "a1", date = "2026-03-20", type = AbsenceType.ABSENCE, justified = false)
    every { absencesRepository.observeAbsences() } returns flowOf(listOf(absence))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.absences.size)
      assertEquals("a1", state.absences.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun initialState_hasEmptyAbsencesList() = runTest {
    every { absencesRepository.observeAbsences() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertTrue(state.absences.isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Giustificazione ──────────────────────────────────────────────────────

  @Test
  fun requestJustification_setsSelectedAbsenceInState() = runTest {
    val absence = AbsenceRecord(id = "a2", date = "2026-03-21", type = AbsenceType.ABSENCE, justified = false, canJustify = true)
    every { absencesRepository.observeAbsences() } returns flowOf(listOf(absence))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem() // initial
      vm.requestJustification(absence)
      val updated = awaitItem()
      assertEquals(absence, updated.selectedAbsence)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun dismissJustification_clearsSelectedAbsence() = runTest {
    val absence = AbsenceRecord(id = "a3", date = "2026-03-22", type = AbsenceType.LATE, justified = false)
    every { absencesRepository.observeAbsences() } returns flowOf(listOf(absence))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.requestJustification(absence)
      awaitItem()
      vm.dismissJustification()
      val cleared = awaitItem()
      assertNull(cleared.selectedAbsence)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun justify_callsRepositoryWithReasonAndClearsSelection() = runTest {
    val absence = AbsenceRecord(id = "a4", date = "2026-03-23", type = AbsenceType.ABSENCE, justified = false, canJustify = true)
    every { absencesRepository.observeAbsences() } returns flowOf(listOf(absence))
    coEvery { absencesRepository.justifyAbsence(absence, any(), any()) } returns Result.success(listOf(absence.copy(justified = true)))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.requestJustification(absence)
      awaitItem()

      vm.justify("Malattia")

      var afterJustify = awaitItem()
      while (afterJustify.isSubmitting || afterJustify.lastMessage == null) {
        afterJustify = awaitItem()
      }

      assertNull(afterJustify.selectedAbsence)
      assertEquals("Giustificazione inviata.", afterJustify.lastMessage)
      coVerify { absencesRepository.justifyAbsence(absence, "Malattia", any()) }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun justify_setsErrorMessageOnFailure() = runTest {
    val absence = AbsenceRecord(id = "a5", date = "2026-03-24", type = AbsenceType.ABSENCE, justified = false, canJustify = true)
    every { absencesRepository.observeAbsences() } returns flowOf(listOf(absence))
    coEvery { absencesRepository.justifyAbsence(absence, any(), any()) } returns Result.failure(Exception("Errore server"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.requestJustification(absence)
      awaitItem()

      vm.justify("Motivo")

      var afterError = awaitItem()
      while (afterError.isSubmitting || afterError.lastMessage == null) {
        afterError = awaitItem()
      }

      assertEquals("Errore server", afterError.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { absencesRepository.observeAbsences() } returns flowOf(emptyList())
    coEvery { absencesRepository.refreshAbsences(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { absencesRepository.refreshAbsences(force = true) }
  }
}
