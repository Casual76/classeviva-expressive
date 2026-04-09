package dev.antigravity.classevivaexpressive.feature.grades

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SimulatedGrade
import dev.antigravity.classevivaexpressive.core.domain.model.SimulationRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val gradesRepository = mockk<GradesRepository>(relaxed = true)
  private val simulationRepository = mockk<SimulationRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = GradesViewModel(gradesRepository, simulationRepository)

  @Test
  fun initialState_hasEmptyGradesList() = runTest {
    every { gradesRepository.observeGrades() } returns flowOf(emptyList())
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertTrue(state.grades.isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun gradesFromRepository_areExposedInState() = runTest {
    val grade = Grade(id = "g1", subject = "Matematica", valueLabel = "8", numericValue = 8.0, date = "2026-03-20", type = "Scritto")
    every { gradesRepository.observeGrades() } returns flowOf(listOf(grade))
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.grades.size)
      assertEquals("g1", state.grades.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun selectPeriod_updatesPeriodCodeInState() = runTest {
    every { gradesRepository.observeGrades() } returns flowOf(emptyList())
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())

    val vm = buildViewModel()

    vm.state.test {
      awaitItem() // initial
      vm.selectPeriod("P2")
      val updated = awaitItem()
      assertEquals("P2", updated.selectedPeriodCode)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { gradesRepository.observeGrades() } returns flowOf(emptyList())
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())
    coEvery { gradesRepository.refreshGrades(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { gradesRepository.refreshGrades(force = true) }
  }

  @Test
  fun openGrade_markGradeSeenInRepository() = runTest {
    every { gradesRepository.observeGrades() } returns flowOf(emptyList())
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())

    val vm = buildViewModel()
    vm.openGrade("g1")

    coVerify { gradesRepository.markGradeSeen("g1") }
  }

  @Test
  fun addSimulatedGrade_callsSimulationRepository() = runTest {
    every { gradesRepository.observeGrades() } returns flowOf(emptyList())
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())

    val vm = buildViewModel()
    vm.addSimulatedGrade("Matematica", 8.0, "Scritto", "")

    coVerify { simulationRepository.addSimulatedGrade(any()) }
  }

  @Test
  fun clearSimulation_callsSimulationRepository() = runTest {
    every { gradesRepository.observeGrades() } returns flowOf(emptyList())
    every { gradesRepository.observePeriods() } returns flowOf(emptyList())
    every { gradesRepository.observeSeenGradeStates() } returns flowOf(emptyList())
    every { gradesRepository.observeSubjectGoals() } returns flowOf(emptyList())
    every { simulationRepository.observeSimulation() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary())

    val vm = buildViewModel()
    vm.clearSimulation()

    coVerify { simulationRepository.clearSimulation() }
  }
}
