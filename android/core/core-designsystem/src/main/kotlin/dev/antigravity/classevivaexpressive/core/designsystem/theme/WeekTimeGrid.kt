package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import kotlin.math.max
import kotlin.math.roundToInt

/** Un intervallo della giornata in minuti dalla mezzanotte, chiuso all'inizio e aperto alla fine. */
@Immutable
data class MinuteSpan(val start: Int, val end: Int) {
  val minutes: Int get() = end - start

  fun overlaps(other: MinuteSpan): Boolean = start < other.end && other.start < end
}

/** Sotto: il contesto, largo quanto la colonna. Sopra: le cose da guardare, affiancate se si sovrappongono. */
enum class TimeGridLayer { Background, Foreground }

@Immutable
data class TimeGridEvent<out T>(
  val key: Any,
  /** L'indice della colonna, nell'ordine di `days`. */
  val day: Int,
  val span: MinuteSpan,
  val layer: TimeGridLayer = TimeGridLayer.Foreground,
  val value: T,
)

@Immutable
data class TimeGridDay(val label: String, val isToday: Boolean = false)

/** Quanto spazio ha una cella: chi la disegna decide cosa ci sta. */
@Immutable
data class TimeGridCell(val height: Dp, val compact: Boolean)

object WeekTimeGridDefaults {
  val GutterWidth: Dp = 40.dp
  val ColumnSpacing: Dp = 6.dp
  val EventGap: Dp = 3.dp

  /** Una cella piu' bassa di cosi' mostra una riga sola. */
  val CompactBelow: Dp = 60.dp

  /** L'altezza sotto cui un'ora corta non si legge piu', a testo normale. */
  val ReadableEvent: Dp = 44.dp
  val MinMinuteHeight: Dp = 0.9.dp
  val MaxMinuteHeight: Dp = 2.2.dp

  /** La mattina di un liceo, quando non c'e' niente da cui ricavarla. */
  val DefaultRange = MinuteSpan(8 * 60, 14 * 60)
  val Clamp = MinuteSpan(7 * 60, 20 * 60)
}

/**
 * Le ore da mostrare: dall'ora piena prima della prima cosa all'ora piena dopo l'ultima, dentro
 * [clamp]. Una settimana senza niente mostra comunque una mattina, non una griglia di un minuto.
 */
fun timeGridRange(
  spans: Iterable<MinuteSpan>,
  fallback: MinuteSpan = WeekTimeGridDefaults.DefaultRange,
  clamp: MinuteSpan = WeekTimeGridDefaults.Clamp,
): MinuteSpan {
  val list = spans.toList()
  if (list.isEmpty()) return fallback
  val start = (list.minOf { it.start } / 60 * 60).coerceIn(clamp.start, clamp.end - 60)
  val end = ((list.maxOf { it.end } + 59) / 60 * 60).coerceIn(start + 60, clamp.end)
  return MinuteSpan(start, end)
}

@Immutable
data class TimeGridLane(val index: Int, val count: Int)

/**
 * La corsia di ogni intervallo, nello stesso ordine: due cose alla stessa ora stanno affiancate
 * invece che una sopra l'altra. Le corsie contano per gruppo di sovrapposizioni, cosi' una
 * verifica sola alle nove resta larga quanto la colonna anche se alle undici ce ne sono due.
 */
fun timeGridLanes(spans: List<MinuteSpan>): List<TimeGridLane> {
  val result = arrayOfNulls<TimeGridLane>(spans.size)
  val order = spans.indices.sortedWith(compareBy({ spans[it].start }, { -spans[it].end }))
  var cluster = mutableListOf<Pair<Int, Int>>() // indice, corsia
  var laneEnds = mutableListOf<Int>()
  var clusterEnd = Int.MIN_VALUE

  fun close() {
    cluster.forEach { (index, lane) -> result[index] = TimeGridLane(lane, laneEnds.size) }
    cluster = mutableListOf()
    laneEnds = mutableListOf()
  }

  order.forEach { index ->
    val span = spans[index]
    if (span.start >= clusterEnd && cluster.isNotEmpty()) close()
    val lane = laneEnds.indexOfFirst { it <= span.start }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds.add(0) }
    laneEnds[lane] = span.end
    cluster.add(index to lane)
    clusterEnd = if (cluster.size == 1) span.end else max(clusterEnd, span.end)
  }
  close()
  return result.map { it ?: TimeGridLane(0, 1) }
}

