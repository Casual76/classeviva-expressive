package dev.antigravity.classevivaexpressive.feature.agenda

import android.content.Intent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.designsystem.theme.bouncyClickable
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")
private val calendarHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", italianLocale)
private val eventDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", italianLocale)
private val createdAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", italianLocale)

data class AgendaUiState(
  val items: List<AgendaItem> = emptyList(),
  val customEvents: List<CustomEvent> = emptyList(),
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
  private val agendaRepository: AgendaRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)

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
          createdAt = LocalDateTime.now().toString().take(16),
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AgendaRoute(
  modifier: Modifier = Modifier,
  viewModel: AgendaViewModel = hiltViewModel(),
  sharedTransitionScope: SharedTransitionScope? = null,
  animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showDialog by rememberSaveable { mutableStateOf(false) }
  var selectedEntry by remember { mutableStateOf<AgendaEntry?>(null) }
  var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
  var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
          teacher = it.teacher,
          date = it.date.toLocalDateOrNull(),
          time = it.time,
          category = it.category,
          sharePayload = it.sharePayload,
          createdAt = it.createdAt,
        )
      })
      addAll(state.customEvents.map {
        AgendaEntry(
          id = it.id,
          title = it.title,
          subtitle = it.subject,
          detail = it.description,
          subject = it.subject,
          teacher = null,
          date = it.date.toLocalDateOrNull(),
          time = it.time,
          category = AgendaCategory.CUSTOM,
          sharePayload = listOfNotNull(it.title, it.subject, it.description).joinToString("\n"),
          createdAt = it.createdAt,
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
      .filter { it.category != AgendaCategory.LESSON }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Agenda",
        subtitle = "Pianifica verifiche, eventi e compiti con una vista calendario leggibile.",
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showDialog = true },
        modifier = Modifier.padding(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Nuovo evento")
      }
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.padding(paddingValues).fillMaxSize(),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().animateContentSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          MonthHeader(
            modifier = Modifier.animateItem(),
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
            modifier = Modifier.animateItem(),
            month = selectedMonth,
            entriesByDate = monthEntriesByDate,
            selectedDate = selectedDate,
            onSelectDate = { selectedDateText = it.toString() },
          )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { ExpressiveAccentLabel(formatDayHeader(selectedDate)) }

        if (selectedDayEntries.isEmpty()) {
          item {
            EmptyState(
              modifier = Modifier.animateItem(),
              title = "Nulla di pianificato",
              detail = "Non ci sono compiti, verifiche o eventi per questa data.",
            )
          }
        } else {
          items(selectedDayEntries, key = { it.id }) { entry ->
            val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
              with(sharedTransitionScope) {
                Modifier.sharedElement(
                  rememberSharedContentState(key = "agenda-${entry.id}"),
                  animatedVisibilityScope = animatedVisibilityScope,
                )
              }
            } else {
              Modifier
            }

            AgendaEntryRow(
              entry = entry,
              onClick = { selectedEntry = entry },
              onLongClick = { shareEntry(context, entry) },
              modifier = Modifier.animateItem().then(sharedModifier),
            )
          }
        }
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

  selectedEntry?.let { entry ->
    AgendaDetailSheet(
      entry = entry,
      onDismiss = { selectedEntry = null },
      onShare = { shareEntry(context, entry) },
      sharedTransitionScope = sharedTransitionScope,
      animatedVisibilityScope = animatedVisibilityScope,
    )
  }
}

