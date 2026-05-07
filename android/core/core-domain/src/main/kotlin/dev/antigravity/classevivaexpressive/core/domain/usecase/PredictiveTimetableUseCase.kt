package dev.antigravity.classevivaexpressive.core.domain.usecase

import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Algoritmo predittivo di nuova generazione.
 *
 * Idee chiave (rispetto alla versione precedente):
 *
 * 1. **Bell-grid discovery**: invece di "clusterare" le lezioni tollerando drift di
 *    centro, scopriamo prima la griglia delle campanelle (gli orari canonici di
 *    inizio/fine ora). Tutto viene poi allineato a quella griglia.
 *
 * 2. **Profilo per (giorno × campanella)**: ogni cella della settimana è valutata
 *    indipendentemente dalle altre. Niente influenza tra slot diversi.
 *
 * 3. **Change-detection**: se le ultime [RecencyWindowWeeks] settimane mostrano
 *    un profilo diverso da quello storico, scartiamo la storia per quella cella.
 *    Questo gestisce cambi di prof a metà anno, supplenti consolidati, riassetti.
 *
 * 4. **Profile-share threshold**: per accettare un profilo dominante deve avere
 *    una quota minima dei voti settimanali. Niente dominanze al 30%.
 *
 * 5. **Decay temporale meno aggressivo**: il decay serve solo per arbitrare tra
 *    profili candidati equilibrati. La decisione "use only recent" è gestita a
 *    parte, in modo deterministico.
 *
 * 6. **Robusto alle supplenze**: la chiave-profilo è (teacher, subject). Una
 *    supplenza occasionale (1 settimana su 8) non sposta il dominante.
 *
 * 7. **Cross-subject teachers**: educazione civica/orientamento vengono
 *    normalizzati al subject "primario" del docente come prima.
 */
class PredictiveTimetableUseCase {

  data class TimetableSlot(
    val time: LocalTime,
    val endTime: LocalTime? = null,
    val durationMinutes: Int,
    val subject: String,
    val teacher: String? = null,
    val room: String? = null,
    val confidence: Float,
    val isPredicted: Boolean = true,
    val topic: String? = null,
  )

  fun generateTimetableTemplate(
    lessons: List<Lesson>,
    recurrenceThreshold: Float = MinFrequency,
  ): TimetableTemplate {
    val parsed = parseLessons(normalizeSubjectsForTeachers(lessons))
    if (parsed.isEmpty()) return TimetableTemplate()

    val sampledWeeks = parsed.map { it.weekStart }.distinct().size
    if (sampledWeeks == 0) return TimetableTemplate()

    val referenceMonday = parsed.maxOf { it.weekStart }
    val bellSlots = discoverBellSchedule(parsed)
    if (bellSlots.isEmpty()) return TimetableTemplate(sampledWeeks = sampledWeeks)

    val snapped = parsed.flatMap { lesson -> snapLessonToBellSlots(lesson, bellSlots) }

    val templateSlots = mutableListOf<TemplateSlot>()
    DayOfWeek.entries.forEach { day ->
      bellSlots.forEach { bellSlot ->
        val cellSamples = snapped.filter { it.lesson.dayOfWeek == day && it.bellSlot.id == bellSlot.id }
        if (cellSamples.isEmpty()) return@forEach
        buildCellSlot(
          day = day,
          bellSlot = bellSlot,
          cellSamples = cellSamples,
          sampledWeeks = sampledWeeks,
          referenceMonday = referenceMonday,
          minFrequency = recurrenceThreshold,
        )?.let(templateSlots::add)
      }
    }

    return TimetableTemplate(
      slots = templateSlots.sortedWith(compareBy({ it.dayOfWeek }, { it.time })),
      sampledWeeks = sampledWeeks,
      lastComputedAt = System.currentTimeMillis(),
    )
  }

