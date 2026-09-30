package dev.antigravity.classevivaexpressive.core.domain.change

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Homework

/** Un campo di un impegno o di un voto che puo' cambiare fra due versioni. */
enum class ChangeField { DATE, TIME, CATEGORY, SUBJECT, TITLE, DETAIL, TEACHER, VALUE, TYPE, WEIGHT, NOTES }

/**
 * Un campo prima e dopo. Per i testi lunghi c'e' anche il confronto parola per parola ([words]);
 * [before] e [after] restano i valori grezzi — una data e' ISO, chi la mostra la formatta.
 */
data class FieldChange(
  val field: ChangeField,
  val before: String?,
  val after: String?,
  val words: List<DiffSegment>? = null,
)

/**
 * Una modifica, cioe' il passaggio da una versione alla successiva.
 *
 * [detectedAtEpochMillis] e' quando l'app se n'e' accorta, non quando il docente l'ha fatta: il
 * registro non lo dice, e le due cose distano quanto un giro di sincronizzazione.
 */
data class ChangeEntry(
  val detectedAtEpochMillis: Long,
  val changes: List<FieldChange>,
)

private val TextFields = setOf(ChangeField.TITLE, ChangeField.DETAIL, ChangeField.NOTES)

/** L'impegno com'e' adesso, nella forma delle sue versioni precedenti. */
fun AgendaItem.toVersion(recordedAtEpochMillis: Long = 0L): AgendaItemVersion = AgendaItemVersion(
  recordedAtEpochMillis = recordedAtEpochMillis,
  title = title,
  subtitle = subtitle,
  date = date,
  time = time,
  detail = detail,
  subject = subject,
  teacher = teacher,
  category = category,
  sharePayload = sharePayload,
  createdAt = createdAt,
)

fun Grade.toVersion(recordedAtEpochMillis: Long = 0L): GradeVersion = GradeVersion(
  recordedAtEpochMillis = recordedAtEpochMillis,
  subject = subject,
  valueLabel = valueLabel,
  numericValue = numericValue,
  description = description,
  date = date,
  type = type,
  weight = weight,
  notes = notes,
  period = period,
  periodCode = periodCode,
  teacher = teacher,
  color = color,
)

/**
 * I campi che cambiano passando da questa versione a [newer].
 *
 * Gli stessi campi e la stessa regola della sincronizzazione ([ChangeText]): il docente conta solo
 * se c'e' da tutte e due le parti, perche' e' l'unica cosa che una risposta piu' povera del
 * registro fa sparire senza che nessuno l'abbia toccata.
 */
fun AgendaItemVersion.fieldChangesTo(
  newer: AgendaItemVersion,
  includeOneSidedText: Boolean = true,
): List<FieldChange> = buildList {
  if (ChangeText.comparableChanged(date, newer.date)) add(FieldChange(ChangeField.DATE, date, newer.date))
  if (category != newer.category) add(FieldChange(ChangeField.CATEGORY, category.name, newer.category.name))
  if (ChangeText.comparableChanged(time, newer.time)) add(FieldChange(ChangeField.TIME, time, newer.time))
  val subjectBefore = subjectLabel()
  val subjectAfter = newer.subjectLabel()
  if (ChangeText.comparableChanged(subjectBefore, subjectAfter)) add(FieldChange(ChangeField.SUBJECT, subjectBefore, subjectAfter))
  if (ChangeText.coreChanged(title, newer.title, includeOneSidedText)) add(textChange(ChangeField.TITLE, title, newer.title))
  if (ChangeText.coreChanged(detail, newer.detail, includeOneSidedText)) add(textChange(ChangeField.DETAIL, detail, newer.detail))
  if (ChangeText.comparableChanged(teacher, newer.teacher)) add(FieldChange(ChangeField.TEACHER, teacher, newer.teacher))
}

