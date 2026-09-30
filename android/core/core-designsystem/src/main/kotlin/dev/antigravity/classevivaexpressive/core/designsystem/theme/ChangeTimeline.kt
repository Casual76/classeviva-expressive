package dev.antigravity.classevivaexpressive.core.designsystem.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.antigravity.classevivaexpressive.core.domain.change.ChangeEntry
import dev.antigravity.classevivaexpressive.core.domain.change.ChangeField
import dev.antigravity.classevivaexpressive.core.domain.change.DiffKind
import dev.antigravity.classevivaexpressive.core.domain.change.DiffSegment
import dev.antigravity.classevivaexpressive.core.domain.change.FieldChange
import dev.antigravity.classevivaexpressive.core.domain.change.TextDiff
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Come si segnano le parole cambiate: una sola coppia di stili, per la cronologia e per le righe.
 *
 * Le parole aggiunte hanno un velo dell'accento dietro e il peso pieno; quelle tolte sono barrate,
 * nel colore dell'errore e un po' spente. Sono gli stessi segni che una revisione di un documento usa, e
 * nella riga d'agenda e nel dettaglio devono dire la stessa cosa.
 */
/** Quanti caratteri di testo uguale si tengono per parte intorno a una modifica, nella cronologia. */
private const val ChangeContextChars = 48

@Immutable
data class ChangeHighlight(
  val added: SpanStyle,
  val removed: SpanStyle,
)

/**
 * [onTint] per una superficie gia' tinta del proprio colore (il pop-up di un voto): li' un velo
 * dell'accento o il rosso dell'errore litigano con la tinta, e i segni si fanno col solo inchiostro.
 */
@Composable
fun changeHighlight(onTint: Boolean = false): ChangeHighlight {
  return if (onTint) {
    val ink = LocalContentColor.current
    ChangeHighlight(
      added = SpanStyle(fontWeight = FontWeight.Bold, background = ink.copy(alpha = 0.16f)),
      removed = SpanStyle(textDecoration = TextDecoration.LineThrough, color = ink.copy(alpha = 0.62f)),
    )
  } else {
    val scheme = MaterialTheme.colorScheme
    ChangeHighlight(
      // Un velo del primario e non `primaryContainer`: su un fondale gia' tinto dell'accento il
      // contenitore restava a quattro livelli di grigio dalla pagina (misurato sul Tab S9) e le
      // parole nuove non si distinguevano. Il testo tiene il suo colore, come sotto un evidenziatore.
      added = SpanStyle(
        fontWeight = FontWeight.SemiBold,
        background = scheme.primary.copy(alpha = 0.24f),
      ),
      removed = SpanStyle(
        textDecoration = TextDecoration.LineThrough,
        color = scheme.error.copy(alpha = 0.85f),
      ),
    )
  }
}

/**
 * Un testo con le sue modifiche segnate.
 *
 * Con [showRemoved] falso restano solo le parole di adesso, con evidenziate quelle nuove: e' la
 * forma della riga, che mostra il testo attuale e dice dove e' cambiato. Nel dettaglio si vedono
 * anche le parole tolte; fra una tolta e un'aggiunta che si toccano va uno spazio, o "290311" si
 * leggerebbe come un numero solo.
 */
fun annotatedChange(
  segments: List<DiffSegment>,
  highlight: ChangeHighlight,
  showRemoved: Boolean,
  contextChars: Int? = null,
): AnnotatedString = buildAnnotatedString {
  segments.forEachIndexed { index, segment ->
    when (segment.kind) {
      DiffKind.SAME -> append(
        if (contextChars == null) {
          segment.text
        } else {
          segment.text.trimmedAround(
            keepStart = index > 0,
            keepEnd = index < segments.lastIndex,
            contextChars = contextChars,
          )
        },
      )
      DiffKind.ADDED -> {
        if (showRemoved && segments.getOrNull(index - 1)?.kind == DiffKind.REMOVED) append(" ")
        withStyle(highlight.added) { append(segment.text) }
      }
      DiffKind.REMOVED -> if (showRemoved) withStyle(highlight.removed) { append(segment.text) }
    }
  }
}