  fun getScheduleForDate(
    date: LocalDate,
    history: List<Lesson>,
    template: TimetableTemplate = generateTimetableTemplate(history),
  ): List<TimetableSlot> {
    val actualToday = history
      .filter { runCatching { LocalDate.parse(it.date) }.getOrNull() == date }
      .mapNotNull(::toActualSlot)

    val predictedToday = template.slots
      .filter { it.weekday == date.dayOfWeek }
      .mapNotNull(::toPredictedSlot)

    val visiblePredictions = predictedToday.filter { predicted ->
      actualToday.none { actual ->
        val diff = abs(predicted.time.toSecondOfDay() - actual.time.toSecondOfDay())
        diff < MergeToleranceMinutes * 60
      }
    }

    return (actualToday + visiblePredictions).sortedBy { it.time }
  }

  // -----------------------------------------------------------------------
  // STEP 1 — Parse lessons into ParsedLesson with start/end minute integers
  // -----------------------------------------------------------------------
  private fun parseLessons(lessons: List<Lesson>): List<ParsedLesson> {
    return lessons.mapNotNull { lesson ->
      val date = runCatching { LocalDate.parse(lesson.date) }.getOrNull() ?: return@mapNotNull null
      val start = lesson.time.takeIf(String::isNotBlank)
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?: return@mapNotNull null
      val end = lesson.endTime
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?: start.plusMinutes(lesson.durationMinutes.coerceAtLeast(DefaultDurationMinutes).toLong())
      if (!end.isAfter(start)) return@mapNotNull null
      val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
      ParsedLesson(
        lesson = lesson,
        date = date,
        weekStart = weekStart,
        dayOfWeek = date.dayOfWeek,
        startMinute = start.toSecondOfDay() / 60,
        endMinute = end.toSecondOfDay() / 60,
      )
    }
  }

  // -----------------------------------------------------------------------
  // STEP 2 — Bell-schedule discovery
  // -----------------------------------------------------------------------
  /**
   * Trova gli orari canonici di INIZIO ora cluster-izzando i campioni con
   * tolleranza stretta. Per ogni cluster prendiamo il minuto MODA come canonico.
   */
  private fun discoverBellSchedule(lessons: List<ParsedLesson>): List<BellSlot> {
    val starts = lessons.map { it.startMinute }.sorted()
    if (starts.isEmpty()) return emptyList()

    val totalCount = starts.size
    val minClusterSize = max(MinBellSupportAbsolute, (totalCount * MinBellSupportFraction).toInt())

    // Cluster i minuti di start con tolleranza stretta: i centri non driftano,
    // appartieni al cluster solo se sei entro BellDiscoveryTolerance dalla MEDIA.
    val clusters = mutableListOf<MutableList<Int>>()
    starts.forEach { minute ->
      val target = clusters.lastOrNull()
      val anchor = target?.let { it.sum().toDouble() / it.size }
      if (target != null && anchor != null && abs(minute - anchor) <= BellDiscoveryTolerance) {
        target += minute
      } else {
        clusters += mutableListOf(minute)
      }
    }

    // Filtra i cluster troppo piccoli (rumore, lezioni speciali isolate).
    val keptClusters = clusters.filter { it.size >= minClusterSize }
    if (keptClusters.isEmpty()) return emptyList()

    // Per ogni cluster: lo START canonico = MODA. L'END canonico = MODA degli end
    // delle lezioni il cui start è in quel cluster.
    return keptClusters.mapIndexed { idx, cluster ->
      val startMode = cluster.modeOrAverage()
      val matchingEnds = lessons
        .filter { it.startMinute >= cluster.min() - 1 && it.startMinute <= cluster.max() + 1 }
        .map { it.endMinute }
      val endMode = if (matchingEnds.isNotEmpty()) matchingEnds.modeOrAverage() else startMode + DefaultDurationMinutes
      BellSlot(
        id = idx,
        startMinute = startMode,
        endMinute = if (endMode > startMode) endMode else startMode + DefaultDurationMinutes,
      )
    }.sortedBy { it.startMinute }
  }

