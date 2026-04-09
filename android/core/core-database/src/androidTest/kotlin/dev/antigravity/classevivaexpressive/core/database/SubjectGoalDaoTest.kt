package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.database.database.SchoolDatabase
import dev.antigravity.classevivaexpressive.core.database.database.SubjectGoalEntity
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
class SubjectGoalDaoTest {

  private lateinit var db: SchoolDatabase

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      SchoolDatabase::class.java,
    ).allowMainThreadQueries().build()
  }

  @After fun tearDown() { db.close() }

  private val dao get() = db.subjectGoalDao()

  // ─── observeByStudent ─────────────────────────────────────────────────────

  @Test
  fun initialState_isEmpty() = runTest {
    dao.observeByStudent("s55").test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun upsert_addsGoalForStudent() = runTest {
    val goal = SubjectGoalEntity(
      id = "goal1", studentId = "s55", subject = "Matematica",
      periodCode = "P1", targetAverage = 8.0, updatedAtEpochMillis = 1_000L,
    )
    dao.observeByStudent("s55").test {
      awaitItem() // empty
      dao.upsert(goal)
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals(8.0, list.first().targetAverage, 0.001)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Ordinamento ASC per materia ─────────────────────────────────────────

  @Test
  fun observeByStudent_orderedBySubjectAscending() = runTest {
    dao.upsert(SubjectGoalEntity(id = "g3", studentId = "s55", subject = "Storia", periodCode = null, targetAverage = 7.0, updatedAtEpochMillis = 1L))
    dao.upsert(SubjectGoalEntity(id = "g1", studentId = "s55", subject = "Fisica", periodCode = null, targetAverage = 8.0, updatedAtEpochMillis = 1L))
    dao.upsert(SubjectGoalEntity(id = "g2", studentId = "s55", subject = "Matematica", periodCode = null, targetAverage = 9.0, updatedAtEpochMillis = 1L))

    dao.observeByStudent("s55").test {
      val subjects = awaitItem().map { it.subject }
      assertEquals(listOf("Fisica", "Matematica", "Storia"), subjects)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── delete con periodCode = null ────────────────────────────────────────

  @Test
  fun delete_withNullPeriodCode_removesCorrectGoal() = runTest {
    dao.upsert(SubjectGoalEntity(id = "g1", studentId = "s55", subject = "Matematica", periodCode = null, targetAverage = 8.0, updatedAtEpochMillis = 1L))
    dao.upsert(SubjectGoalEntity(id = "g2", studentId = "s55", subject = "Fisica", periodCode = "P1", targetAverage = 7.0, updatedAtEpochMillis = 1L))

    dao.delete("s55", "Matematica", null)

    dao.observeByStudent("s55").test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("Fisica", list.first().subject)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun delete_withPeriodCode_removesCorrectGoal() = runTest {
    dao.upsert(SubjectGoalEntity(id = "g1", studentId = "s55", subject = "Fisica", periodCode = "P1", targetAverage = 7.5, updatedAtEpochMillis = 1L))
    dao.upsert(SubjectGoalEntity(id = "g2", studentId = "s55", subject = "Fisica", periodCode = "P2", targetAverage = 8.0, updatedAtEpochMillis = 1L))

    dao.delete("s55", "Fisica", "P1")

    dao.observeByStudent("s55").test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("P2", list.first().periodCode)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Filtraggio per studentId ─────────────────────────────────────────────

  @Test
  fun observeByStudent_returnsOnlyMatchingStudentGoals() = runTest {
    dao.upsert(SubjectGoalEntity(id = "g1", studentId = "s55", subject = "Matematica", periodCode = null, targetAverage = 8.0, updatedAtEpochMillis = 1L))
    dao.upsert(SubjectGoalEntity(id = "g2", studentId = "s99", subject = "Fisica", periodCode = null, targetAverage = 7.0, updatedAtEpochMillis = 1L))

    dao.observeByStudent("s55").test {
      val list = awaitItem()
      assertEquals(1, list.size)
      assertEquals("s55", list.first().studentId)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
