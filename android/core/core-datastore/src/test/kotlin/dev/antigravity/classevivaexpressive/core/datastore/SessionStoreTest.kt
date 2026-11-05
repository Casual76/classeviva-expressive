package dev.antigravity.classevivaexpressive.core.datastore

import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.PortalAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {
  private val json = Json { encodeDefaults = true }

  @Test fun migratesLegacySessionAndSeparateCredentialsInOneDurableCommit() {
    val persistence = MemoryPersistence(
      legacySession = json.encodeToString(session()),
      legacyCredentials = StoredCredentials("student", "legacy-secret"),
    )
    val store = SessionStore(persistence)

    assertEquals(session(), store.readCurrentSession())
    assertEquals(StoredCredentials("student", "legacy-secret"), store.readStoredCredentials())
    assertNull(persistence.legacySession)
    assertNull(persistence.legacyCredentials)
    assertEquals(1, persistence.commits)
    assertEquals(store.readAuthSnapshot(), SessionStore(persistence).readAuthSnapshot())
  }

  @Test fun migratesPasswordEmbeddedInOldSessionWithoutKeepingItInUserSession() {
    val old = """{"token":"old-token","studentId":"123","username":"student","password":"legacy-secret","profile":{}}"""
    val persistence = MemoryPersistence(legacySession = old)
    val store = SessionStore(persistence)

    assertEquals(AuthMethod.PASSWORD, store.readCurrentSession()?.authMethod)
    assertEquals(StoredCredentials("student", "legacy-secret"), store.readStoredCredentials())
    assertNull(store.readAuthSnapshot()?.cie)
    assertEquals(RestAuthenticationState.READY, store.authenticationState.value.rest)
  }

  @Test fun failedMigrationPreservesLegacyAndCanBeRetriedOnNextOpen() {
    val persistence = MemoryPersistence(legacySession = json.encodeToString(session()), failWrites = true)
    val failed = SessionStore(persistence)

    assertNull(failed.readCurrentSession())
    assertNotNull(persistence.legacySession)
    assertNull(persistence.snapshot)
    assertEquals(RestAuthenticationState.STORAGE_UNAVAILABLE, failed.authenticationState.value.rest)
    persistence.failWrites = false
    assertEquals(session(), SessionStore(persistence).readCurrentSession())
  }

  @Test fun corruptedOrUnavailableArchiveIsNotDeletedOrReplaced() {
    val persistence = MemoryPersistence(snapshot = "corrupted-secret-material")
    val store = SessionStore(persistence)

    assertEquals(RestAuthenticationState.STORAGE_UNAVAILABLE, store.authenticationState.value.rest)
    assertEquals("corrupted-secret-material", persistence.snapshot)
    assertEquals(0, persistence.commits)

    persistence.failReads = true
    val unopened = SessionStore(persistence)
    assertEquals(RestAuthenticationState.STORAGE_UNAVAILABLE, unopened.authenticationState.value.rest)
    assertEquals(0, persistence.commits)
  }

  @Test fun failedWriteDoesNotPublishUndurableTokenOrReplacePreviousSnapshot() {
    val persistence = MemoryPersistence()
    val store = SessionStore(persistence)
    val initial = passwordSnapshot()
    store.writeAuthSnapshot(initial)
    val oldDisk = persistence.snapshot
    val oldLease = store.accountGuard.capture()
    persistence.failWrites = true

    val error = runCatching { store.writeAuthSnapshot(initial.copy(session = session(token = "new-secret"))) }.exceptionOrNull()

    assertTrue(error is SecureSessionStorageException)
    assertFalse(error.toString().contains("new-secret"))
    assertEquals(initial, store.readAuthSnapshot())
    assertEquals(initial.session, store.session.value)
    assertEquals(oldDisk, persistence.snapshot)
    assertTrue(store.accountGuard.isCurrent(oldLease))
  }

  @Test fun cieRotationPersistsEveryFieldAndRemovesPasswordAcrossRestart() {
    val persistence = MemoryPersistence()
    val store = SessionStore(persistence)
    store.writeAuthSnapshot(passwordSnapshot())
    val cie = cieSnapshot(refresh = "refresh-one")
    store.writeAuthSnapshot(cie)
    val rotated = cie.copy(cie = cie.cie!!.copy(refreshToken = "refresh-two", expiresAt = 999_999))
    assertTrue(store.compareAndSetAuthSnapshot(cie, rotated, store.accountGuard.capture()))

    val restarted = SessionStore(persistence)
    assertEquals(rotated, restarted.readAuthSnapshot())
    assertNull(restarted.readStoredCredentials())
    assertEquals("refresh-two", restarted.readAuthSnapshot()?.cie?.refreshToken)
    assertNull(persistence.legacyCredentials)
  }

  @Test fun malformedMixedSecretsAndCrossAccountCookiesNeverReachDisk() {
    val persistence = MemoryPersistence()
    val store = SessionStore(persistence)
    val cie = cieSnapshot()
    assertTrue(runCatching { store.writeAuthSnapshot(cie.copy(credentials = StoredCredentials("student", "secret"))) }.isFailure)
    assertTrue(runCatching { store.writeAuthSnapshot(cie.copy(portal = cie.portal!!.copy(studentId = "456"))) }.isFailure)
    assertTrue(runCatching {
      store.writeAuthSnapshot(cie.copy(portal = cie.portal!!.copy(cookies = listOf(StoredPortalCookie("sid", "secret", domain = "spaggiari.eu.evil.test")))))
    }.isFailure)
    assertEquals(0, persistence.commits)
    assertNull(store.readCurrentSession())
  }

  @Test fun cookieMetadataIsPreservedAndSecretsAreRedactedInDiagnostics() {
    val persistence = MemoryPersistence()
    val store = SessionStore(persistence)
    val cookie = StoredPortalCookie("sid", "cookie-secret", domain = ".spaggiari.eu", path = "/home", secure = false,
      httpOnly = true, hostOnly = false, expiresAt = 1_900_000_000_000, persistent = true)
    val value = cieSnapshot().let { it.copy(portal = it.portal!!.copy(cookies = listOf(cookie))) }
    store.writeAuthSnapshot(value)

    assertEquals(cookie, SessionStore(persistence).readAuthSnapshot()?.portal?.cookies?.single())
    listOf(value, value.session, value.cie, value.portal, cookie, StoredCredentials("username", "password")).forEach {
      assertFalse(it.toString().contains("cookie-secret"))
      assertFalse(it.toString().contains("pa-secret"))
      assertFalse(it.toString().contains("rest-secret"))
      assertFalse(it.toString().contains("password"))
    }
  }

  @Test fun sameIdentityRefreshKeepsLeaseButLogoutAndReentryInvalidateIt() {
    val store = SessionStore(MemoryPersistence())
    val original = passwordSnapshot()
    store.writeAuthSnapshot(original)
    val lease = store.accountGuard.capture()
    assertTrue(store.compareAndSetAuthSnapshot(original, original.copy(session = session(token = "rotated")), lease))
    assertTrue(store.accountGuard.isCurrent(lease))

    store.clear()
    store.writeAuthSnapshot(original)
    assertFalse(store.accountGuard.isCurrent(lease))
    assertFalse(store.compareAndSetAuthSnapshot(original, original.copy(session = session(token = "late")), lease))
    assertEquals(original, store.readAuthSnapshot())
  }

  @Test fun acceptingSnapshotDoesNotRetainCallerOwnedCookieList() {
    val store = SessionStore(MemoryPersistence())
    val cookies = mutableListOf(StoredPortalCookie("sid", "original"))
    val candidate = cieSnapshot().let { it.copy(portal = it.portal!!.copy(cookies = cookies)) }
    store.writeAuthSnapshot(candidate)
    cookies.clear()
    assertEquals("original", store.readAuthSnapshot()?.portal?.cookies?.single()?.value)
  }

  @Test fun terminalRestRejectionKeepsOfflineAccountAndInvalidatesInFlightEffects() {
    val store = SessionStore(MemoryPersistence())
    val original = cieSnapshot()
    store.writeAuthSnapshot(original)
    val lease = store.accountGuard.capture()
    val rejected = original.copy(cie = null, authenticationState = original.authenticationState.copy(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED))
    assertTrue(store.compareAndSetAuthSnapshot(original, rejected, lease))

    assertEquals(original.session, store.session.value)
    assertFalse(store.accountGuard.isCurrent(lease))
    assertEquals(RestAuthenticationState.REAUTHENTICATION_REQUIRED, store.authenticationState.value.rest)
    assertNull(store.readAuthSnapshot()?.cie)
  }

  @Test fun portalExpiryDoesNotInvalidateRestLease() {
    val store = SessionStore(MemoryPersistence())
    val original = cieSnapshot()
    store.writeAuthSnapshot(original)
    val lease = store.accountGuard.capture()
    store.writeAuthSnapshot(original.copy(portal = null,
      authenticationState = original.authenticationState.copy(portal = PortalAuthenticationState.REAUTHENTICATION_REQUIRED)))
    assertTrue(store.accountGuard.isCurrent(lease))
    assertEquals(RestAuthenticationState.READY, store.authenticationState.value.rest)
  }

  @Test fun accountCommitAndLogoutCleanupAreOneBarrier() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val lease = store.accountGuard.capture()
    val started = CompletableDeferred<Unit>()
    val releaseCommit = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()
    val oldCommit = launch {
      store.accountGuard.withCurrentAccount(lease) {
        started.complete(Unit)
        releaseCommit.await()
        events += "old-commit"
      }
    }
    started.await()
    val logout = launch { store.clearAndRun { events += "cleanup" } }
    runCurrent()
    assertFalse(logout.isCompleted)
    releaseCommit.complete(Unit)
    oldCommit.join()
    logout.join()
    assertEquals(listOf("old-commit", "cleanup"), events)
    assertNull(store.readCurrentSession())
    var staleEffect = false
    assertNull(store.accountGuard.withCurrentAccount(lease) { staleEffect = true })
    assertFalse(staleEffect)
  }

  @Test fun noNewAccountCanBeCommittedUntilLogoutCleanupFinishes() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val enteredCleanup = CompletableDeferred<Unit>()
    val finishCleanup = CompletableDeferred<Unit>()
    val logout = launch { store.clearAndRun { enteredCleanup.complete(Unit); finishCleanup.await() } }
    enteredCleanup.await()
    val attemptingCommit = CompletableDeferred<Unit>()
    val committed = CompletableDeferred<Unit>()
    val newAccount = passwordSnapshot().copy(session = session().copy(studentId = "456"))
    val newLogin = async(Dispatchers.IO) {
      attemptingCommit.complete(Unit)
      store.writeAuthSnapshot(newAccount)
      committed.complete(Unit)
    }
    attemptingCommit.await()
    runCurrent()
    assertFalse(committed.isCompleted)
    assertNull(store.readCurrentSession())
    finishCleanup.complete(Unit)
    logout.join()
    newLogin.await()
    assertEquals("456", store.readCurrentSession()?.studentId)
  }

  @Test fun providerAttemptSurvivesCredentialExpiryWithoutRevivingOldDataOperations() {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val before = store.captureAuthSnapshot()
    store.writeAuthSnapshot(passwordSnapshot().copy(
      authenticationState = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED),
    ))
    val expired = store.captureAuthSnapshot()
    assertEquals(before.authenticationEpoch, expired.authenticationEpoch)
    assertNotEquals(before.lease, expired.lease)
    assertFalse(store.accountGuard.isCurrent(before.lease, requireReady = false))
    store.writeAuthSnapshot(passwordSnapshot().copy(session = session("reauthenticated")))
    assertEquals(before.authenticationEpoch, store.accountGuard.authenticationEpoch())
    assertFalse(store.accountGuard.isCurrent(before.lease))
    assertTrue(store.accountGuard.isCurrent(store.captureAuthSnapshot().lease))
  }

  @Test fun logoutAndReentryOfTheSameIdentityInvalidatePendingProviderAttempts() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val before = store.captureAuthSnapshot()
    store.clearAndRunWithPrevious(before.authenticationEpoch) { previous ->
      assertEquals("123", previous?.session?.studentId)
      assertNull(store.readCurrentSession())
    }
    store.writeAuthSnapshot(passwordSnapshot())
    assertNotEquals(before.authenticationEpoch, store.captureAuthSnapshot().authenticationEpoch)
    assertFalse(store.accountGuard.isCurrent(before.lease))
  }

  @Test fun staleLogoutCannotClearOrCleanANewerAccount() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val originalEpoch = store.accountGuard.authenticationEpoch()
    store.clear()
    store.writeAuthSnapshot(passwordSnapshot().copy(session = session().copy(studentId = "456")))
    var cleaned = false
    try {
      store.clearAndRunWithPrevious(originalEpoch) { cleaned = true }
      fail("An old logout must not target the replacement account")
    } catch (_: IllegalStateException) { }
    assertFalse(cleaned)
    assertEquals("456", store.readCurrentSession()?.studentId)
  }

  @Test fun captureDuringBlockedDurableCommitReturnsPreviousCoherentPublicationWithoutWaiting() = runTest {
    val persistence = MemoryPersistence()
    val store = SessionStore(persistence)
    store.writeAuthSnapshot(passwordSnapshot())
    val original = store.captureAuthSnapshot()
    val oldDisk = persistence.snapshot
    val entered = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)
    persistence.beforeCommit = { entered.complete(Unit); release.await() }
    val candidate = passwordSnapshot().copy(session = session("candidate").copy(studentId = "456"))
    val writer = async(Dispatchers.IO) { store.writeAuthSnapshot(candidate) }
    try {
      entered.await()
      val during = captureWithoutWaitingForGate(store)
      assertEquals(original, during)
      assertEquals(during.snapshot!!.session!!.studentId, during.lease.studentId)
      assertEquals(original.snapshot!!.session, store.session.value)
      assertEquals(oldDisk, persistence.snapshot)
      release.countDown()
      writer.await()
      val after = store.captureAuthSnapshot()
      assertEquals(candidate, after.snapshot)
      assertEquals("456", after.lease.studentId)
      assertNotEquals(original.authenticationEpoch, after.authenticationEpoch)
      assertNotEquals(original.lease, after.lease)
    } finally { release.countDown(); persistence.beforeCommit = null }
  }

  @Test fun blockedFailedCommitNeverPublishesCandidateOrAdvancesAnyCapturedEpoch() = runTest {
    val persistence = MemoryPersistence()
    val store = SessionStore(persistence)
    store.writeAuthSnapshot(passwordSnapshot())
    val original = store.captureAuthSnapshot()
    val oldDisk = persistence.snapshot
    val entered = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)
    persistence.beforeCommit = { entered.complete(Unit); release.await() }
    persistence.failWrites = true
    val writer = async(Dispatchers.IO) {
      runCatching { store.writeAuthSnapshot(passwordSnapshot().copy(session = session("undurable-candidate"))) }
    }
    try {
      entered.await()
      assertEquals(original, captureWithoutWaitingForGate(store))
      release.countDown()
      assertTrue(writer.await().exceptionOrNull() is SecureSessionStorageException)
      assertEquals(original, store.captureAuthSnapshot())
      assertEquals(original.snapshot!!.session, store.session.value)
      assertEquals(oldDisk, persistence.snapshot)
      assertTrue(store.accountGuard.isCurrent(original.lease))
    } finally { release.countDown(); persistence.beforeCommit = null }
  }

  @Test fun captureDuringLogoutCleanupReturnsSignedOutPublicationAndReentryNeverMixesLeases() = runTest {
    val store = SessionStore(MemoryPersistence())
    store.writeAuthSnapshot(passwordSnapshot())
    val original = store.captureAuthSnapshot()
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val logout = launch(Dispatchers.IO) {
      store.clearAndRun { entered.complete(Unit); release.await() }
    }
    try {
      entered.await()
      val signedOut = captureWithoutWaitingForGate(store)
      assertNull(signedOut.snapshot)
      assertNull(signedOut.lease.studentId)
      assertNotEquals(original.authenticationEpoch, signedOut.authenticationEpoch)
      assertFalse(store.accountGuard.isCurrent(original.lease, requireReady = false))
      release.complete(Unit)
      logout.join()
      store.writeAuthSnapshot(passwordSnapshot())
      val reentered = store.captureAuthSnapshot()
      assertEquals("123", reentered.snapshot!!.session!!.studentId)
      assertEquals("123", reentered.lease.studentId)
      assertNotEquals(original.authenticationEpoch, reentered.authenticationEpoch)
      assertNotEquals(signedOut.authenticationEpoch, reentered.authenticationEpoch)
      store.writeAuthSnapshot(passwordSnapshot().copy(session = session("rotated-token")))
      val rotated = store.captureAuthSnapshot()
      assertEquals("rotated-token", rotated.snapshot!!.session!!.token)
      assertEquals(reentered.lease, rotated.lease)
      assertEquals(reentered.authenticationEpoch, rotated.authenticationEpoch)
    } finally { release.complete(Unit) }
  }

  private fun captureWithoutWaitingForGate(store: SessionStore): AuthSnapshotLease {
    val reader = Executors.newSingleThreadExecutor { Thread(it, "cie-snapshot-test-reader") }
    return try { reader.submit<AuthSnapshotLease> { store.captureAuthSnapshot() }.get(5, TimeUnit.SECONDS) }
    finally { reader.shutdownNow() }
  }

  private fun session(token: String = "rest-secret") = UserSession(token, "123", "student", StudentProfile())
  private fun passwordSnapshot() = StoredAuthSnapshot(session = session(), credentials = StoredCredentials("student", "password-secret"))
  private fun cieSnapshot(refresh: String = "refresh-secret"): StoredAuthSnapshot {
    val session = session().copy(authMethod = AuthMethod.CIE, ident = "S123X")
    return StoredAuthSnapshot(
      session = session,
      cie = CieCredentials("pa-secret", refresh, "S123X", 999_000),
      portal = PortalSessionSnapshot("S123X", "123", "987", listOf(StoredPortalCookie("sid", "cookie-secret"))),
    )
  }

  private class MemoryPersistence(
    var snapshot: String? = null,
    var legacySession: String? = null,
    var legacyCredentials: StoredCredentials? = null,
    var failWrites: Boolean = false,
    var failReads: Boolean = false,
  ) : SecureSessionPersistence {
    var commits = 0
    @Volatile var beforeCommit: (() -> Unit)? = null
    override fun readSnapshot(): String? {
      if (failReads) throw IllegalStateException("crypto")
      return snapshot
    }
    override fun readLegacySession(): String? = legacySession
    override fun readLegacyCredentials(): StoredCredentials? = legacyCredentials
    override fun commit(snapshot: String?): Boolean {
      beforeCommit?.invoke()
      if (failWrites) return false
      this.snapshot = snapshot
      legacySession = null
      legacyCredentials = null
      commits++
      return true
    }
  }
}