  // -----------------------------------------------------------------------
  // STEP 3 — Snap each lesson to the bell slots it occupies
  // -----------------------------------------------------------------------
  /**
   * Una lezione puo' coprire piu' campanelle (es. lab. di 2 ore registrate come
   * un singolo record). Ritorna una SnappedLesson per ogni campanella coperta.
   * Se la lezione e' singola, snap-pa alla campanella piu' vicina entro
   * [MaxSnapDistance].
   */
  private fun snapLessonToBellSlots(lesson: ParsedLesson, bellSlots: List<BellSlot>): List<SnappedLesson> {
    // Caso "spalmata": il record copre piu' campanelle consecutive.
    val covered = bellSlots.filter { slot ->
      slot.startMinute >= lesson.startMinute - SnapTolerance &&
        slot.endMinute <= lesson.endMinute + SnapTolerance
    }
    if (covered.size >= 2) {
      return covered.map { SnappedLesson(lesson, it) }
    }

    // Caso normale: snap a quella piu' vicina entro la tolleranza.
    val closest = bellSlots.minByOrNull { abs(it.startMinute - lesson.startMinute) } ?: return emptyList()
    if (abs(closest.startMinute - lesson.startMinute) > MaxSnapDistance) return emptyList()
    return listOf(SnappedLesson(lesson, closest))
  }

  // -----------------------------------------------------------------------
  // STEP 4 — Build TemplateSlot per (day × bellSlot) cell
  // -----------------------------------------------------------------------
  private fun buildCellSlot(
    day: DayOfWeek,
    bellSlot: BellSlot,
    cellSamples: List<SnappedLesson>,
    sampledWeeks: Int,
    referenceMonday: LocalDate,
    minFrequency: Float,
  ): TemplateSlot? {
    if (cellSamples.isEmpty()) return null

    // Per ogni settimana: profilo dominante quella settimana (se in una stessa
    // settimana ci sono piu' record per la stessa cella, ad es. supplente che
    // arriva tardi, prendiamo quello piu' frequente o il primo).
    val weekProfiles: Map<LocalDate, ProfileKey> = cellSamples
      .groupBy { it.lesson.weekStart }
      .mapValues { (_, samples) ->
        samples.groupingBy { it.lesson.profileKey() }.eachCount()
          .maxByOrNull { it.value }?.key
          ?: samples.first().lesson.profileKey()
      }

    if (weekProfiles.isEmpty()) return null

    val sortedWeeksDesc = weekProfiles.keys.sortedDescending()
    val recentWeeks = sortedWeeksDesc.take(RecencyWindowWeeks)
    val olderWeeks = sortedWeeksDesc.drop(RecencyWindowWeeks)

    val recentProfiles = recentWeeks.map { weekProfiles.getValue(it) }
    val recentDominant = recentProfiles.dominantWithCount()
    val olderProfiles = olderWeeks.map { weekProfiles.getValue(it) }
    val olderDominant = olderProfiles.dominantWithCount()

    val recentConsistency = if (recentProfiles.isEmpty()) 0f
    else (recentDominant?.second ?: 0).toFloat() / recentProfiles.size.toFloat()

    // Change detection: se le ultime settimane mostrano un profilo coerente
    // ma DIVERSO da quello storico → ignoriamo lo storico.
    val useOnlyRecent = recentProfiles.size >= RecencyMinSamples &&
      recentConsistency >= RecencyConsistencyThreshold &&
      olderDominant != null &&
      recentDominant != null &&
      recentDominant.first != olderDominant.first

    val activeWeeks = if (useOnlyRecent) recentWeeks else sortedWeeksDesc
    if (activeWeeks.isEmpty()) return null

    // Voto pesato finale per arbitrare profili "vicini" tra loro.
    val scored: Map<ProfileKey, Double> = activeWeeks
      .groupBy { weekProfiles.getValue(it) }
      .mapValues { (_, weeks) ->
        weeks.sumOf { week ->
          val ageWeeks = ChronoUnit.WEEKS.between(week, referenceMonday).coerceAtLeast(0L)
          exp(-ageWeeks.toDouble() / WeightDecayWeeks)
        }
      }

    val (dominantProfile, dominantWeight) = scored.maxByOrNull { it.value } ?: return null
    val totalWeight = scored.values.sum().takeIf { it > 0.0 } ?: return null
    val profileShare = (dominantWeight / totalWeight).toFloat()

    val dominantWeekCount = activeWeeks.count { weekProfiles.getValue(it) == dominantProfile }
    val effectiveSampledWeeks = if (useOnlyRecent) activeWeeks.size else sampledWeeks
    val frequency = dominantWeekCount.toFloat() / effectiveSampledWeeks.toFloat().coerceAtLeast(1f)

    // Soglie di accettazione: o frequente, o tante presenze.
    val passesFrequency = frequency >= minFrequency
    val passesCount = dominantWeekCount >= MinDominantWeekCount
    if (!passesFrequency && !passesCount) return null
    if (profileShare < MinProfileShare) return null

    // Estrai dettagli (subject/teacher/room) dai campioni con quel profilo.
    val matchingSamples = cellSamples.filter { it.lesson.profileKey() == dominantProfile }
    val representative = matchingSamples.maxByOrNull { it.lesson.date }?.lesson?.lesson
      ?: matchingSamples.first().lesson.lesson

    // Aula: prendiamo la moda fra le aule per il profilo dominante.
    val roomMode = matchingSamples
      .mapNotNull { it.lesson.lesson.room?.trim()?.takeIf(String::isNotBlank) }
      .takeIf { it.isNotEmpty() }
      ?.groupingBy { it }
      ?.eachCount()
      ?.maxByOrNull { it.value }
      ?.key

    val confidence = (frequency * 0.6f + profileShare * 0.4f).coerceIn(0f, 1f)

    return TemplateSlot(
      dayOfWeek = day.value,
      time = formatTime(bellSlot.startMinute),
      endTime = formatTime(bellSlot.endMinute),
      durationMinutes = (bellSlot.endMinute - bellSlot.startMinute).coerceAtLeast(DefaultDurationMinutes),
      subject = representative.subject,
      teacher = representative.teacher,
      room = roomMode ?: representative.room,
      confidence = confidence,
      sampleCount = dominantWeekCount,
    )
  }

