package dev.antigravity.classevivaexpressive.feature.lessons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHero
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHeroMetric
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.fluidGlassGroups
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
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
import kotlin.math.abs
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.currentCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberEmptyGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidPillTabs
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

private const val TAB_TIMETABLE = "Orario"
private const val TAB_HISTORY = "Lezioni svolte"
private val lessonTabs = listOf(TAB_TIMETABLE, TAB_HISTORY)
private val italianLocale: Locale = Locale.forLanguageTag("it-IT")
private val weekdayShortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", italianLocale)
private val weekdayLongFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", italianLocale)
private val weekHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private enum class LessonsContentType {
  Message,
  ModeSelector,
  Hero,
  DaySelector,
  Instruction,
  ImportAction,
  WeekNavigator,
  SectionHeader,
  EmptyDay,
  TimetableRow,
  HistoryRow,
}

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
  var weekOffset by rememberSaveable { mutableIntStateOf(0) }
  val templateListState = rememberLazyListState()
  val historyListState = rememberLazyListState()
  val activeListState = if (selectedTab == TAB_TIMETABLE) templateListState else historyListState
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current

  // Un giorno alla volta. La lista mostrava la settimana intera in sei sezioni, e i tasti giorno
  // scrollavano fino alla sezione — con la barra laterale a fare da terza mano. Ora i tasti
  // *scelgono* la giornata visibile, il cambio scivola di lato come ogni altra transizione
  // dell'app, e scroll sincronizzato, ancore e barra laterale non hanno piu' niente da fare.
  val dayMotion = remember { Animatable(0f) }
  fun slideDay(direction: Float) {
    scope.launch {
      dayMotion.snapTo(direction * with(density) { 28.dp.toPx() })
      dayMotion.animateTo(0f, FluidMotion.standard())
    }
  }
  val dayMotionModifier = Modifier.graphicsLayer {
    translationX = dayMotion.value
    alpha = 1f - (abs(dayMotion.value) / 140.dp.toPx()).coerceIn(0f, 0.16f)
  }

  val templateByDay = remember(state.timetableTemplate) { state.timetableTemplate.slotsByDay() }
  val visibleDays = remember { stableSchoolDays() }
  val templateDayOptions = remember(visibleDays) {
    visibleDays.map { day -> DayOption(key = day.name, label = day.shortLabel()) }
  }
  val templateDayLabels = remember(templateDayOptions) { templateDayOptions.map(DayOption::label) }
  val templateSections = remember(visibleDays, templateByDay) {
    buildTimetableDaySections(visibleDays, templateByDay)
  }

  val currentWeekStart = remember(weekOffset) { schoolWeekStart(weekOffset) }
  val historyDays = remember(currentWeekStart) {
    visibleDays.indices.map { currentWeekStart.plusDays(it.toLong()) }
  }
  val historyDayOptions = remember(historyDays) {
    historyDays.map { date ->
      DayOption(
        key = date.toString(),
        label = date.format(weekdayShortFormatter).replaceFirstChar { it.uppercase() },
      )
    }
  }
  val historyDayLabels = remember(historyDayOptions) { historyDayOptions.map(DayOption::label) }
  val historySections = remember(state.lessons, historyDays) {
    buildHistoryDaySections(historyDays, state.lessons)
  }
  val weekLessonCount = remember(historySections) { historySections.sumOf { it.lessons.size } }

  LaunchedEffect(templateDayOptions) {
    if (selectedTemplateDayKey == null || templateDayOptions.none { it.key == selectedTemplateDayKey }) {
      val todayKey = LocalDate.now().dayOfWeek.name
      selectedTemplateDayKey = templateDayOptions.firstOrNull { it.key == todayKey }?.key
        ?: templateDayOptions.firstOrNull()?.key
    }
  }
  LaunchedEffect(historyDayOptions) {
    if (selectedHistoryDayKey == null || historyDayOptions.none { it.key == selectedHistoryDayKey }) {
      val todayKey = LocalDate.now().toString()
      selectedHistoryDayKey = historyDayOptions.firstOrNull { it.key == todayKey }?.key
        ?: historyDayOptions.firstOrNull()?.key
    }
  }

  fun selectTemplateDay(key: String) {
    if (key == selectedTemplateDayKey) return
    val from = templateDayOptions.indexOfFirst { it.key == selectedTemplateDayKey }
    val to = templateDayOptions.indexOfFirst { it.key == key }
    selectedTemplateDayKey = key
    slideDay(if (to >= from) 1f else -1f)
  }

  fun selectHistoryDay(key: String) {
    if (key == selectedHistoryDayKey) return
    val from = historyDayOptions.indexOfFirst { it.key == selectedHistoryDayKey }
    val to = historyDayOptions.indexOfFirst { it.key == key }
    selectedHistoryDayKey = key
    slideDay(if (to >= from) 1f else -1f)
  }

  fun changeWeek(targetOffset: Int) {
    if (targetOffset == weekOffset) return
    val selectedDay = selectedHistoryDayKey?.toLocalDateOrNull()?.dayOfWeek ?: DayOfWeek.MONDAY
    val targetStart = schoolWeekStart(targetOffset)
    val dayIndex = visibleDays.indexOf(selectedDay).coerceAtLeast(0)
    selectedHistoryDayKey = targetStart.plusDays(dayIndex.toLong()).toString()
    slideDay(if (targetOffset > weekOffset) 1f else -1f)
    weekOffset = targetOffset
  }

  val templateSection = templateSections.firstOrNull { it.day.name == selectedTemplateDayKey }
    ?: templateSections.firstOrNull()
  val historySection = historySections.firstOrNull { it.date.toString() == selectedHistoryDayKey }
    ?: historySections.firstOrNull()

  Box(modifier = modifier.fillMaxSize()) {
    FluidScreen(
      modifier = Modifier.fillMaxSize(),
      title = "Orario",
      ambient = FeatureIdentity.Lessons.ambient(),
      subtitle = "Template settimanale stabile e storico delle lezioni svolte in una sola vista.",
      onBack = onBack,
      actions = {
        // Un tasto solo, con dentro tutti i verbi: toccato aggiorna, tenuto premuto offre anche il
        // ricalcolo e l'importazione — che in barra non ci stavano e prima erano un secondo tasto
        // dal significato oscuro.
        FluidBarAction(
          icon = Icons.Rounded.Refresh,
          contentDescription = "Aggiorna",
          onClick = viewModel::refresh,
          actions = {
            buildList {
              add(
                FluidContextAction(
                  label = "Aggiorna",
                  icon = Icons.Rounded.Refresh,
                  onClick = viewModel::refresh,
                ),
              )
              add(
                FluidContextAction(
                  label = "Ricalcola orario",
                  icon = Icons.Rounded.AutoFixHigh,
                  onClick = viewModel::regenerateTemplate,
                ),
              )
              if (state.canImportOfficialTimetable) {
                add(
                  FluidContextAction(
                    label = "Importa orario ufficiale 4F",
                    icon = Icons.Rounded.AutoStories,
                    onClick = viewModel::importOfficialTimetable,
                  ),
                )
              }
            }
          },
        )
      },
      listState = activeListState,
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
      horizontalPadding = 20.dp,
      itemSpacing = 18.dp,
    ) {
      if (!state.lastMessage.isNullOrBlank()) {
        item(key = "lessons:message", contentType = LessonsContentType.Message) {
          FluidInlineMessage(
            message = state.lastMessage.orEmpty(),
            title = "Aggiornamento orario",
            tone = FluidTone.Warning,
            onDismiss = viewModel::clearMessage,
          )
        }
      }

      item(key = "lessons:mode", contentType = LessonsContentType.ModeSelector) {
        FluidPillTabs(
          options = lessonTabs,
          selected = selectedTab,
          onSelect = { selectedTab = it },
        )
      }

      item(key = "lessons:hero", contentType = LessonsContentType.Hero) {
        val showingTemplate = selectedTab == TAB_TIMETABLE
        val showingOfficialTemplate = showingTemplate && state.timetableTemplate.isOfficial
        val visibleCount = if (showingTemplate) state.timetableTemplate.slots.size else weekLessonCount
        FeatureHero(
          identity = FeatureIdentity.Lessons,
          eyebrow = when {
            showingOfficialTemplate -> "Orario ufficiale"
            showingTemplate -> "Settimana ricorrente"
            else -> "Storico selezionato"
          },
          value = visibleCount.toString(),
          title = if (showingTemplate) "slot nell'orario" else if (visibleCount == 1) {
            "lezione nella settimana"
          } else {
            "lezioni nella settimana"
          },
          description = when {
            showingOfficialTemplate -> "L'orario ufficiale importato, con conferme e modifiche manuali sempre riconoscibili."
            showingTemplate -> "Una timeline stabile costruita dalle ricorrenze reali e dalle tue conferme."
            else -> "Argomenti e firme della settimana scelta, organizzati giorno per giorno."
          },
          icon = Icons.Rounded.AutoStories,
          metrics = listOf(
            FeatureHeroMetric("Settimane campione", state.timetableTemplate.sampledWeeks.toString()),
            FeatureHeroMetric("Docenti rilevati", state.totalTeachersCount.toString()),
            FeatureHeroMetric("Lezioni archiviate", state.lessons.size.toString()),
          ),
        )
      }

      when (selectedTab) {
        TAB_TIMETABLE -> {
          item(key = "lessons:template:selector", contentType = LessonsContentType.DaySelector) {
            FluidPillTabs(
              options = templateDayLabels,
              selected = templateDayOptions.firstOrNull { it.key == selectedTemplateDayKey }?.label
                ?: templateDayOptions.first().label,
              onSelect = { label ->
                templateDayOptions.firstOrNull { it.label == label }?.let { selectTemplateDay(it.key) }
              },
            )
          }
          item(key = "lessons:template:instruction", contentType = LessonsContentType.Instruction) {
            Text(
              text = "Tocca uno slot per confermarlo · Tieni premuto per modificarlo.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 4.dp),
            )
          }
          if (state.canImportOfficialTimetable) {
            item(key = "lessons:template:official-import", contentType = LessonsContentType.ImportAction) {
              FluidButton(
                text = "Importa Orario Ufficiale 4F",
                onClick = viewModel::importOfficialTimetable,
                style = FluidButtonStyle.Filled,
                fillWidth = true,
                leading = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null) },
              )
            }
          }
          templateSection?.let { section ->
            item(
              key = "lessons:template:${section.day}:header",
              contentType = LessonsContentType.SectionHeader,
            ) {
              FluidSectionHeader(
                title = section.day.longLabel(),
                detail = "Orario ricorrente",
                modifier = dayMotionModifier,
              )
            }
            if (section.blocks.isEmpty()) {
              item(
                key = "lessons:template:${section.day}:empty",
                contentType = LessonsContentType.EmptyDay,
              ) {
                TimelineEmptyDay(
                  message = "Nessuno slot stabile per questa giornata.",
                  modifier = dayMotionModifier,
                )
              }
            } else {
              items(
                items = section.blocks,
                key = { block -> "lessons:template:${section.day}:${block.primary.time}:${block.primary.subject}" },
                contentType = { LessonsContentType.TimetableRow },
              ) { block ->
                TimetableBlockRow(
                  block = block,
                  timetable = state.timetableTemplate,
                  onConfirm = { viewModel.startConfirming(block.primary) },
                  onEdit = { viewModel.startEditing(block.primary) },
                  modifier = dayMotionModifier,
                )
              }
            }
          }
        }

        TAB_HISTORY -> {
          item(key = "lessons:history:week", contentType = LessonsContentType.WeekNavigator) {
            WeekNavigator(
              weekStart = currentWeekStart,
              weekOffset = weekOffset,
              onPrevious = { changeWeek(weekOffset - 1) },
              onNext = { if (weekOffset < 0) changeWeek(weekOffset + 1) },
              onToday = { changeWeek(0) },
            )
          }
          item(key = "lessons:history:selector", contentType = LessonsContentType.DaySelector) {
            FluidPillTabs(
              options = historyDayLabels,
              selected = historyDayOptions.firstOrNull { it.key == selectedHistoryDayKey }?.label
                ?: historyDayOptions.first().label,
              onSelect = { label ->
                historyDayOptions.firstOrNull { it.label == label }?.let { selectHistoryDay(it.key) }
              },
            )
          }
          historySection?.let { section ->
            item(
              key = "lessons:history:${section.date}:header",
              contentType = LessonsContentType.SectionHeader,
            ) {
              FluidSectionHeader(
                title = section.date.format(weekdayLongFormatter)
                  .replaceFirstChar(Char::uppercase),
                detail = "Lezioni svolte",
                modifier = dayMotionModifier,
              )
            }
            if (section.lessons.isEmpty()) {
              item(
                key = "lessons:history:${section.date}:empty",
                contentType = LessonsContentType.EmptyDay,
              ) {
                TimelineEmptyDay(
                  message = "Nessuna lezione registrata.",
                  modifier = dayMotionModifier,
                )
              }
            } else {
              fluidGlassGroups(
                items = section.lessons,
                key = "lessons:history:${section.date}:group",
              ) { lesson ->
                HistoryLessonRow(
                  lesson = lesson,
                  modifier = dayMotionModifier,
                )
              }
            }
          }
        }
      }
    }

  }

  // Portali, non sheet: dichiarati sempre, visibili quando c'e' uno slot selezionato. Un portale
  // dentro un `?.let` si smonterebbe alla chiusura e porterebbe via l'animazione di uscita.
  FluidGlassModalPortal(
    item = state.editingSlot,
    onDismissRequest = viewModel::dismissEditing,
    paneTitle = "Modifica slot orario",
  ) { slot ->
    EditSlotContent(
      slot = slot,
      onDismiss = viewModel::dismissEditing,
      onSave = { edited -> viewModel.saveSlotOverride(slot, edited) },
      onReset = { viewModel.deleteSlotOverride(slot) },
    )
  }

  FluidGlassModalPortal(
    item = state.confirmingSlot ?: state.settingRoomSlot,
    onDismissRequest = viewModel::dismissConfirming,
    paneTitle = "Conferma slot",
  ) { slot ->
    SlotConfirmationContent(
      slot = slot,
      enteringRoom = state.settingRoomSlot != null,
      onDismiss = viewModel::dismissConfirming,
      onContinue = { viewModel.startSettingRoom(slot) },
      onBack = { viewModel.startConfirming(slot) },
      onSave = { room -> viewModel.confirmSlotWithRoom(slot, room) },
      onRemoveConfirm = { viewModel.deleteSlotOverride(slot) },
    )
  }
}

