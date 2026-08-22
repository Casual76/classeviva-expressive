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
 * The seed of an app palette.
 *
 * Large surfaces stay restrained, while containers inherit a progressively stronger trace of this
 * hue. A preset holds separate light and dark values because the same RGB colour rarely keeps its
 * character and contrast on both backgrounds.
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
  val useDynamic = settings.dynamicColorEnabled &&
    settings.accentMode == AccentMode.DYNAMIC &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val dynamicScheme = if (useDynamic) {
    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    null
  }
  val colors = resolveClassevivaColorScheme(
    settings = settings,
    isDark = isDark,
    dynamicScheme = dynamicScheme,
  )

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

/** Accent roles are kept as pairs: a background colour is never detached from its readable content. */
@Immutable
private data class AccentRoles(
  val primary: Color,
  val onPrimary: Color,
  val primaryContainer: Color,
  val onPrimaryContainer: Color,
  val secondary: Color,
  val onSecondary: Color,
  val secondaryContainer: Color,
  val onSecondaryContainer: Color,
  val tertiary: Color,
  val onTertiary: Color,
  val tertiaryContainer: Color,
  val onTertiaryContainer: Color,
)

@Immutable
private data class SurfacePalette(
  val background: Color,
  val surface: Color,
  val lowest: Color,
  val low: Color,
  val container: Color,
  val high: Color,
  val highest: Color,
  val onSurface: Color,
  val onSurfaceVariant: Color,
  val outline: Color,
  val outlineVariant: Color,
)

/**
 * Resolves the app palette without Android dependencies, so every colour source can be verified in
 * a local unit test. Dynamic colour contributes all three Material accent families (including their
 * content/container pairs); only the large neutral surfaces are rebuilt to retain the quiet app
 * character. Brand and preset colours use the same surface ladder, with a restrained hue wash that
 * becomes stronger only on smaller, higher containers.
 */
internal fun resolveClassevivaColorScheme(
  settings: AppSettings,
  isDark: Boolean,
  dynamicScheme: ColorScheme? = null,
): ColorScheme {
  val amoled = isDark && (settings.themeMode == ThemeMode.AMOLED || settings.amoledEnabled)
  val dynamic = dynamicScheme.takeIf {
    settings.dynamicColorEnabled && settings.accentMode == AccentMode.DYNAMIC
  }
  val accent = dynamic?.primary ?: when (settings.accentMode) {
    AccentMode.BRAND,
    AccentMode.DYNAMIC,
    -> BrandAccent.resolve(isDark)
    AccentMode.CUSTOM_PRESET -> presetFor(settings.customAccentName).resolve(isDark)
  }
  val surfaces = surfacePalette(accent = accent, isDark = isDark, amoled = amoled)
  val roles = dynamic?.toAccentRoles() ?: fixedAccentRoles(accent, surfaces, isDark)
  return buildColorScheme(roles = roles, surfaces = surfaces, isDark = isDark)
}

private fun ColorScheme.toAccentRoles(): AccentRoles = AccentRoles(
  primary = primary,
  onPrimary = onPrimary,
  primaryContainer = primaryContainer,
  onPrimaryContainer = onPrimaryContainer,
  secondary = secondary,
  onSecondary = onSecondary,
  secondaryContainer = secondaryContainer,
  onSecondaryContainer = onSecondaryContainer,
  tertiary = tertiary,
  onTertiary = onTertiary,
  tertiaryContainer = tertiaryContainer,
  onTertiaryContainer = onTertiaryContainer,
)

private fun fixedAccentRoles(
  accent: Color,
  surfaces: SurfacePalette,
  isDark: Boolean,
): AccentRoles {
  // Fixed palettes still need more than one note. These restrained blends keep the chosen accent
  // recognisable while giving temporal and insight surfaces their own related identity.
  val secondary = lerp(
    accent,
    if (isDark) Color(0xFF5E5CE6) else Color(0xFF5856D6),
    0.26f,
  )
  val tertiary = lerp(
    accent,
    if (isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500),
    0.22f,
  )
  val primaryContainer = lerp(surfaces.low, accent, if (isDark) 0.28f else 0.17f)
  val secondaryContainer = lerp(surfaces.container, secondary, if (isDark) 0.22f else 0.14f)
  val tertiaryContainer = lerp(surfaces.high, tertiary, if (isDark) 0.18f else 0.11f)
  return AccentRoles(
    primary = accent,
    onPrimary = highestContrastContent(accent),
    primaryContainer = primaryContainer,
    onPrimaryContainer = highestContrastContent(primaryContainer),
    secondary = secondary,
    onSecondary = highestContrastContent(secondary),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = highestContrastContent(secondaryContainer),
    tertiary = tertiary,
    onTertiary = highestContrastContent(tertiary),
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = highestContrastContent(tertiaryContainer),
  )
}

