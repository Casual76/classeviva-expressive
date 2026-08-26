package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors

/**
 * Le fasce di un voto, per il vocabolario vivido.
 *
 * Vive qui e non nell'engine per la stessa ragione di [gradeTone]: la soglia del sei, il gradino a
 * cinque, il sette e mezzo e il nove e mezzo sono regole della scuola italiana, non di un design
 * system.
 *
 * Cinque fasce perche' un dieci non e' un otto e un otto non e' un sei. Le due in mezzo sono due
 * famiglie diverse e non due sfumature dello stesso verde — acqua contro verde, con range di tinta
 * disgiunti — perche' due verdi vicini messi in colonna non si distinguono, e una scala che non si
 * distingue non e' una scala.
 *
 * [gradeTone] resta a tre gradini per pill e piastrelle nelle liste raggruppate: le cinque fasce
 * esistono solo dove il voto e' una superficie intera.
 */
enum class GradeBand {
  Insufficiente,
  Recuperabile,
  Sufficiente,
  Eccellente,

  /** Dal nove e mezzo: il colore dell'app, quando l'app ne ha uno abbastanza suo. */
  Lode,
}

fun gradeBand(score: Double?): GradeBand? = when {
  score == null -> null
  score >= 9.5 -> GradeBand.Lode
  score >= 7.5 -> GradeBand.Eccellente
  score >= 6.0 -> GradeBand.Sufficiente
  score >= 5.0 -> GradeBand.Recuperabile
  else -> GradeBand.Insufficiente
}

/**
 * I gradienti delle fasce.
 *
 * Coppie chiaro/scuro esplicite come ogni colore fisso del design system; il contenuto lo sceglie
 * [FluidVividColors.from] sul capo peggiore, mai a occhio.
 */
@Composable
fun gradeVividColors(band: GradeBand): FluidVividColors {
  val scheme = MaterialTheme.colorScheme
  return gradeVividColors(
    band = band,
    isDark = isDarkAppearance(),
    accentPrimary = scheme.primary,
    accentSecondary = scheme.secondary,
  )
}

/**
 * La stessa risoluzione come funzione pura: l'accento arriva da fuori, cosi' il ripiego della
 * [GradeBand.Lode] si verifica in un test invece che a occhio su un dispositivo.
 *
 * Non c'e' un default per l'accento di proposito. Un default qui sarebbe la trappola perfetta:
 * qualcuno chiama la versione corta, ottiene una Lode del colore sbagliato, e non se ne accorge
 * nessuno perche' il numero e' comunque leggibile.
 */
fun gradeVividColors(
  band: GradeBand,
  isDark: Boolean,
  accentPrimary: Color,
  accentSecondary: Color,
): FluidVividColors = when (band) {
  GradeBand.Insufficiente ->
    if (isDark) FluidVividColors.from(Color(0xFFFF453A), Color(0xFFFF375F))
    else FluidVividColors.from(Color(0xFFFF3B30), Color(0xFFFF2D55))

  GradeBand.Recuperabile ->
    if (isDark) FluidVividColors.from(Color(0xFFFF9F0A), Color(0xFFFFB340))
    else FluidVividColors.from(Color(0xFFFF9500), Color(0xFFFF6B00))

  // Verde acqua: menta verso teal. Parte dalla turchese di sistema e vira verso il blu senza mai
  // arrivarci — e' la fascia che deve leggersi come "verde-azzurro" a colpo d'occhio.
  GradeBand.Sufficiente ->
    if (isDark) FluidVividColors.from(Color(0xFF63E6E2), Color(0xFF40C8E0))
    else FluidVividColors.from(Color(0xFF00C7BE), Color(0xFF30B0C7))

  // Verde vero: smeraldo verso il verde di sistema. Tiene il capo che l'eccellenza aveva gia' e
  // gira lontano dall'acqua invece che verso, altrimenti le due fasce condividerebbero il capo.
  GradeBand.Eccellente ->
    if (isDark) FluidVividColors.from(Color(0xFF00D68F), Color(0xFF30D158))
    else FluidVividColors.from(Color(0xFF00A86B), Color(0xFF34C759))

  GradeBand.Lode -> lodeVividColors(isDark, accentPrimary, accentSecondary)
}

/** I colori della lode, o quelli dell'eccellenza quando l'accento non si distingue dalla scala. */
internal fun lodeVividColors(
  isDark: Boolean,
  accentPrimary: Color,
  accentSecondary: Color,
): FluidVividColors {
  val distinct = accentIsDistinctFromGradeScale(accentPrimary, accentSecondary, isDark)
  return if (distinct) {
    accentVividColors(accentPrimary, accentSecondary)
  } else {
    gradeVividColors(GradeBand.Eccellente, isDark, accentPrimary, accentSecondary)
  }
}

/**
 * Vero quando l'accento e' abbastanza suo da poter essere una fascia.
 *
 * Il colore dell'app e' cambiabile, e due preset stanno addosso alla scala: col preset Verde un
 * dieci sarebbe verde come un otto, con l'Arancio somiglierebbe a un cinque e mezzo. Quando succede
 * la lode ripiega sul verde dell'eccellenza: sopra il nove e mezzo il voto prende comunque il
 * colore migliore della scala, semplicemente non ne ha uno suo.
 *
 * Due colori si confondono solo se hanno **la stessa tinta e la stessa massa**, e serve la
 * congiunzione: il preset Blu dista 22 gradi dal capo della fascia acqua — dentro la soglia
 * angolare — ma un blu pieno e una teal chiara sono lontanissimi a vedersi, e infatti distano 0.62
 * per canale. Una regola sulla sola tinta toglierebbe la lode a un preset molto usato.
 */
internal fun accentIsDistinctFromGradeScale(
  accentPrimary: Color,
  accentSecondary: Color,
  isDark: Boolean,
): Boolean {
  val accent = accentVividColors(accentPrimary, accentSecondary)
  val accentEnds = listOf(accent.start, accent.end)
  val scaleEnds = FixedBands.flatMap { band ->
    val colors = gradeVividColors(band, isDark, accentPrimary, accentSecondary)
    listOf(colors.start, colors.end)
  }
  // Una sola collisione basta a far ripiegare: conservativo di proposito, meglio perdere il quinto
  // colore che avere un dieci e un sei che si somigliano.
  return accentEnds.none { accentEnd ->
    scaleEnds.any { scaleEnd ->
      hueDistanceDegrees(accentEnd, scaleEnd) < LodeHueMarginDegrees &&
        channelDistance(accentEnd, scaleEnd) < LodeChannelMargin
    }
  }
}

/** Le quattro fasce a colore fisso: quelle contro cui l'accento deve distinguersi. */
private val FixedBands = listOf(
  GradeBand.Insufficiente,
  GradeBand.Recuperabile,
  GradeBand.Sufficiente,
  GradeBand.Eccellente,
)

private const val LodeHueMarginDegrees = 30f
private const val LodeChannelMargin = 0.35f
