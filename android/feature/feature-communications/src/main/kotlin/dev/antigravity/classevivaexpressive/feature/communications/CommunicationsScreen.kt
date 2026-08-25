package dev.antigravity.classevivaexpressive.feature.communications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHero
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHeroMetric
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
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
import dev.antigravity.fluidengine.ui.fluid.FluidContainerScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidIndeterminateBar
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.fluid.FluidSectionSelectionMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import dev.antigravity.fluidengine.ui.theme.FluidPillTabs
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidSyncAction
import dev.antigravity.fluidengine.ui.theme.FluidSyncNotice
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.lastSyncLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.noticeMessage
import dev.antigravity.classevivaexpressive.core.designsystem.theme.toFluid

private const val TAB_BOARD = "Comunicazioni"
private const val TAB_NOTES = "Note"
private const val FILTER_ALL = "Tutte"
private const val FILTER_UNREAD = "Non lette"
private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

enum class CommunicationsMessageKind {
  Success,
  Error,
  Info,
}

data class CommunicationsMessage(
  val text: String,
  val kind: CommunicationsMessageKind,
)

internal fun CommunicationsMessageKind.toFluidNotificationTone(): FluidNotificationTone = when (this) {
  CommunicationsMessageKind.Success -> FluidNotificationTone.Success
  CommunicationsMessageKind.Error -> FluidNotificationTone.Error
  CommunicationsMessageKind.Info -> FluidNotificationTone.Info
}

data class CommunicationsUiState(
  val communications: List<Communication> = emptyList(),
  val notes: List<Note> = emptyList(),
  val selectedCommunication: CommunicationDetail? = null,
  val selectedNote: NoteDetail? = null,
  val lastMessage: CommunicationsMessage? = null,
  val isSubmittingAction: Boolean = false,
  val isRefreshing: Boolean = false,
  val pendingOpenUri: Uri? = null,
  val attachmentDialog: AttachmentDownloadDialogState? = null,
  val syncStatus: SyncStatus = SyncStatus(),
)

/**
 * What the docked bar says about this screen once the title has gone.
 *
 * Only facts that change: a count that is zero says nothing worth cycling to, so it is left out
 * rather than shown as a reassuring "0".
 */
/**
 * What the docked bar says about this screen once the title has left it.
 *
 * Only things that ask for attention. A running total — how many notices exist in the board — is
 * true, is never actionable, and pushed the one number that *is* actionable out of view for as long
 * as it held the bar. If nothing here needs attention the bar has nothing to add, and says the name
 * of the page instead.
 */
internal fun buildCommunicationsFacets(
  communications: List<Communication>,
  notes: List<Note>,
): List<String> = buildList {
  val unread = communications.count { !it.read }
  if (unread > 0) add(if (unread == 1) "1 da leggere" else "$unread da leggere")
  // A notice asking to be signed, answered or joined is outstanding whether or not it has been
  // opened, so it is counted separately from the unread ones rather than folded into them.
  val pending = communications.count { it.needsAck || it.needsReply || it.needsJoin || it.needsFile }
  if (pending > 0) add(if (pending == 1) "1 da confermare" else "$pending da confermare")
  val unreadNotes = notes.count { !it.read }
  if (unreadNotes > 0) add(if (unreadNotes == 1) "1 nota nuova" else "$unreadNotes note nuove")
}

data class AttachmentDownloadDialogState(
  val fileName: String,
  val title: String,
  val message: String,
  val isWorking: Boolean,
  val isError: Boolean = false,
)

private data class CommunicationsRuntimeState(
  val isRefreshing: Boolean,
  val pendingOpenUri: Uri?,
  val attachmentDialog: AttachmentDownloadDialogState?,
  val syncStatus: SyncStatus,
)

