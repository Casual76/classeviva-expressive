package dev.antigravity.classevivaexpressive.feature.agenda

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHero
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.fluidGlassGroups
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import java.time.DayOfWeek
import java.time.Instant
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
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContainerScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidSyncAction
import dev.antigravity.fluidengine.ui.theme.FluidSyncNotice
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.lastSyncLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.noticeMessage
import dev.antigravity.classevivaexpressive.core.designsystem.theme.toFluid

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")
private val calendarHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", italianLocale)
private val monthOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", italianLocale)
private val eventDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", italianLocale)
private val createdAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", italianLocale)

/**
 * Facts the docked bar cycles through on the agenda. The month is first because it is the one thing
 * a scrolled agenda stops telling you, and the one you most need to know while you are in it.
 */
/** How far ahead the bar is willing to call something "in arrivo". */
private const val AgendaLookaheadDays = 7L

/**
 * What the docked bar says about this screen once the title has left it.
 *
 * The week ahead, split into the two things that are asked about separately. The whole month's
 * count and the current month's name both used to take a turn here; the month is already the
 * heading of the calendar directly below, and a total across a month answers no question anyone has
 * while looking at a bar.
 */
internal fun buildAgendaFacets(
  items: List<AgendaItem>,
  today: LocalDate,
): List<String> = buildList {
  val from = today.toString()
  val until = today.plusDays(AgendaLookaheadDays).toString()
  val window = items.filter { it.date in from..until }
  val assessments = window.count { it.category == AgendaCategory.ASSESSMENT }
  if (assessments > 0) {
    add(if (assessments == 1) "1 verifica" else "$assessments verifiche")
  }
  val tomorrow = today.plusDays(1).toString()
  val dueTomorrow = items.count { it.date == tomorrow && it.category == AgendaCategory.HOMEWORK }
  if (dueTomorrow > 0) {
    add(if (dueTomorrow == 1) "1 compito domani" else "$dueTomorrow compiti domani")
  }
  val rest = window.size - assessments
  if (rest > 0) add(if (rest == 1) "1 impegno" else "$rest impegni")
}

internal fun agendaMonthLabel(date: LocalDate): String =
  monthOnlyFormatter.format(date).replaceFirstChar { it.titlecase(italianLocale) }

