package dev.antigravity.classevivaexpressive.core.designsystem.theme

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
  background = Color(0xFFF4F5F2),
  surface = Color.White,
  surfaceContainer = Color(0xFFEFF1EC),
  surfaceContainerHigh = Color(0xFFE7EAE3),
  surfaceContainerHighest = Color(0xFFDEE3DB),
  onSurface = Color(0xFF141917),
  onSurfaceVariant = Color(0xFF5A625E),
  error = Color(0xFFB42318),
  outline = Color(0xFFD1D8D1),
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
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
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

@Composable
fun ExpressiveHeroCard(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
        )
      }
      Box(contentAlignment = Alignment.Center) {
        trailing?.invoke() ?: Icon(
          imageVector = Icons.Rounded.School,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
fun ExpressiveCard(
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .animateContentSize(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (highlighted) {
        MaterialTheme.colorScheme.surfaceContainerHighest
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      content = content,
    )
  }
}

@Composable
fun SectionTitle(
  eyebrow: String,
  title: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = eyebrow.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
fun StatChip(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
fun QuickAction(
  label: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  AssistChip(
    modifier = modifier,
    onClick = onClick,
    label = { Text(label) },
    shape = RoundedCornerShape(999.dp),
  )
}

@Composable
fun EmptyState(
  title: String,
  detail: String,
  modifier: Modifier = Modifier,
) {
  ExpressiveCard(modifier = modifier, highlighted = true) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = detail,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun AppListItem(
  title: String,
  subtitle: String,
  supporting: String? = null,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  ExpressiveCard(
    modifier = if (onClick != null) {
      modifier.clickable(onClick = onClick)
    } else {
      modifier
    },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        supporting?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (trailing != null) {
        trailing()
      } else if (onClick != null) {
        Icon(
          imageVector = Icons.Rounded.ArrowForward,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
