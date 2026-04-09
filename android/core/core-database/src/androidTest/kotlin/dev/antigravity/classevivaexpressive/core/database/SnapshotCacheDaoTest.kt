package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.SchoolDatabase
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SnapshotCacheDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.snapshotCacheDao()

  // ─── getByKey ─────────────────────────────────────────────────────────────

  @Test
  fun getByKey_returnsNullWhenNoEntryExists() = runTest {
    val result = dao.getByKey("missing_key")
    assertNull(result)
  }

  @Test
  fun upsertAndGetByKey_returnsInsertedEntity() = runTest {
    val entity = SnapshotCacheEntity(cacheKey = "agenda_55", payload = "{}", updatedAtEpochMillis = 1_000L)
    dao.upsert(entity)

    val result = dao.getByKey("agenda_55")
    assertNotNull(result)
    assertEquals("{}", result?.payload)
    assertEquals(1_000L, result?.updatedAtEpochMillis)
  }

  // ─── REPLACE on duplicate key ─────────────────────────────────────────────

  @Test
  fun upsertTwice_replacesExistingEntry() = runTest {
    val first  = SnapshotCacheEntity(cacheKey = "k1", payload = "v1", updatedAtEpochMillis = 100L)
    val second = SnapshotCacheEntity(cacheKey = "k1", payload = "v2", updatedAtEpochMillis = 200L)
    dao.upsert(first)
    dao.upsert(second)

    val result = dao.getByKey("k1")
    assertEquals("v2", result?.payload)
    assertEquals(200L, result?.updatedAtEpochMillis)
  }

  // ─── observeByKey ─────────────────────────────────────────────────────────

  @Test
  fun observeByKey_emitsNullThenValueAfterInsert() = runTest {
    dao.observeByKey("stream_key").test {
      assertNull(awaitItem())
      dao.upsert(SnapshotCacheEntity(cacheKey = "stream_key", payload = "data", updatedAtEpochMillis = 1L))
      val updated = awaitItem()
      assertEquals("data", updated?.payload)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
