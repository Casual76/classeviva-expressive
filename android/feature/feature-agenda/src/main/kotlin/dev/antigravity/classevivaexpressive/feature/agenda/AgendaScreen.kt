package dev.antigravity.classevivaexpressive.feature.agenda

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveMiniChart
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveStatusDot
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.eventColor
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CATEGORY_ALL = "All"
private const val CATEGORY_HOMEWORK = "Homework"
private const val CATEGORY_TESTS = "Tests"
private const val CATEGORY_EVENTS = "Events"

data class AgendaUiState(
  val items: List<AgendaItem> = emptyList(),
  val customEvents: List<CustomEvent> = emptyList(),
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
  private val agendaRepository: AgendaRepository,
) : ViewModel() {
  val state = combine(
    agendaRepository.observeAgenda(),
    agendaRepository.observeCustomEvents(),
  ) { items, customEvents ->
    AgendaUiState(items = items, customEvents = customEvents)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

  init {
    viewModelScope.launch { agendaRepository.refreshAgenda() }
  }

  fun refresh() {
    viewModelScope.launch { agendaRepository.refreshAgenda(force = true) }
  }

  fun addCustomEvent(
    title: String,
    description: String,
    subject: String,
    date: String,
    time: String?,
  ) {
    viewModelScope.launch {
      agendaRepository.addCustomEvent(
        CustomEvent(
          id = "custom-${UUID.randomUUID()}",
          title = title.trim(),
          description = description.trim(),
          subject = subject.trim(),
          date = date.trim(),
          time = time?.trim().takeUnless { it.isNullOrBlank() },
          category = AgendaCategory.CUSTOM,
        ),
      )
    }
  }
}

@Composable
fun AgendaRoute(
  modifier: Modifier = Modifier,
  viewModel: AgendaViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showDialog by rememberSaveable { mutableStateOf(false) }
  var selectedCategory by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
  var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
  var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

  val selectedMonth = remember(selectedMonthText) { YearMonth.parse(selectedMonthText) }
  val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
  val entries = remember(state.items, state.customEvents) {
    buildList {
      addAll(state.items.map {
        AgendaEntry(
          id = it.id,
          title = it.title,
          subtitle = it.subtitle,
          detail = it.detail,
          subject = it.subject,
          date = it.date.toLocalDateOrNull(),
          time = it.time,
          category = it.category,
          sharePayload = it.sharePayload,
        )
      })
      addAll(state.customEvents.map {
        AgendaEntry(
          id = it.id,
          title = it.title,
          subtitle = it.subject,
          detail = it.description,
          subject = it.subject,
          date = it.date.toLocalDateOrNull(),
          time = it.time,
          category = AgendaCategory.CUSTOM,
          sharePayload = listOfNotNull(it.title, it.subject, it.description).joinToString("\n"),
        )
      })
    }.filter { it.date != null }.sortedWith(compareBy<AgendaEntry> { it.date }.thenBy { it.time ?: "" })
  }
  val filteredEntries = remember(entries, selectedCategory, selectedDate) {
    entries.filter { entry ->
      val categoryMatches = when (selectedCategory) {
        CATEGORY_HOMEWORK -> entry.category == AgendaCategory.HOMEWORK
        CATEGORY_TESTS -> entry.category == AgendaCategory.ASSESSMENT
        CATEGORY_EVENTS -> entry.category == AgendaCategory.EVENT || entry.category == AgendaCategory.CUSTOM
        else -> true
      }
      categoryMatches && entry.date == selectedDate
    }
  }
  val monthEntries = remember(entries, selectedMonth) { entries.filter { YearMonth.from(it.date) == selectedMonth } }
  val monthChart = remember(monthEntries, selectedMonth) {
    (1..selectedMonth.lengthOfMonth()).map { day ->
      monthEntries.count { it.date?.dayOfMonth == day }.toFloat()
    }.ifEmpty { listOf(0f) }
  }

  Box(modifier = modifier) {
    LazyColumn(
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        ExpressiveTopHeader(
          title = "Agenda",
          actions = {
            IconButton(onClick = viewModel::refresh) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
          },
        )
      }
      item {
        MonthHeader(
          month = selectedMonth,
          onPrevious = {
            val previous = selectedMonth.minusMonths(1)
            selectedMonthText = previous.toString()
            selectedDateText = previous.atDay(1).toString()
          },
          onNext = {
            val next = selectedMonth.plusMonths(1)
            selectedMonthText = next.toString()
            selectedDateText = next.atDay(1).toString()
          },
        )
      }
      item {
        ExpressivePillTabs(
          options = listOf(CATEGORY_ALL, CATEGORY_HOMEWORK, CATEGORY_TESTS, CATEGORY_EVENTS),
          selected = selectedCategory,
          onSelect = { selectedCategory = it },
        )
      }
      item {
        ExpressiveMiniChart(
          points = monthChart,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.height(96.dp),
        )
      }
      item {
        MonthGrid(
          month = selectedMonth,
          entries = monthEntries,
          selectedDate = selectedDate,
          onSelectDate = { selectedDateText = it.toString() },
        )
      }
      if (filteredEntries.isEmpty()) {
        item {
          EmptyState(
            title = "No events for ${selectedDate.dayOfMonth}",
            detail = "Homework, tests and custom events will appear here for the selected day.",
          )
        }
      } else {
        items(filteredEntries, key = { it.id }) { entry ->
          AgendaEventCard(
            entry = entry,
            onClick = {
              entry.sharePayload?.takeIf { it.isNotBlank() }?.let { payload ->
                val intent = Intent(Intent.ACTION_SEND)
                  .setType("text/plain")
                  .putExtra(Intent.EXTRA_TEXT, payload)
                startActivity(context, Intent.createChooser(intent, "Share event"), null)
              }
            },
          )
        }
      }
    }

    FloatingActionButton(
      onClick = { showDialog = true },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(24.dp),
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
      Icon(Icons.Rounded.Add, contentDescription = "Add event")
    }
  }

  if (showDialog) {
    AddEventDialog(
      onDismiss = { showDialog = false },
      onSave = { title, description, subject, date, time ->
        viewModel.addCustomEvent(title, description, subject, date, time)
        showDialog = false
      },
    )
  }
}

