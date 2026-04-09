package dev.antigravity.classevivaexpressive.feature.absences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class AbsencesUiState(
  val absences: List<AbsenceRecord> = emptyList(),
  val selectedAbsence: AbsenceRecord? = null,
  val lastMessage: String? = null,
  val isSubmitting: Boolean = false,
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class AbsencesViewModel @Inject constructor(
  private val absencesRepository: AbsencesRepository,
) : ViewModel() {
  private val selectedAbsence = MutableStateFlow<AbsenceRecord?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isSubmitting = MutableStateFlow(false)
  private val isRefreshing = MutableStateFlow(false)

  val state = combine(
    absencesRepository.observeAbsences(),
    selectedAbsence,
    lastMessage,
    isSubmitting,
    isRefreshing,
  ) { absences, selected, message, submitting, refreshing ->
    AbsencesUiState(
      absences = absences,
      selectedAbsence = selected,
      lastMessage = message,
      isSubmitting = submitting,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AbsencesUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
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

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      absencesRepository.refreshAbsences(force = force)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare le assenze." }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
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
  val pending = remember(state.absences) { state.absences.filter { !it.justified && it.canJustify }.sortedByDescending { it.date } }
  val history = remember(state.absences) { state.absences.sortedByDescending { it.date } }

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
        title = "Assenze",
        subtitle = "Situazione sintetica, giustificazioni pendenti e cronologia ordinata.",
        onBack = onBack,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
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
          label = "Assenze",
          value = absenceCount.toString(),
          detail = "Assenze registrate",
          tone = if (pending.any { it.type == AbsenceType.ABSENCE }) ExpressiveTone.Danger else ExpressiveTone.Neutral,
          modifier = Modifier.weight(1f),
        )
        MetricTile(
          label = "Ritardi",
          value = lateCount.toString(),
          detail = "Ingressi dopo l'orario",
          tone = if (pending.any { it.type == AbsenceType.LATE }) ExpressiveTone.Warning else ExpressiveTone.Neutral,
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricTile(
          label = "Uscite",
          value = exitCount.toString(),
          detail = "Uscite anticipate registrate",
          tone = if (pending.any { it.type == AbsenceType.EXIT }) ExpressiveTone.Warning else ExpressiveTone.Neutral,
          modifier = Modifier.weight(1f),
        )
        MetricTile(
          label = "Da giustificare",
          value = pending.size.toString(),
          detail = if (pending.isEmpty()) "Situazione allineata" else "Richiede una verifica rapida",
          tone = if (pending.isEmpty()) ExpressiveTone.Neutral else ExpressiveTone.Warning,
          modifier = Modifier.weight(1f),
        )
      }
    }
    if (state.isSubmitting) {
      item {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
    item { ExpressiveAccentLabel("Da giustificare") }
    if (pending.isEmpty()) {
      item {
        EmptyState(
          title = "Nessuna giustificazione in sospeso",
          detail = "Assenze, ritardi e uscite risultano gia allineati con lo stato corrente.",
        )
      }
    } else {
      items(pending, key = { it.id }) { absence ->
        AbsenceRow(
          absence = absence,
          onJustify = { viewModel.requestJustification(absence) },
        )
      }
    }
    item { ExpressiveAccentLabel("Storico") }
    if (history.isEmpty()) {
      item {
        EmptyState(
          title = "Nessuna registrazione disponibile",
          detail = "Quando le API ufficiali sincronizzano presenze e uscite, qui trovi una cronologia leggibile.",
        )
      }
    } else {
      items(history.take(20), key = { it.id }) { absence ->
        AbsenceRow(
          absence = absence,
          onJustify = if (!absence.justified && absence.canJustify) ({ viewModel.requestJustification(absence) }) else null,
        )
      }
    }
    if (!state.lastMessage.isNullOrBlank()) {
      item {
        Text(text = state.lastMessage.orEmpty())
      }
      item {
        TextButton(onClick = viewModel::clearMessage) {
          Text("Nascondi messaggio")
        }
      }
    }
    }
  }

  state.selectedAbsence?.let { absence ->
    var reason by rememberSaveable(absence.id) { mutableStateOf(absence.justificationReason.orEmpty()) }
    AlertDialog(
      onDismissRequest = viewModel::dismissJustification,
      title = { Text("Giustifica ${absenceLabel(absence.type).lowercase(italianLocale)}") },
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
private fun AbsenceRow(
  absence: AbsenceRecord,
  onJustify: (() -> Unit)?,
) {
  RegisterListRow(
    title = absence.date.toReadableDate(),
    subtitle = absenceLabel(absence.type),
    eyebrow = if (absence.justified) "Giustificata" else "Da controllare",
    meta = listOfNotNull(
      absence.hours?.let { hoursLabel(absence.type, it) },
      absence.justificationReason,
      absence.justificationDate?.let { "Giustificata il ${it.toReadableDate()}" },
    ).joinToString(" / ").ifBlank {
      when {
        absence.justified -> "Stato gia confermato."
        absence.canJustify -> "Tocca per inviare la giustificazione."
        else -> "Nessun endpoint ufficiale disponibile per la giustificazione."
      }
    },
    tone = absenceTone(absence),
    badge = {
      StatusBadge(
        label = badgeLabel(absence.type),
        tone = absenceTone(absence),
      )
    },
    onClick = onJustify,
  )
}

internal fun absenceLabel(type: AbsenceType): String {
  return when (type) {
    AbsenceType.ABSENCE -> "Assenza"
    AbsenceType.LATE -> "Ritardo"
    AbsenceType.EXIT -> "Uscita anticipata"
  }
}

internal fun badgeLabel(type: AbsenceType): String {
  return when (type) {
    AbsenceType.ABSENCE -> "A"
    AbsenceType.LATE -> "R"
    AbsenceType.EXIT -> "U"
  }
}

internal fun hoursLabel(type: AbsenceType, hour: Int): String {
  return when (type) {
    AbsenceType.ABSENCE -> "Ora $hour"
    AbsenceType.LATE -> "Ingresso alla $hour"
    AbsenceType.EXIT -> "Uscita alla $hour"
  }
}

internal fun absenceTone(absence: AbsenceRecord): ExpressiveTone {
  return when {
    absence.justified -> ExpressiveTone.Neutral
    absence.type == AbsenceType.ABSENCE -> ExpressiveTone.Danger
    else -> ExpressiveTone.Warning
  }
}

private fun String.toReadableDate(): String {
  val parsed = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
  return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale))
}
