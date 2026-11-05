package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.MemorySessionPersistence
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventDao
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreDao
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.PortalSessionSnapshot
import dev.antigravity.classevivaexpressive.core.datastore.StoredPortalCookie
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.PortalAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.network.client.CieLoginResult
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import dev.antigravity.classevivaexpressive.core.network.client.PortalClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CieLoginBootstrapTest {
  private val persistence = MemorySessionPersistence()
  private val store = SessionStore(persistence)
  private val rest = mockk<ClassevivaRestClient>(relaxed = true)
  private val portal = mockk<PortalClient>(relaxed = true)
  private val templates = mockk<TimetableTemplateStore>(relaxed = true)
  private val events = mockk<CustomEventDao>(relaxed = true)
  private val scores = mockk<StudentScoreDao>(relaxed = true)

  @Test fun cieCommitsExclusiveDurableReadySnapshotBeforePortalAndOptionalProfile() = runTest {
    coEvery { portal.importCieCookies(any(), any()) } coAnswers {
      val restored = SessionStore(persistence).readAuthSnapshot()!!
      assertEquals(RestAuthenticationState.READY, restored.authenticationState.rest)
      assertEquals("rest-token", restored.session!!.token)
      assertNull(restored.credentials)
      assertEquals("original-pa", restored.cie!!.pa)
      assertEquals("refresh", restored.cie!!.refreshToken)
      throw ClassevivaNetworkException("portal unavailable")
    }
    val result = coordinator().loginWithCie("original-pa", "portal=cookie", store.accountGuard.authenticationEpoch())

    assertEquals(AuthMethod.CIE, result.authMethod)
    assertEquals("123", result.studentId)
    assertEquals(result, SessionStore(persistence).readCurrentSession())
    coVerify(exactly = 1) { portal.importCieCookies("portal=cookie", "S123X") }
  }

  @Test fun browserFromPreviousEpochIsRejectedBeforeExchangingPaEvenForSameStudent() = runTest {
    store.writeAuthSnapshot(passwordSnapshot())
    val epoch = store.accountGuard.authenticationEpoch()
    store.clear()
    store.writeAuthSnapshot(passwordSnapshot())
    assertTrue(runCatching { coordinator().loginWithCie("original-pa", "", epoch) }.isFailure)
    coVerify(exactly = 0) { rest.loginWithCie(any()) }
    assertEquals(AuthMethod.PASSWORD, store.readCurrentSession()!!.authMethod)
  }

  @Test fun logoutDuringExchangePreventsAnyLateSnapshotPortalOrAttachment() = runTest {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val sync = coordinator()
    coEvery { rest.loginWithCie(any()) } coAnswers {
      entered.complete(Unit)
      release.await()
      result()
    }
    val old = async { runCatching { sync.loginWithCie("original-pa", "a=b", store.accountGuard.authenticationEpoch()) } }
    entered.await()
    store.clear()
    store.writeAuthSnapshot(passwordSnapshot("456"))
    release.complete(Unit)

    assertTrue(old.await().isFailure)
    assertEquals("456", store.readCurrentSession()!!.studentId)
    coVerify(exactly = 0) { portal.importCieCookies(any(), any()) }
    verify(exactly = 0) { rest.setSession(any()) }
  }

  @Test fun cancellationDuringExchangeLeavesPreviousValidSessionUntouched() = runTest {
    store.writeAuthSnapshot(passwordSnapshot())
    val before = store.readAuthSnapshot()
    val entered = CompletableDeferred<Unit>()
    val sync = coordinator()
    coEvery { rest.loginWithCie(any()) } coAnswers {
      entered.complete(Unit)
      CompletableDeferred<Unit>().await()
      result()
    }
    val pending = async { sync.loginWithCie("original-pa", "", store.accountGuard.authenticationEpoch()) }
    entered.await()
    pending.cancelAndJoin()
    assertEquals(before, store.readAuthSnapshot())
    verify(exactly = 0) { rest.setSession(any()) }
  }

  @Test fun backgroundRefreshRejectionDuringBrowserAndExchangeDoesNotInvalidateFreshCie() = runTest {
    store.writeAuthSnapshot(passwordSnapshot())
    val browserEpoch = store.accountGuard.authenticationEpoch()
    val before = store.readAuthSnapshot()!!
    store.writeAuthSnapshot(before.copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
    val sync = coordinator()
    coEvery { rest.loginWithCie(any()) } coAnswers {
      val current = store.readAuthSnapshot()!!
      store.writeAuthSnapshot(current.copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.READY)))
      store.writeAuthSnapshot(current.copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
      result()
    }

    val authenticated = sync.loginWithCie("original-pa", "", browserEpoch)

    assertEquals(AuthMethod.CIE, authenticated.authMethod)
    assertEquals(browserEpoch, store.accountGuard.authenticationEpoch())
    assertEquals(RestAuthenticationState.READY, store.authenticationState.value.rest)
  }

  @Test fun reauthenticationKeepsLocalTemplatesButRemovesPasswordMaterial() = runTest {
    store.writeAuthSnapshot(passwordSnapshot().copy(authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
    coordinator().loginWithCie("original-pa", "", store.accountGuard.authenticationEpoch())

    assertNull(store.readStoredCredentials())
    assertEquals("original-pa", store.readAuthSnapshot()!!.cie!!.pa)
    coVerify(exactly = 0) { templates.writeAllTemplates(any()) }
    coVerify(exactly = 0) { events.clearAll() }
    coVerify(exactly = 0) { scores.clearAll() }
  }

  @Test fun signedOutAdmissionCleansLegacyUnownedGlobalsBeforePublishingStudent() = runTest {
    coEvery { templates.writeAllTemplates(emptyMap()) } coAnswers { assertNull(store.readCurrentSession()) }
    coordinator().loginWithCie("original-pa", "", store.accountGuard.authenticationEpoch())
    coVerify(exactly = 1) { templates.writeAllTemplates(emptyMap()) }
    coVerify(exactly = 1) { events.clearAll() }
    coVerify(exactly = 1) { scores.clearAll() }
    assertEquals("123", store.readCurrentSession()!!.studentId)
  }

  @Test fun freshRestWithoutVerifiedNewCookiesDoesNotResurrectExpiredPortalSession() = runTest {
    store.writeAuthSnapshot(passwordSnapshot().copy(
      session = passwordSnapshot().session!!.copy(ident = "S123X"),
      portal = PortalSessionSnapshot("S123X", "123", "999", listOf(StoredPortalCookie("portal", "expired"))),
      authenticationState = AuthenticationState(rest = RestAuthenticationState.READY, portal = PortalAuthenticationState.REAUTHENTICATION_REQUIRED),
    ))

    coordinator().loginWithCie("original-pa", "", store.accountGuard.authenticationEpoch())

    assertEquals(RestAuthenticationState.READY, store.authenticationState.value.rest)
    assertEquals(PortalAuthenticationState.REAUTHENTICATION_REQUIRED, store.authenticationState.value.portal)
    coVerify(exactly = 0) { portal.importCieCookies(any(), any()) }
  }

  @Test fun anotherStudentCannotReplaceValidAccountOrClearItsDerivedData() = runTest {
    store.writeAuthSnapshot(passwordSnapshot("456"))
    val original = store.readAuthSnapshot()
    assertTrue(runCatching { coordinator().loginWithCie("original-pa", "other=cookie", store.accountGuard.authenticationEpoch()) }.isFailure)
    assertEquals(original, store.readAuthSnapshot())
    coVerify(exactly = 0) { templates.writeAllTemplates(any()) }
    coVerify(exactly = 0) { portal.importCieCookies(any(), any()) }
  }

  @Test fun durableStorageFailureDoesNotPublishCandidate() = runTest {
    val sync = coordinator()
    persistence.failCommit = true
    assertTrue(runCatching { sync.loginWithCie("original-pa", "a=b", store.accountGuard.authenticationEpoch()) }.isFailure)
    assertNull(store.readCurrentSession())
    assertEquals(RestAuthenticationState.SIGNED_OUT, store.authenticationState.value.rest)
    coVerify(exactly = 0) { portal.importCieCookies(any(), any()) }
    verify(exactly = 0) { rest.setSession(any()) }
  }

  private fun coordinator(): SchoolSyncCoordinator {
    coEvery { rest.loginWithCie(any()) } returns result()
    coEvery { rest.getProfile() } throws ClassevivaNetworkException("optional card unavailable")
    return spyk(SchoolSyncCoordinator(
      json = Json, restClient = rest, portalClient = portal, sessionStore = store,
      schoolYearStore = mockk(relaxed = true), timetableTemplateStore = templates,
      snapshotCacheDao = mockk(relaxed = true), syncMetadataDao = mockk(relaxed = true),
      gradeDao = mockk(relaxed = true), agendaDao = mockk(relaxed = true), changeHistoryDao = mockk(relaxed = true),
      absenceDao = mockk(relaxed = true), communicationDao = mockk(relaxed = true),
      materialDao = mockk(relaxed = true), documentDao = mockk(relaxed = true),
      privateAssetStore = mockk(relaxed = true), predictiveTimetableUseCase = mockk(relaxed = true),
      customEventDao = events, studentScoreDao = scores,
    )).also { coEvery { it.refreshAll(force = true) } returns SyncStatus() }
  }

  private fun result() = CieLoginResult("rest-token", "123", "S123X", "original-pa", "refresh", 999_000, StudentProfile(id = "123"))
  private fun passwordSnapshot(id: String = "123") = StoredAuthSnapshot(
    session = UserSession("old-token", id, "student", StudentProfile(id = id)),
    credentials = StoredCredentials("student", "password"),
  )
}
