package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion tokens calibrated the way Apple parameterises its springs.
 *
 * SwiftUI describes a spring with `response` (the time the system takes to reach the target when
 * critically damped) and `dampingFraction`. Compose instead wants `stiffness` and `dampingRatio`.
 * Compose's solver derives the natural frequency as `sqrt(stiffness)` in rad/s, so the conversion
 * is exact:
 *
 * ```
 * omega     = 2 * PI / response
 * stiffness = omega * omega
 * ```
 *
 * Keeping the tokens expressed in `response` terms is what stops the motion from drifting: every
 * animation in the app finishes in a duration that is a deliberate multiple of the others, which is
 * the single biggest reason iOS motion reads as "one system" rather than a pile of tweens.
 */
object FluidMotion {

  private const val TwoPi = 6.283185307179586

  /** Converts an Apple-style `response` (seconds) into a Compose stiffness. */
  fun stiffnessForResponse(response: Double): Float {
    val omega = TwoPi / response
    return (omega * omega).toFloat()
  }

  /** ~0.20s. Micro-feedback that must land before the finger lifts. */
  val ResponseInstant = stiffnessForResponse(0.20)

  /** ~0.30s. Press states, switches, chrome cross-fades. */
  val ResponseSnappy = stiffnessForResponse(0.30)

  /** ~0.38s. The workhorse: list changes, selection, most enter/exit. */
  val ResponseStandard = stiffnessForResponse(0.38)

  /** ~0.50s. Large surfaces: sheets, expanding cards, hero content. */
  val ResponseSmooth = stiffnessForResponse(0.50)

  /** ~0.62s. Full-screen or long-travel movement. */
  val ResponseGentle = stiffnessForResponse(0.62)

  /**
   * Critically damped. Nothing overshoots, which is what chrome (bars, backgrounds, opacity)
   * must do — a bouncing navigation bar is the single loudest "this is not premium" tell.
   */
  const val DampingChrome = 1f

  /** A whisper of settle. Reads as "physical" without looking springy. */
  const val DampingStandard = 0.9f

  /** Deliberate, visible settle for playful elements only. */
  const val DampingExpressive = 0.74f

  // --- Float ----------------------------------------------------------------

  fun <T> instant(): SpringSpec<T> = spring(DampingChrome, ResponseInstant)

  fun <T> snappy(): SpringSpec<T> = spring(DampingChrome, ResponseSnappy)

  fun <T> standard(): SpringSpec<T> = spring(DampingStandard, ResponseStandard)

  fun <T> smooth(): SpringSpec<T> = spring(DampingStandard, ResponseSmooth)

  fun <T> gentle(): SpringSpec<T> = spring(DampingChrome, ResponseGentle)

  fun <T> expressive(): SpringSpec<T> = spring(DampingExpressive, ResponseStandard)

  // --- Typed variants -------------------------------------------------------
  //
  // Compose's default visibility threshold for a generic <T> is the Float one (0.01). Applied to
  // Dp / IntOffset / IntSize that threshold is far too small: the spring keeps ticking for
  // sub-pixel amounts long after the movement is visually over, which both wastes frames and, when
  // several such animations overlap, produces the low-grade shimmer that reads as "glitchy".

  fun dp(
    dampingRatio: Float = DampingStandard,
    stiffness: Float = ResponseStandard,
  ): SpringSpec<Dp> = spring(dampingRatio, stiffness, Dp.VisibilityThreshold)

  fun intOffset(
    dampingRatio: Float = DampingStandard,
    stiffness: Float = ResponseStandard,
  ): SpringSpec<IntOffset> = spring(dampingRatio, stiffness, IntOffset.VisibilityThreshold)

  fun intSize(
    dampingRatio: Float = DampingStandard,
    stiffness: Float = ResponseStandard,
  ): SpringSpec<IntSize> = spring(dampingRatio, stiffness, IntSize.VisibilityThreshold)

  // --- Duration-based -------------------------------------------------------
  //
  // Reserved for opacity, where a spring buys nothing: alpha has no momentum to preserve, and a
  // critically damped spring's tail makes a fade feel slower than its nominal duration.

  /** Apple's standard ease for content that enters. */
  val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

  /** Symmetric ease for cross-fades and colour changes. */
  val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

  /** For content leaving: starts immediately, so the exit never feels sticky. */
  val EaseIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

  fun <T> fadeIn(durationMillis: Int = 220): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = EaseOut)

  fun <T> fadeOut(durationMillis: Int = 160): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = EaseIn)

  fun <T> crossFade(durationMillis: Int = 200): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = EaseInOut)

  /** Colour transitions read best on a plain ease — springs make tints wobble. */
  fun <T> color(durationMillis: Int = 240): AnimationSpec<T> =
    tween(durationMillis, easing = EaseInOut)
}
