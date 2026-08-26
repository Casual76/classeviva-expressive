package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
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
 * **Tre livelli di testo e non cinque.** La versione precedente impilava data, materia, tipo e
 * giudizio in quattro corpi quasi uguali a opacita' quasi uguali: una poltiglia in cui l'occhio non
 * sapeva dove posarsi. Ora c'e' il numero, cosa riguarda, e una riga sola che lo colloca nel tempo.
 *
 * La stessa card serve a un voto e alla media di una materia: sono la stessa cosa — un numero, cosa
 * riguarda, e da dove viene — e meritano la stessa forma.
 *
 * Un valore non numerico non ha una fascia e resta su una superficie neutra: colorare "N.C." di
 * verde o di rosso direbbe una cosa che il registro non ha detto.
 */
@Composable
fun GradeCard(
  valueLabel: String,
  numericValue: Double?,
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  meta: String? = null,
  unseen: Boolean = false,
  edited: Boolean = false,
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
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title.asReadableSubject(),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = valueLabel,
          style = FluidTextStyles.largeNumeric,
          maxLines = 1,
        )
        GradeCardFlag(unseen = unseen, edited = edited)
      }
    }
    if (!meta.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = LocalContentColor.current.copy(alpha = 0.72f),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
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

/**
 * Il nome di una materia, smesso di urlare.
 *
 * Il registro le manda tutte in maiuscolo ("SCIENZE NATURALI (BIOLOGIA, CHIMICA, SCIENZE DELLA
 * TERRA)"), che su una card larga come questa e' una riga di lettere maiuscole lunga due righe: non
 * si legge, si decifra. La conversione avviene solo se **non c'e' una sola minuscola** in tutta la
 * stringa, cosi' un nome gia' scritto bene non viene toccato e nessun acronimo isolato viene
 * rovinato per sbaglio.
 */
internal fun String.asReadableSubject(): String {
  if (isBlank()) return this
  if (any { it.isLowerCase() }) return this
  val lowered = lowercase()
  val firstLetter = lowered.indexOfFirst { it.isLetter() }
  if (firstLetter < 0) return lowered
  return lowered.substring(0, firstLetter) +
    lowered[firstLetter].uppercaseChar() +
    lowered.substring(firstLetter + 1)
}
