package dev.antigravity.classevivaexpressive.feature.agenda

import dev.antigravity.classevivaexpressive.core.designsystem.theme.MinuteSpan
import dev.antigravity.classevivaexpressive.core.designsystem.theme.WeekTimeGridDefaults
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectKeys
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import java.time.LocalTime

/**
 * Dove sta un impegno nella settimana sulle ore.
 *
 * Classeviva da' un'ora solo a certi eventi; verifiche e compiti arrivano quasi sempre col giorno e
 * basta. Ma una verifica di storia il martedi' si fa all'ora di storia del martedi', ed e' li' che
 * chi guarda la settimana la cerca: quando l'orario lo sa, l'impegno si aggancia alla lezione della
 * sua materia, e solo quando nessuno lo sa finisce nella fascia "tutto il giorno" in cima.
 */
internal data class AgendaPlacementInput(
  val id: String,
  val subject: String?,
  val teacher: String?,
  val time: String?,
)

internal enum class AgendaAnchor { OwnTime, SubjectLesson, TeacherLesson, AllDay }

internal data class AgendaPlacement(val id: String, val span: MinuteSpan?, val anchor: AgendaAnchor)

/** Una lezione nella giornata: piu' ore di fila della stessa materia fanno una banda sola. */
internal data class LessonBand(val subject: String, val teacher: String?, val span: MinuteSpan)

/** Quanto puo' durare l'intervallo fra due ore della stessa materia perche' restino una banda. */
private const val BandJoinGapMinutes = 5

/** Un impegno con un'ora ma senza una lezione attorno occupa quest'ora. */
private const val OwnTimeMinutes = 60

internal fun lessonBands(daySlots: List<TemplateSlot>): List<LessonBand> {
  val timed = daySlots.mapNotNull { slot -> slot.minuteSpan()?.let { slot to it } }.sortedBy { it.second.start }
  val bands = mutableListOf<LessonBand>()
  timed.forEach { (slot, span) ->
    val last = bands.lastOrNull()
    if (last != null &&
      SubjectKeys.keyOf(last.subject) == SubjectKeys.keyOf(slot.subject) &&
      span.start - last.span.end in 0..BandJoinGapMinutes
    ) {
      bands[bands.lastIndex] = last.copy(span = MinuteSpan(last.span.start, maxOf(last.span.end, span.end)))
    } else {
      bands += LessonBand(subject = slot.subject, teacher = slot.teacher, span = span)
    }
  }
  return bands
}

internal fun placeAgendaDay(
  items: List<AgendaPlacementInput>,
  bands: List<LessonBand>,
  clamp: MinuteSpan = WeekTimeGridDefaults.Clamp,
): List<AgendaPlacement> = items.map { item ->
  val ownMinute = item.time?.let(::minuteOfDay)
    // Mezzanotte e' come Classeviva scrive "tutto il giorno": non e' un'ora.
    ?.takeIf { it != 0 && it >= clamp.start && it < clamp.end }
  val subjectKey = SubjectKeys.keyOf(item.subject)
  val teacher = item.teacher?.let(::teacherKey)?.takeIf { it.isNotEmpty() }
  when {
    ownMinute != null -> {
      val around = bands.firstOrNull { ownMinute >= it.span.start && ownMinute < it.span.end }
      AgendaPlacement(item.id, around?.span ?: MinuteSpan(ownMinute, ownMinute + OwnTimeMinutes), AgendaAnchor.OwnTime)
    }
    subjectKey != null && bands.any { SubjectKeys.keyOf(it.subject) == subjectKey } ->
      AgendaPlacement(item.id, bands.first { SubjectKeys.keyOf(it.subject) == subjectKey }.span, AgendaAnchor.SubjectLesson)
    teacher != null && bands.any { it.teacher?.let(::teacherKey) == teacher } ->
      AgendaPlacement(item.id, bands.first { it.teacher?.let(::teacherKey) == teacher }.span, AgendaAnchor.TeacherLesson)
    else -> AgendaPlacement(item.id, null, AgendaAnchor.AllDay)
  }
}

/** "ROSSI MARIO" e "Mario Rossi" sono lo stesso docente: conta l'insieme delle parole. */
private fun teacherKey(raw: String): Set<String> =
  SubjectKeys.normalize(raw).split(' ').filter { it.length > 1 }.toSet()

private fun minuteOfDay(raw: String): Int? =
  runCatching { LocalTime.parse(raw.trim().take(5)) }.getOrNull()?.let { it.hour * 60 + it.minute }

private fun TemplateSlot.minuteSpan(): MinuteSpan? {
  val start = minuteOfDay(time) ?: return null
  val end = endTime?.let(::minuteOfDay)?.takeIf { it > start } ?: (start + durationMinutes.coerceAtLeast(1))
  return MinuteSpan(start, end)
}
