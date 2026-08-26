package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeBandTest {

  @Test
  fun bands_switchExactlyOnTheSchoolThresholds() {
    assertNull(gradeBand(null))
    assertEquals(GradeBand.Insufficiente, gradeBand(2.0))
    assertEquals(GradeBand.Insufficiente, gradeBand(4.99))
    assertEquals(GradeBand.Recuperabile, gradeBand(5.0))
    assertEquals(GradeBand.Recuperabile, gradeBand(5.99))
    assertEquals(GradeBand.Sufficiente, gradeBand(6.0))
    assertEquals(GradeBand.Sufficiente, gradeBand(7.49))
    assertEquals(GradeBand.Eccellente, gradeBand(7.5))
    assertEquals(GradeBand.Eccellente, gradeBand(10.0))
  }

  @Test
  fun everyBand_hasReadableContentOnBothGradientEnds() {
    GradeBand.entries.forEach { band ->
      listOf(false, true).forEach { isDark ->
        val colors = gradeVividColors(band, isDark)
        val worst = minOf(
          contrastRatio(colors.content, colors.start),
          contrastRatio(colors.content, colors.end),
        )
        assertTrue(
          "$band (isDark=$isDark) illeggibile: rapporto peggiore $worst",
          worst >= 4.5f,
        )
      }
    }
  }

  @Test
  fun theTwoGreens_stayDistinguishable() {
    // Sufficiente ed Eccellente condividono la famiglia: a distinguerli sono il capo di partenza
    // (piu' smeraldo) e lo sheen. Il capo di partenza deve comunque restare misurabilmente diverso,
    // o l'eccellenza si vede solo quando la card si muove.
    listOf(false, true).forEach { isDark ->
      val sufficiente = gradeVividColors(GradeBand.Sufficiente, isDark)
      val eccellente = gradeVividColors(GradeBand.Eccellente, isDark)
      val distance = channelDistance(sufficiente.start, eccellente.start) +
        channelDistance(sufficiente.end, eccellente.end)
      assertTrue(
        "verdi indistinguibili (isDark=$isDark): distanza $distance",
        distance >= 0.10f,
      )
    }
  }

  private fun contrastRatio(foreground: Color, background: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }

  private fun channelDistance(first: Color, second: Color): Float =
    kotlin.math.abs(first.red - second.red) +
      kotlin.math.abs(first.green - second.green) +
      kotlin.math.abs(first.blue - second.blue)
}
