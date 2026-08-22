package dev.antigravity.classevivaexpressive.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.ContinuousCornerShape
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidRadius
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.fluidTypography
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode

/**
 * An accent, and nothing else.
 *
 * The palette is deliberately neutral: greys carry the whole interface, and colour is spent only on
 * things a person can act on or on states that mean something. A preset therefore holds one hue,
 * given twice — a colour that is legible on white is rarely the same colour that is legible on black,
 * and iOS ships both for exactly that reason.
 */
@Immutable
data class AccentPreset(
  val name: String,
  val label: String,
  val light: Color,
  val dark: Color,
) {
  fun resolve(isDark: Boolean): Color = if (isDark) dark else light
}

/**
 * Names are kept from the previous palette so a stored preference still resolves; the colours are
 * the iOS system set, which is tuned for exactly this job.
 */
val expressiveAccentPresets = listOf(
  AccentPreset("expressive", "Blu", Color(0xFF007AFF), Color(0xFF0A84FF)),
  AccentPreset("ember", "Arancio", Color(0xFFFF9500), Color(0xFFFF9F0A)),
  AccentPreset("ocean", "Indaco", Color(0xFF5856D6), Color(0xFF7D7AFF)),
  AccentPreset("jade", "Verde", Color(0xFF34C759), Color(0xFF30D158)),
)

/**
 * The app's own accent, used when the accent mode is "Classeviva" rather than a preset.
 *
 * It used to resolve to whichever preset was last stored, which made the brand option a no-op that
 * silently agreed with the previous choice.
 */
private val BrandAccent = AccentPreset("classeviva", "Classeviva", Color(0xFF1F9E6E), Color(0xFF2ED8A0))

fun classevivaBrandAccent(isDark: Boolean): Color = BrandAccent.resolve(isDark)

private fun presetFor(name: String): AccentPreset {
  return expressiveAccentPresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    ?: expressiveAccentPresets.first()
}

private val ClassevivaShapes = Shapes(
  extraSmall = ContinuousCornerShape(FluidRadius.Small),
  small = ContinuousCornerShape(FluidRadius.Control),
  medium = ContinuousCornerShape(FluidRadius.Card),
  large = ContinuousCornerShape(FluidRadius.Group),
  extraLarge = ContinuousCornerShape(FluidRadius.Sheet),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClassevivaExpressiveTheme(
  settings: AppSettings,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val isDark = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK,
    ThemeMode.AMOLED,
    -> true
  }
  val amoled = isDark && (settings.themeMode == ThemeMode.AMOLED || settings.amoledEnabled)
  val accent = when (settings.accentMode) {
    AccentMode.BRAND -> BrandAccent
    else -> presetFor(settings.customAccentName)
  }.resolve(isDark)
  val useDynamic = settings.dynamicColorEnabled &&
    settings.accentMode == AccentMode.DYNAMIC &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  val colors = when {
    // Dynamic colour is honoured as a *hue source* only. Taking the system scheme wholesale would
    // repaint every surface in a tinted grey and undo the neutral palette, so only the accent is
    // borrowed and it is dropped into the same neutral scheme as every other preset.
    useDynamic -> {
      val system = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      neutralScheme(system.primary, isDark, amoled)
    }
    else -> neutralScheme(accent, isDark, amoled)
  }

  SystemBarsAppearance(colors)

  MaterialTheme(
    colorScheme = colors,
    motionScheme = ClassevivaMotionScheme,
    shapes = ClassevivaShapes,
    typography = fluidTypography(),
    content = content,
  )
}

/**
 * Keeps the status and navigation bar icons legible against whatever the app is actually painting.
 *
 * Driven by the resolved colour scheme rather than by the system's dark-mode flag, because the app
 * carries its own theme setting: with the system in light mode and the app forced to AMOLED, dark
 * icons would be invisible against a black background.
 */
@Composable
private fun SystemBarsAppearance(colors: ColorScheme) {
  val view = LocalView.current
  val lightBars = colors.background.luminance() > 0.5f
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = lightBars
        isAppearanceLightNavigationBars = lightBars
      }
    }
  }
}

/**
 * The whole palette, from one accent.
 *
 * Two rules hold everywhere:
 *
 *  * **The greys are neutral.** Not blue-grey, not warm-grey — the previous palette leaned green and
 *    the tint was visible on every large surface, which is what made the app read as "themed" rather
 *    than as a piece of system software.
 *  * **Text has two levels.** [ColorScheme.onSurface] for anything that carries meaning,
 *    [ColorScheme.onSurfaceVariant] for everything supporting. A third grey only ever makes the
 *    second one look broken.
 *
 * Containers are derived from the accent by mixing it into the base surface rather than by picking a
 * separate colour, so every preset stays consistent and a dynamic accent cannot produce a clash.
 */
