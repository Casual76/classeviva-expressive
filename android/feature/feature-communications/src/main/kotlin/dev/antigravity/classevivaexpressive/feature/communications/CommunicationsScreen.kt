package dev.antigravity.classevivaexpressive.feature.communications

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardAction
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAB_BOARD = "Bacheca"
private const val TAB_NOTES = "Note"
private const val FILTER_ALL = "Tutte"
private const val FILTER_UNREAD = "Non lette"
private const val FILTER_ACTION = "Da gestire"
private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class CommunicationsUiState(
  val communications: List<Communication> = emptyList(),
  val notes: List<Note> = emptyList(),
  val selectedCommunication: CommunicationDetail? = null,
  val selectedNote: NoteDetail? = null,
  val lastMessage: String? = null,
  val isSubmittingAction: Boolean = false,
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class CommunicationsViewModel @Inject constructor(
  private val communicationsRepository: CommunicationsRepository,
) : ViewModel() {
  private val selectedCommunication = MutableStateFlow<CommunicationDetail?>(null)
  private val selectedNote = MutableStateFlow<NoteDetail?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isSubmittingAction = MutableStateFlow(false)
  private val isRefreshing = MutableStateFlow(false)

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
    isRefreshing,
  ) { content, note, message, submitting, refreshing ->
    val (communications, notes, communication) = content
    CommunicationsUiState(
      communications = communications,
      notes = notes,
      selectedCommunication = communication,
      selectedNote = note,
      lastMessage = message,
      isSubmittingAction = submitting,
      isRefreshing = refreshing,
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
      communicationsRepository.getCommunicationDetail(pubId, evtCode)
        .onSuccess {
          selectedNote.value = null
          selectedCommunication.value = it
        }
        .onFailure {
          lastMessage.value = it.message ?: "Impossibile aprire la comunicazione."
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
          lastMessage.value = it.message ?: "Impossibile aprire la nota."
        }
    }
  }

  fun downloadAttachment(attachment: RemoteAttachment) {
    viewModelScope.launch {
      communicationsRepository.queueDownload(attachment)
        .onSuccess { lastMessage.value = "Download avviato per ${attachment.name}" }
        .onFailure { lastMessage.value = it.message ?: "Download fallito." }
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
      successMessage = "File inviato al portale.",
      errorMessage = "Non sono riuscito a caricare il file richiesto.",
    ) {
      communicationsRepository.uploadCommunicationFile(detail, fileName, mimeType, bytes)
    }
  }

  fun dismissDetail() {
    selectedCommunication.value = null
    selectedNote.value = null
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
          lastMessage.value = successMessage
        }
        .onFailure {
          lastMessage.value = it.message ?: errorMessage
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
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare la bacheca." }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationsRoute(
  initialTab: String = "board",
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: CommunicationsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current
  var selectedTab by rememberSaveable { mutableStateOf(tabFromRoute(initialTab)) }
  var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
  var pendingUploadDetail by remember { mutableStateOf<CommunicationDetail?>(null) }

  LaunchedEffect(initialTab) {
    selectedTab = tabFromRoute(initialTab)
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
        FILTER_ACTION -> communication.actions.isNotEmpty()
        else -> true
      }
    }
  }

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
        title = "Bacheca",
        subtitle = "Comunicazioni del portale e note scolastiche in una vista unica, con azioni e allegati disponibili.",
        onBack = onBack,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    }
    item {
      ExpressivePillTabs(
        options = listOf(TAB_BOARD, TAB_NOTES),
        selected = selectedTab,
        onSelect = { selectedTab = it },
      )
    }
    if (selectedTab == TAB_BOARD) {
      item {
        ExpressivePillTabs(
          options = listOf(FILTER_ALL, FILTER_UNREAD, FILTER_ACTION),
          selected = selectedFilter,
          onSelect = { selectedFilter = it },
        )
      }
      if (filteredCommunications.isEmpty()) {
        item {
          EmptyState(
            title = "Nessuna comunicazione visibile",
            detail = "Nuove circolari e messaggi compariranno qui con stato di lettura, allegati e azioni richieste.",
          )
        }
      } else {
        items(filteredCommunications, key = { it.id }) { communication ->
          RegisterListRow(
            title = communication.title,
            subtitle = communication.sender.ifBlank { "Bacheca scuola" },
            eyebrow = communication.date.toReadableDate(),
            meta = communication.contentPreview.takeIf { it.isNotBlank() },
            tone = communicationTone(communication),
            badge = {
              StatusBadge(
                label = communicationBadgeLabel(communication),
                tone = communicationTone(communication),
              )
            },
            onClick = { viewModel.openCommunication(communication.pubId, communication.evtCode) },
          )
        }
      }
    } else {
      if (state.notes.isEmpty()) {
        item {
          EmptyState(
            title = "Nessuna nota disponibile",
            detail = "Note disciplinari, annotazioni e richiami compariranno qui in forma sintetica e chiara.",
          )
        }
      } else {
        items(state.notes, key = { it.id }) { note ->
          RegisterListRow(
            title = note.title.ifBlank { note.author.uppercase(italianLocale) },
            subtitle = note.categoryLabel,
            eyebrow = note.date.toReadableDate(),
            meta = note.contentPreview.takeIf { it.isNotBlank() },
            tone = noteTone(note),
            badge = {
              StatusBadge(
                label = if (note.read) "LETTA" else "NOTA",
                tone = noteTone(note),
              )
            },
            onClick = { viewModel.openNote(note.id, note.categoryCode) },
          )
        }
      }
    }
    if (!state.lastMessage.isNullOrBlank()) {
      item { Text(text = state.lastMessage.orEmpty()) }
    }
    }
  }

  state.selectedCommunication?.let { detail ->
    var replyDraft by rememberSaveable(detail.communication.id, detail.replyText) {
      mutableStateOf(detail.replyText.orEmpty())
    }
    AlertDialog(
      onDismissRequest = viewModel::dismissDetail,
      title = { Text(detail.communication.title) },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          item { Text(text = detail.content) }
          if (detail.actions.any { it.type == NoticeboardActionType.REPLY } || detail.replyText != null) {
            item { ExpressiveAccentLabel("Risposta") }
            item {
              OutlinedTextField(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (detail.replyText != null) "Risposta inviata" else "Scrivi una risposta") },
                minLines = 3,
              )
            }
          }
          if (detail.communication.noticeboardAttachments.isNotEmpty()) {
            item { ExpressiveAccentLabel("Allegati") }
            items(detail.communication.noticeboardAttachments, key = { it.id }) { attachment ->
              RegisterListRow(
                title = attachment.name,
                subtitle = attachment.mimeType ?: "Allegato",
                meta = if (attachment.portalOnly) "Apertura tramite portale" else "Download diretto",
                tone = ExpressiveTone.Neutral,
                badge = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
                onClick = {
                  viewModel.downloadAttachment(
                    RemoteAttachment(
                      id = attachment.id,
                      name = attachment.name,
                      url = attachment.url,
                      mimeType = attachment.mimeType,
                      portalOnly = attachment.portalOnly,
                    ),
                  )
                },
              )
            }
          }
        }
      },
      confirmButton = {
        if (state.isSubmittingAction) {
          CircularProgressIndicator()
        } else {
          TextButton(onClick = viewModel::dismissDetail) {
            Text("Chiudi")
          }
        }
      },
      dismissButton = {
        CommunicationActions(
          actions = detail.actions,
          replyDraft = replyDraft,
          onAcknowledge = { viewModel.acknowledge(detail) },
          onReply = { viewModel.reply(detail, replyDraft) },
          onJoin = { viewModel.join(detail) },
          onUpload = {
            pendingUploadDetail = detail
            uploadLauncher.launch(arrayOf("*/*"))
          },
        )
      },
    )
  }

  state.selectedNote?.let { detail ->
    AlertDialog(
      onDismissRequest = viewModel::dismissDetail,
      title = { Text(detail.note.title.ifBlank { detail.note.categoryLabel }) },
      text = { Text(detail.content) },
      confirmButton = {
        TextButton(onClick = viewModel::dismissDetail) {
          Text("Chiudi")
        }
      },
    )
  }
}

