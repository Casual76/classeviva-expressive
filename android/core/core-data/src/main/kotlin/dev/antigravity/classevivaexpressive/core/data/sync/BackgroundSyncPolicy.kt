package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.HomeworkSection
import dev.antigravity.classevivaexpressive.core.data.repository.LessonsSection
import dev.antigravity.classevivaexpressive.core.data.repository.NotesSection
import dev.antigravity.classevivaexpressive.core.data.repository.PeriodsSection
import dev.antigravity.classevivaexpressive.core.data.repository.ProfileSection
import dev.antigravity.classevivaexpressive.core.data.repository.SubjectsSection
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

enum class BackgroundSyncMode {
  FULL,
  FAST,
  MAINTENANCE,
}

internal object BackgroundSyncPolicy {
  private val schoolStart: LocalTime = LocalTime.of(6, 45)
  private val schoolEnd: LocalTime = LocalTime.of(18, 30)
  private val eveningEnd: LocalTime = LocalTime.of(22, 30)

  fun delayMinutes(
    now: LocalDateTime = LocalDateTime.now(),
    consecutiveFailures: Int = 0,
  ): Long {
    val baseDelay = when {
      now.dayOfWeek == DayOfWeek.SUNDAY -> 15L
      now.toLocalTime() >= schoolStart && now.toLocalTime() < schoolEnd -> 2L
      now.toLocalTime() >= schoolEnd && now.toLocalTime() < eveningEnd -> 5L
      else -> 15L
    }
    val failureDelay = when {
      consecutiveFailures <= 0 -> baseDelay
      consecutiveFailures == 1 -> 5L
      consecutiveFailures == 2 -> 10L
      else -> 15L
    }
    return maxOf(baseDelay, failureDelay)
  }

  fun fastSections(preferences: NotificationPreferences): Set<String> {
    if (!preferences.enabled) return emptySet()
    return buildSet {
      if (preferences.grades) add(GradesSection)
      if (preferences.agenda) add(AgendaSection)
      if (preferences.homework) add(HomeworkSection)
      if (preferences.communications) add(CommunicationsSection)
      if (preferences.notes) add(NotesSection)
      if (preferences.liveTimetable) add(LessonsSection)
    }
  }

  fun maintenanceSections(): Set<String> = setOf(
    ProfileSection,
    GradesSection,
    PeriodsSection,
    SubjectsSection,
    LessonsSection,
    HomeworkSection,
    AgendaSection,
    CommunicationsSection,
    NotesSection,
  )
}
