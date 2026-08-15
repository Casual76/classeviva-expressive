package dev.antigravity.classevivaexpressive.feature.dashboard

import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.RepositoryRefreshMetadata
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
class SecondaryScreensViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun documentsState_exposesSchoolbooksFailureSeparatelyFromAuthenticEmptyDocuments() = runTest(testDispatcher) {
    val repository = mockk<DocumentsRepository>(relaxed = true)
    every { repository.observeDocuments() } returns MutableStateFlow(emptyList<DocumentItem>())
    every { repository.observeSchoolbooks() } returns MutableStateFlow(emptyList<SchoolbookCourse>())
    every { repository.observeDocumentsRefreshMetadata() } returns MutableStateFlow(RepositoryRefreshMetadata())
    every { repository.observeSchoolbooksRefreshMetadata() } returns MutableStateFlow(
      RepositoryRefreshMetadata(
        lastAttemptAtEpochMillis = 20L,
        lastSuccessAtEpochMillis = 10L,
        refreshError = "Libri non disponibili",
      ),
    )
    coEvery { repository.refreshDocuments(any()) } returns Result.success(emptyList())
    val viewModel = DocumentsViewModel(repository)
    val collection = backgroundScope.launch(testDispatcher) { viewModel.state.collect() }

    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(null, viewModel.state.value.documentsRefreshError)
    assertEquals("Libri non disponibili", viewModel.state.value.schoolbooksRefreshError)
    assertTrue(viewModel.state.value.schoolbooksAreStale)
    assertFalse(viewModel.state.value.documentsAreStale)
    collection.cancel()
  }

  @Test
  fun materialLinkDetection_isCaseInsensitive() {
    assertTrue(material(objectType = "LINK").isLinkMaterial())
    assertTrue(material(objectType = "Link").isLinkMaterial())
    assertFalse(material(objectType = "FILE").isLinkMaterial())
  }

  @Test
  fun materialsViewModel_preservesExternalUrlReturnedByRepository() = runTest(testDispatcher) {
    val repository = mockk<MaterialsRepository>(relaxed = true)
    val item = material(objectType = "LINK")
    val expected = MaterialAsset(
      id = item.id,
      title = item.title,
      objectType = item.objectType,
      externalUrl = "https://example.test/resource",
    )
    every { repository.observeMaterials() } returns MutableStateFlow(listOf(item))
    every { repository.observeMaterialsRefreshMetadata() } returns MutableStateFlow(RepositoryRefreshMetadata())
    coEvery { repository.refreshMaterials(any()) } returns Result.success(listOf(item))
    coEvery { repository.openAsset(item) } returns Result.success(expected)
    val viewModel = MaterialsViewModel(repository)
    var received: MaterialAsset? = null

    viewModel.openAsset(item, onAsset = { received = it }, onError = {})
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(expected.externalUrl, received?.externalUrl)
  }

  private fun material(objectType: String) = MaterialItem(
    id = "material-1",
    teacherId = "teacher-1",
    teacherName = "Docente",
    folderId = "folder-1",
    folderName = "Cartella",
    title = "Risorsa",
    objectId = "object-1",
    objectType = objectType,
    sharedAt = "2026-05-10",
    capabilityState = CapabilityState(),
  )
}
