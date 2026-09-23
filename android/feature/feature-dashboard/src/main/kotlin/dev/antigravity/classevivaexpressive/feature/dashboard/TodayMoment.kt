package dev.antigravity.classevivaexpressive.feature.dashboard

import dev.antigravity.classevivaexpressive.core.designsystem.theme.MinuteSpan
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectKeys
import java.time.LocalTime

/**
 * A che punto e' la giornata: la domanda a cui la home di un tablet risponde prima di tutto.
 *
 * Gli indici puntano dentro la lista delle lezioni di oggi ordinata per ora, quella che la home
 * mostra sotto.
 */
internal sealed interface TodayMoment {
  /** In lezione: quanto manca alla fine (delle ore di fila della stessa materia) e cosa viene dopo. */
  data class Live(val index: Int, val minutesLeft: Int, val next: Int?) : TodayMoment

  /** Fra una lezione e l'altra, o prima della prima. */
  data class Next(val index: Int, val minutesUntil: Int) : TodayMoment

  data object Over : TodayMoment

  data object Empty : TodayMoment
}

/** La lezione in minuti dalla mezzanotte: la fine scritta dal registro, o l'inizio piu' la durata. */
internal fun Lesson.minuteSpan(): MinuteSpan? {
  val start = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
  val from = start.hour * 60 + start.minute
  val to = endTime?.takeIf(String::isNotBlank)
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    ?.let { it.hour * 60 + it.minute }
    ?.takeIf { it > from }
    ?: (from + durationMinutes.coerceAtLeast(1))
  return MinuteSpan(from, to)
}

/** Le lezioni di oggi nell'ordine della giornata. */
internal fun List<Lesson>.inDayOrder(): List<Lesson> = sortedBy { it.minuteSpan()?.start ?: Int.MAX_VALUE }

/**
 * Chiuso all'inizio e aperto alla fine, come la lezione in corso dell'orario: alle nove in punto
 * quella delle otto e' finita e quella delle nove e' cominciata.
 */
internal fun todayMoment(lessons: List<Lesson>, now: LocalTime): TodayMoment {
  if (lessons.isEmpty()) return TodayMoment.Empty
  val minute = now.hour * 60 + now.minute
  val spans = lessons.map { it.minuteSpan() }
  val live = spans.indexOfFirst { it != null && minute >= it.start && minute < it.end }
  if (live >= 0) {
    // Due ore di fila della stessa materia sono una lezione sola per chi aspetta la fine.
    var endIndex = live
    while (endIndex + 1 < lessons.size) {
      val current = spans[endIndex] ?: break
      val following = spans[endIndex + 1] ?: break
      val sameSubject = SubjectKeys.keyOf(lessons[endIndex + 1].subject) == SubjectKeys.keyOf(lessons[live].subject)
      if (!sameSubject || following.start - current.end !in 0..SameLessonGapMinutes) break
      endIndex += 1
    }
    val end = spans[endIndex]!!.end
    val next = spans.indices.firstOrNull { index -> index > endIndex && spans[index] != null }
    return TodayMoment.Live(index = live, minutesLeft = end - minute, next = next)
  }
  val upcoming = spans.indices
    .filter { spans[it] != null && spans[it]!!.start > minute }
    .minByOrNull { spans[it]!!.start }
  return if (upcoming != null) {
    TodayMoment.Next(index = upcoming, minutesUntil = spans[upcoming]!!.start - minute)
  } else {
    TodayMoment.Over
  }
}

private const val SameLessonGapMinutes = 5
