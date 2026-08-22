package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.antigravity.classevivaexpressive.core.designsystem.theme.resolveClassevivaColorScheme
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidContrastTest {

  @Test
  fun notificationToneIcons_haveAtLeastThreeToOneContrast() {
    appSchemes().forEach { scheme ->
      FluidNotificationTone.entries.forEach { tone ->
        val colors = fluidNotificationIconColors(tone, scheme)
        assertTrue(
          "$tone icon contrast was ${contrastRatio(colors.content, colors.container)}",
          contrastRatio(colors.content, colors.container) >= 3f,
        )
      }
    }
  }

  @Test
  fun selectedAccentRing_hasAtLeastThreeToOneContrastOnItsCardSurface() {
    appSchemes().forEach { scheme ->
      val renderedRing = scheme.onSurface
        .copy(alpha = FluidSelectedRingAlpha)
        .compositeOver(scheme.surfaceContainerLow)

      assertTrue(
        "Selected ring contrast was ${contrastRatio(renderedRing, scheme.surfaceContainerLow)}",
        contrastRatio(renderedRing, scheme.surfaceContainerLow) >= 3f,
      )
    }
  }

  @Test
  fun offSwitchOutline_hasAtLeastThreeToOneContrastOnAppSurfaces() {
    appSchemes().forEach { scheme ->
      val adjacentSurfaces = listOf(
        scheme.surface,
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
      )

      adjacentSurfaces.forEach { surface ->
        val renderedOutline = scheme.onSurface
          .copy(alpha = FluidSwitchOffBorderAlpha)
          .compositeOver(surface)

        assertTrue(
          "Off switch outline contrast was ${contrastRatio(renderedOutline, surface)}",
          contrastRatio(renderedOutline, surface) >= 3f,
        )
      }
    }
  }

  private fun appSchemes() = listOf(
    AppSettings(accentMode = AccentMode.BRAND),
    AppSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "expressive"),
    AppSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"),
    AppSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ocean"),
    AppSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "jade"),
  ).flatMap { settings ->
    listOf(
      resolveClassevivaColorScheme(settings = settings, isDark = false),
      resolveClassevivaColorScheme(settings = settings, isDark = true),
    )
  }

  private fun contrastRatio(foreground: Color, background: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
}
