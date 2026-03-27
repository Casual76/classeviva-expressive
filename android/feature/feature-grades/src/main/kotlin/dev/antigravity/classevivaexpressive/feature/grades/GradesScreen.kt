package dev.antigravity.classevivaexpressive.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveColorTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEditorialCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveMiniChart
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveScoreRing
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.scoreColor
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeSimulationSummary
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SimulatedGrade
import dev.antigravity.classevivaexpressive.core.domain.model.SimulationRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StatsSnapshot
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAB_LAST = "Last grades"
private const val TAB_FIRST = "1st term"
private const val TAB_SECOND = "2nd term"
private const val TAB_GENERAL = "General"

data class GradesUiState(
  val grades: List<Grade> = emptyList(),
  val stats: StatsSnapshot = StatsSnapshot(),
  val simulation: GradeSimulationSummary = GradeSimulationSummary(),
)

@HiltViewModel
class GradesViewModel @Inject constructor(
  private val gradesRepository: GradesRepository,
  private val simulationRepository: SimulationRepository,
) : ViewModel() {
  val state = combine(
    gradesRepository.observeGrades(),
    gradesRepository.observeStats(),
    simulationRepository.observeSimulation(),
  ) { grades, stats, simulation ->
    GradesUiState(grades = grades, stats = stats, simulation = simulation)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesUiState())

  init {
    viewModelScope.launch { gradesRepository.refreshGrades() }
  }

  fun refresh() {
    viewModelScope.launch { gradesRepository.refreshGrades(force = true) }
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
}

@Composable
fun GradesRoute(
  modifier: Modifier = Modifier,
  viewModel: GradesViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf(TAB_LAST) }
  var showSimulationDialog by rememberSaveable { mutableStateOf(false) }

  val lastGrades = remember(state.grades) { state.grades.sortedByDescending { it.date }.take(20) }
  val firstTermGrades = remember(state.grades) { state.grades.filter { it.period.isFirstTermLabel() } }
  val secondTermGrades = remember(state.grades) { state.grades.filter { it.period.isSecondTermLabel() } }
  val activeGrades = when (selectedTab) {
    TAB_FIRST -> firstTermGrades.ifEmpty { state.grades }
    TAB_SECOND -> secondTermGrades.ifEmpty { state.grades }
    else -> state.grades
  }
  val summaryCards = remember(activeGrades, state.stats, selectedTab) {
    when (selectedTab) {
      TAB_GENERAL -> state.stats.subjectSummaries.map {
        SubjectGradeCard(
          subject = it.subject,
          average = it.average,
          detail = performanceHint(it.average),
          recentValues = it.recentValues,
        )
      }

      else -> activeGrades.groupBy { it.subject }.map { (subject, grades) ->
        val numeric = grades.mapNotNull { it.numericValue }
        val average = numeric.takeIf { it.isNotEmpty() }?.average()
        SubjectGradeCard(
          subject = subject,
          average = average,
          detail = performanceHint(average),
          recentValues = numeric.takeLast(6),
        )
      }.sortedByDescending { it.average ?: 0.0 }
    }
  }
  val chartValues = remember(activeGrades, state.stats, selectedTab) {
    val source = if (selectedTab == TAB_GENERAL && state.stats.gradeTrend.isNotEmpty()) {
      state.stats.gradeTrend.map { it.value.toFloat() }
    } else {
      activeGrades.mapNotNull { it.numericValue?.toFloat() }.takeLast(18)
    }
    source.takeIf { it.size >= 4 } ?: listOf(4f, 6f, 7f, 5f, 8f, 7f)
  }
  val average = remember(activeGrades, state.stats, selectedTab) {
    when (selectedTab) {
      TAB_GENERAL -> state.stats.overallAverage
      else -> activeGrades.mapNotNull { it.numericValue }.takeIf { it.isNotEmpty() }?.average()
    }
  }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "Grades",
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
          }
          IconButton(onClick = { showSimulationDialog = true }) {
            Icon(Icons.Rounded.Add, contentDescription = "Add simulation")
          }
          IconButton(onClick = viewModel::clearSimulation) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear simulation")
          }
        },
      )
    }
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
          ExpressivePillTabs(
            options = listOf(TAB_LAST, TAB_FIRST, TAB_SECOND, TAB_GENERAL),
            selected = selectedTab,
            onSelect = { selectedTab = it },
          )
        }
      }
    }
    when (selectedTab) {
      TAB_LAST -> {
        if (lastGrades.isEmpty()) {
          item {
            EmptyState(
              title = "No grades yet",
              detail = "Recent grades will appear here as soon as the sync completes.",
            )
          }
        } else {
          items(lastGrades, key = { it.id }) { grade ->
            ExpressiveColorTile(
              title = grade.subject,
              subtitle = listOfNotNull(
                grade.type.takeIf { it.isNotBlank() },
                grade.description ?: grade.notes,
              ).joinToString(" - ").ifBlank { grade.date },
              detail = grade.date,
              badge = grade.valueLabel,
              color = scoreColor(grade.numericValue),
            )
          }
        }
      }

      else -> {
        item {
          ExpressiveEditorialCard {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              ExpressiveScoreRing(
                valueText = average?.format2() ?: "--",
                progress = ((average ?: 0.0) / 10.0).toFloat(),
                color = scoreColor(average),
                size = 84.dp,
              )
              Box(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = "Average",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = when (selectedTab) {
                      TAB_FIRST -> "First term trend"
                      TAB_SECOND -> "Second term trend"
                      else -> "General trend"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
            ExpressiveMiniChart(
              points = chartValues,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.height(120.dp),
              threshold = 6f,
            )
          }
        }
        if (summaryCards.isEmpty()) {
          item {
            EmptyState(
              title = "No stats available",
              detail = "I need at least a few numeric grades to build this overview.",
            )
          }
        } else {
          items(summaryCards, key = { it.subject }) { summary ->
            SubjectSummaryRow(summary = summary)
          }
        }
      }
    }

    if (state.simulation.grades.isNotEmpty()) {
      item {
        ExpressiveTopHeader(
          title = "Simulation",
          subtitle = "Tap a simulated grade to remove it.",
          actions = {
            Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          },
        )
      }
      itemsIndexed(state.simulation.grades, key = { _, grade -> grade.id }) { _, grade ->
        ExpressiveColorTile(
          title = "${grade.subject} - ${grade.type}",
          subtitle = "Added on ${grade.date}",
          detail = grade.note ?: "Simulated average ${state.simulation.simulatedAverage?.format2() ?: "--"}",
          badge = grade.value.format1(),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
          onClick = { viewModel.removeSimulatedGrade(grade.id) },
        )
      }
    }
  }

  if (showSimulationDialog) {
    AddSimulationDialog(
      onDismiss = { showSimulationDialog = false },
      onSave = { subject, value, type, note ->
        viewModel.addSimulatedGrade(subject, value, type, note)
        showSimulationDialog = false
      },
    )
  }
}

