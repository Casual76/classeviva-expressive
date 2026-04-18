package dev.antigravity.classevivaexpressive.feature.grades

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import dev.antigravity.classevivaexpressive.core.domain.util.parseDecimal
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

private data class GradesContentState(
  val grades: List<Grade>,
  val periods: List<Period>,
  val seenGradeIds: Set<String>,
  val subjectGoals: List<SubjectGoal>,
  val simulation: GradeSimulationSummary,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GradesRoute(
  initialGradeId: String? = null,
  modifier: Modifier = Modifier,
  viewModel: GradesViewModel = hiltViewModel(),
  sharedTransitionScope: SharedTransitionScope? = null,
  animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf(TAB_RECENT) }
  var showSimulationDialog by rememberSaveable { mutableStateOf(false) }
  var goalDialogSubject by rememberSaveable { mutableStateOf<String?>(null) }
  var detailSubject by rememberSaveable { mutableStateOf<String?>(null) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val effectivePeriodCode = remember(state.periods, state.selectedPeriodCode, state.grades) {
    if (state.selectedPeriodCode != null) state.selectedPeriodCode else {
      val latestGrade = state.grades.maxByOrNull { it.date }
      latestGrade?.periodCode ?: selectCurrentPeriodCode(state.periods, null)
    }
  }
  
  val filteredGrades = remember(state.grades, state.periods, effectivePeriodCode) {
    filterGradesByPeriod(state.grades, state.periods, effectivePeriodCode)
  }
  
  val overallAverage = remember(state.grades) {
    calculateOverallAverage(state.grades)
  }
  
  val periodAverage = remember(filteredGrades) {
    calculateOverallAverage(filteredGrades)
  }
  
  val subjectRows = remember(filteredGrades, state.subjectGoals, effectivePeriodCode) {
    buildSubjectRows(filteredGrades, state.subjectGoals, effectivePeriodCode)
  }
  
  val riskSubjectsCount = remember(subjectRows) {
    subjectRows.count { it.average != null && it.average < 6.0 }
  }

  val selectedGrade = remember(state.grades, state.selectedGradeId) {
    state.grades.firstOrNull { it.id == state.selectedGradeId }
  }

  val chartPoints = remember(filteredGrades) {
    filteredGrades.sortedBy { it.date }.mapNotNull { it.numericValue?.toFloat() }
  }

  LaunchedEffect(initialGradeId, state.grades.size) {
    if (!initialGradeId.isNullOrBlank() && state.grades.any { it.id == initialGradeId } && state.selectedGradeId != initialGradeId) {
      viewModel.openGrade(initialGradeId)
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Voti",
        subtitle = "Riepilogo medie, situazione per materia e ultimi voti registrati.",
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
          IconButton(onClick = { showSimulationDialog = true }) {
            Icon(Icons.Rounded.Add, contentDescription = "Simulazione")
          }
          if (state.simulation.grades.isNotEmpty()) {
            IconButton(onClick = viewModel::clearSimulation) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = "Pulisci")
            }
          }
        },
      )
    }
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.padding(paddingValues).fillMaxSize(),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().animateContentSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().animateItem(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            MetricTile(
              label = "Annuale",
              value = overallAverage?.format2() ?: "--",
              detail = "Media globale anno",
              tone = gradeTone(overallAverage),
              modifier = Modifier.weight(1f),
            )
            MetricTile(
              label = "Periodo",
              value = periodAverage?.format2() ?: "--",
              detail = effectivePeriodLabel(state.periods, effectivePeriodCode),
              tone = gradeTone(periodAverage),
              modifier = Modifier.weight(1f),
            )
            MetricTile(
              label = "A rischio",
              value = riskSubjectsCount.toString(),
              detail = "Materie sotto il 6.0",
              tone = if (riskSubjectsCount > 0) ExpressiveTone.Danger else ExpressiveTone.Success,
              modifier = Modifier.weight(1f),
            )
          }
        }
        
        if (chartPoints.isNotEmpty()) {
          item {
            ExpressiveEditorialCard(modifier = Modifier.animateItem()) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(
                    text = "ANDAMENTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  val latestValue = chartPoints.lastOrNull()
                  if (latestValue != null) {
                      Text(
                        text = "Ultimo: ${latestValue.toDouble().format1()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                  }
              }
              Box(modifier = Modifier.fillMaxWidth()) {
                  ExpressiveMiniChart(
                    points = chartPoints.takeLast(15),
                    color = MaterialTheme.colorScheme.primary,
                    threshold = 6f,
                    modifier = Modifier.height(110.dp)
                  )
                  // Min/Max axis indicators
                  Column(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                      Text("10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                      Spacer(Modifier.height(70.dp))
                      Text("2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                  }
              }
            }
          }
        }

        if (state.periods.isNotEmpty()) {
          item {
            PeriodSelector(
              modifier = Modifier.animateItem(),
              periods = state.periods,
              selectedCode = effectivePeriodCode,
              onSelect = viewModel::selectPeriod,
            )
          }
        }
        
        item {
          ExpressivePillTabs(
            modifier = Modifier.animateItem(),
            options = listOf(TAB_RECENT, TAB_SUBJECTS),
            selected = selectedTab,
            onSelect = { selectedTab = it },
          )
        }
        
        val periodUnseen = filteredGrades.filterNot { state.seenGradeIds.contains(it.id) }
        if (periodUnseen.isNotEmpty()) {
          item {
            QuickAction(
              label = "Segna tutto come gia visto",
              onClick = { viewModel.markGradesSeen(periodUnseen.map(Grade::id)) },
            )
          }
        }

        when (selectedTab) {
          TAB_RECENT -> {
            if (filteredGrades.isEmpty()) {
              item {
                EmptyState(
                  modifier = Modifier.animateItem(),
                  title = "Nessun voto in questo periodo",
                  detail = "Seleziona un altro periodo oppure attendi la sincronizzazione dei dati.",
                )
              }
            } else {
              items(filteredGrades.sortedByDescending { it.date }, key = { it.id }) { grade ->
                val unseen = !state.seenGradeIds.contains(grade.id)
                val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                  with(sharedTransitionScope) {
                    Modifier.sharedElement(
                      rememberSharedContentState(key = "grade-${grade.id}"),
                      animatedVisibilityScope = animatedVisibilityScope
                    )
                  }
                } else Modifier

                RegisterListRow(
                  modifier = Modifier.animateItem().then(sharedModifier),
                  title = grade.subject,
                  subtitle = grade.type.ifBlank { "Valutazione" },
                  eyebrow = grade.date.toReadableDate(),
                  meta = listOfNotNull(
                      grade.description ?: grade.notes,
                      grade.teacher
                  ).joinToString(" / ").ifBlank { null },
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
                  modifier = Modifier.animateItem(),
                  title = "Mancano voti numerici",
                  detail = "Le medie per materia vengono calcolate solo in presenza di valutazioni con valore decimale.",
                )
              }
            } else {
              items(subjectRows, key = { it.subject }) { row ->
                RegisterListRow(
                  modifier = Modifier.animateItem(),
                  title = row.subject,
                  subtitle = row.detail,
                  eyebrow = if (row.average != null && row.average < 6.0) "Materia a rischio" else "Per materia",
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
                  onClick = { detailSubject = row.subject },
                )
              }
            }
          }
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

  detailSubject?.let { subject ->
    SubjectDetailSheet(
      subject = subject,
      grades = filteredGrades.filter { it.subject == subject },
      onDismiss = { detailSubject = null },
      onOpenGrade = { viewModel.openGrade(it) },
      onSetGoal = { goalDialogSubject = subject }
    )
  }

  goalDialogSubject?.let { subject ->
    val selectedGoal = state.subjectGoals.firstOrNull { it.subject == subject && it.periodCode == effectivePeriodCode }
      ?: state.subjectGoals.firstOrNull { it.subject == subject && it.periodCode == null }
      
    GoalSheet(
      subject = subject,
      initialValue = selectedGoal?.targetAverage ?: 6.0,
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

  if (selectedGrade != null) {
    ModalBottomSheet(onDismissRequest = viewModel::dismissGrade) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = selectedGrade.subject, style = MaterialTheme.typography.headlineSmall)
        RegisterListRow(
          title = selectedGrade.valueLabel,
          subtitle = selectedGrade.type.ifBlank { "Valutazione" },
          eyebrow = selectedGrade.date.toReadableDate(),
          meta = listOfNotNull(selectedGrade.description, selectedGrade.notes, selectedGrade.teacher).joinToString(" / "),
          tone = gradeTone(selectedGrade.numericValue),
          badge = {
            GradePill(value = selectedGrade.valueLabel, numericValue = selectedGrade.numericValue)
          }
        )
        Button(onClick = viewModel::dismissGrade, modifier = Modifier.fillMaxWidth()) {
          Text("Chiudi")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDetailSheet(
  subject: String,
  grades: List<Grade>,
  onDismiss: () -> Unit,
  onOpenGrade: (String) -> Unit,
  onSetGoal: () -> Unit
) {
  val writtenAvg = calculateSubjectAverage(grades.filter { it.type.contains("scritto", true) || it.type.contains("grafico", true) })
  val oralAvg = calculateSubjectAverage(grades.filter { it.type.contains("orale", true) })
  val practicalAvg = calculateSubjectAverage(grades.filter { it.type.contains("pratico", true) })

  ModalBottomSheet(onDismissRequest = onDismiss) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text(text = subject, style = MaterialTheme.typography.headlineSmall)
          IconButton(onClick = onSetGoal) {
              Icon(Icons.Rounded.Settings, contentDescription = "Obiettivo")
          }
        }
      }
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MetricTile(label = "Scritto", value = writtenAvg?.format1() ?: "--", detail = "Media", modifier = Modifier.weight(1f), tone = gradeTone(writtenAvg))
          MetricTile(label = "Orale", value = oralAvg?.format1() ?: "--", detail = "Media", modifier = Modifier.weight(1f), tone = gradeTone(oralAvg))
          MetricTile(label = "Pratico", value = practicalAvg?.format1() ?: "--", detail = "Media", modifier = Modifier.weight(1f), tone = gradeTone(practicalAvg))
        }
      }
      item { ExpressiveAccentLabel("Tutti i voti") }
      items(grades.sortedByDescending { it.date }) { grade ->
          RegisterListRow(
            title = grade.valueLabel,
            subtitle = grade.type,
            eyebrow = grade.date.toReadableDate(),
            meta = grade.description ?: grade.notes,
            tone = gradeTone(grade.numericValue),
            onClick = { onOpenGrade(grade.id) }
          )
      }
      item { Spacer(Modifier.height(32.dp)) }
    }
  }
}

@Composable
private fun PeriodSelector(
  periods: List<Period>,
  selectedCode: String?,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
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
  initialValue: Double,
  periodLabel: String,
  onDismiss: () -> Unit,
  onClear: () -> Unit,
  onSave: (Double) -> Unit,
) {
  var valueText by rememberSaveable(subject, initialValue) {
    mutableStateOf(initialValue.format1())
  }
  val numeric = valueText.parseDecimal()

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(text = "Imposta Obiettivo / $subject", style = MaterialTheme.typography.titleLarge)
      Text(text = "Periodo: $periodLabel", style = MaterialTheme.typography.bodyMedium)
      OutlinedTextField(
        value = valueText,
        onValueChange = { valueText = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Media desiderata") },
        singleLine = true,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClear) { Text("Rimuovi") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Annulla") }
        Button(onClick = { onSave(numeric ?: 6.0) }, enabled = numeric != null) { Text("Salva") }
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
  val numeric = valueText.parseDecimal()

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(text = "Voto Simulato", style = MaterialTheme.typography.titleLarge)
      OutlinedTextField(value = subject, onValueChange = { subject = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Materia") })
      OutlinedTextField(value = valueText, onValueChange = { valueText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Valore") })
      OutlinedTextField(value = type, onValueChange = { type = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tipologia") })
      OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nota") })
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("Annulla") }
        Button(onClick = { onSave(subject, numeric ?: 0.0, type, note) }, enabled = subject.isNotBlank() && numeric != null) { Text("Aggiungi") }
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
    required > 10.0 -> "Lontano dal target"
    required <= 0.0 -> "Target sicuro"
    currentAverage != null && currentAverage >= targetAverage -> "Soglia sicura: ${required.coerceAtLeast(1.0).format1()}"
    else -> "Serve almeno ${required.coerceAtLeast(1.0).format1()}"
  }
}

private fun buildSubjectRows(
  grades: List<Grade>,
  subjectGoals: List<SubjectGoal>,
  periodCode: String?,
): List<SubjectRow> {
  return grades.groupBy { it.subject }.map { (subject, items) ->
    val numeric = items.filter { it.numericValue != null }
    val average = calculateSubjectAverage(numeric)
    val goal = subjectGoals.firstOrNull { it.subject == subject && it.periodCode == periodCode }
      ?: subjectGoals.firstOrNull { it.subject == subject && it.periodCode == null }
    val goalMessage = goal?.let { calculateRequiredGradeMessage(numeric, it.targetAverage) }
    SubjectRow(
      subject = subject,
      average = average,
      detail = buildString {
        append("${items.size} voti")
        goal?.let { append(" / target ${it.targetAverage.format1()}") }
      },
      meta = listOfNotNull(
        numeric.takeLast(2).mapNotNull { it.numericValue?.format1() }.joinToString(" / ").ifBlank { null },
        goalMessage,
      ).joinToString(" / ").ifBlank { "Nessun trend" },
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
    ?: "Corrente"
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