private fun neutralScheme(accent: Color, isDark: Boolean, amoled: Boolean): ColorScheme {
  val background = when {
    amoled -> Color.Black
    isDark -> Color(0xFF0D0D0F)
    else -> Color(0xFFF2F2F7)
  }
  val surface = when {
    amoled -> Color(0xFF0E0E10)
    isDark -> Color(0xFF1B1B1E)
    else -> Color(0xFFFFFFFF)
  }
  val surfaceHigh = when {
    amoled -> Color(0xFF161618)
    isDark -> Color(0xFF232326)
    else -> Color(0xFFF7F7FA)
  }
  val surfaceHighest = when {
    amoled -> Color(0xFF1D1D20)
    isDark -> Color(0xFF2C2C2F)
    else -> Color(0xFFEBEBF0)
  }
  val onSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF121214)
  // Apple's secondary label, resolved against the surface it sits on.
  val onSurfaceVariant = if (isDark) Color(0xFF98989F) else Color(0xFF8A8A8E)
  val outline = when {
    amoled -> Color(0xFF2A2A2C)
    isDark -> Color(0xFF3A3A3C)
    else -> Color(0xFFD3D3D8)
  }
  val outlineVariant = when {
    amoled -> Color(0xFF1A1A1C)
    isDark -> Color(0xFF2A2A2C)
    else -> Color(0xFFE3E3E8)
  }
  val error = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
  val containerMix = if (isDark) 0.24f else 0.14f
  val accentContainer = lerp(surface, accent, containerMix)
  val errorContainer = lerp(surface, error, containerMix)
  val onAccent = if (accent.luminance() > 0.6f) Color(0xFF121214) else Color.White

  return if (isDark) {
    darkColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accentContainer,
      onPrimaryContainer = accent,
      inversePrimary = accent,
      secondary = accent,
      onSecondary = onAccent,
      secondaryContainer = accentContainer,
      onSecondaryContainer = accent,
      tertiary = accent,
      onTertiary = onAccent,
      tertiaryContainer = surfaceHighest,
      onTertiaryContainer = onSurface,
      background = background,
      onBackground = onSurface,
      surface = surface,
      onSurface = onSurface,
      surfaceVariant = surfaceHigh,
      onSurfaceVariant = onSurfaceVariant,
      surfaceTint = Color.Transparent,
      surfaceBright = surfaceHighest,
      surfaceDim = background,
      surfaceContainerLowest = background,
      surfaceContainerLow = surface,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceHigh,
      surfaceContainerHighest = surfaceHighest,
      inverseSurface = Color(0xFFF2F2F5),
      inverseOnSurface = Color(0xFF121214),
      error = error,
      onError = Color.White,
      errorContainer = errorContainer,
      onErrorContainer = error,
      outline = outline,
      outlineVariant = outlineVariant,
      scrim = Color.Black,
    )
  } else {
    lightColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accentContainer,
      onPrimaryContainer = accent,
      inversePrimary = accent,
      secondary = accent,
      onSecondary = onAccent,
      secondaryContainer = accentContainer,
      onSecondaryContainer = accent,
      tertiary = accent,
      onTertiary = onAccent,
      tertiaryContainer = surfaceHighest,
      onTertiaryContainer = onSurface,
      background = background,
      onBackground = onSurface,
      surface = surface,
      onSurface = onSurface,
      surfaceVariant = surfaceHigh,
      onSurfaceVariant = onSurfaceVariant,
      surfaceTint = Color.Transparent,
      surfaceBright = surface,
      surfaceDim = background,
      surfaceContainerLowest = Color.White,
      surfaceContainerLow = surface,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceHigh,
      surfaceContainerHighest = surfaceHighest,
      inverseSurface = Color(0xFF1C1C1E),
      inverseOnSurface = Color(0xFFF2F2F7),
      error = error,
      onError = Color.White,
      errorContainer = errorContainer,
      onErrorContainer = error,
      outline = outline,
      outlineVariant = outlineVariant,
      scrim = Color.Black,
    )
  }
}

@Composable
fun ExpressiveScreenSurface(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.background,
    content = content,
  )
}
