package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.SchoolAuthRepository
import dev.antigravity.classevivaexpressive.core.datastore.CieCredentials
import dev.antigravity.classevivaexpressive.core.datastore.SecureSessionPersistence
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.LoginResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PasswordLoginBootstrapTest {
  @Test fun sameStudentPasswordReauthenticationPublishesOneReadySnapshot() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot().copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
    val rest = mockk<ClassevivaRestClient>(relaxed = true)
    coEvery { rest.login("student", "new-password") } returns loginResult()
    coEvery { rest.getProfile() } throws ClassevivaNetworkException("card unavailable")
    val coordinator = coordinator(store, rest)
    coEvery { coordinator.refreshAll(force = true) } throws ClassevivaNetworkException("section unavailable")

    val result = coordinator.login("student", "new-password")

    assertEquals("new-token", result.token)
    assertEquals(RestAuthenticationState.READY, store.authenticationState.value.rest)
    assertEquals(StoredCredentials("student", "new-password"), store.readStoredCredentials())
    assertEquals(result, store.readCurrentSession())
  }

  @Test fun optionalCardEnrichmentPreservesTokenRotatedDuringItsRequest() = runTest {
    val store = SessionStore(MemoryPersistence())
    val rest = mockk<ClassevivaRestClient>(relaxed = true)
    coEvery { rest.login(any(), any()) } returns loginResult()
    coEvery { rest.getProfile() } coAnswers {
      val current = store.readAuthSnapshot()!!
      store.writeAuthSnapshot(current.copy(session = current.session!!.copy(token = "rotated-token")))
      StudentProfile(id = "123", name = "Card profile")
    }
    val coordinator = coordinator(store, rest)

    val result = coordinator.login("student", "password")

    assertEquals("rotated-token", result.token)
    assertEquals("Card profile", result.profile.name)
    assertEquals(result, store.readCurrentSession())
  }

  @Test fun loginReturningAnotherStudentCannotReplaceExistingCieAccount() = runTest {
    val store = SessionStore(MemoryPersistence())
    val original = cieSnapshot()
    store.writeAuthSnapshot(original)
    val rest = mockk<ClassevivaRestClient>(relaxed = true)
    coEvery { rest.login(any(), any()) } returns loginResult().copy(studentId = "456")

    assertTrue(runCatching { coordinator(store, rest).login("other", "password") }.isFailure)
    assertEquals(original, store.readAuthSnapshot())
    assertNull(store.readStoredCredentials())
    verify(exactly = 0) { rest.setSession(any()) }
  }

  @Test fun logoutAndAnotherLoginDuringPasswordExchangeDiscardItsLateResult() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val rest = mockk<ClassevivaRestClient>(relaxed = true)
    coEvery { rest.login(any(), any()) } coAnswers {
      entered.complete(Unit)
      release.await()
      loginResult()
    }
    val coordinator = coordinator(store, rest)
    val late = async { runCatching { coordinator.login("student", "password") } }
    entered.await()
    store.clear()
    val replacement = passwordSnapshot().copy(session = session().copy(studentId = "456", token = "other-token"))
    store.writeAuthSnapshot(replacement)
    release.complete(Unit)

    assertTrue(late.await().isFailure)
    assertEquals(replacement, store.readAuthSnapshot())
    verify(exactly = 0) { rest.setSession(any()) }
  }

  @Test fun repositoryPasswordFailureNeverClearsExistingCieSnapshot() = runTest {
    val store = SessionStore(MemoryPersistence())
    val original = cieSnapshot()
    store.writeAuthSnapshot(original)
    val sync = mockk<SchoolSyncCoordinator>(relaxed = true)
    coEvery { sync.login(any(), any()) } throws ClassevivaNetworkException("temporary failure")
    val repository = SchoolAuthRepository(store, sync, mockk(relaxed = true), mockk(relaxed = true))

    assertTrue(repository.login("student", "wrong-password").isFailure)
    assertEquals(original, store.readAuthSnapshot())
    verify(exactly = 0) { sync.attachSession(null) }
  }

  private fun coordinator(store: SessionStore, rest: ClassevivaRestClient): SchoolSyncCoordinator = spyk(SchoolSyncCoordinator(
    json = Json,
    restClient = rest,
    portalClient = mockk(relaxed = true),
    sessionStore = store,
    schoolYearStore = mockk(relaxed = true),
    timetableTemplateStore = mockk(relaxed = true),
    snapshotCacheDao = mockk(relaxed = true),
    syncMetadataDao = mockk(relaxed = true),
    gradeDao = mockk(relaxed = true),
    agendaDao = mockk(relaxed = true),
    changeHistoryDao = mockk(relaxed = true),
    absenceDao = mockk(relaxed = true),
    communicationDao = mockk(relaxed = true),
    materialDao = mockk(relaxed = true),
    documentDao = mockk(relaxed = true),
    privateAssetStore = mockk(relaxed = true),
    predictiveTimetableUseCase = mockk(relaxed = true),
    customEventDao = mockk(relaxed = true),
    studentScoreDao = mockk(relaxed = true),
  )).also { coEvery { it.refreshAll(force = true) } returns SyncStatus() }

  private fun session() = UserSession("old-token", "123", "student", StudentProfile(id = "123"))
  private fun passwordSnapshot() = StoredAuthSnapshot(session = session(), credentials = StoredCredentials("student", "old-password"))
  private fun cieSnapshot() = StoredAuthSnapshot(
    session = session().copy(authMethod = AuthMethod.CIE, ident = "S123X"),
    cie = CieCredentials("pa", "refresh", "S123X", 999_000),
  )
  private fun loginResult() = LoginResult("new-token", "123", StudentProfile(id = "123"), "S123X")

  private class MemoryPersistence : SecureSessionPersistence {
    private var raw: String? = null
    override fun readSnapshot(): String? = raw
    override fun readLegacySession(): String? = null
    override fun readLegacyCredentials(): StoredCredentials? = null
    override fun commit(snapshot: String?): Boolean { raw = snapshot; return true }
  }
}
