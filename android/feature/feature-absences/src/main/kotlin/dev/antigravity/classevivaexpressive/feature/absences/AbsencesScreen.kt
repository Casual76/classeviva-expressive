package dev.antigravity.classevivaexpressive.feature.absences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveScoreRing
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AbsencesUiState(
  val absences: List<AbsenceRecord> = emptyList(),
  val selectedAbsence: AbsenceRecord? = null,
  val lastMessage: String? = null,
  val isSubmitting: Boolean = false,
)

@HiltViewModel
class AbsencesViewModel @Inject constructor(
  private val absencesRepository: AbsencesRepository,
) : ViewModel() {
  private val selectedAbsence = MutableStateFlow<AbsenceRecord?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isSubmitting = MutableStateFlow(false)

  val state = combine(
    absencesRepository.observeAbsences(),
    selectedAbsence,
    lastMessage,
    isSubmitting,
  ) { absences, selected, message, submitting ->
    AbsencesUiState(
      absences = absences,
      selectedAbsence = selected,
      lastMessage = message,
      isSubmitting = submitting,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AbsencesUiState())

  init {
    viewModelScope.launch { absencesRepository.refreshAbsences() }
  }

  fun refresh() {
    viewModelScope.launch { absencesRepository.refreshAbsences(force = true) }
  }

  fun requestJustification(absence: AbsenceRecord) {
    selectedAbsence.value = absence
  }

  fun dismissJustification() {
    selectedAbsence.value = null
  }

  fun justify(reason: String) {
    val target = selectedAbsence.value ?: return
    viewModelScope.launch {
      isSubmitting.value = true
      absencesRepository.justifyAbsence(target, reason.ifBlank { null })
        .onSuccess {
          selectedAbsence.value = null
          lastMessage.value = "Giustificazione inviata."
        }
        .onFailure {
          lastMessage.value = it.message ?: "Non sono riuscito a giustificare l'assenza."
        }
      isSubmitting.value = false
    }
  }

  fun clearMessage() {
    lastMessage.value = null
  }
}

@Composable
fun AbsencesRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: AbsencesViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val absenceCount = remember(state.absences) { state.absences.count { it.type == AbsenceType.ABSENCE } }
  val lateCount = remember(state.absences) { state.absences.count { it.type == AbsenceType.LATE } }
  val exitCount = remember(state.absences) { state.absences.count { it.type == AbsenceType.EXIT } }
  val pending = remember(state.absences) { state.absences.filter { !it.justified }.sortedByDescending { it.date } }
  val justified = remember(state.absences) { state.absences.filter { it.justified }.sortedByDescending { it.date } }
  val trend = remember(state.absences) { buildAbsenceTrend(state.absences) }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "Absences",
        onBack = onBack,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
          }
        },
      )
    }
    item {
      ExpressiveEditorialCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AbsenceMetric(label = "Absences", value = absenceCount, color = Color(0xFFFF4338))
          AbsenceMetric(label = "Early exits", value = exitCount, color = Color(0xFFFFC83D))
          AbsenceMetric(label = "Delay", value = lateCount, color = Color(0xFF2196F3))
        }
        ExpressiveMiniChart(
          points = trend,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.height(144.dp),
        )
      }
    }
    if (state.isSubmitting) {
      item {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
    if (pending.isNotEmpty()) {
      item {
        Text(
          text = "Not justified",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
      items(pending, key = { it.id }) { absence ->
        AbsenceCard(
          absence = absence,
          color = absenceCardColor(absence),
          onJustify = { viewModel.requestJustification(absence) },
        )
      }
    }
    if (justified.isNotEmpty()) {
      item {
        Text(
          text = "Justified",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
      items(justified, key = { it.id }) { absence ->
        AbsenceCard(
          absence = absence,
          color = absenceCardColor(absence),
          onJustify = null,
        )
      }
    }
    if (state.absences.isEmpty()) {
      item {
        EmptyState(
          title = "No absence records yet",
          detail = "Quando il registro sincronizza assenze, ritardi e uscite li vedrai qui divisi in modo molto più leggibile.",
        )
      }
    }
    state.lastMessage?.let { message ->
      item {
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      item {
        TextButton(onClick = viewModel::clearMessage) {
          Text("Nascondi messaggio")
        }
      }
    }
  }

  state.selectedAbsence?.let { absence ->
    var reason by rememberSaveable(absence.id) { mutableStateOf(absence.justificationReason.orEmpty()) }
    AlertDialog(
      onDismissRequest = viewModel::dismissJustification,
      title = { Text("Giustifica ${absence.type.name.lowercase()}") },
      text = {
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Motivazione opzionale") },
          minLines = 3,
        )
      },
      confirmButton = {
        TextButton(
          onClick = { viewModel.justify(reason) },
          enabled = !state.isSubmitting,
        ) {
          Text("Invia")
        }
      },
      dismissButton = {
        TextButton(
          onClick = viewModel::dismissJustification,
          enabled = !state.isSubmitting,
        ) {
          Text("Annulla")
        }
      },
    )
  }
}

@Composable
private fun AbsenceMetric(
  label: String,
  value: Int,
  color: Color,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ExpressiveScoreRing(
      valueText = value.toString(),
      progress = (value.coerceAtMost(10) / 10f),
      color = color,
      size = 74.dp,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun AbsenceCard(
  absence: AbsenceRecord,
  color: Color,
  onJustify: (() -> Unit)?,
) {
  ExpressiveColorTile(
    title = absence.date.toHumanDate(),
    subtitle = absenceTypeLabel(absence),
    detail = listOfNotNull(
      absence.hours?.let { "You entered at hour $it" },
      absence.justificationReason,
      absence.justificationDate?.let { "Justified on $it" },
    ).joinToString("\n").ifBlank { if (absence.justified) "Already justified" else "Tap to justify from the app" },
    badge = absenceBadge(absence),
    color = color,
    onClick = onJustify,
  )
}

private fun absenceBadge(absence: AbsenceRecord): String {
  return when (absence.type) {
    AbsenceType.ABSENCE -> "A"
    AbsenceType.LATE -> "L"
    AbsenceType.EXIT -> "E"
  }
}

private fun absenceTypeLabel(absence: AbsenceRecord): String {
  return when (absence.type) {
    AbsenceType.ABSENCE -> "Full-day absence"
    AbsenceType.LATE -> "Late entry"
    AbsenceType.EXIT -> "Early exit"
  }
}

private fun absenceCardColor(absence: AbsenceRecord): Color {
  return when (absence.type) {
    AbsenceType.ABSENCE -> Color(0xFFFF4338)
    AbsenceType.LATE -> Color(0xFF3493E8)
    AbsenceType.EXIT -> Color(0xFFFFC83D)
  }
}

private fun buildAbsenceTrend(absences: List<AbsenceRecord>): List<Float> {
  val months = listOf(
    Month.SEPTEMBER,
    Month.OCTOBER,
    Month.NOVEMBER,
    Month.DECEMBER,
    Month.JANUARY,
    Month.FEBRUARY,
    Month.MARCH,
    Month.APRIL,
    Month.MAY,
    Month.JUNE,
  )
  return months.map { month ->
    absences.count { record ->
      record.date.toLocalDateOrNull()?.month == month
    }.toFloat()
  }
}

private fun String.toLocalDateOrNull(): LocalDate? {
  return runCatching { LocalDate.parse(this) }.getOrNull()
}

private fun String.toHumanDate(): String {
  val parsed = toLocalDateOrNull() ?: return this
  return parsed.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
}
