package dev.antigravity.classevivaexpressive.feature.grades

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEditorialCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveMiniChart
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.GradePill
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.QuickAction
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.designsystem.theme.gradeTone
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.SimulatedGrade
import dev.antigravity.classevivaexpressive.core.domain.model.SimulationRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectGoal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAB_RECENT = "Ultimi"
private const val TAB_SUBJECTS = "Per materia"
private const val TAB_TREND = "Andamento"
private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class GradesUiState(
  val grades: List<Grade> = emptyList(),
  val periods: List<Period> = emptyList(),
  val seenGradeIds: Set<String> = emptySet(),
  val subjectGoals: List<SubjectGoal> = emptyList(),
  val simulation: GradeSimulationSummary = GradeSimulationSummary(),
  val selectedPeriodCode: String? = null,
  val selectedGradeId: String? = null,
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class GradesViewModel @Inject constructor(
  private val gradesRepository: GradesRepository,
  private val simulationRepository: SimulationRepository,
) : ViewModel() {
  private val selectedPeriodCode = MutableStateFlow<String?>(null)
  private val selectedGradeId = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)

  private val contentState = combine(
    gradesRepository.observeGrades(),
    gradesRepository.observePeriods(),
    gradesRepository.observeSeenGradeStates(),
    gradesRepository.observeSubjectGoals(),
    simulationRepository.observeSimulation(),
  ) { grades, periods, seenStates, subjectGoals, simulation ->
    GradesContentState(
      grades = grades,
      periods = periods,
      seenGradeIds = seenStates.map { it.gradeId }.toSet(),
      subjectGoals = subjectGoals,
      simulation = simulation,
    )
  }

  val state = combine(
    contentState,
    selectedPeriodCode,
    selectedGradeId,
    isRefreshing,
  ) { content, periodCode, gradeId, refreshing ->
    GradesUiState(
      grades = content.grades,
      periods = content.periods,
      seenGradeIds = content.seenGradeIds,
      subjectGoals = content.subjectGoals,
      simulation = content.simulation,
      selectedPeriodCode = periodCode,
      selectedGradeId = gradeId,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  fun selectPeriod(periodCode: String?) {
    selectedPeriodCode.value = periodCode
  }

  fun openGrade(gradeId: String) {
    selectedGradeId.value = gradeId
    viewModelScope.launch { gradesRepository.markGradeSeen(gradeId) }
  }

  fun markGradesSeen(gradeIds: List<String>) {
    val distinctIds = gradeIds.distinct().filter(String::isNotBlank)
    if (distinctIds.isEmpty()) return
    viewModelScope.launch {
      distinctIds.forEach { gradeId ->
        gradesRepository.markGradeSeen(gradeId)
      }
    }
  }

  fun dismissGrade() {
    selectedGradeId.value = null
  }

  fun saveGoal(subject: String, periodCode: String?, targetAverage: Double) {
    viewModelScope.launch { gradesRepository.saveSubjectGoal(subject, periodCode, targetAverage) }
  }

  fun clearGoal(subject: String, periodCode: String?) {
    viewModelScope.launch { gradesRepository.removeSubjectGoal(subject, periodCode) }
  }

  fun addSimulatedGrade(subject: String, value: Double, type: String, note: String) {
    viewModelScope.launch {
      simulationRepository.addSimulatedGrade(
        SimulatedGrade(
          id = "sim-${UUID.randomUUID()}",
          subject = subject.trim(),
          value = value,
          type = type.trim(),
          date = LocalDate.now().toString(),
          note = note.takeIf { it.isNotBlank() },
        ),
      )
    }
  }

  fun removeSimulatedGrade(id: String) {
    viewModelScope.launch { simulationRepository.removeSimulatedGrade(id) }
  }

  fun clearSimulation() {
    viewModelScope.launch { simulationRepository.clearSimulation() }
  }

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      gradesRepository.refreshGrades(force = force)
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesRoute(
  initialGradeId: String? = null,
  modifier: Modifier = Modifier,
  viewModel: GradesViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf(TAB_RECENT) }
  var showSimulationDialog by rememberSaveable { mutableStateOf(false) }
  var goalDialogSubject by rememberSaveable { mutableStateOf<String?>(null) }

  val effectivePeriodCode = remember(state.periods, state.selectedPeriodCode) {
    selectCurrentPeriodCode(state.periods, state.selectedPeriodCode)
  }
  val filteredGrades = remember(state.grades, state.periods, effectivePeriodCode) {
    filterGradesByPeriod(state.grades, state.periods, effectivePeriodCode)
  }
  val unseenGrades = remember(filteredGrades, state.seenGradeIds) {
    filteredGrades.filterNot { state.seenGradeIds.contains(it.id) }
  }
  val average = remember(filteredGrades) {
    filteredGrades.mapNotNull { it.numericValue }.takeIf { it.isNotEmpty() }?.average()
  }
  val subjectRows = remember(filteredGrades, state.subjectGoals, effectivePeriodCode) {
    buildSubjectRows(filteredGrades, state.subjectGoals, effectivePeriodCode)
  }
  val selectedGrade = remember(state.grades, state.selectedGradeId) {
    state.grades.firstOrNull { it.id == state.selectedGradeId }
  }
  val selectedGoal = remember(goalDialogSubject, state.subjectGoals, effectivePeriodCode) {
    val subject = goalDialogSubject ?: return@remember null
    state.subjectGoals.firstOrNull { it.subject == subject && it.periodCode == effectivePeriodCode }
      ?: state.subjectGoals.firstOrNull { it.subject == subject && it.periodCode == null }
  }

  LaunchedEffect(initialGradeId, state.grades.size) {
    if (!initialGradeId.isNullOrBlank() && state.grades.any { it.id == initialGradeId } && state.selectedGradeId != initialGradeId) {
      viewModel.openGrade(initialGradeId)
    }
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
        title = "Voti",
        subtitle = "Periodo corrente, dettagli dei voti, andamento e obiettivi media per materia.",
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
          IconButton(onClick = { showSimulationDialog = true }) {
            Icon(Icons.Rounded.Add, contentDescription = "Aggiungi simulazione")
          }
          if (state.simulation.grades.isNotEmpty()) {
            IconButton(onClick = viewModel::clearSimulation) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = "Svuota simulazione")
            }
          }
        },
      )
    }
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricTile(
          label = "Media",
          value = average?.format2() ?: "--",
          detail = effectivePeriodLabel(state.periods, effectivePeriodCode),
          tone = gradeTone(average),
          modifier = Modifier.weight(1f),
        )
        MetricTile(
          label = "Non visti",
          value = unseenGrades.size.toString(),
          detail = "Voti ancora da aprire",
          tone = if (unseenGrades.isNotEmpty()) ExpressiveTone.Primary else ExpressiveTone.Neutral,
          modifier = Modifier.weight(1f),
        )
        MetricTile(
          label = "Materie",
          value = subjectRows.size.toString(),
          detail = "Discipline nel periodo attivo",
          tone = ExpressiveTone.Info,
          modifier = Modifier.weight(1f),
        )
      }
    }
    if (state.periods.isNotEmpty()) {
      item {
        PeriodSelector(
          periods = state.periods,
          selectedCode = effectivePeriodCode,
          onSelect = viewModel::selectPeriod,
        )
      }
    }
    item {
      ExpressivePillTabs(
        options = listOf(TAB_RECENT, TAB_SUBJECTS, TAB_TREND),
        selected = selectedTab,
        onSelect = { selectedTab = it },
      )
    }
    if (unseenGrades.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickAction(
            label = "Segna tutto come gia visto",
            onClick = { viewModel.markGradesSeen(unseenGrades.map(Grade::id)) },
          )
        }
      }
    }

    when (selectedTab) {
      TAB_RECENT -> {
        if (filteredGrades.isEmpty()) {
          item {
            EmptyState(
              title = "Nessun voto disponibile",
              detail = "I voti del periodo selezionato appariranno qui appena la sincronizzazione li rende disponibili.",
            )
          }
        } else {
          items(filteredGrades.sortedByDescending { it.date }, key = { it.id }) { grade ->
            val unseen = !state.seenGradeIds.contains(grade.id)
            RegisterListRow(
              title = grade.subject,
              subtitle = listOfNotNull(grade.type.takeIf { it.isNotBlank() }, grade.description ?: grade.notes)
                .joinToString(" / ")
                .ifBlank { "Valutazione registrata" },
              eyebrow = grade.date.toReadableDate(),
              meta = listOfNotNull(grade.teacher, grade.period).joinToString(" / ").ifBlank { null },
              tone = gradeTone(grade.numericValue),
              badge = {
                if (unseen) {
                  StatusBadge(label = "NUOVO", tone = ExpressiveTone.Primary)
                }
                GradePill(value = grade.valueLabel, numericValue = grade.numericValue)
              },
              onClick = { viewModel.openGrade(grade.id) },
            )
          }
        }
      }

      TAB_SUBJECTS -> {
        if (subjectRows.isEmpty()) {
          item {
            EmptyState(
              title = "Nessun riepilogo disponibile",
              detail = "Appena arrivano voti numerici sufficienti, qui trovi andamento e obiettivo per materia.",
            )
          }
        } else {
          items(subjectRows, key = { it.subject }) { row ->
            RegisterListRow(
              title = row.subject,
              subtitle = row.detail,
              eyebrow = "Per materia",
              meta = row.meta,
              tone = gradeTone(row.average),
              badge = {
                row.target?.let {
                  StatusBadge(
                    label = "TARGET ${it.format1()}",
                    tone = ExpressiveTone.Primary,
                  )
                }
                GradePill(
                  value = row.average?.format2() ?: "--",
                  numericValue = row.average,
                )
              },
              onClick = { goalDialogSubject = row.subject },
            )
          }
        }
      }

      TAB_TREND -> {
        val numericGrades = filteredGrades.sortedBy { it.date }.mapNotNull { grade ->
          grade.numericValue?.toFloat()?.let { TrendGrade(label = "${grade.subject.take(3)} ${grade.date.takeLast(2)}", value = it, raw = grade) }
        }
        if (numericGrades.isEmpty()) {
          item {
            EmptyState(
              title = "Andamento non disponibile",
              detail = "Servono voti numerici nel periodo selezionato per costruire il trend.",
            )
          }
        } else {
          item {
            ExpressiveEditorialCard {
              Text(text = "Andamento del periodo")
              ExpressiveMiniChart(
                points = numericGrades.takeLast(10).map { it.value },
                color = MaterialTheme.colorScheme.primary,
                threshold = 6f,
              )
            }
          }
          items(numericGrades.takeLast(8).reversed(), key = { it.raw.id }) { point ->
            RegisterListRow(
              title = point.raw.subject,
              subtitle = point.raw.type.ifBlank { "Valutazione" },
              eyebrow = point.raw.date.toReadableDate(),
              meta = point.raw.description ?: point.raw.notes,
              tone = gradeTone(point.raw.numericValue),
              badge = {
                GradePill(
                  value = point.raw.valueLabel,
                  numericValue = point.raw.numericValue,
                )
              },
              onClick = { viewModel.openGrade(point.raw.id) },
            )
          }
        }
      }
    }

    if (state.simulation.grades.isNotEmpty()) {
      item { ExpressiveAccentLabel("Simulazione") }
      item {
        Text(
          text = "I voti simulati non toccano i dati reali e servono solo a capire come potrebbe muoversi la media.",
        )
      }
      items(state.simulation.grades, key = { it.id }) { grade ->
        RegisterListRow(
          title = "${grade.subject} / ${grade.type}",
          subtitle = grade.note ?: "Voto simulato aggiunto il ${grade.date.toReadableDate()}",
          eyebrow = "Simulato",
          meta = "Media simulata ${state.simulation.simulatedAverage?.format2() ?: "--"}",
          tone = gradeTone(grade.value),
          badge = {
            GradePill(value = grade.value.format1(), numericValue = grade.value)
          },
          onClick = { viewModel.removeSimulatedGrade(grade.id) },
        )
      }
    }
    }
  }

  if (showSimulationDialog) {
    AddSimulationSheet(
      onDismiss = { showSimulationDialog = false },
      onSave = { subject, value, type, note ->
        viewModel.addSimulatedGrade(subject, value, type, note)
        showSimulationDialog = false
      },
    )
  }

  if (selectedGrade != null) {
    ModalBottomSheet(
      onDismissRequest = viewModel::dismissGrade,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Text(
            text = selectedGrade.subject,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        item {
          RegisterListRow(
            title = selectedGrade.valueLabel,
            subtitle = selectedGrade.type.ifBlank { "Valutazione" },
            eyebrow = selectedGrade.date.toReadableDate(),
            meta = listOfNotNull(selectedGrade.teacher, selectedGrade.period, selectedGrade.description, selectedGrade.notes)
              .joinToString(" / ")
              .ifBlank { null },
            tone = gradeTone(selectedGrade.numericValue),
            badge = {
              GradePill(
                value = selectedGrade.valueLabel,
                numericValue = selectedGrade.numericValue,
              )
              StatusBadge(
                label = if (state.seenGradeIds.contains(selectedGrade.id)) "VISTO" else "NUOVO",
                tone = if (state.seenGradeIds.contains(selectedGrade.id)) ExpressiveTone.Neutral else ExpressiveTone.Primary,
              )
            },
          )
        }
        item {
          Button(
            onClick = viewModel::dismissGrade,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Chiudi")
          }
        }
      }
    }
  }

  goalDialogSubject?.let { subject ->
    GoalSheet(
      subject = subject,
      initialValue = selectedGoal?.targetAverage,
      periodLabel = effectivePeriodLabel(state.periods, effectivePeriodCode),
      onDismiss = { goalDialogSubject = null },
      onClear = {
        viewModel.clearGoal(subject, effectivePeriodCode)
        goalDialogSubject = null
      },
      onSave = { target ->
        viewModel.saveGoal(subject, effectivePeriodCode, target)
        goalDialogSubject = null
      },
    )
  }
}

