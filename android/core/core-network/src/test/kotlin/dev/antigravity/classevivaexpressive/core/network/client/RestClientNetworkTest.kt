package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.GsonBuilder
import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
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
import org.junit.Assert.assertNull
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
    io.mockk.mockkStatic(android.util.Log::class)
    io.mockk.every { android.util.Log.i(any(), any()) } returns 0
    io.mockk.every { android.util.Log.e(any(), any(), any()) } returns 0

    server = MockWebServer()
    server.start()
    sessionStorage = TestSessionStorage()

    val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
    val gson = GsonBuilder().create()
    val testOriginPolicy = RestOriginPolicy.fromBaseUrl(server.url("/rest/"))
    val headersInterceptor = ClassevivaHeadersInterceptor(testOriginPolicy)
    authService = buildAuthService(gson, headersInterceptor)
    apiSessionManager = ApiSessionManager(sessionStorage, authService)
    val authTokenInterceptor = AuthTokenInterceptor(sessionStorage, testOriginPolicy)
    val authenticator = SessionAuthenticator(apiSessionManager, testOriginPolicy)
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
  fun getMaterials_acceptsLegacyWrapperAndDirectNestedArrays() = runBlocking {
    setActiveSession(token = "token-materials", studentId = "312345")
    server.enqueue(jsonResponse(fixture("didactics_direct_arrays.json")))

    val material = restClient.getMaterials().single()
    val request = server.takeRequest()

    assertEquals("/rest/v1/students/312345/didactics", request.path)
    assertEquals("content-01", material.id)
    assertEquals("Dispensa introduttiva", material.title)
  }

  @Test
  fun openMaterialStream_leavesAuthenticatedBodyForCallerConsumption() = runBlocking {
    setActiveSession(token = "token-material", studentId = "312345")
    server.enqueue(
      MockResponse()
        .addHeader("Content-Type", "application/pdf")
        .setBody("streamed-material"),
    )
    val item = MaterialItem(
      id = "content-01",
      teacherId = "teacher",
      teacherName = "Docente",
      folderId = "folder",
      folderName = "Cartella",
      title = "Dispensa.pdf",
      objectId = "object",
      objectType = "FILE",
      sharedAt = "2026-04-01",
      capabilityState = CapabilityState(),
    )

    val payload = restClient.openMaterialStream(item).use { stream ->
      assertEquals("application/pdf", stream.mimeType)
      stream.byteStream().bufferedReader().readText()
    }
    val request = server.takeRequest()

    assertEquals("streamed-material", payload)
    assertEquals("/rest/v1/students/312345/didactics/item/content-01", request.path)
    assertEquals("token-material", request.getHeader("Z-Auth-Token"))
  }

  @Test
  fun linkMaterial_resolvesOnlySafeExternalHttpsUrlAndNeverStartsAuthenticatedDownload() = runBlocking {
    val linkItem = MaterialItem(
      id = "content-link",
      teacherId = "teacher",
      teacherName = "Docente",
      folderId = "folder",
      folderName = "Cartella",
      title = "Risorsa esterna",
      objectId = "object-link",
      objectType = "LINK",
      sharedAt = "2026-04-02",
      capabilityState = CapabilityState(),
      attachments = listOf(
        RemoteAttachment(
          id = "content-link",
          name = "Risorsa esterna",
          url = "https://example.edu/materiali/risorsa",
        ),
      ),
    )

    assertEquals(
      "https://example.edu/materiali/risorsa",
      restClient.resolveMaterialExternalUrl(linkItem),
    )
    assertNull(
      restClient.resolveMaterialExternalUrl(
        linkItem.copy(
          attachments = listOf(
            RemoteAttachment(
              id = "unsafe",
              name = "URL non sicuro",
              url = "http://example.edu/materiali/risorsa",
            ),
          ),
        ),
      ),
    )

    try {
      restClient.openMaterialStream(linkItem)
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertTrue(exception.message.orEmpty().contains("URL esterno"))
    }
    assertEquals(0, server.requestCount)
  }

  @Test
  fun getSchoolbooks_acceptsTopLevelBooksArray() = runBlocking {
    setActiveSession(token = "token-books", studentId = "312345")
    server.enqueue(jsonResponse(fixture("schoolbooks_direct_books.json")))

    val course = restClient.getSchoolbooks().single()
    val request = server.takeRequest()

    assertEquals("/rest/v1/students/312345/schoolbooks", request.path)
    assertEquals("GET", request.method)
    assertEquals("9780000000001", course.books.single().isbn)
  }

  @Test
  fun getDocuments_postsWithoutBodyAndCombinesCollectionsUsingRemoteHash() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    server.enqueue(jsonResponse(fixture("documents_combined.json")))

    val documents = restClient.getDocuments()
    val request = server.takeRequest()

    assertEquals("/rest/v1/students/312345/documents", request.path)
    assertEquals("POST", request.method)
    assertEquals(0L, request.body.size)
    assertEquals(2, documents.size)
    assertTrue(documents.all { it.restReadUrl?.endsWith(it.remoteHash.orEmpty()) == true })
    assertTrue(documents.all { it.confirmUrl?.endsWith(it.remoteHash.orEmpty()) == true })
  }

  @Test
  fun getDocuments_retriesWithGetOnlyFor400InvalidPayload() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .addHeader("Content-Type", "application/json")
        .setBody("""{ "error": "invalid payload" }"""),
    )
    server.enqueue(jsonResponse("""{ "documents": [] }"""))

    assertTrue(restClient.getDocuments().isEmpty())
    val postRequest = server.takeRequest()
    val getRequest = server.takeRequest()

    assertEquals("POST", postRequest.method)
    assertEquals(0L, postRequest.body.size)
    assertEquals("GET", getRequest.method)
    assertEquals(postRequest.path, getRequest.path)
  }

  @Test
  fun getDocuments_doesNotRetryOther400Responses() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .addHeader("Content-Type", "application/json")
        .setBody("""{ "error": "student not enabled" }"""),
    )

    try {
      restClient.getDocuments()
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertTrue(exception.message.orEmpty().contains("400"))
    }

    assertEquals("POST", server.takeRequest().method)
    assertEquals(1, server.requestCount)
  }

  @Test
  fun getDocuments_doesNotSynthesizeRestUrlsWhenHashIsMissing() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    server.enqueue(
      jsonResponse(
        """
        {
          "documents": [
            { "id": "document-without-hash", "desc": "Documento senza hash" }
          ]
        }
        """.trimIndent(),
      ),
    )

    val document = restClient.getDocuments().single()

    assertNull(document.remoteHash)
    assertNull(document.restReadUrl)
    assertNull(document.viewUrl)
    assertNull(document.confirmUrl)
  }

  @Test
  fun openDocumentStream_usesRemoteHashWithoutBufferingBody() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/pdf")
        .setBody("sanitized-pdf-placeholder"),
    )
    val document = DocumentItem(
      id = "local-document-id",
      title = "Documento dimostrativo",
      detail = "Documento",
      remoteHash = "remote-hash-01",
    )

    val (mimeType, payload) = restClient.openDocumentStream(document).use { stream ->
      stream.mimeType to stream.byteStream().bufferedReader().readText()
    }
    val request = server.takeRequest()

    assertEquals("/rest/v1/students/312345/documents/read/remote-hash-01", request.path)
    assertEquals("application/pdf", mimeType)
    assertEquals("sanitized-pdf-placeholder", payload)
  }

  @Test
  fun checkDocument_postsRemoteHashInsteadOfLocalDocumentId() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    server.enqueue(jsonResponse("""{ "checked": true }"""))
    val document = DocumentItem(
      id = "local-document-id",
      title = "Documento dimostrativo",
      detail = "Documento",
      remoteHash = "remote-hash-02",
    )

    restClient.checkDocument(document)
    val request = server.takeRequest()

    assertEquals("/rest/v1/students/312345/documents/check/remote-hash-02", request.path)
    assertEquals("POST", request.method)
  }

  @Test
  fun checkDocument_rejectsMissingRemoteHashWithoutNetworkCall() = runBlocking {
    setActiveSession(token = "token-documents", studentId = "312345")
    val document = DocumentItem(
      id = "local-document-id",
      title = "Documento senza hash",
      detail = "Documento",
    )

    try {
      restClient.checkDocument(document)
      fail("Expected ClassevivaNetworkException")
    } catch (exception: ClassevivaNetworkException) {
      assertTrue(exception.message.orEmpty().contains("hash remoto"))
    }

    assertEquals(0, server.requestCount)
  }

  @Test
  fun dynamicRestUrl_rejectsHostileOriginWhileOfficialOriginReceivesCredentials() = runBlocking {
    setActiveSession(token = "token-origin-guard", studentId = "312345")
    val hostileServer = MockWebServer().apply { start() }
    try {
      val hostileDocument = DocumentItem(
        id = "hostile-document",
        title = "Documento esterno",
        detail = "Documento",
        restReadUrl = hostileServer.url("/rest/v1/file").toString(),
      )

      try {
        restClient.openDocumentStream(hostileDocument)
        fail("Expected ClassevivaNetworkException")
      } catch (exception: ClassevivaNetworkException) {
        assertTrue(exception.message.orEmpty().isNotBlank())
      }
      assertEquals(0, hostileServer.requestCount)

      server.enqueue(MockResponse().addHeader("Content-Type", "application/pdf").setBody("safe"))
      val officialItem = MaterialItem(
        id = "official-material",
        teacherId = "teacher",
        teacherName = "Docente",
        folderId = "folder",
        folderName = "Cartella",
        title = "Materiale.pdf",
        objectId = "object",
        objectType = "FILE",
        sharedAt = "2026-04-01",
        capabilityState = CapabilityState(),
      )
      restClient.openMaterialStream(officialItem).use { stream ->
        assertEquals("safe", stream.byteStream().bufferedReader().readText())
      }
      val officialRequest = server.takeRequest()
      assertEquals("token-origin-guard", officialRequest.getHeader("Z-Auth-Token"))
      assertEquals(DevApiKey, officialRequest.getHeader("Z-Dev-ApiKey"))

      server.enqueue(
        MockResponse()
          .setResponseCode(302)
          .addHeader("Location", hostileServer.url("/rest/redirect-target")),
      )
      hostileServer.enqueue(MockResponse().setBody("redirected-without-credentials"))
      restClient.openMaterialStream(officialItem.copy(id = "redirect-material")).use { stream ->
        assertEquals("redirected-without-credentials", stream.byteStream().bufferedReader().readText())
      }
      val redirectSourceRequest = server.takeRequest()
      val redirectTargetRequest = hostileServer.takeRequest()
      assertEquals("token-origin-guard", redirectSourceRequest.getHeader("Z-Auth-Token"))
      assertEquals(DevApiKey, redirectSourceRequest.getHeader("Z-Dev-ApiKey"))
      assertNull(redirectTargetRequest.getHeader("Z-Auth-Token"))
      assertNull(redirectTargetRequest.getHeader("Z-Dev-ApiKey"))
      assertNull(redirectTargetRequest.getHeader(SkipAuthHeader))
    } finally {
      hostileServer.shutdown()
    }
  }

  @Test
  fun getCommunicationDetail_postsOfficialPayloadToReadEndpoint() = runBlocking {
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
    val body = readRequest.body.readUtf8()
    assertTrue(body.contains("\"pubId\":99"))
    assertTrue(body.contains("\"cntId\":99"))
    assertTrue(body.contains("\"evtCode\":\"CIR\""))
  }

  @Test
  fun confirmNoticeboard_callsReadEndpointDirectly() = runBlocking {
    setActiveSession(token = "token-notice", studentId = "312345")
    val communication = Communication(
      id = "25849227",
      pubId = "25849227",
      evtCode = "CIR",
      title = "Circolare",
      contentPreview = "Preview",
      sender = "Scuola",
      date = "20260520",
      read = false,
      needsAck = true,
    )
    server.enqueue(
      jsonResponse(
        """
        {
          "items": [
            {
              "id": "25849227",
              "pubId": "25849227",
              "evtCode": "CIR",
              "cntTitle": "Circolare",
              "itemText": "Preview",
              "authorName": "Scuola",
              "evtDate": "20260520"
            }
          ]
        }
        """.trimIndent(),
      ),
    )
    server.enqueue(jsonResponse("""{ "item": { "text": "Dettaglio" } }"""))

    restClient.confirmNoticeboard(communication)

    server.takeRequest() // Consume getCommunications list request
    val readRequest = server.takeRequest()
    assertEquals("/rest/v1/students/312345/noticeboard/read/CIR/25849227/101", readRequest.path)
    assertEquals("POST", readRequest.method)
    assertEquals("token-notice", readRequest.getHeader("Z-Auth-Token"))
    val readBody = readRequest.body.readUtf8()
    assertTrue(readBody.contains("\"pubId\":25849227"))
    assertTrue(readBody.contains("\"cntId\":25849227"))
    assertTrue(readBody.contains("\"evtCode\":\"CIR\""))
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
      .addNetworkInterceptor(headersInterceptor)
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
      .addNetworkInterceptor(authTokenInterceptor)
      .addNetworkInterceptor(headersInterceptor)
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

  private fun fixture(name: String): String {
    return checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
      "Fixture not found: $name"
    }.readText()
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
