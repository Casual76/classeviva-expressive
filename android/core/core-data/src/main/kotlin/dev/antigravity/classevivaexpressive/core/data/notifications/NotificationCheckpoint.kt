package dev.antigravity.classevivaexpressive.core.data.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource

/** The account and school year that own the records advertised by a notification. */
@Serializable
data class NotificationScope(val studentId: String, val schoolYearId: String)

@Serializable
data class SyncSnapshotPayloads(
  val homeworks: String? = null,
  /** Null until the dedicated homework source has been read at least once. */
  val dedicatedHomeworks: String? = null,
  val communications: String? = null,
  val absences: String? = null,
  val grades: String? = null,
  val agenda: String? = null,
  val notes: String? = null,
  val lessons: String? = null,
  val scope: NotificationScope? = null,
)

private val checkpointJson = Json { ignoreUnknownKeys = true }

internal fun notificationCheckpointCacheKey(scope: NotificationScope): String =
  "${scope.studentId}::${scope.schoolYearId}::notification_checkpoint"

internal fun decodeNotificationCheckpoint(payload: String?): SyncSnapshotPayloads? =
  payload?.let { runCatching { checkpointJson.decodeFromString<SyncSnapshotPayloads>(it) }.getOrNull() }

internal fun encodeNotificationCheckpoint(snapshot: SyncSnapshotPayloads): String =
  checkpointJson.encodeToString(SyncSnapshotPayloads.serializer(), snapshot)

/**
 * Discovering a source and delivering an alert are different checkpoints. Silence only the first
 * dedicated snapshot even if Android currently blocks alerts; retain pending agenda items and
 * subsequent dedicated additions until delivery succeeds.
 */
internal fun primeDedicatedHomeworkSource(
  previous: SyncSnapshotPayloads,
  current: SyncSnapshotPayloads,
): SyncSnapshotPayloads {
  if (decodeHomeworkCheckpoint(previous.dedicatedHomeworks) != null) return previous
  val baseline = previous.copy(dedicatedHomeworks = null)
  val dedicated = decodeHomeworkCheckpoint(current.dedicatedHomeworks) ?: return baseline
  val before = decodeHomeworkCheckpoint(previous.homeworks)
  val afterById = decodeHomeworkCheckpoint(current.homeworks).orEmpty().associateBy(Homework::id)
  val primedPayload = if (before != null) {
    // The dedicated and merged cache reads can straddle a foreground write. Remember the actual
    // first source snapshot even if its corresponding merged snapshot has not been observed yet.
    val firstDedicated = dedicated.map { item ->
      afterById[item.id]?.takeIf { it.source == HomeworkSource.DEDICATED } ?: item
    }
    val baseline = (before + firstDedicated).distinctBy(Homework::id)
    checkpointJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Homework.serializer()), baseline)
  } else previous.homeworks
  return previous.copy(homeworks = primedPayload, dedicatedHomeworks = current.dedicatedHomeworks)
}

internal fun advanceHomeworkNotificationCheckpoint(
  previous: SyncSnapshotPayloads,
  current: SyncSnapshotPayloads,
  categoryEnabled: Boolean,
  canDeliver: Boolean,
  deliver: (List<Homework>, List<Homework>, dedicatedPrimed: Boolean) -> Boolean,
): SyncSnapshotPayloads {
  var baseline = primeDedicatedHomeworkSource(previous, current)
  val knownDedicated = decodeHomeworkCheckpoint(previous.dedicatedHomeworks)
  val currentMerged = decodeHomeworkCheckpoint(current.homeworks)
  val mergedBefore = decodeHomeworkCheckpoint(baseline.homeworks)
  val firstDedicated = decodeHomeworkCheckpoint(baseline.dedicatedHomeworks)
  if (mergedBefore != null && firstDedicated != null) {
    // A lagging merged cache must not erase the first-source history after a successful no-op post.
    baseline = baseline.copy(homeworks = checkpointJson.encodeToString(
      kotlinx.serialization.builtins.ListSerializer(Homework.serializer()), (mergedBefore + firstDedicated).distinctBy(Homework::id),
    ))
  } else if (knownDedicated != null && currentMerged != null) {
    // The source may have been observed before the first merged snapshot. Prime only the agenda
    // history now; dedicated additions are compared with that earlier source snapshot.
    val known = (knownDedicated + currentMerged.filter { it.source == HomeworkSource.AGENDA }).distinctBy(Homework::id)
    baseline = baseline.copy(homeworks = checkpointJson.encodeToString(
      kotlinx.serialization.builtins.ListSerializer(Homework.serializer()), known,
    ))
  }
  val acknowledged = advanceNotificationCheckpoint(
    baseline.homeworks, current.homeworks, categoryEnabled, canDeliver,
    decode = { decodeHomeworkCheckpoint(it) },
    deliver = { before, after -> deliver(before, after, baseline.dedicatedHomeworks != null) },
  )
  val acknowledgedItems = decodeHomeworkCheckpoint(acknowledged)
  val currentDedicated = decodeHomeworkCheckpoint(current.dedicatedHomeworks)
  val acknowledgedIds = acknowledgedItems?.map(Homework::id)?.toSet().orEmpty()
  val sourceIncludedInAcknowledgement = acknowledged == current.homeworks && acknowledgedItems != null &&
    currentDedicated != null && currentDedicated.all { it.id in acknowledgedIds }
  return baseline.copy(
    homeworks = acknowledged,
    // Advance the source only when all of it has actually reached an acknowledged merged snapshot.
    // Otherwise retain its first baseline through blocked posts and out-of-phase cache reads.
    dedicatedHomeworks = if (currentDedicated != null && (!categoryEnabled || sourceIncludedInAcknowledgement))
      current.dedicatedHomeworks else baseline.dedicatedHomeworks,
  )
}

private fun decodeHomeworkCheckpoint(payload: String?): List<Homework>? = payload?.let {
  runCatching { checkpointJson.decodeFromString<List<Homework>>(it) }.getOrNull()
}

/**
 * A UI refresh must not acknowledge an undelivered notification. Prime an unknown source silently,
 * acknowledge an explicitly disabled category, and otherwise move its checkpoint only after all
 * of its notification posts succeed. A blocked Android permission/channel can therefore recover.
 */
internal inline fun <T> advanceNotificationCheckpoint(
  previousPayload: String?,
  currentPayload: String?,
  categoryEnabled: Boolean,
  canDeliver: Boolean,
  decode: (String) -> List<T>?,
  deliver: (before: List<T>, after: List<T>) -> Boolean,
): String? {
  val after = currentPayload?.let(decode) ?: return previousPayload
  val before = previousPayload?.let(decode) ?: return currentPayload
  if (!categoryEnabled || previousPayload == currentPayload) return currentPayload
  if (!canDeliver) return previousPayload
  val delivered = try {
    deliver(before, after)
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    false
  }
  return if (delivered) currentPayload else previousPayload
}
