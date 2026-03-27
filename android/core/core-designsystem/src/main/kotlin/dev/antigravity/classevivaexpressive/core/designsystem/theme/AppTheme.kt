package dev.antigravity.classevivaexpressive.core.designsystem.theme

import android.os.Build
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
  AccentPreset("ember", Color(0xFFE84C3D), Color(0xFF26272F), Color(0xFFFF8A65)),
  AccentPreset("ocean", Color(0xFF006CFF), Color(0xFF0E1A2B), Color(0xFF00C2FF)),
  AccentPreset("jade", Color(0xFF1AA574), Color(0xFF111B17), Color(0xFFFFB454)),
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
  primaryContainer = accent.primary.copy(alpha = 0.12f),
  onPrimaryContainer = Color(0xFF1F1615),
  secondary = accent.secondary,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFF2F2F6),
  onSecondaryContainer = Color(0xFF1A1B20),
  tertiary = accent.tertiary,
  background = Color(0xFFF5F6FA),
  surface = Color.White,
  surfaceContainer = Color(0xFFF0F2F7),
  surfaceContainerHigh = Color(0xFFE7EAF1),
  surfaceContainerHighest = Color(0xFFDEE3EC),
  onSurface = Color(0xFF14161C),
  onSurfaceVariant = Color(0xFF585D69),
  error = Color(0xFFD23636),
  outline = Color(0xFFD0D4DE),
)

private fun darkBrandScheme(accent: AccentPreset): ColorScheme = darkColorScheme(
  primary = accent.primary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF2E1716),
  onPrimaryContainer = Color(0xFFFFDAD5),
  secondary = Color(0xFFCAC7D0),
  onSecondary = Color(0xFF1A1B22),
  secondaryContainer = Color(0xFF23242D),
  onSecondaryContainer = Color(0xFFF3F0F8),
  tertiary = accent.tertiary,
  background = Color(0xFF0D0E11),
  surface = Color(0xFF121317),
  surfaceContainer = Color(0xFF17191D),
  surfaceContainerHigh = Color(0xFF1D2025),
  surfaceContainerHighest = Color(0xFF25292F),
  onSurface = Color(0xFFF1F2F6),
  onSurfaceVariant = Color(0xFFB9BEC9),
  error = Color(0xFFFFB4AB),
  outline = Color(0xFF3D414C),
)

private fun amoledScheme(accent: AccentPreset): ColorScheme = darkColorScheme(
  primary = accent.primary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF220F10),
  onPrimaryContainer = Color(0xFFFFDAD5),
  secondary = Color(0xFFE4E1E8),
  onSecondary = Color.Black,
  secondaryContainer = Color(0xFF101114),
  onSecondaryContainer = Color(0xFFF4F4F8),
  tertiary = accent.tertiary,
  background = Color.Black,
  surface = Color.Black,
  surfaceContainer = Color.Black,
  surfaceContainerHigh = Color(0xFF020202),
  surfaceContainerHighest = Color(0xFF060606),
  onSurface = Color(0xFFF2F3F8),
  onSurfaceVariant = Color(0xFFBBC1CC),
  error = Color(0xFFFFB4AB),
  outline = Color(0xFF15171B),
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
    modifier = modifier.fillMaxWidth(),
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