@HiltViewModel
class CommunicationsViewModel @Inject constructor(
  private val communicationsRepository: CommunicationsRepository,
  private val dashboardRepository: DashboardRepository,
) : ViewModel() {
  private val selectedCommunication = MutableStateFlow<CommunicationDetail?>(null)
  private val selectedNote = MutableStateFlow<NoteDetail?>(null)
  private val lastMessage = MutableStateFlow<CommunicationsMessage?>(null)
  private val isSubmittingAction = MutableStateFlow(false)
  private val isRefreshing = MutableStateFlow(false)
  private val pendingOpenUri = MutableStateFlow<Uri?>(null)
  private val attachmentDialog = MutableStateFlow<AttachmentDownloadDialogState?>(null)

  private val contentState = combine(
    communicationsRepository.observeCommunications(),
    communicationsRepository.observeNotes(),
    selectedCommunication,
  ) { communications, notes, communication ->
    Triple(communications, notes, communication)
  }

  val state = combine(
    contentState,
    selectedNote,
    lastMessage,
    isSubmittingAction,
    combine(
      isRefreshing,
      pendingOpenUri,
      attachmentDialog,
      dashboardRepository.observeDashboard(),
    ) { refreshing, openUri, dialog, dashboard ->
      CommunicationsRuntimeState(refreshing, openUri, dialog, dashboard.syncStatus)
    },
  ) { content, note, message, submitting, runtime ->
    val (communications, notes, communication) = content
    CommunicationsUiState(
      communications = communications,
      notes = notes,
      selectedCommunication = communication,
      selectedNote = note,
      lastMessage = message,
      isSubmittingAction = submitting,
      isRefreshing = runtime.isRefreshing,
      pendingOpenUri = runtime.pendingOpenUri,
      attachmentDialog = runtime.attachmentDialog,
      syncStatus = runtime.syncStatus,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunicationsUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  fun openCommunication(pubId: String, evtCode: String) {
    viewModelScope.launch {
      val cached = state.value.communications.firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
      if (cached != null) {
        selectedNote.value = null
        selectedCommunication.value = CommunicationDetail(
          communication = cached,
          content = cached.contentPreview.ifBlank { "Caricamento contenuto..." },
          actions = cached.actions,
        )
      }
      communicationsRepository.getCommunicationDetail(pubId, evtCode)
        .onSuccess {
          selectedNote.value = null
          selectedCommunication.value = it
        }
        .onFailure { error ->
          if (cached == null) {
            lastMessage.value = CommunicationsMessage(
              text = error.message ?: "Impossibile aprire la comunicazione.",
              kind = CommunicationsMessageKind.Error,
            )
          }
        }
    }
  }

  fun openNote(id: String, categoryCode: String) {
    viewModelScope.launch {
      communicationsRepository.getNoteDetail(id, categoryCode)
        .onSuccess {
          selectedCommunication.value = null
          selectedNote.value = it
        }
        .onFailure {
          lastMessage.value = CommunicationsMessage(
            text = it.message ?: "Impossibile aprire la nota.",
            kind = CommunicationsMessageKind.Error,
          )
        }
    }
  }

  fun downloadAttachment(attachment: RemoteAttachment) {
    viewModelScope.launch {
      communicationsRepository.queueDownload(attachment)
        .onSuccess {
          lastMessage.value = CommunicationsMessage(
            text = "Download avviato per ${attachment.name}",
            kind = CommunicationsMessageKind.Info,
          )
        }
        .onFailure {
          lastMessage.value = CommunicationsMessage(
            text = it.message ?: "Download fallito.",
            kind = CommunicationsMessageKind.Error,
          )
        }
    }
  }

  fun openAttachment(attachment: RemoteAttachment, context: Context) {
    viewModelScope.launch {
      val fileName = attachment.name.ifBlank { "allegato" }
      isSubmittingAction.value = true
      attachmentDialog.value = AttachmentDownloadDialogState(
        fileName = fileName,
        title = "Preparazione allegato",
        message = "Controllo la memoria locale. Se il file non è già salvato, lo scarico e lo conservo per 30 giorni.",
        isWorking = true,
      )
      communicationsRepository.resolveAttachmentLocalPath(attachment)
        .onSuccess { path ->
          val file = java.io.File(path)
          val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
          attachmentDialog.value = AttachmentDownloadDialogState(
            fileName = fileName,
            title = "Allegato pronto",
            message = "Il file è disponibile in memoria locale. Lo apro ora.",
            isWorking = false,
          )
          pendingOpenUri.value = uri
        }
        .onFailure {
          val message = it.message ?: "Impossibile aprire l'allegato."
          attachmentDialog.value = AttachmentDownloadDialogState(
            fileName = fileName,
            title = "Download non riuscito",
            message = message,
            isWorking = false,
            isError = true,
          )
          lastMessage.value = CommunicationsMessage(
            text = message,
            kind = CommunicationsMessageKind.Error,
          )
        }
      isSubmittingAction.value = false
    }
  }

  fun clearPendingUri() {
    pendingOpenUri.value = null
    if (attachmentDialog.value?.isError != true) {
      attachmentDialog.value = null
    }
  }

  fun reportAttachmentOpenFailure(error: Throwable) {
    val current = attachmentDialog.value
    val message = error.message ?: "Nessuna app disponibile per aprire questo allegato."
    pendingOpenUri.value = null
    attachmentDialog.value = AttachmentDownloadDialogState(
      fileName = current?.fileName ?: "allegato",
      title = "Apertura non riuscita",
      message = message,
      isWorking = false,
      isError = true,
    )
    lastMessage.value = CommunicationsMessage(
      text = message,
      kind = CommunicationsMessageKind.Error,
    )
  }

  fun dismissAttachmentDialog() {
    if (attachmentDialog.value?.isWorking != true) {
      attachmentDialog.value = null
    }
  }

  fun acknowledge(detail: CommunicationDetail) {
    runCommunicationAction(
      successMessage = "Conferma inviata.",
      errorMessage = "Non sono riuscito a confermare la comunicazione.",
    ) {
      communicationsRepository.acknowledgeCommunication(detail)
    }
  }

  fun reply(detail: CommunicationDetail, text: String) {
    runCommunicationAction(
      successMessage = "Risposta inviata.",
      errorMessage = "Non sono riuscito a inviare la risposta.",
    ) {
      communicationsRepository.replyToCommunication(detail, text)
    }
  }

  fun join(detail: CommunicationDetail) {
    runCommunicationAction(
      successMessage = "Adesione registrata.",
      errorMessage = "Non sono riuscito a completare l'adesione.",
    ) {
      communicationsRepository.joinCommunication(detail)
    }
  }

  fun upload(detail: CommunicationDetail, fileName: String, mimeType: String?, bytes: ByteArray) {
    runCommunicationAction(
      successMessage = "File inviato alla comunicazione.",
      errorMessage = "Non sono riuscito a caricare il file richiesto.",
    ) {
      communicationsRepository.uploadCommunicationFile(detail, fileName, mimeType, bytes)
    }
  }

  fun dismissDetail() {
    selectedCommunication.value = null
    selectedNote.value = null
  }

  fun markAllAsRead() {
    viewModelScope.launch {
      communicationsRepository.markAllAsRead()
        .onSuccess {
          lastMessage.value = CommunicationsMessage(
            text = "Tutte le comunicazioni segnate come lette.",
            kind = CommunicationsMessageKind.Success,
          )
        }
        .onFailure {
          lastMessage.value = CommunicationsMessage(
            text = it.message ?: "Errore durante l'operazione.",
            kind = CommunicationsMessageKind.Error,
          )
        }
    }
  }

  fun markCommunicationRead(id: String) {
    viewModelScope.launch {
      communicationsRepository.markCommunicationRead(id)
      // Immediately reflect the new read state in the open bottom sheet.
      val current = selectedCommunication.value
      if (current != null && current.communication.id == id) {
        selectedCommunication.value = current.copy(
          communication = current.communication.copy(read = true),
        )
      }
    }
  }

  fun clearMessage() {
    lastMessage.value = null
  }

  private fun runCommunicationAction(
    successMessage: String,
    errorMessage: String,
    block: suspend () -> Result<CommunicationDetail>,
  ) {
    viewModelScope.launch {
      isSubmittingAction.value = true
      block()
        .onSuccess {
          selectedCommunication.value = it
          lastMessage.value = CommunicationsMessage(
            text = successMessage,
            kind = CommunicationsMessageKind.Success,
          )
        }
        .onFailure {
          lastMessage.value = CommunicationsMessage(
            text = it.message ?: errorMessage,
            kind = CommunicationsMessageKind.Error,
          )
        }
      isSubmittingAction.value = false
    }
  }

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      communicationsRepository.refreshCommunications(force = force)
        .onFailure {
          lastMessage.value = CommunicationsMessage(
            text = it.message ?: "Impossibile aggiornare la bacheca.",
            kind = CommunicationsMessageKind.Error,
          )
        }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationsRoute(
  initialTab: String = "board",
  initialCommunicationPubId: String? = null,
  initialCommunicationEvtCode: String? = null,
  initialNoteId: String? = null,
  initialNoteCategoryCode: String? = null,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  onOpenCommunication: ((pubId: String, evtCode: String) -> Unit)? = null,
  onOpenNote: ((id: String, categoryCode: String) -> Unit)? = null,
  viewModel: CommunicationsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  var selectedTab by rememberSaveable { mutableStateOf(tabFromRoute(initialTab)) }
  var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
  var pendingUploadDetail by remember { mutableStateOf<CommunicationDetail?>(null) }
  val notificationHostState = LocalFluidNotificationHostState.current
  val launchRequest = remember(
    initialTab,
    initialCommunicationPubId,
    initialCommunicationEvtCode,
    initialNoteId,
    initialNoteCategoryCode,
  ) {
    communicationsLaunchRequest(
      initialTab = initialTab,
      communicationPubId = initialCommunicationPubId,
      communicationEvtCode = initialCommunicationEvtCode,
      noteId = initialNoteId,
      noteCategoryCode = initialNoteCategoryCode,
    )
  }
  var launchRequestConsumed by rememberSaveable(launchRequest.stableKey) { mutableStateOf(false) }

  // Route and deep-link arguments are launch intents, not durable UI state. Persisting the consumed
  // bit prevents an already-dismissed detail from reopening after configuration/process restore.
  LaunchedEffect(launchRequest.stableKey, launchRequestConsumed) {
    if (launchRequestConsumed) return@LaunchedEffect
    selectedTab = launchRequest.tab
    when (launchRequest) {
      is CommunicationsLaunchRequest.Communication -> {
        viewModel.openCommunication(launchRequest.pubId, launchRequest.evtCode)
      }
      is CommunicationsLaunchRequest.Note -> {
        viewModel.openNote(launchRequest.id, launchRequest.categoryCode)
      }
      is CommunicationsLaunchRequest.Tab -> Unit
    }
    launchRequestConsumed = true
  }

  LaunchedEffect(state.lastMessage) {
    val feedback = state.lastMessage
    if (feedback != null) {
      if (feedback.text.isNotBlank()) {
        notificationHostState?.show(
          FluidNotification(
            id = "communications:${feedback.kind}:${feedback.text.hashCode()}",
            title = when (feedback.kind) {
              CommunicationsMessageKind.Error -> "Problema nelle comunicazioni"
              CommunicationsMessageKind.Success -> "Comunicazioni aggiornate"
              CommunicationsMessageKind.Info -> "Comunicazioni"
            },
            message = feedback.text,
            tone = feedback.kind.toFluidNotificationTone(),
            durationMillis = if (feedback.kind == CommunicationsMessageKind.Error) 7_000L else 4_500L,
          ),
        )
      }
      viewModel.clearMessage()
    }
  }

  LaunchedEffect(state.pendingOpenUri) {
    val uri = state.pendingOpenUri ?: return@LaunchedEffect
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
      context.startActivity(Intent.createChooser(intent, "Apri allegato"))
    }.onSuccess {
      viewModel.clearPendingUri()
    }.onFailure { error ->
      viewModel.reportAttachmentOpenFailure(error)
    }
  }

  val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    val detail = pendingUploadDetail ?: return@rememberLauncherForActivityResult
    pendingUploadDetail = null
    val picked = uri?.let { readPickedDocument(context, it) }
    if (picked != null) {
      viewModel.upload(
        detail = detail,
        fileName = picked.fileName,
        mimeType = picked.mimeType,
        bytes = picked.bytes,
      )
    }
  }

  val filteredCommunications = remember(state.communications, selectedFilter) {
    state.communications.filter { communication ->
      when (selectedFilter) {
        FILTER_UNREAD -> !communication.read
        else -> true
      }
    }
  }
  val totalUnreadCount = remember(state.communications) { state.communications.count { !it.read } }
  val boardSections = remember(filteredCommunications) {
    archiveMonthSections(filteredCommunications, Communication::date)
  }
  val noteSections = remember(state.notes) {
    archiveMonthSections(state.notes, Note::date)
  }
  val listState = rememberLazyListState()
  val listScope = rememberCoroutineScope()
  val railSettleOffsetPx = with(LocalDensity.current) { 10.dp.roundToPx() }
  val sectionAnchors = remember(selectedTab, boardSections, noteSections, selectedFilter) {
    if (selectedTab == TAB_BOARD) {
      communicationSectionAnchors(
        sections = boardSections,
        includeMarkAllReadAction = filteredCommunications.any { !it.read },
        includeUnreadAnchor = selectedFilter == FILTER_ALL,
      )
    } else {
      noteSectionAnchors(noteSections)
    }
  }
  val activeSectionKey by remember(listState, sectionAnchors) {
    derivedStateOf {
      sectionAnchors.lastOrNull { it.itemIndex <= listState.firstVisibleItemIndex }?.key
        ?: sectionAnchors.firstOrNull()?.key
    }
  }

  val titleFacets = remember(state.communications, state.notes) {
    buildCommunicationsFacets(state.communications, state.notes)
  }

  Box(modifier = modifier) {
    FluidScreen(
    modifier = Modifier.fillMaxWidth(),
    title = "Comunicazioni",
    ambient = FeatureIdentity.Communications.ambient(),
    subtitle = state.syncStatus.lastSyncLabel(),
    titleFacets = titleFacets,
    onBack = onBack,
    actions = {
      FluidSyncAction(status = state.syncStatus.toFluid(), onRetry = viewModel::refresh)
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 18.dp,
    listState = listState,
    overlay = { backdrop ->
      FluidSectionIndex(
        sections = sectionAnchors,
        activeSectionKey = activeSectionKey,
        visible = sectionAnchors.size >= 3 &&
          (listState.canScrollBackward || listState.canScrollForward),
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.CenterEnd),
        onSelectSection = { anchor, motion ->
          listScope.launch {
            listState.stopScroll()
            val currentIndex = listState.firstVisibleItemIndex
            val nearby = kotlin.math.abs(anchor.itemIndex - currentIndex) <= 8
            if (motion == FluidSectionSelectionMotion.Animated && nearby) {
              listState.animateScrollToItem(anchor.itemIndex)
            } else {
              listState.scrollToItem(anchor.itemIndex, railSettleOffsetPx)
              if (motion == FluidSectionSelectionMotion.Animated) {
                listState.animateScrollToItem(anchor.itemIndex)
              }
            }
          }
        },
      )
    },
  ) {
    // Whatever the sync could not deliver, said where the missing data would have been. Reserved
    // only when there is something to say, so an ordinary page keeps its first item at the top.
    if (state.syncStatus.noticeMessage() != null) {
      item {
        FluidSyncNotice(status = state.syncStatus.toFluid(), onRetry = viewModel::refresh)
      }
    }
    item {
      FeatureHero(
        identity = FeatureIdentity.Communications,
        eyebrow = "Centro messaggi",
        value = totalUnreadCount.toString(),
        title = if (totalUnreadCount == 1) "comunicazione da leggere" else "comunicazioni da leggere",
        description = if (totalUnreadCount == 0) {
          "Hai letto tutti gli avvisi disponibili; note e circolari restano raccolte qui."
        } else {
          "Dai priorità ai nuovi avvisi senza perdere allegati, richieste e annotazioni."
        },
        icon = if (totalUnreadCount > 0) Icons.Rounded.MarkEmailUnread else Icons.Rounded.Forum,
        metrics = listOf(
          FeatureHeroMetric("Circolari", state.communications.size.toString()),
          FeatureHeroMetric("Note", state.notes.size.toString()),
        ),
      )
    }
    item {
      FluidPillTabs(
        options = listOf(TAB_BOARD, TAB_NOTES),
        selected = selectedTab,
        onSelect = { selectedTab = it },
      )
    }
    if (selectedTab == TAB_BOARD) {
      val unreadCount = filteredCommunications.count { !it.read }
      item {
        FluidPillTabs(
          options = listOf(FILTER_ALL, FILTER_UNREAD),
          selected = selectedFilter,
          onSelect = { selectedFilter = it },
        )
      }
      if (unreadCount > 0) {
          item {
              dev.antigravity.fluidengine.ui.theme.FluidQuickAction(
                  label = "Segna tutte come lette",
                  onClick = viewModel::markAllAsRead
              )
          }
      }
      if (filteredCommunications.isEmpty()) {
        item {
          FluidEmptyState(
            title = "Nessuna comunicazione visibile",
            detail = "Nuove circolari e messaggi compariranno qui con stato di lettura, allegati e azioni richieste.",
          )
        }
      } else {
        boardSections.forEach { section ->
          item(
            key = "communication-month:${section.key}",
            contentType = "archive-month-header",
          ) {
            FluidSectionHeader(section.label)
          }
          items(
            items = section.items,
            key = { "communication:${it.id}" },
            contentType = { "communication-row" },
          ) { communication ->
            FluidListRow(
              modifier = Modifier
                .animateItem(),
              title = communication.title,
              subtitle = communication.sender.ifBlank { "Bacheca scuola" },
              eyebrow = communication.date.toReadableDate(),
              meta = communication.contentPreview.takeIf { it.isNotBlank() },
              tone = communicationTone(communication),
              leading = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
              badge = {
                FluidStatusBadge(
                  label = communicationBadgeLabel(communication),
                  tone = communicationTone(communication),
                )
              },
              onClick = {
                if (onOpenCommunication != null) {
                  onOpenCommunication(communication.pubId, communication.evtCode)
                } else {
                  viewModel.openCommunication(communication.pubId, communication.evtCode)
                }
              },
              animatePress = true,
            )
          }
        }
      }
    } else {
      if (state.notes.isEmpty()) {
        item {
          FluidEmptyState(
            title = "Nessuna nota disponibile",
            detail = "Note disciplinari, annotazioni e richiami compariranno qui in forma sintetica e chiara.",
          )
        }
      } else {
        noteSections.forEach { section ->
          item(
            key = "note-month:${section.key}",
            contentType = "archive-month-header",
          ) {
            FluidSectionHeader(section.label)
          }
          items(
            items = section.items,
            key = { "note:${it.id}" },
            contentType = { "note-row" },
          ) { note ->
            FluidListRow(
              modifier = Modifier
                .animateItem(),
              title = note.title.ifBlank { note.author.uppercase(italianLocale) },
              subtitle = note.categoryLabel,
              eyebrow = note.date.toReadableDate(),
              meta = note.contentPreview.takeIf { it.isNotBlank() },
              tone = noteTone(note),
              leading = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
              badge = {
                FluidStatusBadge(
                  label = if (note.read) "LETTA" else "NOTA",
                  tone = noteTone(note),
                )
              },
              onClick = {
                if (onOpenNote != null) {
                  onOpenNote(note.id, note.categoryCode)
                } else {
                  viewModel.openNote(note.id, note.categoryCode)
                }
              },
              animatePress = true,
            )
          }
        }
      }
    }
  }
  }

  if (onOpenCommunication == null) state.selectedCommunication?.let { detail ->
    var replyDraft by rememberSaveable(detail.communication.id, detail.replyText) {
      mutableStateOf(detail.replyText.orEmpty())
    }
    val commSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    FluidSheet(
      onDismissRequest = viewModel::dismissDetail,
      sheetState = commSheetState,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Text(
            text = detail.communication.title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
          )
        }
        item {
          val formattedDate = runCatching {
            val parsed = LocalDate.parse(detail.communication.date)
            parsed.format(DateTimeFormatter.ofPattern("d MMMM yyyy", italianLocale))
          }.getOrDefault(detail.communication.date)
          androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = detail.communication.sender,
              style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
            Text(
              text = formattedDate,
              style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            if (!detail.communication.category.isNullOrBlank()) {
              FluidStatusBadge(label = detail.communication.category!!, tone = FluidTone.Info)
            }
            if (!detail.communication.read) {
              FluidButton(
                text = "Segna come letta",
                onClick = {
                  viewModel.markCommunicationRead(detail.communication.id)
                },
                style = FluidButtonStyle.Tinted,
                fillWidth = true,
              )
            }
          }
        }
        item {
          val rendered = remember(detail.content, detail.communication.title) {
            renderCommunicationContent(detail.content, detail.communication.title)
          }
          if (rendered.isBlank()) {
            Text(
              text = "Nessun contenuto fornito dal registro per questa comunicazione.",
              style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
              color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(text = rendered)
          }
        }
        val canReply = shouldShowReplyComposer(detail)
        if (canReply) {
          item { FluidSectionHeader("Risposta") }
          item {
            FluidTextField(
              value = replyDraft,
              onValueChange = { replyDraft = it },
              modifier = Modifier.fillMaxWidth(),
              label = if (detail.replyText != null) "Risposta inviata" else "Scrivi una risposta",
              readOnly = detail.replyText != null,
              minLines = 3,
            )
          }
        }
        if (detail.communication.noticeboardAttachments.isNotEmpty()) {
          item { FluidSectionHeader("Allegati") }
          items(detail.communication.noticeboardAttachments, key = { it.id }) { attachment ->
            // Da v5.6.0: usiamo SEMPRE il path auth-aware (RestClient con
            // refresh automatico del token). Il vecchio downloadAttachment
            // usava DownloadManager che spesso falliva perche' non riceveva
            // l'header Z-Auth-Token aggiornato.
            val hasUrl = !attachment.url.isNullOrBlank()
            FluidListRow(
              title = attachment.name,
              subtitle = attachment.mimeType ?: "Allegato",
              meta = when {
                !hasUrl -> "Allegato non disponibile in API"
                attachment.portalOnly -> "Tocca per aprire. Se manca, lo scarico e lo tengo per 30 giorni"
                else -> "Tocca per aprire. Se è già in memoria non riscarico nulla"
              },
              tone = FluidTone.Neutral,
              badge = {
                Icon(Icons.Rounded.AttachFile, contentDescription = null)
                if (hasUrl) {
                  FluidStatusBadge("CACHE 30G", tone = FluidTone.Info)
                }
              },
              onClick = if (hasUrl) {
                {
                  viewModel.openAttachment(
                    RemoteAttachment(
                      id = attachment.id,
                      name = attachment.name,
                      url = attachment.url,
                      mimeType = attachment.mimeType,
                      portalOnly = attachment.portalOnly,
                    ),
                    context,
                  )
                }
              } else {
                null
              },
              animatePress = true,
            )
          }
        }
        item {
          if (state.isSubmittingAction) {
            FluidLoading()
          } else {
            CommunicationActions(
              detail = detail,
              canReply = canReply,
              replyDraft = replyDraft,
              onAcknowledge = { viewModel.acknowledge(detail) },
              onReply = { viewModel.reply(detail, replyDraft) },
              onJoin = { viewModel.join(detail) },
              onUpload = {
                pendingUploadDetail = detail
                uploadLauncher.launch(arrayOf("*/*"))
              },
            )
          }
        }
        item {
          FluidButton(
            text = "Chiudi",
            onClick = viewModel::dismissDetail,
            style = FluidButtonStyle.Filled,
            fillWidth = true,
          )
        }
      }
    }
  }

  if (onOpenNote == null) state.selectedNote?.let { detail ->
    FluidSheet(
      onDismissRequest = viewModel::dismissDetail,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Text(
            text = detail.note.title.ifBlank { detail.note.categoryLabel },
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
          )
        }
        item { Text(detail.content) }
        item {
          FluidButton(
            text = "Chiudi",
            onClick = viewModel::dismissDetail,
            style = FluidButtonStyle.Filled,
            fillWidth = true,
          )
        }
      }
    }
  }

  state.attachmentDialog?.let { dialog ->
    AttachmentDownloadDialog(
      state = dialog,
      onDismiss = viewModel::dismissAttachmentDialog,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationDetailRoute(
  pubId: String,
  evtCode: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CommunicationsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  val base = remember(state.communications, pubId, evtCode) {
    state.communications.firstOrNull { it.pubId == pubId && it.evtCode == evtCode }
  }
  val detail = state.selectedCommunication
    ?.takeIf { it.communication.pubId == pubId && it.communication.evtCode == evtCode }
  val communication = detail?.communication ?: base
  var showActions by rememberSaveable(pubId, evtCode) { mutableStateOf(false) }
  var pendingUploadDetail by remember { mutableStateOf<CommunicationDetail?>(null) }

  LaunchedEffect(pubId, evtCode) {
    viewModel.openCommunication(pubId, evtCode)
  }
  DisposableEffect(viewModel, pubId, evtCode) {
    onDispose { viewModel.dismissDetail() }
  }
  LaunchedEffect(state.pendingOpenUri) {
    val uri = state.pendingOpenUri ?: return@LaunchedEffect
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Apri allegato")) }
      .onSuccess { viewModel.clearPendingUri() }
      .onFailure(viewModel::reportAttachmentOpenFailure)
  }

  val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    val selected = pendingUploadDetail ?: return@rememberLauncherForActivityResult
    pendingUploadDetail = null
    uri?.let { readPickedDocument(context, it) }?.let { picked ->
      viewModel.upload(selected, picked.fileName, picked.mimeType, picked.bytes)
    }
  }

  if (communication == null) {
    FluidScreen(
      title = "Dettaglio comunicazione",
      modifier = modifier,
      onBack = onBack,
    ) {
      item(key = "communication-detail-missing") {
        FluidEmptyState(
          title = "Comunicazione non disponibile",
          detail = "Il messaggio potrebbe essere stato rimosso o non ancora sincronizzato.",
        )
      }
    }
    return
  }

  FluidContainerScaffold(
    title = "Dettaglio comunicazione",
    modifier = modifier,
    onBack = onBack,
    hero = {
      FluidListRow(
        title = communication.title,
        subtitle = communication.sender.ifBlank { "Bacheca scuola" },
        eyebrow = communication.date.toReadableDate(),
        meta = communication.contentPreview.takeIf(String::isNotBlank),
        tone = communicationTone(communication),
        leading = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
        badge = {
          FluidStatusBadge(
            label = communicationBadgeLabel(communication),
            tone = communicationTone(communication),
          )
        },
        animatePress = false,
      )
    },
    secondary = {
      val rendered = remember(detail?.content, communication.title, communication.contentPreview) {
        renderCommunicationContent(detail?.content ?: communication.contentPreview, communication.title)
      }
      Text(
        text = rendered.ifBlank { "Nessun contenuto fornito dal registro per questa comunicazione." },
        style = MaterialTheme.typography.bodyLarge,
        color = if (rendered.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
      )
      if (detail == null) {
        FluidLoading()
      }
      if (communication.noticeboardAttachments.isNotEmpty()) {
        FluidSectionHeader("Allegati")
        communication.noticeboardAttachments.forEach { attachment ->
          val hasUrl = !attachment.url.isNullOrBlank()
          FluidListRow(
            title = attachment.name,
            subtitle = attachment.mimeType ?: "Allegato",
            meta = if (hasUrl) "Apri con download autenticato e cache locale" else "Non disponibile in API",
            tone = FluidTone.Neutral,
            leading = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
            onClick = if (hasUrl) {
              {
                viewModel.openAttachment(
                  RemoteAttachment(
                    id = attachment.id,
                    name = attachment.name,
                    url = attachment.url,
                    mimeType = attachment.mimeType,
                    portalOnly = attachment.portalOnly,
                  ),
                  context,
                )
              }
            } else null,
          )
        }
      }
      if (detail != null && detail.hasTransactionalActions()) {
        FluidButton(
          text = "Azioni comunicazione",
          onClick = { showActions = true },
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
        )
      }
    },
  )

  if (showActions && detail != null) {
    var replyDraft by rememberSaveable(detail.communication.id, detail.replyText) {
      mutableStateOf(detail.replyText.orEmpty())
    }
    FluidSheet(onDismissRequest = { showActions = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text("Azioni comunicazione", style = MaterialTheme.typography.headlineSmall)
        if (!detail.communication.read) {
          FluidButton(
            text = "Segna come letta",
            onClick = { viewModel.markCommunicationRead(detail.communication.id) },
            style = FluidButtonStyle.Tinted,
            fillWidth = true,
          )
        }
        val canReply = shouldShowReplyComposer(detail)
        if (canReply) {
          FluidTextField(
            value = replyDraft,
            onValueChange = { replyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = if (detail.replyText != null) "Risposta inviata" else "Scrivi una risposta",
            readOnly = detail.replyText != null,
            minLines = 3,
          )
        }
        if (state.isSubmittingAction) {
          FluidLoading()
        } else {
          CommunicationActions(
            detail = detail,
            canReply = canReply,
            replyDraft = replyDraft,
            onAcknowledge = { viewModel.acknowledge(detail) },
            onReply = { viewModel.reply(detail, replyDraft) },
            onJoin = { viewModel.join(detail) },
            onUpload = {
              pendingUploadDetail = detail
              uploadLauncher.launch(arrayOf("*/*"))
            },
          )
        }
        FluidButton(
          text = "Chiudi",
          onClick = { showActions = false },
          style = FluidButtonStyle.Plain,
          fillWidth = true,
        )
      }
    }
  }

  state.attachmentDialog?.let { dialog ->
    AttachmentDownloadDialog(dialog, viewModel::dismissAttachmentDialog)
  }
}

@Composable
fun NoteDetailRoute(
  id: String,
  categoryCode: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CommunicationsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val base = remember(state.notes, id, categoryCode) {
    state.notes.firstOrNull { it.id == id && it.categoryCode == categoryCode }
  }
  val detail = state.selectedNote
    ?.takeIf { it.note.id == id && it.note.categoryCode == categoryCode }
  val note = detail?.note ?: base

  LaunchedEffect(id, categoryCode) { viewModel.openNote(id, categoryCode) }
  DisposableEffect(viewModel, id, categoryCode) {
    onDispose { viewModel.dismissDetail() }
  }

  if (note == null) {
    FluidScreen(title = "Dettaglio nota", modifier = modifier, onBack = onBack) {
      item(key = "note-detail-missing") {
        FluidEmptyState(
          title = "Nota non disponibile",
          detail = "La nota potrebbe essere stata rimossa o non ancora sincronizzata.",
        )
      }
    }
    return
  }

  FluidContainerScaffold(
    title = "Dettaglio nota",
    modifier = modifier,
    onBack = onBack,
    hero = {
      FluidListRow(
        title = note.title.ifBlank { note.author.uppercase(italianLocale) },
        subtitle = note.categoryLabel,
        eyebrow = note.date.toReadableDate(),
        meta = note.contentPreview.takeIf(String::isNotBlank),
        tone = noteTone(note),
        leading = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
        badge = { FluidStatusBadge(if (note.read) "LETTA" else "NOTA", tone = noteTone(note)) },
        animatePress = false,
      )
    },
    secondary = {
      if (detail == null) FluidLoading()
      Text(
        text = detail?.content?.takeIf(String::isNotBlank)
          ?: note.contentPreview.takeIf(String::isNotBlank)
          ?: "Nessun dettaglio disponibile.",
        style = MaterialTheme.typography.bodyLarge,
      )
    },
  )
}

private fun CommunicationDetail.hasTransactionalActions(): Boolean =
  !communication.read ||
    shouldShowAcknowledgeAction(this) ||
    shouldShowReplyComposer(this) ||
    shouldShowJoinAction(this) ||
    shouldShowUploadAction(this)

@Composable
private fun AttachmentDownloadDialog(
  state: AttachmentDownloadDialogState,
  onDismiss: () -> Unit,
) {
  FluidAlert(
    onDismissRequest = { if (!state.isWorking) onDismiss() },
    title = state.title,
    actions = listOf(
      FluidAlertAction(if (state.isError) "Chiudi" else "Ok", onDismiss, FluidAlertAction.Emphasis.Preferred, enabled = !state.isWorking),
    ),
    content = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = state.fileName,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(state.message)
        if (state.isWorking) {
          FluidIndeterminateBar(modifier = Modifier.fillMaxWidth())
        }
      }
    },
  )
}

@Composable
private fun CommunicationActions(
  detail: CommunicationDetail,
  canReply: Boolean,
  replyDraft: String,
  onAcknowledge: () -> Unit,
  onReply: () -> Unit,
  onJoin: () -> Unit,
  onUpload: () -> Unit,
) {
  val canAck = shouldShowAcknowledgeAction(detail)
  val canJoin = shouldShowJoinAction(detail)
  val canUpload = shouldShowUploadAction(detail)

  androidx.compose.foundation.layout.Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (canAck) {
      val ackLabel = "Conferma lettura"
      FluidButton(
        text = ackLabel,
        onClick = onAcknowledge,
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    }
    if (canReply) {
      FluidButton(
        text = if (detail.replyText != null) "Risposta già inviata" else "Invia risposta",
        onClick = onReply,
        modifier = Modifier.fillMaxWidth(),
        style = FluidButtonStyle.Tinted,
        enabled = replyDraft.isNotBlank() && detail.replyText == null,
        fillWidth = true,
      )
    }
    if (canJoin) {
      FluidButton(
        text = "Aderisci",
        onClick = onJoin,
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    }
    if (canUpload) {
      val uploadLabel = detail.actions.firstOrNull { it.type == NoticeboardActionType.UPLOAD }?.label
        ?: "Carica file"
      FluidButton(
        text = uploadLabel,
        onClick = onUpload,
        style = FluidButtonStyle.Plain,
        fillWidth = true,
      )
    }
  }
}

internal fun shouldShowAcknowledgeAction(detail: CommunicationDetail): Boolean {
  if (detail.communication.read) return false
  val parsedTypes = detail.actions.map { it.type }.toSet()
  return NoticeboardActionType.ACKNOWLEDGE in parsedTypes ||
    detail.communication.needsAck ||
    !detail.acknowledgeUrl.isNullOrBlank()
}

internal fun shouldShowReplyComposer(detail: CommunicationDetail): Boolean {
  if (detail.replyText != null) return true
  if (!detail.replyUrl.isNullOrBlank()) return true
  return !detail.portalDetailUrl.isNullOrBlank() && (
    detail.actions.any { it.type == NoticeboardActionType.REPLY } ||
      detail.communication.needsReply ||
      detectsReplyIntent(detail)
    )
}

internal fun shouldShowJoinAction(detail: CommunicationDetail): Boolean {
  if (!detail.joinUrl.isNullOrBlank()) return true
  return detail.actions.any { it.type == NoticeboardActionType.JOIN } ||
    detail.communication.needsJoin ||
    (!detail.portalDetailUrl.isNullOrBlank() && detectsJoinIntent(detail))
}

internal fun shouldShowUploadAction(detail: CommunicationDetail): Boolean {
  if (!detail.fileUploadUrl.isNullOrBlank()) return true
  return !detail.portalDetailUrl.isNullOrBlank() && (
    detail.actions.any { it.type == NoticeboardActionType.UPLOAD } ||
      detail.communication.needsFile ||
      detectsUploadIntent(detail)
    )
}

private fun detailActionHaystack(detail: CommunicationDetail): String {
  return (detail.communication.title + " " + detail.content + " " +
    (detail.communication.category ?: ""))
    .lowercase(italianLocale)
}

private fun containsActionKeywords(haystack: String, keywords: List<String>): Boolean {
  return keywords.any { haystack.contains(it) }
}

private fun detectsReplyIntent(detail: CommunicationDetail): Boolean {
  val haystack = detailActionHaystack(detail)
  return listOf(
    "rispost",
    "rispond",
    "questionar",
    "feedback",
    "motivazion",
  ).any { haystack.contains(it) }
}

private fun detectsJoinIntent(detail: CommunicationDetail): Boolean {
  return containsActionKeywords(
    detailActionHaystack(detail),
    listOf(
      "adesion",
      "aderisc",
      "partecip",
      "consenso",
      "autorizzazion",
      "prenot",
    ),
  )
}

private fun detectsUploadIntent(detail: CommunicationDetail): Boolean {
  return containsActionKeywords(
    detailActionHaystack(detail),
    listOf(
      "alleg",
      "upload",
      "caric",
      "modul firm",
      "pdf",
      "file",
    ),
  )
}

internal fun communicationTone(communication: Communication): FluidTone {
  return when {
    !communication.read -> FluidTone.Danger
    communication.actions.isNotEmpty() -> FluidTone.Warning
    else -> FluidTone.Neutral
  }
}

internal fun communicationBadgeLabel(communication: Communication): String {
  return when {
    !communication.read -> "NUOVA"
    communication.actions.isNotEmpty() -> "AZIONE"
    else -> "LETTA"
  }
}

private fun noteTone(note: Note): FluidTone {
  val normalized = note.severity.uppercase(italianLocale)
  return when {
    normalized.contains("HIGH") || normalized.contains("GRAVE") || normalized.contains("CRIT") -> FluidTone.Danger
    normalized.contains("MED") || normalized.contains("WARN") -> FluidTone.Warning
    else -> FluidTone.Neutral
  }
}

private data class PickedDocument(
  val fileName: String,
  val mimeType: String?,
  val bytes: ByteArray,
)

private fun readPickedDocument(context: Context, uri: Uri): PickedDocument? {
  val mimeType = context.contentResolver.getType(uri)
  val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor ->
      val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
    ?: "allegato"
  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
  return PickedDocument(fileName = fileName, mimeType = mimeType, bytes = bytes)
}

private fun String.toReadableDate(): String {
  val parsed = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
  return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale))
}