data class AgendaUiState(
  val items: List<AgendaItem> = emptyList(),
  val customEvents: List<CustomEvent> = emptyList(),
  val isRefreshing: Boolean = false,
  val syncStatus: SyncStatus = SyncStatus(),
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
  private val agendaRepository: AgendaRepository,
  private val dashboardRepository: DashboardRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)

  val state = combine(
    agendaRepository.observeAgenda(),
    agendaRepository.observeCustomEvents(),
    isRefreshing,
    dashboardRepository.observeDashboard(),
  ) { items, customEvents, refreshing, dashboard ->
    AgendaUiState(
      items = items,
      customEvents = customEvents,
      isRefreshing = refreshing,
      syncStatus = dashboard.syncStatus,
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaRoute(
  initialAgendaId: String? = null,
  initialDate: String? = null,
  onOpenEntry: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
  viewModel: AgendaViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showDialog by rememberSaveable { mutableStateOf(false) }
  var selectedEntry by remember { mutableStateOf<AgendaEntry?>(null) }
  var detailOrigin by remember { mutableStateOf<Rect?>(null) }
  val initialDateValue = remember(initialDate) {
    initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
  }
  var selectedMonthText by rememberSaveable(initialDateValue) { mutableStateOf(YearMonth.from(initialDateValue).toString()) }
  var selectedDateText by rememberSaveable(initialDateValue) { mutableStateOf(initialDateValue.toString()) }

  val selectedMonth = remember(selectedMonthText) { YearMonth.parse(selectedMonthText) }
  val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
  val entries = remember(state.items, state.customEvents) { state.toAgendaEntries() }

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

  LaunchedEffect(initialAgendaId, initialDate, entries) {
    initialDate?.let { date ->
      runCatching { LocalDate.parse(date) }.getOrNull()?.let {
        selectedMonthText = YearMonth.from(it).toString()
        selectedDateText = it.toString()
      }
    }
    if (!initialAgendaId.isNullOrBlank()) {
      entries.firstOrNull { it.id == initialAgendaId }?.let { entry ->
        entry.date?.let {
          selectedMonthText = YearMonth.from(it).toString()
          selectedDateText = it.toString()
        }
        selectedEntry = entry
      }
    }
  }

  val facetToday = remember { LocalDate.now() }
  val titleFacets = remember(state.items, facetToday) {
    buildAgendaFacets(state.items, facetToday)
  }

  FluidScreen(
    modifier = modifier,
    title = "Agenda",
    ambient = FeatureIdentity.Agenda.ambient(),
    subtitle = state.syncStatus.lastSyncLabel(),
    titleFacets = titleFacets,
    actions = {
      FluidSyncAction(status = state.syncStatus.toFluid(), onRetry = viewModel::refresh)
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 12.dp,
  ) {
    // Whatever the sync could not deliver, said where the missing data would have been. Reserved
    // only when there is something to say, so an ordinary page keeps its first item at the top.
    if (state.syncStatus.noticeMessage() != null) {
      item {
        FluidSyncNotice(status = state.syncStatus.toFluid(), onRetry = viewModel::refresh)
      }
    }
    item {
      FeatureHero(
        identity = FeatureIdentity.Agenda,
        eyebrow = selectedMonth.format(calendarHeaderFormatter).replaceFirstChar { it.uppercase() },
        value = selectedDayEntries.size.toString(),
        label = if (selectedDayEntries.size == 1) "impegno nel giorno" else "impegni nel giorno",
        icon = Icons.Rounded.CalendarMonth,
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
    item { Spacer(modifier = Modifier.height(8.dp)) }
    item { FluidSectionHeader(formatDayHeader(selectedDate)) }

    if (selectedDayEntries.isEmpty()) {
      item {
        FluidEmptyState(
          title = "Nulla di pianificato",
          detail = "Non ci sono compiti, verifiche o eventi per questa data.",
        )
      }
    } else {
      fluidGlassGroups(selectedDayEntries) { entry ->
        var rowBounds by remember { mutableStateOf<Rect?>(null) }
        AgendaEntryRow(
          entry = entry,
          modifier = Modifier.fluidExpandOrigin { rowBounds = it },
          onClick = {
            detailOrigin = rowBounds
            if (onOpenEntry != null) onOpenEntry(entry.id) else selectedEntry = entry
          },
          // Era un onLongClick che condivideva e basta, senza dirlo: la stessa pressione ora
          // apre un menu che dice cosa sa fare.
          onLongClick = null,
          contextActions = {
            listOf(
              FluidContextAction(
                label = "Dettagli",
                icon = categoryIcon(entry.category),
                onClick = {
                  detailOrigin = rowBounds
                  if (onOpenEntry != null) onOpenEntry(entry.id) else selectedEntry = entry
                },
              ),
              FluidContextAction(
                label = "Condividi",
                icon = Icons.Rounded.Share,
                onClick = { shareEntry(context, entry) },
              ),
            )
          },
        )
      }
    }
  }

  // Portali, non sheet: dichiarati sempre e visibili a comando, cosi' l'uscita non si smonta
  // insieme alla condizione che li mostrava.
  FluidGlassModalPortal(
    visible = showDialog,
    onDismissRequest = { showDialog = false },
    paneTitle = "Nuovo evento",
  ) {
    AddEventContent(
      onDismiss = { showDialog = false },
      onSave = { title, description, subject, date, time ->
        viewModel.addCustomEvent(title, description, subject, date, time)
        showDialog = false
      },
    )
  }

  FluidGlassModalPortal(
    item = selectedEntry,
    onDismissRequest = { selectedEntry = null },
    origin = { detailOrigin },
    paneTitle = "Dettaglio agenda",
  ) { entry ->
    AgendaDetailContent(
      entry = entry,
      onShare = { shareEntry(context, entry) },
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
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val targetContainerColor = when {
    selected -> MaterialTheme.colorScheme.primary
    isPressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else -> Color.Transparent
  }
  val containerColor by animateColorAsState(
    targetValue = targetContainerColor,
    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    label = "calendarDayPressColor",
  )
  val textColor = when {
    selected -> MaterialTheme.colorScheme.onPrimary
    isSunday -> MaterialTheme.colorScheme.primary
    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    else -> MaterialTheme.colorScheme.onBackground
  }

  Column(
    modifier = modifier
      .height(60.dp)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      ),
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
      // Dalla palette, non da due esadecimali. Il verde 2E8B57 e l'arancio FF9800 erano gli unici
      // colori dell'app che non sapevano niente ne' del tema scuro, ne' di Material You, ne'
      // dell'accento scelto in impostazioni: tre puntini che restavano identici mentre tutto il
      // resto della pagina cambiava.
      if (categories.contains(AgendaCategory.EVENT) || categories.contains(AgendaCategory.CUSTOM)) {
        CalendarDot(color = MaterialTheme.colorScheme.tertiary)
      }
      if (categories.contains(AgendaCategory.HOMEWORK)) {
        CalendarDot(color = MaterialTheme.colorScheme.secondary)
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
  onClick: (() -> Unit)?,
  onLongClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  contextActions: (() -> List<FluidContextAction>)? = null,
) {
  FluidListRow(
    title = entry.title,
    subtitle = entry.subject ?: entry.subtitle,
    eyebrow = entry.time ?: categoryLabel(entry.category),
    meta = buildList {
      entry.createdAtLabel()?.let { add("Aggiunto: $it") }
      entry.modifiedAtLabel()?.let { add("Modificato: $it") }
      entry.detail?.takeIf(String::isNotBlank)?.let(::add)
      entry.teacher?.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" / ").ifBlank { null },
    tone = categoryTone(entry.category),
    leading = { Icon(categoryIcon(entry.category), contentDescription = null) },
    badge = {
      if (entry.history.isNotEmpty()) {
        FluidStatusBadge(
          label = "MODIFICATO",
          tone = FluidTone.Info,
        )
      }
      FluidStatusBadge(
        label = categoryLabel(entry.category),
        tone = categoryTone(entry.category),
      )
    },
    onClick = onClick,
    onLongClick = onLongClick,
    contextActions = contextActions,
    modifier = modifier,
    animatePress = onClick != null || onLongClick != null,
  )
}

@Composable
private fun AgendaDetailContent(
  entry: AgendaEntry,
  onShare: () -> Unit,
) {
  var showHistory by rememberSaveable(entry.id) { mutableStateOf(false) }

  Box {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        FluidListRow(
          title = entry.title,
          subtitle = entry.subject ?: entry.subtitle.ifBlank { "Agenda" },
          eyebrow = categoryLabel(entry.category),
          meta = entry.eventDateLabel(),
          tone = categoryTone(entry.category),
          badge = { FluidStatusBadge(categoryLabel(entry.category), tone = categoryTone(entry.category)) },
        )
      }
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          InfoLine(label = "Data evento", value = entry.eventDateLabel())
          entry.createdAtLabel()?.let { addedAt ->
            InfoLine(label = "Aggiunto", value = addedAt)
          }
          entry.modifiedAtLabel()?.let { modifiedAt ->
            InfoLine(label = "Modificato", value = modifiedAt)
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
        FluidCard(highlighted = true, glass = true) {
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
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (entry.history.isNotEmpty()) {
            FluidButton(
              text = if (showHistory) "Nascondi cronologia" else "Cronologia versioni (${entry.history.size})",
              onClick = { showHistory = !showHistory },
              style = FluidButtonStyle.Tinted,
              fillWidth = true,
            )
          }
          // Niente bottone "Chiudi": il popover si congeda con un tocco fuori o col back, e un
          // bottone che duplica il gesto occupava meta' della riga delle azioni.
          FluidButton(
            text = "Condividi",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            style = FluidButtonStyle.Tinted,
          )
        }
      }
      if (showHistory && entry.history.isNotEmpty()) {
        item {
          AgendaHistorySection(entry = entry)
        }
      }
      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun AgendaHistorySection(entry: AgendaEntry) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    FluidSectionHeader("Cronologia versioni")
    AgendaVersionCard(
      label = "Versione attuale",
      title = entry.title,
      subtitle = entry.subject ?: entry.subtitle.ifBlank { "Agenda" },
      eventDate = entry.eventDateLabel(),
      detail = entry.detail,
      teacher = entry.teacher,
      recordedAt = entry.modifiedAtLabel(),
      category = entry.category,
    )
    entry.history.forEachIndexed { index, version ->
      AgendaVersionCard(
        label = "Versione precedente ${index + 1}",
        title = version.title,
        subtitle = version.subject ?: version.subtitle.ifBlank { "Agenda" },
        eventDate = version.eventDateLabel(),
        detail = version.detail,
        teacher = version.teacher,
        recordedAt = version.recordedAtEpochMillis.toReadableDateTime(),
        category = version.category,
      )
    }
  }
}

@Composable
private fun AgendaVersionCard(
  label: String,
  title: String,
  subtitle: String,
  eventDate: String,
  detail: String?,
  teacher: String?,
  recordedAt: String?,
  category: AgendaCategory,
) {
  FluidCard(highlighted = label == "Versione attuale", glass = true) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    InfoLine(label = "Data evento", value = eventDate)
    recordedAt?.let { InfoLine(label = if (label == "Versione attuale") "Ultima modifica" else "Rilevata", value = it) }
    teacher?.takeIf(String::isNotBlank)?.let { InfoLine(label = "Docente", value = it) }
    detail?.takeIf(String::isNotBlank)?.let { InfoLine(label = "Dettagli", value = it) }
    FluidStatusBadge(categoryLabel(category), tone = categoryTone(category))
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
private fun AddEventContent(
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

  Box {
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
        FluidTextField(
          value = title,
          onValueChange = { title = it },
          modifier = Modifier.fillMaxWidth(),
          label = "Titolo",
          singleLine = true,
        )
      }
      item {
        FluidTextField(
          value = subject,
          onValueChange = { subject = it },
          modifier = Modifier.fillMaxWidth(),
          label = "Materia o tag",
          singleLine = true,
        )
      }
      item {
        FluidListRow(
          title = "Data",
          subtitle = date.toLocalDateOrNull()
            ?.format(eventDateFormatter)
            ?.replaceFirstChar { it.uppercase() }
            ?: date,
          eyebrow = "DatePicker",
          tone = FluidTone.Primary,
          onClick = { showDatePicker = true },
          badge = { FluidStatusBadge("SELEZIONA", tone = FluidTone.Primary) },
          animatePress = true,
        )
      }
      item {
        FluidListRow(
          title = "Ora",
          subtitle = if (time.isBlank()) "Opzionale" else time,
          eyebrow = "TimePicker",
          tone = FluidTone.Info,
          onClick = { showTimePicker = true },
          badge = { FluidStatusBadge(if (time.isBlank()) "OPZIONALE" else "IMPOSTATA", tone = FluidTone.Info) },
          animatePress = true,
        )
      }
      item {
        FluidTextField(
          value = description,
          onValueChange = { description = it },
          modifier = Modifier.fillMaxWidth(),
          label = "Dettagli",
          minLines = 3,
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FluidButton(
            text = "Annulla",
            onClick = onDismiss,
            style = FluidButtonStyle.Plain,
          )
          FluidButton(
            text = "Salva",
            onClick = { onSave(title, description, subject, date, time) },
            style = FluidButtonStyle.Filled,
            enabled = title.isNotBlank() && date.isNotBlank(),
          )
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
        FluidButton(
          text = "Seleziona",
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              date = millisToLocalDate(millis).toString()
            }
            showDatePicker = false
          },
          style = FluidButtonStyle.Plain,
        )
      },
      dismissButton = {
        FluidButton(
          text = "Annulla",
          onClick = { showDatePicker = false },
          style = FluidButtonStyle.Plain,
        )
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
    FluidAlert(
      onDismissRequest = { showTimePicker = false },
      title = "Seleziona orario",
      actions = listOf(
        FluidAlertAction("Rimuovi", {
                time = ""
                showTimePicker = false
              }),
        FluidAlertAction("Annulla", { showTimePicker = false }),
        FluidAlertAction("Conferma", {
            time = String.format(italianLocale, "%02d:%02d", timePickerState.hour, timePickerState.minute)
            showTimePicker = false
          }, FluidAlertAction.Emphasis.Preferred),
      ),
      content = { TimePicker(state = timePickerState) },
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
  val history: List<AgendaItemVersion>,
)

private fun AgendaUiState.toAgendaEntries(): List<AgendaEntry> = buildList {
  addAll(items.map {
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
      history = it.history,
    )
  })
  addAll(customEvents.map {
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
      history = emptyList(),
    )
  })
}.filter { it.date != null }
  .sortedWith(compareBy<AgendaEntry> { it.date }.thenBy { it.time ?: "" })

private fun buildCalendarCells(month: YearMonth): List<LocalDate> {
  val firstDay = month.atDay(1)
  val leading = (firstDay.dayOfWeek.value + 6) % 7
  val start = firstDay.minusDays(leading.toLong())
  return (0 until 42).map { start.plusDays(it.toLong()) }
}

private fun categoryTone(category: AgendaCategory): FluidTone {
  return when (category) {
    AgendaCategory.HOMEWORK -> FluidTone.Warning
    AgendaCategory.ASSESSMENT -> FluidTone.Danger
    AgendaCategory.LESSON -> FluidTone.Neutral
    AgendaCategory.EVENT,
    AgendaCategory.CUSTOM,
    -> FluidTone.Success
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

@Composable
fun AgendaDetailRoute(
  entryId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AgendaViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val entry = remember(state.items, state.customEvents, entryId) {
    state.toAgendaEntries().firstOrNull { it.id == entryId }
  }
  var showHistory by rememberSaveable(entryId) { mutableStateOf(false) }

  if (entry == null) {
    FluidScreen(
      title = "Dettaglio agenda",
      modifier = modifier,
      onBack = onBack,
    ) {
      item(key = "agenda-detail-missing") {
        FluidEmptyState(
          title = "Voce non disponibile",
          detail = "L'elemento potrebbe essere stato rimosso o non ancora sincronizzato.",
        )
      }
    }
    return
  }

  FluidContainerScaffold(
    title = "Dettaglio agenda",
    modifier = modifier,
    onBack = onBack,
    hero = {
      AgendaEntryRow(
        entry = entry,
        onClick = null,
        onLongClick = null,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    secondary = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoLine(label = "Data evento", value = entry.eventDateLabel())
        entry.createdAtLabel()?.let { InfoLine(label = "Aggiunto", value = it) }
        entry.modifiedAtLabel()?.let { InfoLine(label = "Modificato", value = it) }
        entry.subject?.takeIf(String::isNotBlank)?.let { InfoLine(label = "Materia", value = it) }
        entry.teacher?.takeIf(String::isNotBlank)?.let { InfoLine(label = "Docente", value = it) }
      }
      HorizontalDivider()
      FluidCard(highlighted = true, glass = true) {
        Text(
          text = "Dettagli",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = entry.detail?.takeIf(String::isNotBlank)
            ?: "Nessuna descrizione completa disponibile.",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
      if (entry.history.isNotEmpty()) {
        FluidButton(
          text = if (showHistory) "Nascondi cronologia" else "Cronologia versioni (${entry.history.size})",
          onClick = { showHistory = !showHistory },
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
        )
      }
      if (showHistory) AgendaHistorySection(entry)
      FluidButton(
        text = "Condividi",
        onClick = { shareEntry(context, entry) },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    },
  )
}

private fun categoryIcon(category: AgendaCategory): ImageVector {
  return when (category) {
    AgendaCategory.LESSON -> Icons.Rounded.School
    AgendaCategory.HOMEWORK -> Icons.AutoMirrored.Rounded.Assignment
    AgendaCategory.ASSESSMENT -> Icons.Rounded.Quiz
    AgendaCategory.EVENT -> Icons.Rounded.Event
    AgendaCategory.CUSTOM -> Icons.Rounded.EditCalendar
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

private fun AgendaEntry.modifiedAtLabel(): String? {
  return history.maxByOrNull { it.recordedAtEpochMillis }
    ?.recordedAtEpochMillis
    ?.toReadableDateTime()
}

private fun AgendaItemVersion.eventDateLabel(): String {
  return buildList {
    add(date.toLocalDateOrNull()?.format(eventDateFormatter)?.replaceFirstChar { it.uppercase() } ?: date)
    time?.takeIf(String::isNotBlank)?.let(::add)
  }.joinToString(" • ")
}

private fun Long.toReadableDateTime(): String {
  return Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()
    .format(createdAtFormatter)
}

private fun shareEntry(context: android.content.Context, entry: AgendaEntry) {
  entry.sharePayload?.takeIf { it.isNotBlank() }?.let { payload ->
    val intent = Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, payload)
    context.startActivity(Intent.createChooser(intent, "Condividi evento"))
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
