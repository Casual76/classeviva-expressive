package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectKeys
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors
import dev.antigravity.fluidengine.ui.fluid.fluidRowPressable

/**
 * Il colore di ogni materia.
 *
 * Vive qui e non nell'engine per la stessa ragione delle fasce dei voti: che la storia sia beige e
 * la fisica verde e' una scelta di chi va a scuola, non di un design system. L'engine sa solo che
 * esistono superfici vivide ([FluidVividColors]); quale colore abbia una materia lo decide l'app.
 *
 * I default sono quelli scelti da Alessio; ognuno si cambia dalle Impostazioni, e la scelta si
 * salva per famiglia ([SubjectKeys]), non per nome, cosi' "Storia dell'arte" e "DISEGNO E STORIA
 * DELL'ARTE" restano lo stesso giallo.
 *
 * Dove si usa: la settimana sulle ore (orario e agenda) e la home. **Non** sui voti — li' la
 * superficie dice gia' com'e' andato il voto, e un secondo colore sulla stessa card sarebbe il
 * doppio indicatore che il vocabolario vivido esiste per evitare.
 */
@Immutable
data class SubjectSwatch(val label: String, val color: Color)

/** I colori che il selettore offre: i default e qualche parente, ciascuno con un nome da dire. */
val SubjectSwatches: List<SubjectSwatch> = listOf(
  SubjectSwatch("Bianco", Color(0xFFFFFFFF)),
  SubjectSwatch("Avorio", Color(0xFFEFE4D0)),
  SubjectSwatch("Beige", Color(0xFFD9C3A0)),
  SubjectSwatch("Nocciola", Color(0xFFA07850)),
  SubjectSwatch("Giallo", Color(0xFFF5C518)),
  SubjectSwatch("Arancio", Color(0xFFF08A24)),
  SubjectSwatch("Terracotta", Color(0xFFC0673A)),
  SubjectSwatch("Rosso", Color(0xFFD9534F)),
  SubjectSwatch("Rosa", Color(0xFFD96C9A)),
  SubjectSwatch("Prugna", Color(0xFF7B3F6E)),
  SubjectSwatch("Viola", Color(0xFF8E44AD)),
  SubjectSwatch("Indaco", Color(0xFF5B4DB8)),
  SubjectSwatch("Blu", Color(0xFF2F6FDE)),
  SubjectSwatch("Celeste", Color(0xFF7CC4F0)),
  SubjectSwatch("Acciaio", Color(0xFF4F7C99)),
  SubjectSwatch("Ardesia", Color(0xFF6B7A8F)),
  SubjectSwatch("Verde acqua", Color(0xFF1E9E9A)),
  SubjectSwatch("Acqua", Color(0xFF3FB8A5)),
  SubjectSwatch("Menta", Color(0xFF7ED6A5)),
  SubjectSwatch("Verde", Color(0xFF2FB344)),
  SubjectSwatch("Verde oliva", Color(0xFF8A9A3B)),
)

private fun swatch(label: String): Color = SubjectSwatches.first { it.label == label }.color

private val SubjectDefaults: Map<String, Color> = mapOf(
  SubjectKeys.Storia to swatch("Beige"),
  SubjectKeys.Scienze to swatch("Verde oliva"),
  SubjectKeys.Inglese to swatch("Viola"),
  SubjectKeys.Filosofia to swatch("Celeste"),
  SubjectKeys.Fisica to swatch("Verde"),
  SubjectKeys.Italiano to swatch("Bianco"),
  SubjectKeys.Informatica to swatch("Avorio"),
  SubjectKeys.Arte to swatch("Giallo"),
  SubjectKeys.Motorie to swatch("Rosso"),
  SubjectKeys.Religione to swatch("Indaco"),
  SubjectKeys.Matematica to swatch("Blu"),
  SubjectKeys.Latino to swatch("Terracotta"),
  SubjectKeys.Greco to swatch("Verde acqua"),
  SubjectKeys.Civica to swatch("Ardesia"),
  SubjectKeys.Geografia to swatch("Acqua"),
)

/**
 * Le materie che nessuno ha previsto (diritto, scienze umane, un laboratorio) prendono uno di
 * questi, scelto dal nome: sempre lo stesso per la stessa materia, perche' `String.hashCode` e'
 * fissato dalla specifica della JVM.
 */
private val SubjectFallbacks: List<Color> = listOf("Rosa", "Menta", "Prugna", "Acciaio", "Nocciola", "Arancio")
  .map(::swatch)

/** Il colore di default di una famiglia, prima di ogni scelta e prima del tema. */
fun subjectDefaultColor(key: String): Color =
  SubjectDefaults[key] ?: SubjectFallbacks[Math.floorMod(key.hashCode(), SubjectFallbacks.size)]

/**
 * Lo stesso colore nel tema scuro: i chiari si abbassano (un bianco pieno su fondo nero abbaglia e
 * non si legge come "italiano", si legge come un buco), e tutti perdono un poco di saturazione,
 * come ogni colore fisso del design system fa di sera.
 */
