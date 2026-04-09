package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.SchoolDatabase
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreSnapshotEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StudentScoreDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.studentScoreDao()

  // ─── observeLatest ────────────────────────────────────────────────────────

  @Test
  fun observeLatest_returnsNullWhenEmpty() = runTest {
    dao.observeLatest().test {
      assertNull(awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun observeLatest_returnsMostRecentByEpoch() = runTest {
    dao.upsert(StudentScoreSnapshotEntity(id = "s1", payload = "old", createdAtEpochMillis = 1_000L))
    dao.upsert(StudentScoreSnapshotEntity(id = "s2", payload = "new", createdAtEpochMillis = 2_000L))

    dao.observeLatest().test {
      val latest = awaitItem()
      assertNotNull(latest)
      assertEquals("s2", latest?.id)
      assertEquals("new", latest?.payload)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── observeAll ───────────────────────────────────────────────────────────

  @Test
  fun observeAll_orderedByCreatedAtDescending() = runTest {
    dao.upsert(StudentScoreSnapshotEntity(id = "s1", payload = "{}", createdAtEpochMillis = 1_000L))
    dao.upsert(StudentScoreSnapshotEntity(id = "s3", payload = "{}", createdAtEpochMillis = 3_000L))
    dao.upsert(StudentScoreSnapshotEntity(id = "s2", payload = "{}", createdAtEpochMillis = 2_000L))

    dao.observeAll().test {
      val ids = awaitItem().map { it.id }
      assertEquals(listOf("s3", "s2", "s1"), ids)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── REPLACE on same id ───────────────────────────────────────────────────

  @Test
  fun upsertSameId_replacesExistingSnapshot() = runTest {
    dao.upsert(StudentScoreSnapshotEntity(id = "s1", payload = "v1", createdAtEpochMillis = 1_000L))
    dao.upsert(StudentScoreSnapshotEntity(id = "s1", payload = "v2", createdAtEpochMillis = 2_000L))

    dao.observeAll().test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("v2", list.first().payload)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── observeLatest aggiornato dopo upsert ────────────────────────────────

  @Test
  fun observeLatest_updatesAfterNewUpsert() = runTest {
    dao.observeLatest().test {
      assertNull(awaitItem())
      dao.upsert(StudentScoreSnapshotEntity(id = "s1", payload = "first", createdAtEpochMillis = 1_000L))
      assertEquals("s1", awaitItem()?.id)
      dao.upsert(StudentScoreSnapshotEntity(id = "s2", payload = "second", createdAtEpochMillis = 9_000L))
      assertEquals("s2", awaitItem()?.id)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
