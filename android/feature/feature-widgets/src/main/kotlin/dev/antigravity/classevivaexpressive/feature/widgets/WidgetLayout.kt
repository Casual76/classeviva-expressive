package dev.antigravity.classevivaexpressive.feature.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * How much of the overview fits in the cell the launcher handed us.
 *
 * Glance has no measurement pass to fall back on: the widget is turned into RemoteViews and
 * whatever does not fit is simply cut off by the host, mid-row. The size is therefore turned into
 * an explicit budget here — header, rows, counters — and the content asks for exactly what the
 * budget allows.
 */
internal data class WidgetLayout(
  val padding: Dp,
  val compact: Boolean,
  val rowLimit: Int,
  val showSyncLine: Boolean,
  val showCounters: Boolean,
)

internal object WidgetMetrics {
  /** A two-line row: 15sp title over a 12sp subtitle, plus the padding around them. */
  val RowHeight: Dp = 48.dp

  /** One line and a date, for a cell too short to give a row two of them. */
  val CompactRowHeight: Dp = 34.dp

  /** Title over the sync line, alongside the refresh button. */
  val HeaderHeight: Dp = 48.dp
  val CompactHeaderHeight: Dp = 32.dp
  val CountersHeight: Dp = 38.dp
  val Gap: Dp = 8.dp
  val Padding: Dp = 14.dp
  val CompactPadding: Dp = 12.dp

  /** Beyond four rows a home-screen widget stops being glanceable. */
  const val MaxRows: Int = 4
}

/**
 * [hasCounters] is asked for rather than assumed: the counters disappear when there is nothing
 * unread, and the row budget should get that space back instead of leaving a gap.
 */
internal fun resolveWidgetLayout(size: DpSize, hasCounters: Boolean): WidgetLayout {
  val compact = size.width < 240.dp || size.height < 150.dp
  val padding = if (compact) WidgetMetrics.CompactPadding else WidgetMetrics.Padding
  val header = if (compact) WidgetMetrics.CompactHeaderHeight else WidgetMetrics.HeaderHeight
  val rowHeight = if (compact) WidgetMetrics.CompactRowHeight else WidgetMetrics.RowHeight

  var available = size.height - padding * 2 - header - WidgetMetrics.Gap
  val showCounters = hasCounters &&
    available >= rowHeight + WidgetMetrics.Gap + WidgetMetrics.CountersHeight
  if (showCounters) {
    available -= WidgetMetrics.CountersHeight + WidgetMetrics.Gap
  }

  val fitting = if (available <= 0.dp) 0 else (available / rowHeight).toInt()
  // A widget that shows only its header has nothing to say. When even one row does not fit, the
  // first one is drawn anyway and the host trims it — still more useful than an empty card.
  val rowLimit = fitting.coerceIn(if (showCounters) 0 else 1, WidgetMetrics.MaxRows)

  return WidgetLayout(
    padding = padding,
    compact = compact,
    rowLimit = rowLimit,
    showSyncLine = !compact,
    showCounters = showCounters,
  )
}
