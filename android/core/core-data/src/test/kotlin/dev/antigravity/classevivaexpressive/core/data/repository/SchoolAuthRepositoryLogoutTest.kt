package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.preview.PrivateSessionDataCleaner
import dev.antigravity.classevivaexpressive.core.data.external.ExternalDashboardInvalidator
import dev.antigravity.classevivaexpressive.core.data.sync.SchoolSyncCoordinator
import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolAuthRepositoryLogoutTest {
  @Test fun logoutInvalidatesPublishedPrivateDataBeforeSlowCleanupFinishes() = runTest {
    val store = testSessionStore()
    val cleaner = mockk<PrivateSessionDataCleaner>()
    val enteredCleanup = CompletableDeferred<Unit>()
    val releaseCleanup = CompletableDeferred<Unit>()
    var publishedPrivateData = true
    val invalidator = object : ExternalDashboardInvalidator {
      override suspend fun invalidateDashboard() {
        assertNull(store.readCurrentSession())
        assertNull(store.session.value)
        assertEquals(store.accountGuard.capture().epoch, store.dataEpoch.value)
        publishedPrivateData = false
      }
    }
    coEvery { cleaner.clear(any()) } coAnswers { enteredCleanup.complete(Unit); releaseCleanup.await() }
    val repository = SchoolAuthRepository(store, mockk(relaxed = true), mockk(relaxed = true), cleaner, setOf(invalidator))
    val pending = async { repository.logout() }
    enteredCleanup.await()
    assertTrue("published full data remained visible during cleanup", !publishedPrivateData)
    assertTrue(!pending.isCompleted)
    releaseCleanup.complete(Unit)
    pending.await()
  }

  @Test fun oneFailedExternalInvalidationDoesNotSkipTheOthersOrPrivateCleanup() = runTest {
    val store = testSessionStore()
    val cleaner = mockk<PrivateSessionDataCleaner>(relaxed = true)
    var repainted = false
    val broken = object : ExternalDashboardInvalidator {
      override suspend fun invalidateDashboard() { throw IllegalStateException("surface unavailable") }
    }
    val remaining = object : ExternalDashboardInvalidator {
      override suspend fun invalidateDashboard() { repainted = true }
    }
    val repository = SchoolAuthRepository(store, mockk(relaxed = true), mockk(relaxed = true), cleaner, linkedSetOf(broken, remaining))
    repository.logout()
    assertTrue(repainted)
    coVerify(exactly = 1) { cleaner.clear("student-1") }
  }

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
