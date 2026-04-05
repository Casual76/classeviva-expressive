package dev.antigravity.classevivaexpressive.feature.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class LessonsUiState(
  val lessons: List<Lesson> = emptyList(),
  val lastMessage: String? = null,
  val isRefreshing: Boolean = false,
)

private data class DayOption(
  val date: String,
  val label: String,
)

@HiltViewModel
class LessonsViewModel @Inject constructor(
  private val lessonsRepository: LessonsRepository,
) : ViewModel() {
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)

  val state = combine(
    lessonsRepository.observeLessons(),
    lastMessage,
    isRefreshing,
  ) { lessons, message, refreshing ->
    LessonsUiState(
      lessons = lessons,
      lastMessage = message,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonsUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  fun clearMessage() {
    lastMessage.value = null
  }

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      lessonsRepository.refreshLessons(force = force)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare l'orario." }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: LessonsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val groupedByDay = remember(state.lessons) {
    state.lessons
      .groupBy { it.date }
      .toSortedMap()
  }
  val dayOptions = remember(groupedByDay) {
    groupedByDay.keys.map { date ->
      DayOption(
        date = date,
        label = date.toLessonDayLabel(),
      )
    }
  }
  var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

  LaunchedEffect(dayOptions) {
    if (selectedDay == null || dayOptions.none { it.date == selectedDay }) {
      selectedDay = dayOptions.firstOrNull()?.date
    }
  }

  val selectedLessons = remember(groupedByDay, selectedDay) {
    selectedDay?.let { day ->
      groupedByDay[day].orEmpty().sortedBy { it.time }
    }.orEmpty()
  }
  val teacherCount = remember(state.lessons) {
    state.lessons.mapNotNull { it.teacher?.takeIf(String::isNotBlank) }.distinct().size
  }
  val currentDayLabel = remember(dayOptions, selectedDay) {
    dayOptions.firstOrNull { it.date == selectedDay }?.date?.toLessonDayTitle() ?: "Settimana"
  }

  PullToRefreshBox(
    modifier = modifier.fillMaxSize(),
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      item {
        ExpressiveTopHeader(
          title = "Orario",
          subtitle = "Lezioni giornaliere ricostruite anche con fallback dagli eventi agenda, con refresh manuale sempre disponibile.",
          onBack = onBack,
          actions = {
            IconButton(onClick = viewModel::refresh) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
            }
          },
        )
      }
      if (!state.lastMessage.isNullOrBlank()) {
        item {
          RegisterListRow(
            title = "Aggiornamento orario",
            subtitle = state.lastMessage.orEmpty(),
            tone = ExpressiveTone.Warning,
            badge = { StatusBadge("AVVISO", tone = ExpressiveTone.Warning) },
          )
        }
        item {
          TextButton(onClick = viewModel::clearMessage) {
            Text("Nascondi messaggio")
          }
        }
      }
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricTile(
            label = "Lezioni",
            value = state.lessons.size.toString(),
            detail = "Voci trovate in settimana.",
            modifier = Modifier.fillMaxWidth(),
          )
          MetricTile(
            label = "Giorni",
            value = dayOptions.size.toString(),
            detail = "Date con orario disponibile.",
            modifier = Modifier.fillMaxWidth(),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Docenti",
            value = teacherCount.toString(),
            detail = "Nomi distinti rilevati.",
            modifier = Modifier.fillMaxWidth(),
            tone = ExpressiveTone.Success,
          )
        }
      }
      if (dayOptions.isEmpty()) {
        item {
          EmptyState(
            title = "Orario non disponibile",
            detail = "Le lezioni compariranno qui appena la sincronizzazione recupera il planning della settimana.",
          )
        }
      } else {
        item {
          ExpressivePillTabs(
            options = dayOptions.map { it.label },
            selected = dayOptions.firstOrNull { it.date == selectedDay }?.label ?: dayOptions.first().label,
            onSelect = { label ->
              selectedDay = dayOptions.firstOrNull { it.label == label }?.date
            },
          )
        }
        item {
          ExpressiveAccentLabel(currentDayLabel)
        }
        if (selectedLessons.isEmpty()) {
          item {
            EmptyState(
              title = "Nessuna lezione in questa giornata",
              detail = "Prova un altro giorno oppure aggiorna l'orario dalle API ufficiali.",
            )
          }
        } else {
          items(selectedLessons, key = { it.id }) { lesson ->
            RegisterListRow(
              title = lesson.subject,
              subtitle = lesson.topic?.takeIf(String::isNotBlank) ?: "Argomento non disponibile",
              eyebrow = lesson.timeRangeLabel(),
              meta = lesson.metaLabel(),
              tone = if (lesson.topic.isNullOrBlank()) ExpressiveTone.Neutral else ExpressiveTone.Primary,
              badge = {
                StatusBadge(
                  label = "${lesson.durationMinutes} min",
                  tone = ExpressiveTone.Info,
                )
              },
            )
          }
        }
      }
    }
  }
}

private fun Lesson.timeRangeLabel(): String {
  val start = time.toLocalTimeOrNull() ?: return time
  val end = start.plusMinutes(durationMinutes.toLong())
  return "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private fun Lesson.metaLabel(): String {
  return listOfNotNull(
    teacher?.takeIf(String::isNotBlank),
    room?.takeIf(String::isNotBlank),
    date.toLocalDateOrNull()?.format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale)),
  ).joinToString(" / ")
}

private fun String.toLessonDayLabel(): String {
  val date = toLocalDateOrNull() ?: return this
  return date.format(DateTimeFormatter.ofPattern("EEE d", italianLocale))
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(italianLocale) else it.toString() }
}

private fun String.toLessonDayTitle(): String {
  val date = toLocalDateOrNull() ?: return this
  return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", italianLocale))
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(italianLocale) else it.toString() }
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}

private fun String.toLocalTimeOrNull(): LocalTime? {
  return runCatching { LocalTime.parse(this) }.getOrNull()
}
