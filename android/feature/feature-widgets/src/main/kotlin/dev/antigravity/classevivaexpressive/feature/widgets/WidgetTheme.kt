package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import dev.antigravity.classevivaexpressive.core.designsystem.theme.classevivaColorScheme
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode

/**
 * A background paired with the content colour that stays readable on it.
 *
 * The app's grouped rows carry their category on the icon tile rather than on the row background,
 * and the widget follows the same rule: a pair is never split, so a tile can't end up tinted with a
 * colour its glyph can't be read against.
 */
internal data class WidgetTone(
  val container: ColorProvider,
  val content: ColorProvider,
)

/**
 * Every colour the widget draws, already resolved for both launcher themes.
 *
 * Glance hands its content to the home screen as RemoteViews, so nothing can be decided at draw
 * time: each role is a day/night pair the launcher picks from. Alphas are composited here against
 * the surface they sit on rather than left transparent, because a widget's parent is the launcher's
 * wallpaper, not the app's background.
 */
internal data class WidgetPalette(
  val background: ColorProvider,
  val card: ColorProvider,
  val hairline: ColorProvider,
  val onSurface: ColorProvider,
  val onSurfaceVariant: ColorProvider,
  val accent: ColorProvider,
  val accentContainer: ColorProvider,
  val onAccentContainer: ColorProvider,
  val attention: ColorProvider,
  val homework: WidgetTone,
  val assessment: WidgetTone,
  val event: WidgetTone,
  val grades: WidgetTone,
  val board: WidgetTone,
)

internal fun WidgetPalette.toneFor(type: WidgetUpcomingType): WidgetTone = when (type) {
  WidgetUpcomingType.HOMEWORK -> homework
  WidgetUpcomingType.ASSESSMENT -> assessment
  WidgetUpcomingType.EVENT -> event
}

/**
 * Builds the widget palette from the user's own app settings.
 *
 * The widget used to carry a frozen set of light-only greens, which is why it kept looking like the
 * previous app after the redesign. It now resolves the same scheme the app renders — accent, forced
 * theme mode, AMOLED and dynamic colour included — so changing the accent in Settings moves the
 * widget with it.
 */
internal fun widgetPalette(context: Context, settings: AppSettings): WidgetPalette {
  val light = widgetScheme(context, settings, isDark = false)
  val dark = widgetScheme(context, settings, isDark = true)

  fun pair(select: ColorScheme.() -> Color): ColorProvider =
    DayNightColorProvider(day = light.select(), night = dark.select())

  fun tone(container: ColorScheme.() -> Color, content: ColorScheme.() -> Color) = WidgetTone(
    container = pair(container),
    content = pair(content),
  )

  return WidgetPalette(
    background = pair { background },
    card = pair { surfaceContainerLow },
    // The app draws its row separators as onSurface at 10%. Over the launcher there is no
    // guaranteed backdrop to blend into, so the same value is flattened onto the card.
    hairline = DayNightColorProvider(
      day = light.onSurface.copy(alpha = HairlineAlpha).compositeOver(light.surfaceContainerLow),
      night = dark.onSurface.copy(alpha = HairlineAlpha).compositeOver(dark.surfaceContainerLow),
    ),
    onSurface = pair { onSurface },
    onSurfaceVariant = pair { onSurfaceVariant },
    accent = pair { primary },
    accentContainer = pair { primaryContainer },
    onAccentContainer = pair { onPrimaryContainer },
    attention = pair { error },
    // The categories keep the tones the agenda already gives them, so an orange row in the app is
    // an orange row on the home screen.
    homework = WidgetTone(
      container = tileContainer(light, dark, WarningContentLight, WarningContentDark),
      content = DayNightColorProvider(day = WarningContentLight, night = WarningContentDark),
    ),
    // An assessment takes the scheme's own error red rather than the agenda's Danger pair: that
    // pair resolves its content to near-black on a light background, which is legible in a list but
    // leaves the tile grey — the one category a glance should never have to read to recognise.
    assessment = WidgetTone(
      container = DayNightColorProvider(
        day = light.error.copy(alpha = TileAlpha).compositeOver(light.surfaceContainerLow),
        night = dark.error.copy(alpha = TileAlpha).compositeOver(dark.surfaceContainerLow),
      ),
      content = pair { error },
    ),
    event = WidgetTone(
      container = tileContainer(light, dark, SuccessContentLight, SuccessContentDark),
      content = DayNightColorProvider(day = SuccessContentLight, night = SuccessContentDark),
    ),
    grades = tone({ primaryContainer }, { onPrimaryContainer }),
    board = tone({ secondaryContainer }, { onSecondaryContainer }),
  )
}

/**
 * The scheme for one launcher theme.
 *
 * A theme the user has pinned in the app wins over the launcher's: with the app forced to Dark, the
 * widget stays dark on a light home screen, which is what every other surface of the app does.
 */
private fun widgetScheme(context: Context, settings: AppSettings, isDark: Boolean): ColorScheme {
  val resolvedDark = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK,
    ThemeMode.AMOLED,
    -> true
  }
  val dynamic = if (
    settings.dynamicColorEnabled &&
    settings.accentMode == AccentMode.DYNAMIC &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  ) {
    if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    null
  }
  return classevivaColorScheme(settings = settings, isDark = resolvedDark, dynamicScheme = dynamic)
}

private fun tileContainer(
  light: ColorScheme,
  dark: ColorScheme,
  lightContent: Color,
  darkContent: Color,
): ColorProvider = DayNightColorProvider(
  day = lightContent.copy(alpha = TileAlpha).compositeOver(light.surfaceContainerLow),
  night = darkContent.copy(alpha = TileAlpha).compositeOver(dark.surfaceContainerLow),
)

/** The same 16% the app's icon tiles use, and the same 10% for a hairline. */
private const val TileAlpha = 0.16f
private const val HairlineAlpha = 0.10f

// Warning and success have no Material role, so the app keeps fixed pairs for them. These are those
// pairs, kept in step with the design system's tone table.
private val WarningContentLight = Color(0xFFE65100)
private val WarningContentDark = Color(0xFFFFB74D)
private val SuccessContentLight = Color(0xFF1B5E20)
private val SuccessContentDark = Color(0xFF81C784)
