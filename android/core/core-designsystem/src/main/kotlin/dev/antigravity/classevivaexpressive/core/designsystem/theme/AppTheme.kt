package dev.antigravity.classevivaexpressive.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode

@Immutable
data class AccentPreset(
  val name: String,
  val primary: Color,
  val secondary: Color,
  val tertiary: Color,
)

val expressiveAccentPresets = listOf(
  AccentPreset("expressive", Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
  AccentPreset("ember", Color(0xFF176B63), Color(0xFF233239), Color(0xFFC48A3A)),
  AccentPreset("ocean", Color(0xFF275F7A), Color(0xFF223541), Color(0xFFD0A15B)),
  AccentPreset("jade", Color(0xFF3F6D58), Color(0xFF27352F), Color(0xFFB98A5F)),
)

private fun presetFor(name: String): AccentPreset {
  return expressiveAccentPresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    ?: expressiveAccentPresets.first()
}

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
  val accent = presetFor(settings.customAccentName)
  val colors = when {
    settings.dynamicColorEnabled &&
      settings.accentMode == AccentMode.DYNAMIC &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark && (settings.themeMode == ThemeMode.AMOLED || settings.amoledEnabled) -> amoledScheme(accent)
    isDark -> darkBrandScheme(accent)
    else -> lightBrandScheme(accent)
  }

  MaterialTheme(
    colorScheme = colors,
    motionScheme = ClassevivaMotionScheme,
    shapes = Shapes(),
    typography = expressiveTypography(),
    content = content,
  )
}

private fun lightBrandScheme(accent: AccentPreset): ColorScheme = lightColorScheme(
  primary = accent.primary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDCECE8),
  onPrimaryContainer = Color(0xFF133C39),
  secondary = accent.secondary,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE9EEEC),
  onSecondaryContainer = Color(0xFF182228),
  tertiary = accent.tertiary,
  onTertiary = Color(0xFF271600),
  tertiaryContainer = Color(0xFFFFDDB6),
  onTertiaryContainer = Color(0xFF291800),
  background = Color(0xFFF5F4EF),
  surface = Color(0xFFFFFBF7),
  surfaceContainer = Color(0xFFF1F0EA),
  surfaceContainerHigh = Color(0xFFE9E7E1),
  surfaceContainerHighest = Color(0xFFE1DED7),
  onSurface = Color(0xFF161D1A),
  onSurfaceVariant = Color(0xFF55605B),
  error = Color(0xFFDC3545),
  errorContainer = Color(0xFFFFDAD6),
  onError = Color.White,
  onErrorContainer = Color(0xFF410002),
  outline = Color(0xFFCAD2CC),
)

private fun darkBrandScheme(accent: AccentPreset): ColorScheme = darkColorScheme(
  primary = accent.primary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF113632),
  onPrimaryContainer = Color(0xFFDCECE8),
  secondary = Color(0xFFC7D0CC),
  onSecondary = Color(0xFF172026),
  secondaryContainer = Color(0xFF1E2624),
  onSecondaryContainer = Color(0xFFE8EEEB),
  tertiary = accent.tertiary,
  onTertiary = Color(0xFF3B2B15),
  tertiaryContainer = Color(0xFF5A4221),
  onTertiaryContainer = Color(0xFFFFDDB6),
  background = Color(0xFF0E1110),
  surface = Color(0xFF131716),
  surfaceContainer = Color(0xFF191D1C),
  surfaceContainerHigh = Color(0xFF1F2523),
  surfaceContainerHighest = Color(0xFF272E2B),
  onSurface = Color(0xFFF0F3F1),
  onSurfaceVariant = Color(0xFFB8C1BC),
  error = Color(0xFFFFB4AB),
  outline = Color(0xFF3F4844),
)

private fun amoledScheme(accent: AccentPreset): ColorScheme = darkColorScheme(
  primary = accent.primary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF0B2824),
  onPrimaryContainer = Color(0xFFDCECE8),
  secondary = Color(0xFFE4E1E8),
  onSecondary = Color.Black,
  secondaryContainer = Color(0xFF0D0F0E),
  onSecondaryContainer = Color(0xFFF4F4F8),
  tertiary = accent.tertiary,
  onTertiary = Color(0xFF2E1E09),
  tertiaryContainer = Color(0xFF463310),
  onTertiaryContainer = Color(0xFFFFDDB6),
  background = Color.Black,
  surface = Color.Black,
  surfaceContainer = Color.Black,
  surfaceContainerHigh = Color(0xFF020202),
  surfaceContainerHighest = Color(0xFF060606),
  onSurface = Color(0xFFF0F3F1),
  onSurfaceVariant = Color(0xFFB8C1BC),
  error = Color(0xFFFFB4AB),
  outline = Color(0xFF141817),
)

private fun expressiveTypography(): Typography {
  return Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
  )
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