private fun tabFromRoute(value: String): String = if (value.equals("notes", ignoreCase = true)) TAB_NOTES else TAB_BOARD

internal sealed interface CommunicationsLaunchRequest {
  val tab: String
  val stableKey: String

  data class Tab(override val tab: String) : CommunicationsLaunchRequest {
    override val stableKey: String = "tab:$tab"
  }

  data class Communication(
    val pubId: String,
    val evtCode: String,
  ) : CommunicationsLaunchRequest {
    override val tab: String = TAB_BOARD
    override val stableKey: String = "communication:$pubId:$evtCode"
  }

  data class Note(
    val id: String,
    val categoryCode: String,
  ) : CommunicationsLaunchRequest {
    override val tab: String = TAB_NOTES
    override val stableKey: String = "note:$id:$categoryCode"
  }
}

internal fun communicationsLaunchRequest(
  initialTab: String,
  communicationPubId: String?,
  communicationEvtCode: String?,
  noteId: String?,
  noteCategoryCode: String?,
): CommunicationsLaunchRequest = when {
  !communicationPubId.isNullOrBlank() && !communicationEvtCode.isNullOrBlank() -> {
    CommunicationsLaunchRequest.Communication(communicationPubId, communicationEvtCode)
  }
  !noteId.isNullOrBlank() && !noteCategoryCode.isNullOrBlank() -> {
    CommunicationsLaunchRequest.Note(noteId, noteCategoryCode)
  }
  else -> CommunicationsLaunchRequest.Tab(tabFromRoute(initialTab))
}

