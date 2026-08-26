package dev.antigravity.classevivaexpressive.feature.absences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidIndeterminateBar
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

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
  initialAbsenceId: String? = null,
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


  LaunchedEffect(initialAbsenceId, state.absences) {
    if (!initialAbsenceId.isNullOrBlank() && state.selectedAbsence?.id != initialAbsenceId) {
      state.absences.firstOrNull { it.id == initialAbsenceId && !it.justified && it.canJustify }?.let {
        viewModel.requestJustification(it)
      }
    }
  }

  FluidScreen(
    modifier = modifier,
    title = "Assenze",
    // Rosso sotto la pagina quando c'e' qualcosa da giustificare, esattamente come l'intestazione:
    // l'urgenza di questa sezione e' un fatto sulla sezione, non una decorazione del riquadro in
    // cima.
    ambient = FeatureIdentity.Attendance.ambient(urgent = pending.isNotEmpty()),
    subtitle = "Situazione sintetica, giustificazioni pendenti e cronologia ordinata.",
    onBack = onBack,
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 18.dp,
  ) {
    item {
      FeatureHero(
        identity = FeatureIdentity.Attendance,
        eyebrow = "Presenze",
        value = pending.size.toString(),
        label = "da giustificare",
        icon = if (pending.isEmpty()) Icons.AutoMirrored.Rounded.FactCheck else Icons.Rounded.EventBusy,
        urgent = pending.isNotEmpty(),
      )
    }
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FluidMetricTile(
          label = "Assenze",
          value = absenceCount.toString(),
          detail = "nell'anno",
          modifier = Modifier.weight(1f),
          glass = true,
        )
        FluidMetricTile(
          label = "Ritardi",
          value = lateCount.toString(),
          detail = "nell'anno",
          modifier = Modifier.weight(1f),
          glass = true,
        )
        FluidMetricTile(
          label = "Uscite",
          value = exitCount.toString(),
          detail = "anticipate",
          modifier = Modifier.weight(1f),
          glass = true,
        )
      }
    }
    if (state.isSubmitting) {
      item {
        FluidIndeterminateBar(modifier = Modifier.fillMaxWidth())
      }
    }
    item { FluidSectionHeader("Da giustificare") }
    if (pending.isEmpty()) {
      item {
        FluidEmptyState(
          title = "Nessuna giustificazione in sospeso",
          detail = "Assenze, ritardi e uscite risultano già allineati con lo stato corrente.",
        )
      }
    } else {
      fluidGlassGroups(pending) { absence ->
        AbsenceRow(
          absence = absence,
          onJustify = { viewModel.requestJustification(absence) },
        )
      }
    }
    item { FluidSectionHeader("Storico") }
    if (history.isEmpty()) {
      item {
        FluidEmptyState(
          title = "Nessuna registrazione disponibile",
          detail = "Quando le API ufficiali sincronizzano presenze e uscite, qui trovi una cronologia leggibile.",
        )
      }
    } else {
      fluidGlassGroups(history.take(20)) { absence ->
        AbsenceRow(
          absence = absence,
          onJustify = if (!absence.justified && absence.canJustify) ({ viewModel.requestJustification(absence) }) else null,
        )
      }
    }
    if (!state.lastMessage.isNullOrBlank()) {
      item {
        FluidInlineMessage(
          message = state.lastMessage.orEmpty(),
          title = "Assenze",
          onDismiss = viewModel::clearMessage,
        )
      }
    }
  }

  state.selectedAbsence?.let { absence ->
    var reason by rememberSaveable(absence.id) { mutableStateOf(absence.justificationReason.orEmpty()) }
    FluidAlert(
      onDismissRequest = viewModel::dismissJustification,
      title = "Giustifica ${absenceLabel(absence.type).lowercase(italianLocale)}",
      actions = listOf(
        FluidAlertAction("Annulla", viewModel::dismissJustification, FluidAlertAction.Emphasis.Normal, enabled = !state.isSubmitting),
        FluidAlertAction("Invia", { viewModel.justify(reason) }, FluidAlertAction.Emphasis.Preferred, enabled = !state.isSubmitting),
      ),
      content = {
        FluidTextField(
          value = reason,
          onValueChange = { reason = it },
          modifier = Modifier.fillMaxWidth(),
          label = "Motivazione opzionale",
          minLines = 3,
        )
      },
    )
  }
}

@Composable
private fun AbsenceRow(
  absence: AbsenceRecord,
  onJustify: (() -> Unit)?,
) {
  FluidListRow(
    title = absence.date.toReadableDate(),
    subtitle = absenceLabel(absence.type),
    eyebrow = if (absence.justified) "Giustificata" else "Da controllare",
    meta = listOfNotNull(
      absence.hours?.let { hoursLabel(absence.type, it) },
      absence.justificationReason,
      absence.justificationDate?.let { "Giustificata il ${it.toReadableDate()}" },
    ).joinToString(" / ").ifBlank {
      when {
        absence.justified -> "Stato già confermato."
        absence.canJustify -> "Tocca per inviare la giustificazione."
        else -> "Nessun endpoint ufficiale disponibile per la giustificazione."
      }
    },
    tone = absenceTone(absence),
    leading = {
      Icon(
        imageVector = when (absence.type) {
          AbsenceType.ABSENCE -> Icons.Rounded.EventBusy
          AbsenceType.LATE -> Icons.AutoMirrored.Rounded.Login
          AbsenceType.EXIT -> Icons.AutoMirrored.Rounded.Logout
        },
        contentDescription = null,
      )
    },
    badge = {
      FluidStatusBadge(
        label = badgeLabel(absence.type),
        tone = absenceTone(absence),
      )
    },
    onClick = onJustify,
    animatePress = true,
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

internal fun absenceTone(absence: AbsenceRecord): FluidTone {
  return when {
    absence.justified -> FluidTone.Neutral
    absence.type == AbsenceType.ABSENCE -> FluidTone.Danger
    else -> FluidTone.Warning
  }
}

private fun String.toReadableDate(): String {
  val parsed = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
  return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale))
}
