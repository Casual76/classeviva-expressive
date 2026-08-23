package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidHero
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMetric
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone

/**
 * Le sezioni di ClasseViva, e dove ognuna si colloca nella famiglia cromatica.
 *
 * Questo enum e' l'unica parte dell'intestazione editoriale che l'engine non puo' avere: sa cosa
 * sono i voti, l'agenda e le assenze. L'engine conosce solo un anello di toni e un insieme di
 * motivi astratti; qui si dice quale sezione sta dove.
 *
 * L'ordine sull'anello non e' casuale: sezioni che si visitano una dopo l'altra prendono toni
 * vicini, cosi' il passaggio non sembra un cambio di app.
 */
enum class FeatureIdentity(
  internal val tone: FluidHeroTone,
  internal val motif: FluidHeroMotif,
) {
  Overview(FluidHeroTone.Primary, FluidHeroMotif.Glow),
  Lessons(FluidHeroTone.PrimaryToSecondary, FluidHeroMotif.Cards),
  Agenda(FluidHeroTone.Secondary, FluidHeroMotif.Dots),
  Communications(FluidHeroTone.SecondaryToTertiary, FluidHeroMotif.Ripples),
  Grades(FluidHeroTone.Tertiary, FluidHeroMotif.Bars),
  People(FluidHeroTone.TertiaryToPrimary, FluidHeroMotif.Figures),
  Attendance(FluidHeroTone.Alert, FluidHeroMotif.Ticks),
}

/** Il tipo dell'engine, riesportato con il nome che le schermate usano gia'. */
typealias FeatureHeroMetric = FluidHeroMetric

/**
 * L'apertura editoriale di una schermata, con l'identita' della sezione al posto di tono e motivo.
 *
 * La firma resta quella che le sette schermate chiamano gia': il guscio serve proprio a non doverle
 * toccare quando cambia cosa c'e' sotto.
 */
@Composable
fun FeatureHero(
  identity: FeatureIdentity,
  eyebrow: String,
  value: String,
  title: String,
  description: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  metrics: List<FeatureHeroMetric> = emptyList(),
  urgent: Boolean = false,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  FluidHero(
    tone = identity.tone,
    motif = identity.motif,
    eyebrow = eyebrow,
    value = value,
    title = title,
    description = description,
    icon = icon,
    modifier = modifier,
    metrics = metrics,
    urgent = urgent,
    actionLabel = actionLabel,
    onAction = onAction,
  )
}
