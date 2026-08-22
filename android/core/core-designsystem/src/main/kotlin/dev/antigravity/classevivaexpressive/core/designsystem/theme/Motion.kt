package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MotionScheme
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidMotion

/**
 * The app's motion vocabulary, expressed once and reused by both the Material components (through
 * [ClassevivaMotionScheme]) and the hand-built chrome.
 *
 * Everything here forwards to [FluidMotion], so there is a single set of durations in the app.
 */
object MotionTokens {
  val EasingStandard: Easing = FluidMotion.EaseInOut
  val EasingEmphasized: Easing = FluidMotion.EaseOut

  fun <T> spatial(): FiniteAnimationSpec<T> = FluidMotion.standard()

  fun <T> expressive(): FiniteAnimationSpec<T> = FluidMotion.expressive()

  fun <T> emphasized(): FiniteAnimationSpec<T> = FluidMotion.smooth()

  fun <T> fastEffects(): FiniteAnimationSpec<T> = FluidMotion.snappy()

  /**
   * Route changes. Duration-based rather than springy: a screen transition has a fixed budget
   * (long enough to be followed, short enough to stay out of the way) and no momentum to preserve.
   */
  fun <T> routeSpatial(): FiniteAnimationSpec<T> = FluidMotion.fadeIn(320)

  fun <T> routeEffects(): FiniteAnimationSpec<T> = FluidMotion.crossFade(180)

  fun <T> sharedSpatial(): FiniteAnimationSpec<T> = FluidMotion.smooth()

  fun <T> sharedEffects(): FiniteAnimationSpec<T> = FluidMotion.crossFade(180)
}

/**
 * Feeds the app's springs to every Material 3 component that reads `MaterialTheme.motionScheme`,
 * so built-in components (switches, chips, sheets) move on the same timings as the custom chrome.
 */
object ClassevivaMotionScheme : MotionScheme {
  override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = FluidMotion.standard()

  override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = FluidMotion.snappy()

  override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = FluidMotion.smooth()

  override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = FluidMotion.snappy()

  override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = FluidMotion.instant()

  override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = FluidMotion.smooth()
}