  // -----------------------------------------------------------------------
  // Helpers — output adapters
  // -----------------------------------------------------------------------
  private fun toActualSlot(lesson: Lesson): TimetableSlot? {
    val start = lesson.time.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
      ?: return null
    val end = lesson.endTime
      ?.takeIf(String::isNotBlank)
      ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
      ?: start.plusMinutes(lesson.durationMinutes.coerceAtLeast(DefaultDurationMinutes).toLong())
    return TimetableSlot(
      time = start,
      endTime = end,
      durationMinutes = positiveDuration(start, end, lesson.durationMinutes),
      subject = lesson.subject,
      teacher = lesson.teacher,
      room = lesson.room,
      confidence = 1f,
      isPredicted = false,
      topic = lesson.topic,
    )
  }

  private fun toPredictedSlot(slot: TemplateSlot): TimetableSlot? {
    val start = runCatching { LocalTime.parse(slot.time) }.getOrNull() ?: return null
    val end = slot.endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
      ?: start.plusMinutes(slot.durationMinutes.toLong())
    return TimetableSlot(
      time = start,
      endTime = end,
      durationMinutes = positiveDuration(start, end, slot.durationMinutes),
      subject = slot.subject,
      teacher = slot.teacher,
      room = slot.room,
      confidence = slot.confidence,
    )
  }

  private fun positiveDuration(start: LocalTime, end: LocalTime, fallback: Int): Int {
    val diff = java.time.Duration.between(start, end).toMinutes().toInt()
    return if (diff > 0) diff else fallback.coerceAtLeast(DefaultDurationMinutes)
  }

