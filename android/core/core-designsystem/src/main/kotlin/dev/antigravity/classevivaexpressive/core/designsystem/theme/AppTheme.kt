package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode as AppAccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode as AppThemeMode
import dev.antigravity.fluidengine.foundation.AccentMode as EngineAccentMode
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode

/**
 * Il verde di ClasseViva, nelle due versioni che gli servono.
 *
 * Da questa coppia l'engine deriva l'intera scala di superfici: cambiare qui cambia l'app in modo
 * coerente. Prima era il seme di una palette scritta in questo modulo; ora e' l'unica cosa che
 * questo modulo ha ancora da dire sul colore.
 */
private val BrandAccent = AccentPreset(
  name = "classeviva",
  label = "Classeviva",
  light = Color(0xFF1F9E6E),
  dark = Color(0xFF2ED8A0),
)

/**
 * I preset che il selettore mostra.
 *
 * Gli stessi colori dell'engine, ma con i **nomi storici di ClasseViva**: le impostazioni salvano
 * la scelta per nome e la confrontano con `preset.name`, quindi rinominare "expressive" in "fluid"
 * farebbe apparire nessun preset come selezionato a chiunque abbia gia' scelto il blu.
 *
 * L'engine, dal canto suo, non conosce il nome "expressive" e ricade sul primo preset, che e' lo
 * stesso blu: il colore resta quello, cambia solo chi lo chiama come.
 */
val expressiveAccentPresets: List<AccentPreset> = listOf(
  AccentPreset("expressive", "Blu", Color(0xFF007AFF), Color(0xFF0A84FF)),
  AccentPreset("ember", "Arancio", Color(0xFFFF9500), Color(0xFFFF9F0A)),
  AccentPreset("ocean", "Indaco", Color(0xFF5856D6), Color(0xFF7D7AFF)),
  AccentPreset("jade", "Verde", Color(0xFF34C759), Color(0xFF30D158)),
)

fun classevivaBrandAccent(isDark: Boolean): Color = BrandAccent.resolve(isDark)

/**
 * Il tema dell'app, ora costruito sul Fluid Engine.
 *
 * La firma resta identica di proposito: nessuna delle schermate sa che sotto e' cambiato tutto.
 * Quello che c'era qui — la derivazione della palette, la scala delle superfici, le forme, il
 * motion scheme, la gestione delle barre di sistema — era la copia locale di quello che l'engine
 * fa adesso, ed era la copia da cui l'engine e' stato estratto.
 */
@Composable
fun ClassevivaExpressiveTheme(
  settings: AppSettings,
  content: @Composable () -> Unit,
) {
  FluidTheme(
    settings = settings.toEngine(),
    brand = BrandAccent,
    content = content,
  )
}

/**
 * La palette dell'app, risolta fuori da una composizione.
 *
 * Serve al widget della home: Glance consegna il suo contenuto al launcher, che risolve chiaro e
 * scuro per conto suo al momento di applicarlo, quindi il widget deve costruire entrambi gli schemi
 * in anticipo e non ha un `MaterialTheme` da cui leggerli.
 */
fun classevivaColorScheme(
  settings: AppSettings,
  isDark: Boolean,
  dynamicScheme: ColorScheme? = null,
): ColorScheme = fluidColorScheme(
  settings = settings.toEngine(),
  isDark = isDark,
  brand = BrandAccent,
  dynamicScheme = dynamicScheme,
)

/**
 * Da impostazioni dell'app a impostazioni dell'engine.
 *
 * Le due strutture si somigliano perche' l'engine e' nato da questa, ma restano separate apposta:
 * `AppSettings` continuera' a crescere di preferenze che a un design system non servono, e questa
 * funzione e' il punto in cui quella crescita si ferma.
 */
private fun AppSettings.toEngine(): EngineSettings = EngineSettings(
  themeMode = when (themeMode) {
    AppThemeMode.SYSTEM -> EngineThemeMode.SYSTEM
    AppThemeMode.LIGHT -> EngineThemeMode.LIGHT
    AppThemeMode.DARK -> EngineThemeMode.DARK
    AppThemeMode.AMOLED -> EngineThemeMode.AMOLED
  },
  accentMode = when (accentMode) {
    AppAccentMode.BRAND -> EngineAccentMode.BRAND
    AppAccentMode.DYNAMIC -> EngineAccentMode.DYNAMIC
    AppAccentMode.CUSTOM_PRESET -> EngineAccentMode.CUSTOM_PRESET
  },
  customAccentName = customAccentName,
  dynamicColorEnabled = dynamicColorEnabled,
  amoledEnabled = amoledEnabled,
)
