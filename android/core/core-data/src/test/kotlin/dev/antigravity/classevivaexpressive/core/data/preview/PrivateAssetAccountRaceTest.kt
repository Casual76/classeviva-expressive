package dev.antigravity.classevivaexpressive.core.data.preview

import android.content.Context
import dev.antigravity.classevivaexpressive.core.data.sync.captureAccountWork
import dev.antigravity.classevivaexpressive.core.data.sync.commitAccountWork
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateAssetAccountRaceTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun oldStreamCannotRecreateAccountDirectoryAfterLogoutAndReplacementLogin() = runTest {
    val context = mockk<Context>()
    val files = temporary.newFolder("files")
    val cache = temporary.newFolder("cache")
    every { context.filesDir } returns files
    every { context.cacheDir } returns cache
    val assets = PrivateAssetStore(context)
    val store = testSessionStore()
    val workA = store.captureAccountWork()
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var first = true
    val source = object : ByteArrayInputStream("private-data".encodeToByteArray()) {
      override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (first) {
          first = false
          entered.complete(Unit)
          runBlocking { release.await() }
        }
        return super.read(buffer, offset, length)
      }
    }
    val pending = async {
      runCatching {
        assets.store(workA.session.studentId, "2026-2027", "asset", "asset.pdf", "application/pdf", source,
          admitCommit = { effect -> store.commitAccountWork(workA) { effect() } })
      }
    }
    entered.await()
    store.clearAndRun { runCatching { assets.clearStudent(workA.session.studentId) } }
    val replacement = workA.session.copy(studentId = "student-b", token = "b")
    store.writeSession(replacement)
    val replacementDirectory = File(files, "offline_assets/${privateAccountPathPart(replacement.studentId)}").apply { mkdirs() }
    val replacementFile = File(replacementDirectory, "keep.pdf").apply { writeText("b-data") }
    release.complete(Unit)

    assertTrue(pending.await().isFailure)
    assertFalse(File(files, "offline_assets/${privateAccountPathPart(workA.session.studentId)}").exists())
    assertEquals("b-data", replacementFile.readText())
    assertTrue(File(cache, "school_asset_staging").listFiles().orEmpty().isEmpty())
  }
}