@Composable
private fun MonthHeader(
  month: YearMonth,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onPrevious) {
      Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
    }
    Text(
      text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      fontWeight = FontWeight.Medium,
    )
    IconButton(onClick = onNext) {
      Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
    }
  }
}

@Composable
private fun MonthGrid(
  month: YearMonth,
  entries: List<AgendaEntry>,
  selectedDate: LocalDate,
  onSelectDate: (LocalDate) -> Unit,
) {
  val cells = remember(month) { buildCalendarCells(month) }
  val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      weekdayLabels.forEach { label ->
        Text(
          text = label,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
          color = if (label == "Sun") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    cells.chunked(7).forEach { week ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        week.forEach { day ->
          val dayEntries = entries.filter { it.date == day }
          CalendarDayCell(
            date = day,
            inMonth = day.month == month.month,
            selected = day == selectedDate,
            dots = dayEntries.take(4).map { eventColor(it.category.name) },
            onClick = { onSelectDate(day) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun CalendarDayCell(
  date: LocalDate,
  inMonth: Boolean,
  selected: Boolean,
  dots: List<Color>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val weekend = date.dayOfWeek == DayOfWeek.SUNDAY
  val textColor = when {
    selected -> MaterialTheme.colorScheme.onPrimary
    weekend -> MaterialTheme.colorScheme.primary
    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    else -> MaterialTheme.colorScheme.onBackground
  }

  Column(
    modifier = modifier
      .height(60.dp)
      .clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .background(
          color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
          shape = MaterialTheme.shapes.extraLarge,
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = date.dayOfMonth.toString(),
        style = MaterialTheme.typography.titleMedium,
        color = textColor,
        fontWeight = FontWeight.Medium,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      dots.forEach { dot ->
        ExpressiveStatusDot(color = dot, modifier = Modifier.size(8.dp))
      }
    }
  }
}

@Composable
private fun AgendaEventCard(
  entry: AgendaEntry,
  onClick: () -> Unit,
) {
  val cardColor = eventColor(entry.category.name)
  val headline = entry.subject?.takeIf { it.isNotBlank() } ?: entry.title
  val subheadline = entry.title.takeIf { it.isNotBlank() && it != headline }
  val supporting = listOfNotNull(
    entry.subtitle.takeIf { it.isNotBlank() && it != headline && it != subheadline },
    entry.detail.takeIf { it?.isNotBlank() == true && it != headline && it != subheadline },
  ).joinToString(" - ").ifBlank { null }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = MaterialTheme.shapes.extraLarge,
    color = cardColor,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Column(
        modifier = Modifier
          .width(56.dp)
          .height(60.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = entry.time?.let { "hour\n$it" } ?: "All\nday",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White.copy(alpha = 0.94f),
          fontWeight = FontWeight.Medium,
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = headline,
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        subheadline?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.96f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        supporting?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun AddEventDialog(
  onDismiss: () -> Unit,
  onSave: (title: String, description: String, subject: String, date: String, time: String?) -> Unit,
) {
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var subject by rememberSaveable { mutableStateOf("") }
  var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
  var time by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuovo evento") },
    text = {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titolo") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Materia o tag") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Data ISO") },
            supportingText = { Text("Esempio: 2026-03-27") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            label = { Text("Ora") },
            supportingText = { Text("Opzionale, formato 14:30") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Dettagli") },
            minLines = 3,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(title, description, subject, date, time) },
        enabled = title.isNotBlank() && date.isNotBlank(),
      ) {
        Text("Salva")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Annulla")
      }
    },
  )
}

private data class AgendaEntry(
  val id: String,
  val title: String,
  val subtitle: String,
  val detail: String?,
  val subject: String?,
  val date: LocalDate?,
  val time: String?,
  val category: AgendaCategory,
  val sharePayload: String?,
)

private fun buildCalendarCells(month: YearMonth): List<LocalDate> {
  val firstDay = month.atDay(1)
  val leading = (firstDay.dayOfWeek.value + 6) % 7
  val start = firstDay.minusDays(leading.toLong())
  return (0 until 42).map { start.plusDays(it.toLong()) }
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}