/**
 * Il testo uguale intorno a una modifica, accorciato a qualche parola per parte.
 *
 * Un link aggiunto in fondo a un paragrafo di dieci righe si leggeva ripetendo le dieci righe: il
 * testo intero sta gia' nell'intestazione del dettaglio, qui serve solo a ritrovare il punto.
 * [keepStart] tiene l'inizio (il testo segue una modifica), [keepEnd] la fine (ne precede una).
 */
internal fun String.trimmedAround(keepStart: Boolean, keepEnd: Boolean, contextChars: Int): String {
  if (length <= contextChars * 2 + 8) return this
  val head = if (keepStart) take(contextChars).substringBeforeLast(' ', take(contextChars)) else ""
  val tail = if (keepEnd) takeLast(contextChars).substringAfter(' ', takeLast(contextChars)) else ""
  return when {
    keepStart && keepEnd -> "$head … $tail"
    keepStart -> "$head …"
    keepEnd -> "… $tail"
    else -> this
  }
}

/**
 * Cosa e' cambiato in un impegno, un compito o un voto: in cima al dettaglio, non in fondo.
 *
 * La modifica piu' recente e' aperta; le precedenti stanno dietro un tasto, perche' la domanda che
 * porta ad aprire un impegno segnato come modificato e' quasi sempre una sola — cosa c'e' di diverso
 * da ieri — e cinque versioni impilate la seppellivano. Ogni voce dice solo i campi che cambiano,
 * prima e dopo, e nei testi le parole.
 */
@Composable
fun ChangeTimeline(
  entries: List<ChangeEntry>,
  modifier: Modifier = Modifier,
  onTint: Boolean = false,
  title: String = "Cosa è cambiato",
  detectedAtLabel: (Long) -> String = ::gradeDateTimeLabel,
) {
  if (entries.isEmpty()) return
  var showOlder by rememberSaveable(entries.size, entries.first().detectedAtEpochMillis) { mutableStateOf(false) }
  val highlight = changeHighlight(onTint)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .animateContentSize(animationSpec = FluidMotion.intSize()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    FluidSectionHeader(title = title)
    ChangeEntryBlock(entries.first(), highlight, onTint, detectedAtLabel)
    val older = entries.drop(1)
    if (older.isNotEmpty()) {
      if (showOlder) {
        older.forEach { entry -> ChangeEntryBlock(entry, highlight, onTint, detectedAtLabel) }
      }
      FluidButton(
        text = if (showOlder) "Nascondi le modifiche precedenti" else "Modifiche precedenti (${older.size})",
        onClick = { showOlder = !showOlder },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    }
  }
}

@Composable
private fun ChangeEntryBlock(
  entry: ChangeEntry,
  highlight: ChangeHighlight,
  onTint: Boolean,
  detectedAtLabel: (Long) -> String,
) {
  val secondary = LocalContentColor.current.copy(alpha = 0.72f)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // "Rilevata" e non "modificata": e' l'ora in cui l'app se n'e' accorta, e il registro non dice
    // quando il docente l'ha fatto.
    detectedAtLabel(entry.detectedAtEpochMillis).takeIf(String::isNotBlank)?.let { label ->
      Text(text = "Rilevata $label", style = MaterialTheme.typography.labelMedium, color = secondary)
    }
    entry.changes.forEach { change -> FieldChangeLine(change, highlight, onTint) }
  }
}

