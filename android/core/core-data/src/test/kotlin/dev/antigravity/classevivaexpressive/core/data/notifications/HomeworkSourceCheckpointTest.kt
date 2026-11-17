package dev.antigravity.classevivaexpressive.core.data.notifications

import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkSourceCheckpointTest {
  private val posted = mutableListOf<String>()

  @Test fun firstSourceReadDuringAndroidBlockDoesNotConsumeSubsequentNewHomework() {
    val first = homework("H1")
    val second = homework("H2")
    val unknownDedicated = snapshot(emptyList(), dedicated = null)
    val firstRead = advance(unknownDedicated, snapshot(listOf(first)), allowed = false)
    val blocked = advance(firstRead, snapshot(listOf(first, second)), allowed = false)
    assertTrue(posted.isEmpty())
    val recovered = advance(blocked, snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
    advance(recovered, snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  @Test fun primingDedicatedSourceDoesNotAcknowledgePendingAgendaHomework() {
    val agenda = homework("agenda-A1").copy(source = HomeworkSource.AGENDA)
    val dedicated = homework("H1")
    val firstRead = advance(snapshot(emptyList(), dedicated = null), snapshot(listOf(agenda, dedicated)), allowed = false)
    advance(firstRead, snapshot(listOf(agenda, dedicated)), allowed = true)
    assertEquals(listOf("agenda-A1"), posted)
  }

  @Test fun firstSourceSnapshotIsRememberedEvenWhenMergedCacheLagsBehind() {
    val first = homework("H1")
    val second = homework("H2")
    val lagged = advance(snapshot(emptyList(), dedicated = null), snapshot(emptyList(), listOf(first)), allowed = false)
    advance(lagged, snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  @Test fun corruptSourceMarkerIsPrimedWithoutAnnouncingItsHistory() {
    val first = homework("H1")
    val invalid = snapshot(emptyList()).copy(dedicatedHomeworks = "invalid")
    advance(invalid, snapshot(listOf(first)), allowed = true)
    assertTrue(posted.isEmpty())
  }

  @Test fun aKnownEmptyDedicatedSourceDoesNotifyItsFirstLaterAddition() {
    advance(snapshot(emptyList()), snapshot(listOf(homework("H1"))), allowed = true)
    assertEquals(listOf("H1"), posted)
  }

  @Test fun unknownMergedCacheCanCatchUpWithoutLosingNewDedicatedItemsOrAnnouncingAgendaHistory() {
    val first = homework("H1")
    val second = homework("H2")
    val agendaHistory = homework("agenda-history").copy(source = HomeworkSource.AGENDA)
    val sourceOnly = SyncSnapshotPayloads(dedicatedHomeworks = Json.encodeToString(listOf(first)))
    val observed = advance(SyncSnapshotPayloads(), sourceOnly, allowed = false)
    advance(observed, snapshot(listOf(agendaHistory, first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  @Test fun corruptMergedCacheDoesNotEraseTheKnownDedicatedSourceBaseline() {
    val first = homework("H1")
    val second = homework("H2")
    val previous = snapshot(listOf(first)).copy(homeworks = "corrupt")
    advance(previous, snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  @Test fun repeatedSourceOnlyReadsRetainTheFirstBaselineUntilMergedItemsCanBeDelivered() {
    val first = homework("H1")
    val second = homework("H2")
    val firstRead = advance(SyncSnapshotPayloads(), SyncSnapshotPayloads(dedicatedHomeworks = Json.encodeToString(listOf(first))), allowed = false)
    val secondRead = advance(firstRead, SyncSnapshotPayloads(dedicatedHomeworks = Json.encodeToString(listOf(first, second))), allowed = false)
    advance(secondRead, snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  @Test fun successfulNoOpOnLaggedMergedDataCannotTurnFirstSourceHistoryIntoANewAlert() {
    val first = homework("H1")
    val second = homework("H2")
    val lagged = advance(snapshot(emptyList(), dedicated = null), snapshot(emptyList(), listOf(first)), allowed = true)
    advance(lagged, snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  @Test fun corruptMergedCheckpointDoesNotReplayDedicatedItemsAlreadyDelivered() {
    val first = homework("H1")
    val second = homework("H2")
    val delivered = advance(snapshot(listOf(first)), snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
    advance(delivered.copy(homeworks = "corrupt"), snapshot(listOf(first, second)), allowed = true)
    assertEquals(listOf("H2"), posted)
  }

  private fun advance(previous: SyncSnapshotPayloads, current: SyncSnapshotPayloads, allowed: Boolean) =
    advanceHomeworkNotificationCheckpoint(previous, current, categoryEnabled = true, canDeliver = allowed) { before, after, primed ->
      posted += homeworksToNotify(before, after, primed, agendaChannelOn = false).map(Homework::id)
      true
    }

  private fun snapshot(items: List<Homework>, dedicated: List<Homework>? = items.filter { it.source == HomeworkSource.DEDICATED }) =
    SyncSnapshotPayloads(homeworks = Json.encodeToString(items), dedicatedHomeworks = dedicated?.let { Json.encodeToString(it) })

  private fun homework(id: String) = Homework(id, "Matematica", "Esercizi $id", "2026-11-18", source = HomeworkSource.DEDICATED)
}
