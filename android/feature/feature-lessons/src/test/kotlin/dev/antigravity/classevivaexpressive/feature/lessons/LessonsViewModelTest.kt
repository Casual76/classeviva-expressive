package dev.antigravity.classevivaexpressive.feature.lessons

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
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
class LessonsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val lessonsRepository = mockk<LessonsRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = LessonsViewModel(lessonsRepository)

  // ─── Caricamento lezioni ──────────────────────────────────────────────────

  @Test
  fun lessonsFromRepository_areExposedInState() = runTest {
    val lesson = Lesson(id = "l1", subject = "Matematica", date = "2026-03-20", time = "08:00", durationMinutes = 60)
    every { lessonsRepository.observeLessons() } returns flowOf(listOf(lesson))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.lessons.size)
      assertEquals("Matematica", state.lessons.first().subject)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun initialState_hasEmptyLessonsList() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertTrue(state.lessons.isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun multipleLessons_areAllPresentInState() = runTest {
    val lessons = listOf(
      Lesson(id = "l1", subject = "Matematica", date = "2026-03-20", time = "08:00", durationMinutes = 60),
      Lesson(id = "l2", subject = "Fisica", date = "2026-03-20", time = "09:00", durationMinutes = 60),
      Lesson(id = "l3", subject = "Italiano", date = "2026-03-20", time = "10:00", durationMinutes = 60),
    )
    every { lessonsRepository.observeLessons() } returns flowOf(lessons)

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(3, state.lessons.size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())
    coEvery { lessonsRepository.refreshLessons(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { lessonsRepository.refreshLessons(force = true) }
  }

  @Test
  fun refresh_setsErrorMessageOnFailure() = runTest {
    every { lessonsRepository.observeLessons() } returns flowOf(emptyList())
    coEvery { lessonsRepository.refreshLessons(any()) } returns Result.failure(Exception("Connessione assente"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.refresh()
      awaitItem() // isRefreshing = true
      val error = awaitItem()
      assertEquals("Connessione assente", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