/**
 * Quanti dp vale un minuto: quanto serve perche' la giornata stia in [viewport], ma mai tanto poco
 * che l'ora piu' corta non si legga — a testo grande la griglia si allunga e la pagina scorre,
 * invece di stringere le parole.
 */
fun timeGridMinuteHeight(
  viewport: Dp,
  range: MinuteSpan,
  shortestEventMinutes: Int,
  fontScale: Float,
): Dp {
  val fit = viewport / range.minutes.coerceAtLeast(1)
  val readable = WeekTimeGridDefaults.ReadableEvent * fontScale / shortestEventMinutes.coerceAtLeast(1)
  val maxHeight = WeekTimeGridDefaults.MaxMinuteHeight * fontScale.coerceAtLeast(1f)
  return max(fit.value, readable.value).dp
    .coerceAtMost(maxHeight)
    .coerceAtLeast(WeekTimeGridDefaults.MinMinuteHeight)
}

/** [timeGridMinuteHeight] per lo schermo corrente: la giornata sta in quello che resta sotto la testata. */
@Composable
fun rememberTimeGridMinuteHeight(range: MinuteSpan, shortestEventMinutes: Int): Dp {
  val screenHeight = LocalConfiguration.current.screenHeightDp.dp
  val fontScale = LocalDensity.current.fontScale
  return remember(range, shortestEventMinutes, screenHeight, fontScale) {
    timeGridMinuteHeight(
      viewport = (screenHeight - TimeGridChromeHeight).coerceAtLeast(240.dp),
      range = range,
      shortestEventMinutes = shortestEventMinutes,
      fontScale = fontScale,
    )
  }
}

/** Testata della pagina, intestazione dei giorni e barra in basso: quello che la griglia non ha. */
private val TimeGridChromeHeight = 300.dp

/**
 * La settimana sulle ore: una colonna per giorno, le ore a sinistra, ogni cosa alta quanto dura.
 *
 * Un solo elemento a altezza fissa, pensato per stare dentro la pagina che gia' scorre: nessuno
 * scorrimento dentro lo scorrimento. La linea di adesso si legge al disegno, cosi' il minuto che
 * passa ridisegna una riga invece di ricomporre la settimana.
 */
