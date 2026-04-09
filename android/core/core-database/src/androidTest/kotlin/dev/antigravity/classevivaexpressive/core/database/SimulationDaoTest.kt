package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.SchoolDatabase
import dev.antigravity.classevivaexpressive.core.database.database.SimulatedGradeEntity
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
class SimulationDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.simulationDao()

  // ─── upsert / observeAll ──────────────────────────────────────────────────

  @Test
  fun initialState_isEmpty() = runTest {
    dao.observeAll().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun upsert_addsGradeToObservable() = runTest {
    val grade = SimulatedGradeEntity(id = "g1", payload = "{}", subject = "Matematica", date = "2026-04-10")
    dao.observeAll().test {
      awaitItem() // empty
      dao.upsert(grade)
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("Matematica", list.first().subject)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── deleteById ───────────────────────────────────────────────────────────

  @Test
  fun deleteById_removesCorrectGrade() = runTest {
    dao.upsert(SimulatedGradeEntity(id = "g1", payload = "{}", subject = "Matematica", date = "2026-04-10"))
    dao.upsert(SimulatedGradeEntity(id = "g2", payload = "{}", subject = "Fisica", date = "2026-04-11"))
    dao.deleteById("g1")

    dao.observeAll().test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("g2", list.first().id)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── clearAll ─────────────────────────────────────────────────────────────

  @Test
  fun clearAll_removesAllGrades() = runTest {
    dao.upsert(SimulatedGradeEntity(id = "g1", payload = "{}", subject = "Matematica", date = "2026-04-10"))
    dao.upsert(SimulatedGradeEntity(id = "g2", payload = "{}", subject = "Fisica", date = "2026-04-11"))
    dao.clearAll()

    dao.observeAll().test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Ordinamento ASC per data ─────────────────────────────────────────────

  @Test
  fun observeAll_orderedByDateAscending() = runTest {
    dao.upsert(SimulatedGradeEntity(id = "g3", payload = "{}", subject = "Chimica", date = "2026-04-15"))
    dao.upsert(SimulatedGradeEntity(id = "g1", payload = "{}", subject = "Matematica", date = "2026-04-10"))
    dao.upsert(SimulatedGradeEntity(id = "g2", payload = "{}", subject = "Fisica", date = "2026-04-12"))

    dao.observeAll().test {
      val ids = awaitItem().map { it.id }
      assertEquals(listOf("g1", "g2", "g3"), ids)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
