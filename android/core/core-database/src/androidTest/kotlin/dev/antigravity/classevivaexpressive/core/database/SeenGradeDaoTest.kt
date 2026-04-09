package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.SchoolDatabase
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeEntity
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
class SeenGradeDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.seenGradeDao()

  // ─── observeByStudent ─────────────────────────────────────────────────────

  @Test
  fun initialState_isEmpty() = runTest {
    dao.observeByStudent("s55").test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun upsert_addsSeenGradeForStudent() = runTest {
    val entity = SeenGradeEntity(id = "sg1", studentId = "s55", gradeId = "g1", seenAtEpochMillis = 1_000L)
    dao.observeByStudent("s55").test {
      awaitItem() // empty
      dao.upsert(entity)
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("g1", list.first().gradeId)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Filtraggio per studentId ─────────────────────────────────────────────

  @Test
  fun observeByStudent_returnsOnlyMatchingStudentGrades() = runTest {
    dao.upsert(SeenGradeEntity(id = "sg1", studentId = "s55", gradeId = "g1", seenAtEpochMillis = 1_000L))
    dao.upsert(SeenGradeEntity(id = "sg2", studentId = "s99", gradeId = "g2", seenAtEpochMillis = 2_000L))
    dao.upsert(SeenGradeEntity(id = "sg3", studentId = "s55", gradeId = "g3", seenAtEpochMillis = 3_000L))

    dao.observeByStudent("s55").test {
      val list = awaitItem()
      assertEquals(2, list.size)
      assertTrue(list.all { it.studentId == "s55" })
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── REPLACE on same id ───────────────────────────────────────────────────

  @Test
  fun upsertSameId_replacesExistingEntry() = runTest {
    dao.upsert(SeenGradeEntity(id = "sg1", studentId = "s55", gradeId = "g1", seenAtEpochMillis = 1_000L))
    dao.upsert(SeenGradeEntity(id = "sg1", studentId = "s55", gradeId = "g1", seenAtEpochMillis = 5_000L))

    dao.observeByStudent("s55").test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals(5_000L, list.first().seenAtEpochMillis)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
