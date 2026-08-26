package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors

/**
 * Le fasce di un voto, per il vocabolario vivido.
 *
 * Vive qui e non nell'engine per la stessa ragione di [gradeTone]: la soglia del sei, il gradino a
 * cinque e il sette e mezzo dell'eccellenza sono regole della scuola italiana. Quattro fasce e non
 * tre perche' un otto non e' un sei: sopra il 7,5 il verde diventa smeraldo e la card si guadagna
 * lo sheen — e' l'unico elemento dell'app che si muove da fermo, e se lo deve meritare.
 *
 * [gradeTone] resta a tre gradini per pill e piastrelle nelle liste raggruppate: la quarta fascia
 * esiste solo dove il voto e' una superficie intera.
 */
enum class GradeBand {
  Insufficiente,
  Recuperabile,
  Sufficiente,
  Eccellente,
}

fun gradeBand(score: Double?): GradeBand? = when {
  score == null -> null
  score >= 7.5 -> GradeBand.Eccellente
  score >= 6.0 -> GradeBand.Sufficiente
  score >= 5.0 -> GradeBand.Recuperabile
  else -> GradeBand.Insufficiente
}

/**
 * I gradienti delle fasce. Coppie chiaro/scuro esplicite come ogni colore fisso del design system;
 * il contenuto lo sceglie [FluidVividColors.from] sul capo peggiore, mai a occhio.
 */
@Composable
fun gradeVividColors(band: GradeBand): FluidVividColors {
  // Come il vetro: chiaro e scuro si leggono dalla superficie risolta, non dal sistema — l'app ha
  // un suo interruttore di tema.
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  return gradeVividColors(band, isDark)
}

/** La stessa risoluzione come funzione pura, cosi' contrasto e coppie si verificano in un test. */
fun gradeVividColors(band: GradeBand, isDark: Boolean): FluidVividColors = when (band) {
  GradeBand.Insufficiente ->
    if (isDark) FluidVividColors.from(Color(0xFFFF453A), Color(0xFFFF375F))
    else FluidVividColors.from(Color(0xFFFF3B30), Color(0xFFFF2D55))

  GradeBand.Recuperabile ->
    if (isDark) FluidVividColors.from(Color(0xFFFF9F0A), Color(0xFFFFB340))
    else FluidVividColors.from(Color(0xFFFF9500), Color(0xFFFF6B00))

  GradeBand.Sufficiente ->
    if (isDark) FluidVividColors.from(Color(0xFF30D158), Color(0xFF63E6E2))
    else FluidVividColors.from(Color(0xFF34C759), Color(0xFF00C7BE))

  GradeBand.Eccellente ->
    if (isDark) FluidVividColors.from(Color(0xFF30D158), Color(0xFF40E0D0))
    else FluidVividColors.from(Color(0xFF00A86B), Color(0xFF00C7BE))
}
