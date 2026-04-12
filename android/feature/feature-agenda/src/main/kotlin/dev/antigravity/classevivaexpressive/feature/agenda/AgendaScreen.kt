package dev.antigravity.classevivaexpressive.feature.agenda

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class AgendaUiState(
  val items: List<AgendaItem> = emptyList(),
  val customEvents: List<CustomEvent> = emptyList(),
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
  private val agendaRepository: AgendaRepository,
) : ViewModel() {
  private val isRefreshing = kotlinx.coroutines.flow.MutableStateFlow(false)

  val state = combine(
    agendaRepository.observeAgenda(),
    agendaRepository.observeCustomEvents(),
    isRefreshing,
  ) { items, customEvents, refreshing ->
    AgendaUiState(items = items, customEvents = customEvents, isRefreshing = refreshing)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
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

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      agendaRepository.refreshAgenda(force = force)
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaRoute(
  modifier: Modifier = Modifier,
  viewModel: AgendaViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showDialog by rememberSaveable { mutableStateOf(false) }
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

  val entriesByDate = remember(entries) {
    entries.mapNotNull { entry -> entry.date?.let { it to entry } }
      .groupBy({ it.first }, { it.second })
  }
  val monthEntriesByDate = remember(entriesByDate, selectedMonth) {
    entriesByDate.filterKeys { YearMonth.from(it) == selectedMonth }
  }
  val selectedDayEntries = remember(entriesByDate, selectedDate) {
    entriesByDate[selectedDate].orEmpty()
  }

  PullToRefreshBox(
    modifier = modifier.fillMaxSize(),
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        item {
          ExpressiveTopHeader(
            title = "Agenda",
            subtitle = "Calendario compatto e lista del giorno selezionato, senza riepiloghi mensili ridondanti.",
            actions = {
              IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
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
              selectedDateText = previous.atDay(selectedDate.dayOfMonth.coerceAtMost(previous.lengthOfMonth())).toString()
            },
            onNext = {
              val next = selectedMonth.plusMonths(1)
              selectedMonthText = next.toString()
              selectedDateText = next.atDay(selectedDate.dayOfMonth.coerceAtMost(next.lengthOfMonth())).toString()
            },
          )
        }
        item {
          MonthGrid(
            month = selectedMonth,
            entriesByDate = monthEntriesByDate,
            selectedDate = selectedDate,
            onSelectDate = { selectedDateText = it.toString() },
          )
        }
        item { ExpressiveAccentLabel(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", italianLocale)).replaceFirstChar { it.uppercase() }) }
        if (selectedDayEntries.isEmpty()) {
          item {
            EmptyState(
              title = "Nessun evento per il giorno selezionato",
              detail = "Compiti, verifiche, lezioni ed eventi compariranno qui solo per la data che stai guardando.",
            )
          }
        } else {
          items(selectedDayEntries, key = { it.id }) { entry ->
            AgendaEntryRow(
              entry = entry,
              onClick = { shareEntry(context, entry) },
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
        Icon(Icons.Rounded.Add, contentDescription = "Nuovo evento")
      }
    }
  }

  if (showDialog) {
    AddEventSheet(
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
      Icon(Icons.Rounded.ChevronLeft, contentDescription = "Mese precedente")
    }
    Text(
      text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", italianLocale)).replaceFirstChar { it.uppercase() },
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      fontWeight = FontWeight.SemiBold,
    )
    IconButton(onClick = onNext) {
      Icon(Icons.Rounded.ChevronRight, contentDescription = "Mese successivo")
    }
  }
}

@Composable
private fun MonthGrid(
  month: YearMonth,
  entriesByDate: Map<LocalDate, List<AgendaEntry>>,
  selectedDate: LocalDate,
  onSelectDate: (LocalDate) -> Unit,
) {
  val cells = remember(month) { buildCalendarCells(month) }
  val today = remember { LocalDate.now() }
  val weekdayLabels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      weekdayLabels.forEach { label ->
        Text(
          text = label,
          modifier = Modifier.weight(1f),
          color = if (label == "Dom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    cells.chunked(7).forEach { week ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        week.forEach { day ->
          CalendarDayCell(
            date = day,
            inMonth = day.month == month.month,
            isToday = day == today,
            selected = day == selectedDate,
            entries = entriesByDate[day].orEmpty(),
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
  isToday: Boolean,
  selected: Boolean,
  entries: List<AgendaEntry>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isSunday = date.dayOfWeek == DayOfWeek.SUNDAY
  val containerColor = when {
    selected -> MaterialTheme.colorScheme.primary
    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else -> Color.Transparent
  }
  val textColor = when {
    selected -> MaterialTheme.colorScheme.onPrimary
    isSunday -> MaterialTheme.colorScheme.primary
    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    else -> MaterialTheme.colorScheme.onBackground
  }

  Column(
    modifier = modifier
      .height(72.dp)
      .clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(
          color = containerColor,
          shape = MaterialTheme.shapes.extraLarge,
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = date.dayOfMonth.toString(),
        color = textColor,
        fontWeight = FontWeight.SemiBold,
      )
    }
    Row(
      modifier = Modifier.height(8.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      entries.map { it.category }.distinct().take(3).forEach { category ->
        val dotColor = when (categoryTone(category)) {
          ExpressiveTone.Primary -> MaterialTheme.colorScheme.primary
          ExpressiveTone.Success -> Color(0xFF2E8B57)
          ExpressiveTone.Warning -> Color(0xFFFF9800)
          ExpressiveTone.Danger -> MaterialTheme.colorScheme.error
          ExpressiveTone.Info -> MaterialTheme.colorScheme.tertiary
          ExpressiveTone.Neutral -> MaterialTheme.colorScheme.outline
        }
        Box(
          modifier = Modifier
            .size(6.dp)
            .background(color = dotColor, shape = androidx.compose.foundation.shape.CircleShape)
        )
      }
    }
  }
}

@Composable
private fun AgendaEntryRow(
  entry: AgendaEntry,
  onClick: () -> Unit,
) {
  RegisterListRow(
    title = entry.title,
    subtitle = entry.subject ?: entry.subtitle,
    eyebrow = entry.time ?: entry.date?.format(DateTimeFormatter.ofPattern("d MMM", italianLocale)) ?: "Data",
    meta = entry.detail,
    tone = categoryTone(entry.category),
    badge = {
      StatusBadge(
        label = categoryLabel(entry.category),
        tone = categoryTone(entry.category),
      )
    },
    onClick = onClick,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventSheet(
  onDismiss: () -> Unit,
  onSave: (title: String, description: String, subject: String, date: String, time: String?) -> Unit,
) {
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var subject by rememberSaveable { mutableStateOf("") }
  var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
  var time by rememberSaveable { mutableStateOf("") }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showTimePicker by rememberSaveable { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "Nuovo evento",
          style = MaterialTheme.typography.headlineSmall,
        )
      }
      item {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Titolo") },
          singleLine = true,
        )
      }
      item {
        OutlinedTextField(
          value = subject,
          onValueChange = { subject = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Materia o tag") },
          singleLine = true,
        )
      }
      item {
        RegisterListRow(
          title = "Data",
          subtitle = date.toLocalDateOrNull()
            ?.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", italianLocale))
            ?.replaceFirstChar { it.uppercase() }
            ?: date,
          eyebrow = "DatePicker",
          tone = ExpressiveTone.Primary,
          onClick = { showDatePicker = true },
          badge = { StatusBadge("SELEZIONA", tone = ExpressiveTone.Primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Ora",
          subtitle = if (time.isBlank()) "Opzionale" else time,
          eyebrow = "TimePicker",
          tone = ExpressiveTone.Info,
          onClick = { showTimePicker = true },
          badge = { StatusBadge(if (time.isBlank()) "OPZIONALE" else "IMPOSTATA", tone = ExpressiveTone.Info) },
        )
      }
      item {
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Dettagli") },
          minLines = 3,
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TextButton(onClick = onDismiss) {
            Text("Annulla")
          }
          Button(
            onClick = { onSave(title, description, subject, date, time) },
            enabled = title.isNotBlank() && date.isNotBlank(),
          ) {
            Text("Salva")
          }
        }
      }
    }
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = date.toLocalDateOrNull()?.toEpochMillis(),
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              date = millisToLocalDate(millis).toString()
            }
            showDatePicker = false
          },
        ) {
          Text("Seleziona")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text("Annulla")
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  if (showTimePicker) {
    val initialTime = time.toLocalTimeOrNull() ?: LocalTime.of(14, 30)
    val timePickerState = rememberTimePickerState(
      initialHour = initialTime.hour,
      initialMinute = initialTime.minute,
      is24Hour = true,
    )
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      title = { Text("Seleziona orario") },
      text = { TimePicker(state = timePickerState) },
      confirmButton = {
        TextButton(
          onClick = {
            time = String.format(italianLocale, "%02d:%02d", timePickerState.hour, timePickerState.minute)
            showTimePicker = false
          },
        ) {
          Text("Conferma")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (time.isNotBlank()) {
            TextButton(
              onClick = {
                time = ""
                showTimePicker = false
              },
            ) {
              Text("Rimuovi")
            }
          }
          TextButton(onClick = { showTimePicker = false }) {
            Text("Annulla")
          }
        }
      },
    )
  }
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

private fun categoryTone(category: AgendaCategory): ExpressiveTone {
  return when (category) {
    AgendaCategory.HOMEWORK -> ExpressiveTone.Primary
    AgendaCategory.ASSESSMENT -> ExpressiveTone.Warning
    AgendaCategory.LESSON -> ExpressiveTone.Neutral
    AgendaCategory.EVENT,
    AgendaCategory.CUSTOM,
    -> ExpressiveTone.Success
  }
}

private fun categoryLabel(category: AgendaCategory): String {
  return when (category) {
    AgendaCategory.LESSON -> "Lezione"
    AgendaCategory.HOMEWORK -> "Compito"
    AgendaCategory.ASSESSMENT -> "Verifica"
    AgendaCategory.EVENT -> "Evento"
    AgendaCategory.CUSTOM -> "Personalizzato"
  }
}

private fun shareEntry(context: android.content.Context, entry: AgendaEntry) {
  entry.sharePayload?.takeIf { it.isNotBlank() }?.let { payload ->
    val intent = Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, payload)
    startActivity(context, Intent.createChooser(intent, "Condividi evento"), null)
  }
}

private fun LocalDate.toEpochMillis(): Long {
  return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun millisToLocalDate(value: Long): LocalDate {
  return java.time.Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}

private fun String.toLocalTimeOrNull(): LocalTime? {
  return runCatching { LocalTime.parse(this) }.getOrNull()
}
