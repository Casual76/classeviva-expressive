package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccountWorkTest {
  @Test fun transparentTokenRefreshDoesNotDiscardLegitimateInFlightWork() = runTest {
    val store = testSessionStore()
    val work = store.captureAccountWork()
    val current = store.readAuthSnapshot()!!
    store.writeAuthSnapshot(current.copy(session = current.session!!.copy(token = "rotated")))
    var committed = false
    store.commitAccountWork(work) { committed = true }
    assertTrue(committed)
  }

  @Test fun logoutAndSameStudentReentryDiscardLateWriterEvenWithMatchingStudentId() = runTest {
    val store = testSessionStore()
    val work = store.captureAccountWork()
    val original = store.readAuthSnapshot()!!
    store.clear()
    store.writeAuthSnapshot(original)
    var committed = false
    assertTrue(runCatching { store.commitAccountWork(work) { committed = true } }.isFailure)
    assertFalse(committed)
  }

  @Test fun nestedOldWorkerCannotAdoptNewAccountForLocalTemplateWriter() = runTest {
    val store = testSessionStore()
    val workA = store.captureAccountWork()
    withAccountWork(workA) {
      val original = store.readAuthSnapshot()!!
      store.clear()
      store.writeAuthSnapshot(original.copy(session = original.session!!.copy(studentId = "student-b")))
      val workB = store.captureAccountWork()
      var wroteB = false
      assertTrue(runCatching { store.commitAccountWork(workB) { wroteB = true } }.isFailure)
      assertFalse(wroteB)
    }
  }

  @Test fun logoutWaitsForAlreadyAdmittedSuspendedDaoBeforeRunningCleanup() = runTest {
    val store = testSessionStore()
    val work = store.captureAccountWork()
    val daoEntered = CompletableDeferred<Unit>()
    val releaseDao = CompletableDeferred<Unit>()
    val order = mutableListOf<String>()
    val writer = async {
      store.commitAccountWork(work) {
        daoEntered.complete(Unit)
        releaseDao.await()
        order += "dao"
      }
    }
    daoEntered.await()
    val logout = async { store.clearAndRun { order += "cleanup" } }
    assertFalse(logout.isCompleted)
    releaseDao.complete(Unit)
    writer.await()
    logout.await()
    assertEquals(listOf("dao", "cleanup"), order)
    assertNull(store.readCurrentSession())
  }

  @Test fun terminalRestRejectionSuspendsRemoteWorkWhileOfflineCaptureRemainsAvailable() = runTest {
    val store = testSessionStore()
    val old = store.captureAccountWork()
    store.writeAuthSnapshot(store.readAuthSnapshot()!!.copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
    var remoteEffect = false
    assertTrue(runCatching { store.commitAccountWork(old) { remoteEffect = true } }.isFailure)
    assertFalse(remoteEffect)
    assertTrue(runCatching { store.captureAccountWork() }.isFailure)
    val offline = store.captureAccountWork(allowOffline = true)
    assertEquals("cached", store.commitAccountWork(offline) { "cached" })
  }
}
