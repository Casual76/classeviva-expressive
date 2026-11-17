package dev.antigravity.classevivaexpressive.feature.communications

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationsDetailRaceTest {
  private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val communications = mockk<CommunicationsRepository>(relaxed = true)
  private val dashboard = mockk<DashboardRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { communications.observeCommunications() } returns flowOf(emptyList())
    every { communications.observeNotes() } returns flowOf(emptyList())
    every { dashboard.observeDashboard() } returns flowOf(DashboardSnapshot())
    coEvery { communications.refreshCommunications(any()) } returns Result.success(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun communication(id: String) = Communication(
    id = id, pubId = id, evtCode = "CIR", title = id, contentPreview = "Anteprima $id",
    sender = "Scuola", date = "2026-11-17", read = false,
  )

  private fun detail(id: String) = CommunicationDetail(communication(id), "Contenuto $id")

  private fun noteDetail() = NoteDetail(
    note = Note(
      id = "n1", categoryCode = "NTWN", categoryLabel = "Richiamo", title = "Nota",
      contentPreview = "Anteprima nota", date = "2026-11-17", author = "Docente",
      read = false, severity = "warning",
    ),
    content = "Dettaglio nota",
  )

  @Test
  fun slowerPreviousCommunicationCannotReplaceTheLastTappedCommunication() = runTest {
    val firstResponse = CompletableDeferred<Result<CommunicationDetail>>()
    coEvery { communications.getCommunicationDetail("a", "CIR") } coAnswers {
      // Some repository/cache boundaries finish their work despite cancellation.
      withContext(NonCancellable) { firstResponse.await() }
    }
    coEvery { communications.getCommunicationDetail("b", "CIR") } returns Result.success(detail("b"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      vm.openCommunication("b", "CIR")
      assertEquals("b", vm.state.value.selectedCommunication?.communication?.id)
      firstResponse.complete(Result.success(detail("a")))
      assertEquals("b", vm.state.value.selectedCommunication?.communication?.id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun communicationResponseCannotReplaceANoteOpenedAfterwards() = runTest {
    val firstResponse = CompletableDeferred<Result<CommunicationDetail>>()
    coEvery { communications.getCommunicationDetail("a", "CIR") } coAnswers {
      withContext(NonCancellable) { firstResponse.await() }
    }
    coEvery { communications.getNoteDetail("n1", "NTWN") } returns Result.success(noteDetail())
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      vm.openNote("n1", "NTWN")
      firstResponse.complete(Result.success(detail("a")))
      assertNull(vm.state.value.selectedCommunication)
      assertEquals("n1", vm.state.value.selectedNote?.note?.id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun noteResponseCannotReplaceACommunicationOpenedAfterwards() = runTest {
    val firstResponse = CompletableDeferred<Result<NoteDetail>>()
    coEvery { communications.getNoteDetail("n1", "NTWN") } coAnswers {
      withContext(NonCancellable) { firstResponse.await() }
    }
    coEvery { communications.getCommunicationDetail("b", "CIR") } returns Result.success(detail("b"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openNote("n1", "NTWN")
      vm.openCommunication("b", "CIR")
      firstResponse.complete(Result.success(noteDetail()))
      assertNull(vm.state.value.selectedNote)
      assertEquals("b", vm.state.value.selectedCommunication?.communication?.id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun dismissingCachedCommunicationCancelsItsPendingDetailAndDoesNotReopenIt() = runTest {
    val response = CompletableDeferred<Result<CommunicationDetail>>()
    every { communications.observeCommunications() } returns flowOf(listOf(communication("a")))
    coEvery { communications.getCommunicationDetail("a", "CIR") } coAnswers {
      withContext(NonCancellable) { response.await() }
    }
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      assertEquals("a", vm.state.value.selectedCommunication?.communication?.id)
      vm.dismissDetail()
      response.complete(Result.success(detail("a")))
      assertNull(vm.state.value.selectedCommunication)
      assertNull(vm.state.value.selectedNote)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun dismissingPendingNoteDoesNotOpenItWhenTheResponseArrives() = runTest {
    val response = CompletableDeferred<Result<NoteDetail>>()
    coEvery { communications.getNoteDetail("n1", "NTWN") } coAnswers {
      withContext(NonCancellable) { response.await() }
    }
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openNote("n1", "NTWN")
      vm.dismissDetail()
      response.complete(Result.success(noteDetail()))
      assertNull(vm.state.value.selectedNote)
      assertNull(vm.state.value.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun actionCompletionDoesNotReopenADismissedCommunication() = runTest {
    val response = CompletableDeferred<Result<CommunicationDetail>>()
    coEvery { communications.getCommunicationDetail("a", "CIR") } returns Result.success(detail("a"))
    coEvery { communications.acknowledgeCommunication(any()) } coAnswers { response.await() }
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      vm.acknowledge(detail("a"))
      vm.dismissDetail()
      response.complete(Result.success(detail("a")))
      assertNull(vm.state.value.selectedCommunication)
      assertFalse(vm.state.value.isSubmittingAction)
      assertEquals(CommunicationsMessageKind.Success, vm.state.value.lastMessage?.kind)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun actionCompletionCannotReplaceTheNextCommunicationAndDuplicateSubmitIsIgnored() = runTest {
    val response = CompletableDeferred<Result<CommunicationDetail>>()
    coEvery { communications.getCommunicationDetail(any(), "CIR") } coAnswers {
      Result.success(detail(firstArg()))
    }
    coEvery { communications.acknowledgeCommunication(any()) } coAnswers { response.await() }
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      vm.acknowledge(detail("a"))
      vm.acknowledge(detail("a"))
      vm.openCommunication("b", "CIR")
      response.complete(Result.success(detail("a")))
      assertEquals("b", vm.state.value.selectedCommunication?.communication?.id)
      coVerify(exactly = 1) { communications.acknowledgeCommunication(any()) }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun cancellationWrappedInResultDoesNotBecomeAUserError() = runTest {
    coEvery { communications.getCommunicationDetail("a", "CIR") } returns
      Result.failure(CancellationException("Account changed"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      assertNull(vm.state.value.selectedCommunication)
      assertNull(vm.state.value.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun failedFullContentLoadKeepsTheCachedPreviewAndReportsTheFailure() = runTest {
    every { communications.observeCommunications() } returns flowOf(listOf(communication("a")))
    coEvery { communications.getCommunicationDetail("a", "CIR") } returns
      Result.failure(IllegalStateException("Offline"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      assertEquals("Anteprima a", vm.state.value.selectedCommunication?.content)
      assertEquals("Offline", vm.state.value.lastMessage?.text)
      assertEquals(CommunicationsMessageKind.Error, vm.state.value.lastMessage?.kind)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun failedFullContentLoadWithoutPreviewDoesNotLeaveALoadingMessageForever() = runTest {
    every { communications.observeCommunications() } returns
      flowOf(listOf(communication("a").copy(contentPreview = "")))
    coEvery { communications.getCommunicationDetail("a", "CIR") } returns
      Result.failure(IllegalStateException("Offline"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.openCommunication("a", "CIR")
      assertEquals(
        "Contenuto completo non disponibile. Riprova ad aprire la comunicazione.",
        vm.state.value.selectedCommunication?.content,
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun listAcknowledgementIgnoresRepeatedTapsWhileTheFirstRequestIsPending() = runTest {
    val response = CompletableDeferred<Result<CommunicationDetail>>()
    coEvery { communications.getCommunicationDetail("a", "CIR") } coAnswers { response.await() }
    coEvery { communications.acknowledgeCommunication(any()) } returns Result.success(detail("a"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.acknowledgeFromList("a", "CIR")
      vm.acknowledgeFromList("a", "CIR")
      response.complete(Result.success(detail("a")))
      coVerify(exactly = 1) { communications.getCommunicationDetail("a", "CIR") }
      coVerify(exactly = 1) { communications.acknowledgeCommunication(any()) }
      assertNull(vm.state.value.selectedCommunication)
      assertFalse(vm.state.value.isSubmittingAction)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun cancelledActionClearsBusyStateWithoutAnErrorMessageOrAReopenedDetail() = runTest {
    coEvery { communications.acknowledgeCommunication(any()) } returns
      Result.failure(CancellationException("Account changed"))
    val vm = CommunicationsViewModel(communications, dashboard)

    vm.state.test {
      awaitItem()
      vm.acknowledge(detail("a"))
      assertFalse(vm.state.value.isSubmittingAction)
      assertNull(vm.state.value.lastMessage)
      assertNull(vm.state.value.selectedCommunication)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
