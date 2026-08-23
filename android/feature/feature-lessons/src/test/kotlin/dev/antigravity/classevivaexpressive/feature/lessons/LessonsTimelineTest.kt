package dev.antigravity.classevivaexpressive.feature.lessons

import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor

class LessonsTimelineTest {

  @Test
  fun schoolDays_alwaysKeepMondayThroughSaturday() {
    assertEquals(
      listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
      ),
      stableSchoolDays(),
    )
  }

  @Test
  fun timetableSections_keepEmptyDaysAndSortBlocksByTime() {
    val days = stableSchoolDays()
    val slots = mapOf(
      DayOfWeek.MONDAY to listOf(
        slot(DayOfWeek.MONDAY, "10:00", "Storia"),
        slot(DayOfWeek.MONDAY, "08:00", "Matematica"),
      ),
    )

    val sections = buildTimetableDaySections(days, slots)

    assertEquals(6, sections.size)
    assertEquals(listOf("08:00", "10:00"), sections.first().blocks.map { it.primary.time })
    assertTrue(sections.drop(1).all { it.blocks.isEmpty() })
    assertEquals(DayOfWeek.SATURDAY, sections.last().day)
  }

  @Test
  fun historySections_keepEveryDateAndSortLessonsByTime() {
    val monday = LocalDate.of(2026, 8, 17)
    val days = (0 until 6).map { monday.plusDays(it.toLong()) }
    val lessons = listOf(
      lesson("l2", monday, "10:00"),
      lesson("l1", monday, "08:00"),
      lesson("outside-week", monday.minusDays(1), "09:00"),
    )

    val sections = buildHistoryDaySections(days, lessons)

    assertEquals(6, sections.size)
    assertEquals(listOf("l1", "l2"), sections.first().lessons.map(Lesson::id))
    assertTrue(sections.drop(1).all { it.lessons.isEmpty() })
    assertEquals(DayOfWeek.SATURDAY, sections.last().date.dayOfWeek)
  }

  @Test
  fun activeAnchor_usesLatestHeaderThatCrossedTheActivationLine() {
    val anchors = listOf(
      FluidSectionAnchor("mon", "Lunedì", 6),
      FluidSectionAnchor("tue", "Martedì", 9),
      FluidSectionAnchor("wed", "Mercoledì", 12),
    )
    val indices = intArrayOf(8, 9, 10, 12)
    val offsets = intArrayOf(-120, 48, 180, 520)

    val active = activeTimelineAnchorForViewport(
      anchors = anchors,
      firstVisibleItemIndex = 8,
      activationLine = 64,
      visibleItemCount = indices.size,
      itemIndexAt = indices::get,
      itemOffsetAt = offsets::get,
    )

    assertEquals("tue", active?.key)
  }

  @Test
  fun activeAnchor_fallsBackToTheSectionOwningTheFirstVisibleItem() {
    val anchors = listOf(
      FluidSectionAnchor("mon", "Lunedì", 6),
      FluidSectionAnchor("tue", "Martedì", 9),
    )
    val indices = intArrayOf(7, 8)
    val offsets = intArrayOf(-20, 160)

    val active = activeTimelineAnchorForViewport(
      anchors = anchors,
      firstVisibleItemIndex = 7,
      activationLine = 64,
      visibleItemCount = indices.size,
      itemIndexAt = indices::get,
      itemOffsetAt = offsets::get,
    )

    assertEquals("mon", active?.key)
  }

  @Test
  fun timelineAnchors_accountForHeaderAndOneStableEmptyRow() {
    val anchors = buildLessonTimelineAnchors(
      sections = listOf(
        LessonTimelineSectionLayout("mon", "Lunedì", contentItemCount = 2),
        LessonTimelineSectionLayout("tue", "Martedì", contentItemCount = 0),
        LessonTimelineSectionLayout("wed", "Mercoledì", contentItemCount = 3),
      ),
      firstItemIndex = 6,
    )

    assertEquals(listOf(6, 9, 11), anchors.map { it.itemIndex })
  }

  @Test
  fun weekStart_preservesDirectionAroundCurrentWeek() {
    val wednesday = LocalDate.of(2026, 8, 19)
    assertEquals(LocalDate.of(2026, 8, 17), schoolWeekStart(0, wednesday))
    assertEquals(LocalDate.of(2026, 8, 10), schoolWeekStart(-1, wednesday))
    assertEquals(LocalDate.of(2026, 8, 24), schoolWeekStart(1, wednesday))
  }

  private fun slot(day: DayOfWeek, time: String, subject: String) = TemplateSlot(
    dayOfWeek = day.value,
    time = time,
    endTime = java.time.LocalTime.parse(time).plusHours(1).toString(),
    durationMinutes = 60,
    subject = subject,
  )

  private fun lesson(id: String, date: LocalDate, time: String) = Lesson(
    id = id,
    subject = "Materia $id",
    date = date.toString(),
    time = time,
    durationMinutes = 60,
  )
}
