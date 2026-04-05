package dev.antigravity.classevivaexpressive.feature.meetings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val meetingsLocale: Locale = Locale.forLanguageTag("it-IT")

data class MeetingsUiState(
  val teachers: List<MeetingTeacher> = emptyList(),
  val slots: List<MeetingSlot> = emptyList(),
  val bookings: List<MeetingBooking> = emptyList(),
  val selectedYear: SchoolYearRef = SchoolYearRef.current(LocalDate.now().year, LocalDate.now().monthValue),
  val capability: FeatureCapability? = null,
  val lastMessage: String? = null,
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class MeetingsViewModel @Inject constructor(
  private val meetingsRepository: MeetingsRepository,
  private val schoolYearRepository: SchoolYearRepository,
  private val capabilityResolver: CapabilityResolver,
) : ViewModel() {
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)

  private val contentState = combine(
    meetingsRepository.observeMeetingTeachers(),
    meetingsRepository.observeMeetingSlots(),
    meetingsRepository.observeMeetingBookings(),
    schoolYearRepository.observeSelectedSchoolYear(),
    capabilityResolver.observeCapability(RegistroFeature.MEETINGS),
  ) { teachers, slots, bookings, year, capability ->
    MeetingsUiState(
      teachers = teachers,
      slots = slots.sortedBy { "${it.date}-${it.startTime}" },
      bookings = bookings.sortedBy { "${it.slot.date}-${it.slot.startTime}" },
      selectedYear = year,
      capability = capability,
    )
  }

  val state = combine(
    contentState,
    lastMessage,
    isRefreshing,
  ) { content, message, refreshing ->
    MeetingsUiState(
      teachers = content.teachers,
      slots = content.slots,
      bookings = content.bookings,
      selectedYear = content.selectedYear,
      capability = content.capability,
      lastMessage = message,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingsUiState())

  init {
    refresh(showIndicator = false)
  }

  fun refresh(showIndicator: Boolean = true) {
    viewModelScope.launch {
      if (showIndicator) isRefreshing.value = true
      meetingsRepository.refreshMeetings(force = true)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare i colloqui." }
      isRefreshing.value = false
    }
  }

  fun book(slot: MeetingSlot) {
    viewModelScope.launch {
      meetingsRepository.bookMeeting(slot)
        .onSuccess { lastMessage.value = "Colloquio prenotato." }
        .onFailure { lastMessage.value = it.message ?: "Prenotazione non riuscita." }
    }
  }

  fun cancel(booking: MeetingBooking) {
    viewModelScope.launch {
      meetingsRepository.cancelMeeting(booking)
        .onSuccess { lastMessage.value = "Prenotazione annullata." }
        .onFailure { lastMessage.value = it.message ?: "Annullamento non riuscito." }
    }
  }

  fun join(booking: MeetingBooking) {
    viewModelScope.launch {
      meetingsRepository.joinMeeting(booking)
        .onSuccess { lastMessage.value = "Link colloquio pronto: ${it.url}" }
        .onFailure { lastMessage.value = it.message ?: "Link colloquio non disponibile." }
    }
  }

  fun clearMessage() {
    lastMessage.value = null
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: MeetingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedBooking by remember { mutableStateOf<MeetingBooking?>(null) }
  val teacherNames = remember(state.teachers) { state.teachers.associateBy { it.id } }

  PullToRefreshBox(
    modifier = modifier.fillMaxSize(),
    isRefreshing = state.isRefreshing,
    onRefresh = { viewModel.refresh(showIndicator = true) },
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      item {
        ExpressiveTopHeader(
          title = "Colloqui",
          subtitle = "Disponibilita e prenotazioni per ${state.selectedYear.label}, con azioni gestite dal gateway controllato.",
          onBack = onBack,
          actions = {
            IconButton(onClick = { viewModel.refresh(showIndicator = true) }) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
            }
          },
        )
      }
      state.capability?.let { capability ->
        item {
          RegisterListRow(
            title = capability.label.ifBlank { "Disponibilita colloqui" },
            subtitle = capability.detail ?: "Capability non disponibile.",
            eyebrow = state.selectedYear.label,
            tone = if (capability.enabled) ExpressiveTone.Info else ExpressiveTone.Warning,
            badge = {
              StatusBadge(
                label = capability.mode.name.replace('_', ' '),
                tone = if (capability.enabled) ExpressiveTone.Info else ExpressiveTone.Warning,
              )
            },
          )
        }
      }
      item { ExpressiveAccentLabel("Prenotati") }
      if (state.bookings.isEmpty()) {
        item {
          EmptyState(
            title = "Nessun colloquio prenotato",
            detail = "Le prenotazioni attive appariranno qui appena il gateway restituisce disponibilita o booking correnti.",
          )
        }
      } else {
        items(state.bookings, key = { it.id }) { booking ->
          RegisterListRow(
            title = teacherNames[booking.teacher.id]?.name ?: booking.teacher.name,
            subtitle = booking.teacher.subject ?: "Docente",
            eyebrow = booking.slot.date.toReadableDate(),
            meta = listOfNotNull(booking.slot.startTime, booking.slot.location, booking.status).joinToString(" - "),
            tone = ExpressiveTone.Success,
            onClick = { selectedBooking = booking },
            badge = { StatusBadge("BOOKED", tone = ExpressiveTone.Success) },
          )
        }
      }
      item { ExpressiveAccentLabel("Disponibili") }
      if (state.slots.none { it.available }) {
        item {
          EmptyState(
            title = "Nessuno slot disponibile",
            detail = "Gli slot prenotabili compariranno qui quando il modulo colloqui del tenant e disponibile.",
          )
        }
      } else {
        items(state.slots.filter { it.available }, key = { it.id }) { slot ->
          RegisterListRow(
            title = teacherNames[slot.teacherId]?.name ?: slot.teacherId,
            subtitle = teacherNames[slot.teacherId]?.subject ?: "Colloquio",
            eyebrow = slot.date.toReadableDate(),
            meta = listOfNotNull(slot.startTime, slot.endTime, slot.location).joinToString(" - "),
            tone = ExpressiveTone.Primary,
            onClick = { viewModel.book(slot) },
            badge = { StatusBadge("BOOK", tone = ExpressiveTone.Primary) },
          )
        }
      }
      if (!state.lastMessage.isNullOrBlank()) {
        item {
          Text(state.lastMessage.orEmpty())
        }
        item {
          TextButton(onClick = viewModel::clearMessage) {
            Text("Nascondi messaggio")
          }
        }
      }
    }
  }

  selectedBooking?.let { booking ->
    AlertDialog(
      onDismissRequest = { selectedBooking = null },
      title = { Text(booking.teacher.name) },
      text = {
        Text(
          listOfNotNull(
            booking.slot.date.toReadableDate(),
            booking.slot.startTime,
            booking.slot.endTime,
            booking.slot.location,
            booking.status,
          ).joinToString("\n"),
        )
      },
      confirmButton = {
        TextButton(onClick = { viewModel.join(booking) }) {
          Text("Apri link")
        }
      },
      dismissButton = {
        TextButton(onClick = {
          viewModel.cancel(booking)
          selectedBooking = null
        }) {
          Text("Annulla")
        }
      },
    )
  }
}

private fun String.toReadableDate(): String {
  val parsed = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
  return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", meetingsLocale))
}
