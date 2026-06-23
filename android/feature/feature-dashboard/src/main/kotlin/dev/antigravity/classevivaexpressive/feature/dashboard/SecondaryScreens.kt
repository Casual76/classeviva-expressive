package dev.antigravity.classevivaexpressive.feature.dashboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveLoading
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.InlineMessageCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingBooking
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingSlot
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingTeacher
import dev.antigravity.classevivaexpressive.core.domain.model.MeetingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComparison
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// MATERIALS
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class MaterialsViewModel @Inject constructor(
  private val materialsRepository: MaterialsRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)

  val state = materialsRepository.observeMaterials()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val refreshing = isRefreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() {
    refresh(force = true, showIndicator = true)
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      materialsRepository.refreshMaterials(force = force)
      isRefreshing.value = false
    }
  }

  fun openAsset(
    item: MaterialItem,
    onUrl: (String) -> Unit,
    onTextPreview: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch {
      materialsRepository.openAsset(item)
        .onSuccess { asset ->
          val url = asset.sourceUrl
          val preview = asset.textPreview
          when {
            url != null -> onUrl(url)
            preview != null -> onTextPreview(preview)
            else -> onError("Nessun contenuto disponibile")
          }
        }
        .onFailure { onError(it.message ?: "Errore") }
    }
  }
}

data class MeetingsUiState(
  val teachers: List<MeetingTeacher> = emptyList(),
  val slots: List<MeetingSlot> = emptyList(),
  val bookings: List<MeetingBooking> = emptyList(),
  val isRefreshing: Boolean = false,
  val selectedBooking: MeetingBooking? = null,
  val selectedSlot: MeetingSlot? = null,
  val lastMessage: String? = null,
)

private data class MeetingsUiExtras(
  val isRefreshing: Boolean = false,
  val selectedBooking: MeetingBooking? = null,
  val selectedSlot: MeetingSlot? = null,
  val lastMessage: String? = null,
)

