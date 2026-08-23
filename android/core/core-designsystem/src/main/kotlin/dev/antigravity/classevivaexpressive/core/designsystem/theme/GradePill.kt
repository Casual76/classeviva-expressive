package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * Un voto, con il colore che gli spetta.
 *
 * Vive qui e non nell'engine perche' sa cos'e' la sufficienza, e l'engine non deve saperlo: la
 * soglia del sei e il gradino a cinque sono una regola della scuola italiana, non di un design
 * system. Quello che arriva dall'engine e' la forma - [FluidStatusBadge] - e la scala di toni.
 */
@Composable
fun GradePill(
  value: String,
  numericValue: Double? = null,
  modifier: Modifier = Modifier,
) {
  FluidStatusBadge(label = value, modifier = modifier, tone = gradeTone(numericValue))
}

/**
 * Da voto a tono.
 *
 * Il gradino intermedio esiste apposta: un cinque e mezzo non e' un tre, e colorarli uguale toglie
 * l'unica informazione che quella riga sta dando a colpo d'occhio.
 */
fun gradeTone(score: Double?): FluidTone = when {
  score == null -> FluidTone.Neutral
  score >= 6.0 -> FluidTone.Success
  score >= 5.0 -> FluidTone.Warning
  else -> FluidTone.Danger
}
