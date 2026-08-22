package dev.antigravity.classevivaexpressive.macrobenchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that navigation remains functional when Android animations are disabled globally. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AnimatorScaleSmokeTest {
  @Test
  fun animatorScaleZero_topLevelNavigationRemainsUsable() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val originalScales = ANIMATOR_SCALE_KEYS.associateWith(device::readGlobalSetting)

    try {
      ANIMATOR_SCALE_KEYS.forEach { device.writeGlobalSetting(it, "0") }
      device.executeShellCommand("am force-stop $TARGET_PACKAGE")
      device.executeShellCommand("am start -W -n $TARGET_PACKAGE/.MainActivity")

      assertTrue(
        "Animator 0x smoke requires an already authenticated app session.",
        device.wait(Until.hasObject(By.text("Home")), UI_TIMEOUT_MS),
      )

      checkNotNull(device.wait(Until.findObject(By.text("Voti")), UI_TIMEOUT_MS)) {
        "Voti tab was not available at animator scale 0x."
      }.click()
      device.waitForIdle()
      checkNotNull(device.wait(Until.findObject(By.text("Bacheca")), UI_TIMEOUT_MS)) {
        "Bacheca tab was not available at animator scale 0x."
      }.click()
      device.waitForIdle()

      assertEquals(TARGET_PACKAGE, device.currentPackageName)
      assertTrue(device.hasObject(By.text("Bacheca")))
    } finally {
      originalScales.forEach { (key, value) ->
        if (value == null) device.deleteGlobalSetting(key) else device.writeGlobalSetting(key, value)
      }
    }
  }
}

private fun UiDevice.readGlobalSetting(key: String): String? =
  executeShellCommand("settings get global $key").trim().takeUnless { it == "null" || it.isBlank() }

private fun UiDevice.writeGlobalSetting(key: String, value: String) {
  executeShellCommand("settings put global $key $value")
}

private fun UiDevice.deleteGlobalSetting(key: String) {
  executeShellCommand("settings delete global $key")
}

private const val UI_TIMEOUT_MS = 10_000L
private val ANIMATOR_SCALE_KEYS = listOf(
  "window_animation_scale",
  "transition_animation_scale",
  "animator_duration_scale",
)