@HiltViewModel
class MeetingsViewModel @Inject constructor(
  private val meetingsRepository: MeetingsRepository,
) : ViewModel() {
  private val extras = MutableStateFlow(MeetingsUiExtras())

  val state = combine(
    meetingsRepository.observeMeetingTeachers(),
    meetingsRepository.observeMeetingSlots(),
    meetingsRepository.observeMeetingBookings(),
    extras,
  ) { teachers, slots, bookings, ex ->
    MeetingsUiState(
      teachers = teachers,
      slots = slots,
      bookings = bookings,
      isRefreshing = ex.isRefreshing,
      selectedBooking = ex.selectedBooking,
      selectedSlot = ex.selectedSlot,
      lastMessage = ex.lastMessage,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingsUiState())

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() = refresh(force = true, showIndicator = true)

  fun selectBooking(booking: MeetingBooking) {
    extras.update { it.copy(selectedBooking = booking, selectedSlot = null, lastMessage = null) }
  }

  fun selectSlot(slot: MeetingSlot) {
    extras.update { it.copy(selectedSlot = slot, selectedBooking = null, lastMessage = null) }
  }

  fun dismissSelection() {
    extras.update { it.copy(selectedBooking = null, selectedSlot = null) }
  }

  fun clearMessage() {
    extras.update { it.copy(lastMessage = null) }
  }

  fun portalUrl(): String = meetingsRepository.getPortalMeetingsUrl()

  fun bookSelectedSlot() {
    val slot = state.value.selectedSlot ?: return
    viewModelScope.launch {
      meetingsRepository.bookMeeting(slot)
        .onSuccess { booking ->
          extras.update {
            it.copy(
              selectedSlot = null,
              selectedBooking = booking,
              lastMessage = "Colloquio prenotato.",
            )
          }
        }
        .onFailure { error ->
          extras.update { it.copy(lastMessage = error.message ?: "Prenotazione colloquio non riuscita.") }
        }
    }
  }

  fun cancelSelectedBooking() {
    val booking = state.value.selectedBooking ?: return
    viewModelScope.launch {
      meetingsRepository.cancelMeeting(booking)
        .onSuccess {
          extras.update {
            it.copy(
              selectedBooking = null,
              lastMessage = "Prenotazione annullata.",
            )
          }
        }
        .onFailure { error ->
          extras.update { it.copy(lastMessage = error.message ?: "Annullamento colloquio non riuscito.") }
        }
    }
  }

  fun joinSelectedBooking(onUrl: (String) -> Unit) {
    val booking = state.value.selectedBooking ?: return
    viewModelScope.launch {
      meetingsRepository.joinMeeting(booking)
        .onSuccess { link ->
          onUrl(link.url)
          extras.update { it.copy(lastMessage = "Link colloquio aperto.") }
        }
        .onFailure { error ->
          val directUrl = booking.slot.joinUrl
          if (!directUrl.isNullOrBlank()) {
            onUrl(directUrl)
          } else {
            extras.update { it.copy(lastMessage = error.message ?: "Link colloquio non disponibile.") }
          }
        }
    }
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) = viewModelScope.launch {
    if (showIndicator) extras.update { it.copy(isRefreshing = true) }
    meetingsRepository.refreshMeetings(force)
      .onFailure { error -> extras.update { it.copy(lastMessage = error.message ?: "Aggiornamento colloqui non riuscito.") } }
    extras.update { it.copy(isRefreshing = false) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsRoute(
  onBack: (() -> Unit)? = null,
  viewModel: MeetingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val teachersById = remember(state.teachers) { state.teachers.associateBy { it.id } }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Colloqui",
        subtitle = "Prenotazioni e disponibilita dei docenti.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        state.lastMessage?.let { message ->
          item {
            InlineMessageCard(
              message = message,
              title = "Colloqui",
              onDismiss = viewModel::clearMessage,
            )
          }
        }

        if (state.bookings.isNotEmpty()) {
          item { ExpressiveAccentLabel("Prenotati") }
          items(state.bookings, key = { it.id }) { booking ->
            RegisterListRow(
              title = booking.teacher.name,
              subtitle = booking.slot.meetingSlotLabel(),
              eyebrow = booking.teacher.subject ?: "Colloquio",
              meta = booking.bookingPosition?.let { "Posizione: $it" } ?: booking.status,
              tone = ExpressiveTone.Success,
              onClick = { viewModel.selectBooking(booking) },
              badge = { StatusBadge("PRENOTATO", tone = ExpressiveTone.Success) },
              animatePress = true,
            )
          }
        }

        val availableSlots = state.slots.filter { it.available }
        if (availableSlots.isNotEmpty()) {
          item { ExpressiveAccentLabel("Disponibili") }
          items(availableSlots, key = { it.id }) { slot ->
            val teacher = teachersById[slot.teacherId]
            RegisterListRow(
              title = teacher?.name ?: "Docente",
              subtitle = slot.meetingSlotLabel(),
              eyebrow = teacher?.subject ?: "Disponibile",
              meta = slot.location,
              tone = ExpressiveTone.Info,
              onClick = { viewModel.selectSlot(slot) },
              badge = { StatusBadge("PRENOTA", tone = ExpressiveTone.Info) },
              animatePress = true,
            )
          }
        }

        if (state.bookings.isEmpty() && availableSlots.isEmpty() && !state.isRefreshing) {
          item {
            EmptyState(
              title = "Nessun colloquio disponibile",
              detail = "Le prenotazioni e le disponibilita compariranno qui dopo la sincronizzazione o quando il portale le espone.",
            )
          }
          item {
            OutlinedButton(
              onClick = { context.openUrl(viewModel.portalUrl()) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
              Text("Apri portale colloqui")
            }
          }
        }
      }
    }
  }

  state.selectedBooking?.let { booking ->
    ModalBottomSheet(onDismissRequest = viewModel::dismissSelection) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(booking.teacher.name, style = MaterialTheme.typography.headlineSmall)
        Text(booking.slot.meetingSlotLabel(), style = MaterialTheme.typography.bodyMedium)
        booking.bookingPosition?.let {
          Text("Posizione: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
          onClick = { viewModel.joinSelectedBooking(context::openUrl) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
          Text("Partecipa")
        }
        OutlinedButton(
          onClick = viewModel::cancelSelectedBooking,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Annulla prenotazione")
        }
        TextButton(
          onClick = { context.openUrl(viewModel.portalUrl()) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Apri portale")
        }
      }
    }
  }

  state.selectedSlot?.let { slot ->
    val teacher = teachersById[slot.teacherId]
    ModalBottomSheet(onDismissRequest = viewModel::dismissSelection) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(teacher?.name ?: "Docente", style = MaterialTheme.typography.headlineSmall)
        Text(slot.meetingSlotLabel(), style = MaterialTheme.typography.bodyMedium)
        slot.location?.takeIf(String::isNotBlank)?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
          onClick = viewModel::bookSelectedSlot,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Prenota colloquio")
        }
        TextButton(
          onClick = { context.openUrl(viewModel.portalUrl()) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Apri portale")
        }
      }
    }
  }
}

