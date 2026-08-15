package dev.antigravity.classevivaexpressive.core.data.preview

import android.app.DownloadManager
import android.content.Context
import dev.antigravity.classevivaexpressive.core.database.database.AttachmentCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordDao
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateSessionDataCleanerTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun clear_removesOnlyTheRequestedAccountsFilesAndRows() = runTest {
    val context = mockk<Context>()
    val privateAssetStore = mockk<PrivateAssetStore>(relaxed = true)
    val attachmentCacheDao = mockk<AttachmentCacheDao>(relaxed = true)
    val downloadRecordDao = mockk<DownloadRecordDao>(relaxed = true)
    val downloadManager = mockk<DownloadManager>(relaxed = true)
    val filesDirectory = temporaryFolder.newFolder("files")
    val downloadsDirectory = temporaryFolder.newFolder("downloads")
    every { context.filesDir } returns filesDirectory
    every { context.getExternalFilesDir(any()) } returns downloadsDirectory
    val accountA = privateAccountPathPart("student-a")
    val accountB = privateAccountPathPart("student-b")
    val attachmentA = File(filesDirectory, "attachment_cache/$accountA").apply { mkdirs() }
    val attachmentB = File(filesDirectory, "attachment_cache/$accountB").apply { mkdirs() }
    val downloadA = File(downloadsDirectory, accountA).apply { mkdirs() }
    val downloadB = File(downloadsDirectory, accountB).apply { mkdirs() }
    coEvery { downloadRecordDao.getByPrefix("student-a::") } returns listOf(
      DownloadRecordEntity(
        id = "student-a::42",
        sourceUrl = "https://example.test/a.pdf",
        displayName = "a.pdf",
        mimeType = "application/pdf",
        status = "QUEUED",
        updatedAtEpochMillis = 1L,
      ),
    )
    val cleaner = PrivateSessionDataCleaner(
      context = context,
      privateAssetStore = privateAssetStore,
      attachmentCacheDao = attachmentCacheDao,
      downloadRecordDao = downloadRecordDao,
      downloadManager = downloadManager,
    )

    cleaner.clear("student-a")

    assertFalse(attachmentA.exists())
    assertFalse(downloadA.exists())
    assertTrue(attachmentB.exists())
    assertTrue(downloadB.exists())
    verify(exactly = 1) { privateAssetStore.clearStudent("student-a") }
    verify(exactly = 1) { downloadManager.remove(42L) }
    coVerify(exactly = 1) { attachmentCacheDao.deleteByPrefix("student-a::") }
    coVerify(exactly = 1) { downloadRecordDao.deleteByPrefix("student-a::") }
    coVerify(exactly = 0) { attachmentCacheDao.clearAll() }
    coVerify(exactly = 0) { downloadRecordDao.clearAll() }
  }
}
