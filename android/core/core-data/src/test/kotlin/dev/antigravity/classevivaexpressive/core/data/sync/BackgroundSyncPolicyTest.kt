package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.AbsencesSection
import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.DocumentsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.LessonsSection
import dev.antigravity.classevivaexpressive.core.data.repository.MaterialsSection
import dev.antigravity.classevivaexpressive.core.data.repository.SchoolbooksSection
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSyncPolicyTest {

  @Test
  fun delayMinutes_usesSchoolEveningAndQuietCadence() {
    assertEquals(2L, BackgroundSyncPolicy.delayMinutes(LocalDateTime.of(2026, 6, 3, 7, 0)))
    assertEquals(5L, BackgroundSyncPolicy.delayMinutes(LocalDateTime.of(2026, 6, 3, 19, 0)))
    assertEquals(15L, BackgroundSyncPolicy.delayMinutes(LocalDateTime.of(2026, 6, 7, 12, 0)))
    assertEquals(15L, BackgroundSyncPolicy.delayMinutes(LocalDateTime.of(2026, 6, 3, 23, 0)))
  }

  @Test
  fun delayMinutes_appliesFailureBackoffWithoutShorteningQuietCadence() {
    val schoolTime = LocalDateTime.of(2026, 6, 3, 10, 0)
    assertEquals(5L, BackgroundSyncPolicy.delayMinutes(schoolTime, consecutiveFailures = 1))
    assertEquals(10L, BackgroundSyncPolicy.delayMinutes(schoolTime, consecutiveFailures = 2))
    assertEquals(15L, BackgroundSyncPolicy.delayMinutes(schoolTime, consecutiveFailures = 3))

    val quietTime = LocalDateTime.of(2026, 6, 3, 23, 0)
    assertEquals(15L, BackgroundSyncPolicy.delayMinutes(quietTime, consecutiveFailures = 1))
  }

  @Test
  fun fastSections_followEnabledNotificationCategories() {
    val sections = BackgroundSyncPolicy.fastSections(
      NotificationPreferences(
        enabled = true,
        homework = false,
        communications = true,
        absences = true,
        grades = true,
        agenda = true,
        notes = false,
        liveTimetable = true,
      ),
    )

    assertEquals(setOf(GradesSection, AgendaSection, CommunicationsSection, LessonsSection), sections)
  }

  @Test
  fun fastSections_emptyWhenNotificationsAreDisabled() {
    assertEquals(
      emptySet<String>(),
      BackgroundSyncPolicy.fastSections(NotificationPreferences(enabled = false)),
    )
  }

  @Test
  fun maintenanceSections_skipHeavyOnDemandSections() {
    val sections = BackgroundSyncPolicy.maintenanceSections()

    assertTrue(GradesSection in sections)
    assertTrue(AgendaSection in sections)
    assertTrue(CommunicationsSection in sections)
    assertFalse(AbsencesSection in sections)
    assertFalse(DocumentsSection in sections)
    assertFalse(MaterialsSection in sections)
    assertFalse(SchoolbooksSection in sections)
  }
}