@Composable
private fun MonthHeader(
  month: YearMonth,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
      Icon(Icons.Rounded.ChevronLeft, contentDescription = "Mese precedente")
    }
    Text(
      text = month.format(calendarHeaderFormatter).replaceFirstChar { it.uppercase() },
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
      fontWeight = FontWeight.SemiBold,
    )
    IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
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
  modifier: Modifier = Modifier,
) {
  val cells = remember(month) { buildCalendarCells(month) }
  val today = remember { LocalDate.now() }
  val weekdayLabels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
      weekdayLabels.forEach { label ->
        Text(
          text = label,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelSmall,
          textAlign = TextAlign.Center,
          color = if (label == "Dom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    cells.chunked(7).forEach { week ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
      .height(60.dp)
      .bouncyClickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Box(
      modifier = Modifier
        .size(32.dp)
        .background(
          color = containerColor,
          shape = MaterialTheme.shapes.medium,
        )
        .padding(2.dp)
        .run { this },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = date.dayOfMonth.toString(),
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
      )
    }
    Row(
      modifier = Modifier.height(6.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val categories = entries.map { it.category }.distinct()
      if (categories.contains(AgendaCategory.EVENT) || categories.contains(AgendaCategory.CUSTOM)) {
        CalendarDot(color = Color(0xFF2E8B57))
      }
      if (categories.contains(AgendaCategory.HOMEWORK)) {
        CalendarDot(color = Color(0xFFFF9800))
      }
      if (categories.contains(AgendaCategory.ASSESSMENT)) {
        CalendarDot(color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

@Composable
private fun CalendarDot(color: Color) {
  Box(
    modifier = Modifier
      .size(5.dp)
      .background(color = color, shape = androidx.compose.foundation.shape.CircleShape),
  )
}

@Composable
private fun AgendaEntryRow(
  entry: AgendaEntry,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  RegisterListRow(
    title = entry.title,
    subtitle = entry.subject ?: entry.subtitle,
    eyebrow = entry.time ?: categoryLabel(entry.category),
    meta = buildList {
      entry.detail?.takeIf(String::isNotBlank)?.let(::add)
      entry.teacher?.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" / ").ifBlank { null },
    tone = categoryTone(entry.category),
    badge = {
      StatusBadge(
        label = categoryLabel(entry.category),
        tone = categoryTone(entry.category),
      )
    },
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AgendaDetailSheet(
  entry: AgendaEntry,
  onDismiss: () -> Unit,
  onShare: () -> Unit,
  sharedTransitionScope: SharedTransitionScope?,
  animatedVisibilityScope: AnimatedVisibilityScope?,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().animateContentSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        RegisterListRow(
          title = entry.title,
          subtitle = entry.subject ?: entry.subtitle.ifBlank { "Agenda" },
          eyebrow = categoryLabel(entry.category),
          meta = entry.eventDateLabel(),
          tone = categoryTone(entry.category),
          badge = { StatusBadge(categoryLabel(entry.category), tone = categoryTone(entry.category)) },
        )
      }
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          InfoLine(label = "Data evento", value = entry.eventDateLabel())
          entry.createdAtLabel()?.let { addedAt ->
            InfoLine(label = "Aggiunto", value = addedAt)
          }
          entry.subject?.takeIf(String::isNotBlank)?.let { subject ->
            InfoLine(label = "Materia", value = subject)
          }
          entry.teacher?.takeIf(String::isNotBlank)?.let { teacher ->
            InfoLine(label = "Docente", value = teacher)
          }
        }
      }
      item { HorizontalDivider() }
      item {
        ExpressiveCard(highlighted = true) {
          Text(
            text = "Dettagli",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = entry.detail?.takeIf(String::isNotBlank) ?: "Nessuna descrizione completa disponibile.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FilledTonalButton(
            onClick = onShare,
            modifier = Modifier.weight(1f),
          ) {
            Text("Condividi")
          }
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
          ) {
            Text("Chiudi")
          }
        }
      }
      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun InfoLine(
  label: String,
  value: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
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

  ModalBottomSheet(onDismissRequest = onDismiss) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().animateContentSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "Nuovo evento",
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.animateItem(),
        )
      }
      item {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          modifier = Modifier.fillMaxWidth().animateItem(),
          label = { Text("Titolo") },
          singleLine = true,
        )
      }
      item {
        OutlinedTextField(
          value = subject,
          onValueChange = { subject = it },
          modifier = Modifier.fillMaxWidth().animateItem(),
          label = { Text("Materia o tag") },
          singleLine = true,
        )
      }
      item {
        RegisterListRow(
          title = "Data",
          subtitle = date.toLocalDateOrNull()
            ?.format(eventDateFormatter)
            ?.replaceFirstChar { it.uppercase() }
            ?: date,
          eyebrow = "DatePicker",
          tone = ExpressiveTone.Primary,
          onClick = { showDatePicker = true },
          badge = { StatusBadge("SELEZIONA", tone = ExpressiveTone.Primary) },
          modifier = Modifier.animateItem(),
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
          modifier = Modifier.animateItem(),
        )
      }
      item {
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          modifier = Modifier.fillMaxWidth().animateItem(),
          label = { Text("Dettagli") },
          minLines = 3,
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth().animateItem(),
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
  val teacher: String?,
  val date: LocalDate?,
  val time: String?,
  val category: AgendaCategory,
  val sharePayload: String?,
  val createdAt: String?,
)

private fun buildCalendarCells(month: YearMonth): List<LocalDate> {
  val firstDay = month.atDay(1)
  val leading = (firstDay.dayOfWeek.value + 6) % 7
  val start = firstDay.minusDays(leading.toLong())
  return (0 until 42).map { start.plusDays(it.toLong()) }
}

private fun categoryTone(category: AgendaCategory): ExpressiveTone {
  return when (category) {
    AgendaCategory.HOMEWORK -> ExpressiveTone.Warning
    AgendaCategory.ASSESSMENT -> ExpressiveTone.Danger
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

private fun formatDayHeader(date: LocalDate): String {
  return date.format(eventDateFormatter).replaceFirstChar { it.uppercase() }
}

private fun AgendaEntry.eventDateLabel(): String {
  val resolvedDate = date ?: return listOfNotNull(time).joinToString(" • ")
  return buildList {
    add(resolvedDate.format(eventDateFormatter).replaceFirstChar { it.uppercase() })
    time?.takeIf(String::isNotBlank)?.let(::add)
  }.joinToString(" • ")
}

private fun AgendaEntry.createdAtLabel(): String? {
  val value = createdAt?.trim()?.takeIf { it.isNotBlank() } ?: return null
  return runCatching {
    when {
      value.contains("+") || value.endsWith("Z") -> {
        OffsetDateTime.parse(value).toLocalDateTime().format(createdAtFormatter)
      }
      value.contains("T") -> {
        LocalDateTime.parse(value).format(createdAtFormatter)
      }
      else -> LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale))
    }
  }.getOrElse { value }
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
