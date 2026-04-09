package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveTopHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  TopAppBar(
    modifier = modifier.fillMaxWidth(),
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    navigationIcon = {
      if (onBack != null) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      }
    },
    actions = actions,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color.Transparent,
      scrolledContainerColor = Color.Transparent,
      navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
      actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      titleContentColor = MaterialTheme.colorScheme.onSurface,
    ),
    windowInsets = WindowInsets(0, 0, 0, 0),
  )
}

@Composable
fun ExpressiveAccentLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text.uppercase(),
    modifier = modifier,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
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
  SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth(),
  ) {
    options.forEachIndexed { index, option ->
      SegmentedButton(
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        selected = option == selected,
        onClick = { onSelect(option) },
        label = {
          Text(
            text = option,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
      )
    }
  }
}

@Composable
fun ExpressiveEditorialCard(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable ColumnScope.() -> Unit,
) {
  ElevatedCard(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.elevatedCardColors(
      containerColor = color,
      contentColor = contentColor,
    ),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
  SuggestionChip(
    modifier = modifier,
    onClick = {},
    label = {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
    },
    colors = SuggestionChipDefaults.suggestionChipColors(
      containerColor = colors.container,
      labelColor = colors.content,
      disabledContainerColor = colors.container,
      disabledLabelColor = colors.content,
    ),
    enabled = false,
  )
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
  ElevatedCard(
    modifier = modifier,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.container),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = colors.content,
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
  OutlinedCard(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (onClick != null) {
          Modifier.clickable(onClick = onClick)
        } else {
          Modifier
        },
      ),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, colors.container.copy(alpha = 0.65f)),
  ) {
    ListItem(
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      overlineContent = eyebrow?.let {
        {
          Text(
            text = it,
            color = colors.content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      },
      headlineContent = {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      },
      supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          meta?.takeIf { it.isNotBlank() }?.let {
            Text(
              text = it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      leadingContent = leading,
      trailingContent = badge?.let {
        {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            it()
          }
        }
      },
    )
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
    ExpressiveTone.Success -> ToneColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    ExpressiveTone.Warning -> ToneColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    ExpressiveTone.Danger -> ToneColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    ExpressiveTone.Info -> ToneColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.primary)
    ExpressiveTone.Neutral -> ToneColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
