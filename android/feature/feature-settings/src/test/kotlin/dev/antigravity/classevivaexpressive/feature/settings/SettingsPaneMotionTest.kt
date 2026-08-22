package dev.antigravity.classevivaexpressive.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPaneMotionTest {

  @Test
  fun predictiveBackProgress_isKeptInsideSeekableTransitionBounds() {
    assertEquals(0f, clampSettingsBackProgress(-0.2f))
    assertEquals(0.42f, clampSettingsBackProgress(0.42f))
    assertEquals(1f, clampSettingsBackProgress(1.3f))
  }

  @Test
  fun opening_usesOpaqueForwardStackOffsets() {
    assertEquals(1_000, settingsPaneEnterOffset(width = 1_000, opening = true))
    assertEquals(-250, settingsPaneExitOffset(width = 1_000, opening = true))
  }

  @Test
  fun closing_isTheExactSpatialInverse() {
    assertEquals(-250, settingsPaneEnterOffset(width = 1_000, opening = false))
    assertEquals(1_000, settingsPaneExitOffset(width = 1_000, opening = false))
  }

  @Test
  fun travellingPane_staysAboveTheDestinationInBothDirections() {
    assertEquals(1f, settingsPaneTargetZIndex(opening = true))
    assertEquals(-1f, settingsPaneTargetZIndex(opening = false))
  }

  @Test
  fun settleDuration_isProportionalToTheDistanceLeft() {
    assertEquals(270, settingsPaneSettleDurationMillis(progress = 0.25f, completing = true))
    assertEquals(90, settingsPaneSettleDurationMillis(progress = 0.25f, completing = false))
    assertEquals(180, settingsPaneSettleDurationMillis(progress = 0.5f, completing = true))
    assertEquals(180, settingsPaneSettleDurationMillis(progress = 0.5f, completing = false))
  }
}