@Composable
fun <T> WeekTimeGrid(
  days: List<TimeGridDay>,
  events: List<TimeGridEvent<T>>,
  range: MinuteSpan,
  minuteHeight: Dp,
  modifier: Modifier = Modifier,
  todayIndex: Int? = null,
  nowMinute: () -> Int? = { null },
  allDay: (@Composable ColumnScope.(day: Int) -> Unit)? = null,
  event: @Composable (TimeGridEvent<T>, TimeGridCell) -> Unit,
) {
  val gutter = WeekTimeGridDefaults.GutterWidth
  val spacing = WeekTimeGridDefaults.ColumnSpacing
  val scheme = MaterialTheme.colorScheme
  val fontScale = LocalDensity.current.fontScale
  val byDay = remember(events, days.size) {
    List(days.size) { day -> events.filter { it.day == day } }
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
      Box(Modifier.width(gutter))
      days.forEach { day ->
        Text(
          text = day.label,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.SemiBold,
          color = if (day.isToday) scheme.primary else scheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
      }
    }
    if (allDay != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        Box(Modifier.width(gutter))
        days.indices.forEach { day ->
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) { allDay(day) }
        }
      }
    }

    val gridLine = scheme.onSurface.copy(alpha = 0.08f)
    val nowColor = scheme.primary
    Row(
      horizontalArrangement = Arrangement.spacedBy(spacing),
      modifier = Modifier
        .fillMaxWidth()
        .height(range.minutes * minuteHeight)
        .drawBehind {
          val left = (gutter + spacing).toPx()
          var hour = (range.start + 59) / 60 * 60
          while (hour < range.end) {
            val y = (hour - range.start) * minuteHeight.toPx()
            drawLine(gridLine, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            hour += 60
          }
        }
        .drawWithContent {
          drawContent()
          val today = todayIndex ?: return@drawWithContent
          val minute = nowMinute() ?: return@drawWithContent
          if (minute < range.start || minute >= range.end || today !in days.indices) return@drawWithContent
          val gutterPx = gutter.toPx()
          val spacingPx = spacing.toPx()
          val columnWidth = (size.width - gutterPx - spacingPx * days.size) / days.size
          val left = gutterPx + spacingPx + today * (columnWidth + spacingPx)
          val y = (minute - range.start) * minuteHeight.toPx()
          drawLine(nowColor, Offset(left, y), Offset(left + columnWidth, y), strokeWidth = 2.dp.toPx())
          drawCircle(nowColor, radius = 4.dp.toPx(), center = Offset(left, y))
        },
    ) {
      Box(Modifier.width(gutter).fillMaxHeight()) {
        var hour = (range.start + 59) / 60 * 60
        while (hour < range.end) {
          Text(
            text = "${hour / 60}:00",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.offset(y = (hour - range.start) * minuteHeight + 2.dp),
          )
          hour += 60
        }
      }
      byDay.forEach { dayEvents ->
        TimeGridDayColumn(
          events = dayEvents,
          range = range,
          minuteHeight = minuteHeight,
          compactBelow = WeekTimeGridDefaults.CompactBelow * fontScale,
          modifier = Modifier.weight(1f).fillMaxHeight(),
          event = event,
        )
      }
    }
  }
}

@Composable
private fun <T> TimeGridDayColumn(
  events: List<TimeGridEvent<T>>,
  range: MinuteSpan,
  minuteHeight: Dp,
  compactBelow: Dp,
  modifier: Modifier,
  event: @Composable (TimeGridEvent<T>, TimeGridCell) -> Unit,
) {
  // Prima lo sfondo, poi quello che gli sta sopra: l'ordine dei figli e' l'ordine di disegno.
  val ordered = remember(events, range) {
    events
      .filter { it.span.end > range.start && it.span.start < range.end }
      .sortedBy { it.layer.ordinal }
  }
  val lanes = remember(ordered) {
    val foreground = ordered.filter { it.layer == TimeGridLayer.Foreground }
    val assigned = timeGridLanes(foreground.map { it.span })
    foreground.zip(assigned).associate { (event, lane) -> event.key to lane }
  }
  val gap = WeekTimeGridDefaults.EventGap
  Layout(
    modifier = modifier,
    content = {
      ordered.forEach { item ->
        key(item.key) {
          val visible = MinuteSpan(max(item.span.start, range.start), minOf(item.span.end, range.end))
          val height = visible.minutes * minuteHeight - gap
          Box { event(item, TimeGridCell(height = height, compact = height < compactBelow)) }
        }
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val minutePx = minuteHeight.toPx()
    val gapPx = gap.roundToPx()
    val placeables = measurables.mapIndexed { index, measurable ->
      val item = ordered[index]
      val lane = lanes[item.key] ?: TimeGridLane(0, 1)
      val laneWidth = (width - gapPx * (lane.count - 1)) / lane.count
      val start = max(item.span.start, range.start)
      val end = minOf(item.span.end, range.end)
      val height = ((end - start) * minutePx).roundToInt() - gapPx
      val placeable = measurable.measure(Constraints.fixed(laneWidth.coerceAtLeast(0), height.coerceAtLeast(0)))
      Triple(placeable, lane.index * (laneWidth + gapPx), ((start - range.start) * minutePx).roundToInt())
    }
    layout(width, constraints.maxHeight) {
      placeables.forEach { (placeable, x, y) -> placeable.place(x, y) }
    }
  }
}
