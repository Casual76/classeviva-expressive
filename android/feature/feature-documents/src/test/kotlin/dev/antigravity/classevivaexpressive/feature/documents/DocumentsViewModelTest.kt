package dev.antigravity.classevivaexpressive.feature.documents

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
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
class DocumentsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val documentsRepository = mockk<DocumentsRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = DocumentsViewModel(documentsRepository)

  private fun buildDocument(id: String = "doc1") = DocumentItem(
    id = id, title = "Documento $id", detail = "Dettaglio",
    viewUrl = "https://web.spaggiari.eu/doc/$id",
    capabilityState = CapabilityState(status = CapabilityStatus.AVAILABLE),
  )

  private fun stubBase() {
    every { documentsRepository.observeDocuments() } returns flowOf(emptyList())
    every { documentsRepository.observeSchoolbooks() } returns flowOf(emptyList())
  }

  // ─── Caricamento documenti ────────────────────────────────────────────────

  @Test
  fun documentsFromRepository_areExposedInState() = runTest {
    val doc = buildDocument()
    every { documentsRepository.observeDocuments() } returns flowOf(listOf(doc))
    every { documentsRepository.observeSchoolbooks() } returns flowOf(emptyList())

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.documents.size)
      assertEquals("doc1", state.documents.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun schoolbooksFromRepository_areExposedInState() = runTest {
    val course = SchoolbookCourse(id = "c1", title = "Matematica", books = emptyList())
    every { documentsRepository.observeDocuments() } returns flowOf(emptyList())
    every { documentsRepository.observeSchoolbooks() } returns flowOf(listOf(course))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.schoolbooks.size)
      assertEquals("Matematica", state.schoolbooks.first().title)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Anteprima documento ──────────────────────────────────────────────────

  @Test
  fun preview_setsSelectedDocumentAndLoadsAsset() = runTest {
    val doc = buildDocument()
    val asset = DocumentAsset(
      id = "doc1", title = "Documento doc1",
      sourceUrl = "https://web.spaggiari.eu/doc/doc1",
      capabilityState = CapabilityState(CapabilityStatus.AVAILABLE),
    )
    every { documentsRepository.observeDocuments() } returns flowOf(listOf(doc))
    every { documentsRepository.observeSchoolbooks() } returns flowOf(emptyList())
    coEvery { documentsRepository.openDocument(doc) } returns Result.success(asset)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.preview(doc)
      val loading = awaitItem()
      assertTrue(loading.isLoadingPreview)
      val loaded = awaitItem()
      assertNotNull(loaded.selectedAsset)
      assertEquals("https://web.spaggiari.eu/doc/doc1", loaded.selectedAsset?.sourceUrl)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun preview_setsErrorMessageOnFailure() = runTest {
    val doc = buildDocument()
    every { documentsRepository.observeDocuments() } returns flowOf(listOf(doc))
    every { documentsRepository.observeSchoolbooks() } returns flowOf(emptyList())
    coEvery { documentsRepository.openDocument(doc) } returns Result.failure(Exception("Documento non disponibile"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.preview(doc)
      awaitItem() // isLoadingPreview = true
      val error = awaitItem()
      assertEquals("Documento non disponibile", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Download documento ───────────────────────────────────────────────────

  @Test
  fun download_callsQueueDownloadAndSetsMessage() = runTest {
    stubBase()
    val doc = buildDocument()
    coEvery { documentsRepository.queueDownload(doc) } returns Result.success(1L)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.download(doc)
      val updated = awaitItem()
      assertTrue(updated.lastMessage?.contains("Documento doc1") == true)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun download_setsErrorMessageOnFailure() = runTest {
    stubBase()
    val doc = buildDocument()
    coEvery { documentsRepository.queueDownload(doc) } returns Result.failure(Exception("Download fallito"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.download(doc)
      val error = awaitItem()
      assertEquals("Download fallito", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    stubBase()
    coEvery { documentsRepository.refreshDocuments(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { documentsRepository.refreshDocuments(force = true) }
  }
}
