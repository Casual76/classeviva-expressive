package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.fluidPressable

/**
 * Press feedback for tappable surfaces.
 *
 * Kept under its original name so every existing call site picks up the new treatment, but the
 * implementation now delegates to [fluidPressable]. The previous version animated the corner radius
 * from 24dp to 30dp and re-clipped the element on every frame of a press, which made cards visibly
 * wobble against their neighbours; [shape] is therefore accepted and ignored.
 */
@Composable
fun Modifier.bouncyClickable(
  enabled: Boolean = true,
  @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
): Modifier = fluidPressable(
  onClick = onClick,
  onLongClick = onLongClick,
  enabled = enabled,
)
