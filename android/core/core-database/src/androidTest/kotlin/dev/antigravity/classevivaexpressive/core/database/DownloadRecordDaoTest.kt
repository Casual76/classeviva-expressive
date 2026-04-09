package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordEntity
import dev.antigravity.classevivaexpressive.core.database.database.SchoolDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadRecordDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.downloadRecordDao()

  private fun buildRecord(id: String, updatedAt: Long = 1_000L) = DownloadRecordEntity(
    id = id, sourceUrl = "https://example.com/$id", displayName = "file-$id.pdf",
    mimeType = "application/pdf", status = "COMPLETED", localUri = null,
    updatedAtEpochMillis = updatedAt,
  )

  // ─── observeAll / upsert ──────────────────────────────────────────────────

  @Test
  fun initialState_isEmpty() = runTest {
    dao.observeAll().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun upsert_addsRecordObservable() = runTest {
    val record = buildRecord("d1")
    dao.observeAll().test {
      awaitItem() // empty
      dao.upsert(record)
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("d1", list.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Ordinamento DESC per updatedAtEpochMillis ────────────────────────────

  @Test
  fun observeAll_orderedByUpdatedAtDescending() = runTest {
    dao.upsert(buildRecord("d1", updatedAt = 1_000L))
    dao.upsert(buildRecord("d3", updatedAt = 3_000L))
    dao.upsert(buildRecord("d2", updatedAt = 2_000L))

    dao.observeAll().test {
      val ids = awaitItem().map { it.id }
      assertEquals(listOf("d3", "d2", "d1"), ids)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── REPLACE on same id ───────────────────────────────────────────────────

  @Test
  fun upsertSameId_replacesExistingRecord() = runTest {
    dao.upsert(buildRecord("d1", updatedAt = 1_000L).copy(status = "PENDING"))
    dao.upsert(buildRecord("d1", updatedAt = 2_000L).copy(status = "COMPLETED"))

    dao.observeAll().test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("COMPLETED", list.first().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── mimeType nullable ────────────────────────────────────────────────────

  @Test
  fun upsert_withNullMimeType_persistsCorrectly() = runTest {
    dao.upsert(buildRecord("d1").copy(mimeType = null))

    dao.observeAll().test {
      val record = awaitItem().first()
      assertTrue(record.mimeType == null)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
