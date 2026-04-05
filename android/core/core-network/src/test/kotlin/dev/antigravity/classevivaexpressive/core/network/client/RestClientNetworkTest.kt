package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.GsonBuilder
import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RestClientNetworkTest {
  private lateinit var server: MockWebServer
  private lateinit var sessionStorage: TestSessionStorage
  private lateinit var authService: ClassevivaAuthService
  private lateinit var apiService: ClassevivaApiService
  private lateinit var apiSessionManager: ApiSessionManager
  private lateinit var restClient: ClassevivaRestClient

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    sessionStorage = TestSessionStorage()

    val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
    val gson = GsonBuilder().create()
    val headersInterceptor = NetworkModule.provideHeadersInterceptor()
    authService = buildAuthService(gson, headersInterceptor)
    apiSessionManager = ApiSessionManager(sessionStorage, authService)
    val authTokenInterceptor = NetworkModule.provideAuthTokenInterceptor(sessionStorage)
    val authenticator = NetworkModule.provideSessionAuthenticator(apiSessionManager)
    apiService = buildApiService(gson, headersInterceptor, authTokenInterceptor, authenticator)
    restClient = ClassevivaRestClient(json, apiService, authService, apiSessionManager)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun login_sendsOfficialHeadersAndPayload() = runBlocking {
    server.enqueue(
      jsonResponse(
        """
        {
          "token": "token-1",
          "ident": "312345",
          "firstName": "Ada",
          "lastName": "Lovelace"
        }
        """.trimIndent(),
      ),
    )

    val result = restClient.login("312345", "secret")
    val request = server.takeRequest()

    assertEquals("token-1", result.token)
    assertEquals("312345", result.studentId)
    assertEquals("/rest/v1/auth/login", request.path)
    assertEquals("POST", request.method)
    assertEquals(UserAgent, request.getHeader("User-Agent"))
    assertEquals(DevApiKey, request.getHeader("Z-Dev-ApiKey"))
    val body = request.body.readUtf8()
    assertTrue(body.contains("\"uid\":\"312345\""))
    assertTrue(body.contains("\"pass\":\"secret\""))
    assertTrue(body.contains("\"app\":\"CVVS\""))
    assertTrue(body.contains("\"login\":\"0\""))
    assertTrue(body.contains("\"multipleToken\":\"multiple\""))
  }

  @Test
  fun getAgenda_formatsDatesAndInjectsToken() = runBlocking {
    setActiveSession(token = "token-agenda", studentId = "312345")
    server.enqueue(jsonResponse("""{ "agenda": [] }"""))

    restClient.getAgenda("2026-03-01", "2026-03-07")
    val request = server.takeRequest()

    assertEquals("/rest/v1/students/312345/agenda/all/20260301/20260307", request.path)
    assertEquals("token-agenda", request.getHeader("Z-Auth-Token"))
    assertEquals(UserAgent, request.getHeader("User-Agent"))
    assertEquals(DevApiKey, request.getHeader("Z-Dev-ApiKey"))
  }

  @Test
  fun getCommunicationDetail_postsEmptyBodyToReadEndpoint() = runBlocking {
    setActiveSession(token = "token-notice", studentId = "312345")
    server.enqueue(
      jsonResponse(
        """
        {
          "items": [
            {
              "id": "99",
              "pubId": "99",
              "evtCode": "CIR",
              "cntTitle": "Circolare",
              "itemText": "Preview",
              "authorName": "Scuola",
              "evtDate": "20260320"
            }
          ]
        }
        """.trimIndent(),
      ),
    )
    server.enqueue(jsonResponse("""{ "item": { "text": "Dettaglio" } }"""))

    restClient.getCommunicationDetail(pubId = "99", evtCode = "CIR")

    server.takeRequest()
    val readRequest = server.takeRequest()
    assertEquals("/rest/v1/students/312345/noticeboard/read/CIR/99/101", readRequest.path)
    assertEquals("POST", readRequest.method)
    assertEquals("{}", readRequest.body.readUtf8())
  }

  @Test
  fun getProfile_retriesAfter401WithStatusReloginAndRetry() = runBlocking {
    setActiveSession(token = "stale-token", studentId = "312345", username = "312345", password = "secret")
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(
      jsonResponse(
        """
        {
          "token": "fresh-token",
          "ident": "312345",
          "firstName": "Ada",
          "lastName": "Lovelace"
        }
        """.trimIndent(),
      ),
    )
    server.enqueue(
      jsonResponse(
        """
        {
          "card": {
            "usrId": "312345",
            "firstName": "Ada",
            "lastName": "Lovelace",
            "classDesc": "5A"
          }
        }
        """.trimIndent(),
      ),
    )

    val profile = restClient.getProfile()

    assertEquals("Ada", profile.name)
    assertEquals("fresh-token", sessionStorage.currentSession?.token)

    val initialCardRequest = server.takeRequest()
    val statusRequest = server.takeRequest()
    val loginRequest = server.takeRequest()
    val retriedCardRequest = server.takeRequest()

    assertEquals("/rest/v1/students/312345/card", initialCardRequest.path)
    assertEquals("stale-token", initialCardRequest.getHeader("Z-Auth-Token"))
    assertEquals("/rest/v1/auth/status", statusRequest.path)
    assertEquals("stale-token", statusRequest.getHeader("Z-Auth-Token"))
    assertEquals("/rest/v1/auth/login", loginRequest.path)
    assertEquals("/rest/v1/students/312345/card", retriedCardRequest.path)
    assertEquals("fresh-token", retriedCardRequest.getHeader("Z-Auth-Token"))
  }

  @Test
  fun getProfile_maps404ToDomainError() = runBlocking {
    setActiveSession(token = "token-404", studentId = "312345")
    server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "not found" }"""))

    try {
      restClient.getProfile()
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertEquals("Risorsa Classeviva non trovata.", exception.message)
    }
  }

  @Test
  fun getProfile_maps500ToDomainError() = runBlocking {
    setActiveSession(token = "token-500", studentId = "312345")
    server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))

    try {
      restClient.getProfile()
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertEquals("Classeviva ha restituito un errore server.", exception.message)
    }
  }

  private fun buildAuthService(
    gson: com.google.gson.Gson,
    headersInterceptor: Interceptor,
  ): ClassevivaAuthService {
    val client = OkHttpClient.Builder()
      .addInterceptor(headersInterceptor)
      .build()
    return Retrofit.Builder()
      .baseUrl(server.url("/rest/"))
      .client(client)
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build()
      .create(ClassevivaAuthService::class.java)
  }

  private fun buildApiService(
    gson: com.google.gson.Gson,
    headersInterceptor: Interceptor,
    authTokenInterceptor: Interceptor,
    authenticator: okhttp3.Authenticator,
  ): ClassevivaApiService {
    val client = OkHttpClient.Builder()
      .addInterceptor(authTokenInterceptor)
      .addInterceptor(headersInterceptor)
      .authenticator(authenticator)
      .build()
    return Retrofit.Builder()
      .baseUrl(server.url("/rest/"))
      .client(client)
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build()
      .create(ClassevivaApiService::class.java)
  }

  private fun jsonResponse(body: String): MockResponse {
    return MockResponse()
      .setResponseCode(200)
      .addHeader("Content-Type", "application/json")
      .setBody(body)
  }

  private fun setActiveSession(
    token: String,
    studentId: String,
    username: String = "studente",
    password: String? = null,
  ) {
    val session = UserSession(
      token = token,
      studentId = studentId,
      username = username,
      profile = StudentProfile(id = studentId, name = "Ada", surname = "Lovelace"),
    )
    sessionStorage.writeSession(session)
    password?.let { sessionStorage.writeCredentials(username = username, password = it) }
    restClient.setSession(session)
  }

  private class TestSessionStorage : SessionStorage {
    var currentSession: UserSession? = null
    private var credentials: StoredCredentials? = null

    override fun readCurrentSession(): UserSession? = currentSession

    override fun writeSession(session: UserSession) {
      currentSession = session
    }

    override fun readStoredCredentials(): StoredCredentials? = credentials

    override fun writeCredentials(username: String, password: String) {
      credentials = StoredCredentials(username = username, password = password)
    }

    override fun clear() {
      currentSession = null
      credentials = null
    }
  }
}
