package dev.antigravity.classevivaexpressive.feature.homework

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmission
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmissionReceipt
import dev.antigravity.classevivaexpressive.core.domain.model.GatewayActionState
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeworkViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val homeworkRepository = mockk<HomeworkRepository>(relaxed = true)
  private val schoolYearRepository = mockk<SchoolYearRepository>(relaxed = true)
  private val capabilityResolver = mockk<CapabilityResolver>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel(): HomeworkViewModel {
    every { schoolYearRepository.observeSelectedSchoolYear() } returns flowOf(SchoolYearRef(2025, 2026))
    every { capabilityResolver.observeCapability(RegistroFeature.HOMEWORKS) } returns flowOf(
      FeatureCapability(feature = RegistroFeature.HOMEWORKS, mode = FeatureCapabilityMode.DIRECT_REST, enabled = true, label = "Disponibile"),
    )
    return HomeworkViewModel(homeworkRepository, schoolYearRepository, capabilityResolver)
  }

  private fun buildHomework(id: String = "hw1") = Homework(
    id = id, subject = "Matematica", description = "Esercizi pag. 45", dueDate = "2026-03-25",
  )

  // ─── Caricamento compiti ──────────────────────────────────────────────────

  @Test
  fun homeworksFromRepository_areExposedInState() = runTest {
    val hw = buildHomework()
    every { homeworkRepository.observeHomeworks() } returns flowOf(listOf(hw))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.homeworks.size)
      assertEquals("hw1", state.homeworks.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Dettaglio compito ────────────────────────────────────────────────────

  @Test
  fun openHomework_setsSelectedDetailInState() = runTest {
    val hw = buildHomework()
    val detail = HomeworkDetail(homework = hw, fullText = "Testo completo del compito")
    every { homeworkRepository.observeHomeworks() } returns flowOf(listOf(hw))
    coEvery { homeworkRepository.getHomeworkDetail("hw1") } returns Result.success(detail)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.openHomework("hw1")
      val updated = awaitItem()
      assertNotNull(updated.selectedDetail)
      assertEquals("Testo completo del compito", updated.selectedDetail?.fullText)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Consegna compito ─────────────────────────────────────────────────────

  @Test
  fun submit_callsRepositoryWithCorrectSubmission() = runTest {
    val hw = buildHomework()
    val detail = HomeworkDetail(homework = hw, fullText = "Testo")
    val receipt = HomeworkSubmissionReceipt(homeworkId = "hw1", state = GatewayActionState.SUCCESS)
    every { homeworkRepository.observeHomeworks() } returns flowOf(listOf(hw))
    coEvery { homeworkRepository.submitHomework(any()) } returns Result.success(receipt)

    val vm = buildViewModel()
    vm.submit(detail, "Mia risposta", null)

    coVerify {
      homeworkRepository.submitHomework(
        match { it.homeworkId == "hw1" && it.text == "Mia risposta" },
      )
    }
  }

  @Test
  fun submit_setsErrorMessageOnFailure() = runTest {
    val hw = buildHomework()
    val detail = HomeworkDetail(homework = hw, fullText = "Testo")
    every { homeworkRepository.observeHomeworks() } returns flowOf(listOf(hw))
    coEvery { homeworkRepository.submitHomework(any()) } returns Result.failure(Exception("Gateway non raggiungibile"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.submit(detail, "Risposta", null)
      val submitting = awaitItem() // isSubmitting = true
      val error = awaitItem()
      assertEquals("Gateway non raggiungibile", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { homeworkRepository.observeHomeworks() } returns flowOf(emptyList())
    coEvery { homeworkRepository.refreshHomeworks(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { homeworkRepository.refreshHomeworks(force = true) }
  }
}
