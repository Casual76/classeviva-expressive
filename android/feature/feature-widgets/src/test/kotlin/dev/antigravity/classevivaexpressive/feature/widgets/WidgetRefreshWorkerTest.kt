package dev.antigravity.classevivaexpressive.feature.widgets

import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshWorkerTest {
  @Test
  fun createWidgetRefreshWorkRequestTargetsRefreshWorker() {
    val request = createWidgetRefreshWorkRequest()

    assertEquals(WidgetRefreshWorker::class.java.name, request.workSpec.workerClassName)
  }

  @Test
  fun repeatedTapsKeepTheAlreadyRunningRefresh() {
    assertEquals("classeviva-expressive-widget-refresh", WidgetRefreshUniqueWorkName)
    assertEquals(ExistingWorkPolicy.KEEP, WidgetRefreshExistingWorkPolicy)
  }

  @Test
  fun cancelledRefreshStillClearsPersistedRefreshingState() = runTest {
    val entered = CompletableDeferred<Unit>()
    var refreshing = true
    val pending = async {
      completeWidgetRefresh(
        refresh = { entered.complete(Unit); awaitCancellation() },
        finish = { refreshing = false },
      )
    }
    entered.await()
    pending.cancelAndJoin()
    assertTrue(pending.isCancelled)
    assertEquals(false, refreshing)
  }

  @Test
  fun thrownRefreshFailureFinishesWithReadableError() = runTest {
    var error: String? = null
    completeWidgetRefresh(
      refresh = { throw IllegalStateException("Connessione non disponibile") },
      finish = { error = it },
    )
    assertEquals("Connessione non disponibile", error)
  }

  @Test
  fun calendarChangesRepaintTheCachedWidget() {
    assertTrue(isWidgetCalendarChange("android.intent.action.DATE_CHANGED"))
    assertTrue(isWidgetCalendarChange("android.intent.action.TIME_SET"))
    assertTrue(isWidgetCalendarChange("android.intent.action.TIMEZONE_CHANGED"))
    assertEquals(false, isWidgetCalendarChange("android.appwidget.action.APPWIDGET_UPDATE"))
  }
}
