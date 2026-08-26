package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors
import kotlin.math.abs

/**
 * Le superfici vivide che nascono dalla palette invece che da colori scritti a mano.
 *
 * Sono le stesse due ricette che l'engine usa per le fasce di sezione (`fluidHeroBandColors`):
 * l'accento pieno che scivola verso la famiglia successiva, e l'errore che scivola appena verso il
 * caldo. Vivono qui e non nell'engine perche' quella funzione e' `internal` — quando ci sara' una
 * release dell'engine per altri motivi, la cosa giusta e' renderla pubblica e cancellare questo
 * file. Fino ad allora: nessuna costante nuova, solo ruoli di [MaterialTheme.colorScheme].
 */

/** Il colore dell'app come superficie: quello che si usa per "il fatto che conta adesso". */
fun accentVividColors(primary: Color, secondary: Color): FluidVividColors =
  FluidVividColors.from(primary, lerp(primary, secondary, 0.35f))

@Composable
fun accentVividColors(): FluidVividColors = with(MaterialTheme.colorScheme) {
  accentVividColors(primary, secondary)
}

/**
 * L'urgenza come superficie.
 *
 * La deriva verso il terziario e' corta di proposito: a 0.35 il capo lontano si schiariva abbastanza
 * da lasciare entrambi i contenuti sotto la soglia leggibile sui preset vicini all'arancio.
 */
fun dangerVividColors(error: Color, tertiary: Color): FluidVividColors =
  FluidVividColors.from(error, lerp(error, tertiary, 0.15f))

@Composable
fun dangerVividColors(): FluidVividColors = with(MaterialTheme.colorScheme) {
  dangerVividColors(error, tertiary)
}

/** Vero quando il tema corrente e' quello scuro, letto dalla superficie e non dal sistema. */
@Composable
internal fun isDarkAppearance(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * La tinta in gradi, 0..360.
 *
 * Su un colore desaturo la tinta non significa niente (e' il rapporto fra canali quasi uguali), ed
 * e' il motivo per cui chi la usa qui la mette sempre in congiunzione con una distanza per canale:
 * due grigi hanno tinte casuali e distanza minima, due colori pieni no.
 */
internal fun hueDegrees(color: Color): Float {
  val r = color.red
  val g = color.green
  val b = color.blue
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val delta = max - min
  if (delta <= 0f) return 0f
  val hue = when (max) {
    r -> 60f * (((g - b) / delta) % 6f)
    g -> 60f * (((b - r) / delta) + 2f)
    else -> 60f * (((r - g) / delta) + 4f)
  }
  return (hue + 360f) % 360f
}

/** La distanza fra due tinte sul cerchio, 0..180. */
internal fun hueDistanceDegrees(first: Color, second: Color): Float {
  val raw = abs(hueDegrees(first) - hueDegrees(second))
  return if (raw > 180f) 360f - raw else raw
}

/** La distanza L1 per canale in sRGB, 0..3. La stessa metrica gia' usata dai test della palette. */
internal fun channelDistance(first: Color, second: Color): Float =
  abs(first.red - second.red) + abs(first.green - second.green) + abs(first.blue - second.blue)
