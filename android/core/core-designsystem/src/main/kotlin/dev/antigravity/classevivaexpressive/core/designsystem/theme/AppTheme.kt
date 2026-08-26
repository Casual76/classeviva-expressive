package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.AccentPoles
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode as AppAccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode as AppThemeMode
import dev.antigravity.fluidengine.foundation.AccentMode as EngineAccentMode
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode

/**
 * L'ametista di ClasseViva, nelle due versioni che le servono.
 *
 * Da questa coppia l'engine deriva l'intera scala di superfici: cambiare qui cambia l'app in modo
 * coerente. Il nome resta "classeviva" perche' le impostazioni salvano la scelta per nome: e' il
 * marchio che cambia colore, non un preset nuovo.
 *
 * La saturazione sta con gli altri accenti di proposito: la prima ametista era a 0.50 mentre
 * l'indaco sta a 0.60 e il verde a 0.74, ed era l'unico colore della famiglia che sembrava spento.
 *
 * I poli servono perche' un viola sta a un passo dall'ancora storica del secondary (l'indaco iOS):
 * senza, l'anello a sette toni collassa in un viola solo. Con lo zaffiro e il quarzo rosa come
 * parenti, le tre famiglie restano gemme evidentemente imparentate ma distinguibili.
 */
private val BrandAccent = AccentPreset(
  name = "classeviva",
  label = "Ametista",
  light = Color(0xFF9D4EDD),
  dark = Color(0xFFC77DFF),
  poles = AccentPoles(
    secondaryLight = Color(0xFF007AFF),
    secondaryDark = Color(0xFF0A84FF),
    tertiaryLight = Color(0xFFFF2D55),
    tertiaryDark = Color(0xFFFF375F),
    secondaryBlend = 0.45f,
    tertiaryBlend = 0.40f,
  ),
)

/**
 * I preset che il selettore mostra.
 *
 * Gli stessi colori dell'engine, ma con i **nomi storici di ClasseViva**: le impostazioni salvano
 * la scelta per nome e la confrontano con `preset.name`, quindi rinominare "expressive" in "fluid"
 * farebbe apparire nessun preset come selezionato a chiunque abbia gia' scelto il blu.
 *
 * Questa lista viene passata a `FluidTheme(presets = ...)`, quindi i nomi si risolvono davvero:
 * niente piu' fallback fortuito sul primo preset dell'engine.
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
    presets = expressiveAccentPresets,
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
  presets = expressiveAccentPresets,
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
