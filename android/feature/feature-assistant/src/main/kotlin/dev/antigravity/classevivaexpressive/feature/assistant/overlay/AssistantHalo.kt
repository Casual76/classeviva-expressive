package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlin.math.PI
import kotlin.math.sin

/** Come si muove l'aureola: ascolta (segue la voce), lavora (pulsa), scrive (onda), tace. */
enum class HaloMood { HIDDEN, LISTENING, WORKING, WRITING, DONE, ERROR }

/**
 * L'aureola in cima allo schermo: quattro macchie di colore che scorrono lungo il bordo alto e
 * sfumano verso il basso. In ascolto l'ampiezza segue il livello del microfono; al lavoro pulsa
 * lenta; mentre scrive scorre come un'onda. Con le animazioni ridotte resta ferma e sfuma
 * soltanto. Solo gradienti: niente shader, cosi' gira uguale sui device deboli.
 */
@Composable
fun AssistantHalo(
  mood: HaloMood,
  level: Float,
  accent: Color,
  secondary: Color,
  tertiary: Color,
  modifier: Modifier = Modifier,
  height: Dp = 132.dp,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val visible = mood != HaloMood.HIDDEN
  val presence by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = spring(dampingRatio = FluidMotion.DampingStandard, stiffness = FluidMotion.ResponseSmooth),
    label = "haloPresence",
  )
  // Attacco veloce e rilascio lento, come un indicatore di livello vero.
  val target = if (mood == HaloMood.LISTENING) 0.30f + level * 0.70f else 0.55f
  val amplitude = remember { Animatable(0.55f) }
  LaunchedEffect(target, mood, reducedMotion) {
    if (reducedMotion) {
      amplitude.snapTo(target)
      return@LaunchedEffect
    }
    val rising = target > amplitude.value
    amplitude.animateTo(
      targetValue = target,
      animationSpec = spring(
        dampingRatio = FluidMotion.DampingStandard,
        stiffness = if (rising) HaloAttackStiffness else HaloReleaseStiffness,
      ),
    )
  }
  val amplitudeValue = amplitude.value
  var time by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(visible, reducedMotion, mood) {
    if (!visible || reducedMotion) return@LaunchedEffect
    val speed = when (mood) {
      HaloMood.WRITING -> 1.6f
      HaloMood.LISTENING -> 1.1f
      HaloMood.WORKING -> 0.6f
      else -> 0.3f
    }
    var last = 0L
    while (true) {
      withFrameNanos { now ->
        if (last != 0L) time += (now - last) / 1_000_000_000f * speed
        last = now
      }
    }
  }
  if (presence <= 0.001f) return
  // I colori dell'app, non colori inventati: l'accento e i suoi due poli, che sono quelli che il
  // tema gia' usa per le sette sezioni. L'errore e' l'unico che prende il rosso del tema.
  val targetColours = remember(accent, secondary, tertiary, mood) { haloColours(accent, secondary, tertiary, mood) }
  val colours = targetColours.mapIndexed { index, colour ->
    animateColorAsState(targetValue = colour, animationSpec = FluidMotion.smooth(), label = "haloColour$index").value
  }
  Canvas(modifier.fillMaxWidth().height(height)) {
    drawHalo(time, amplitudeValue, presence, colours)
  }
}

private fun haloColours(accent: Color, secondary: Color, tertiary: Color, mood: HaloMood): List<Color> = when (mood) {
  HaloMood.ERROR -> listOf(Color(0xFFFF5A5F), Color(0xFFFF9A3D), Color(0xFFFF5A5F), Color(0xFFFFC46B))
  HaloMood.DONE -> listOf(accent, secondary.copy(alpha = 0.9f), accent.copy(alpha = 0.9f), tertiary.copy(alpha = 0.8f))
  else -> listOf(accent, secondary, tertiary, accent.copy(alpha = 0.85f))
}

private fun DrawScope.drawHalo(time: Float, amplitude: Float, presence: Float, colours: List<Color>) {
  val width = size.width
  val height = size.height
  val blobs = colours.size
  for (i in 0 until blobs) {
    val phase = time * (0.8f + i * 0.17f) + i * (PI.toFloat() * 2f / blobs)
    val x = width * (0.5f + 0.42f * sin(phase))
    val y = -height * 0.35f + height * 0.18f * sin(phase * 0.7f + i)
    val radius = width * (0.28f + 0.14f * amplitude * (0.6f + 0.4f * sin(phase * 1.3f)))
    val alpha = (0.55f + 0.35f * amplitude) * presence
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(colours[i].copy(alpha = alpha), colours[i].copy(alpha = 0f)),
        center = Offset(x, y),
        radius = radius,
      ),
      radius = radius,
      center = Offset(x, y),
    )
  }
}

/** L'attacco: la voce sale subito, come su un indicatore di livello. */
private const val HaloAttackStiffness = 1_400f

/** Il rilascio: scende piano, cosi' fra due sillabe l'aureola non si spegne. */
private const val HaloReleaseStiffness = 180f