internal fun renderCommunicationContent(rawContent: String?, title: String): String {
  val raw = rawContent?.trim().orEmpty()
  if (raw.isEmpty()) return ""
  // Se sembra HTML, estrai il testo via JSoup preservando i ritorni a capo dei <br> e dei <p>.
  val plain = if (raw.contains('<') && raw.contains('>')) {
    runCatching {
      val doc = org.jsoup.Jsoup.parse(raw)
      doc.outputSettings().prettyPrint(false)
      val singleBreak = "[[CVEX_BR_1]]"
      val doubleBreak = "[[CVEX_BR_2]]"
      doc.select("br").after(singleBreak)
      doc.select("p, div, li, tr").after(doubleBreak)
      doc.text()
        .replace(doubleBreak, "\n\n")
        .replace(singleBreak, "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    }.getOrDefault(raw)
  } else {
    raw
  }
  // Strip del titolo duplicato in cima al body (case-insensitive, anche se segue una nuova riga).
  val normalizedTitle = title.trim()
  val withoutDuplicateTitle = if (normalizedTitle.isNotEmpty() && plain.length >= normalizedTitle.length) {
    val head = plain.take(normalizedTitle.length)
    if (head.equals(normalizedTitle, ignoreCase = true)) {
      plain.substring(normalizedTitle.length).trimStart('\n', '\r', ' ', '\t', ':', '-', '·')
    } else {
      plain
    }
  } else {
    plain
  }
  return withoutDuplicateTitle.trim().ifBlank { plain.trim() }
}
