package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsColorMutationTest {

  @Test
  fun enablingDynamic_selectsTheDynamicSourceInTheSameMutation() {
    val result = AppSettings(
      accentMode = AccentMode.CUSTOM_PRESET,
      dynamicColorEnabled = false,
    ).withDynamicColorEnabled(true)

    assertTrue(result.dynamicColorEnabled)
    assertEquals(AccentMode.DYNAMIC, result.accentMode)
  }

  @Test
  fun disablingDynamic_returnsToBrand() {
    val result = AppSettings(
      accentMode = AccentMode.DYNAMIC,
      dynamicColorEnabled = true,
    ).withDynamicColorEnabled(false)

    assertFalse(result.dynamicColorEnabled)
    assertEquals(AccentMode.BRAND, result.accentMode)
  }

  @Test
  fun selectingDynamicSwatch_reenablesTheLegacyFlag() {
    val result = AppSettings(
      accentMode = AccentMode.BRAND,
      dynamicColorEnabled = false,
    ).withAccentMode(AccentMode.DYNAMIC)

    assertTrue(result.dynamicColorEnabled)
    assertEquals(AccentMode.DYNAMIC, result.accentMode)
  }

  @Test
  fun disablingDynamicWhileUsingPreset_keepsThePreset() {
    val result = AppSettings(
      accentMode = AccentMode.CUSTOM_PRESET,
      dynamicColorEnabled = true,
    ).withDynamicColorEnabled(false)

    assertFalse(result.dynamicColorEnabled)
    assertEquals(AccentMode.CUSTOM_PRESET, result.accentMode)
  }

  @Test
  fun selectingPreset_updatesSourceNameAndDynamicFlagTogether() {
    val result = AppSettings(
      accentMode = AccentMode.DYNAMIC,
      customAccentName = "jade",
      dynamicColorEnabled = true,
    ).withCustomAccent("ember")

    assertEquals(AccentMode.CUSTOM_PRESET, result.accentMode)
    assertEquals("ember", result.customAccentName)
    assertFalse(result.dynamicColorEnabled)
  }
}