internal data class TimetableDaySection(
  val day: DayOfWeek,
  val blocks: List<SlotBlock>,
)

internal data class HistoryDaySection(
  val date: LocalDate,
  val lessons: List<Lesson>,
)

internal fun stableSchoolDays(): List<DayOfWeek> = listOf(
  DayOfWeek.MONDAY,
  DayOfWeek.TUESDAY,
  DayOfWeek.WEDNESDAY,
  DayOfWeek.THURSDAY,
  DayOfWeek.FRIDAY,
  DayOfWeek.SATURDAY,
)

internal fun schoolWeekStart(
  weekOffset: Int,
  today: LocalDate = LocalDate.now(),
): LocalDate = today
  .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  .plusWeeks(weekOffset.toLong())

internal fun buildTimetableDaySections(
  visibleDays: List<DayOfWeek>,
  slotsByDay: Map<DayOfWeek, List<TemplateSlot>>,
): List<TimetableDaySection> = visibleDays.map { day ->
  TimetableDaySection(
    day = day,
    blocks = slotsByDay[day].orEmpty().mergeIntoBlocks(),
  )
}

internal fun buildHistoryDaySections(
  days: List<LocalDate>,
  lessons: List<Lesson>,
): List<HistoryDaySection> {
  val byDate = HashMap<LocalDate, MutableList<Lesson>>(days.size)
  days.forEach { date -> byDate[date] = mutableListOf() }
  lessons.forEach { lesson ->
    val date = lesson.date.toLocalDateOrNull() ?: return@forEach
    byDate[date]?.add(lesson)
  }
  return days.map { date ->
    HistoryDaySection(
      date = date,
      lessons = byDate[date].orEmpty().sortedBy { it.time },
    )
  }
}

