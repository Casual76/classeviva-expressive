package dev.antigravity.classevivaexpressive.feature.dashboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
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
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContainerScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidIndeterminateBar
import dev.antigravity.fluidengine.ui.fluid.FluidLoadingBlock
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidHeroCard
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidPillTabs
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

// ─────────────────────────────────────────────────────────────────────────────
// MATERIALS
// ─────────────────────────────────────────────────────────────────────────────

data class MaterialsUiState(
  val items: List<MaterialItem> = emptyList(),
  val initialLoading: Boolean = true,
  val refreshing: Boolean = false,
  val refreshError: String? = null,
  val isStale: Boolean = false,
)

internal fun MaterialItem.isLinkMaterial(): Boolean = objectType.equals("link", ignoreCase = true)

private data class MaterialsUiExtras(
  val initialLoading: Boolean = true,
  val refreshing: Boolean = false,
  val refreshError: String? = null,
)

@HiltViewModel
class MaterialsViewModel @Inject constructor(
  private val materialsRepository: MaterialsRepository,
) : ViewModel() {
  private val extras = MutableStateFlow(MaterialsUiExtras())

  val state = combine(
    materialsRepository.observeMaterials(),
    materialsRepository.observeMaterialsRefreshMetadata(),
    extras,
  ) { items, metadata, local ->
    MaterialsUiState(
      items = items,
      initialLoading = local.initialLoading,
      refreshing = local.refreshing,
      refreshError = local.refreshError ?: metadata.refreshError,
      isStale = metadata.isStale || ((local.refreshError ?: metadata.refreshError) != null && items.isNotEmpty()),
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaterialsUiState())

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() {
    refresh(force = true, showIndicator = true)
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        extras.update { it.copy(refreshing = true, refreshError = null) }
      }
      materialsRepository.refreshMaterials(force = force)
        .onFailure { error -> extras.update { it.copy(refreshError = error.message ?: "Aggiornamento didattica non riuscito") } }
      extras.update { it.copy(initialLoading = false, refreshing = false) }
    }
  }

  fun openAsset(
    item: MaterialItem,
    onAsset: (MaterialAsset) -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch {
      materialsRepository.openAsset(item)
        .onSuccess(onAsset)
        .onFailure { onError(it.message ?: "Errore") }
    }
  }

  fun queueDownload(
    item: MaterialItem,
    onSuccess: (MaterialAsset) -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch {
      materialsRepository.queueDownload(item)
        .onSuccess(onSuccess)
        .onFailure { onError(it.message ?: "Download non riuscito") }
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
  val teachersById = remember(state.teachers) { state.teachers.associateBy { it.id } }

  FluidScreen(
    title = "Colloqui",
    subtitle = "Prenotazioni e disponibilita dei docenti.",
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
    itemSpacing = 12.dp,
  ) {
    item {
      FluidHeroCard(
        title = "Colloqui",
        subtitle = "${state.slots.count { it.available }} disponibilità · ${state.bookings.size} prenotazioni",
      )
    }
    state.lastMessage?.let { message ->
      item {
        FluidInlineMessage(
          message = message,
          title = "Colloqui",
          onDismiss = viewModel::clearMessage,
        )
      }
    }

    if (state.bookings.isNotEmpty()) {
      item { FluidSectionHeader("Prenotati") }
      items(state.bookings, key = { it.id }) { booking ->
        FluidListRow(
          title = booking.teacher.name,
          subtitle = booking.slot.meetingSlotLabel(),
          eyebrow = booking.teacher.subject ?: "Colloquio",
          meta = booking.bookingPosition?.let { "Posizione: $it" } ?: booking.status,
          tone = FluidTone.Success,
          onClick = { viewModel.selectBooking(booking) },
          badge = { FluidStatusBadge("PRENOTATO", tone = FluidTone.Success) },
          animatePress = true,
        )
      }
    }

    val availableSlots = state.slots.filter { it.available }
    if (availableSlots.isNotEmpty()) {
      item { FluidSectionHeader("Disponibili") }
      items(availableSlots, key = { it.id }) { slot ->
        val teacher = teachersById[slot.teacherId]
        FluidListRow(
          title = teacher?.name ?: "Docente",
          subtitle = slot.meetingSlotLabel(),
          eyebrow = teacher?.subject ?: "Disponibile",
          meta = slot.location,
          tone = FluidTone.Info,
          onClick = { viewModel.selectSlot(slot) },
          badge = { FluidStatusBadge("PRENOTA", tone = FluidTone.Info) },
          animatePress = true,
        )
      }
    }

    if (state.bookings.isEmpty() && availableSlots.isEmpty() && !state.isRefreshing) {
      item {
        FluidEmptyState(
          title = "Nessun colloquio disponibile",
          detail = "Le prenotazioni e le disponibilita compariranno qui dopo la sincronizzazione o quando il portale le espone.",
        )
      }
      item {
        FluidButton(
          text = "Apri portale colloqui",
          onClick = { context.openUrl(viewModel.portalUrl()) },
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
          leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,) },
        )
      }
    }
  }

  state.selectedBooking?.let { booking ->
    FluidSheet(onDismissRequest = viewModel::dismissSelection) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(booking.teacher.name, style = MaterialTheme.typography.headlineSmall)
        Text(booking.slot.meetingSlotLabel(), style = MaterialTheme.typography.bodyMedium)
        booking.bookingPosition?.let {
          Text("Posizione: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FluidButton(
          text = "Partecipa",
          onClick = { viewModel.joinSelectedBooking(context::openUrl) },
          style = FluidButtonStyle.Filled,
          fillWidth = true,
          leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,) },
        )
        FluidButton(
          text = "Annulla prenotazione",
          onClick = viewModel::cancelSelectedBooking,
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
        )
        FluidButton(
          text = "Apri portale",
          onClick = { context.openUrl(viewModel.portalUrl()) },
          style = FluidButtonStyle.Plain,
          fillWidth = true,
        )
      }
    }
  }

  state.selectedSlot?.let { slot ->
    val teacher = teachersById[slot.teacherId]
    FluidSheet(onDismissRequest = viewModel::dismissSelection) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(teacher?.name ?: "Docente", style = MaterialTheme.typography.headlineSmall)
        Text(slot.meetingSlotLabel(), style = MaterialTheme.typography.bodyMedium)
        slot.location?.takeIf(String::isNotBlank)?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FluidButton(
          text = "Prenota colloquio",
          onClick = viewModel::bookSelectedSlot,
          style = FluidButtonStyle.Filled,
          fillWidth = true,
        )
        FluidButton(
          text = "Apri portale",
          onClick = { context.openUrl(viewModel.portalUrl()) },
          style = FluidButtonStyle.Plain,
          fillWidth = true,
        )
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

private fun Context.openResource(
  contentUri: String?,
  externalUrl: String?,
  mimeType: String?,
): Boolean {
  val intent = when {
    !contentUri.isNullOrBlank() -> Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(Uri.parse(contentUri), mimeType ?: "application/octet-stream")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    !externalUrl.isNullOrBlank() -> Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
    else -> return false
  }
  return runCatching {
    startActivity(intent)
    true
  }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsRoute(
  onBack: (() -> Unit)? = null,
  onOpenMaterial: ((String) -> Unit)? = null,
  viewModel: MaterialsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val items = state.items
  var selectedItem by remember { mutableStateOf<MaterialItem?>(null) }
  var assetPreviewText by remember { mutableStateOf<String?>(null) }
  var assetErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var isDownloading by rememberSaveable { mutableStateOf(false) }
  var downloadMessage by rememberSaveable { mutableStateOf<String?>(null) }
  val context = LocalContext.current

  FluidScreen(
    title = "Didattica",
    subtitle = "Materiali condivisi dai docenti, link a risorse esterne e file da scaricare.",
    onBack = onBack,
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.refreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 12.dp,
  ) {
    item {
      FluidHeroCard(
        title = "Didattica",
        subtitle = if (items.isEmpty()) "Materiali in sincronizzazione" else "${items.size} contenuti disponibili",
      )
    }
    // Loading, error and empty all live inside the scroll rather than replacing it, so the title
    // stays put and pull-to-refresh keeps working while the screen has nothing to show.
    when {
      state.initialLoading && items.isEmpty() -> item { FluidLoadingBlock() }

      items.isEmpty() && state.refreshError != null -> item {
        FluidInlineMessage(
          title = "Didattica non disponibile",
          message = state.refreshError.orEmpty(),
          tone = FluidTone.Warning,
        )
      }

      items.isEmpty() -> item {
        FluidEmptyState(
          title = "Nessun materiale",
          detail = "Non ci sono ancora file o link condivisi dai tuoi professori.",
        )
      }

      else -> {
        if (state.isStale && state.refreshError != null) {
          item {
            FluidInlineMessage(
              title = "Contenuti non aggiornati",
              message = "Mostro l'ultima copia disponibile. ${state.refreshError}",
              tone = FluidTone.Warning,
            )
          }
        }
        items(items, key = { it.id }) { item ->
          FluidListRow(
            title = item.title,
            subtitle = item.teacherName,
            eyebrow = item.folderName,
            meta = item.sharedAt,
            tone = FluidTone.Info,
            onClick = {
              if (onOpenMaterial != null) onOpenMaterial(item.id) else selectedItem = item
            },
            badge = {
              FluidStatusBadge(if (item.isLinkMaterial()) "LINK" else "FILE", tone = FluidTone.Info)
            },
          )
        }
      }
    }
  }

  selectedItem?.let { item ->
    FluidSheet(onDismissRequest = {
      selectedItem = null
      assetPreviewText = null
      assetErrorMessage = null
      isDownloading = false
      downloadMessage = null
    }) {
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
        downloadMessage?.let {
          Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        assetErrorMessage?.let {
          Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        FluidButton(
          text = if (item.isLinkMaterial()) "Vai al link" else "Apri file",
          onClick = {
            assetErrorMessage = null
            viewModel.openAsset(
              item = item,
              onAsset = { asset ->
                assetPreviewText = asset.textPreview
                if (
                  asset.textPreview == null &&
                  !context.openResource(
                    contentUri = asset.contentUri,
                    externalUrl = asset.externalUrl,
                    mimeType = asset.mimeType,
                  )
                ) {
                  assetErrorMessage = "Nessun contenuto o link disponibile per questo materiale."
                }
              },
              onError = { error -> assetErrorMessage = error },
            )
          },
          style = FluidButtonStyle.Filled,
          fillWidth = true,
          leading = { Icon( if (item.isLinkMaterial()) Icons.Rounded.Link else Icons.Rounded.Download, contentDescription = null, ) },
        )
        if (!item.isLinkMaterial()) {
          FluidButton(
            text = if (isDownloading) "Download in corso" else "Salva per uso offline",
            onClick = {
              isDownloading = true
              downloadMessage = null
              viewModel.queueDownload(
                item = item,
                onSuccess = { asset ->
                  isDownloading = false
                  downloadMessage = "File disponibile offline."
                  context.openResource(asset.contentUri, asset.externalUrl, asset.mimeType)
                },
                onError = { error ->
                  isDownloading = false
                  downloadMessage = error
                },
              )
            },
            style = FluidButtonStyle.Tinted,
            enabled = !isDownloading,
            loading = isDownloading,
            fillWidth = true,
            leading = { Icon(Icons.Rounded.Download, contentDescription = null) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailRoute(
  itemId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MaterialsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val item = remember(state.items, itemId) { state.items.firstOrNull { it.id == itemId } }
  val context = LocalContext.current
  var showActions by rememberSaveable(itemId) { mutableStateOf(false) }
  var assetPreviewText by remember { mutableStateOf<String?>(null) }
  var assetErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var isDownloading by rememberSaveable { mutableStateOf(false) }
  var downloadMessage by rememberSaveable { mutableStateOf<String?>(null) }

  if (item == null) {
    FluidScreen(title = "Dettaglio materiale", modifier = modifier, onBack = onBack) {
      item(key = "material-detail-missing") {
        FluidEmptyState(
          title = "Materiale non disponibile",
          detail = "Il contenuto potrebbe essere stato rimosso o non ancora sincronizzato.",
        )
      }
    }
    return
  }

  FluidContainerScaffold(
    title = "Dettaglio materiale",
    modifier = modifier,
    onBack = onBack,
    hero = {
      FluidListRow(
        title = item.title,
        subtitle = item.teacherName,
        eyebrow = item.folderName,
        meta = item.sharedAt,
        tone = FluidTone.Info,
        badge = { FluidStatusBadge(if (item.isLinkMaterial()) "LINK" else "FILE", tone = FluidTone.Info) },
        animatePress = false,
      )
    },
    secondary = {
      Text(
        text = "Condiviso da ${item.teacherName} in ${item.folderName}",
        style = MaterialTheme.typography.bodyMedium,
      )
      FluidButton(
        text = if (item.isLinkMaterial()) "Apri risorsa" else "Apri o scarica",
        onClick = { showActions = true },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    },
  )

  if (showActions) {
    FluidSheet(onDismissRequest = { showActions = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
        assetPreviewText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        downloadMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        assetErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FluidButton(
          text = if (item.isLinkMaterial()) "Vai al link" else "Apri file",
          onClick = {
            assetErrorMessage = null
            viewModel.openAsset(
              item,
              onAsset = { asset ->
                assetPreviewText = asset.textPreview
                if (asset.textPreview == null && !context.openResource(asset.contentUri, asset.externalUrl, asset.mimeType)) {
                  assetErrorMessage = "Nessun contenuto o link disponibile per questo materiale."
                }
              },
              onError = { assetErrorMessage = it },
            )
          },
          style = FluidButtonStyle.Filled,
          fillWidth = true,
          leading = { Icon(if (item.isLinkMaterial()) Icons.Rounded.Link else Icons.Rounded.Download, null) },
        )
        if (!item.isLinkMaterial()) {
          FluidButton(
            text = if (isDownloading) "Download in corso" else "Salva per uso offline",
            onClick = {
              isDownloading = true
              downloadMessage = null
              viewModel.queueDownload(
                item,
                onSuccess = { asset ->
                  isDownloading = false
                  downloadMessage = "File disponibile offline."
                  context.openResource(asset.contentUri, asset.externalUrl, asset.mimeType)
                },
                onError = {
                  isDownloading = false
                  assetErrorMessage = it
                },
              )
            },
            style = FluidButtonStyle.Tinted,
            enabled = !isDownloading,
            loading = isDownloading,
            fillWidth = true,
            leading = { Icon(Icons.Rounded.Download, null) },
          )
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
  onOpenHomework: ((String) -> Unit)? = null,
  viewModel: HomeworkViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(initialHomeworkId, state.homeworks) {
    if (!initialHomeworkId.isNullOrBlank() && state.selectedHomework?.id != initialHomeworkId) {
      state.homeworks.firstOrNull { it.id == initialHomeworkId }?.let(viewModel::selectHomework)
    }
  }

  FluidScreen(
    title = "Compiti",
    subtitle = "Compiti assegnati dai docenti con data di consegna e dettaglio.",
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
    itemSpacing = 12.dp,
  ) {
    item {
      FluidHeroCard(
        title = "Compiti",
        subtitle = if (state.homeworks.isEmpty()) "Nessuna attività assegnata" else "${state.homeworks.size} attività assegnate",
      )
    }
    if (state.homeworks.isEmpty()) {
      if (state.isRefreshing) {
        item { FluidLoadingBlock() }
      } else {
        item {
          FluidEmptyState(
            title = "Nessun compito",
            detail = "Non ci sono compiti assegnati al momento.",
          )
        }
      }
    } else {
      items(state.homeworks, key = { it.id }) { item ->
        FluidListRow(
          title = item.subject,
          subtitle = item.description,
          eyebrow = "COMPITO",
          meta = item.homeworkMeta(),
          tone = FluidTone.Warning,
          onClick = {
            if (onOpenHomework != null) onOpenHomework(item.id) else viewModel.selectHomework(item)
          },
          badge = {
            if (item.history.isNotEmpty()) {
              FluidStatusBadge("MODIFICATO", tone = FluidTone.Info)
            }
            FluidStatusBadge("COMPITO", tone = FluidTone.Warning)
          },
        )
      }
    }
  }

  if (onOpenHomework == null) state.selectedHomework?.let { hw ->
    FluidSheet(onDismissRequest = viewModel::dismiss) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = hw.subject, style = MaterialTheme.typography.headlineSmall)
        if (state.isLoadingDetail) {
          FluidIndeterminateBar(modifier = Modifier.fillMaxWidth())
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
          FluidStatusBadge("MODIFICATO", tone = FluidTone.Info)
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

@Composable
fun HomeworkDetailRoute(
  homeworkId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HomeworkViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val homework = remember(state.homeworks, homeworkId) {
    state.homeworks.firstOrNull { it.id == homeworkId }
  }

  LaunchedEffect(homeworkId, homework) {
    if (homework != null && state.selectedHomework?.id != homeworkId) viewModel.selectHomework(homework)
  }
  DisposableEffect(viewModel, homeworkId) {
    onDispose { viewModel.dismiss() }
  }

  if (homework == null) {
    FluidScreen(title = "Dettaglio compito", modifier = modifier, onBack = onBack) {
      item(key = "homework-detail-missing") {
        FluidEmptyState(
          title = "Compito non disponibile",
          detail = "Il compito potrebbe essere stato rimosso o non ancora sincronizzato.",
        )
      }
    }
    return
  }

  val detail = state.selectedDetail?.takeIf { it.homework.id == homeworkId }
  FluidContainerScaffold(
    title = "Dettaglio compito",
    modifier = modifier,
    onBack = onBack,
    hero = {
      FluidListRow(
        title = homework.subject,
        subtitle = homework.description,
        eyebrow = "COMPITO",
        meta = homework.homeworkMeta(),
        tone = FluidTone.Warning,
        badge = {
          if (homework.history.isNotEmpty()) FluidStatusBadge("MODIFICATO", tone = FluidTone.Info)
          FluidStatusBadge("COMPITO", tone = FluidTone.Warning)
        },
        animatePress = false,
      )
    },
    secondary = {
      if (state.isLoadingDetail) FluidIndeterminateBar(Modifier.fillMaxWidth())
      Text(
        text = detail?.fullText?.takeIf(String::isNotBlank) ?: homework.description,
        style = MaterialTheme.typography.bodyLarge,
      )
      detail?.assignedDate?.let { Text("Aggiunto: ${it.homeworkCreatedAtLabel()}") }
      homework.modifiedAtLabel()?.let { Text("Modificato: $it") }
      detail?.teacher?.takeIf(String::isNotBlank)?.let { Text("Docente: $it") }
      homework.notes?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      homework.dueDate.takeIf(String::isNotBlank)?.let { Text("Scadenza: $it") }
    },
  )
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
  val initialLoading: Boolean = true,
  val refreshing: Boolean = false,
  val documentsRefreshError: String? = null,
  val schoolbooksRefreshError: String? = null,
  val documentsAreStale: Boolean = false,
  val schoolbooksAreStale: Boolean = false,
  val refreshError: String? = null,
  val isStale: Boolean = false,
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val isOpeningDocument: Boolean = false,
  val isDownloadingDocument: Boolean = false,
  val downloadMessage: String? = null,
  val lastError: String? = null,
)

private data class DocumentsUiExtras(
  val initialLoading: Boolean = true,
  val refreshing: Boolean = false,
  val refreshError: String? = null,
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val isOpeningDocument: Boolean = false,
  val isDownloadingDocument: Boolean = false,
  val downloadMessage: String? = null,
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
    documentsRepository.observeDocumentsRefreshMetadata(),
    documentsRepository.observeSchoolbooksRefreshMetadata(),
    extras,
  ) { docs, books, documentsMetadata, schoolbooksMetadata, ex ->
    val documentsError = ex.refreshError ?: documentsMetadata.refreshError
    val schoolbooksError = ex.refreshError ?: schoolbooksMetadata.refreshError
    val documentsStale = documentsMetadata.isStale || (documentsError != null && docs.isNotEmpty())
    val schoolbooksStale = schoolbooksMetadata.isStale || (schoolbooksError != null && books.isNotEmpty())
    DocumentsUiState(
      documents = docs,
      schoolbookCourses = books,
      initialLoading = ex.initialLoading,
      refreshing = ex.refreshing,
      documentsRefreshError = documentsError,
      schoolbooksRefreshError = schoolbooksError,
      documentsAreStale = documentsStale,
      schoolbooksAreStale = schoolbooksStale,
      refreshError = documentsError ?: schoolbooksError,
      isStale = documentsStale || schoolbooksStale,
      selectedDocument = ex.selectedDocument,
      selectedAsset = ex.selectedAsset,
      isOpeningDocument = ex.isOpeningDocument,
      isDownloadingDocument = ex.isDownloadingDocument,
      downloadMessage = ex.downloadMessage,
      lastError = ex.lastError,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() = refresh(force = true, showIndicator = true)

  fun openDocument(doc: DocumentItem) {
    extras.update { it.copy(selectedDocument = doc, selectedAsset = null, lastError = null, downloadMessage = null) }
    viewModelScope.launch {
      extras.update { it.copy(isOpeningDocument = true) }
      documentsRepository.openDocument(doc)
        .onSuccess { asset -> extras.update { it.copy(selectedAsset = asset) } }
        .onFailure { e -> extras.update { it.copy(lastError = e.message ?: "Errore apertura documento") } }
      extras.update { it.copy(isOpeningDocument = false) }
    }
  }

  fun dismissDocument() {
    extras.update {
      it.copy(
        selectedDocument = null,
        selectedAsset = null,
        lastError = null,
        isDownloadingDocument = false,
        downloadMessage = null,
      )
    }
  }

  fun queueDownload(doc: DocumentItem) = viewModelScope.launch {
    extras.update { it.copy(isDownloadingDocument = true, downloadMessage = null, lastError = null) }
    documentsRepository.queueDownload(doc)
      .onSuccess { asset ->
        extras.update { it.copy(selectedAsset = asset, downloadMessage = "Documento disponibile offline.") }
      }
      .onFailure { error ->
        extras.update { it.copy(lastError = error.message ?: "Download documento non riuscito") }
      }
    extras.update { it.copy(isDownloadingDocument = false) }
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) = viewModelScope.launch {
    if (showIndicator) extras.update { it.copy(refreshing = true, refreshError = null) }
    documentsRepository.refreshDocuments(force)
      .onFailure { error -> extras.update { it.copy(refreshError = error.message ?: "Aggiornamento documenti non riuscito") } }
    extras.update { it.copy(initialLoading = false, refreshing = false) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsRoute(
  onBack: (() -> Unit)? = null,
  onOpenDocument: ((String) -> Unit)? = null,
  viewModel: DocumentsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf("Documenti") }
  val selectedRefreshError = if (selectedTab == "Documenti") {
    state.documentsRefreshError
  } else {
    state.schoolbooksRefreshError
  }
  val selectedContentIsStale = if (selectedTab == "Documenti") {
    state.documentsAreStale
  } else {
    state.schoolbooksAreStale
  }
  val context = LocalContext.current

  FluidScreen(
    title = "Documenti e libri",
    subtitle = "Documenti della scuola, pagelle e libri scolastici adottati.",
    onBack = onBack,
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.refreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 12.dp,
  ) {
    item {
      FluidHeroCard(
        title = "Archivio scolastico",
        subtitle = "${state.documents.size} documenti · ${state.schoolbookCourses.size} corsi con libri",
      )
    }
    if (state.initialLoading && state.documents.isEmpty() && state.schoolbookCourses.isEmpty()) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
          FluidLoading()
        }
      }
    }
    item {
      FluidPillTabs(
        options = listOf("Documenti", "Libri"),
        selected = selectedTab,
        onSelect = { selectedTab = it },
      )
    }

    if (selectedContentIsStale && selectedRefreshError != null) {
      item {
        FluidInlineMessage(
          title = if (selectedTab == "Documenti") "Documenti non aggiornati" else "Libri non aggiornati",
          message = "Mostro l'ultima copia disponibile. $selectedRefreshError",
          tone = FluidTone.Warning,
        )
      }
    } else if (selectedRefreshError != null) {
      item {
        FluidInlineMessage(
          title = if (selectedTab == "Documenti") "Documenti non disponibili" else "Libri non disponibili",
          message = selectedRefreshError,
          tone = FluidTone.Warning,
        )
      }
    }

    if (selectedTab == "Documenti") {
      if (state.documents.isEmpty() && !state.initialLoading && state.documentsRefreshError == null) {
        item {
          FluidEmptyState(
            title = "Nessun documento",
            detail = "Non ci sono ancora documenti disponibili.",
          )
        }
      } else {
        items(state.documents, key = { it.id }) { doc ->
          FluidListRow(
            title = doc.title,
            subtitle = doc.detail,
            tone = FluidTone.Info,
            onClick = {
              if (onOpenDocument != null) onOpenDocument(doc.id) else viewModel.openDocument(doc)
            },
            badge = { FluidStatusBadge("DOCUMENTO", tone = FluidTone.Info) },
            animatePress = true,
          )
        }
      }
    } else {
      if (state.schoolbookCourses.isEmpty() && !state.initialLoading && state.schoolbooksRefreshError == null) {
        item {
          FluidEmptyState(
            title = "Nessun libro",
            detail = "Non ci sono libri scolastici disponibili per quest'anno.",
          )
        }
      } else {
        state.schoolbookCourses.forEach { course ->
          item(key = "header_${course.id}") {
            FluidSectionHeader(course.title)
          }
          items(course.books, key = { it.id }) { book ->
            val bookTone = when {
              book.alreadyOwned -> FluidTone.Success
              book.toBuy -> FluidTone.Warning
              else -> FluidTone.Neutral
            }
            val bookBadge = when {
              book.alreadyOwned -> "POSSEDUTO"
              book.toBuy -> "DA ACQUISTARE"
              else -> "INFO"
            }
            FluidListRow(
              title = book.title,
              subtitle = book.author ?: "—",
              eyebrow = book.subject,
              meta = "ISBN: ${book.isbn}",
              tone = bookTone,
              badge = { FluidStatusBadge(bookBadge, tone = bookTone) },
            )
          }
        }
      }
    }
  }

  if (onOpenDocument == null) state.selectedDocument?.let { doc ->
    FluidSheet(onDismissRequest = viewModel::dismissDocument) {
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
              FluidLoading()
            }
          }
          state.selectedAsset != null -> {
            val asset = state.selectedAsset!!
            asset.textPreview?.let {
              Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (!asset.contentUri.isNullOrBlank() || !asset.externalUrl.isNullOrBlank()) {
              FluidButton(
                text = "Apri documento",
                onClick = { context.openResource(asset.contentUri, asset.externalUrl, asset.mimeType) },
                style = FluidButtonStyle.Filled,
                fillWidth = true,
                leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,) },
              )
            }
            if (asset.fileName != null) {
              FluidButton(
                text = if (state.isDownloadingDocument) "Download in corso" else "Salva per uso offline",
                onClick = { viewModel.queueDownload(doc) },
                style = FluidButtonStyle.Tinted,
                enabled = !state.isDownloadingDocument,
                loading = state.isDownloadingDocument,
                fillWidth = true,
                leading = { Icon(Icons.Rounded.Download, contentDescription = null) },
              )
            }
            state.downloadMessage?.let { message ->
              Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            state.lastError?.let { error ->
              Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
          }
          state.lastError != null -> {
            val errorMsg = state.lastError!!
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            FluidButton(
              text = "Riprova",
              onClick = { viewModel.openDocument(doc) },
              style = FluidButtonStyle.Filled,
              fillWidth = true,
            )
          }
          else -> {
            FluidButton(
              text = "Apri",
              onClick = { viewModel.openDocument(doc) },
              style = FluidButtonStyle.Filled,
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
fun DocumentDetailRoute(
  documentId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DocumentsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val document = remember(state.documents, documentId) {
    state.documents.firstOrNull { it.id == documentId }
  }
  val context = LocalContext.current
  var showActions by rememberSaveable(documentId) { mutableStateOf(false) }

  DisposableEffect(viewModel, documentId) {
    onDispose { viewModel.dismissDocument() }
  }

  if (document == null) {
    FluidScreen(title = "Dettaglio documento", modifier = modifier, onBack = onBack) {
      item(key = "document-detail-missing") {
        FluidEmptyState(
          title = "Documento non disponibile",
          detail = "Il documento potrebbe essere stato rimosso o non ancora sincronizzato.",
        )
      }
    }
    return
  }

  FluidContainerScaffold(
    title = "Dettaglio documento",
    modifier = modifier,
    onBack = onBack,
    hero = {
      FluidListRow(
        title = document.title,
        subtitle = document.detail,
        tone = FluidTone.Info,
        badge = { FluidStatusBadge("DOCUMENTO", tone = FluidTone.Info) },
        animatePress = false,
      )
    },
    secondary = {
      if (document.detail.isNotBlank()) Text(document.detail, style = MaterialTheme.typography.bodyLarge)
      FluidButton(
        text = "Apri o scarica",
        onClick = {
          showActions = true
          if (state.selectedDocument?.id != documentId) viewModel.openDocument(document)
        },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    },
  )

  if (showActions) {
    FluidSheet(
      onDismissRequest = {
        showActions = false
        viewModel.dismissDocument()
      },
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(document.title, style = MaterialTheme.typography.headlineSmall)
        when {
          state.isOpeningDocument -> FluidLoading()
          state.selectedAsset != null -> {
            val asset = state.selectedAsset
            asset?.textPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (asset != null && (!asset.contentUri.isNullOrBlank() || !asset.externalUrl.isNullOrBlank())) {
              FluidButton(
                text = "Apri documento",
                onClick = { context.openResource(asset.contentUri, asset.externalUrl, asset.mimeType) },
                style = FluidButtonStyle.Filled,
                fillWidth = true,
                leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
              )
            }
            if (asset?.fileName != null) {
              FluidButton(
                text = if (state.isDownloadingDocument) "Download in corso" else "Salva per uso offline",
                onClick = { viewModel.queueDownload(document) },
                style = FluidButtonStyle.Tinted,
                enabled = !state.isDownloadingDocument,
                loading = state.isDownloadingDocument,
                fillWidth = true,
                leading = { Icon(Icons.Rounded.Download, null) },
              )
            }
          }
          state.lastError != null -> {
            Text(state.lastError.orEmpty(), color = MaterialTheme.colorScheme.error)
            FluidButton(
              text = "Riprova",
              onClick = { viewModel.openDocument(document) },
              style = FluidButtonStyle.Filled,
              fillWidth = true,
            )
          }
        }
        state.downloadMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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

  LaunchedEffect(initialImportPayload) {
    if (!initialImportPayload.isNullOrBlank()) {
      viewModel.importPayload(initialImportPayload)
    }
  }

  FluidScreen(
    title = "Media studente",
    subtitle = "Punteggio composito calcolato su media voti, frequenza e costanza.",
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 12.dp,
  ) {
    state.currentScore?.let { score ->
      item {
        FluidHeroCard(
          title = "${score.score.roundToInt()}/100",
          subtitle = score.label,
        )
      }
      if (score.components.isNotEmpty()) {
        item { FluidSectionHeader("Componenti") }
        items(score.components, key = { it.title }) { component ->
          FluidMetricTile(
            label = component.title,
            value = "%.1f / %.0f".format(component.value, component.maxValue),
            detail = "Peso ${(component.weight * 100).roundToInt()}%",
            glass = true,
          )
        }
      }
    } ?: item {
      FluidEmptyState(
        title = "Punteggio non disponibile",
        detail = "Il punteggio verrà calcolato dopo il primo aggiornamento dei dati.",
      )
    }

    item { FluidSectionHeader("Azioni") }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = if (state.isExporting) "Esportando..." else "Esporta punteggio",
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
          style = FluidButtonStyle.Filled,
          enabled = state.currentScore != null && !state.isExporting,
          fillWidth = true,
          leading = { Icon(Icons.Rounded.Share, contentDescription = null,) },
        )
        FluidButton(
          text = "Importa da clipboard",
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotBlank()) viewModel.importPayload(text)
          },
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
        )
      }
    }

    if (state.snapshots.size > 1) {
      item { FluidSectionHeader("Storico") }
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
        FluidListRow(
          title = "${snap.score.roundToInt()}/100",
          subtitle = snap.label,
          meta = dateLabel,
          tone = FluidTone.Neutral,
        )
      }
    }

    state.lastMessage?.let { msg ->
      item {
        FluidInlineMessage(
          message = msg,
          title = "Media studente",
          tone = FluidTone.Warning,
          onDismiss = viewModel::clearMessage,
        )
      }
    }
  }

  state.importResult?.let { comparison ->
    FluidSheet(onDismissRequest = viewModel::dismissImport) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Confronto punteggio", style = MaterialTheme.typography.headlineSmall)
        FluidMetricTile(
          label = "Punteggio corrente",
          value = "${comparison.current.score.roundToInt()}/100",
          detail = comparison.current.label,
          tone = FluidTone.Info,
          glass = true,
        )
        FluidMetricTile(
          label = "Punteggio importato",
          value = "${comparison.imported.score.roundToInt()}/100",
          detail = comparison.imported.label,
          tone = FluidTone.Neutral,
          glass = true,
        )
        val diffTone = when {
          comparison.difference > 0 -> FluidTone.Success
          comparison.difference < 0 -> FluidTone.Danger
          else -> FluidTone.Neutral
        }
        FluidMetricTile(
          label = "Differenza",
          value = "${if (comparison.difference >= 0) "+" else ""}${"%.1f".format(comparison.difference)}",
          detail = if (comparison.difference > 0) "In miglioramento" else if (comparison.difference < 0) "In peggioramento" else "Invariato",
          tone = diffTone,
          glass = true,
        )
      }
    }
  }
}
