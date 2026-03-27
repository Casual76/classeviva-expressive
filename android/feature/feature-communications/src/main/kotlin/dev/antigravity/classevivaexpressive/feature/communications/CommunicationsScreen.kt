package dev.antigravity.classevivaexpressive.feature.communications

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEditorialCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEnvelopeBadge
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveSimpleListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import dev.antigravity.classevivaexpressive.core.domain.model.NoteDetail
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAB_BOARD = "Notice board"
private const val TAB_NOTES = "Notes"

data class CommunicationsUiState(
  val communications: List<Communication> = emptyList(),
  val notes: List<Note> = emptyList(),
  val selectedCommunication: CommunicationDetail? = null,
  val selectedNote: NoteDetail? = null,
  val lastMessage: String? = null,
  val isSubmittingAction: Boolean = false,
)

@HiltViewModel
class CommunicationsViewModel @Inject constructor(
  private val communicationsRepository: CommunicationsRepository,
) : ViewModel() {
  private val selectedCommunication = MutableStateFlow<CommunicationDetail?>(null)
  private val selectedNote = MutableStateFlow<NoteDetail?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isSubmittingAction = MutableStateFlow(false)

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
  ) { content, note, message, submitting ->
    val (communications, notes, communication) = content
    CommunicationsUiState(
      communications = communications,
      notes = notes,
      selectedCommunication = communication,
      selectedNote = note,
      lastMessage = message,
      isSubmittingAction = submitting,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunicationsUiState())

  init {
    viewModelScope.launch { communicationsRepository.refreshCommunications() }
  }

  fun refresh() {
    viewModelScope.launch { communicationsRepository.refreshCommunications(force = true) }
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
}

@Composable
fun CommunicationsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: CommunicationsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var selectedTab by rememberSaveable { mutableStateOf(TAB_BOARD) }
  var pendingUploadDetail by remember { mutableStateOf<CommunicationDetail?>(null) }
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
    } else {
      viewModel.clearMessage()
    }
  }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = if (selectedTab == TAB_BOARD) "Notice board" else "Notes",
        onBack = onBack,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
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
      if (state.communications.isEmpty()) {
        item {
          EmptyState(
            title = "No communications",
            detail = "Quando la scuola pubblica nuove circolari le troverai qui con stato di lettura, allegati e azioni portale.",
          )
        }
      } else {
        items(state.communications, key = { it.id }) { communication ->
          CommunicationCard(
            communication = communication,
            onClick = { viewModel.openCommunication(communication.pubId, communication.evtCode) },
          )
        }
      }
    } else {
      if (state.notes.isEmpty()) {
        item {
          EmptyState(
            title = "No notes",
            detail = "Le note disciplinari e i richiami docenti verranno mostrati qui in una vista molto più asciutta.",
          )
        }
      } else {
        items(state.notes, key = { it.id }) { note ->
          ExpressiveSimpleListRow(
            title = note.author.uppercase(),
            subtitle = "${note.categoryLabel} - ${note.date}",
            meta = note.contentPreview.takeIf { it.isNotBlank() },
            onClick = { viewModel.openNote(note.id, note.categoryCode) },
          )
        }
      }
    }
    if (!state.lastMessage.isNullOrBlank()) {
      item {
        Text(
          text = state.lastMessage.orEmpty(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
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
          item { Text(detail.content) }
          if (detail.communication.needsReply || detail.replyText != null) {
            item {
              OutlinedTextField(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (detail.replyText != null) "Risposta inviata" else "Risposta") },
                minLines = 3,
              )
            }
          }
          if (detail.communication.attachments.isNotEmpty()) {
            items(detail.communication.attachments, key = { it.id }) { attachment ->
              ExpressiveSimpleListRow(
                title = attachment.name,
                subtitle = attachment.mimeType ?: "Attachment",
                meta = if (attachment.portalOnly) "Portale integrato" else "Download nativo",
                onClick = { viewModel.downloadAttachment(attachment) },
                trailing = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
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
          detail = detail,
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
      title = { Text(detail.note.title) },
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
private fun CommunicationCard(
  communication: Communication,
  onClick: () -> Unit,
) {
  ExpressiveEditorialCard(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = communication.title,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 4,
        )
        Text(
          text = communication.date,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (communication.contentPreview.isNotBlank()) {
          Text(
            text = communication.contentPreview,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            maxLines = 4,
          )
        }
      }
      ExpressiveEnvelopeBadge(color = communicationEnvelopeColor(communication))
    }
    TextButton(onClick = onClick) {
      Text("Apri")
    }
  }
}

@Composable
private fun CommunicationActions(
  detail: CommunicationDetail,
  replyDraft: String,
  onAcknowledge: () -> Unit,
  onReply: () -> Unit,
  onJoin: () -> Unit,
  onUpload: () -> Unit,
) {
  LazyColumn {
    if (detail.communication.needsAck) {
      item {
        TextButton(onClick = onAcknowledge) {
          Text("Conferma")
        }
      }
    }
    if (detail.communication.needsReply) {
      item {
        TextButton(onClick = onReply, enabled = replyDraft.isNotBlank()) {
          Text("Invia risposta")
        }
      }
    }
    if (detail.communication.needsJoin) {
      item {
        TextButton(onClick = onJoin) {
          Text("Aderisci")
        }
      }
    }
    if (detail.communication.needsFile) {
      item {
        TextButton(onClick = onUpload) {
          Text("Carica file")
        }
      }
    }
  }
}

private fun communicationEnvelopeColor(communication: Communication): androidx.compose.ui.graphics.Color {
  return if (communication.read && !communication.needsAck && !communication.needsReply) {
    androidx.compose.ui.graphics.Color(0xFF59C95A)
  } else {
    androidx.compose.ui.graphics.Color(0xFFFF4338)
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
