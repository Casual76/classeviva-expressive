package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.GsonBuilder
import dev.antigravity.classevivaexpressive.core.datastore.CieCredentials
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SecureSessionPersistence
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CieAuthenticationNetworkTest {
  private lateinit var server: MockWebServer
  private lateinit var storage: SessionStore
  private lateinit var manager: ApiSessionManager
  private lateinit var rest: ClassevivaRestClient

  @Before fun setUp() {
    server = MockWebServer().also { it.start() }
    storage = memorySessionStore(cieSnapshot())
    val policy = RestOriginPolicy.fromBaseUrl(server.url("/rest/"))
    val headers = ClassevivaHeadersInterceptor(policy)
    val authClient = NetworkModule.provideAuthHttpClient(HttpLoggingInterceptor(), headers)
    val auth = Retrofit.Builder().baseUrl(server.url("/rest/")).client(authClient)
      .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create())).build()
      .create(ClassevivaAuthService::class.java)
    manager = ApiSessionManager(storage, auth)
    val apiClient = OkHttpClient.Builder().addInterceptor(RestAccountInterceptor(storage, policy))
      .addNetworkInterceptor(AuthTokenInterceptor(storage, policy))
      .addNetworkInterceptor(headers).authenticator(SessionAuthenticator(manager, policy)).build()
    val api = Retrofit.Builder().baseUrl(server.url("/rest/")).callFactory(ExpectedAccountCallFactory(apiClient))
      .addConverterFactory(GsonConverterFactory.create()).build().create(ClassevivaApiService::class.java)
    rest = ClassevivaRestClient(Json { ignoreUnknownKeys = true }, api, auth, manager)
  }

  @After fun tearDown() { server.shutdown() }

  @Test fun loginUsesExplicitNullAndReadsOnlyRefreshResponseHeader() = runBlocking {
    server.enqueue(ok(loginResponse()).addHeader(CieProtocol.RefreshHeader, "refresh-header"))
    val result = rest.loginWithCie("original-pa")
    val request = server.takeRequest()
    assertEquals("/rest/v1/auth/spidlogin", request.path)
    assertEquals("{\"pa\":\"original-pa\",\"rf\":null}", request.body.readUtf8())
    assertNull(request.getHeader(CieProtocol.RefreshHeader))
    assertEquals("refresh-header", result.refreshToken)
    assertEquals("1234567", result.studentId)
    assertEquals("S1234567X", result.ident)
    assertEquals("stale-token", storage.readCurrentSession()?.token)
    assertEquals("CieLoginResult([redacted])", result.toString())
  }

  @Test fun passwordReauthenticationTrustsReturnedIdentityOverDigitsInUsername() = runBlocking {
    server.enqueue(ok(loginResponse(ident = "S9876543X")))
    val candidate = rest.login("S1234567X", "password")
    assertEquals("9876543", candidate.studentId)
    assertEquals("S9876543X", candidate.ident)
    assertEquals("1234567", storage.readCurrentSession()?.studentId)
  }

  @Test fun restRequestStopsAfterOneRetryEvenWhenFreshTokenIsRejected() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(ok(loginResponse()))
    server.enqueue(MockResponse().setResponseCode(401))
    try { rest.getGrades(); fail("A second 401 must stop the request") }
    catch (_: ClassevivaNetworkException) { }
    assertEquals(3, server.requestCount)
    assertEquals("/rest/v1/students/1234567/grades", server.takeRequest().path)
    assertEquals("/rest/v1/auth/spidrf", server.takeRequest().path)
    val retry = server.takeRequest()
    assertEquals("fresh-token", retry.getHeader("Z-Auth-Token"))
  }

  @Test fun loginDoesNotTreatTokenApOrBodyRefreshAsRefreshHeader() = runBlocking {
    server.enqueue(ok(loginResponse().dropLast(1) + ",\"tokenAP\":\"wrong\",\"rf\":\"also-wrong\"}"))
    assertNull(rest.loginWithCie("pa").refreshToken)
  }

  @Test fun loginRejectsParentIdentityAndMalformedExpiryWithoutChangingAccount() = runBlocking {
    for (body in listOf(loginResponse(ident = "G1234567X"), loginResponse(expire = "invalid"))) {
      server.enqueue(ok(body))
      try { rest.loginWithCie("pa"); fail("Invalid response must not be accepted") }
      catch (_: ClassevivaNetworkException) { }
      assertEquals("stale-token", storage.readCurrentSession()?.token)
    }
  }

  @Test fun refreshPreservesOriginalPaAndRotatesHeader() {
    server.enqueue(ok(loginResponse(token = "fresh-token")).addHeader(CieProtocol.RefreshHeader, "rotated"))
    assertEquals("fresh-token", manager.refreshTokenBlocking("stale-token"))
    val request = server.takeRequest()
    assertEquals("/rest/v1/auth/spidrf", request.path)
    assertEquals("{\"pa\":\"original-pa\",\"rf\":\"refresh-1\"}", request.body.readUtf8())
    assertNull(request.getHeader(CieProtocol.RefreshHeader))
    assertEquals("rotated", storage.readAuthSnapshot()?.cie?.refreshToken)
    assertEquals("original-pa", storage.readAuthSnapshot()?.cie?.pa)
    server.enqueue(ok(loginResponse(token = "next-token")))
    assertEquals("next-token", manager.refreshTokenBlocking("fresh-token"))
    assertEquals("rotated", storage.readAuthSnapshot()?.cie?.refreshToken)
    assertEquals("next-token", rest.currentToken())
  }

  @Test fun simultaneousUnauthorizedRequestsShareOneRefresh() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        return ok(loginResponse(token = "fresh-token"))
      }
    }
    val pool = Executors.newFixedThreadPool(2)
    try {
      val first = pool.submit<String?> { manager.refreshTokenBlocking("stale-token") }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      val second = pool.submit<String?> { manager.refreshTokenBlocking("stale-token") }
      release.countDown()
      assertEquals("fresh-token", first.get(5, TimeUnit.SECONDS))
      assertEquals("fresh-token", second.get(5, TimeUnit.SECONDS))
      assertEquals(1, server.requestCount)
    } finally { release.countDown(); pool.shutdownNow() }
  }

  @Test fun refreshResponseCannotResurrectLoggedOutAccount() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
        return ok(loginResponse(token = "too-late"))
      }
    }
    val pool = Executors.newSingleThreadExecutor()
    try {
      val result = pool.submit<String?> { manager.refreshTokenBlocking("stale-token") }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      storage.clear()
      release.countDown()
      assertNull(result.get(5, TimeUnit.SECONDS))
      assertNull(storage.readCurrentSession())
    } finally { release.countDown(); pool.shutdownNow() }
  }

  @Test fun delayed401FromOldAccountNeverRetriesUsingNewAccountsToken() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
        return MockResponse().setResponseCode(401)
      }
    }
    val pool = Executors.newSingleThreadExecutor()
    try {
      val result = pool.submit { runBlocking { runCatching { rest.getGrades() } } }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      storage.clear()
      storage.writeAuthSnapshot(cieSnapshot("B-token", "S9876543X"))
      release.countDown()
      result.get(5, TimeUnit.SECONDS)
      assertEquals(1, server.requestCount)
      assertEquals("stale-token", server.takeRequest().getHeader("Z-Auth-Token"))
      assertEquals("B-token", storage.readCurrentSession()?.token)
    } finally { release.countDown(); pool.shutdownNow() }
  }

  @Test fun transientRefreshErrorsKeepSessionAndTerminalErrorsKeepOfflineIdentity() {
    for ((code, body) in listOf(429 to "{}", 500 to "{}", 503 to "{}", 422 to "{\"error\":\"PATokenNotYetExpired\"}")) {
      server.enqueue(MockResponse().setResponseCode(code).setBody(body))
      assertNull(manager.refreshTokenBlocking("stale-token"))
      assertEquals(RestAuthenticationState.READY, storage.readAuthSnapshot()?.authenticationState?.rest)
      assertEquals("stale-token", storage.readCurrentSession()?.token)
    }
    server.enqueue(MockResponse().setResponseCode(422).setBody("{\"error\":\"RFTokenNotValid\"}"))
    assertNull(manager.refreshTokenBlocking("stale-token"))
    assertEquals(RestAuthenticationState.REAUTHENTICATION_REQUIRED, storage.readAuthSnapshot()?.authenticationState?.rest)
    assertEquals("1234567", storage.readCurrentSession()?.studentId)
  }

  @Test fun authRedirectCannotForwardSecretPostBody() = runBlocking {
    val external = MockWebServer().also { it.start() }
    try {
      server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", external.url("/collect")))
      try { rest.loginWithCie("private-pa"); fail("Auth redirect must not be followed") }
      catch (_: ClassevivaNetworkException) { }
      assertEquals(0, external.requestCount)
    } finally { external.shutdown() }
  }

  @Test fun suspendedDataOperationCannotStartARetrofitRequestUnderANewAccount() = runBlocking {
    val original = storage.accountGuard.capture()
    storage.clear()
    storage.writeAuthSnapshot(cieSnapshot("B-token", "S9876543X"))
    server.enqueue(ok("""{"grades":[]}"""))
    assertTrue(runCatching { withExpectedAccount(original) { rest.getGrades() } }.isFailure)
    assertEquals(0, server.requestCount)
    // The coroutine context is restored after the failed old operation.
    assertTrue(rest.getGrades().isEmpty())
    assertEquals("B-token", server.takeRequest().getHeader("Z-Auth-Token"))
  }

  @Test fun nestedDataOperationCannotAdoptANewAccountLease() = runBlocking {
    val original = storage.accountGuard.capture()
    storage.clear()
    storage.writeAuthSnapshot(cieSnapshot("B-token", "S9876543X"))
    val replacement = storage.accountGuard.capture()
    var executed = false
    assertTrue(runCatching { withExpectedAccount(original) {
      withExpectedAccount(replacement) { executed = true }
    } }.isFailure)
    assertFalse(executed)
  }

  private fun ok(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
  private fun loginResponse(token: String = "fresh-token", ident: String = "S1234567X", expire: String = "2030-01-01T12:00:00+01:00") =
    """{"token":"$token","ident":"$ident","expire":"$expire","firstName":"Example","lastName":"Student"}"""
}

internal fun cieSnapshot(token: String = "stale-token", ident: String = "S1234567X"): StoredAuthSnapshot {
  val studentId = checkNotNull(CieProtocol.studentId(ident))
  return StoredAuthSnapshot(
    session = UserSession(token, studentId, ident, StudentProfile(studentId, "Example", "Student"), AuthMethod.CIE, ident),
    cie = CieCredentials("original-pa", "refresh-1", ident, 1_900_000_000_000),
  )
}

internal class NetworkMemorySessionPersistence : SecureSessionPersistence {
  private var payload: String? = null
  var failCommit: Boolean = false
  override fun readSnapshot(): String? = payload
  override fun readLegacySession(): String? = null
  override fun readLegacyCredentials(): StoredCredentials? = null
  override fun commit(snapshot: String?): Boolean {
    if (failCommit) return false
    payload = snapshot
    return true
  }
}

internal fun memorySessionStore(
  initial: StoredAuthSnapshot,
  persistence: NetworkMemorySessionPersistence = NetworkMemorySessionPersistence(),
): SessionStore = SessionStore(persistence).apply { writeAuthSnapshot(initial) }