@Composable
private fun FieldChangeLine(change: FieldChange, highlight: ChangeHighlight, onTint: Boolean) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = fieldLabel(change.field).uppercase(),
      style = MaterialTheme.typography.labelSmall,
      // Sopra una tinta il primario del tema si legge male: l'etichetta prende l'inchiostro.
      color = if (onTint) LocalContentColor.current.copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
    )
    val words = change.words
    when {
      words != null && TextDiff.changedShare(words) <= TextDiff.RewriteShare -> Text(
        text = annotatedChange(words, highlight, showRemoved = true, contextChars = ChangeContextChars),
        style = MaterialTheme.typography.bodyLarge,
      )
      // Riscritto quasi tutto: le parole segnate una per una si leggono peggio di due testi interi.
      words != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        change.before?.takeIf(String::isNotBlank)?.let { before ->
          Text(
            text = buildAnnotatedString {
              append("Prima  ")
              withStyle(highlight.removed) { append(before) }
            },
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Text(
          text = buildAnnotatedString {
            append("Ora  ")
            withStyle(highlight.added) { append(change.after.orEmpty().ifBlank { "—" }) }
          },
          style = MaterialTheme.typography.bodyLarge,
        )
      }
      else -> Text(
        text = buildAnnotatedString {
          withStyle(highlight.removed) { append(fieldValue(change.field, change.before)) }
          append("  →  ")
          withStyle(highlight.added) { append(fieldValue(change.field, change.after)) }
        },
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}

/**
 * Cosa e' cambiato, detto in poche parole per la riga: "data e testo", "orario".
 * Vuota se non c'e' niente da dire.
 */
fun changeSummary(entry: ChangeEntry): String {
  val names = entry.changes.map { fieldLabel(it.field).lowercase() }.distinct()
  return when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " e " + names.last()
  }
}

/**
 * La meta della riga di un impegno modificato. Se e' cambiata solo la data lo dice con la data di
 * prima ("Spostato dal 29 set"), che e' l'informazione che serve a chi l'aveva segnato altrove.
 */
fun changeMetaLabel(entry: ChangeEntry, detectedAtLabel: (Long) -> String): String {
  val moved = entry.changes.singleOrNull()?.takeIf { it.field == ChangeField.DATE }
  movedFromLabel(moved?.before)?.let { return it }
  val summary = changeSummary(entry)
  val at = detectedAtLabel(entry.detectedAtEpochMillis)
  return listOf("Modificato", at).filter(String::isNotBlank).joinToString(" ") +
    summary.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
}

private fun fieldLabel(field: ChangeField): String = when (field) {
  ChangeField.DATE -> "Data"
  ChangeField.TIME -> "Orario"
  ChangeField.CATEGORY -> "Tipo"
  ChangeField.SUBJECT -> "Materia"
  ChangeField.TITLE -> "Testo"
  ChangeField.DETAIL -> "Dettagli"
  ChangeField.TEACHER -> "Docente"
  ChangeField.VALUE -> "Voto"
  ChangeField.TYPE -> "Prova"
  ChangeField.WEIGHT -> "Peso"
  ChangeField.NOTES -> "Note"
}

private fun fieldValue(field: ChangeField, value: String?): String {
  val raw = value?.takeIf(String::isNotBlank) ?: return "—"
  return when (field) {
    ChangeField.DATE -> gradeDateLabel(raw.take(10))
    ChangeField.CATEGORY -> runCatching { AgendaCategory.valueOf(raw) }.getOrNull()?.let(::categoryName) ?: raw
    ChangeField.SUBJECT -> raw.asReadableSubject()
    else -> raw
  }
}

private fun categoryName(category: AgendaCategory): String = when (category) {
  AgendaCategory.LESSON -> "Lezione"
  AgendaCategory.HOMEWORK -> "Compito"
  AgendaCategory.ASSESSMENT -> "Verifica"
  AgendaCategory.EVENT -> "Evento"
  AgendaCategory.CUSTOM -> "Personalizzato"
}

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")
private val sameYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", italianLocale)

/** "Spostato dal 29 set", "Spostato dall'8 ott": l'articolo si elide davanti a uno, otto e undici. */
private fun movedFromLabel(isoDate: String?, today: LocalDate = LocalDate.now()): String? {
  val date = isoDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
  val preposition = if (date.dayOfMonth in setOf(1, 8, 11)) "dall'" else "dal "
  val label = if (date.year == today.year) date.format(sameYearFormatter) else gradeDateLabel(date.toString())
  return "Spostato $preposition$label"
}
