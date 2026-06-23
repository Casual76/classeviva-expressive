package dev.antigravity.classevivaexpressive.feature.widgets

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshWorkerTest {
  @Test
  fun createWidgetRefreshWorkRequestTargetsRefreshWorker() {
    val request = createWidgetRefreshWorkRequest()

    assertEquals(WidgetRefreshWorker::class.java.name, request.workSpec.workerClassName)
  }

  @Test
  fun refreshWorkUsesUniqueReplacePolicy() {
    assertEquals("classeviva-expressive-widget-refresh", WidgetRefreshUniqueWorkName)
    assertEquals(ExistingWorkPolicy.REPLACE, WidgetRefreshExistingWorkPolicy)
  }
}
