package dev.antigravity.classevivaexpressive.core.data.notifications

import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSource

/**
 * I compiti da annunciare dopo una sincronizzazione.
 *
 * Tre cose da non fare, e il motivo per cui questa scelta sta qui e non sparsa nel dispatcher:
 * - **Annunciare tutto l'anno.** La prima volta che la sezione Compiti si legge
 *   ([dedicatedPrimed] falso) i suoi compiti sono nuovi per l'app, non per lo studente.
 * - **Annunciare due volte lo stesso compito.** Un compito d'agenda lo annuncia gia' il canale
 *   dell'agenda quando e' acceso ([agendaChannelOn]); e un compito della sezione che ha assorbito
 *   una riga d'agenda gia' vista non e' nuovo solo perche' ha cambiato id.
 */
internal fun homeworksToNotify(
  before: List<Homework>,
  after: List<Homework>,
  dedicatedPrimed: Boolean,
  agendaChannelOn: Boolean,
): List<Homework> {
  val known = before.flatMap { homework ->
    listOf(homework.id) + homework.linkedAgendaIds.map { agendaId -> "agenda-$agendaId" }
  }.toSet()
  return after.filter { homework ->
    homework.id !in known &&
      homework.linkedAgendaIds.none { agendaId -> "agenda-$agendaId" in known }
  }.filter { homework ->
    when (homework.source) {
      HomeworkSource.DEDICATED -> dedicatedPrimed
      HomeworkSource.AGENDA -> !agendaChannelOn
    }
  }
}

/**
 * Le righe d'agenda che il canale dell'agenda non deve annunciare perche' sono gia' un compito
 * della sezione Compiti, e lo annuncia quel canale.
 */
internal fun agendaIdsCoveredByDedicatedHomework(homeworks: List<Homework>): Set<String> {
  return homeworks
    .filter { it.source == HomeworkSource.DEDICATED }
    .flatMap(Homework::linkedAgendaIds)
    .toSet()
}
