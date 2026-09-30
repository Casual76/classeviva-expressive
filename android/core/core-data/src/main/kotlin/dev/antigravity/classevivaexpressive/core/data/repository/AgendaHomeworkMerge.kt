package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource

/**
 * L'agenda con i compiti dentro, ognuno una volta sola.
 *
 * Un compito prende il posto delle righe d'agenda che dicono la stessa cosa: quelle che ha legato a
 * se' ([Homework.linkedAgendaIds]) e, per i compiti letti prima che il legame esistesse, quelle con
 * la stessa chiave di data, materia e testo.
 */
internal fun mergeHomeworksIntoAgenda(
  agenda: List<AgendaItem>,
  homeworks: List<Homework>,
): List<AgendaItem> {
  val linkedIds = homeworks.flatMap(Homework::linkedAgendaIds).toSet()
  val homeworkKeys = homeworks.map { homework ->
    agendaHomeworkKey(homework.dueDate, homework.subject, homework.description)
  }.toSet()
  val agendaWithoutDuplicates = agenda.filterNot { item ->
    item.category == AgendaCategory.HOMEWORK &&
      (item.id in linkedIds || agendaHomeworkKey(item.date, item.subject ?: item.subtitle, item.title) in homeworkKeys)
  }
  return agendaWithoutDuplicates + homeworks.map { homework ->
    AgendaItem(
      id = "homework-${homework.id}",
      title = homework.description,
      subtitle = homework.subject,
      date = homework.dueDate,
      time = null,
      detail = homework.notes,
      subject = homework.subject,
      teacher = homework.teacher,
      category = AgendaCategory.HOMEWORK,
      sharePayload = "${homework.subject} - ${homework.description} - ${homework.dueDate}",
      createdAt = homework.createdAt,
      history = homework.history,
    )
  }
}

/**
 * Ogni compito con quello che l'agenda sa di lui: da quando c'e', le note, e le versioni precedenti.
 *
 * Lo storico di un compito della sezione Compiti e' il suo ([homeworkHistory], per id); solo se non
 * ne ha si prende quello della riga d'agenda gemella, che e' quello che c'era prima che la sezione
 * si leggesse.
 */
internal fun enrichHomeworksFromAgenda(
  homeworks: List<Homework>,
  agenda: List<AgendaItem>,
  homeworkHistory: Map<String, List<AgendaItemVersion>>,
): List<Homework> {
  val agendaHomework = agenda.filter { it.category == AgendaCategory.HOMEWORK }
  val agendaById = agendaHomework.associateBy(AgendaItem::id)
  val agendaByKey = agendaHomework
    .groupBy { agendaHomeworkKey(it.date, it.subject ?: it.subtitle, it.title) }
    .mapValues { (_, items) ->
      items.sortedWith(
        compareByDescending<AgendaItem> { it.history.size }
          .thenByDescending { it.history.maxOfOrNull { version -> version.recordedAtEpochMillis } ?: Long.MIN_VALUE },
      ).first()
    }
  return homeworks.map { homework ->
    val ownAgendaId = homework.id.removePrefix("agenda-")
      .takeIf { homework.source == HomeworkSource.AGENDA && homework.id.startsWith("agenda-") }
    val twin = homework.linkedAgendaIds.firstNotNullOfOrNull(agendaById::get)
      ?: ownAgendaId?.let(agendaById::get)
      ?: agendaByKey[agendaHomeworkKey(homework.dueDate, homework.subject, homework.description)]
    val ownHistory = homeworkHistory[homework.id].orEmpty()
    homework.copy(
      createdAt = homework.createdAt ?: twin?.createdAt,
      history = ownHistory.ifEmpty { homework.history.ifEmpty { twin?.history.orEmpty() } },
      notes = homework.notes ?: twin?.detail,
      teacher = homework.teacher ?: twin?.teacher,
    )
  }
}

internal fun agendaHomeworkKey(date: String, subject: String?, text: String): String {
  return listOf(date, subject.orEmpty(), text)
    .joinToString("|")
    .lowercase()
    .replace(Regex("\\s+"), " ")
    .trim()
}
