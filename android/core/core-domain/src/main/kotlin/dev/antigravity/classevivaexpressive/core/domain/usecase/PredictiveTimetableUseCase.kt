package dev.antigravity.classevivaexpressive.core.domain.usecase

import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp

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
    recurrenceThreshold: Float = RecurrenceThreshold,
  ): TimetableTemplate {
    val samples = buildSamples(lessons)
    if (samples.isEmpty()) return TimetableTemplate()

    val sampledWeeks = samples.map { it.weekStart }.distinct().size
    if (sampledWeeks == 0) return TimetableTemplate()

    val slots = DayOfWeek.entries.flatMap { day ->
      clusterSamples(samples.filter { it.dayOfWeek == day })
        .mapNotNull { cluster -> buildTemplateSlot(cluster, sampledWeeks, recurrenceThreshold) }
        .sortedBy { it.time }
    }

    return TimetableTemplate(
      slots = slots,
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

  private fun buildSamples(lessons: List<Lesson>): List<LessonSample> {
    val datedLessons = lessons.mapNotNull { lesson ->
      val date = runCatching { LocalDate.parse(lesson.date) }.getOrNull() ?: return@mapNotNull null
      val time = lesson.time.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?: return@mapNotNull null
      val endTime = lesson.endTime
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?: time.plusMinutes(lesson.durationMinutes.coerceAtLeast(DefaultDurationMinutes).toLong())
      LessonWithDate(date = date, lesson = lesson, start = time, end = endTime)
    }

    val latestDate = datedLessons.maxOfOrNull(LessonWithDate::date) ?: return emptyList()
    return datedLessons.map { entry ->
      val weekStart = entry.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
      LessonSample(
        weekStart = weekStart,
        dayOfWeek = entry.date.dayOfWeek,
        start = entry.start,
        end = entry.end,
        durationMinutes = positiveDuration(entry.start, entry.end, entry.lesson.durationMinutes),
        ageWeeks = java.time.temporal.ChronoUnit.WEEKS.between(weekStart, latestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),
        lesson = entry.lesson,
      )
    }
  }

  private fun clusterSamples(samples: List<LessonSample>): List<List<LessonSample>> {
    if (samples.isEmpty()) return emptyList()
    val clusters = mutableListOf<MutableList<LessonSample>>()

    samples.sortedBy { it.start }.forEach { sample ->
      val sampleMinute = sample.start.toSecondOfDay() / 60
      val targetCluster = clusters.firstOrNull { cluster ->
        val anchor = cluster.map { it.start.toSecondOfDay() / 60 }.average()
        abs(sampleMinute - anchor.toInt()) <= MergeToleranceMinutes
      }
      if (targetCluster == null) {
        clusters += mutableListOf(sample)
      } else {
        targetCluster += sample
      }
    }

    return clusters
  }

  private fun buildTemplateSlot(
    cluster: List<LessonSample>,
    sampledWeeks: Int,
    recurrenceThreshold: Float,
  ): TemplateSlot? {
    if (cluster.isEmpty() || sampledWeeks == 0) return null

    val recurringWeeks = cluster.map { it.weekStart }.distinct().size
    val confidence = recurringWeeks.toFloat() / sampledWeeks.toFloat()
    if (confidence < recurrenceThreshold) return null

    val weightedProfiles = cluster.groupBy {
      Triple(
        it.lesson.subject.trim(),
        it.lesson.teacher?.trim().orEmpty(),
        it.lesson.room?.trim().orEmpty(),
      )
    }.mapValues { (_, entries) ->
      entries.sumOf { exp(-(it.ageWeeks.toDouble()) / WeightDecayWeeks) }
    }
    val dominantProfile = weightedProfiles.maxByOrNull { it.value }?.key ?: return null

    val startMinute = weightedMedian(
      cluster.map {
        WeightedValue(
          value = it.start.toSecondOfDay() / 60,
          weight = exp(-(it.ageWeeks.toDouble()) / WeightDecayWeeks),
        )
      },
    )
    val endMinute = weightedMedian(
      cluster.map {
        WeightedValue(
          value = it.end.toSecondOfDay() / 60,
          weight = exp(-(it.ageWeeks.toDouble()) / WeightDecayWeeks),
        )
      },
    )
    val durationMinutes = weightedMedian(
      cluster.map {
        WeightedValue(
          value = it.durationMinutes.coerceAtLeast(DefaultDurationMinutes),
          weight = exp(-(it.ageWeeks.toDouble()) / WeightDecayWeeks),
        )
      },
    ).coerceAtLeast(DefaultDurationMinutes)

    val resolvedStart = LocalTime.of(startMinute / 60, startMinute % 60)
    val resolvedEnd = if (endMinute > startMinute) {
      LocalTime.of(endMinute / 60, endMinute % 60)
    } else {
      resolvedStart.plusMinutes(durationMinutes.toLong())
    }

    return TemplateSlot(
      dayOfWeek = cluster.first().dayOfWeek.value,
      time = resolvedStart.toString().take(5),
      endTime = resolvedEnd.toString().take(5),
      durationMinutes = positiveDuration(resolvedStart, resolvedEnd, durationMinutes),
      subject = dominantProfile.first,
      teacher = dominantProfile.second.ifBlank { null },
      room = dominantProfile.third.ifBlank { null },
      confidence = confidence,
      sampleCount = recurringWeeks,
    )
  }

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

  private fun weightedMedian(values: List<WeightedValue>): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sortedBy { it.value }
    val totalWeight = sorted.sumOf { it.weight }.takeIf { it > 0.0 } ?: return sorted[sorted.size / 2].value
    var cumulative = 0.0
    val threshold = totalWeight / 2.0
    sorted.forEach { item ->
      cumulative += item.weight
      if (cumulative >= threshold) return item.value
    }
    return sorted.last().value
  }

  private fun positiveDuration(start: LocalTime, end: LocalTime, fallback: Int): Int {
    val diff = java.time.Duration.between(start, end).toMinutes().toInt()
    return if (diff > 0) diff else fallback.coerceAtLeast(DefaultDurationMinutes)
  }

  private data class WeightedValue(
    val value: Int,
    val weight: Double,
  )

  private data class LessonWithDate(
    val date: LocalDate,
    val lesson: Lesson,
    val start: LocalTime,
    val end: LocalTime,
  )

  private data class LessonSample(
    val weekStart: LocalDate,
    val dayOfWeek: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val durationMinutes: Int,
    val ageWeeks: Long,
    val lesson: Lesson,
  )

  companion object {
    private const val DefaultDurationMinutes = 60
    private const val MergeToleranceMinutes = 20
    private const val WeightDecayWeeks = 16.0
    private const val RecurrenceThreshold = 0.25f
  }
}
