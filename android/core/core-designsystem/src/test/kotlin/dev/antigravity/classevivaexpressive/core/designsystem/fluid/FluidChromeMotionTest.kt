package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import org.junit.Assert.assertEquals
import org.junit.Test

class FluidChromeMotionTest {

  @Test
  fun collapseProgress_isClearBeforeFirstLayout() {
    assertEquals(
      0f,
      calculateCollapseProgress(
        hasVisibleItems = false,
        firstVisibleItemIndex = 0,
        titleBottomPx = null,
        topBarHeightPx = 100f,
        collapseDistancePx = 30f,
      ),
      0f,
    )
  }

  @Test
  fun collapseProgress_isCollapsedOnlyAfterTitleActuallyLeaves() {
    assertEquals(
      0f,
      calculateCollapseProgress(
        hasVisibleItems = true,
        firstVisibleItemIndex = 0,
        titleBottomPx = null,
        topBarHeightPx = 100f,
        collapseDistancePx = 30f,
      ),
      0f,
    )
    assertEquals(
      1f,
      calculateCollapseProgress(
        hasVisibleItems = true,
        firstVisibleItemIndex = 1,
        titleBottomPx = null,
        topBarHeightPx = 100f,
        collapseDistancePx = 30f,
      ),
      0f,
    )
  }

  @Test
  fun glassIntensity_usesDeadZoneAndSmoothLongRamp() {
    val deadZone = 8f
    val ramp = 64f

    assertEquals(0f, calculateGlassIntensity(0, 0, 0f, deadZone, ramp), 0f)
    assertEquals(0f, calculateGlassIntensity(0, 8, 0f, deadZone, ramp), 0f)
    assertEquals(0.5f, calculateGlassIntensity(0, 40, 0f, deadZone, ramp), 0.0001f)
    assertEquals(1f, calculateGlassIntensity(0, 72, 0f, deadZone, ramp), 0f)
    assertEquals(1f, calculateGlassIntensity(1, 0, 0f, deadZone, ramp), 0f)
  }

  @Test
  fun glassIntensity_neverFallsBelowTitleCollapse() {
    assertEquals(
      0.7f,
      calculateGlassIntensity(
        firstVisibleItemIndex = 0,
        firstVisibleItemScrollOffset = 8,
        collapseProgress = 0.7f,
        deadZonePx = 8f,
        rampDistancePx = 64f,
      ),
      0f,
    )
  }

  @Test
  fun bottomBarOffset_hidesForwardAndRevealsInReverse() {
    assertEquals(24f, calculateBottomBarOffset(0f, -24f, 64f), 0f)
    assertEquals(64f, calculateBottomBarOffset(48f, -30f, 64f), 0f)
    assertEquals(18f, calculateBottomBarOffset(48f, 30f, 64f), 0f)
    assertEquals(0f, calculateBottomBarOffset(18f, 30f, 64f), 0f)
  }

  @Test
  fun bottomBarSettle_respectsVelocityThenNearestRestingPoint() {
    val threshold = bottomBarVelocityThresholdPx(density = 1f)
    assertEquals(64f, calculateBottomBarSettleTarget(4f, 64f, -400f, threshold), 0f)
    assertEquals(0f, calculateBottomBarSettleTarget(60f, 64f, 400f, threshold), 0f)
    assertEquals(0f, calculateBottomBarSettleTarget(20f, 64f, 0f, threshold), 0f)
    assertEquals(64f, calculateBottomBarSettleTarget(44f, 64f, 0f, threshold), 0f)
  }

  @Test
  fun bottomBarSettle_usesTheSameDpPerSecondThresholdAtEveryDensity() {
    val mdpiThreshold = bottomBarVelocityThresholdPx(density = 1f)
    val xxxhdpiThreshold = bottomBarVelocityThresholdPx(density = 4f)

    assertEquals(200f, mdpiThreshold, 0f)
    assertEquals(800f, xxxhdpiThreshold, 0f)

    // The same 250 dp/s fling hides the bar at both densities.
    assertEquals(64f, calculateBottomBarSettleTarget(4f, 64f, -250f, mdpiThreshold), 0f)
    assertEquals(64f, calculateBottomBarSettleTarget(4f, 64f, -1_000f, xxxhdpiThreshold), 0f)

    // The same sub-threshold 150 dp/s motion falls back to the nearest resting point at both.
    assertEquals(0f, calculateBottomBarSettleTarget(4f, 64f, -150f, mdpiThreshold), 0f)
    assertEquals(0f, calculateBottomBarSettleTarget(4f, 64f, -600f, xxxhdpiThreshold), 0f)
  }
}
