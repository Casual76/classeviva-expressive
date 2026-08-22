package dev.antigravity.classevivaexpressive.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureHeroLayoutTest {

  @Test
  fun roomyPhone_keepsThreeMetricsOnOneRow() {
    assertEquals(
      3,
      featureHeroMetricColumnCount(
        availableWidthDp = 331f,
        fontScale = 1f,
        metricCount = 3,
      ),
    )
  }

  @Test
  fun widthNearOldBreakpoint_usesTwoColumnsInsteadOfFullStack() {
    assertEquals(
      2,
      featureHeroMetricColumnCount(
        availableWidthDp = 285f,
        fontScale = 1f,
        metricCount = 3,
      ),
    )
  }

  @Test
  fun compactWidth_fallsBackToOneColumn() {
    assertEquals(
      1,
      featureHeroMetricColumnCount(
        availableWidthDp = 215f,
        fontScale = 1f,
        metricCount = 3,
      ),
    )
  }

  @Test
  fun fontScale_reducesDensityProgressively() {
    assertEquals(2, featureHeroMetricColumnCount(331f, 1.3f, 3))
    assertEquals(1, featureHeroMetricColumnCount(331f, 1.6f, 3))
  }

  @Test
  fun columnCount_neverExceedsAvailableMetrics() {
    assertEquals(2, featureHeroMetricColumnCount(700f, 1f, 2))
    assertEquals(0, featureHeroMetricColumnCount(700f, 1f, 0))
  }
}
