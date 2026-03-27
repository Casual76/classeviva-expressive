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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

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
            Icon(
              imageVector = Icons.Rounded.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
        }
        Text(
          text = title,
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
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
    if (subtitle != null) {
      Text(
        text = subtitle,
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
fun ExpressiveScoreRing(
  valueText: String,
  progress: Float,
  color: Color,
  modifier: Modifier = Modifier,
  size: Dp = 76.dp,
  trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
) {
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = this.size.minDimension * 0.12f
      drawCircle(
        color = trackColor,
        style = Stroke(width = strokeWidth),
      )
      drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * progress.coerceIn(0f, 1f),
        useCenter = false,
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.cornerPathEffect(strokeWidth / 2f)),
      )
    }
    Text(
      text = valueText,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
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
    val xStep = if (points.size == 1) 0f else size.width / (points.lastIndex.coerceAtLeast(1))

    for (index in 0..4) {
      val y = size.height * index / 4f
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
      )
    }

    threshold?.let { level ->
      val y = size.height - ((level - safeMin) / range) * size.height
      drawLine(
        color = thresholdColor,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.5.dp.toPx(),
      )
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
        colors = listOf(
          color.copy(alpha = fillAlpha),
          color.copy(alpha = 0.02f),
        ),
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
      drawCircle(
        color = color,
        radius = 4.dp.toPx(),
        center = Offset(x, y),
      )
      drawCircle(
        color = centerColor,
        radius = 2.dp.toPx(),
        center = Offset(x, y),
      )
    }
  }
}

@Composable
fun ExpressiveStatusDot(
  color: Color,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(14.dp)
      .clip(CircleShape)
      .background(color),
  )
}

@Composable
fun ExpressiveColorTile(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  detail: String? = null,
  badge: String? = null,
  color: Color,
  onClick: (() -> Unit)? = null,
) {
  Surface(
    modifier = if (onClick != null) {
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
    } else {
      modifier.fillMaxWidth()
    },
    shape = RoundedCornerShape(18.dp),
    color = color,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      if (badge != null) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.96f)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = badge,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
          )
        }
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.96f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
fun ExpressiveSimpleListRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  meta: String? = null,
  trailing: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  Row(
    modifier = if (onClick != null) {
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 6.dp)
    } else {
      modifier
        .fillMaxWidth()
        .padding(horizontal = 2.dp, vertical = 6.dp)
    },
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      if (meta != null) {
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f),
      )
      }
    }
    trailing?.invoke()
  }
}

@Composable
fun ExpressiveEnvelopeBadge(
  color: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(width = 40.dp, height = 32.dp),
    shape = RoundedCornerShape(10.dp),
    color = color,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
      val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
        moveTo(0f, 0f)
        lineTo(size.width / 2f, size.height / 2f)
        lineTo(size.width, 0f)
      }
      drawPath(path = path, color = Color.Black.copy(alpha = 0.74f), style = Stroke(width = 2.dp.toPx()))
    }
  }
}

fun scoreColor(score: Double?): Color {
  return when {
    score == null -> Color(0xFF8A8D98)
    score >= 8.0 -> Color(0xFF4CAF50)
    score >= 6.0 -> Color(0xFFFFC83D)
    else -> Color(0xFFFF4338)
  }
}

fun subjectPalette(subject: String): Color {
  val palette = listOf(
    Color(0xFF9C27B0),
    Color(0xFFFF9800),
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFF3F51B5),
    Color(0xFF69F0AE),
    Color(0xFFFF4338),
    Color(0xFF26C6DA),
  )
  val index = subject.uppercase().fold(0) { acc, char -> acc + char.code }.mod(palette.size)
  return palette[index]
}

fun eventColor(label: String): Color {
  val normalized = label.uppercase()
  return when {
    normalized.contains("VERIF") || normalized.contains("ASSESS") -> Color(0xFFFF4338)
    normalized.contains("COMPIT") || normalized.contains("HOMEWORK") -> Color(0xFF4CAF50)
    normalized.contains("CUSTOM") || normalized.contains("EVENT") -> Color(0xFFFFC83D)
    else -> Color(0xFF4CAF50)
  }
}
