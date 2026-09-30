package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.domain.change.toAgendaVersion
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectKeys

/**
 * Sotto questa lunghezza due testi devono essere uguali per dirsi lo stesso compito: "Studiare"
 * contenuto in "Studiare pag. 40" non e' un indizio, e' una coincidenza.
 */
private const val MinContainedTextLength = 12

/**
 * Unisce la sezione Compiti del registro con i compiti riconosciuti in agenda.
 *
 * Il compito della sezione vince: ha id stabile, docente e scadenza propri. Un compito d'agenda che
 * dice la stessa cosa — stessa scadenza, stesso testo, materia compatibile — non si mostra una
 * seconda volta: confluisce nel primo, che si ricorda da quali righe d'agenda viene
 * ([Homework.linkedAgendaIds]) perche' l'agenda possa nascondere il gemello anche dove il testo
 * dell'agenda porta un prefisso che la chiave per testo non riconosce.
 *
 * Quelli senza testo o senza scadenza si scartano, come prima: un compito senza data non ha un
 * giorno in cui mostrarsi, e metterlo a oggi era il difetto da cui si partiva.
 */
internal fun mergeHomeworkSources(
  dedicated: List<Homework>,
  agendaDerived: List<Homework>,
): List<Homework> {
  val remaining = agendaDerived.filter(Homework::isUsable).toMutableList()
  val merged = dedicated.filter(Homework::isUsable).map { homework ->
    val twins = remaining.filter { candidate -> candidate.isTwinOf(homework) }
    if (twins.isEmpty()) return@map homework
    remaining.removeAll(twins)
    homework.copy(
      linkedAgendaIds = (homework.linkedAgendaIds + twins.flatMap(Homework::agendaIds)).distinct(),
      createdAt = homework.createdAt ?: twins.firstNotNullOfOrNull(Homework::createdAt),
      notes = homework.notes ?: twins.firstNotNullOfOrNull(Homework::notes),
      history = homework.history.ifEmpty { twins.flatMap(Homework::history) },
    )
  }
  return (merged + remaining).sortedWith(
    compareBy<Homework> { it.dueDate }.thenBy { it.subject }.thenBy { it.description },
  )
}

/**
 * Le versioni precedenti dei compiti della sezione Compiti che sono cambiati fra due letture.
 *
 * Il confronto e' per id, che nella sezione e' stabile. Conta quello che si legge: scadenza,
 * materia, testo, note, docente. Non conta [Homework.done], che e' lo studente a cambiare, non il
 * docente, e che una cronologia delle modifiche del registro non deve riportare.
 */
internal fun dedicatedHomeworkRevisions(
  previous: List<Homework>,
  incoming: List<Homework>,
  recordedAtEpochMillis: Long,
): List<Pair<String, AgendaItemVersion>> {
  val previousById = previous.associateBy(Homework::id)
  return incoming.mapNotNull { current ->
    val before = previousById[current.id] ?: return@mapNotNull null
    if (!before.differsFrom(current)) return@mapNotNull null
    current.id to before.toAgendaVersion(recordedAtEpochMillis)
  }
}

private fun Homework.isUsable(): Boolean = description.isNotBlank() && dueDate.isNotBlank()

private fun Homework.agendaIds(): List<String> {
  val own = id.removePrefix("agenda-").takeIf { source == HomeworkSource.AGENDA && id.startsWith("agenda-") }
  return listOfNotNull(own) + linkedAgendaIds
}

private fun Homework.isTwinOf(dedicated: Homework): Boolean {
  if (dueDate != dedicated.dueDate) return false
  if (!subjectsCompatible(subject, dedicated.subject)) return false
  val mine = comparableText(description)
  val theirs = comparableText(dedicated.description)
  if (mine == theirs) return true
  val shorter = if (mine.length <= theirs.length) mine else theirs
  val longer = if (shorter === mine) theirs else mine
  return shorter.length >= MinContainedTextLength && longer.contains(shorter)
}

private fun subjectsCompatible(first: String?, second: String?): Boolean {
  if (first.isNullOrBlank() || second.isNullOrBlank()) return true
  val firstKey = SubjectKeys.keyOf(first)
  val secondKey = SubjectKeys.keyOf(second)
  if (firstKey == null || secondKey == null) return true
  return firstKey == secondKey
}

private fun Homework.differsFrom(other: Homework): Boolean {
  return dueDate != other.dueDate ||
    comparableText(subject) != comparableText(other.subject) ||
    comparableText(description) != comparableText(other.description) ||
    comparableText(notes) != comparableText(other.notes) ||
    comparableText(teacher) != comparableText(other.teacher)
}

private fun comparableText(value: String?): String =
  value.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim()
