package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidVividCard
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors

/**
 * Un voto come superficie intera del proprio colore.
 *
 * E' il posto dove la regola "il tono sta sulla piastrella, mai sullo sfondo" NON si applica: un
 * voto non e' una riga in un gruppo, e' un fatto che sta da solo, e il suo colore e' l'informazione
 * principale che porta. La fascia viene da [gradeBand].
 *
 * **La card non si muove, e non deve.** C'e' stato un momento in cui la fascia piu' alta aveva una
 * banda di luce che la attraversava: su una superficie di questa taglia non si leggeva come pregio,
 * si leggeva come un difetto di disegno. Il voto si distingue per il colore che ha, non per quanto
 * si agita. Se torna la tentazione, l'effetto esiste ancora nell'engine (`FluidVividEffect`) e la
 * risposta e' comunque no.
 *
 * Un voto non numerico non ha una fascia e resta su una superficie neutra: colorare "N.C." di verde
 * o di rosso direbbe una cosa che il registro non ha detto.
 */
@Composable
fun GradeCard(
  valueLabel: String,
  numericValue: Double?,
  subject: String,
  date: String,
  type: String,
  modifier: Modifier = Modifier,
  meta: String? = null,
  unseen: Boolean = false,
  edited: Boolean = false,
  compact: Boolean = false,
  onClick: (() -> Unit)? = null,
  contextActions: (() -> List<FluidContextAction>)? = null,
) {
  val band = gradeBand(numericValue)
  val colors = if (band != null) {
    gradeVividColors(band)
  } else {
    FluidVividColors(
      start = MaterialTheme.colorScheme.surfaceContainerHigh,
      end = MaterialTheme.colorScheme.surfaceContainerHigh,
      content = MaterialTheme.colorScheme.onSurface,
    )
  }
  FluidVividCard(
    colors = colors,
    modifier = modifier,
    onClick = onClick,
    contextActions = contextActions,
    contentPadding = if (compact) PaddingValues(14.dp) else PaddingValues(horizontal = 18.dp, vertical = 16.dp),
  ) {
    if (compact) {
      CompactGradeContent(
        valueLabel = valueLabel,
        subject = subject,
        date = date,
        unseen = unseen,
        edited = edited,
      )
    } else {
      FullGradeContent(
        valueLabel = valueLabel,
        subject = subject,
        date = date,
        type = type,
        meta = meta,
        unseen = unseen,
        edited = edited,
      )
    }
  }
}

@Composable
private fun FullGradeContent(
  valueLabel: String,
  subject: String,
  date: String,
  type: String,
  meta: String?,
  unseen: Boolean,
  edited: Boolean,
) {
  val content = LocalContentColor.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = date.uppercase(),
        style = FluidTextStyles.uppercaseCaption,
        color = content.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subject,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = type,
        style = MaterialTheme.typography.bodySmall,
        color = content.copy(alpha = 0.78f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!meta.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = meta,
          style = MaterialTheme.typography.bodySmall,
          color = content.copy(alpha = 0.66f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Text(
        text = valueLabel,
        style = FluidTextStyles.largeNumeric,
        maxLines = 1,
      )
      GradeCardFlag(unseen = unseen, edited = edited)
    }
  }
}

@Composable
private fun CompactGradeContent(
  valueLabel: String,
  subject: String,
  date: String,
  unseen: Boolean,
  edited: Boolean,
) {
  val content = LocalContentColor.current
  Column(
    modifier = Modifier.widthIn(min = 128.dp, max = 168.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        text = valueLabel,
        style = FluidTextStyles.largeNumeric,
        maxLines = 1,
      )
      GradeCardFlag(unseen = unseen, edited = edited)
    }
    Text(
      text = subject,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = date,
      style = MaterialTheme.typography.labelSmall,
      color = content.copy(alpha = 0.72f),
      maxLines = 1,
    )
  }
}

/** Il segnale di stato del voto: la stessa capsula on-color di ogni superficie satura. */
@Composable
private fun GradeCardFlag(unseen: Boolean, edited: Boolean) {
  val label = when {
    unseen -> "NUOVO"
    edited -> "MODIFICATO"
    else -> return
  }
  VividBadge(label = label)
}