@Composable
private fun SubjectSummaryRow(
  summary: SubjectGradeCard,
) {
  ExpressiveEditorialCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ExpressiveScoreRing(
        valueText = summary.average?.format2() ?: "--",
        progress = ((summary.average ?: 0.0) / 10.0).toFloat(),
        color = scoreColor(summary.average),
        size = 78.dp,
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = summary.subject,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = summary.detail,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.recentValues.isNotEmpty()) {
          Text(
            text = "Recent: ${summary.recentValues.takeLast(4).joinToString(" - ") { it.format1() }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
          )
        }
      }
    }
  }
}

@Composable
private fun AddSimulationDialog(
  onDismiss: () -> Unit,
  onSave: (subject: String, value: Double, type: String, note: String) -> Unit,
) {
  var subject by rememberSaveable { mutableStateOf("") }
  var valueText by rememberSaveable { mutableStateOf("") }
  var type by rememberSaveable { mutableStateOf("Interrogazione") }
  var note by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Aggiungi voto simulato") },
    text = {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
          OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Materia") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = valueText,
            onValueChange = { valueText = it },
            label = { Text("Valore") },
            supportingText = { Text("Esempio: 7.5") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = type,
            onValueChange = { type = it },
            label = { Text("Tipologia") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Nota") },
            minLines = 2,
          )
        }
      }
    },
    confirmButton = {
      val numeric = valueText.toDoubleOrNull()
      TextButton(
        onClick = { onSave(subject, numeric ?: 0.0, type, note) },
        enabled = subject.isNotBlank() && numeric != null,
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

private data class SubjectGradeCard(
  val subject: String,
  val average: Double?,
  val detail: String,
  val recentValues: List<Double>,
)

private fun String?.isFirstTermLabel(): Boolean {
  val value = this?.uppercase(Locale.getDefault()).orEmpty()
  return value.contains("1") || value.contains("PRIMO") || value.contains("FIRST")
}

private fun String?.isSecondTermLabel(): Boolean {
  val value = this?.uppercase(Locale.getDefault()).orEmpty()
  return value.contains("2") || value.contains("SECONDO") || value.contains("SECOND")
}

private fun performanceHint(average: Double?): String {
  return when {
    average == null -> "Waiting for enough numeric grades."
    average >= 7.0 -> "Stable and under control."
    average >= 6.0 -> "Keep this pace and avoid dips."
    else -> "You need to recover above ${minOf(10.0, 6.0 + (6.0 - average)).format2()}."
  }
}

private fun Double.format1(): String = String.format(Locale.US, "%.1f", this)

private fun Double.format2(): String = String.format(Locale.US, "%.2f", this)
