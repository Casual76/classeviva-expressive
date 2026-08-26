package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors
import dev.antigravity.fluidengine.ui.theme.FluidTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    assertEquals(GradeBand.Eccellente, gradeBand(9.49))
    assertEquals(GradeBand.Lode, gradeBand(9.5))
    assertEquals(GradeBand.Lode, gradeBand(10.0))
  }

  @Test
  fun everyBand_hasReadableContentOnBothGradientEnds() {
    accentSchemes().forEach { (name, scheme) ->
      GradeBand.entries.forEach { band ->
        val colors = vivid(band, scheme)
        val worst = minOf(
          contrastRatio(colors.content, colors.start),
          contrastRatio(colors.content, colors.end),
        )
        assertTrue(
          "$band in $name illeggibile: rapporto peggiore $worst",
          worst >= 4.5f,
        )
      }
    }
  }

  @Test
  fun everyBand_readsWithDarkContent() {
    // La lode era l'unica card della lista con il testo bianco, e per un soffio (4.53 contro 4.07):
    // una sola card che si legge al contrario non dice "sono speciale", dice "sono un errore".
    // L'accento viene schiarito quanto basta perche' tutta la scala si legga allo stesso modo.
    accentSchemes().forEach { (name, scheme) ->
      GradeBand.entries.forEach { band ->
        val colors = vivid(band, scheme)
        val onDark = contrastRatio(Color(0xFF121214), colors.start)
        val onLight = contrastRatio(Color(0xFFFDFDFF), colors.start)
        assertTrue(
          "$band in $name si legge col bianco invece che con lo scuro ($onDark vs $onLight)",
          onDark >= onLight,
        )
      }
    }
  }

  @Test
  fun theTwoGreens_stayDistinguishable() {
    // Prima condividevano il capo #00C7BE e differivano solo per la partenza: 0.396 di distanza
    // totale. Ora sono due famiglie, e la soglia sta sopra il valore vecchio apposta — se qualcuno
    // rimette l'acqua dentro il verde, questo test lo prende.
    listOf(false, true).forEach { isDark ->
      val scheme = brandScheme(isDark)
      val sufficiente = vivid(GradeBand.Sufficiente, scheme)
      val eccellente = vivid(GradeBand.Eccellente, scheme)
      val distance = channelDistance(sufficiente.start, eccellente.start) +
        channelDistance(sufficiente.end, eccellente.end)
      assertTrue(
        "verdi indistinguibili (isDark=$isDark): distanza $distance",
        distance >= 0.60f,
      )
    }
  }

  @Test
  fun theAquaBandIsBlueGreen_andTheGreenBandIsNot() {
    // L'invariante che dice davvero "una e' verde acqua e l'altra e' verde": quanto blu c'e'
    // rispetto al verde. E' piu' leggibile della tinta in gradi e non dipende dalla luminosita'.
    listOf(false, true).forEach { isDark ->
      val scheme = brandScheme(isDark)
      listOf(vivid(GradeBand.Sufficiente, scheme)).forEach { aqua ->
        listOf(aqua.start, aqua.end).forEach { end ->
          assertTrue(
            "la fascia acqua non e' abbastanza blu (isDark=$isDark): $end",
            end.blue >= 0.80f * end.green,
          )
        }
      }
      listOf(vivid(GradeBand.Eccellente, scheme)).forEach { green ->
        listOf(green.start, green.end).forEach { end ->
          assertTrue(
            "la fascia verde e' troppo blu (isDark=$isDark): $end",
            end.blue < 0.80f * end.green,
          )
        }
      }
    }
  }

  @Test
  fun everyPairOfFixedBands_staysApart() {
    val fixed = listOf(
      GradeBand.Insufficiente,
      GradeBand.Recuperabile,
      GradeBand.Sufficiente,
      GradeBand.Eccellente,
    )
    listOf(false, true).forEach { isDark ->
      val scheme = brandScheme(isDark)
      fixed.forEachIndexed { index, first ->
        fixed.drop(index + 1).forEach { second ->
          val a = vivid(first, scheme)
          val b = vivid(second, scheme)
          val distance = channelDistance(a.start, b.start) + channelDistance(a.end, b.end)
          assertTrue(
            "$first e $second troppo vicine (isDark=$isDark): distanza $distance",
            distance >= 0.60f,
          )
        }
      }
    }
  }

  @Test
  fun greenAccent_fallsBackToTheGreenBand() {
    assertLodeFallsBack("jade")
  }

  @Test
  fun orangeAccent_fallsBackToTheGreenBand() {
    assertLodeFallsBack("ember")
  }

  @Test
  fun amethystAndBlueAccents_keepTheirOwnLodeBand() {
    listOf(false, true).forEach { isDark ->
      val brand = brandScheme(isDark)
      assertNotEquals(
        "il marchio ametista deve avere una lode sua (isDark=$isDark)",
        vivid(GradeBand.Eccellente, brand),
        vivid(GradeBand.Lode, brand),
      )
      val blue = presetScheme("expressive", isDark)
      assertNotEquals(
        "il preset blu deve avere una lode sua (isDark=$isDark)",
        vivid(GradeBand.Eccellente, blue),
        vivid(GradeBand.Lode, blue),
      )
    }
  }

  @Test
  fun gradeTone_staysThreeSteps() {
    // Le cinque fasce vivono solo sulle superfici intere. Nelle liste raggruppate il tono resta a
    // tre gradini: una piastrella da 32dp non ha spazio per dire cinque cose diverse.
    assertEquals(FluidTone.Success, gradeTone(9.9))
    assertEquals(FluidTone.Success, gradeTone(6.0))
    assertEquals(FluidTone.Warning, gradeTone(5.5))
    assertEquals(FluidTone.Danger, gradeTone(4.0))
    assertEquals(FluidTone.Neutral, gradeTone(null))
  }

  private fun assertLodeFallsBack(presetName: String) {
    listOf(false, true).forEach { isDark ->
      val scheme = presetScheme(presetName, isDark)
      assertEquals(
        "l'accento '$presetName' collide con la scala: la lode deve ripiegare (isDark=$isDark)",
        vivid(GradeBand.Eccellente, scheme),
        vivid(GradeBand.Lode, scheme),
      )
    }
  }

  private fun vivid(band: GradeBand, scheme: ColorScheme): FluidVividColors = gradeVividColors(
    band = band,
    isDark = scheme.surface.luminance() < 0.5f,
    accentPrimary = scheme.primary,
    accentSecondary = scheme.secondary,
  )

  private fun brandScheme(isDark: Boolean): ColorScheme =
    classevivaColorScheme(AppSettings(), isDark = isDark)

  private fun presetScheme(name: String, isDark: Boolean): ColorScheme = classevivaColorScheme(
    settings = AppSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = name),
    isDark = isDark,
  )

  private fun accentSchemes(): List<Pair<String, ColorScheme>> = buildList {
    listOf(false, true).forEach { isDark ->
      val suffix = if (isDark) "scuro" else "chiaro"
      add("marchio $suffix" to brandScheme(isDark))
      expressiveAccentPresets.forEach { preset ->
        add("${preset.name} $suffix" to presetScheme(preset.name, isDark))
      }
    }
  }

  private fun contrastRatio(foreground: Color, background: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
}
