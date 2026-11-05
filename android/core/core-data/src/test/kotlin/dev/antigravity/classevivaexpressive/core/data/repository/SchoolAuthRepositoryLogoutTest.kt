package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.preview.PrivateSessionDataCleaner
import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class SchoolAuthRepositoryLogoutTest {
  @Test fun cancellingCallerDoesNotInterruptAdmittedPrivateCleanup() = runTest {
    val store = testSessionStore()
    val sync = mockk<SchoolSyncCoordinator>(relaxed = true)
    val cleaner = mockk<PrivateSessionDataCleaner>()
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()
    coEvery { cleaner.clear("student-1") } coAnswers {
      entered.complete(Unit)
      release.await()
      finished.complete(Unit)
    }
    val repository = SchoolAuthRepository(store, sync, mockk(relaxed = true), cleaner)
    val pending = async { repository.logout() }
    entered.await()
    pending.cancel()
    assertNull(store.readCurrentSession())
    release.complete(Unit)
    pending.cancelAndJoin()
    finished.await()
    verify(exactly = 1) { sync.attachSession(null) }
    coVerify(exactly = 1) { sync.clearAccountDerivedState() }
    coVerify(exactly = 1) { cleaner.clear("student-1") }
  }
}
