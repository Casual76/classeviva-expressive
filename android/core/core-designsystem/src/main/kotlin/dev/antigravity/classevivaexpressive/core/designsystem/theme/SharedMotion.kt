package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.expressiveSharedBounds(
  sharedTransitionScope: SharedTransitionScope?,
  animatedVisibilityScope: AnimatedVisibilityScope?,
  sharedKey: String?,
): Modifier {
  if (sharedTransitionScope == null || animatedVisibilityScope == null || sharedKey == null) {
    return this
  }

  val boundsTransform = remember {
    BoundsTransform { _, _ -> MotionTokens.sharedSpatial() }
  }

  return with(sharedTransitionScope) {
    this@expressiveSharedBounds.sharedBounds(
      sharedContentState = rememberSharedContentState(sharedKey),
      animatedVisibilityScope = animatedVisibilityScope,
      enter = fadeIn(animationSpec = MotionTokens.sharedEffects()),
      exit = fadeOut(animationSpec = MotionTokens.sharedEffects()),
      boundsTransform = boundsTransform,
    )
  }
}
