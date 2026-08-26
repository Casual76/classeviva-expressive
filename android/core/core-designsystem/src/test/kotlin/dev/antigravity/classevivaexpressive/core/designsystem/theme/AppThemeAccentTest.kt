package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeAccentTest {

  /**
   * Ogni nome della lista dell'app deve risolversi nel SUO colore. Prima della lista iniettata
   * "expressive" funzionava solo perche' il fallback dell'engine cadeva per coincidenza sullo
   * stesso blu: questo test trasforma quella coincidenza in un contratto, e fa esplodere in CI un
   * preset aggiunto solo nel picker.
   */
  @Test
  fun everyPickerPreset_resolvesToItsOwnAccent() {
    expressiveAccentPresets.forEach { preset ->
      listOf(false, true).forEach { isDark ->
        val scheme = classevivaColorScheme(
          settings = AppSettings(
            accentMode = AccentMode.CUSTOM_PRESET,
            customAccentName = preset.name,
          ),
          isDark = isDark,
        )
        assertEquals(
          "preset ${preset.name} (isDark=$isDark)",
          preset.resolve(isDark),
          scheme.primary,
        )
      }
    }
  }

  /** L'ametista e' il default: senza scelte salvate, primary deve essere il marchio. */
  @Test
  fun brandMode_isAmethyst() {
    assertEquals(Color(0xFF9D4EDD), classevivaColorScheme(AppSettings(), isDark = false).primary)
    assertEquals(Color(0xFFC77DFF), classevivaColorScheme(AppSettings(), isDark = true).primary)
  }

  /**
   * La ragione dei poli: con un primary viola l'anello derivato dalle ancore storiche collassava
   * (secondary a un passo dal primary). Le tre famiglie devono restare visibilmente distinte,
   * misurate come distanza per canale — la disuguaglianza esatta non basta, e' quello che il
   * collasso superava.
   */
  @Test
  fun amethystFamilies_stayVisiblyApart() {
    listOf(false, true).forEach { isDark ->
      val scheme = classevivaColorScheme(AppSettings(), isDark = isDark)
      listOf(
        Triple("primary/secondary", scheme.primary, scheme.secondary),
        Triple("primary/tertiary", scheme.primary, scheme.tertiary),
        Triple("secondary/tertiary", scheme.secondary, scheme.tertiary),
      ).forEach { (pair, first, second) ->
        val distance = channelDistance(first, second)
        assertTrue(
          "$pair troppo vicini (isDark=$isDark): distanza $distance",
          distance >= 0.20f,
        )
      }
    }
  }

  private fun channelDistance(first: Color, second: Color): Float =
    kotlin.math.abs(first.red - second.red) +
      kotlin.math.abs(first.green - second.green) +
      kotlin.math.abs(first.blue - second.blue)
}
