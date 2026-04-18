package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme

object MotionTokens {
  val EasingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  val EasingEmphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

  fun <T> spatial(): FiniteAnimationSpec<T> = spring(
    dampingRatio = 0.8f,
    stiffness = 380f,
  )

  fun <T> expressive(): FiniteAnimationSpec<T> = spring(
    dampingRatio = 0.65f,
    stiffness = 250f,
  )

  fun <T> emphasized(): FiniteAnimationSpec<T> = spring(
    dampingRatio = 0.75f,
    stiffness = 200f,
  )

  fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(
    dampingRatio = 0.82f,
    stiffness = 430f,
  )
}

object ClassevivaMotionScheme : MotionScheme {
  override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = MotionTokens.spatial()

  override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MotionTokens.expressive()

  override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = MotionTokens.emphasized()

  override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = MotionTokens.expressive()

  override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = MotionTokens.fastEffects()

  override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = MotionTokens.emphasized()
}
