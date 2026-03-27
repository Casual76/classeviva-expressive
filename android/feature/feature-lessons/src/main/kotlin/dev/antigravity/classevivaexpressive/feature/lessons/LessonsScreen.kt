package dev.antigravity.classevivaexpressive.feature.lessons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEditorialCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveSimpleListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.subjectPalette
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val hourHeight = 78.dp
private const val scheduleStartHour = 7
private const val scheduleEndHour = 17

@HiltViewModel
class LessonsViewModel @Inject constructor(
  private val lessonsRepository: LessonsRepository,
) : ViewModel() {
  val lessons = lessonsRepository.observeLessons()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    viewModelScope.launch { lessonsRepository.refreshLessons() }
  }

  fun refresh() {
    viewModelScope.launch { lessonsRepository.refreshLessons(force = true) }
  }
}

@Composable
fun LessonsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: LessonsViewModel = hiltViewModel(),
) {
  val lessons by viewModel.lessons.collectAsStateWithLifecycle()
  var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

  val groupedByDay = remember(lessons) {
    lessons.groupBy { it.date }.toSortedMap()
  }
  val week = remember(groupedByDay) { groupedByDay.keys.take(5) }
  val selectedDayLessons = remember(groupedByDay, selectedDay, week) {
    val fallback = selectedDay ?: week.firstOrNull()
    fallback?.let { groupedByDay[it].orEmpty().sortedBy(Lesson::time) }.orEmpty()
  }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "Timetable",
        onBack = onBack,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
          }
        },
      )
    }
    if (week.isEmpty()) {
      item {
        EmptyState(
          title = "Timetable unavailable",
          detail = "Lessons will appear here as soon as the weekly schedule is available.",
        )
      }
    } else {
      item {
        TimetableGrid(
          week = week,
          lessonsByDay = groupedByDay,
          selectedDay = selectedDay ?: week.first(),
          onSelectDay = { selectedDay = it },
        )
      }
      item {
        Text(
          text = "Selected day",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium,
        )
      }
      if (selectedDayLessons.isEmpty()) {
        item {
          EmptyState(
            title = "No lessons for this day",
            detail = "Try another day or refresh the timetable.",
          )
        }
      } else {
        items(selectedDayLessons, key = { it.id }) { lesson ->
          ExpressiveSimpleListRow(
            title = lesson.subject,
            subtitle = lesson.topic ?: "No topic available",
            meta = listOfNotNull(lesson.time, lesson.teacher, lesson.room).joinToString(" - "),
          )
        }
      }
    }
  }
}

@Composable
private fun TimetableGrid(
  week: List<String>,
  lessonsByDay: Map<String, List<Lesson>>,
  selectedDay: String,
  onSelectDay: (String) -> Unit,
) {
  val scrollState = rememberScrollState()
  val gridHeight = hourHeight * (scheduleEndHour - scheduleStartHour)

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(scrollState),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SpacerTimeColumnHeader()
      week.forEach { day ->
        val label = day.toLocalDateOrNull()?.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)) ?: day
        Text(
          text = label.replaceFirstChar { it.uppercase() },
          modifier = Modifier
            .width(160.dp)
            .padding(horizontal = 4.dp)
            .background(
              color = if (day == selectedDay) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
              shape = MaterialTheme.shapes.large,
            )
            .padding(vertical = 8.dp),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          fontWeight = FontWeight.Medium,
        )
      }
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(scrollState),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      TimeColumn(gridHeight = gridHeight)
      week.forEach { day ->
        DayScheduleColumn(
          day = day,
          lessons = lessonsByDay[day].orEmpty(),
          gridHeight = gridHeight,
          selected = day == selectedDay,
          onSelect = { onSelectDay(day) },
        )
      }
    }
  }
}

@Composable
private fun SpacerTimeColumnHeader() {
  Box(modifier = Modifier.width(54.dp))
}

@Composable
private fun TimeColumn(
  gridHeight: androidx.compose.ui.unit.Dp,
) {
  Box(
    modifier = Modifier
      .width(54.dp)
      .height(gridHeight),
  ) {
    Column(
      modifier = Modifier.fillMaxHeight(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      (scheduleStartHour..scheduleEndHour).forEach { hour ->
        Text(
          text = "%02d:00".format(hour),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
        )
      }
    }
  }
}

@Composable
private fun DayScheduleColumn(
  day: String,
  lessons: List<Lesson>,
  gridHeight: androidx.compose.ui.unit.Dp,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  val columnWidth = 160.dp
  val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

  Surface(
    modifier = Modifier
      .width(columnWidth)
      .height(gridHeight),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 0.dp,
    onClick = onSelect,
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val hourLines = scheduleEndHour - scheduleStartHour
        repeat(hourLines + 1) { index ->
          val y = (size.height / hourLines) * index
          drawLine(
            color = gridLineColor,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
          )
        }
      }
      lessons.sortedBy(Lesson::time).forEach { lesson ->
        val lessonStart = lesson.time.toMinutesOfDay()
        val topHours = ((lessonStart - scheduleStartHour * 60).coerceAtLeast(0)).toFloat() / 60f
        val blockHeight = hourHeight * (lesson.durationMinutes / 60f)
        val subjectColor = subjectPalette(lesson.subject)
        Surface(
          modifier = Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .offset(y = hourHeight * topHours)
            .height(blockHeight),
          shape = MaterialTheme.shapes.medium,
          color = subjectColor,
          contentColor = Color.White,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = lesson.subject.uppercase(Locale.getDefault()),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Medium,
            )
            lesson.topic?.takeIf { it.isNotBlank() }?.let {
              Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
              )
            }
          }
        }
      }
      if (selected) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        )
      }
    }
  }
}

private fun String.toMinutesOfDay(): Int {
  val parts = split(":")
  val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
  val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
  return hour * 60 + minute
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}
