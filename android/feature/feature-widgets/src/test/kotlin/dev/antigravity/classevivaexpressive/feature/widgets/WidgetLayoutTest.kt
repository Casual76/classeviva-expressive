package dev.antigravity.classevivaexpressive.feature.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout budget is the one part of the widget a test can hold on to: everything below it is
 * RemoteViews, and the host silently truncates whatever does not fit rather than failing.
 */
class WidgetLayoutTest {

  @Test
  fun `smallest cell keeps one row and drops everything optional`() {
    val layout = resolveWidgetLayout(DpSize(180.dp, 110.dp), hasCounters = true)

    assertTrue(layout.compact)
    assertEquals(1, layout.rowLimit)
    assertFalse("counters do not fit next to a row here", layout.showCounters)
    assertFalse(layout.showSyncLine)
  }

  @Test
  fun `a short but wide cell is still compact`() {
    val layout = resolveWidgetLayout(DpSize(260.dp, 110.dp), hasCounters = true)

    assertTrue("height decides compactness as much as width does", layout.compact)
    assertEquals(1, layout.rowLimit)
  }

  @Test
  fun `counters take their space from the rows`() {
    val withCounters = resolveWidgetLayout(DpSize(180.dp, 180.dp), hasCounters = true)
    val withoutCounters = resolveWidgetLayout(DpSize(180.dp, 180.dp), hasCounters = false)

    assertTrue(withCounters.showCounters)
    assertFalse(withoutCounters.showCounters)
    assertTrue(
      "nothing unread means the rows get the space back",
      withoutCounters.rowLimit > withCounters.rowLimit,
    )
  }

  @Test
  fun `a full-size cell shows the sync line, rows and counters`() {
    val layout = resolveWidgetLayout(DpSize(300.dp, 240.dp), hasCounters = true)

    assertFalse(layout.compact)
    assertTrue(layout.showSyncLine)
    assertTrue(layout.showCounters)
    assertEquals(2, layout.rowLimit)
  }

  @Test
  fun `a taller cell buys more rows`() {
    val tall = resolveWidgetLayout(DpSize(300.dp, 300.dp), hasCounters = true)
    val standard = resolveWidgetLayout(DpSize(300.dp, 240.dp), hasCounters = true)

    assertTrue(tall.rowLimit > standard.rowLimit)
  }

  @Test
  fun `the list stops being glanceable past four rows`() {
    val layout = resolveWidgetLayout(DpSize(300.dp, 900.dp), hasCounters = true)

    assertEquals(WidgetMetrics.MaxRows, layout.rowLimit)
  }
}
