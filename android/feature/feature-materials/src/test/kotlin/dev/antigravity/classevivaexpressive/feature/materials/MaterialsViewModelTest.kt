package dev.antigravity.classevivaexpressive.feature.materials

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
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
class MaterialsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val materialsRepository = mockk<MaterialsRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel() = MaterialsViewModel(materialsRepository)

  private fun buildMaterialItem(id: String = "m1") = MaterialItem(
    id = id, teacherId = "t1", teacherName = "Docente", folderId = "f1", folderName = "Cartella",
    title = "Materiale $id", objectId = "obj", objectType = "link", sharedAt = "2026-03-20",
    capabilityState = CapabilityState(status = CapabilityStatus.AVAILABLE),
  )

  // ─── Caricamento materiali ────────────────────────────────────────────────

  @Test
  fun materialsFromRepository_areExposedInState() = runTest {
    val item = buildMaterialItem()
    every { materialsRepository.observeMaterials() } returns flowOf(listOf(item))

    val vm = buildViewModel()

    vm.state.test {
      val state = awaitItem()
      assertEquals(1, state.materials.size)
      assertEquals("m1", state.materials.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Apertura materiale ───────────────────────────────────────────────────

  @Test
  fun open_setsSelectedItemAndLoadsAsset() = runTest {
    val item = buildMaterialItem()
    val asset = MaterialAsset(id = "m1", title = "Materiale", objectType = "link", sourceUrl = "https://example.com", capabilityState = CapabilityState(CapabilityStatus.AVAILABLE))
    every { materialsRepository.observeMaterials() } returns flowOf(listOf(item))
    coEvery { materialsRepository.openAsset(item) } returns Result.success(asset)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.open(item)
      val loading = awaitItem()
      assertTrue(loading.isLoadingPreview)
      val loaded = awaitItem()
      assertNotNull(loaded.selectedAsset)
      assertEquals("https://example.com", loaded.selectedAsset?.sourceUrl)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun open_setsErrorMessageOnFailure() = runTest {
    val item = buildMaterialItem()
    every { materialsRepository.observeMaterials() } returns flowOf(listOf(item))
    coEvery { materialsRepository.openAsset(item) } returns Result.failure(Exception("Contenuto non disponibile"))

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.open(item)
      awaitItem() // loading
      val error = awaitItem()
      assertEquals("Contenuto non disponibile", error.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Download allegato ────────────────────────────────────────────────────

  @Test
  fun download_callsQueueDownloadAndSetsMessage() = runTest {
    val attachment = RemoteAttachment(id = "att1", name = "slide.pdf", url = "https://web.spaggiari.eu/rest/v1/students/55/didactics/item/10")
    every { materialsRepository.observeMaterials() } returns flowOf(emptyList())
    coEvery { materialsRepository.queueDownload(attachment) } returns Result.success(1L)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.download(attachment)
      val updated = awaitItem()
      assertTrue(updated.lastMessage?.contains("slide.pdf") == true)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsRepositoryRefresh() = runTest {
    every { materialsRepository.observeMaterials() } returns flowOf(emptyList())
    coEvery { materialsRepository.refreshMaterials(any()) } returns Result.success(emptyList())

    val vm = buildViewModel()
    vm.refresh()

    coVerify { materialsRepository.refreshMaterials(force = true) }
  }
}
