package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real WorkManager KEEP admission and the production action, with a suspended final repaint. */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshKeepRaceTest {
  @Test fun lateTapDuringFinalRepaintCannotReviveTheClearedRefreshingFlag() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val gate = RefreshGate()
    val executor = Executors.newSingleThreadExecutor()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
      .setExecutor(executor)
      .setWorkerFactory(object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
          if (workerClassName == WidgetRefreshWorker::class.java.name) GatedRefreshWorker(appContext, workerParameters, gate) else null
      }).build())
    val manager = WorkManager.getInstance(context)
    try {
      WidgetRefreshWorker.enqueue(context)
      assertTrue("worker did not reach final repaint", gate.repaintStarted.await(10, TimeUnit.SECONDS))
      assertFalse(gate.refreshing)
      val original = manager.getWorkInfosForUniqueWork(WidgetRefreshUniqueWorkName).get(10, TimeUnit.SECONDS).single()

      // The worker remains RUNNING in updateAll after its false write. These actions must only
      // enqueue; KEEP rejects them without another true write or cancelling the existing worker.
      repeat(2) {
        RefreshWidgetAction().onAction(context, object : GlanceId {}, actionParametersOf())
      }
      val afterTaps = manager.getWorkInfosForUniqueWork(WidgetRefreshUniqueWorkName).get(10, TimeUnit.SECONDS)
      assertEquals(listOf(original.id), afterTaps.map { it.id })
      assertEquals(1, gate.starts.get())
      assertFalse(gate.refreshing)

      gate.finishRepaint.complete(Unit)
      val completed = withTimeout(10_000) {
        manager.getWorkInfoByIdFlow(original.id).filterNotNull().first { it.state.isFinished }
      }
      assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, completed.state)
      assertFalse(gate.refreshing)
    } finally {
      gate.finishRepaint.complete(Unit)
      manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
      WorkManagerTestInitHelper.closeWorkDatabase()
      executor.shutdownNow()
    }
  }
}

private class RefreshGate {
  @Volatile var refreshing = false
  val starts = AtomicInteger()
  val repaintStarted = CountDownLatch(1)
  val finishRepaint = CompletableDeferred<Unit>()
}

private class GatedRefreshWorker(context: Context, parameters: WorkerParameters, private val gate: RefreshGate) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    completeWidgetRefresh(
      start = { gate.starts.incrementAndGet(); gate.refreshing = true },
      refresh = { kotlin.Result.success(Unit) },
      finish = {
        gate.refreshing = false
        gate.repaintStarted.countDown()
        gate.finishRepaint.await()
      },
    )
    return Result.success()
  }
}
