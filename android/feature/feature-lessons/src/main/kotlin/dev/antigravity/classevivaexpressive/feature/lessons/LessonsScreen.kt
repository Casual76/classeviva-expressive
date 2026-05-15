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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import dev.antigravity.classevivaexpressive.core.domain.model.slotFingerprint
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
  val editingSlot: TemplateSlot? = null,
  val confirmingSlot: TemplateSlot? = null,
  val settingRoomSlot: TemplateSlot? = null,
  val canImportOfficialTimetable: Boolean = false,
)

private data class DayOption(
  val key: String,
  val label: String,
)

private sealed interface SlotAction {
  data class Editing(val slot: TemplateSlot) : SlotAction
  data class Confirming(val slot: TemplateSlot) : SlotAction
  data class SettingRoom(val slot: TemplateSlot) : SlotAction
}

@HiltViewModel
class LessonsViewModel @Inject constructor(
  private val lessonsRepository: LessonsRepository,
  private val dashboardRepository: DashboardRepository,
) : ViewModel() {
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)
  private val slotAction = MutableStateFlow<SlotAction?>(null)

  val state = combine(
    combine(
      lessonsRepository.observeLessons(),
      lessonsRepository.observeTimetableTemplate(),
      dashboardRepository.observeDashboard(),
      ::Triple
    ),
    lastMessage,
    isRefreshing,
    slotAction,
  ) { (lessons, timetableTemplate, dashboard), message, refreshing, action ->
    val teachers = lessons.mapNotNull { it.teacher?.takeIf(String::isNotBlank) }.distinct().size
    val teacherSet = lessons.mapNotNull { it.teacher?.uppercase() }.toSet()
    val agnoletti4FSignatures = listOf(
      "MONTI ALESSANDRO", "PAOLETTI LAURA", "DE LUCA SIMONA",
      "FERRARA ELISA", "MUCCI SILVIA", "VESER CORRADO", "PISANO ELENA",
      "RICCIO EMANUELE", "RUGGERI CARLO", "IACI FILIPPA",
    )
    val profile = dashboard.profile
    val is4F = profile.schoolClass == "4" && profile.section.uppercase() == "F"
    val has4FSignatures = agnoletti4FSignatures.count { sig -> teacherSet.any { it.contains(sig) } } >= 5
    // Assuming 'dashboard.profile' might not have the year directly, we can check if it's the 25/26 year.
    // Or we check dashboard.schoolYear if available, let's just check the date since it's 2025/26
    val isYear2526 = dashboard.profile.schoolYear?.contains("25/26") == true || dashboard.profile.schoolYear?.contains("2025") == true
    val canImport = (is4F || has4FSignatures) && isYear2526
    
    LessonsUiState(
      lessons = lessons.sortedBy { "${it.date}-${it.time}" },
      timetableTemplate = timetableTemplate,
      totalTeachersCount = teachers,
      lastMessage = message,
      isRefreshing = refreshing,
      editingSlot = (action as? SlotAction.Editing)?.slot,
      confirmingSlot = (action as? SlotAction.Confirming)?.slot,
      settingRoomSlot = (action as? SlotAction.SettingRoom)?.slot,
      canImportOfficialTimetable = canImport,
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

  fun startEditing(slot: TemplateSlot) {
    slotAction.value = SlotAction.Editing(slot)
  }

  fun dismissEditing() {
    slotAction.value = null
  }

  fun startConfirming(slot: TemplateSlot) {
    slotAction.value = SlotAction.Confirming(slot)
  }

  fun dismissConfirming() {
    slotAction.value = null
  }

  fun saveSlotOverride(original: TemplateSlot, edited: TemplateSlot) {
    viewModelScope.launch {
      lessonsRepository.saveSlotOverride(original.slotFingerprint(), edited)
      slotAction.value = null
    }
  }

  fun confirmSlot(slot: TemplateSlot) {
    viewModelScope.launch {
      lessonsRepository.saveSlotOverride(slot.slotFingerprint(), slot.copy(confirmed = true))
      slotAction.value = null
    }
  }

  fun startSettingRoom(slot: TemplateSlot) {
    slotAction.value = SlotAction.SettingRoom(slot)
  }

  fun confirmSlotWithRoom(slot: TemplateSlot, room: String?) {
    viewModelScope.launch {
      lessonsRepository.saveSlotOverride(
        slot.slotFingerprint(),
        slot.copy(confirmed = true, room = room ?: slot.room),
      )
      slotAction.value = null
    }
  }

  fun deleteSlotOverride(slot: TemplateSlot) {
    viewModelScope.launch {
      lessonsRepository.deleteSlotOverride(slot.slotFingerprint())
      slotAction.value = null
    }
  }

  fun regenerateTemplate() {
    viewModelScope.launch {
      isRefreshing.value = true
      lessonsRepository.regenerateTimetableTemplate()
        .onSuccess { lastMessage.value = "Orario ricalcolato dal tuo storico." }
        .onFailure { lastMessage.value = it.message ?: "Ricalcolo orario fallito." }
      isRefreshing.value = false
    }
  }

  fun importOfficialTimetable() {
    viewModelScope.launch {
      isRefreshing.value = true
      lessonsRepository.importOfficialTemplate(agnoletti4FOfficialSlots())
        .onSuccess { lastMessage.value = "Orario ufficiale 4F importato." }
        .onFailure { lastMessage.value = it.message ?: "Importazione fallita." }
      isRefreshing.value = false
    }
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
  val templateBlocks = remember(templateSlots) { templateSlots.mergeIntoBlocks() }

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
          IconButton(onClick = viewModel::regenerateTemplate) {
            Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Ricalcola orario")
          }
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
              item {
                Text(
                  text = "Tocca uno slot per confermarlo · Tieni premuto per modificarlo.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 4.dp),
                )
              }
              if (state.canImportOfficialTimetable) {
                item {
                  Button(
                    onClick = viewModel::importOfficialTimetable,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Icon(
                      Icons.Rounded.AutoFixHigh,
                      contentDescription = null,
                      modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Importa Orario Ufficiale 4F")
                  }
                }
              }
              if (templateBlocks.isEmpty()) {
                item {
                  EmptyState(
                    title = "Nessuno slot stabile",
                    detail = "Per questo giorno servono piu settimane coerenti prima di proporre un template affidabile.",
                  )
                }
              } else {
                items(templateBlocks, key = { "${it.primary.dayOfWeek}-${it.primary.time}" }) { block ->
                  val primary = block.primary
                  val isOverridden = block.allSlots.any {
                    state.timetableTemplate.manualOverrides.containsKey(it.slotFingerprint())
                  }
                  val isConfirmed = block.allSlots.all { it.confirmed }
                  val displayRoom = block.allSlots
                    .mapNotNull { it.room?.trim()?.takeIf(String::isNotBlank) }
                    .firstOrNull()
                  val isOfficial = state.timetableTemplate.isOfficial
                  RegisterListRow(
                    title = block.displaySubject,
                    subtitle = primary.teacher ?: "Docente non specificato",
                    eyebrow = block.timeRangeLabel(),
                    meta = listOfNotNull(
                      displayRoom,
                      when {
                        isConfirmed -> "Confermato manualmente"
                        isOverridden -> "Modificato manualmente"
                        isOfficial -> "Importato da orario ufficiale"
                        block.isMulti -> "Blocco ${block.allSlots.size}h · ${(primary.confidence * 100).toInt()}%"
                        else -> "Ricorrenza ${(primary.confidence * 100).toInt()}% · ${primary.sampleCount} settimane"
                      },
                    ).joinToString(" / "),
                    tone = when {
                      isConfirmed -> ExpressiveTone.Success
                      isOverridden -> ExpressiveTone.Info
                      isOfficial -> ExpressiveTone.Success
                      primary.confidence >= 0.8f -> ExpressiveTone.Success
                      primary.confidence >= 0.6f -> ExpressiveTone.Info
                      else -> ExpressiveTone.Warning
                    },
                    onClick = { viewModel.startConfirming(primary) },
                    onLongClick = { viewModel.startEditing(primary) },
                    badge = {
                      when {
                        isConfirmed -> StatusBadge("CONFERMATO", tone = ExpressiveTone.Success)
                        isOverridden -> StatusBadge("MODIFICATO", tone = ExpressiveTone.Info)
                        isOfficial -> StatusBadge("IMPORT", tone = ExpressiveTone.Success)
                        block.isMulti -> StatusBadge("BLOCCO ${block.allSlots.size}H", tone = ExpressiveTone.Info)
                        primary.confidence >= 0.75f -> StatusBadge("STABILE", tone = ExpressiveTone.Success)
                        else -> StatusBadge("DINAMICO", tone = ExpressiveTone.Warning)
                      }
                    },
                    animatePress = false,
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

  state.editingSlot?.let { slot ->
    EditSlotSheet(
      slot = slot,
      onDismiss = viewModel::dismissEditing,
      onSave = { edited -> viewModel.saveSlotOverride(slot, edited) },
      onReset = { viewModel.deleteSlotOverride(slot) },
    )
  }

  state.confirmingSlot?.let { slot ->
    ConfirmSlotDialog(
      slot = slot,
      onDismiss = viewModel::dismissConfirming,
      onConfirm = { viewModel.startSettingRoom(slot) },
      onRemoveConfirm = { viewModel.deleteSlotOverride(slot) },
    )
  }

  state.settingRoomSlot?.let { slot ->
    RoomInputDialog(
      slot = slot,
      onDismiss = { viewModel.confirmSlotWithRoom(slot, null) },
      onSave = { room -> viewModel.confirmSlotWithRoom(slot, room) },
    )
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

private data class SlotBlock(
  val primary: TemplateSlot,
  val extra: List<TemplateSlot> = emptyList(),
) {
  val allSlots: List<TemplateSlot> get() = listOf(primary) + extra
  val isMulti: Boolean get() = extra.isNotEmpty()
  val displaySubject: String get() = allSlots.map { it.subject }.distinct().joinToString(" / ")

  fun timeRangeLabel(): String {
    val start = runCatching { LocalTime.parse(primary.time) }.getOrNull() ?: return primary.time
    val lastSlot = extra.lastOrNull() ?: primary
    val end = lastSlot.endTime
      ?.takeIf(String::isNotBlank)
      ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
      ?: start.plusMinutes(allSlots.sumOf { it.durationMinutes }.toLong())
    return "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
  }
}

private fun List<TemplateSlot>.mergeIntoBlocks(): List<SlotBlock> {
  if (isEmpty()) return emptyList()
  val sorted = sortedBy { it.time }
  val blocks = mutableListOf<SlotBlock>()
  var currentPrimary = sorted[0]
  val currentExtra = mutableListOf<TemplateSlot>()

  for (i in 1 until sorted.size) {
    val prev = currentExtra.lastOrNull() ?: currentPrimary
    val next = sorted[i]
    val sameTeacher = !prev.teacher.isNullOrBlank() && prev.teacher == next.teacher
    val consecutive = run {
      val prevEnd = runCatching { LocalTime.parse(prev.endTime ?: prev.time) }.getOrNull()
      val nextStart = runCatching { LocalTime.parse(next.time) }.getOrNull()
      if (prevEnd == null || nextStart == null) return@run false
      java.time.Duration.between(prevEnd, nextStart).toMinutes() in -2..5
    }
    if (sameTeacher && consecutive) {
      currentExtra.add(next)
    } else {
      blocks.add(SlotBlock(currentPrimary, currentExtra.toList()))
      currentPrimary = next
      currentExtra.clear()
    }
  }
  blocks.add(SlotBlock(currentPrimary, currentExtra.toList()))
  return blocks
}

@Composable
private fun ConfirmSlotDialog(
  slot: TemplateSlot,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  onRemoveConfirm: () -> Unit,
) {
  if (slot.confirmed) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Slot confermato") },
      text = { Text("Questo slot e gia confermato. Vuoi rimuovere la conferma e lasciare che l'algoritmo lo rivaluti?") },
      confirmButton = {
        TextButton(onClick = onRemoveConfirm) { Text("Rimuovi conferma") }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("Annulla") }
      },
    )
  } else {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("È corretto?") },
      text = {
        Text(
          "${slot.subject}${slot.teacher?.let { " con $it" }.orEmpty()} · ${slot.timeRangeLabel()}.",
        )
      },
      confirmButton = {
        TextButton(onClick = onConfirm) { Text("Sì, è corretto") }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("Annulla") }
      },
    )
  }
}

@Composable
private fun RoomInputDialog(
  slot: TemplateSlot,
  onDismiss: () -> Unit,
  onSave: (room: String?) -> Unit,
) {
  var room by rememberSaveable { mutableStateOf(slot.room.orEmpty()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Aula") },
    text = {
      OutlinedTextField(
        value = room,
        onValueChange = { room = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Aula (opzionale)") },
        placeholder = { Text("es. P1 Aula 21") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { onSave(room.trim().ifBlank { null }) }) { Text("Salva") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Salta") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSlotSheet(
  slot: TemplateSlot,
  onDismiss: () -> Unit,
  onSave: (TemplateSlot) -> Unit,
  onReset: () -> Unit,
) {
  var subject by rememberSaveable { mutableStateOf(slot.subject) }
  var teacher by rememberSaveable { mutableStateOf(slot.teacher.orEmpty()) }
  var room by rememberSaveable { mutableStateOf(slot.room.orEmpty()) }
  var startTime by rememberSaveable { mutableStateOf(slot.time) }
  var endTime by rememberSaveable { mutableStateOf(slot.endTime.orEmpty()) }
  var editingField by rememberSaveable { mutableStateOf<String?>(null) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Modifica slot orario", style = MaterialTheme.typography.headlineSmall)
      OutlinedTextField(
        value = subject,
        onValueChange = { subject = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Materia") },
        singleLine = true,
      )
      OutlinedTextField(
        value = teacher,
        onValueChange = { teacher = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Docente (opzionale)") },
        singleLine = true,
      )
      OutlinedTextField(
        value = room,
        onValueChange = { room = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Aula (opzionale)") },
        singleLine = true,
      )
      RegisterListRow(
        title = "Ora inizio",
        subtitle = startTime.ifBlank { "Non impostata" },
        eyebrow = "Orario",
        tone = ExpressiveTone.Info,
        onClick = { editingField = "start" },
        badge = { StatusBadge("MODIFICA", tone = ExpressiveTone.Info) },
        animatePress = false,
      )
      RegisterListRow(
        title = "Ora fine",
        subtitle = endTime.ifBlank { "Non impostata" },
        eyebrow = "Orario",
        tone = ExpressiveTone.Info,
        onClick = { editingField = "end" },
        badge = { StatusBadge("MODIFICA", tone = ExpressiveTone.Info) },
        animatePress = false,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(onClick = { onReset(); onDismiss() }) { Text("Ripristina") }
        TextButton(onClick = onDismiss) { Text("Annulla") }
        Button(
          onClick = {
            val resolvedDuration = runCatching {
              val s = LocalTime.parse(startTime)
              val e = LocalTime.parse(endTime.ifBlank { startTime })
              java.time.Duration.between(s, e).toMinutes().toInt().coerceAtLeast(slot.durationMinutes)
            }.getOrDefault(slot.durationMinutes)
            onSave(
              slot.copy(
                subject = subject.trim().ifBlank { slot.subject },
                teacher = teacher.trim().ifBlank { null },
                room = room.trim().ifBlank { null },
                time = startTime.ifBlank { slot.time },
                endTime = endTime.ifBlank { null },
                durationMinutes = resolvedDuration,
                confirmed = false,
              ),
            )
          },
          enabled = subject.isNotBlank(),
        ) {
          Text("Salva")
        }
      }
    }
  }

  if (editingField != null) {
    val currentValue = if (editingField == "start") startTime else endTime
    val initialTime = runCatching { LocalTime.parse(currentValue) }.getOrElse { LocalTime.of(8, 0) }
    val timePickerState = rememberTimePickerState(
      initialHour = initialTime.hour,
      initialMinute = initialTime.minute,
      is24Hour = true,
    )
    AlertDialog(
      onDismissRequest = { editingField = null },
      title = { Text(if (editingField == "start") "Ora inizio" else "Ora fine") },
      text = { TimePicker(state = timePickerState) },
      confirmButton = {
        TextButton(
          onClick = {
            val formatted = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
            if (editingField == "start") startTime = formatted else endTime = formatted
            editingField = null
          },
        ) { Text("Imposta") }
      },
      dismissButton = {
        TextButton(onClick = { editingField = null }) { Text("Annulla") }
      },
    )
  }
}

private fun agnoletti4FOfficialSlots(): List<TemplateSlot> = listOf(
  // LUNEDÌ
  TemplateSlot(1, "08:00", "09:00", 60, "Storia", "MONTI ALESSANDRO", "P1 Aula 21"),
  TemplateSlot(1, "09:00", "10:00", 60, "Sc. Naturali", "PAOLETTI LAURA", "P1 Aula 21"),
  TemplateSlot(1, "10:00", "11:00", 60, "Inglese", "DE LUCA SIMONA", "P1 Aula 21"),
  TemplateSlot(1, "11:00", "12:00", 60, "Filosofia", "MONTI ALESSANDRO", "P1 Aula 21"),
  TemplateSlot(1, "12:00", "13:00", 60, "Fisica", "MUCCI SILVIA", "P1 Lab fisica"),
  // MARTEDÌ
  TemplateSlot(2, "08:00", "09:00", 60, "Sc. Naturali", "PAOLETTI LAURA", "P2 Aula 36 Scienze"),
  TemplateSlot(2, "09:00", "10:00", 60, "Sc. Naturali", "PAOLETTI LAURA", "P2 Aula 36 Scienze"),
  TemplateSlot(2, "10:00", "11:00", 60, "Italiano", "FERRARA ELISA", "P2 Aula 33"),
  TemplateSlot(2, "11:00", "12:00", 60, "Inglese", "DE LUCA SIMONA", "P2 Aula 33"),
  TemplateSlot(2, "12:00", "13:00", 60, "Matematica", "MUCCI SILVIA", "P2 Aula 33"),
  // MERCOLEDÌ
  TemplateSlot(3, "08:00", "09:00", 60, "Italiano", "FERRARA ELISA", "P2 Aula 31"),
  TemplateSlot(3, "09:00", "10:00", 60, "Sc. Naturali", "PAOLETTI LAURA", "P2 Aula 31"),
  TemplateSlot(3, "10:00", "11:00", 60, "Informatica", "VESER CORRADO", "P1 Aula 17 - Lab Info 3"),
  TemplateSlot(3, "11:00", "12:00", 60, "Informatica", "VESER CORRADO", "P1 Aula 17 - Lab Info 3"),
  TemplateSlot(3, "12:00", "13:00", 60, "Dis e Storia dell'arte", "RICCIO EMANUELE", "P0 Aula 11"),
  // GIOVEDÌ
  TemplateSlot(4, "08:00", "09:00", 60, "Fisica", "MUCCI SILVIA", "P1 Aula fisica"),
  TemplateSlot(4, "09:00", "10:00", 60, "Inglese", "DE LUCA SIMONA", "P1 Aula 28"),
  TemplateSlot(4, "10:00", "11:00", 60, "Dis e Storia dell'arte", "RICCIO EMANUELE", "P0 Aula 01 dis"),
  TemplateSlot(4, "11:00", "12:00", 60, "Filosofia", "MONTI ALESSANDRO", "P0 Aula 09"),
  TemplateSlot(4, "12:00", "13:00", 60, "Storia", "MONTI ALESSANDRO", "P0 Aula 09"),
  // VENERDÌ
  TemplateSlot(5, "08:00", "09:00", 60, "Matematica", "MUCCI SILVIA", "P1 Aula 21"),
  TemplateSlot(5, "09:00", "10:00", 60, "Matematica", "MUCCI SILVIA", "P1 Aula 21"),
  TemplateSlot(5, "10:00", "11:00", 60, "Italiano", "FERRARA ELISA", "P1 Aula 24"),
  TemplateSlot(5, "11:00", "12:00", 60, "Italiano", "FERRARA ELISA", "P1 Aula 24"),
  TemplateSlot(5, "12:00", "13:00", 60, "Sc. Naturali", "PAOLETTI LAURA", "P2 Aula 34"),
  TemplateSlot(5, "13:00", "14:00", 60, "Religione", "IACI FILIPPA", "P2 Aula 37"),
  // SABATO
  TemplateSlot(6, "08:00", "09:00", 60, "Sc. Motorie", "PISANO ELENA", "Palestre Sesto"),
  TemplateSlot(6, "09:00", "10:00", 60, "Sc. Motorie", "PISANO ELENA", "Palestre Sesto"),
  TemplateSlot(6, "10:00", "11:00", 60, "Fisica", "MUCCI SILVIA", "P1 Aula Lab info 1"),
  TemplateSlot(6, "11:00", "12:00", 60, "Matematica", "MUCCI SILVIA", "P1 Aula Lab info 1"),
)