@Composable
private fun CommunicationActions(
  actions: List<NoticeboardAction>,
  replyDraft: String,
  onAcknowledge: () -> Unit,
  onReply: () -> Unit,
  onJoin: () -> Unit,
  onUpload: () -> Unit,
) {
  LazyColumn {
    actions.distinctBy { it.type }.forEach { action ->
      when (action.type) {
        NoticeboardActionType.ACKNOWLEDGE -> item {
          TextButton(onClick = onAcknowledge) {
            Text(action.label)
          }
        }
        NoticeboardActionType.REPLY -> item {
          TextButton(onClick = onReply, enabled = replyDraft.isNotBlank()) {
            Text("Invia risposta")
          }
        }
        NoticeboardActionType.JOIN -> item {
          TextButton(onClick = onJoin) {
            Text(action.label)
          }
        }
        NoticeboardActionType.UPLOAD -> item {
          TextButton(onClick = onUpload) {
            Text(action.label)
          }
        }
        NoticeboardActionType.DOWNLOAD -> Unit
      }
    }
  }
}

private fun communicationTone(communication: Communication): ExpressiveTone {
  return when {
    communication.actions.isNotEmpty() -> ExpressiveTone.Warning
    !communication.read -> ExpressiveTone.Danger
    else -> ExpressiveTone.Neutral
  }
}

private fun communicationBadgeLabel(communication: Communication): String {
  return when {
    communication.actions.isNotEmpty() -> "AZIONE"
    !communication.read -> "NUOVA"
    else -> "LETTA"
  }
}

private fun noteTone(note: Note): ExpressiveTone {
  val normalized = note.severity.uppercase(italianLocale)
  return when {
    normalized.contains("HIGH") || normalized.contains("GRAVE") || normalized.contains("CRIT") -> ExpressiveTone.Danger
    normalized.contains("MED") || normalized.contains("WARN") -> ExpressiveTone.Warning
    else -> ExpressiveTone.Neutral
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
