package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventEntity
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
class CustomEventDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.customEventDao()

  // ─── observeAll / upsert ──────────────────────────────────────────────────

  @Test
  fun initialState_isEmpty() = runTest {
    dao.observeAll().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun upsert_addsEventObservable() = runTest {
    val event = CustomEventEntity(id = "e1", payload = "{}", date = "2026-04-10", time = "09:00")
    dao.observeAll().test {
      awaitItem() // empty
      dao.upsert(event)
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("e1", list.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Ordinamento ASC per data/ora ─────────────────────────────────────────

  @Test
  fun observeAll_orderedByDateThenTimeAscending() = runTest {
    dao.upsert(CustomEventEntity(id = "e3", payload = "{}", date = "2026-04-12", time = "08:00"))
    dao.upsert(CustomEventEntity(id = "e1", payload = "{}", date = "2026-04-10", time = "10:00"))
    dao.upsert(CustomEventEntity(id = "e2", payload = "{}", date = "2026-04-10", time = "08:00"))

    dao.observeAll().test {
      val list = awaitItem()
      assertEquals(listOf("e2", "e1", "e3"), list.map { it.id })
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── deleteById ───────────────────────────────────────────────────────────

  @Test
  fun deleteById_removesCorrectEvent() = runTest {
    dao.upsert(CustomEventEntity(id = "e1", payload = "{}", date = "2026-04-10", time = null))
    dao.upsert(CustomEventEntity(id = "e2", payload = "{}", date = "2026-04-11", time = null))
    dao.deleteById("e1")

    dao.observeAll().test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("e2", list.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun deleteById_nonExistentId_doesNotThrow() = runTest {
    dao.upsert(CustomEventEntity(id = "e1", payload = "{}", date = "2026-04-10", time = null))
    dao.deleteById("non_existent")

    dao.observeAll().test {
      assertEquals(1, awaitItem().size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── REPLACE on same id ───────────────────────────────────────────────────

  @Test
  fun upsertSameId_replacesExistingEvent() = runTest {
    dao.upsert(CustomEventEntity(id = "e1", payload = "old", date = "2026-04-10", time = null))
    dao.upsert(CustomEventEntity(id = "e1", payload = "new", date = "2026-04-10", time = null))

    dao.observeAll().test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("new", list.first().payload)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
