package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

enum class ExpressiveTone {
  Primary,
  Success,
  Warning,
  Danger,
  Info,
  Neutral,
}

@Composable
fun ExpressiveTopHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onBack != null) {
          IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
          }
        }
        Text(
          text = title,
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = actions,
      )
    }
    subtitle?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ExpressiveAccentLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    modifier = modifier,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
fun ExpressivePillTabs(
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEach { option ->
      val isSelected = option == selected
      Surface(
        modifier = Modifier
          .clip(RoundedCornerShape(18.dp))
          .clickable { onSelect(option) },
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Text(
          text = option,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyLarge,
          color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
fun ExpressiveEditorialCard(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.surfaceContainer,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = color,
    contentColor = contentColor,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      content = content,
    )
  }
}

@Composable
fun ExpressiveMiniChart(
  points: List<Float>,
  color: Color,
  modifier: Modifier = Modifier,
  fillAlpha: Float = 0.28f,
  threshold: Float? = null,
) {
  val centerColor = MaterialTheme.colorScheme.surface
  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val thresholdColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(136.dp)
      .then(modifier),
  ) {
    if (points.isEmpty()) return@Canvas

    val safeMax = max(points.maxOrNull() ?: 1f, 1f)
    val safeMin = min(points.minOrNull() ?: 0f, threshold ?: 0f)
    val range = max(safeMax - safeMin, 1f)
    val xStep = if (points.size == 1) 0f else size.width / points.lastIndex.coerceAtLeast(1)

    for (index in 0..4) {
      val y = size.height * index / 4f
      drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }

    threshold?.let { level ->
      val y = size.height - ((level - safeMin) / range) * size.height
      drawLine(thresholdColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
    }

    val linePath = Path()
    val fillPath = Path()
    points.forEachIndexed { index, value ->
      val x = index * xStep
      val normalized = (value - safeMin) / range
      val y = size.height - normalized * size.height
      if (index == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }
    fillPath.lineTo(size.width, size.height)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(
        colors = listOf(color.copy(alpha = fillAlpha), color.copy(alpha = 0.02f)),
      ),
    )
    drawPath(
      path = linePath,
      color = color,
      style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(12f)),
    )
    points.forEachIndexed { index, value ->
      val x = index * xStep
      val normalized = (value - safeMin) / range
      val y = size.height - normalized * size.height
      drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
      drawCircle(color = centerColor, radius = 2.dp.toPx(), center = Offset(x, y))
    }
  }
}

@Composable
fun StatusBadge(
  label: String,
  modifier: Modifier = Modifier,
  tone: ExpressiveTone = ExpressiveTone.Neutral,
) {
  val colors = toneColors(tone)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = colors.container,
    contentColor = colors.content,
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
fun MetricTile(
  label: String,
  value: String,
  detail: String,
  modifier: Modifier = Modifier,
  tone: ExpressiveTone = ExpressiveTone.Neutral,
) {
  val colors = toneColors(tone)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, colors.container.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.content)
      Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun RegisterListRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  eyebrow: String? = null,
  meta: String? = null,
  tone: ExpressiveTone = ExpressiveTone.Neutral,
  badge: (@Composable () -> Unit)? = null,
  leading: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  val colors = toneColors(tone)
  Surface(
    modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, colors.container.copy(alpha = 0.32f)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      leading?.invoke()
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        eyebrow?.let {
          Text(it, style = MaterialTheme.typography.labelSmall, color = colors.content, fontWeight = FontWeight.Bold)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        meta?.takeIf { it.isNotBlank() }?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      badge?.invoke()
    }
  }
}

@Composable
fun GradePill(
  value: String,
  numericValue: Double? = null,
  modifier: Modifier = Modifier,
) {
  StatusBadge(label = value, modifier = modifier, tone = gradeTone(numericValue))
}

fun gradeTone(score: Double?): ExpressiveTone {
  return when {
    score == null -> ExpressiveTone.Neutral
    score >= 6.0 -> ExpressiveTone.Success
    score >= 5.0 -> ExpressiveTone.Warning
    else -> ExpressiveTone.Danger
  }
}

private data class ToneColors(
  val container: Color,
  val content: Color,
)

@Composable
private fun toneColors(tone: ExpressiveTone): ToneColors {
  return when (tone) {
    ExpressiveTone.Primary -> ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
    ExpressiveTone.Success -> ToneColors(Color(0x1F2E7D32), Color(0xFF2E7D32))
    ExpressiveTone.Warning -> ToneColors(Color(0x1FFF8F00), Color(0xFFB26A00))
    ExpressiveTone.Danger -> ToneColors(Color(0x1FC62828), Color(0xFFC62828))
    ExpressiveTone.Info -> ToneColors(Color(0x1F1565C0), Color(0xFF1565C0))
    ExpressiveTone.Neutral -> ToneColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
