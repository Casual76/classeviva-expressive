package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.database.database.*
import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.classevivaexpressive.core.network.client.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ScopedSchoolRestBoundaryTest {
  @Test fun realRestSelectionAndAliasFailuresCannotReplaceKnownScopedCache() = runBlocking {
    val server = MockWebServer()
    server.start()
    try {
      val store = testSessionStore()
      val session = store.readAuthSnapshot()!!.session!!
      val retrofit = Retrofit.Builder().baseUrl(server.url("/rest/"))
        .addConverterFactory(GsonConverterFactory.create()).build()
      val api = retrofit.create(ClassevivaApiService::class.java)
      val auth = retrofit.create(ClassevivaAuthService::class.java)
      val manager = mockk<ApiSessionManager>()
      every { manager.currentSession() } returns session
      every { manager.canUseRest() } returns true
      val rest = ClassevivaRestClient(Json, api, auth, manager)
      val cache = mockk<SnapshotCacheDao>()
      var saved: SnapshotCacheEntity? = null
      coEvery { cache.getByKey(any()) } answers { saved }
      coEvery { cache.upsert(any()) } answers { saved = firstArg() }
      val repository = ScopedSchoolDataAccess(store, cache, rest, Json)
      val query = SchoolDataQuery("agenda", SchoolYearRef(2026, 2027), refresh = true)
      val row = """{"id":"real-1","date":"2026-11-07","title":"Assessment"}"""
      for (body in listOf("""{"data":[$row]}""", """{"agenda":{"data":[$row]}}""", """{"agenda":[],"events":[$row]}""")) {
        server.enqueue(MockResponse().setBody(body))
        val page = repository.read(query)
        assertEquals(1, page.total); assertEquals(true, page.complete)
        assertEquals("real-1", page.records.single()["id"]!!.jsonPrimitive.content)
      }
      val known = saved
      for (body in listOf("""{"agenda":[$row],"events":[$row]}""", """{"agenda":[],"data":{"unknown":[]}}""",
        """{"agenda":[{"id":false,"evtId":"real-1","date":"2026-11-07","title":false,"evtTitle":"Assessment"}]}""")) {
        server.enqueue(MockResponse().setBody(body))
        try { repository.read(query); fail(body) } catch (e: SchoolDataUnavailable) { assertEquals("refresh_failed", e.code) }
        assertSame(known, saved)
        val cached = repository.read(query.copy(refresh = false))
        assertEquals(1, cached.total); assertEquals("real-1", cached.records.single()["id"]!!.jsonPrimitive.content)
      }
      coVerify(exactly = 3) { cache.upsert(any()) }
    } finally { server.shutdown() }
  }
}
