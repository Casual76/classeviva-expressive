package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val hasAction = onClick != null || onLongClick != null
    if (!hasAction) return@composed this

    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val motionScheme = MaterialTheme.motionScheme

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
        .pointerInput(enabled) {
            if (!enabled || !hasAction) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(false)
                    scope.launch {
                        scale.animateTo(
                            0.97f,
                            animationSpec = motionScheme.fastEffectsSpec(),
                        )
                    }
                    waitForUpOrCancellation()
                    scope.launch {
                        scale.animateTo(
                            1f,
                            animationSpec = motionScheme.defaultEffectsSpec(),
                        )
                    }
                }
            }
        }
}
