package dev.antigravity.classevivaexpressive.feature.assistant.bar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
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
 * Il tasto "Chiedi all'AI" sopra la pillola della navigazione: una capsula ovale con l'icona e la
 * scritta, centrata, che **sparisce con la piega** — mentre la pillola si ripiega a sinistra il
 * tasto sfuma e scende verso di lei, e ritorna quando la pillola si riapre. Ripiegato non si
 * tocca: un tasto invisibile che risponde e' peggio di uno che non c'e'. Tocco = voce, pressione
 * lunga = testo; mentre lavora l'icona respira.
 *
 * Come la barra dell'engine, la piega si legge **in disegno, mai in composizione**: un fotogramma
 * della piega costa un aggiornamento del livello grafico, e niente si ricompone. La misura non
 * cambia mai, cosi' la pagina sotto non salta.
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
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val pulse = rememberInfiniteTransition(label = "assistantPulse")
  val breath by pulse.animateFloat(
    initialValue = 1f,
    targetValue = 1.18f,
    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    label = "assistantScale",
  )

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .graphicsLayer {
        // Se ne va nella prima parte della piega, prima che la pillola si sia chiusa del tutto:
        // sfuma, si stringe un poco e scende verso la barra, come se ci rientrasse.
        val t = (fold().fastCoerceIn(0f, 1f) / FadeEnd).fastCoerceIn(0f, 1f)
        alpha = 1f - t
        if (!reducedMotion) {
          val s = lerp(1f, 0.8f, t)
          scaleX = s
          scaleY = s
          translationY = t * size.height * 0.75f
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
      }
      .semantics { contentDescription = "Chiedi all'AI" }
      .clip(FluidCapsuleShape)
      .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
      // Il tocco apre il microfono e l'assistente risponde subito con la sua salita: un tap in piu'
      // si sentirebbe come una sbavatura sola. Ripiegato (quindi invisibile) il tocco non conta.
      .fluidPressable(
        onClick = { if (fold() < HiddenFrom) onTap() },
        onLongClick = { if (fold() < HiddenFrom) onLongPress() },
        pressedScale = 1f,
        role = Role.Button,
        haptic = null,
      )
      .onGloballyPositioned { onBounds(it.boundsInRoot()) }
      .height(AssistantBarButtonDefaults.OpenHeight)
      .padding(horizontal = AssistantBarButtonDefaults.HorizontalPadding),
  ) {
    Icon(
      imageVector = Icons.Rounded.AutoAwesome,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .size(AssistantBarButtonDefaults.IconSize)
        .graphicsLayer {
          if (working && !reducedMotion) {
            scaleX = breath
            scaleY = breath
          }
        },
    )
    Spacer(Modifier.width(AssistantBarButtonDefaults.Gap))
    Text(
      text = "Chiedi all'AI",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
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

/** A questa frazione della piega il tasto e' sparito del tutto. */
private const val FadeEnd = 0.6f

/** Da questa frazione della piega in poi il tasto non risponde piu' al tocco. */
private const val HiddenFrom = 0.4f