  private fun formatTime(totalMinute: Int): String {
    val safe = totalMinute.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(safe / 60, safe % 60)
  }

  // -----------------------------------------------------------------------
  // Subject normalization for cross-subject teachers
  // -----------------------------------------------------------------------
  private fun normalizeSubjectsForTeachers(lessons: List<Lesson>): List<Lesson> {
    val teacherPrimarySubject: Map<String, String> = lessons
      .mapNotNull { lesson ->
        val teacher = lesson.teacher?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val subject = lesson.subject.trim()
        if (subject.lowercase() in CrossSubjectAliases) null else teacher to subject
      }
      .groupBy({ it.first }, { it.second })
      .mapValues { (_, subjects) ->
        subjects.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: subjects.first()
      }
    return lessons.map { lesson ->
      val teacher = lesson.teacher?.trim()?.takeIf { it.isNotBlank() } ?: return@map lesson
      if (lesson.subject.trim().lowercase() !in CrossSubjectAliases) return@map lesson
      val primary = teacherPrimarySubject[teacher] ?: return@map lesson
      lesson.copy(subject = primary)
    }
  }

  // -----------------------------------------------------------------------
  // Internal data classes
  // -----------------------------------------------------------------------
  private data class ParsedLesson(
    val lesson: Lesson,
    val date: LocalDate,
    val weekStart: LocalDate,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
  ) {
    /** Chiave-profilo: docente + materia. Robusta a supplenze occasionali. */
    fun profileKey(): ProfileKey {
      val teacher = lesson.teacher?.trim()?.lowercase().orEmpty()
      val subject = lesson.subject.trim().lowercase()
      return ProfileKey(teacher = teacher, subject = subject)
    }
  }

  private data class BellSlot(val id: Int, val startMinute: Int, val endMinute: Int)

  private data class SnappedLesson(val lesson: ParsedLesson, val bellSlot: BellSlot)

  private data class ProfileKey(val teacher: String, val subject: String)

  // -----------------------------------------------------------------------
  // Iterable helpers
  // -----------------------------------------------------------------------
  private fun List<Int>.modeOrAverage(): Int {
    if (isEmpty()) return 0
    val counts = groupingBy { it }.eachCount()
    val maxCount = counts.maxOf { it.value }
    val ties = counts.filterValues { it == maxCount }.keys
    if (ties.size == 1) return ties.first()
    // Tie-break: media degli intervalli vincitori arrotondata.
    return (ties.sum().toDouble() / ties.size).toInt()
  }

  private fun List<ProfileKey>.dominantWithCount(): Pair<ProfileKey, Int>? {
    if (isEmpty()) return null
    val counts = groupingBy { it }.eachCount()
    val (key, value) = counts.maxBy { it.value }
    return key to value
  }

  companion object {
    private const val DefaultDurationMinutes = 60
    private const val MergeToleranceMinutes = 10

    // Bell-grid discovery
    private const val BellDiscoveryTolerance = 7    // minuti dalla MEDIA del cluster
    private const val MinBellSupportAbsolute = 2     // minimo numero campioni per accettare un cluster
    private const val MinBellSupportFraction = 0.015 // o l'1.5% del totale

    // Snap
    private const val SnapTolerance = 12             // tolleranza per "lezione spalmata"
    private const val MaxSnapDistance = 18           // massimo distacco per snap singolo

    // Recency
    private const val RecencyWindowWeeks = 3
    private const val RecencyMinSamples = 2
    private const val RecencyConsistencyThreshold = 0.60f

    // Decay (usato solo per arbitrare profili equilibrati)
    private const val WeightDecayWeeks = 6.0

    // Soglie di accettazione del profilo
    private const val MinFrequency = 0.30f
    private const val MinDominantWeekCount = 2
    private const val MinProfileShare = 0.50f

    val CrossSubjectAliases = setOf(
      "educazione civica",
      "educazione civica (clil)",
      "orientamento",
      "cittadinanza e costituzione",
    )
  }
}