private fun MeetingSlot.meetingSlotLabel(): String {
  return listOfNotNull(
    date.takeIf(String::isNotBlank),
    buildString {
      append(startTime)
      endTime?.takeIf(String::isNotBlank)?.let { append(" - $it") }
    }.takeIf(String::isNotBlank),
  ).joinToString(" / ")
}

private fun Context.openUrl(url: String) {
  if (url.isBlank()) return
  startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsRoute(
  onBack: (() -> Unit)? = null,
  viewModel: MaterialsViewModel = hiltViewModel(),
) {
  val items by viewModel.state.collectAsStateWithLifecycle()
  val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()
  var selectedItem by remember { mutableStateOf<MaterialItem?>(null) }
  var assetPreviewText by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Didattica",
        subtitle = "Materiali condivisi dai docenti, link a risorse esterne e file da scaricare.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      if (items.isEmpty() && !isRefreshing) {
        EmptyState(
          title = "Nessun materiale",
          detail = "Non ci sono ancora file o link condivisi dai tuoi professori.",
          modifier = Modifier.padding(20.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(items, key = { it.id }) { item ->
            RegisterListRow(
              title = item.title,
              subtitle = item.teacherName,
              eyebrow = item.folderName,
              meta = item.sharedAt,
              tone = ExpressiveTone.Info,
              onClick = { selectedItem = item },
              badge = {
                StatusBadge(if (item.objectType == "link") "LINK" else "FILE", tone = ExpressiveTone.Info)
              },
              animatePress = true,
            )
          }
        }
      }
    }
  }

  selectedItem?.let { item ->
    ModalBottomSheet(onDismissRequest = { selectedItem = null; assetPreviewText = null }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
        Text(
          text = "Condiviso da ${item.teacherName} in ${item.folderName}",
          style = MaterialTheme.typography.bodyMedium,
        )
        assetPreviewText?.let {
          Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        Button(
          onClick = {
            viewModel.openAsset(
              item = item,
              onUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
              onTextPreview = { text -> assetPreviewText = text },
              onError = {},
            )
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            if (item.objectType == "link") Icons.Rounded.Link else Icons.Rounded.Download,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
          )
          Text(if (item.objectType == "link") "Vai al link" else "Scarica file")
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOMEWORK (COMPITI)
// ─────────────────────────────────────────────────────────────────────────────

data class HomeworkUiState(
  val homeworks: List<Homework> = emptyList(),
  val isRefreshing: Boolean = false,
  val selectedHomework: Homework? = null,
  val selectedDetail: HomeworkDetail? = null,
  val isLoadingDetail: Boolean = false,
)

@HiltViewModel
class HomeworkViewModel @Inject constructor(
  private val homeworkRepository: HomeworkRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)
  private val selectedHomework = MutableStateFlow<Homework?>(null)
  private val selectedDetail = MutableStateFlow<HomeworkDetail?>(null)
  private val isLoadingDetail = MutableStateFlow(false)

  val state = combine(
    homeworkRepository.observeHomeworks(),
    isRefreshing,
    selectedHomework,
    selectedDetail,
    isLoadingDetail,
  ) { homeworks, refreshing, selected, detail, loadingDetail ->
    HomeworkUiState(homeworks, refreshing, selected, detail, loadingDetail)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeworkUiState())

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() = refresh(force = true, showIndicator = true)

  fun selectHomework(hw: Homework) {
    selectedHomework.value = hw
    selectedDetail.value = null
    viewModelScope.launch {
      isLoadingDetail.value = true
      homeworkRepository.getHomeworkDetail(hw.id).onSuccess { selectedDetail.value = it }
      isLoadingDetail.value = false
    }
  }

  fun dismiss() {
    selectedHomework.value = null
    selectedDetail.value = null
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) = viewModelScope.launch {
    if (showIndicator) isRefreshing.value = true
    homeworkRepository.refreshHomeworks(force)
    isRefreshing.value = false
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkRoute(
  initialHomeworkId: String? = null,
  onBack: (() -> Unit)? = null,
  viewModel: HomeworkViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  LaunchedEffect(initialHomeworkId, state.homeworks) {
    if (!initialHomeworkId.isNullOrBlank() && state.selectedHomework?.id != initialHomeworkId) {
      state.homeworks.firstOrNull { it.id == initialHomeworkId }?.let(viewModel::selectHomework)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Compiti",
        subtitle = "Compiti assegnati dai docenti con data di consegna e dettaglio.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
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
      if (state.homeworks.isEmpty() && !state.isRefreshing) {
        EmptyState(
          title = "Nessun compito",
          detail = "Non ci sono compiti assegnati al momento.",
          modifier = Modifier.padding(20.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(state.homeworks, key = { it.id }) { item ->
            RegisterListRow(
              title = item.subject,
              subtitle = item.description,
              eyebrow = "COMPITO",
              meta = item.homeworkMeta(),
              tone = ExpressiveTone.Warning,
              onClick = { viewModel.selectHomework(item) },
              badge = {
                if (item.history.isNotEmpty()) {
                  StatusBadge("MODIFICATO", tone = ExpressiveTone.Info)
                }
                StatusBadge("COMPITO", tone = ExpressiveTone.Warning)
              },
              animatePress = true,
            )
          }
        }
      }
    }
  }

  state.selectedHomework?.let { hw ->
    ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = hw.subject, style = MaterialTheme.typography.headlineSmall)
        if (state.isLoadingDetail) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.selectedDetail?.let { detail ->
          Text(text = detail.fullText, style = MaterialTheme.typography.bodyMedium)
          detail.assignedDate?.let {
            Text(
              text = "Aggiunto: ${it.homeworkCreatedAtLabel()}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          hw.modifiedAtLabel()?.let {
            Text(
              text = "Modificato: $it",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          detail.teacher?.let {
            Text(
              text = "Docente: $it",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } ?: run {
          if (!state.isLoadingDetail) {
            Text(text = hw.description, style = MaterialTheme.typography.bodyMedium)
            hw.notes?.let {
              Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        if (hw.history.isNotEmpty()) {
          StatusBadge("MODIFICATO", tone = ExpressiveTone.Info)
        }
        if (hw.dueDate.isNotBlank()) {
          Text(
            text = "Scadenza: ${hw.dueDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// MEETINGS (COLLOQUI)
// ─────────────────────────────────────────────────────────────────────────────

private val homeworkCreatedAtFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALIAN)

private fun Homework.homeworkMeta(): String? {
  return buildList {
    addedAtLabel()?.let { add("Aggiunto: $it") }
    modifiedAtLabel()?.let { add("Modificato: $it") }
    dueDate.takeIf(String::isNotBlank)?.let { add("Scadenza: $it") }
  }.joinToString(" / ").ifBlank { null }
}

private fun Homework.addedAtLabel(): String? = createdAt
  ?.trim()
  ?.takeIf(String::isNotBlank)
  ?.homeworkCreatedAtLabel()

private fun Homework.modifiedAtLabel(): String? {
  return history.maxByOrNull { it.recordedAtEpochMillis }
    ?.recordedAtEpochMillis
    ?.let { millis ->
      Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(homeworkCreatedAtFormatter)
    }
}

private fun String.homeworkCreatedAtLabel(): String {
  val value = trim().takeIf { it.isNotBlank() } ?: return this
  return runCatching {
    OffsetDateTime.parse(value).toLocalDateTime().format(homeworkCreatedAtFormatter)
  }.recoverCatching {
    LocalDateTime.parse(value).format(homeworkCreatedAtFormatter)
  }.getOrDefault(value)
}

// ─────────────────────────────────────────────────────────────────────────────
// DOCUMENTS (DOCUMENTI E LIBRI)
// ─────────────────────────────────────────────────────────────────────────────

data class DocumentsUiState(
  val documents: List<DocumentItem> = emptyList(),
  val schoolbookCourses: List<SchoolbookCourse> = emptyList(),
  val isRefreshing: Boolean = false,
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val isOpeningDocument: Boolean = false,
  val lastError: String? = null,
)

private data class DocumentsUiExtras(
  val isRefreshing: Boolean = false,
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val isOpeningDocument: Boolean = false,
  val lastError: String? = null,
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
  private val documentsRepository: DocumentsRepository,
) : ViewModel() {
  private val extras = MutableStateFlow(DocumentsUiExtras())

  val state = combine(
    documentsRepository.observeDocuments(),
    documentsRepository.observeSchoolbooks(),
    extras,
  ) { docs, books, ex ->
    DocumentsUiState(docs, books, ex.isRefreshing, ex.selectedDocument, ex.selectedAsset, ex.isOpeningDocument, ex.lastError)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() = refresh(force = true, showIndicator = true)

  fun openDocument(doc: DocumentItem) {
    extras.update { it.copy(selectedDocument = doc, selectedAsset = null, lastError = null) }
    viewModelScope.launch {
      extras.update { it.copy(isOpeningDocument = true) }
      documentsRepository.openDocument(doc)
        .onSuccess { asset -> extras.update { it.copy(selectedAsset = asset) } }
        .onFailure { e -> extras.update { it.copy(lastError = e.message ?: "Errore apertura documento") } }
      extras.update { it.copy(isOpeningDocument = false) }
    }
  }

  fun dismissDocument() {
    extras.update { it.copy(selectedDocument = null, selectedAsset = null, lastError = null) }
  }

  fun queueDownload(doc: DocumentItem) = viewModelScope.launch {
    documentsRepository.queueDownload(doc)
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) = viewModelScope.launch {
    if (showIndicator) extras.update { it.copy(isRefreshing = true) }
    documentsRepository.refreshDocuments(force)
    extras.update { it.copy(isRefreshing = false) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsRoute(
  onBack: (() -> Unit)? = null,
  viewModel: DocumentsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf("Documenti") }
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Documenti e libri",
        subtitle = "Documenti della scuola, pagelle e libri scolastici adottati.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          ExpressivePillTabs(
            options = listOf("Documenti", "Libri"),
            selected = selectedTab,
            onSelect = { selectedTab = it },
          )
        }

        if (selectedTab == "Documenti") {
          if (state.documents.isEmpty() && !state.isRefreshing) {
            item {
              EmptyState(
                title = "Nessun documento",
                detail = "Non ci sono ancora documenti disponibili.",
              )
            }
          } else {
            items(state.documents, key = { it.id }) { doc ->
              RegisterListRow(
                title = doc.title,
                subtitle = doc.detail,
                tone = ExpressiveTone.Info,
                onClick = { viewModel.openDocument(doc) },
                badge = { StatusBadge("DOCUMENTO", tone = ExpressiveTone.Info) },
                animatePress = true,
              )
            }
          }
        } else {
          if (state.schoolbookCourses.isEmpty() && !state.isRefreshing) {
            item {
              EmptyState(
                title = "Nessun libro",
                detail = "Non ci sono libri scolastici disponibili per quest'anno.",
              )
            }
          } else {
            state.schoolbookCourses.forEach { course ->
              item(key = "header_${course.id}") {
                ExpressiveAccentLabel(course.title)
              }
              items(course.books, key = { it.id }) { book ->
                val bookTone = when {
                  book.alreadyOwned -> ExpressiveTone.Success
                  book.toBuy -> ExpressiveTone.Warning
                  else -> ExpressiveTone.Neutral
                }
                val bookBadge = when {
                  book.alreadyOwned -> "POSSEDUTO"
                  book.toBuy -> "DA ACQUISTARE"
                  else -> "INFO"
                }
                RegisterListRow(
                  title = book.title,
                  subtitle = book.author ?: "—",
                  eyebrow = book.subject,
                  meta = "ISBN: ${book.isbn}",
                  tone = bookTone,
                  badge = { StatusBadge(bookBadge, tone = bookTone) },
                )
              }
            }
          }
        }
      }
    }
  }

  state.selectedDocument?.let { doc ->
    ModalBottomSheet(onDismissRequest = viewModel::dismissDocument) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = doc.title, style = MaterialTheme.typography.headlineSmall)
        if (doc.detail.isNotBlank()) {
          Text(text = doc.detail, style = MaterialTheme.typography.bodyMedium)
        }

        when {
          state.isOpeningDocument -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              ExpressiveLoading()
            }
          }
          state.selectedAsset != null -> {
            val asset = state.selectedAsset!!
            asset.textPreview?.let {
              Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            asset.sourceUrl?.let { url ->
              Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Apri documento")
              }
            }
            if (asset.fileName != null) {
              OutlinedButton(
                onClick = { viewModel.queueDownload(doc) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Scarica")
              }
            }
          }
          state.lastError != null -> {
            val errorMsg = state.lastError!!
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Button(
              onClick = { viewModel.openDocument(doc) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Riprova")
            }
          }
          else -> {
            Button(
              onClick = { viewModel.openDocument(doc) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Apri")
            }
          }
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// STUDENT SCORE (MEDIA STUDENTE)
// ─────────────────────────────────────────────────────────────────────────────

data class StudentScoreUiState(
  val currentScore: StudentScoreSnapshot? = null,
  val snapshots: List<StudentScoreSnapshot> = emptyList(),
  val isRefreshing: Boolean = false,
  val importResult: StudentScoreComparison? = null,
  val lastMessage: String? = null,
  val isExporting: Boolean = false,
)

private data class ScoreUiExtras(
  val isRefreshing: Boolean = false,
  val importResult: StudentScoreComparison? = null,
  val lastMessage: String? = null,
  val isExporting: Boolean = false,
)

@HiltViewModel
class StudentScoreViewModel @Inject constructor(
  private val studentScoreRepository: StudentScoreRepository,
) : ViewModel() {
  private val extras = MutableStateFlow(ScoreUiExtras())

  val state = combine(
    studentScoreRepository.observeCurrentScore(),
    studentScoreRepository.observeSnapshots(),
    extras,
  ) { score, snapshots, ex ->
    StudentScoreUiState(score, snapshots, ex.isRefreshing, ex.importResult, ex.lastMessage, ex.isExporting)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentScoreUiState())

  init {
    viewModelScope.launch { studentScoreRepository.refreshStudentScore(force = false) }
  }

  fun refresh() = viewModelScope.launch {
    extras.update { it.copy(isRefreshing = true) }
    studentScoreRepository.refreshStudentScore(force = true)
    extras.update { it.copy(isRefreshing = false) }
  }

  fun importPayload(payload: String) = viewModelScope.launch {
    studentScoreRepository.importPayload(payload)
      .onSuccess { comparison -> extras.update { it.copy(importResult = comparison) } }
      .onFailure { e -> extras.update { it.copy(lastMessage = e.message ?: "Payload non valido") } }
  }

  fun exportPayload(onPayload: (String) -> Unit) = viewModelScope.launch {
    extras.update { it.copy(isExporting = true) }
    studentScoreRepository.exportCurrentPayload()
      .onSuccess { onPayload(it) }
      .onFailure { e -> extras.update { it.copy(lastMessage = e.message ?: "Export non riuscito") } }
    extras.update { it.copy(isExporting = false) }
  }

  fun dismissImport() { extras.update { it.copy(importResult = null) } }
  fun clearMessage() { extras.update { it.copy(lastMessage = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScoreRoute(
  initialImportPayload: String? = null,
  viewModel: StudentScoreViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  LaunchedEffect(initialImportPayload) {
    if (!initialImportPayload.isNullOrBlank()) {
      viewModel.importPayload(initialImportPayload)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Media studente",
        subtitle = "Punteggio composito calcolato su media voti, frequenza e costanza.",
        scrollBehavior = scrollBehavior,
        actions = {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        state.currentScore?.let { score ->
          item {
            ExpressiveHeroCard(
              title = "${score.score.roundToInt()}/100",
              subtitle = score.label,
            )
          }
          if (score.components.isNotEmpty()) {
            item { ExpressiveAccentLabel("Componenti") }
            items(score.components, key = { it.title }) { component ->
              MetricTile(
                label = component.title,
                value = "%.1f / %.0f".format(component.value, component.maxValue),
                detail = "Peso ${(component.weight * 100).roundToInt()}%",
              )
            }
          }
        } ?: item {
          EmptyState(
            title = "Punteggio non disponibile",
            detail = "Il punteggio verrà calcolato dopo il primo aggiornamento dei dati.",
          )
        }

        item { ExpressiveAccentLabel("Azioni") }
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                viewModel.exportPayload { payload ->
                  val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload)
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Condividi punteggio"))
                }
              },
              modifier = Modifier.fillMaxWidth(),
              enabled = state.currentScore != null && !state.isExporting,
            ) {
              Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
              Text(if (state.isExporting) "Esportando..." else "Esporta punteggio")
            }
            OutlinedButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (text.isNotBlank()) viewModel.importPayload(text)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Importa da clipboard")
            }
          }
        }

        if (state.snapshots.size > 1) {
          item { ExpressiveAccentLabel("Storico") }
          items(
            state.snapshots.sortedByDescending { it.computedAtEpochMillis },
            key = { it.computedAtEpochMillis },
          ) { snap ->
            val dateLabel = try {
              val instant = Instant.ofEpochMilli(snap.computedAtEpochMillis)
              DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant)
            } catch (_: Exception) {
              "—"
            }
            RegisterListRow(
              title = "${snap.score.roundToInt()}/100",
              subtitle = snap.label,
              meta = dateLabel,
              tone = ExpressiveTone.Neutral,
            )
          }
        }

        state.lastMessage?.let { msg ->
          item {
            InlineMessageCard(
              message = msg,
              title = "Media studente",
              tone = ExpressiveTone.Warning,
              onDismiss = viewModel::clearMessage,
            )
          }
        }
      }
    }
  }

  state.importResult?.let { comparison ->
    ModalBottomSheet(onDismissRequest = viewModel::dismissImport) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Confronto punteggio", style = MaterialTheme.typography.headlineSmall)
        MetricTile(
          label = "Punteggio corrente",
          value = "${comparison.current.score.roundToInt()}/100",
          detail = comparison.current.label,
          tone = ExpressiveTone.Info,
        )
        MetricTile(
          label = "Punteggio importato",
          value = "${comparison.imported.score.roundToInt()}/100",
          detail = comparison.imported.label,
          tone = ExpressiveTone.Neutral,
        )
        val diffTone = when {
          comparison.difference > 0 -> ExpressiveTone.Success
          comparison.difference < 0 -> ExpressiveTone.Danger
          else -> ExpressiveTone.Neutral
        }
        MetricTile(
          label = "Differenza",
          value = "${if (comparison.difference >= 0) "+" else ""}${"%.1f".format(comparison.difference)}",
          detail = if (comparison.difference > 0) "In miglioramento" else if (comparison.difference < 0) "In peggioramento" else "Invariato",
          tone = diffTone,
        )
      }
    }
  }
}
