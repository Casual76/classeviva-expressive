package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.yearScopedCacheKey
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusPersistenceTest {
  private val schoolYear = SchoolYearRef(startYear = 2025, endYear = 2026)

  @Test
  fun observePersistedSyncStatus_readsExplicitSyncMarker() = runTest {
    val dao = snapshotCacheDaoWith(
      SyncStatusCacheKey to SnapshotCacheEntity(
        cacheKey = SyncStatusCacheKey,
        payload = "456",
        updatedAtEpochMillis = 123L,
      ),
    )

    val status = observePersistedSyncStatus(dao, schoolYear).first()

    assertEquals(456L, status.lastSuccessfulSyncEpochMillis)
  }

  @Test
  fun observePersistedSyncStatus_fallsBackToSectionCacheTimestamp() = runTest {
    val gradesKey = yearScopedCacheKey(GradesSection, schoolYear)
    val dao = snapshotCacheDaoWith(
      gradesKey to SnapshotCacheEntity(
        cacheKey = gradesKey,
        payload = "[]",
        updatedAtEpochMillis = 789L,
      ),
    )

    val status = observePersistedSyncStatus(dao, schoolYear).first()

    assertEquals(789L, status.lastSuccessfulSyncEpochMillis)
  }

  @Test
  fun withPersistedLastSuccessKeepsNewestTimestamp() {
    val runtime = SyncStatus(lastSuccessfulSyncEpochMillis = 100L)
    val persisted = SyncStatus(lastSuccessfulSyncEpochMillis = 200L)

    val status = runtime.withPersistedLastSuccess(persisted)

    assertEquals(200L, status.lastSuccessfulSyncEpochMillis)
  }

  private fun snapshotCacheDaoWith(
    vararg entries: Pair<String, SnapshotCacheEntity>,
  ): SnapshotCacheDao {
    val byKey = entries.toMap()
    return mockk<SnapshotCacheDao> {
      every { observeByKey(any()) } answers { flowOf(byKey[invocation.args[0] as String]) }
    }
  }
}
