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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.fluidGlassGroups
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
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidIndeterminateBar
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.fluid.FluidSectionSelectionMotion
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
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

  /**
   * Firma dal menu contestuale, senza aprire il dettaglio.
   *
   * La conferma vuole il [CommunicationDetail] (porta con se' l'URL di acknowledge), che la riga
   * di lista non ha: lo si recupera e si firma in un gesto solo. Il dettaglio NON viene mostrato —
   * chi voleva leggere avrebbe toccato la riga.
   */
  fun acknowledgeFromList(pubId: String, evtCode: String) {
    viewModelScope.launch {
      isSubmittingAction.value = true
      communicationsRepository.getCommunicationDetail(pubId, evtCode)
        .onSuccess { detail ->
          communicationsRepository.acknowledgeCommunication(detail)
            .onSuccess {
              lastMessage.value = CommunicationsMessage(
                text = "Conferma inviata.",
                kind = CommunicationsMessageKind.Success,
              )
            }
            .onFailure {
              lastMessage.value = CommunicationsMessage(
                text = it.message ?: "Non sono riuscito a confermare la comunicazione.",
                kind = CommunicationsMessageKind.Error,
              )
            }
        }
        .onFailure {
          lastMessage.value = CommunicationsMessage(
            text = it.message ?: "Non sono riuscito a confermare la comunicazione.",
            kind = CommunicationsMessageKind.Error,
          )
        }
      isSubmittingAction.value = false
    }
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
  viewModel: CommunicationsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  var selectedTab by rememberSaveable { mutableStateOf(tabFromRoute(initialTab)) }
  var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
  var pendingUploadDetail by remember { mutableStateOf<CommunicationDetail?>(null) }
  // Il rettangolo della riga toccata, in coordinate radice: e' da li' che il pop-up si espande.
  // Un deep link non passa da nessuna riga e lo lascia null, e il modale apre dal centro.
  var detailOrigin by remember { mutableStateOf<Rect?>(null) }
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
    // Aprire, non condividere.
    //
    // Erano due errori sovrapposti. Senza tipo MIME esplicito il sistema deve indovinarlo dal
    // provider, e quando non ci riesce offre *tutto* — compresi i bersagli di condivisione. E il
    // chooser, anche su ACTION_VIEW, e' il foglio che su Android si e' imparato a leggere come
    // "condividi con": si tocca un PDF e si finisce a scegliere a chi mandarlo.
    //
    // Col tipo dichiarato e senza chooser, l'allegato si apre e basta, nel lettore predefinito. Il
    // chooser resta come ripiego per l'unico caso in cui serve davvero: nessuna app registrata per
    // quel tipo.
    val resolvedType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
      ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, resolvedType)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
      context.startActivity(intent)
    }.recoverCatching {
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
          // Un mese e' un pannello, non venti pannelli.
          //
          // Le righe erano `items()` separate e senza sfondo: testo appoggiato sulla pagina, che
          // sopra un fondale colorato non e' piu' una lista ma un elenco che galleggia. Il vetro
          // pero' va sul contenitore — venti superfici di vetro alte 80 dp costano venti
          // registrazioni di layer per un effetto che nessuno distingue da una — quindi il gruppo
          // e' un item solo e il mese intero ci sta dentro.
          //
          // Il prezzo e' `animateItem()`, che funziona solo su un item diretto della lista: le
          // righe non si riordinano piu' con un'animazione propria. Nessuna di queste liste
          // riordina niente mentre la guardi; arrivano gia' ordinate dal database.
          fluidGlassGroups(
            items = section.items,
            key = "communication-month-group:${section.key}",
          ) { communication ->
            var rowBounds by remember { mutableStateOf<Rect?>(null) }
            FluidListRow(
              // La riga smette di disegnarsi mentre il suo dettaglio e' aperto: il pannello che ne
              // esce e' vetro, e la riga si vedeva attraverso — due bordi uno dentro l'altro e il
              // titolo leggibile due volte.
              modifier = Modifier.fluidExpandOrigin(
                open = {
                  state.selectedCommunication?.communication?.let {
                    it.pubId == communication.pubId && it.evtCode == communication.evtCode
                  } == true
                },
              ) { rowBounds = it },
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
                detailOrigin = rowBounds
                viewModel.openCommunication(communication.pubId, communication.evtCode)
              },
              // La pressione lunga dice cosa sa fare: aprire, firmare senza aprire (quando il
              // registro chiede la conferma), andare dritti agli allegati, segnare come letta,
              // condividere. Le voci condizionali compaiono solo quando l'azione esiste davvero:
              // un menu che promette cose finte e' peggio di un menu corto.
              contextActions = {
                buildList {
                  add(
                    FluidContextAction(
                      label = "Apri",
                      icon = Icons.Rounded.Campaign,
                      onClick = {
                        detailOrigin = rowBounds
                        viewModel.openCommunication(communication.pubId, communication.evtCode)
                      },
                    ),
                  )
                  if (communication.needsAck) {
                    add(
                      FluidContextAction(
                        label = "Firma per confermare",
                        icon = Icons.Rounded.Draw,
                        onClick = {
                          viewModel.acknowledgeFromList(communication.pubId, communication.evtCode)
                        },
                      ),
                    )
                  }
                  // Direttamente l'allegato, non il dettaglio che lo contiene: una voce "Apri
                  // allegato" che apriva il pop-up completo era una promessa non mantenuta. Con
                  // piu' allegati, una voce ciascuno — col nome, cosi' si sceglie dal menu stesso.
                  val openableAttachments = communication.noticeboardAttachments
                    .filter { !it.url.isNullOrBlank() }
                  openableAttachments.take(4).forEach { attachment ->
                    add(
                      FluidContextAction(
                        label = if (openableAttachments.size == 1) {
                          "Apri allegato"
                        } else {
                          "Apri ${attachment.name.ifBlank { "allegato" }}"
                        },
                        icon = Icons.Rounded.AttachFile,
                        onClick = {
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
                        },
                      ),
                    )
                  }
                  if (!communication.read) {
                    add(
                      FluidContextAction(
                        label = "Segna come letta",
                        icon = Icons.Rounded.MarkEmailRead,
                        onClick = { viewModel.markCommunicationRead(communication.id) },
                      ),
                    )
                  }
                  add(
                    FluidContextAction(
                      label = "Condividi",
                      icon = Icons.Rounded.Share,
                      onClick = { shareCommunication(context, communication) },
                    ),
                  )
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
          fluidGlassGroups(
            items = section.items,
            key = "note-month-group:${section.key}",
          ) { note ->
            var rowBounds by remember { mutableStateOf<Rect?>(null) }
            FluidListRow(
              modifier = Modifier.fluidExpandOrigin(
                open = { state.selectedNote?.note?.id == note.id },
              ) { rowBounds = it },
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
                detailOrigin = rowBounds
                viewModel.openNote(note.id, note.categoryCode)
              },
              contextActions = {
                listOf(
                  FluidContextAction(
                    label = "Apri",
                    icon = Icons.Rounded.Gavel,
                    onClick = {
                      detailOrigin = rowBounds
                      viewModel.openNote(note.id, note.categoryCode)
                    },
                  ),
                  FluidContextAction(
                    label = "Condividi",
                    icon = Icons.Rounded.Share,
                    onClick = { shareNote(context, note) },
                  ),
                )
              },
              animatePress = true,
            )
          }

        }
      }
    }
  }
  }

  // Il dettaglio esisteva tre volte — una rotta, uno sheet inline, un terzo sheet per le azioni.
  // Ora e' questo portale e basta: dichiarato qui, accanto allo stato che legge, e disegnato alla
  // radice dell'app sopra la tab bar, dove il vetro campiona davvero la pagina.
  //
  // La variante con `item` e' quella che sopravvive alla propria chiusura: e' il portale a
  // conservare l'ultimo dettaglio non-null e a ripassarlo a questa lambda per tutta l'uscita.
  // Catturare il valore in proprio non bastava — la lambda e' un contenitore mutabile e al primo
  // frame della chiusura le sue catture erano gia' null, quindi il testo spariva di colpo mentre lo
  // scrim restava a sfumare da solo.
  FluidGlassModalPortal(
    item = state.selectedCommunication,
    onDismissRequest = viewModel::dismissDetail,
    origin = { detailOrigin },
    paneTitle = "Dettaglio comunicazione",
  ) { detail ->
    CommunicationDetailContent(
      detail = detail,
      isSubmittingAction = state.isSubmittingAction,
      viewModel = viewModel,
      context = context,
      onUpload = { current ->
        pendingUploadDetail = current
        uploadLauncher.launch(arrayOf("*/*"))
      },
    )
  }

  FluidGlassModalPortal(
    item = state.selectedNote,
    onDismissRequest = viewModel::dismissDetail,
    origin = { detailOrigin },
    paneTitle = "Dettaglio nota",
  ) { noteDetail ->
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = noteDetail.note.title.ifBlank { noteDetail.note.categoryLabel },
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(noteDetail.content)
    }
  }

  state.attachmentDialog?.let { dialog ->
    AttachmentDownloadDialog(
      state = dialog,
      onDismiss = viewModel::dismissAttachmentDialog,
    )
  }
}