/** Come per gli impegni: i campi che cambiano passando da questo voto a [newer]. */
fun GradeVersion.fieldChangesTo(
  newer: GradeVersion,
  includeOneSidedText: Boolean = true,
): List<FieldChange> = buildList {
  if (ChangeText.gradeValueChanged(numericValue, valueLabel, newer.numericValue, newer.valueLabel)) {
    add(FieldChange(ChangeField.VALUE, valueLabel, newer.valueLabel))
  }
  if (ChangeText.comparableChanged(date, newer.date)) add(FieldChange(ChangeField.DATE, date, newer.date))
  if (ChangeText.comparableChanged(subject, newer.subject)) add(FieldChange(ChangeField.SUBJECT, subject, newer.subject))
  if (ChangeText.coreChanged(description, newer.description, includeOneSidedText)) {
    add(textChange(ChangeField.DETAIL, description, newer.description))
  }
  if (ChangeText.coreChanged(notes, newer.notes, includeOneSidedText)) add(textChange(ChangeField.NOTES, notes, newer.notes))
  if (ChangeText.comparableChanged(ChangeText.significantGradeType(type), ChangeText.significantGradeType(newer.type))) {
    add(FieldChange(ChangeField.TYPE, type, newer.type))
  }
  if (ChangeText.numberChanged(weight, newer.weight)) {
    add(FieldChange(ChangeField.WEIGHT, weight?.let(::trimNumber), newer.weight?.let(::trimNumber)))
  }
}

/**
 * Le versioni precedenti che raccontano davvero una modifica, dalla piu' recente.
 *
 * Ognuna si confronta con quella che l'ha seguita, non con l'attuale: con A → B → A la A di prima
 * e' diversa da B, e nasconderla perche' uguale a oggi cancellava proprio la parte della storia
 * che sorprende. Si scarta solo una versione identica a quella che la segue — il rumore di una
 * lettura registrata due volte.
 */
fun <T> meaningfulVersionChain(
  current: T,
  newestFirst: List<T>,
  differs: (older: T, newer: T) -> Boolean,
): List<T> {
  var successor = current
  return newestFirst.filter { version ->
    val keep = differs(version, successor)
    if (keep) successor = version
    keep
  }
}

/**
 * Un compito nella forma delle versioni d'agenda, che e' quella in cui lo storico lo conserva e lo
 * mostra.
 */
fun Homework.toAgendaVersion(recordedAtEpochMillis: Long = 0L): AgendaItemVersion = AgendaItemVersion(
  recordedAtEpochMillis = recordedAtEpochMillis,
  title = description,
  subtitle = subject,
  date = dueDate,
  detail = notes,
  subject = subject.takeIf(String::isNotBlank),
  teacher = teacher,
  category = AgendaCategory.HOMEWORK,
  createdAt = createdAt,
)

/** Le modifiche di un impegno, dalla piu' recente. [AgendaItem.history] e' gia' dalla piu' recente. */
fun AgendaItem.changeTimeline(): List<ChangeEntry> = agendaChangeTimeline(toVersion(), history)

/** Le modifiche a partire da com'e' adesso ([current]) e dalle versioni precedenti, dalla piu' recente. */
fun agendaChangeTimeline(current: AgendaItemVersion, newestFirst: List<AgendaItemVersion>): List<ChangeEntry> =
  timeline(current, newestFirst, { it.recordedAtEpochMillis }) { older, newer -> older.fieldChangesTo(newer) }

fun Grade.changeTimeline(): List<ChangeEntry> =
  timeline(toVersion(), history, { it.recordedAtEpochMillis }) { older, newer -> older.fieldChangesTo(newer) }

/**
 * Il titolo di adesso confrontato con quello della versione precedente, o null se non e' cambiato:
 * e' quello che la riga evidenzia.
 */
fun AgendaItem.latestTitleDiff(): List<DiffSegment>? = latestTitleDiff(title, history)

fun latestTitleDiff(currentTitle: String, newestFirst: List<AgendaItemVersion>): List<DiffSegment>? {
  val previous = newestFirst.firstOrNull() ?: return null
  if (!ChangeText.coreChanged(previous.title, currentTitle, includeOneSidedText = true)) return null
  return TextDiff.words(previous.title, currentTitle)
}

private fun <V> timeline(
  current: V,
  newestFirst: List<V>,
  recordedAt: (V) -> Long,
  changes: (older: V, newer: V) -> List<FieldChange>,
): List<ChangeEntry> {
  return newestFirst.mapIndexedNotNull { index, older ->
    val newer = if (index == 0) current else newestFirst[index - 1]
    changes(older, newer).takeIf(List<FieldChange>::isNotEmpty)?.let { ChangeEntry(recordedAt(older), it) }
  }
}

private fun textChange(field: ChangeField, before: String?, after: String?): FieldChange {
  require(field in TextFields)
  return FieldChange(field, before, after, words = TextDiff.words(before.orEmpty(), after.orEmpty()))
}

private fun AgendaItemVersion.subjectLabel(): String = subject?.takeIf(String::isNotBlank) ?: subtitle

private fun trimNumber(value: Double): String =
  if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().replace('.', ',')
