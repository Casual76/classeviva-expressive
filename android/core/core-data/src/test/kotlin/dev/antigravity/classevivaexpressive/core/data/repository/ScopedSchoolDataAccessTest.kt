package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.data.testSessionStore
import dev.antigravity.classevivaexpressive.core.database.database.*
import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaRestClient
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ScopedSchoolDataAccessTest {
  private val cache = mockk<SnapshotCacheDao>()
  private val rest = mockk<ClassevivaRestClient>()
  private val store = testSessionStore()
  private val repository = ScopedSchoolDataAccess(store, cache, rest, Json)
  private val query = SchoolDataQuery("agenda", SchoolYearRef(2025, 2026))
  init { coEvery { cache.getByKey(any()) } returns null; coEvery { cache.upsert(any()) } just Runs }
  private suspend fun failure(q: SchoolDataQuery): String = try { repository.read(q); error("expected error") } catch(e: SchoolDataUnavailable) { e.code }
  @Test fun absentCacheIsUnavailableAndNeverKnownEmpty() = runTest {
    assertEquals("data_unavailable", failure(query)); coVerify(exactly = 0) { rest.getAgenda(any(), any()) }
  }
  @Test fun historicalRefreshUsesExplicitDatesAndNeverSelectionStore() = runTest {
    coEvery { rest.getAgenda("2025-09-01", "2026-08-31") } returns emptyList()
    val result = repository.read(query.copy(refresh = true))
    assertEquals(0, result.total); assertEquals(true, result.complete); assertTrue(result.refreshed)
    assertFalse(result.stale); assertNotNull(result.syncedAt); assertEquals(true, result.authValid)
    coVerify { cache.upsert(match { it.cacheKey.contains("2025-2026") && it.cacheKey.contains("student-1") }) }
  }
  @Test fun mismatchedAccountAndStudentNeverAccessNetworkOrCache() = runTest {
    assertEquals("scope_unavailable", failure(query.copy(account = "other")))
    assertEquals("scope_unavailable", failure(query.copy(student = "other")))
    coVerify(exactly = 0) { cache.getByKey(any()) }
  }
  @Test fun currentLoginDoesNotMakeOldCacheFreshOrAuthValidated() = runTest {
    coEvery { cache.getByKey(any()) } answers { SnapshotCacheEntity(firstArg(), "[]", 1) }
    val result = repository.read(query)
    assertTrue(result.stale); assertFalse(result.refreshed); assertNull(result.authValid)
  }
  @Test fun logoutDuringNetworkCannotWriteOrReturnOldData() = runTest {
    coEvery { rest.getAgenda(any(), any()) } coAnswers { store.clear(); emptyList() }
    try { repository.read(query.copy(refresh = true)); fail("account change accepted") } catch(_: Exception) { }
    coVerify(exactly = 0) { cache.upsert(any()) }
  }
  @Test fun missingAuthAndNetworkErrorAreDistinct() = runTest {
    coEvery { rest.getAgenda(any(), any()) } throws java.io.IOException("offline")
    assertEquals("refresh_failed", failure(query.copy(refresh = true)))
    store.clear(); assertEquals("auth_missing", failure(query))
  }
  @Test fun historicalNonYearEndpointNeverSubstitutesCurrentData() = runTest {
    assertEquals("historical_refresh_unavailable", failure(query.copy(section = "grades", refresh = true)))
    coVerify(exactly = 0) { rest.getGrades() }
  }
  @Test fun invalidatedAuthenticationCanReadStaleCacheButCannotRefresh() = runTest {
    val snapshot = store.readAuthSnapshot()!!
    store.writeAuthSnapshot(snapshot.copy(authenticationState = snapshot.authenticationState.copy(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)))
    coEvery { cache.getByKey(any()) } answers { SnapshotCacheEntity(firstArg(), "[]", System.currentTimeMillis()) }
    assertTrue(repository.read(query).stale)
    assertEquals("auth_required", failure(query.copy(refresh = true)))
    coVerify(exactly = 0) { rest.getAgenda(any(), any()) }
  }
  @Test fun accountInvalidationDuringCacheReadDiscardsResult() = runTest {
    coEvery { cache.getByKey(any()) } answers { store.clear(); SnapshotCacheEntity(firstArg(), "[]", 1) }
    assertEquals("account_changed", failure(query))
  }

  @Test fun dedicatedCacheKeysSeparateAccountsAndYearsForSameStudent() = runTest {
    val keys = mutableListOf<String>()
    coEvery { cache.getByKey(capture(keys)) } answers { SnapshotCacheEntity(firstArg(), "[]", 1) }
    repository.read(query)
    repository.read(query.copy(year = SchoolYearRef(2024, 2025)))
    val original = store.readAuthSnapshot()!!
    store.writeAuthSnapshot(original.copy(session = original.session!!.copy(username = "another-account")))
    repository.read(query)
    assertEquals(3, keys.distinct().size)
    assertTrue(keys.all { it.startsWith("aria:") && it.contains("student-1") })
    assertTrue(keys[0].contains("2025-2026"))
    assertTrue(keys[1].contains("2024-2025"))
    assertTrue(keys[2].contains("another-account"))
  }

  @Test fun legacyStudentCacheCannotBeRelabeledForScopedRead() = runTest {
    coEvery { cache.getByKey(any()) } answers {
      val key = firstArg<String>()
      if (key.startsWith("aria:")) null else SnapshotCacheEntity(key, "[]", 1)
    }
    assertEquals("data_unavailable", failure(query))
    coVerify(exactly = 0) { cache.getByKey(match { !it.startsWith("aria:") }) }
    coVerify(exactly = 0) { cache.upsert(any()) }
  }

  @Test fun sameStudentAccountSwitchDuringRefreshCannotCommitPreviousAccountData() = runTest {
    coEvery { rest.getAgenda(any(), any()) } coAnswers {
      val previous = store.readAuthSnapshot()!!
      store.writeAuthSnapshot(previous.copy(session = previous.session!!.copy(username = "another-account")))
      emptyList()
    }
    assertEquals("account_changed", failure(query.copy(refresh = true)))
    coVerify(exactly = 0) { cache.upsert(any()) }
  }

  @Test fun outOfYearRequestsRejectBeforeCacheOrNetworkEvenWhenSourceIsEmpty() = runTest {
    for ((from, to) in listOf("2024-01-01" to "2024-12-31", "2027-01-01" to "2027-12-31",
      "2025-08-31" to "2025-09-02", "2026-08-30" to "2026-09-01")) {
      for (refresh in listOf(false, true)) {
        val error = runCatching { repository.read(query.copy(from = from, to = to, refresh = refresh)) }.exceptionOrNull()
        assertEquals("range_outside_school_year", error?.message)
      }
    }
    coVerify(exactly = 0) { cache.getByKey(any()) }
    coVerify(exactly = 0) { rest.getAgenda(any(), any()) }
  }
  @Test fun failedStrictRefreshPreservesKnownCache() = runTest {
    val known = SnapshotCacheEntity("fixture", "[]", 1)
    coEvery { cache.getByKey(any()) } returns known
    coEvery { rest.getAgenda(any(), any()) } throws dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException("source_payload_invalid")
    assertEquals("refresh_failed", failure(query.copy(refresh = true)))
    coVerify(exactly = 0) { cache.upsert(any()) }
    assertEquals(0, repository.read(query).total)
  }

}
