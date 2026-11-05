package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.domain.model.PortalAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CiePortalNetworkTest {
  private lateinit var server: MockWebServer
  private lateinit var storage: SessionStore
  private lateinit var persistence: NetworkMemorySessionPersistence
  private lateinit var portal: PortalClient

  @Before fun setUp() {
    server = MockWebServer().also { it.start() }
    persistence = NetworkMemorySessionPersistence()
    storage = memorySessionStore(cieSnapshot(), persistence)
    portal = client()
  }
  @After fun tearDown() { server.shutdown() }

  @Test fun initialHeaderDoesNotInventCookieAttributesOrAcceptDuplicates() {
    val origin = "https://web.spaggiari.eu/".toHttpUrl()
    val cookie = checkNotNull(parseCieCookieHeader("PHPSESSID=opaque", origin)).single()
    assertTrue(cookie.hostOnly)
    assertTrue(cookie.secure)
    assertEquals("/", cookie.path)
    assertNull(cookie.httpOnly)
    assertNull(cookie.expiresAt)
    assertFalse(cookie.persistent)
    assertNull(parseCieCookieHeader("x=one; x=two", origin))
    assertNull(parseCieCookieHeader("x=one\r\nInjected:value", origin))
    assertNull(parseCieCookieHeader("missingvalue", origin))
  }

  @Test fun identityParserSeparatesRouteIdAndRejectsAmbiguousOrParentIdentities() {
    val identity = parseCiePortalIdentity(Json.parseToJsonElement("""{"id":9123456,"ident":"S1234567X"}"""))
    assertEquals("9123456", identity?.routeId)
    assertEquals("S1234567X", identity?.ident)
    assertNull(parseCiePortalIdentity(Json.parseToJsonElement("""{"id":1234567,"ident":"G1234567X"}""")))
    assertNull(parseCiePortalIdentity(Json.parseToJsonElement("""{"id":1234567}""")))
    assertNull(parseCiePortalIdentity(Json.parseToJsonElement("""{"id":1234567,"ident":"S1234567X","user":{"ident":"S7654321X"}}""")))
  }

  @Test fun importValidatesWhoamiAndUsesPortalRoutingId() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=initial", "S1234567X"))
    val importRequest = server.takeRequest()
    assertEquals("/rest/w1/misc/whoami", importRequest.path)
    assertEquals("PHPSESSID=initial", importRequest.getHeader("Cookie"))
    assertEquals("9123456", storage.readAuthSnapshot()?.portal?.portalRouteId)
    assertEquals(PortalAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.portal)
    server.enqueue(json("""{"homeworks":[]}"""))
    portal.getHomeworksIndex("1234567")
    assertEquals("/rest/w1/students/9123456/homeworks/index", server.takeRequest().path)
  }

  @Test fun restoredCookiesAreRevalidatedBeforePortalData() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=persisted", "S1234567X"))
    server.takeRequest()
    val restoredClient = client()
    server.enqueue(whoami())
    server.enqueue(json("""{"homeworks":[]}"""))
    restoredClient.getHomeworksIndex("1234567")
    assertEquals("/rest/w1/misc/whoami", server.takeRequest().path)
    assertEquals("PHPSESSID=persisted", server.takeRequest().getHeader("Cookie"))
  }

  @Test fun mismatchedOrUnavailableWhoamiCannotEnableCandidateCookiesOrInvalidateRest() = runBlocking {
    for (response in listOf(whoami("S7654321X"), MockResponse().setResponseCode(503))) {
      server.enqueue(response)
      assertFalse(portal.importCieCookies("PHPSESSID=candidate", "S1234567X"))
      assertNull(storage.readAuthSnapshot()?.portal)
      assertEquals(RestAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.rest)
      assertEquals("stale-token", storage.readCurrentSession()?.token)
    }
  }

  @Test fun failedImportDoesNotErasePreviouslyVerifiedPortalSession() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=verified", "S1234567X"))
    val previous = storage.readAuthSnapshot()?.portal
    server.enqueue(whoami("S7654321X"))
    assertFalse(portal.importCieCookies("PHPSESSID=wrong", "S1234567X"))
    assertEquals(previous, storage.readAuthSnapshot()?.portal)
  }

  @Test fun expiredPortalCookieRequiresOnlyPortalReauthentication() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=expired-later", "S1234567X"))
    server.enqueue(MockResponse().setResponseCode(401))
    try { portal.getHomeworksIndex("1234567"); fail("Must not fall back to the REST student ID") }
    catch (_: ClassevivaPortalReauthenticationException) { }
    assertEquals(PortalAuthenticationState.REAUTHENTICATION_REQUIRED, storage.readAuthSnapshot()?.authenticationState?.portal)
    assertEquals(RestAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.rest)
    assertEquals("stale-token", storage.readCurrentSession()?.token)
  }

  @Test fun serverCookieRotationPreservesRealAttributesAndDistinctPaths() = runBlocking {
    server.enqueue(whoami().addHeader("Set-Cookie", "PHPSESSID=rotated; Path=/; HttpOnly; Max-Age=3600")
      .addHeader("Set-Cookie", "PHPSESSID=scoped; Path=/rest; Max-Age=1800"))
    assertTrue(portal.importCieCookies("PHPSESSID=initial", "S1234567X"))
    val cookies = checkNotNull(storage.readAuthSnapshot()?.portal).cookies
    assertEquals(2, cookies.size)
    assertEquals("rotated", cookies.single { it.path == "/" }.value)
    assertEquals(true, cookies.single { it.path == "/" }.httpOnly)
    assertTrue(cookies.all { it.persistent && it.expiresAt != null })
    assertEquals("scoped", cookies.single { it.path == "/rest" }.value)
  }

  @Test fun failedInitialCookieCommitNeverActivatesCandidatesInMemory() = runBlocking {
    persistence.failCommit = true
    server.enqueue(whoami())
    assertFalse(portal.importCieCookies("PHPSESSID=uncommitted", "S1234567X"))
    assertNull(storage.readAuthSnapshot()?.portal)
    assertTrue(portal.getSessionCookies().isEmpty())
    assertEquals(RestAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.rest)
    persistence.failCommit = false
    try { portal.getHomeworksIndex("1234567"); fail("Uncommitted candidates must not be used") }
    catch (_: ClassevivaPortalReauthenticationException) { }
    assertEquals(1, server.requestCount)
  }

  @Test fun failedCookieRotationCommitCannotBeUsedByTheNextRequest() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=durable", "S1234567X"))
    server.takeRequest()
    val durable = storage.readAuthSnapshot()?.portal
    persistence.failCommit = true
    server.enqueue(json("""{"homeworks":[]}""").addHeader("Set-Cookie", "PHPSESSID=uncommitted-rotation; Path=/"))
    try { portal.getHomeworksIndex("1234567"); fail("A failed durable rotation must surface") }
    catch (_: ClassevivaNetworkException) { }
    assertEquals(durable, storage.readAuthSnapshot()?.portal)
    assertTrue(portal.getSessionCookies().isEmpty())
    assertEquals("PHPSESSID=durable", server.takeRequest().getHeader("Cookie"))
    persistence.failCommit = false
    server.enqueue(whoami())
    server.enqueue(json("""{"homeworks":[]}"""))
    portal.getHomeworksIndex("1234567")
    assertEquals("PHPSESSID=durable", server.takeRequest().getHeader("Cookie"))
    assertEquals("PHPSESSID=durable", server.takeRequest().getHeader("Cookie"))
  }

  @Test fun lateRejectedOperationCannotInvalidateNewAccountsPortal() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=account-A", "S1234567X"))
    server.takeRequest()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.path?.contains("homeworks") == true && request.getHeader("Cookie") == "PHPSESSID=account-A") {
          entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
          return MockResponse().setResponseCode(401)
        }
        return whoami("S7654321X")
      }
    }
    val pool = Executors.newSingleThreadExecutor()
    try {
      val old = pool.submit<Boolean> { runBlocking { runCatching { portal.getHomeworksIndex("1234567") }.isFailure } }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      storage.clear()
      portal.clearSession()
      storage.writeAuthSnapshot(cieSnapshot("B-token", "S7654321X"))
      assertTrue(portal.importCieCookies("PHPSESSID=account-B", "S7654321X"))
      val newPortal = storage.readAuthSnapshot()?.portal
      release.countDown()
      assertTrue(old.get(5, TimeUnit.SECONDS))
      assertEquals(newPortal, storage.readAuthSnapshot()?.portal)
      assertEquals(PortalAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.portal)
      assertEquals("account-B", portal.getSessionCookies().single().value)
      assertEquals(3, server.requestCount)
    } finally { release.countDown(); pool.shutdownNow() }
  }

  @Test fun lateActionHtmlCannotSubmitOldUsersTextWithNewAccountsCookies() = runBlocking {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=account-A", "S1234567X"))
    server.takeRequest()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.path == "/action-page") {
          entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
          return MockResponse().setBody("<form action='/submit'><textarea name='reply'></textarea></form>")
        }
        return whoami("S7654321X")
      }
    }
    val pool = Executors.newSingleThreadExecutor()
    try {
      val old = pool.submit<Boolean> { runBlocking {
        runCatching { portal.submitPortalAction(server.url("/action-page").toString(), listOf("reply"), "private-text-A") }.isFailure
      } }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      storage.clear()
      portal.clearSession()
      storage.writeAuthSnapshot(cieSnapshot("B-token", "S7654321X"))
      assertTrue(portal.importCieCookies("PHPSESSID=account-B", "S7654321X"))
      release.countDown()
      assertTrue(old.get(5, TimeUnit.SECONDS))
      assertEquals(3, server.requestCount)
      repeat(2) { assertEquals("GET", server.takeRequest().method) }
      assertEquals("account-B", portal.getSessionCookies().single().value)
      assertEquals("B-token", storage.readCurrentSession()?.token)
    } finally { release.countDown(); pool.shutdownNow() }
  }

  @Test fun lateRejectionCannotInvalidateNewPortalForTheSameStudent() = runBlocking {
    assertPreviousPortalOperationStopsAfterReplacement(action = false)
  }

  @Test fun lateFormCannotSubmitAcrossPortalReplacementForTheSameStudent() = runBlocking {
    assertPreviousPortalOperationStopsAfterReplacement(action = true)
  }

  private suspend fun assertPreviousPortalOperationStopsAfterReplacement(action: Boolean) {
    server.enqueue(whoami())
    assertTrue(portal.importCieCookies("PHPSESSID=session-A", "S1234567X"))
    server.takeRequest()
    val lease = storage.accountGuard.capture()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.getHeader("Cookie") == "PHPSESSID=session-A") {
          entered.countDown()
          check(release.await(5, TimeUnit.SECONDS))
          return if (action) MockResponse().setBody("<form action='/submit'><textarea name='reply'></textarea></form>")
            else MockResponse().setResponseCode(401)
        }
        return whoami()
      }
    }
    val pool = Executors.newSingleThreadExecutor()
    try {
      val old = pool.submit<Boolean> { runBlocking {
        runCatching {
          if (action) portal.submitPortalAction(server.url("/action-page").toString(), listOf("reply"), "old-private-text")
          else portal.getHomeworksIndex("1234567")
        }.isFailure
      } }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      assertTrue(portal.importCieCookies("PHPSESSID=session-B", "S1234567X"))
      assertEquals(lease, storage.accountGuard.capture())
      val replacement = storage.readAuthSnapshot()?.portal
      release.countDown()
      assertTrue(old.get(5, TimeUnit.SECONDS))
      assertEquals(replacement, storage.readAuthSnapshot()?.portal)
      assertEquals(PortalAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.portal)
      assertEquals("session-B", portal.getSessionCookies().single().value)
      assertEquals(3, server.requestCount)
      repeat(2) { assertEquals("GET", server.takeRequest().method) }
    } finally {
      release.countDown()
      pool.shutdownNow()
    }
  }

  @Test fun rejectedSavedCookieVerificationCannotEraseAConcurrentRotation() = runBlocking {
    assertSavedVerificationCannotReplaceRotation(rejected = true)
  }

  @Test fun successfulSavedCookieVerificationCannotOverwriteAConcurrentRotation() = runBlocking {
    assertSavedVerificationCannotReplaceRotation(rejected = false)
  }

  private suspend fun assertSavedVerificationCannotReplaceRotation(rejected: Boolean) {
    val clock = AtomicLong(System.currentTimeMillis())
    portal = client(clock::get)
    server.enqueue(whoami().addHeader("Set-Cookie", "PHPSESSID=session-A; Path=/; Max-Age=600"))
    assertTrue(portal.importCieCookies("PHPSESSID=session-A", "S1234567X"))
    server.takeRequest()
    val ordinaryEntered = CountDownLatch(1)
    val verificationEntered = CountDownLatch(1)
    val releaseOrdinary = CountDownLatch(1)
    val releaseVerification = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.path?.contains("homeworks") == true) {
          ordinaryEntered.countDown()
          check(releaseOrdinary.await(5, TimeUnit.SECONDS))
          return json("""{"homeworks":[]}""")
            .addHeader("Set-Cookie", "PHPSESSID=rotated-A; Path=/; Max-Age=3600")
        }
        verificationEntered.countDown()
        check(releaseVerification.await(5, TimeUnit.SECONDS))
        return if (rejected) MockResponse().setResponseCode(401) else whoami()
      }
    }
    val pool = Executors.newFixedThreadPool(2)
    try {
      val ordinary = pool.submit { runBlocking { portal.getHomeworksIndex("1234567") } }
      assertTrue(ordinaryEntered.await(5, TimeUnit.SECONDS))
      // Expire the saved cookie deterministically while the first request is already in flight.
      clock.addAndGet(TimeUnit.MINUTES.toMillis(20))
      val verification = pool.submit<Boolean> { runBlocking {
        runCatching { portal.getHomeworksIndex("1234567") }.isFailure
      } }
      assertTrue(verificationEntered.await(5, TimeUnit.SECONDS))
      releaseOrdinary.countDown()
      ordinary.get(5, TimeUnit.SECONDS)
      val rotated = storage.readAuthSnapshot()?.portal
      assertEquals("rotated-A", rotated?.cookies?.single()?.value)
      releaseVerification.countDown()
      assertTrue(verification.get(5, TimeUnit.SECONDS))
      assertEquals(rotated, storage.readAuthSnapshot()?.portal)
      assertEquals("rotated-A", portal.getSessionCookies().single().value)
      assertEquals(PortalAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.portal)
      assertEquals(RestAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.rest)
      assertEquals(3, server.requestCount)
    } finally {
      releaseOrdinary.countDown()
      releaseVerification.countDown()
      pool.shutdownNow()
    }
  }

  @Test fun suspendedDataOperationCannotStartPortalAuthenticationUnderANewAccount() = runBlocking {
    val original = storage.accountGuard.capture()
    storage.clear()
    storage.writeAuthSnapshot(cieSnapshot("B-token", "S7654321X"))
    server.enqueue(whoami("S7654321X"))
    assertTrue(runCatching { withExpectedAccount(original) {
      portal.importCieCookies("PHPSESSID=account-B", "S7654321X")
    } }.isFailure)
    assertEquals(0, server.requestCount)
    assertTrue(portal.importCieCookies("PHPSESSID=account-B", "S7654321X"))
    assertEquals("account-B", portal.getSessionCookies().single().value)
    assertEquals(1, server.requestCount)
  }

  private fun client(clock: () -> Long = System::currentTimeMillis) = PortalClient(
    storage, HttpLoggingInterceptor(), server.url("/home/app/default/login.php").toString(),
    "https://web.spaggiari.eu/".toHttpUrl(), clock,
  )
  private fun whoami(ident: String = "S1234567X") = json("""{"id":9123456,"ident":"$ident"}""")
  private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
