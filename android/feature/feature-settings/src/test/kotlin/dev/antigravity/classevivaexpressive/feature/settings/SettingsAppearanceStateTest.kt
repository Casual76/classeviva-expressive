package dev.antigravity.classevivaexpressive.feature.settings

import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAppearanceStateTest {

  @Test
  fun dynamicStoredOnUnsupportedDevice_visuallyFallsBackToBrand() {
    val settings = AppSettings(
      accentMode = AccentMode.DYNAMIC,
      dynamicColorEnabled = true,
    )

    assertEquals(
      AccentMode.BRAND,
      effectiveAccentMode(settings = settings, dynamicColorSupported = false),
    )
  }

  @Test
  fun inconsistentDisabledDynamicState_visuallyFallsBackToBrand() {
    val settings = AppSettings(
      accentMode = AccentMode.DYNAMIC,
      dynamicColorEnabled = false,
    )

    assertEquals(
      AccentMode.BRAND,
      effectiveAccentMode(settings = settings, dynamicColorSupported = true),
    )
  }

  @Test
  fun availableEnabledDynamicState_remainsSelected() {
    val settings = AppSettings(
      accentMode = AccentMode.DYNAMIC,
      dynamicColorEnabled = true,
    )

    assertEquals(
      AccentMode.DYNAMIC,
      effectiveAccentMode(settings = settings, dynamicColorSupported = true),
    )
  }
}