internal fun subjectFillForTheme(base: Color, isDark: Boolean): Color {
  if (!isDark) return base
  val dim = ((base.luminance() - 0.55f) / 0.45f).coerceIn(0f, 1f) * DarkMaxDim
  val lowered = lerp(base, Color.Black, dim)
  val grey = (lowered.red + lowered.green + lowered.blue) / 3f
  return lerp(lowered, Color(grey, grey, grey, lowered.alpha), DarkDesaturation)
}

/** Vero quando il colore non si stacca dalla superficie: il bianco e l'avorio sul tema chiaro. */
internal fun subjectNeedsOutline(fill: Color, surface: Color): Boolean =
  contrast(fill, surface) < OutlineContrastThreshold

/**
 * La superficie vivida di un colore di materia: un gradiente corto, verso lo scuro sui chiari e
 * verso il chiaro sugli scuri, con il contenuto scelto per contrasto e mai a occhio.
 */
internal fun subjectVividColors(fill: Color): FluidVividColors {
  val towards = if (fill.luminance() > 0.5f) Color.Black else Color.White
  return FluidVividColors.from(fill, lerp(fill, towards, 0.08f))
}

private fun contrast(first: Color, second: Color): Float {
  val high = maxOf(first.luminance(), second.luminance())
  val low = minOf(first.luminance(), second.luminance())
  return (high + 0.05f) / (low + 0.05f)
}

private const val DarkMaxDim = 0.16f
private const val DarkDesaturation = 0.10f
private const val OutlineContrastThreshold = 1.35f

/**
 * I colori delle materie risolti per il tema corrente e per le scelte di chi usa l'app.
 *
 * Una materia vuota (un impegno senza materia) prende un neutro della superficie: non un colore a
 * caso, che direbbe una materia che non c'e'.
 */
@Immutable
class SubjectPalette(
  private val overrides: Map<String, Int>,
  private val isDark: Boolean,
  private val surface: Color,
  private val onSurface: Color,
) {
  /** Il colore scelto (o di default) di una famiglia, prima del tema: quello che il selettore mostra. */
  fun baseForKey(key: String): Color = overrides[key]?.let(::Color) ?: subjectDefaultColor(key)

  fun isOverridden(key: String): Boolean = key in overrides

  fun fillForKey(key: String): Color = subjectFillForTheme(baseForKey(key), isDark)

  fun fill(subject: String?): Color =
    SubjectKeys.keyOf(subject)?.let(::fillForKey) ?: lerp(surface, onSurface, 0.12f)

  fun vivid(subject: String?): FluidVividColors = subjectVividColors(fill(subject))

  fun needsOutline(subject: String?): Boolean = subjectNeedsOutline(fill(subject), surface)

  /** Vero quando anche un colore scelto dal selettore va contornato per vedersi sulla superficie. */
  fun needsOutline(color: Color): Boolean = subjectNeedsOutline(subjectFillForTheme(color, isDark), surface)

  /** Il colore per un segno sottile: quello della materia, scurito quanto basta dove sparirebbe. */
  fun mark(subject: String?): Color {
    val fill = fill(subject)
    return if (subjectNeedsOutline(fill, surface)) lerp(fill, onSurface, 0.35f) else fill
  }

  /**
   * Il colore di un'icona della materia sopra il suo velo: la stessa tinta, scurita dove e' chiara
   * (il beige e il giallo su un velo beige e giallo sparirebbero) e schiarita dove e' scura nel
   * tema scuro. Il bianco dell'italiano diventa un grigio che si legge.
   */
  fun iconTint(subject: String?): Color {
    val fill = fill(subject)
    val luminance = fill.luminance()
    return when {
      !isDark && luminance > 0.35f -> lerp(fill, Color.Black, 0.25f + (luminance - 0.35f) * 0.6f)
      isDark && luminance < 0.25f -> lerp(fill, Color.White, 0.35f)
      else -> fill
    }
  }

  /** Il colore del contorno, per le superfici che non si staccano da sole. */
  val outline: Color get() = onSurface.copy(alpha = 0.16f)

  companion object {
    val LightDefaults = SubjectPalette(emptyMap(), isDark = false, surface = Color.White, onSurface = Color.Black)
  }
}

val LocalSubjectPalette = staticCompositionLocalOf { SubjectPalette.LightDefaults }

@Composable
fun subjectPalette(): SubjectPalette = LocalSubjectPalette.current

/**
 * Un pallino pieno di un colore di materia, contornato quando serve: per il selettore e per le
 * righe delle Impostazioni.
 */
@Composable
fun SubjectSwatchDot(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
  val palette = subjectPalette()
  val fill = subjectFillForTheme(color, isDarkAppearance())
  Box(
    modifier = modifier
      .size(size)
      .clip(ContinuousCornerShape(size / 2))
      .background(fill)
      .then(
        if (palette.needsOutline(color)) {
          Modifier.border(1.dp, palette.outline, ContinuousCornerShape(size / 2))
        } else {
          Modifier
        },
      ),
  )
}