@Composable
private fun TimelineEmptyDay(
  message: String,
  modifier: Modifier = Modifier,
) {
  FluidListRow(
    title = "Giornata libera",
    subtitle = message,
    eyebrow = "Nessuna attività",
    tone = FluidTone.Neutral,
    modifier = modifier,
  )
}

@Composable
private fun TimetableBlockRow(
  block: SlotBlock,
  timetable: TimetableTemplate,
  onConfirm: () -> Unit,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primary = block.primary
  val isOverridden = block.allSlots.any {
    timetable.manualOverrides.containsKey(it.slotFingerprint())
  }
  val isConfirmed = block.allSlots.all(TemplateSlot::confirmed)
  val displayRoom = block.allSlots
    .mapNotNull { it.room?.trim()?.takeIf(String::isNotBlank) }
    .firstOrNull()
  val isOfficial = timetable.isOfficial
  FluidListRow(
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
      isConfirmed -> FluidTone.Success
      isOverridden -> FluidTone.Info
      isOfficial -> FluidTone.Success
      primary.confidence >= 0.8f -> FluidTone.Success
      primary.confidence >= 0.6f -> FluidTone.Info
      else -> FluidTone.Warning
    },
    leading = { Icon(Icons.Rounded.School, contentDescription = null) },
    onClick = onConfirm,
    // Era un onLongClick che apriva la modifica senza dirlo. Il menu dice entrambe le cose che
    // questa riga sa fare, e il tap resta la piu' frequente.
    contextActions = {
      listOf(
        FluidContextAction(
          label = "Conferma",
          icon = Icons.Rounded.School,
          onClick = onConfirm,
        ),
        FluidContextAction(
          label = "Modifica",
          icon = Icons.Rounded.Edit,
          onClick = onEdit,
        ),
      )
    },
    badge = {
      when {
        isConfirmed -> FluidStatusBadge("CONFERMATO", tone = FluidTone.Success)
        isOverridden -> FluidStatusBadge("MODIFICATO", tone = FluidTone.Info)
        isOfficial -> FluidStatusBadge("IMPORT", tone = FluidTone.Success)
        block.isMulti -> FluidStatusBadge("BLOCCO ${block.allSlots.size}H", tone = FluidTone.Info)
        primary.confidence >= 0.75f -> FluidStatusBadge("STABILE", tone = FluidTone.Success)
        else -> FluidStatusBadge("DINAMICO", tone = FluidTone.Warning)
      }
    },
    animatePress = true,
    modifier = modifier,
  )
}

