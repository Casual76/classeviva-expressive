package dev.antigravity.classevivaexpressive.feature.communications

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
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
class CommunicationsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val communicationsRepository = mockk<CommunicationsRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = CommunicationsViewModel(communicationsRepository)

  private fun stubBase() {
    every { communicationsRepository.observeCommunications() } returns flowOf(emptyList())
    every { communicationsRepository.observeNotes() } returns flowOf(emptyList())
  }

  private fun buildCommunication(id: String = "c1") = Communication(
    id = id, pubId = id, evtCode = "CIR", title = "Circolare $id",
    contentPreview = "Preview", sender = "Scuola", date = "2026-03-20", read = false,
    capabilityState = CapabilityState(),
  )

  private fun buildDetail(comm: Communication = buildCommunication()) = CommunicationDetail(
    communication = comm, content = "Contenuto della comunicazione",
  )

  // ─── Caricamento comunicazioni ────────────────────────────────────────────

  @Test
  fun communicationsFromRepository_areExposedInState() = runTest {
    val comm = buildCommunication()
    every { communicationsRepository.observeCommunications() } returns flowOf(listOf(comm))
    every { communicationsRepository.observeNotes() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.communications.size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun notesFromRepository_areExposedInState() = runTest {
    val note = Note(id = "n1", categoryCode = "NTWN", categoryLabel = "Richiamo", title = "Richiamo", contentPreview = "Nota", date = "2026-03-20", author = "Prof", read = false, severity = "warning")
    every { communicationsRepository.observeCommunications() } returns flowOf(emptyList())
    every { communicationsRepository.observeNotes() } returns flowOf(listOf(note))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.notes.size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Apertura dettaglio comunicazione ─────────────────────────────────────

  @Test
  fun openCommunication_setsSelectedCommunicationInState() = runTest {
    stubBase()
    val comm = buildCommunication()
    val detail = buildDetail(comm)
    coEvery { communicationsRepository.getCommunicationDetail("c1", "CIR") } returns Result.success(detail)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.openCommunication("c1", "CIR")
      val updated = awaitItem()
      assertNotNull(updated.selectedCommunication)
      assertEquals("Contenuto della comunicazione", updated.selectedCommunication?.content)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun openCommunication_setsErrorMessageOnFailure() = runTest {
    stubBase()
    coEvery { communicationsRepository.getCommunicationDetail(any(), any()) } returns Result.failure(Exception("Errore rete"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.openCommunication("c1", "CIR")
      val updated = awaitItem()
      assertEquals("Errore rete", updated.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Aderire alla comunicazione ───────────────────────────────────────────

  @Test
  fun acknowledge_callsRepositoryAndSetsSuccessMessage() = runTest {
    stubBase()
    val detail = buildDetail()
    val updatedDetail = detail.copy(communication = detail.communication.copy(read = true))
    coEvery { communicationsRepository.acknowledgeCommunication(detail) } returns Result.success(updatedDetail)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.acknowledge(detail)
      val updated = awaitItem()
      assertEquals("Conferma inviata.", updated.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
    coVerify { communicationsRepository.acknowledgeCommunication(detail) }
  }

  // ─── Risposta alla comunicazione ──────────────────────────────────────────

  @Test
  fun reply_callsRepositoryWithText() = runTest {
    stubBase()
    val detail = buildDetail()
    coEvery { communicationsRepository.replyToCommunication(detail, "Testo risposta") } returns Result.success(detail)

    val vm = buildViewModel()
    vm.reply(detail, "Testo risposta")

    coVerify { communicationsRepository.replyToCommunication(detail, "Testo risposta") }
  }

  // ─── Download allegato ────────────────────────────────────────────────────

  @Test
  fun downloadAttachment_callsQueueDownloadAndSetsMessage() = runTest {
    stubBase()
    val attachment = RemoteAttachment(id = "att1", name = "circolare.pdf", url = "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/attach/CIR/99/101")
    coEvery { communicationsRepository.queueDownload(attachment) } returns Result.success(1L)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.downloadAttachment(attachment)
      val updated = awaitItem()
      assertTrue(updated.lastMessage?.contains("circolare.pdf") == true)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Caricamento file ─────────────────────────────────────────────────────

  @Test
  fun uploadFile_callsRepositoryAndSetsMessage() = runTest {
    stubBase()
    val detail = buildDetail()
    coEvery { communicationsRepository.uploadCommunicationFile(detail, "file.pdf", "application/pdf", any()) } returns Result.success(detail)

    val vm = buildViewModel()
    vm.upload(detail, "file.pdf", "application/pdf", byteArrayOf(1, 2, 3))

    coVerify { communicationsRepository.uploadCommunicationFile(detail, "file.pdf", "application/pdf", any()) }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    stubBase()
    coEvery { communicationsRepository.refreshCommunications(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { communicationsRepository.refreshCommunications(force = true) }
  }
}
