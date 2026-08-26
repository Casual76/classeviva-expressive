package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroBand
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
 *
 * Le sezioni figlie (raggiungibili da "Altro") riusano il tono della madre e si distinguono col
 * motivo: l'anello ha sette posizioni perche' sette sono le famiglie percettivamente distinte, e
 * una figlia che inventasse un tono suo romperebbe la parentela visiva con la sezione da cui si
 * arriva.
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

  /** Figlia di [People]: i colloqui sono persone. */
  Meetings(FluidHeroTone.TertiaryToPrimary, FluidHeroMotif.Ripples),

  /** Figlia di [Lessons]: la didattica arriva dalle lezioni. */
  Materials(FluidHeroTone.PrimaryToSecondary, FluidHeroMotif.Dots),

  /** Famiglia di [Agenda]: i compiti sono tempo. */
  Homework(FluidHeroTone.Secondary, FluidHeroMotif.Ticks),

  /** Famiglia di [Grades]: pagelle e documenti sono l'archivio dei voti. */
  Documents(FluidHeroTone.Tertiary, FluidHeroMotif.Cards),

  /** Famiglia di [Grades]: il punteggio e' un voto con la memoria lunga. */
  StudentScore(FluidHeroTone.Tertiary, FluidHeroMotif.Glow),

  /** La casa dell'app: il tono del marchio, senza pretese di sezione. */
  Settings(FluidHeroTone.Primary, FluidHeroMotif.Dots),
}

/**
 * Il fondale della sezione, dalla stessa identita' che ne disegna l'intestazione.
 *
 * Non c'e' niente da decidere qui, ed e' il punto: la mappa fra sezione, tono e motivo esiste gia'
 * sopra, e' gia' stata rivista, e il canvas la riusa invariata. Se il fondale avesse una tabella
 * propria, prima o poi una sezione avrebbe un'intestazione di un colore e un fondale di un altro, e
 * nessuno se ne accorgerebbe finche' non le si guarda una accanto all'altra.
 *
 * [urgent] promuove la sezione alla famiglia dell'errore esattamente come fa [FeatureHero]: le
 * assenze da giustificare sono rosse anche sotto la pagina, non solo nella fascia in cima.
 */
fun FeatureIdentity.ambient(urgent: Boolean = false, intensity: Float = 1f): FluidAmbient =
  FluidAmbient(tone = tone, motif = motif, intensity = intensity, urgent = urgent)

/**
 * L'apertura di una schermata, con l'identita' della sezione al posto di tono e motivo.
 *
 * Non e' piu' il pannello editoriale: e' la fascia satura di [FluidHeroBand] — colore di sezione
 * pieno, un fatto solo, e lo spazio che prima andava a metriche e descrizioni torna al contenuto
 * della pagina. Chi aveva metriche cliccabili le mette nella pagina, dove possono essere superfici
 * vere.
 */
@Composable
fun FeatureHero(
  identity: FeatureIdentity,
  eyebrow: String,
  value: String,
  label: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  urgent: Boolean = false,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  FluidHeroBand(
    tone = identity.tone,
    motif = identity.motif,
    eyebrow = eyebrow,
    value = value,
    label = label,
    icon = icon,
    modifier = modifier,
    urgent = urgent,
    trailing = trailing,
  )
}