/**
 * Il corpo del dettaglio di una circolare: contenuto, allegati e azioni, in un posto solo.
 *
 * Prima esisteva tre volte — una rotta, uno sheet inline e un terzo sheet annidato per le azioni —
 * e le tre copie divergevano gia'. Le euristiche ([shouldShowAcknowledgeAction] e sorelle) restano
 * identiche: decidevano cosa mostrare in uno sheet, ora decidono cosa mostrare nel modale.
 */
@Composable
private fun CommunicationDetailContent(
  detail: CommunicationDetail,
  isSubmittingAction: Boolean,
  viewModel: CommunicationsViewModel,
  context: Context,
  onUpload: (CommunicationDetail) -> Unit,
) {
  var replyDraft by rememberSaveable(detail.communication.id, detail.replyText) {
    mutableStateOf(detail.replyText.orEmpty())
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = detail.communication.title,
      style = MaterialTheme.typography.headlineSmall,
    )
    val formattedDate = remember(detail.communication.date) {
      runCatching {
        LocalDate.parse(detail.communication.date)
          .format(DateTimeFormatter.ofPattern("d MMMM yyyy", italianLocale))
      }.getOrDefault(detail.communication.date)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = detail.communication.sender,
        style = MaterialTheme.typography.labelLarge,
      )
      Text(
        text = formattedDate,
        style = MaterialTheme.typography.bodySmall,
      )
      if (!detail.communication.category.isNullOrBlank()) {
        FluidStatusBadge(label = detail.communication.category!!, tone = FluidTone.Info)
      }
      if (!detail.communication.read) {
        FluidButton(
          text = "Segna come letta",
          onClick = { viewModel.markCommunicationRead(detail.communication.id) },
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
        )
      }
    }
    val rendered = remember(detail.content, detail.communication.title) {
      renderCommunicationContent(detail.content, detail.communication.title)
    }
    if (rendered.isBlank()) {
      Text(
        text = "Nessun contenuto fornito dal registro per questa comunicazione.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text(text = rendered)
    }
    val canReply = shouldShowReplyComposer(detail)
    if (canReply) {
      FluidSectionHeader("Risposta")
      FluidTextField(
        value = replyDraft,
        onValueChange = { replyDraft = it },
        modifier = Modifier.fillMaxWidth(),
        label = if (detail.replyText != null) "Risposta inviata" else "Scrivi una risposta",
        readOnly = detail.replyText != null,
        minLines = 3,
      )
    }
    if (detail.communication.noticeboardAttachments.isNotEmpty()) {
      FluidSectionHeader("Allegati")
      // Da v5.6.0: sempre il path auth-aware (RestClient con refresh automatico del token). Il
      // vecchio downloadAttachment usava DownloadManager, che spesso falliva perche' non riceveva
      // l'header Z-Auth-Token aggiornato.
      // Dentro un contenitore, come ogni altra lista dell'app.
      //
      // Erano righe sciolte appoggiate al pannello: nessuno spessore, nessun bordo, nessuna
      // superficie — cioe' niente che dicesse che erano oggetti, tantomeno oggetti da toccare. E la
      // graffetta stava a destra insieme alla freccia e al badge, che e' il posto dove si mettono
      // gli *stati*, non l'identita' di una cosa: davanti, come tessera, dice cos'e' la riga.
      FluidListGroup(glass = true) {
        detail.communication.noticeboardAttachments.forEachIndexed { index, attachment ->
        if (index > 0) FluidListDivider()
        val hasUrl = !attachment.url.isNullOrBlank()
        FluidListRow(
          title = attachment.name,
          // Una riga sola: dentro un pop-up ogni riga di spiegazione spinge il contenuto vero
          // fuori dallo schermo, e "come funziona la cache" non e' una cosa da leggere ogni volta.
          subtitle = if (hasUrl) "Tocca per aprire" else "Non disponibile in API",
          tone = if (hasUrl) FluidTone.Info else FluidTone.Neutral,
          leading = {
            Icon(Icons.Rounded.AttachFile, contentDescription = null)
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
    }
    if (isSubmittingAction) {
      FluidLoading()
    } else {
      CommunicationActions(
        detail = detail,
        canReply = canReply,
        replyDraft = replyDraft,
        onAcknowledge = { viewModel.acknowledge(detail) },
        onReply = { viewModel.reply(detail, replyDraft) },
        onJoin = { viewModel.join(detail) },
        onUpload = { onUpload(detail) },
      )
    }
    // Niente pulsante "Chiudi": un pop-up che tiene la pagina visibile dietro di se' si chiude
    // come ci si aspetta — toccando la pagina, o con back. Un pulsantone pieno in fondo diceva
    // "questa e' una schermata", che e' esattamente cio' che il modale non e'.
  }
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

private fun shareText(context: Context, title: String, text: String) {
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
  }
  runCatching { context.startActivity(Intent.createChooser(intent, title)) }
}

private fun shareCommunication(context: Context, communication: Communication) {
  shareText(
    context = context,
    title = "Condividi comunicazione",
    text = buildString {
      append(communication.title)
      if (communication.sender.isNotBlank()) append("\n").append(communication.sender)
      append("\n").append(communication.date.toReadableDate())
      communication.contentPreview.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
    },
  )
}

private fun shareNote(context: Context, note: Note) {
  shareText(
    context = context,
    title = "Condividi nota",
    text = buildString {
      append(note.title.ifBlank { note.categoryLabel })
      append("\n").append(note.date.toReadableDate())
      note.contentPreview.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
    },
  )
}
