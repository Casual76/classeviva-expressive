package dev.antigravity.classevivaexpressive.feature.homework

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.AttachmentPayload
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkSubmission
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class HomeworkUiState(
  val homeworks: List<Homework> = emptyList(),
  val selectedYear: SchoolYearRef = SchoolYearRef.current(LocalDate.now().year, LocalDate.now().monthValue),
  val capability: FeatureCapability? = null,
  val selectedDetail: HomeworkDetail? = null,
  val lastMessage: String? = null,
  val isRefreshing: Boolean = false,
  val isSubmitting: Boolean = false,
)

@HiltViewModel
class HomeworkViewModel @Inject constructor(
  private val homeworkRepository: HomeworkRepository,
  private val schoolYearRepository: SchoolYearRepository,
  private val capabilityResolver: CapabilityResolver,
) : ViewModel() {
  private val selectedDetail = MutableStateFlow<HomeworkDetail?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)
  private val isSubmitting = MutableStateFlow(false)

  private val contentState = combine(
    homeworkRepository.observeHomeworks(),
    schoolYearRepository.observeSelectedSchoolYear(),
    capabilityResolver.observeCapability(RegistroFeature.HOMEWORKS),
    selectedDetail,
  ) { homeworks, year, capability, detail ->
    HomeworkUiState(
      homeworks = homeworks.sortedBy { it.dueDate },
      selectedYear = year,
      capability = capability,
      selectedDetail = detail,
    )
  }

  val state = combine(
    contentState,
    lastMessage,
    isRefreshing,
    isSubmitting,
  ) { content, message, refreshing, submitting ->
    HomeworkUiState(
      homeworks = content.homeworks,
      selectedYear = content.selectedYear,
      capability = content.capability,
      selectedDetail = content.selectedDetail,
      lastMessage = message,
      isRefreshing = refreshing,
      isSubmitting = submitting,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeworkUiState())

  init {
    refresh(showIndicator = false)
  }

  fun refresh(showIndicator: Boolean = true) {
    viewModelScope.launch {
      if (showIndicator) isRefreshing.value = true
      homeworkRepository.refreshHomeworks(force = true)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare i compiti." }
      isRefreshing.value = false
    }
  }

  fun openHomework(id: String) {
    viewModelScope.launch {
      homeworkRepository.getHomeworkDetail(id)
        .onSuccess { selectedDetail.value = it }
        .onFailure { lastMessage.value = it.message ?: "Impossibile aprire il compito." }
    }
  }

  fun submit(detail: HomeworkDetail, text: String, attachment: AttachmentPayload?) {
    viewModelScope.launch {
      isSubmitting.value = true
      homeworkRepository.submitHomework(
        HomeworkSubmission(
          homeworkId = detail.homework.id,
          text = text.ifBlank { null },
          attachments = listOfNotNull(attachment),
        ),
      ).onSuccess {
        lastMessage.value = it.message ?: "Consegna inviata."
        selectedDetail.value = null
      }.onFailure {
        lastMessage.value = it.message ?: "Non sono riuscito a inviare il compito."
      }
      isSubmitting.value = false
    }
  }

  fun queueAttachmentDownload(attachment: dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment) {
    viewModelScope.launch {
      homeworkRepository.queueAttachmentDownload(attachment)
        .onSuccess { lastMessage.value = "Download avviato per ${attachment.name}" }
        .onFailure { lastMessage.value = it.message ?: "Download non riuscito." }
    }
  }

  fun dismissDetail() {
    selectedDetail.value = null
  }

  fun clearMessage() {
    lastMessage.value = null
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: HomeworkViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var pendingAttachment by remember(state.selectedDetail?.homework?.id) { mutableStateOf<AttachmentPayload?>(null) }

  val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    pendingAttachment = uri?.let { readPickedAttachment(context, it) }
  }

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
          title = "Compiti",
          subtitle = "Sezione dedicata ai compiti per ${state.selectedYear.label}, con consegna gestita dal gateway quando richiesta.",
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
            title = capability.label.ifBlank { "Disponibilita compiti" },
            subtitle = capability.detail ?: "Capability non disponibile.",
            eyebrow = state.selectedYear.label,
            tone = if (capability.enabled) ExpressiveTone.Success else ExpressiveTone.Warning,
            badge = {
              StatusBadge(
                label = capability.mode.name.replace('_', ' '),
                tone = if (capability.enabled) ExpressiveTone.Success else ExpressiveTone.Warning,
              )
            },
          )
        }
      }
      if (state.homeworks.isEmpty()) {
        item {
          EmptyState(
            title = "Nessun compito disponibile",
            detail = "I compiti dell'anno selezionato compariranno qui appena la sincronizzazione restituisce elementi validi.",
          )
        }
      } else {
        items(state.homeworks, key = { it.id }) { homework ->
          RegisterListRow(
            title = homework.description,
            subtitle = homework.subject,
            eyebrow = homework.dueDate.toReadableDate(),
            meta = homework.notes,
            tone = ExpressiveTone.Primary,
            onClick = { viewModel.openHomework(homework.id) },
            badge = { StatusBadge("COMPITO", tone = ExpressiveTone.Primary) },
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

  state.selectedDetail?.let { detail ->
    var draft by rememberSaveable(detail.homework.id) { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = {
        pendingAttachment = null
        viewModel.dismissDetail()
      },
      title = { Text(detail.homework.subject) },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          item { ExpressiveAccentLabel(detail.homework.dueDate.toReadableDate()) }
          item { Text(detail.fullText) }
          if (detail.homework.attachments.isNotEmpty()) {
            item { ExpressiveAccentLabel("Allegati") }
            items(detail.homework.attachments, key = { it.id }) { attachment ->
              RegisterListRow(
                title = attachment.name,
                subtitle = attachment.mimeType ?: "Allegato",
                tone = ExpressiveTone.Neutral,
                badge = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
                onClick = { viewModel.queueAttachmentDownload(attachment) },
              )
            }
          }
          item {
            OutlinedTextField(
              value = draft,
              onValueChange = { draft = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("Risposta o nota") },
              minLines = 3,
            )
          }
          pendingAttachment?.let { attachment ->
            item {
              RegisterListRow(
                title = attachment.fileName,
                subtitle = attachment.mimeType ?: "Allegato pronto",
                tone = ExpressiveTone.Info,
                badge = { StatusBadge("FILE", tone = ExpressiveTone.Info) },
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.submit(detail, draft, pendingAttachment)
            pendingAttachment = null
          },
          enabled = !state.isSubmitting && (draft.isNotBlank() || pendingAttachment != null),
        ) {
          Text("Invia")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            pendingAttachment = null
            uploadLauncher.launch(arrayOf("*/*"))
          },
          enabled = !state.isSubmitting,
        ) {
          Text(if (pendingAttachment == null) "Allega file" else "Cambia file")
        }
      },
    )
  }
}

private fun readPickedAttachment(context: Context, uri: Uri): AttachmentPayload? {
  val mimeType = context.contentResolver.getType(uri)
  val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor ->
      val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
    ?: "allegato"
  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
  return AttachmentPayload(
    fileName = fileName,
    mimeType = mimeType,
    base64Content = Base64.getEncoder().encodeToString(bytes),
  )
}

private fun String.toReadableDate(): String {
  val parsed = runCatching { LocalDate.parse(this) }.getOrNull() ?: return this
  return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", italianLocale))
}
