package dev.antigravity.classevivaexpressive.feature.lessons

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LessonsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val lessonsRepository = mockk<LessonsRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = LessonsViewModel(lessonsRepository)

  @Test
  fun lessonsFromRepository_areExposedInState() = runTest {
    val today = LocalDate.now().toString()
    val lesson = Lesson(id = "l1", subject = "Matematica", date = today, time = "08:00", durationMinutes = 60)
    every { lessonsRepository.observeLessons() } returns flowOf(listOf(lesson))
    every { lessonsRepository.observeTimetableTemplate() } returns flowOf(TimetableTemplate())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.lessons.size)
      assertEquals("Matematica", state.lessons.first().subject)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun timetableTemplate_isExposedInState() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())
    every { lessonsRepository.observeTimetableTemplate() } returns flowOf(
      TimetableTemplate(
        slots = listOf(
          TemplateSlot(
            dayOfWeek = DayOfWeek.MONDAY.value,
            time = "08:00",
            endTime = "09:00",
            durationMinutes = 60,
            subject = "Fisica",
            confidence = 0.8f,
            sampleCount = 4,
          ),
        ),
        sampledWeeks = 4,
      ),
    )

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.timetableTemplate.slots.size)
      assertEquals(4, state.timetableTemplate.sampledWeeks)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun initialState_hasEmptyLessonsList() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())
    every { lessonsRepository.observeTimetableTemplate() } returns flowOf(TimetableTemplate())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertTrue(state.lessons.isEmpty())
      assertTrue(state.timetableTemplate.slots.isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())
    every { lessonsRepository.observeTimetableTemplate() } returns flowOf(TimetableTemplate())
    coEvery { lessonsRepository.refreshLessons(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { lessonsRepository.refreshLessons(force = true) }
  }

  @Test
  fun refresh_setsErrorMessageOnFailure() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())
    every { lessonsRepository.observeTimetableTemplate() } returns flowOf(TimetableTemplate())
    coEvery { lessonsRepository.refreshLessons(any()) } returns Result.failure(Exception("Connessione assente"))

    val vm = buildViewModel()

    vm.refresh()
    testDispatcher.scheduler.advanceUntilIdle()

    val job = launch(testDispatcher) { vm.state.collect {} }
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("Connessione assente", vm.state.value.lastMessage)
    job.cancel()
  }
}