@Composable
private fun HistoryLessonRow(
  lesson: Lesson,
  modifier: Modifier = Modifier,
) {
  FluidListRow(
    title = lesson.subject,
    subtitle = lesson.topic?.takeIf(String::isNotBlank)
      ?: if (lesson.isSigned) "Argomento non inserito" else "Lezione non firmata",
    eyebrow = lesson.timeRangeLabel(),
    meta = listOfNotNull(
      lesson.teacher?.takeIf(String::isNotBlank),
      lesson.room?.takeIf(String::isNotBlank),
    ).joinToString(" / ").ifBlank { null },
    tone = if (lesson.isSigned || !lesson.topic.isNullOrBlank()) {
      FluidTone.Success
    } else {
      FluidTone.Neutral
    },
    leading = { Icon(Icons.Rounded.HistoryEdu, contentDescription = null) },
    badge = {
      FluidStatusBadge(
        label = if (lesson.isSigned) "FIRMATA" else "NON FIRMATA",
        tone = if (lesson.isSigned) FluidTone.Success else FluidTone.Neutral,
      )
    },
    modifier = modifier,
  )
}

@Composable
private fun WeekNavigator(
  weekStart: LocalDate,
  weekOffset: Int,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToday: () -> Unit,
) {
  // Lenti di vetro, non IconButton Material: sopra il fondale colorato della pagina il cerchio
  // grigio del ripple era l'unico controllo rimasto del vecchio vocabolario. Il fondale campionato
  // e' il canvas ambientale — il corpo conterrebbe queste stesse frecce.
  val emptyBackdrop = rememberEmptyGlassBackdrop()
  val backdrop = currentCanvasBackdrop() ?: emptyBackdrop
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidGlassIconButton(onClick = onPrevious, backdrop = backdrop) {
        Icon(
          imageVector = Icons.Rounded.ChevronLeft,
          contentDescription = "Settimana precedente",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
        text = "Settimana del ${weekStart.format(weekHeaderFormatter)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      FluidGlassIconButton(onClick = onNext, backdrop = backdrop, enabled = weekOffset < 0) {
        Icon(
          imageVector = Icons.Rounded.ChevronRight,
          contentDescription = "Settimana successiva",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
    if (weekOffset != 0) {
      FluidButton(
        text = "Oggi",
        onClick = onToday,
        modifier = Modifier.align(Alignment.End),
        style = FluidButtonStyle.Filled,
      )
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
    DayOfWeek.MONDAY -> "Lunedì"
    DayOfWeek.TUESDAY -> "Martedì"
    DayOfWeek.WEDNESDAY -> "Mercoledì"
    DayOfWeek.THURSDAY -> "Giovedì"
    DayOfWeek.FRIDAY -> "Venerdì"
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
  return "${start.format(timeFormatter)} - ${end.format(timeFormatter)}"
}

private fun TemplateSlot.timeRangeLabel(): String {
  val start = runCatching { LocalTime.parse(time) }.getOrNull() ?: return time
  val end = endTime
    ?.takeIf(String::isNotBlank)
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    ?: start.plusMinutes(durationMinutes.toLong())
  return "${start.format(timeFormatter)} - ${end.format(timeFormatter)}"
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}

internal data class SlotBlock(
  val primary: TemplateSlot,
  val extra: List<TemplateSlot> = emptyList(),
) {
  val allSlots: List<TemplateSlot> = listOf(primary) + extra
  val isMulti: Boolean get() = extra.isNotEmpty()
  val displaySubject: String = allSlots.map { it.subject }.distinct().joinToString(" / ")

  fun timeRangeLabel(): String {
    val start = runCatching { LocalTime.parse(primary.time) }.getOrNull() ?: return primary.time
    val lastSlot = extra.lastOrNull() ?: primary
    val end = lastSlot.endTime
      ?.takeIf(String::isNotBlank)
      ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
      ?: start.plusMinutes(allSlots.sumOf { it.durationMinutes }.toLong())
    return "${start.format(timeFormatter)} - ${end.format(timeFormatter)}"
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

private enum class SlotConfirmationStep {
  Review,
  Room,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotConfirmationContent(
  slot: TemplateSlot,
  enteringRoom: Boolean,
  onDismiss: () -> Unit,
  onContinue: () -> Unit,
  onBack: () -> Unit,
  onSave: (String?) -> Unit,
  onRemoveConfirm: () -> Unit,
) {
  var room by rememberSaveable(slot.slotFingerprint()) { mutableStateOf(slot.room.orEmpty()) }
  val step = if (enteringRoom) SlotConfirmationStep.Room else SlotConfirmationStep.Review

  Box {
    AnimatedContent(
      targetState = step,
      transitionSpec = {
        val entering = if (targetState == SlotConfirmationStep.Room) {
          slideInHorizontally(
            initialOffsetX = { width -> width / 3 },
            animationSpec = FluidMotion.standard(),
          ) + fadeIn(FluidMotion.fadeIn(140))
        } else {
          slideInHorizontally(
            initialOffsetX = { width -> -width / 3 },
            animationSpec = FluidMotion.standard(),
          ) + fadeIn(FluidMotion.fadeIn(140))
        }
        val leaving = if (targetState == SlotConfirmationStep.Room) {
          slideOutHorizontally(
            targetOffsetX = { width -> -width / 4 },
            animationSpec = FluidMotion.standard(),
          ) + fadeOut(FluidMotion.fadeOut(110))
        } else {
          slideOutHorizontally(
            targetOffsetX = { width -> width / 4 },
            animationSpec = FluidMotion.standard(),
          ) + fadeOut(FluidMotion.fadeOut(110))
        }
        entering togetherWith leaving using SizeTransform(clip = true)
      },
      label = "slot confirmation step",
    ) { currentStep ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 12.dp)
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        when (currentStep) {
          SlotConfirmationStep.Review -> {
            Text(
              text = if (slot.confirmed) "Slot confermato" else "Conferma questo slot",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.SemiBold,
            )
            FluidListRow(
              title = slot.subject,
              subtitle = slot.teacher ?: "Docente non specificato",
              eyebrow = slot.timeRangeLabel(),
              meta = slot.room?.takeIf(String::isNotBlank),
              tone = if (slot.confirmed) FluidTone.Success else FluidTone.Info,
              leading = { Icon(Icons.Rounded.School, contentDescription = null) },
              badge = {
                FluidStatusBadge(
                  label = if (slot.confirmed) "CONFERMATO" else "DA VERIFICARE",
                  tone = if (slot.confirmed) FluidTone.Success else FluidTone.Info,
                )
              },
            )
            Text(
              text = if (slot.confirmed) {
                "Puoi rimuovere la conferma e lasciare che l'orario venga rivalutato."
              } else {
                "Conferma materia, docente e fascia oraria; nel passaggio successivo puoi aggiungere l'aula."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (slot.confirmed) {
              FluidButton(
                text = "Rimuovi conferma",
                onClick = onRemoveConfirm,
                style = FluidButtonStyle.Tinted,
                fillWidth = true,
              )
            } else {
              FluidButton(
                text = "Continua",
                onClick = onContinue,
                style = FluidButtonStyle.Filled,
                fillWidth = true,
              )
            }
            FluidButton(
              text = "Annulla",
              onClick = onDismiss,
              style = FluidButtonStyle.Plain,
              fillWidth = true,
            )
          }

          SlotConfirmationStep.Room -> {
            Text(
              text = "Aula dello slot",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "L'aula è facoltativa: la conferma dell'orario resta valida anche senza inserirla.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FluidTextField(
              value = room,
              onValueChange = { room = it },
              modifier = Modifier.fillMaxWidth(),
              label = "Aula (opzionale)",
              placeholder = "es. P1 Aula 21",
              singleLine = true,
            )
            FluidButton(
              text = "Conferma${room.trim().takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
              onClick = { onSave(room.trim().ifBlank { null }) },
              style = FluidButtonStyle.Filled,
              fillWidth = true,
            )
            FluidButton(
              text = "Conferma senza aula",
              onClick = { onSave(null) },
              style = FluidButtonStyle.Tinted,
              fillWidth = true,
            )
            FluidButton(
              text = "Indietro",
              onClick = onBack,
              style = FluidButtonStyle.Plain,
              fillWidth = true,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSlotContent(
  slot: TemplateSlot,
  onDismiss: () -> Unit,
  onSave: (TemplateSlot) -> Unit,
  onReset: () -> Unit,
) {
  var subject by rememberSaveable(slot.slotFingerprint()) { mutableStateOf(slot.subject) }
  var teacher by rememberSaveable(slot.slotFingerprint()) { mutableStateOf(slot.teacher.orEmpty()) }
  var room by rememberSaveable(slot.slotFingerprint()) { mutableStateOf(slot.room.orEmpty()) }
  var startTime by rememberSaveable(slot.slotFingerprint()) { mutableStateOf(slot.time) }
  var endTime by rememberSaveable(slot.slotFingerprint()) { mutableStateOf(slot.endTime.orEmpty()) }
  var editingField by rememberSaveable { mutableStateOf<String?>(null) }

  Box {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Modifica slot orario", style = MaterialTheme.typography.headlineSmall)
      FluidTextField(
        value = subject,
        onValueChange = { subject = it },
        modifier = Modifier.fillMaxWidth(),
        label = "Materia",
        singleLine = true,
      )
      FluidTextField(
        value = teacher,
        onValueChange = { teacher = it },
        modifier = Modifier.fillMaxWidth(),
        label = "Docente (opzionale)",
        singleLine = true,
      )
      FluidTextField(
        value = room,
        onValueChange = { room = it },
        modifier = Modifier.fillMaxWidth(),
        label = "Aula (opzionale)",
        singleLine = true,
      )
      FluidListRow(
        title = "Ora inizio",
        subtitle = startTime.ifBlank { "Non impostata" },
        eyebrow = "Orario",
        tone = FluidTone.Info,
        onClick = { editingField = "start" },
        badge = { FluidStatusBadge("MODIFICA", tone = FluidTone.Info) },
        animatePress = true,
      )
      FluidListRow(
        title = "Ora fine",
        subtitle = endTime.ifBlank { "Non impostata" },
        eyebrow = "Orario",
        tone = FluidTone.Info,
        onClick = { editingField = "end" },
        badge = { FluidStatusBadge("MODIFICA", tone = FluidTone.Info) },
        animatePress = true,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FluidButton(
          text = "Ripristina",
          onClick = { onReset(); onDismiss() },
          style = FluidButtonStyle.Plain,
        )
        FluidButton(
          text = "Annulla",
          onClick = onDismiss,
          style = FluidButtonStyle.Plain,
        )
        FluidButton(
          text = "Salva",
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
          style = FluidButtonStyle.Filled,
          enabled = subject.isNotBlank(),
        )
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
    FluidAlert(
      onDismissRequest = { editingField = null },
      title = if (editingField == "start") "Ora inizio" else "Ora fine",
      actions = listOf(
        FluidAlertAction("Annulla", { editingField = null }),
        FluidAlertAction("Imposta", {
            val formatted = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
            if (editingField == "start") startTime = formatted else endTime = formatted
            editingField = null
          }, FluidAlertAction.Emphasis.Preferred),
      ),
      content = { TimePicker(state = timePickerState) },
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
