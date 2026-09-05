package dev.antigravity.classevivaexpressive.feature.assistant.bar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface

/**
 * Quello che la barra sa dell'assistente, senza conoscerlo: se sta lavorando, cosa fare al tocco
 * (voce) e alla pressione lunga (testo), a chi dire dove sta il tasto (l'origine del morph).
 */
@androidx.compose.runtime.Stable
class AssistantBarState(
  val working: Boolean,
  val onTap: () -> Unit,
  val onLongPress: () -> Unit,
  val onBounds: (Rect) -> Unit,
)

/**
 * Il tasto "Chiedi all'AI" sopra la pillola della navigazione, piegato dallo stesso numero della
 * pillola: aperto e' una capsula ovale con l'icona e la scritta, ripiegato e' un cerchio con la
 * sola icona, largo quanto la pillola ripiegata, che le sta sopra. Tocco = voce, pressione lunga =
 * testo; mentre lavora l'icona respira.
 *
 * Come la barra dell'engine, la piega si legge **in misura e in disegno, mai in composizione**: un
 * fotogramma della piega costa una misura e un disegno, e niente si ricompone.
 */
@Composable
fun AssistantBarButton(
  fold: () -> Float,
  working: Boolean,
  backdrop: GlassBackdropState,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
  onBounds: (Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val openHeightPx = with(density) { AssistantBarButtonDefaults.OpenHeight.roundToPx() }
  val foldedPx = with(density) { FluidFoldingTabBarDefaults.FoldedHeight.roundToPx() }
  val padPx = with(density) { AssistantBarButtonDefaults.HorizontalPadding.roundToPx() }
  val gapPx = with(density) { AssistantBarButtonDefaults.Gap.roundToPx() }
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val pulse = rememberInfiniteTransition(label = "assistantPulse")
  val scale by pulse.animateFloat(
    initialValue = 1f,
    targetValue = 1.18f,
    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    label = "assistantScale",
  )

  Layout(
    modifier = modifier
      .semantics { contentDescription = "Chiedi all'AI" }
      .clip(FluidCapsuleShape)
      .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
      // Il tocco apre il microfono e l'assistente risponde subito con la sua salita: un tap in piu'
      // si sentirebbe come una sbavatura sola.
      .fluidPressable(onClick = onTap, onLongClick = onLongPress, pressedScale = 1f, role = Role.Button, haptic = null)
      .onGloballyPositioned { onBounds(it.boundsInRoot()) },
    content = {
      Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
          .layoutId(SlotIcon)
          .size(AssistantBarButtonDefaults.IconSize)
          .graphicsLayer {
            if (working && !reducedMotion) {
              scaleX = scale
              scaleY = scale
            }
          },
      )
      Text(
        text = "Chiedi all'AI",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
          .layoutId(SlotLabel)
          // La scritta va via nel primo terzo della piega: smette di essere leggibile prima di
          // qualsiasi altra cosa.
          .graphicsLayer { alpha = (1f - fold().fastCoerceIn(0f, 1f) / 0.45f).fastCoerceIn(0f, 1f) },
      )
    },
  ) { measurables, _ ->
    val f = fold().fastCoerceIn(0f, 1f)
    val icon = measurables.first { it.layoutId == SlotIcon }.measure(Constraints())
    val label = measurables.first { it.layoutId == SlotLabel }.measure(Constraints())
    val openWidth = padPx * 2 + icon.width + gapPx + label.width
    val width = lerp(openWidth, foldedPx, f)
    val height = lerp(openHeightPx, foldedPx, f)
    layout(width, height) {
      // L'icona viaggia dal margine sinistro della capsula al centro del cerchio; la scritta la
      // segue e sparisce dietro il bordo che si chiude.
      val iconX = lerp(padPx, (width - icon.width) / 2, f)
      icon.place(iconX, (height - icon.height) / 2)
      label.place(iconX + icon.width + gapPx, (height - label.height) / 2)
    }
  }
}

object AssistantBarButtonDefaults {
  val OpenHeight: Dp = 46.dp
  val HorizontalPadding: Dp = 16.dp
  val Gap: Dp = 8.dp
  val IconSize: Dp = 22.dp

  /** Lo spazio fra il tasto e la pillola sotto. */
  val Spacing: Dp = 8.dp

  /** Quanto in piu' una pagina deve lasciare libero in fondo quando il tasto c'e'. */
  val ContentInset: Dp = OpenHeight + Spacing
}

private const val SlotIcon = "assistant:icon"
private const val SlotLabel = "assistant:label"
