package dev.antigravity.classevivaexpressive.feature.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAB_TIMETABLE = "Orario"
private const val TAB_HISTORY = "Lezioni svolte"
private val italianLocale: Locale = Locale.forLanguageTag("it-IT")
private val weekdayShortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", italianLocale)
private val weekHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale)

data class LessonsUiState(
  val lessons: List<Lesson> = emptyList(),
  val timetableTemplate: TimetableTemplate = TimetableTemplate(),
  val totalTeachersCount: Int = 0,
  val lastMessage: String? = null,
  val isRefreshing: Boolean = false,
)

private data class DayOption(
  val key: String,
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
    lessonsRepository.observeTimetableTemplate(),
    lastMessage,
    isRefreshing,
  ) { lessons, timetableTemplate, message, refreshing ->
    val teachers = lessons.mapNotNull { it.teacher?.takeIf(String::isNotBlank) }.distinct().size
    LessonsUiState(
      lessons = lessons.sortedBy { "${it.date}-${it.time}" },
      timetableTemplate = timetableTemplate,
      totalTeachersCount = teachers,
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
  var selectedTab by rememberSaveable { mutableStateOf(TAB_TIMETABLE) }
  var selectedTemplateDayKey by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedHistoryDayKey by rememberSaveable { mutableStateOf<String?>(null) }
  var weekOffset by rememberSaveable { mutableStateOf(0) }

  val templateByDay = remember(state.timetableTemplate) { state.timetableTemplate.slotsByDay() }
  val hasSaturday = remember(state.lessons, state.timetableTemplate) {
    state.timetableTemplate.hasLessonsOn(DayOfWeek.SATURDAY) ||
      state.lessons.any { it.date.toLocalDateOrNull()?.dayOfWeek == DayOfWeek.SATURDAY }
  }
  val visibleDays = remember(hasSaturday) {
    buildList {
      addAll(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
      if (hasSaturday) add(DayOfWeek.SATURDAY)
    }
  }
  val templateDayOptions = remember(visibleDays) {
    visibleDays.map { day ->
      DayOption(
        key = day.name,
        label = day.shortLabel(),
      )
    }
  }
  val templateSlots = remember(templateByDay, selectedTemplateDayKey) {
    selectedTemplateDayKey
      ?.let { key -> templateByDay[DayOfWeek.valueOf(key)] }
      .orEmpty()
  }

  val currentWeekStart = remember(weekOffset) {
    LocalDate.now()
      .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
      .plusWeeks(weekOffset.toLong())
  }
  val historyDays = remember(currentWeekStart, hasSaturday) {
    (0 until if (hasSaturday) 6 else 5).map { currentWeekStart.plusDays(it.toLong()) }
  }
  val historyDayOptions = remember(historyDays) {
    historyDays.map { date ->
      DayOption(
        key = date.toString(),
        label = date.format(weekdayShortFormatter).replaceFirstChar { it.uppercase() },
      )
    }
  }
  val weekLessons = remember(state.lessons, historyDays) {
    val validDates = historyDays.toSet()
    state.lessons.filter { lesson -> lesson.date.toLocalDateOrNull() in validDates }
  }
  val lessonsForSelectedDay = remember(weekLessons, selectedHistoryDayKey) {
    weekLessons.filter { it.date == selectedHistoryDayKey }
  }

  LaunchedEffect(templateDayOptions) {
    if (selectedTemplateDayKey == null || templateDayOptions.none { it.key == selectedTemplateDayKey }) {
      val todayKey = LocalDate.now().dayOfWeek.name
      selectedTemplateDayKey = if (templateDayOptions.any { it.key == todayKey }) todayKey else templateDayOptions.firstOrNull()?.key
    }
  }

  LaunchedEffect(historyDayOptions) {
    if (selectedHistoryDayKey == null || historyDayOptions.none { it.key == selectedHistoryDayKey }) {
      val todayKey = LocalDate.now().toString()
      selectedHistoryDayKey = if (historyDayOptions.any { it.key == todayKey }) todayKey else historyDayOptions.firstOrNull()?.key
    }
  }

  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Orario",
        subtitle = "Template settimanale stabile e storico delle lezioni svolte in una sola vista.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
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
          ExpressivePillTabs(
            options = listOf(TAB_TIMETABLE, TAB_HISTORY),
            selected = selectedTab,
            onSelect = { selectedTab = it },
          )
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            MetricTile(
              label = if (selectedTab == TAB_TIMETABLE) "Slot stabili" else "Lezioni",
              value = if (selectedTab == TAB_TIMETABLE) {
                state.timetableTemplate.slots.size.toString()
              } else {
                weekLessons.size.toString()
              },
              detail = if (selectedTab == TAB_TIMETABLE) "Template riutilizzabile." else "Settimana selezionata.",
              modifier = Modifier.weight(1f),
              tone = ExpressiveTone.Info,
            )
            MetricTile(
              label = "Settimane",
              value = state.timetableTemplate.sampledWeeks.toString(),
              detail = "Storico usato per il template.",
              modifier = Modifier.weight(1f),
              tone = ExpressiveTone.Success,
            )
            MetricTile(
              label = "Docenti",
              value = state.totalTeachersCount.toString(),
              detail = "Nomi distinti rilevati.",
              modifier = Modifier.weight(1f),
            )
          }
        }

        when (selectedTab) {
          TAB_TIMETABLE -> {
            if (templateDayOptions.isEmpty()) {
              item {
                EmptyState(
                  title = "Orario non disponibile",
                  detail = "Il template settimanale apparira qui appena la sincronizzazione raccoglie abbastanza lezioni.",
                )
              }
            } else {
              item {
                ExpressivePillTabs(
                  options = templateDayOptions.map { it.label },
                  selected = templateDayOptions.firstOrNull { it.key == selectedTemplateDayKey }?.label ?: templateDayOptions.first().label,
                  onSelect = { label ->
                    selectedTemplateDayKey = templateDayOptions.firstOrNull { it.label == label }?.key
                  },
                )
              }
              item {
                val selectedDay = selectedTemplateDayKey?.let(DayOfWeek::valueOf)
                ExpressiveAccentLabel(
                  text = selectedDay?.longLabel()?.let { "$it ricorrente" } ?: "Orario ricorrente",
                )
              }
              if (templateSlots.isEmpty()) {
                item {
                  EmptyState(
                    title = "Nessuno slot stabile",
                    detail = "Per questo giorno servono piu settimane coerenti prima di proporre un template affidabile.",
                  )
                }
              } else {
                items(templateSlots, key = { "${it.dayOfWeek}-${it.time}-${it.subject}" }) { slot ->
                  RegisterListRow(
                    title = slot.subject,
                    subtitle = slot.teacher ?: "Docente non specificato",
                    eyebrow = slot.timeRangeLabel(),
                    meta = listOfNotNull(
                      slot.room,
                      "Ricorrenza ${(slot.confidence * 100).toInt()}%",
                      "${slot.sampleCount} settimane",
                    ).joinToString(" / "),
                    tone = when {
                      slot.confidence >= 0.8f -> ExpressiveTone.Success
                      slot.confidence >= 0.6f -> ExpressiveTone.Info
                      else -> ExpressiveTone.Warning
                    },
                    badge = {
                      StatusBadge(
                        label = if (slot.confidence >= 0.75f) "STABILE" else "DINAMICO",
                        tone = if (slot.confidence >= 0.75f) ExpressiveTone.Success else ExpressiveTone.Warning,
                      )
                    },
                  )
                }
              }
            }
          }

          TAB_HISTORY -> {
            item {
              WeekNavigator(
                weekStart = currentWeekStart,
                weekOffset = weekOffset,
                onPrevious = { weekOffset -= 1 },
                onNext = { if (weekOffset < 0) weekOffset += 1 },
                onToday = { weekOffset = 0 },
              )
            }
            if (historyDayOptions.isEmpty()) {
              item {
                EmptyState(
                  title = "Storico vuoto",
                  detail = "Le lezioni svolte compariranno qui appena il registro restituisce date e argomenti firmati.",
                )
              }
            } else {
              item {
                ExpressivePillTabs(
                  options = historyDayOptions.map { it.label },
                  selected = historyDayOptions.firstOrNull { it.key == selectedHistoryDayKey }?.label ?: historyDayOptions.first().label,
                  onSelect = { label ->
                    selectedHistoryDayKey = historyDayOptions.firstOrNull { it.label == label }?.key
                  },
                )
              }
              item {
                val label = selectedHistoryDayKey?.toLocalDateOrNull()
                  ?.format(DateTimeFormatter.ofPattern("EEEE d MMMM", italianLocale))
                  ?.replaceFirstChar { it.uppercase() }
                  ?: "Lezioni svolte"
                ExpressiveAccentLabel(label)
              }
              if (lessonsForSelectedDay.isEmpty()) {
                item {
                  EmptyState(
                    title = "Nessuna lezione registrata",
                    detail = "Per questa giornata non risultano argomenti o firme nel registro sincronizzato.",
                  )
                }
              } else {
                items(lessonsForSelectedDay, key = { it.id }) { lesson ->
                  RegisterListRow(
                    title = lesson.subject,
                    subtitle = lesson.topic?.takeIf(String::isNotBlank) ?: "Argomento non inserito",
                    eyebrow = lesson.timeRangeLabel(),
                    meta = listOfNotNull(
                      lesson.teacher?.takeIf(String::isNotBlank),
                      lesson.room?.takeIf(String::isNotBlank),
                    ).joinToString(" / ").ifBlank { null },
                    tone = if (lesson.topic.isNullOrBlank()) ExpressiveTone.Neutral else ExpressiveTone.Success,
                    badge = {
                      StatusBadge(
                        label = "SVOLTA",
                        tone = ExpressiveTone.Success,
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WeekNavigator(
  weekStart: LocalDate,
  weekOffset: Int,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToday: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onPrevious) {
        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Settimana precedente")
      }
      Text(
        text = "Settimana del ${weekStart.format(weekHeaderFormatter)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      IconButton(onClick = onNext, enabled = weekOffset < 0) {
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Settimana successiva")
      }
    }
    if (weekOffset != 0) {
      Button(onClick = onToday, modifier = Modifier.align(Alignment.End)) {
        Text("Oggi")
      }
    }
  }
}

private fun DayOfWeek.shortLabel(): String {
  return when (this) {
    DayOfWeek.MONDAY -> "Lun"
    DayOfWeek.TUESDAY -> "Mar"
    DayOfWeek.WEDNESDAY -> "Mer"
    DayOfWeek.THURSDAY -> "Gio"
    DayOfWeek.FRIDAY -> "Ven"
    DayOfWeek.SATURDAY -> "Sab"
    DayOfWeek.SUNDAY -> "Dom"
  }
}

private fun DayOfWeek.longLabel(): String {
  return when (this) {
    DayOfWeek.MONDAY -> "Lunedi"
    DayOfWeek.TUESDAY -> "Martedi"
    DayOfWeek.WEDNESDAY -> "Mercoledi"
    DayOfWeek.THURSDAY -> "Giovedi"
    DayOfWeek.FRIDAY -> "Venerdi"
    DayOfWeek.SATURDAY -> "Sabato"
    DayOfWeek.SUNDAY -> "Domenica"
  }
}

private fun Lesson.timeRangeLabel(): String {
  val start = runCatching { LocalTime.parse(time) }.getOrNull() ?: return time
  val end = endTime
    ?.takeIf(String::isNotBlank)
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    ?: start.plusMinutes(durationMinutes.toLong())
  return "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private fun TemplateSlot.timeRangeLabel(): String {
  val start = runCatching { LocalTime.parse(time) }.getOrNull() ?: return time
  val end = endTime
    ?.takeIf(String::isNotBlank)
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    ?: start.plusMinutes(durationMinutes.toLong())
  return "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}
