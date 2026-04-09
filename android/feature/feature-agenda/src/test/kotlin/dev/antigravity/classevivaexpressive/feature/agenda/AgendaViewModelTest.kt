package dev.antigravity.classevivaexpressive.feature.agenda

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val agendaRepository = mockk<AgendaRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = AgendaViewModel(agendaRepository)

  // ─── Caricamento voci agenda ──────────────────────────────────────────────

  @Test
  fun agendaItemsFromRepository_areExposedInState() = runTest {
    val item = AgendaItem(id = "i1", title = "Verifica chimica", subtitle = "Chimica", date = "2026-03-25", category = AgendaCategory.ASSESSMENT)
    every { agendaRepository.observeAgenda() } returns flowOf(listOf(item))
    every { agendaRepository.observeCustomEvents() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.items.size)
      assertEquals("i1", state.items.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun homeworkItemsArePresentInAgendaState() = runTest {
    val homework = AgendaItem(id = "hw1", title = "Compito di fisica", subtitle = "Fisica", date = "2026-03-26", category = AgendaCategory.HOMEWORK)
    every { agendaRepository.observeAgenda() } returns flowOf(listOf(homework))
    every { agendaRepository.observeCustomEvents() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(AgendaCategory.HOMEWORK, state.items.first().category)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun customEventsFromRepository_areExposedInState() = runTest {
    val event = CustomEvent(id = "c1", title = "Mio evento", description = "Descrizione", subject = "Personale", date = "2026-04-01")
    every { agendaRepository.observeAgenda() } returns flowOf(emptyList())
    every { agendaRepository.observeCustomEvents() } returns flowOf(listOf(event))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.customEvents.size)
      assertEquals("c1", state.customEvents.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Aggiunta evento personalizzato ──────────────────────────────────────

  @Test
  fun addCustomEvent_callsRepositoryWithCorrectData() = runTest {
    every { agendaRepository.observeAgenda() } returns flowOf(emptyList())
    every { agendaRepository.observeCustomEvents() } returns flowOf(emptyList())

    val vm = buildViewModel()
    vm.addCustomEvent(
      title = "Ripetizione",
      description = "Matematica con prof",
      subject = "Matematica",
      date = "2026-04-05",
      time = "15:00",
    )

    coVerify {
      agendaRepository.addCustomEvent(
        match {
          it.title == "Ripetizione" &&
            it.subject == "Matematica" &&
            it.date == "2026-04-05" &&
            it.time == "15:00" &&
            it.category == AgendaCategory.CUSTOM
        },
      )
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { agendaRepository.observeAgenda() } returns flowOf(emptyList())
    every { agendaRepository.observeCustomEvents() } returns flowOf(emptyList())
    coEvery { agendaRepository.refreshAgenda(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { agendaRepository.refreshAgenda(force = true) }
  }

  @Test
  fun refresh_setsIsRefreshingTrueTheFalse() = runTest {
    every { agendaRepository.observeAgenda() } returns flowOf(emptyList())
    every { agendaRepository.observeCustomEvents() } returns flowOf(emptyList())
    coEvery { agendaRepository.refreshAgenda(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      awaitItem() // initial
      vm.refresh()
      val refreshing = awaitItem()
      assertTrue(refreshing.isRefreshing)
      val done = awaitItem()
      assertTrue(!done.isRefreshing)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
