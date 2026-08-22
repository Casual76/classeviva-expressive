package dev.antigravity.classevivaexpressive.macrobenchmark

import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Warm-path frame benchmarks for the motion system's release gates.
 *
 * The connected device must already contain an authenticated Classeviva session. Tests fail with
 * an explicit precondition error instead of silently measuring the login screen. FrameTimingMetric
 * emits both CPU frame duration and frame-overrun percentiles plus a Perfetto trace per iteration.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMacrobenchmarkApi::class)
class MotionMacrobenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun homeScroll() = measureMotion(
    setup = { openHome() },
    measure = {
      swipeContentUp()
      swipeContentUp()
      swipeContentDown()
    },
  )

  @Test
  fun topLevelSwitch() = measureMotion(
    setup = { openHome() },
    measure = {
      clickText("Voti")
      clickText("Bacheca")
      clickText("Home")
    },
  )

  @Test
  fun gradeContainerDetail() = measureMotion(
    setup = {
      openHome()
      clickText("Voti")
      revealFirstGradeRow()
    },
    measure = {
      openFirstGradeRow()
      SystemClock.sleep(MOTION_SETTLE_MS)
      device.pressBack()
      SystemClock.sleep(MOTION_SETTLE_MS)
    },
  )

  @Test
  fun lessonsTimeline() = measureMotion(
    setup = {
      openHome()
      clickText("Altro")
      clickText("Orario")
      requireText("Orario")
    },
    measure = {
      swipeContentUp()
      clickDescriptionIfPresent("Settimana successiva")
      swipeContentDown()
    },
  )

  @Test
  fun communicationsSections() = measureMotion(
    setup = {
      openHome()
      clickText("Bacheca")
      requireText("Bacheca")
    },
    measure = {
      swipeContentUp()
      swipeContentUp()
      swipeContentDown()
    },
  )

  private fun measureMotion(
    setup: MacrobenchmarkScope.() -> Unit,
    measure: MacrobenchmarkScope.() -> Unit,
  ) = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(FrameTimingMetric()),
    // Personal-device QA must never let Macrobenchmark reinstall the authenticated target.
    // The runner also passes androidx.benchmark.compilation.enabled=false as a second guard.
    compilationMode = CompilationMode.Ignore(),
    startupMode = StartupMode.WARM,
    iterations = MOTION_ITERATIONS,
    setupBlock = {
      pressHome()
      startActivityAndWait()
      setup()
    },
    measureBlock = measure,
  )
}

private fun MacrobenchmarkScope.openHome() {
  requireAuthenticatedSession()
  clickText("Home")
  requireText("Home")
}

private fun MacrobenchmarkScope.requireAuthenticatedSession() {
  check(device.wait(Until.hasObject(By.text("Home")), UI_TIMEOUT_MS)) {
    "Macrobenchmark requires an already authenticated app session; Home was not visible."
  }
}

private fun MacrobenchmarkScope.requireText(text: String): UiObject2 {
  return checkNotNull(device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS)) {
    "Expected '$text' to be visible while preparing the benchmark."
  }
}

private fun MacrobenchmarkScope.clickText(text: String) {
  requireText(text).click()
  device.waitForIdle()
  SystemClock.sleep(SHORT_SETTLE_MS)
}

private fun MacrobenchmarkScope.clickDescriptionIfPresent(description: String) {
  device.wait(Until.findObject(By.desc(description)), SHORT_UI_TIMEOUT_MS)?.let {
    it.click()
    device.waitForIdle()
    SystemClock.sleep(SHORT_SETTLE_MS)
  }
}

private fun MacrobenchmarkScope.openFirstGradeRow() {
  val gradeRow = findFirstGradeRow()

  checkNotNull(gradeRow) {
    "No numeric grade row is visible after preparing the grades list."
  }.click()
  device.waitForIdle()
}

/**
 * Normalizes a restored grades-list offset before the measured interaction. The grades screen can
 * legitimately reopen above or below the first row, so the benchmark must not mistake saved scroll
 * state for missing user data.
 */
private fun MacrobenchmarkScope.revealFirstGradeRow() {
  repeat(3) { swipeContentDown() }
  repeat(6) { attempt ->
    if (findFirstGradeRow() != null) return
    if (attempt < 5) swipeContentUp()
  }
  error("No numeric grade row is available after traversing the grades list.")
}

private fun MacrobenchmarkScope.findFirstGradeRow(): UiObject2? {
  val valuePattern = Pattern.compile("^(10|[1-9])(?:(?:[,.][0-9]{1,2})|½)?\\s*[+\\-−]?$")
  val contentTop = device.displayHeight / 4
  val contentBottom = device.displayHeight * 4 / 5
  val gradeNode = device.findObjects(By.text(valuePattern))
    .firstOrNull { node ->
      node.visibleBounds.centerY() in contentTop..contentBottom &&
        node.visibleBounds.centerX() >= device.displayWidth / 2
    }
  return generateSequence(gradeNode) { it.parent }
    .firstOrNull { node -> node.isClickable && node.visibleBounds.isUsefulRow(device.displayWidth) }
}

private fun Rect.isUsefulRow(displayWidth: Int): Boolean =
  width() >= displayWidth / 2 && height() > 0

private fun MacrobenchmarkScope.swipeContentUp() {
  val x = device.displayWidth / 2
  device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 3, SWIPE_STEPS)
  device.waitForIdle()
}

private fun MacrobenchmarkScope.swipeContentDown() {
  val x = device.displayWidth / 2
  device.swipe(x, device.displayHeight / 3, x, device.displayHeight * 3 / 4, SWIPE_STEPS)
  device.waitForIdle()
}

internal const val TARGET_PACKAGE = "dev.antigravity.classevivaexpressive"
private const val UI_TIMEOUT_MS = 10_000L
private const val SHORT_UI_TIMEOUT_MS = 1_500L
private const val SHORT_SETTLE_MS = 250L
private const val MOTION_SETTLE_MS = 650L
private const val SWIPE_STEPS = 18
private const val MOTION_ITERATIONS = 5
