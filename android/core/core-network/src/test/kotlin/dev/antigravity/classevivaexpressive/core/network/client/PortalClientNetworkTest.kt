package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PortalClientNetworkTest {
  private lateinit var server: MockWebServer
  private lateinit var sessionStorage: TestSessionStorage
  private lateinit var portalClient: PortalClient

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    sessionStorage = TestSessionStorage().apply {
      writeCredentials(username = "student-demo", password = "password-demo")
    }
    portalClient = PortalClient(
      sessionStorage = sessionStorage,
      loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
      },
      portalLoginUrl = server.url("/login").toString(),
    )
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun openSchoolReport_logsInCarriesCookieAndPreservesServerMetadata() = runBlocking {
    enqueuePortalLogin()
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf; charset=binary")
        .addHeader("Content-Disposition", "attachment; filename*=UTF-8''Pagella%20Demo.pdf")
        .setBody("sanitized-pdf-placeholder"),
    )

    val download = portalClient.openSchoolReport(
      viewUrl = server.url("/reports/view?id=demo").toString(),
      confirmUrl = server.url("/reports/confirm?id=demo").toString(),
    )
    download.use {
      assertEquals("application/pdf", it.mimeType)
      assertEquals("Pagella Demo.pdf", it.fileName)
      assertEquals("sanitized-pdf-placeholder", it.byteStream().bufferedReader().readText())
    }

    val loginRequest = server.takeRequest()
    val reportRequest = server.takeRequest()
    assertEquals("POST", loginRequest.method)
    assertEquals("/auth-p7/app/default/AuthApi4.php?a=aLoginPwd", loginRequest.path)
    assertEquals("/reports/view?id=demo", reportRequest.path)
    assertTrue(reportRequest.getHeader("Cookie").orEmpty().contains("PHPSESSID=session-demo"))
  }

  @Test
  fun openSchoolReport_usesConfirmUrlWhenViewUrlIsMissing() = runBlocking {
    enqueuePortalLogin()
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf")
        .setBody("confirm-pdf"),
    )

    portalClient.openSchoolReport(
      viewUrl = null,
      confirmUrl = server.url("/reports/confirm?id=demo").toString(),
    ).use { download ->
      assertEquals("application/pdf", download.mimeType)
      assertEquals("confirm", download.fileName)
      assertEquals("confirm-pdf", download.byteStream().bufferedReader().readText())
    }

    server.takeRequest()
    assertEquals("/reports/confirm?id=demo", server.takeRequest().path)
  }

  @Test
  fun openSchoolReport_rejectsHtmlAssetAndFallsBackFromViewToConfirm() = runBlocking {
    enqueuePortalLogin()
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<html><body>Pagina intermedia</body></html>"),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf")
        .setBody("confirmed-pdf"),
    )

    portalClient.openSchoolReport(
      viewUrl = server.url("/reports/view?id=demo").toString(),
      confirmUrl = server.url("/reports/confirm?id=demo").toString(),
    ).use { download ->
      assertEquals("application/pdf", download.mimeType)
      assertEquals("confirmed-pdf", download.byteStream().bufferedReader().readText())
    }

    server.takeRequest()
    assertEquals("/reports/view?id=demo", server.takeRequest().path)
    assertEquals("/reports/confirm?id=demo", server.takeRequest().path)
  }

  @Test
  fun openSchoolReport_doesNotFollowRedirectAndUsesConfirmWithoutContactingRedirectOrigin() = runBlocking {
    val hostileServer = MockWebServer().apply { start() }
    try {
      enqueuePortalLogin()
      server.enqueue(
        MockResponse()
          .setResponseCode(302)
          .addHeader("Location", hostileServer.url("/login")),
      )
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Content-Type", "application/pdf")
          .setBody("safe-confirm-pdf"),
      )

      portalClient.openSchoolReport(
        viewUrl = server.url("/reports/view?id=demo").toString(),
        confirmUrl = server.url("/reports/confirm?id=demo").toString(),
      ).use { download ->
        assertEquals("safe-confirm-pdf", download.byteStream().bufferedReader().readText())
      }

      assertEquals(0, hostileServer.requestCount)
      server.takeRequest()
      assertEquals("/reports/view?id=demo", server.takeRequest().path)
      assertEquals("/reports/confirm?id=demo", server.takeRequest().path)
    } finally {
      hostileServer.shutdown()
    }
  }

  @Test
  fun openSchoolReport_skipsCrossOriginViewAndUsesSameOriginConfirm() = runBlocking {
    val hostileServer = MockWebServer().apply { start() }
    try {
      enqueuePortalLogin()
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Content-Type", "application/pdf")
          .setBody("same-origin-confirm"),
      )

      portalClient.openSchoolReport(
        viewUrl = hostileServer.url("/reports/view?id=demo").toString(),
        confirmUrl = server.url("/reports/confirm?id=demo").toString(),
      ).use { download ->
        assertEquals("same-origin-confirm", download.byteStream().bufferedReader().readText())
      }

      assertEquals(0, hostileServer.requestCount)
      server.takeRequest()
      assertEquals("/reports/confirm?id=demo", server.takeRequest().path)
    } finally {
      hostileServer.shutdown()
    }
  }

  @Test
  fun openSchoolReport_refreshesRejectedSessionOnceBeforeRetryingAsset() = runBlocking {
    enqueuePortalLogin(cookieValue = "session-old")
    server.enqueue(
      MockResponse()
        .setResponseCode(302)
        .addHeader("Location", server.url("/login")),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(302)
        .addHeader("Location", server.url("/login")),
    )
    enqueuePortalLogin(cookieValue = "session-fresh")
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf")
        .setBody("fresh-session-pdf"),
    )

    portalClient.openSchoolReport(
      viewUrl = server.url("/reports/view?id=demo").toString(),
      confirmUrl = server.url("/reports/confirm?id=demo").toString(),
    ).use { download ->
      assertEquals("fresh-session-pdf", download.byteStream().bufferedReader().readText())
    }

    val requests = List(5) { server.takeRequest() }
    assertEquals(
      listOf(PortalLoginApiPath, "/reports/view?id=demo", "/reports/confirm?id=demo", PortalLoginApiPath, "/reports/view?id=demo"),
      requests.map { it.path },
    )
    assertTrue(requests.last().getHeader("Cookie").orEmpty().contains("PHPSESSID=session-fresh"))
  }

  @Test
  fun openSchoolReport_neverReturnsHtmlWhenBothCandidatesAreInvalid() = runBlocking {
    enqueuePortalLogin()
    repeat(2) {
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Content-Type", "application/octet-stream")
          .setBody("<!doctype html><html><body>Contenuto non disponibile</body></html>"),
      )
    }

    try {
      portalClient.openSchoolReport(
        viewUrl = server.url("/reports/view?id=demo").toString(),
        confirmUrl = server.url("/reports/confirm?id=demo").toString(),
      )
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertTrue(exception.message.orEmpty().contains("HTML"))
    }
  }

  @Test
  fun portalLogin_usesAuthApiWithoutTouchingTheLoginPage() = runBlocking {
    // La pagina di login rimanda all'accesso SPID su un altro dominio: si entra da AuthApi.
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("Set-Cookie", "PHPSESSID=api-session; Path=/; HttpOnly")
        .setBody("""{"data":{"auth":{"loggedIn":true,"verified":true}}}"""),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf")
        .setBody("pdf-after-api-login"),
    )

    portalClient.openSchoolReport(
      viewUrl = server.url("/reports/view?id=demo").toString(),
      confirmUrl = null,
    ).use { download ->
      assertEquals("pdf-after-api-login", download.byteStream().bufferedReader().readText())
    }

    val apiRequest = server.takeRequest()
    assertEquals("POST", apiRequest.method)
    assertEquals(PortalLoginApiPath, apiRequest.path)
    val form = apiRequest.body.readUtf8()
    assertTrue(form.contains("uid=student-demo"))
    assertTrue(form.contains("pwd=password-demo"))
    val reportRequest = server.takeRequest()
    assertEquals("/reports/view?id=demo", reportRequest.path)
    assertTrue(reportRequest.getHeader("Cookie").orEmpty().contains("PHPSESSID=api-session"))
  }

  @Test
  fun portalLogin_fallsBackToLoginFormWhenAuthApiIsMissing() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(404))
    enqueueLoginForm(cookieValue = "form-session")
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf")
        .setBody("pdf-after-form-login"),
    )

    portalClient.openSchoolReport(
      viewUrl = server.url("/reports/view?id=demo").toString(),
      confirmUrl = null,
    ).use { download ->
      assertEquals("pdf-after-form-login", download.byteStream().bufferedReader().readText())
    }

    assertEquals(
      listOf(PortalLoginApiPath, "/login", "/session", "/reports/view?id=demo"),
      List(4) { server.takeRequest().path },
    )
  }

  @Test
  fun portalLogin_reportsRejectedCredentialsFromAuthApi() = runBlocking {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("""{"data":{"auth":{"loggedIn":false,"errors":["credenziali"]}}}"""),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(302)
        .addHeader("Location", "https://eid.example.invalid/spid/start"),
    )

    try {
      portalClient.openSchoolReport(
        viewUrl = server.url("/reports/view?id=demo").toString(),
        confirmUrl = null,
      ).close()
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertEquals("Il portale ha rifiutato l'accesso.", exception.message)
    }
  }

  private fun enqueuePortalLogin(cookieValue: String = "session-demo") {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("Set-Cookie", "PHPSESSID=$cookieValue; Path=/; HttpOnly")
        .setBody("""{"data":{"auth":{"loggedIn":true}}}"""),
    )
  }

  private fun enqueueLoginForm(cookieValue: String) {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/html")
        .setBody(
          """
          <html><body>
            <form action="/session" method="post">
              <input name="login" />
              <input name="password" type="password" />
            </form>
          </body></html>
          """.trimIndent(),
        ),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Set-Cookie", "PHPSESSID=$cookieValue; Path=/; HttpOnly")
        .setBody("ok"),
    )
  }

  private class TestSessionStorage : SessionStorage {
    private var session: UserSession? = null
    private var credentials: StoredCredentials? = null

    override fun readCurrentSession(): UserSession? = session

    override fun writeSession(session: UserSession) {
      this.session = session
    }

    override fun readStoredCredentials(): StoredCredentials? = credentials

    override fun writeCredentials(username: String, password: String) {
      credentials = StoredCredentials(username, password)
    }

    override fun clear() {
      session = null
      credentials = null
    }
  }
}

private const val PortalLoginApiPath = "/auth-p7/app/default/AuthApi4.php?a=aLoginPwd"
