package dev.antigravity.classevivaexpressive.core.data.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCheckpointTest {
  private val old = "[\"known\"]"
  private val fresh = "[\"known\",\"new\"]"
  private val delivered = mutableListOf<Pair<List<String>, List<String>>>()

  private fun advance(
    before: String? = old,
    after: String? = fresh,
    enabled: Boolean = true,
    allowed: Boolean = true,
    succeeds: Boolean = true,
  ): String? = advanceNotificationCheckpoint(
    before, after, enabled, allowed,
    decode = { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() },
    deliver = { previous, current -> delivered += previous to current; succeeds },
  )

  @Test fun firstSuccessfulReadPrimesWithoutAnnouncingHistoricalRecords() {
    assertEquals(fresh, advance(before = null))
    assertTrue(delivered.isEmpty())
  }

  @Test fun emptyKnownSourceStillAnnouncesNewRecords() {
    assertEquals(fresh, advance(before = "[]"))
    assertEquals(listOf(emptyList<String>() to listOf("known", "new")), delivered)
  }

  @Test fun corruptPreviousSourcePrimesWithoutTreatingHistoryAsNew() {
    assertEquals(fresh, advance(before = "invalid-json"))
    assertTrue(delivered.isEmpty())
  }

  @Test fun missingOrCorruptCurrentSourcePreservesKnownCheckpoint() {
    assertEquals(old, advance(after = null))
    assertEquals(old, advance(after = "{}"))
    assertTrue(delivered.isEmpty())
  }

  @Test fun deniedPermissionOrBlockedChannelRetainsChangesUntilRecovery() {
    val blocked = advance(allowed = false)
    assertEquals(old, blocked)
    assertTrue(delivered.isEmpty())
    assertEquals(fresh, advance(before = blocked, allowed = true))
    assertEquals(listOf(listOf("known") to listOf("known", "new")), delivered)
  }

  @Test fun categoryExplicitlyDisabledInAppDoesNotReplayBacklogWhenEnabled() {
    val disabled = advance(enabled = false, allowed = false)
    assertEquals(fresh, disabled)
    assertTrue(delivered.isEmpty())
    assertEquals(fresh, advance(before = disabled, after = fresh))
    assertTrue(delivered.isEmpty())
  }

  @Test fun failedPostCanBeRetriedAndSuccessfulAcknowledgementIsNotReplayed() {
    val failed = advance(succeeds = false)
    assertEquals(old, failed)
    val acknowledged = advance(before = failed)
    assertEquals(fresh, acknowledged)
    advance(before = acknowledged)
    assertEquals(2, delivered.size)
  }

  @Test fun notificationBuilderFailureDoesNotConsumeTheCheckpoint() {
    val acknowledged = advanceNotificationCheckpoint(
      old, fresh, true, true,
      decode = { Json.decodeFromString<List<String>>(it) },
      deliver = { _, _ -> error("notification builder failed") },
    )
    assertEquals(old, acknowledged)
  }

  @Test(expected = CancellationException::class)
  fun cancellationPropagatesWithoutBeingTreatedAsDeliveryFailure() {
    advanceNotificationCheckpoint(
      old, fresh, true, true,
      decode = { Json.decodeFromString<List<String>>(it) },
      deliver = { _, _ -> throw CancellationException("stopped") },
    )
  }

  @Test fun independentSectionsCanAcknowledgePartialDelivery() {
    val grades = advance(succeeds = false)
    val notes = advance(succeeds = true)
    assertEquals(old, grades)
    assertEquals(fresh, notes)
  }

  @Test fun codecKeepsAccountYearAndPerSourcePrimingAcrossProcessRestart() {
    val scope = NotificationScope("student-1", "2026-2027")
    val checkpoint = SyncSnapshotPayloads(scope = scope, grades = fresh, dedicatedHomeworks = "[]")
    assertEquals(checkpoint, decodeNotificationCheckpoint(encodeNotificationCheckpoint(checkpoint)))
    assertEquals("student-1::2026-2027::notification_checkpoint", notificationCheckpointCacheKey(scope))
    assertFalse(notificationCheckpointCacheKey(scope) == notificationCheckpointCacheKey(scope.copy(studentId = "student-2")))
    assertFalse(notificationCheckpointCacheKey(scope) == notificationCheckpointCacheKey(scope.copy(schoolYearId = "2025-2026")))
    assertNull(decodeNotificationCheckpoint("broken"))
  }
}