@Composable
private fun PeriodSelector(
  periods: List<Period>,
  selectedCode: String?,
  onSelect: (String?) -> Unit,
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    periods.sortedBy { it.order }.forEach { period ->
      FilterChip(
        selected = period.code == selectedCode,
        onClick = { onSelect(period.code) },
        label = { Text(period.label.ifBlank { period.description }) },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(
  subject: String,
  initialValue: Double?,
  periodLabel: String,
  onDismiss: () -> Unit,
  onClear: () -> Unit,
  onSave: (Double) -> Unit,
) {
  var valueText by rememberSaveable(subject, initialValue) {
    mutableStateOf(initialValue?.format1().orEmpty())
  }
  val numeric = valueText.replace(",", ".").toDoubleOrNull()

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
          text = "Obiettivo media / $subject",
          style = MaterialTheme.typography.headlineSmall,
        )
      }
      item { Text("Periodo attivo: $periodLabel") }
      item {
        OutlinedTextField(
          value = valueText,
          onValueChange = { valueText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Media obiettivo") },
          supportingText = { Text("Esempio: 7.5") },
          singleLine = true,
        )
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (initialValue != null) {
            TextButton(onClick = onClear) {
              Text("Rimuovi")
            }
          }
          TextButton(onClick = onDismiss) {
            Text("Annulla")
          }
          Button(
            onClick = { onSave(numeric ?: 0.0) },
            enabled = numeric != null,
          ) {
            Text("Salva")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSimulationSheet(
  onDismiss: () -> Unit,
  onSave: (subject: String, value: Double, type: String, note: String) -> Unit,
) {
  var subject by rememberSaveable { mutableStateOf("") }
  var valueText by rememberSaveable { mutableStateOf("") }
  var type by rememberSaveable { mutableStateOf("Interrogazione") }
  var note by rememberSaveable { mutableStateOf("") }
  val numeric = valueText.replace(",", ".").toDoubleOrNull()

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
          text = "Aggiungi voto simulato",
          style = MaterialTheme.typography.headlineSmall,
        )
      }
      item {
        OutlinedTextField(
          value = subject,
          onValueChange = { subject = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Materia") },
          singleLine = true,
        )
      }
      item {
        OutlinedTextField(
          value = valueText,
          onValueChange = { valueText = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Valore") },
          supportingText = { Text("Esempio: 7.5") },
          singleLine = true,
        )
      }
      item {
        OutlinedTextField(
          value = type,
          onValueChange = { type = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Tipologia") },
          singleLine = true,
        )
      }
      item {
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Nota") },
          minLines = 2,
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
            onClick = { onSave(subject, numeric ?: 0.0, type, note) },
            enabled = subject.isNotBlank() && numeric != null,
          ) {
            Text("Salva")
          }
        }
      }
    }
  }
}

private data class SubjectRow(
  val subject: String,
  val average: Double?,
  val detail: String,
  val meta: String,
  val target: Double?,
)

private data class TrendGrade(
  val label: String,
  val value: Float,
  val raw: Grade,
)

private data class GradesContentState(
  val grades: List<Grade>,
  val periods: List<Period>,
  val seenGradeIds: Set<String>,
  val subjectGoals: List<SubjectGoal>,
  val simulation: GradeSimulationSummary,
)

internal fun selectCurrentPeriodCode(
  periods: List<Period>,
  selectedCode: String?,
  today: LocalDate = LocalDate.now(),
): String? {
  if (!selectedCode.isNullOrBlank() && periods.any { it.code == selectedCode }) return selectedCode
  return periods
    .sortedBy { it.order }
    .firstOrNull { period ->
      val start = period.startDate.toLocalDateOrNull()
      val end = period.endDate.toLocalDateOrNull()
      start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
    }?.code
    ?: periods
      .sortedBy { it.order }
      .lastOrNull { period ->
        val start = period.startDate.toLocalDateOrNull()
        start != null && !today.isBefore(start)
      }?.code
    ?: periods.sortedBy { it.order }.firstOrNull()?.code
}

internal fun filterGradesByPeriod(
  grades: List<Grade>,
  periods: List<Period>,
  periodCode: String?,
): List<Grade> {
  if (periodCode.isNullOrBlank()) return grades
  val period = periods.firstOrNull { it.code == periodCode } ?: return grades
  val start = period.startDate.toLocalDateOrNull()
  val end = period.endDate.toLocalDateOrNull()
  return grades.filter { grade ->
    when {
      grade.periodCode == periodCode -> true
      !grade.period.isNullOrBlank() && (
        grade.period.equals(period.label, ignoreCase = true) ||
          grade.period.equals(period.description, ignoreCase = true)
        ) -> true
      start != null && end != null -> {
        val date = grade.date.toLocalDateOrNull()
        date != null && !date.isBefore(start) && !date.isAfter(end)
      }
      else -> false
    }
  }
}

internal fun calculateRequiredGradeMessage(
  grades: List<Grade>,
  targetAverage: Double,
): String {
  val numericGrades = grades.mapNotNull { grade ->
    grade.numericValue?.let { value -> value to (grade.weight ?: 1.0) }
  }
  val currentAverage = numericGrades.takeIf { it.isNotEmpty() }?.let { values ->
    values.sumOf { it.first * it.second } / values.sumOf { it.second }
  }
  val currentWeight = numericGrades.sumOf { it.second }
  val weightedSum = numericGrades.sumOf { it.first * it.second }
  val required = if (currentWeight == 0.0) targetAverage else (targetAverage * (currentWeight + 1.0)) - weightedSum

  return when {
    required > 10.0 -> "Media non raggiungibile con un singolo voto"
    required <= 0.0 -> "non ti preoccupare!"
    currentAverage != null && currentAverage >= targetAverage -> "non prendere meno di ${required.coerceAtLeast(1.0).format1()}"
    else -> "devi prendere almeno ${required.coerceAtLeast(1.0).format1()}"
  }
}

private fun buildSubjectRows(
  grades: List<Grade>,
  subjectGoals: List<SubjectGoal>,
  periodCode: String?,
): List<SubjectRow> {
  return grades.groupBy { it.subject }.map { (subject, items) ->
    val numeric = items.filter { it.numericValue != null }
    val average = numeric
      .takeIf { it.isNotEmpty() }
      ?.let { values ->
        val totalWeight = values.sumOf { it.weight ?: 1.0 }
        values.sumOf { (it.numericValue ?: 0.0) * (it.weight ?: 1.0) } / totalWeight
      }
    val goal = subjectGoals.firstOrNull { it.subject == subject && it.periodCode == periodCode }
      ?: subjectGoals.firstOrNull { it.subject == subject && it.periodCode == null }
    val goalMessage = goal?.let { calculateRequiredGradeMessage(numeric, it.targetAverage) }
    SubjectRow(
      subject = subject,
      average = average,
      detail = buildString {
        append("${items.size} valutazioni")
        goal?.let { append(" / obiettivo ${it.targetAverage.format1()}") }
      },
      meta = listOfNotNull(
        numeric.takeLast(3).mapNotNull { it.numericValue?.format1() }.joinToString(" / ").ifBlank { null },
        goalMessage,
      ).joinToString(" / ").ifBlank { "Nessun trend numerico disponibile" },
      target = goal?.targetAverage,
    )
  }.sortedBy { it.subject }
}

internal fun calculateSubjectAverage(grades: List<Grade>): Double? {
  val numeric = grades.filter { it.numericValue != null }
  if (numeric.isEmpty()) return null
  val totalWeight = numeric.sumOf { it.weight ?: 1.0 }
  return numeric.sumOf { (it.numericValue ?: 0.0) * (it.weight ?: 1.0) } / totalWeight
}

internal fun calculateOverallAverage(grades: List<Grade>): Double? {
  val bySubject = grades.groupBy { it.subject }
  val subjectAverages = bySubject.values.mapNotNull { calculateSubjectAverage(it) }
  if (subjectAverages.isEmpty()) return null
  return subjectAverages.average()
}

private fun effectivePeriodLabel(periods: List<Period>, periodCode: String?): String {
  return periods.firstOrNull { it.code == periodCode }?.label
    ?: periods.firstOrNull { it.code == periodCode }?.description
    ?: "Periodo corrente"
}

private fun String.toReadableDate(): String {
  val parsed = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
  return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale))
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}

private fun Double.format1(): String = String.format(italianLocale, "%.1f", this)

private fun Double.format2(): String = String.format(italianLocale, "%.2f", this)