/**
 * Un blocco di materia nella settimana sulle ore.
 *
 * Pieno, del colore della materia, perche' in una griglia oraria ogni blocco sta da solo: e' il
 * colore che fa leggere la settimana a colpo d'occhio, come un orario di carta. Fermo, come ogni
 * superficie vivida; la lezione in corso si dice con un anello, non con un movimento.
 *
 * [faint] e' la lezione che fa da sfondo agli impegni in agenda: un velo con una striscia, perche'
 * li' la materia e' contesto e non la cosa da guardare.
 */
@Composable
fun SubjectBlock(
  subject: String?,
  modifier: Modifier = Modifier,
  faint: Boolean = false,
  live: Boolean = false,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = subjectPalette()
  val shape = ContinuousCornerShape(FluidRadius.Control)
  val scheme = MaterialTheme.colorScheme
  val surfaceModifier = if (faint) {
    val fill = palette.fill(subject)
    val stripe = palette.mark(subject)
    Modifier
      .background(fill.copy(alpha = if (isDarkAppearance()) 0.20f else 0.26f))
      .drawBehind {
        drawRect(color = stripe, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height))
      }
  } else {
    val vivid = palette.vivid(subject)
    Modifier
      .background(Brush.verticalGradient(listOf(vivid.start, vivid.end)))
      .then(if (palette.needsOutline(subject)) Modifier.border(1.dp, palette.outline, shape) else Modifier)
  }
  val contentColor = if (faint) scheme.onSurface else palette.vivid(subject).content
  CompositionLocalProvider(LocalContentColor provides contentColor) {
    Column(
      modifier = modifier
        .clip(shape)
        .then(surfaceModifier)
        .then(if (live) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
        .fluidRowPressable(
          onClick = onClick,
          onLongClick = onLongClick,
          shape = shape,
          highlightColor = contentColor.copy(alpha = 0.10f),
        )
        .padding(contentPadding),
      content = content,
    )
  }
}

/**
 * L'icona di una materia: il libro per italiano, la provetta per scienze, la tavolozza per arte.
 * Una materia senza famiglia (o una supplenza) ha il cappello da scuola, un'ora senza nome l'orologio.
 */
fun subjectIcon(subject: String?): ImageVector = when (SubjectKeys.keyOf(subject)) {
  SubjectKeys.Italiano -> Icons.Rounded.AutoStories
  SubjectKeys.Storia -> Icons.Rounded.HistoryEdu
  SubjectKeys.Filosofia -> Icons.Rounded.Psychology
  SubjectKeys.Inglese -> Icons.Rounded.Translate
  SubjectKeys.Latino, SubjectKeys.Greco -> Icons.Rounded.AccountBalance
  SubjectKeys.Matematica -> Icons.Rounded.Calculate
  SubjectKeys.Fisica -> Icons.Rounded.Bolt
  SubjectKeys.Scienze -> Icons.Rounded.Science
  SubjectKeys.Informatica -> Icons.Rounded.Computer
  SubjectKeys.Arte -> Icons.Rounded.Palette
  SubjectKeys.Motorie -> Icons.AutoMirrored.Rounded.DirectionsRun
  SubjectKeys.Religione -> Icons.Rounded.Church
  SubjectKeys.Civica -> Icons.Rounded.Gavel
  SubjectKeys.Geografia -> Icons.Rounded.Public
  null -> Icons.Rounded.Schedule
  else -> Icons.Rounded.School
}

/**
 * L'icona di una materia nella piastrella di una riga ([dev.antigravity.fluidengine.ui.theme.FluidListRow]
 * con `tone = Neutral`): l'icona della materia nel suo colore, e dietro la piastrella velata della
 * stessa tinta. Il colore resta sulla piastrella, come vuole una riga dentro un gruppo.
 *
 * La piastrella dell'engine e' di 32 dp con il contenuto in 19 al centro: il velo si disegna fuori
 * dal contenuto, largo quanto la piastrella, perche' l'engine non accetta un colore qualunque.
 */
@Composable
fun SubjectRowIcon(subject: String?) {
  val palette = subjectPalette()
  val veil = palette.fill(subject).copy(alpha = if (isDarkAppearance()) 0.26f else 0.30f)
  val tint = palette.iconTint(subject)
  val shape = ContinuousCornerShape(FluidRadius.Small)
  Box(
    modifier = Modifier
      .size(19.dp)
      .drawBehind {
        val tile = 32.dp.toPx()
        val inset = (tile - size.width) / 2f
        val outline = shape.createOutline(Size(tile, tile), layoutDirection, this)
        translate(left = -inset, top = -inset) { drawOutline(outline, veil) }
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(imageVector = subjectIcon(subject), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
  }
}