private fun surfacePalette(accent: Color, isDark: Boolean, amoled: Boolean): SurfacePalette {
  val neutralBackground = when {
    amoled -> Color.Black
    isDark -> Color(0xFF0D0D0F)
    else -> Color(0xFFF2F2F7)
  }
  val neutralSurface = when {
    amoled -> Color(0xFF0E0E10)
    isDark -> Color(0xFF1B1B1E)
    else -> Color.White
  }
  val neutralLowest = when {
    amoled -> Color.Black
    isDark -> Color(0xFF101012)
    else -> Color.White
  }
  val neutralLow = when {
    amoled -> Color(0xFF121214)
    isDark -> Color(0xFF1D1D20)
    else -> Color(0xFFFCFCFE)
  }
  val neutralContainer = when {
    amoled -> Color(0xFF161618)
    isDark -> Color(0xFF202023)
    else -> Color(0xFFF9F9FC)
  }
  val neutralHigh = when {
    amoled -> Color(0xFF1B1B1E)
    isDark -> Color(0xFF252528)
    else -> Color(0xFFF3F3F7)
  }
  val neutralHighest = when {
    amoled -> Color(0xFF222225)
    isDark -> Color(0xFF2C2C2F)
    else -> Color(0xFFEBEBF0)
  }
  val onSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF121214)
  val onSurfaceVariant = if (isDark) Color(0xFFAAAAB2) else Color(0xFF68686E)
  val outline = when {
    amoled -> Color(0xFF343438)
    isDark -> Color(0xFF48484D)
    else -> Color(0xFFB8B8BF)
  }
  val outlineVariant = when {
    amoled -> Color(0xFF222225)
    isDark -> Color(0xFF323236)
    else -> Color(0xFFD8D8DE)
  }

  return SurfacePalette(
    background = lerp(neutralBackground, accent, if (amoled) 0f else 0.012f),
    surface = lerp(neutralSurface, accent, if (amoled) 0.010f else 0.014f),
    lowest = lerp(neutralLowest, accent, if (amoled) 0f else 0.008f),
    low = lerp(neutralLow, accent, 0.024f),
    container = lerp(neutralContainer, accent, 0.038f),
    high = lerp(neutralHigh, accent, 0.055f),
    highest = lerp(neutralHighest, accent, 0.075f),
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
  )
}

private fun buildColorScheme(
  roles: AccentRoles,
  surfaces: SurfacePalette,
  isDark: Boolean,
): ColorScheme {
  val error = if (isDark) Color(0xFFFF6961) else Color(0xFFD70015)
  val errorContainer = lerp(surfaces.container, error, if (isDark) 0.24f else 0.14f)
  val base = if (isDark) darkColorScheme() else lightColorScheme()
  return base.copy(
    primary = roles.primary,
    onPrimary = roles.onPrimary,
    primaryContainer = roles.primaryContainer,
    onPrimaryContainer = roles.onPrimaryContainer,
    inversePrimary = roles.primary,
    secondary = roles.secondary,
    onSecondary = roles.onSecondary,
    secondaryContainer = roles.secondaryContainer,
    onSecondaryContainer = roles.onSecondaryContainer,
    tertiary = roles.tertiary,
    onTertiary = roles.onTertiary,
    tertiaryContainer = roles.tertiaryContainer,
    onTertiaryContainer = roles.onTertiaryContainer,
    background = surfaces.background,
    onBackground = surfaces.onSurface,
    surface = surfaces.surface,
    onSurface = surfaces.onSurface,
    surfaceVariant = surfaces.high,
    onSurfaceVariant = surfaces.onSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceBright = if (isDark) surfaces.highest else surfaces.surface,
    surfaceDim = surfaces.background,
    surfaceContainerLowest = surfaces.lowest,
    surfaceContainerLow = surfaces.low,
    surfaceContainer = surfaces.container,
    surfaceContainerHigh = surfaces.high,
    surfaceContainerHighest = surfaces.highest,
    inverseSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF1C1C1E),
    inverseOnSurface = if (isDark) Color(0xFF121214) else Color(0xFFF2F2F7),
    error = error,
    onError = highestContrastContent(error),
    errorContainer = errorContainer,
    onErrorContainer = highestContrastContent(errorContainer),
    outline = surfaces.outline,
    outlineVariant = surfaces.outlineVariant,
    scrim = Color.Black,
  )
}

private fun highestContrastContent(background: Color): Color {
  val dark = Color(0xFF121214)
  val light = Color(0xFFFDFDFF)
  fun ratio(foreground: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
  return if (ratio(dark) >= ratio(light)) dark else light
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
