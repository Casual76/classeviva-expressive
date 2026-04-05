package dev.antigravity.classevivaexpressive.feature.documents

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.data.preview.decodePreviewContent
import dev.antigravity.classevivaexpressive.core.data.preview.openPreviewFile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEditorialCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentsUiState(
  val documents: List<DocumentItem> = emptyList(),
  val schoolbooks: List<SchoolbookCourse> = emptyList(),
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val lastMessage: String? = null,
  val isLoadingPreview: Boolean = false,
  val isRefreshing: Boolean = false,
)

private data class PreviewBundle(
  val documents: List<DocumentItem>,
  val schoolbooks: List<SchoolbookCourse>,
  val document: DocumentItem?,
  val asset: DocumentAsset?,
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
  private val documentsRepository: DocumentsRepository,
) : ViewModel() {
  private val selectedDocument = MutableStateFlow<DocumentItem?>(null)
  private val selectedAsset = MutableStateFlow<DocumentAsset?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isLoadingPreview = MutableStateFlow(false)
  private val isRefreshing = MutableStateFlow(false)

  private val contentState = combine(
    documentsRepository.observeDocuments(),
    documentsRepository.observeSchoolbooks(),
    selectedDocument,
    selectedAsset,
  ) { documents, schoolbooks, document, asset ->
    PreviewBundle(documents, schoolbooks, document, asset)
  }

  val state = combine(
    contentState,
    lastMessage,
    isLoadingPreview,
    isRefreshing,
  ) { content, message, loading, refreshing ->
    DocumentsUiState(
      documents = content.documents,
      schoolbooks = content.schoolbooks,
      selectedDocument = content.document,
      selectedAsset = content.asset,
      lastMessage = message,
      isLoadingPreview = loading,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  fun preview(document: DocumentItem) {
    viewModelScope.launch {
      isLoadingPreview.value = true
      selectedDocument.value = document
      selectedAsset.value = null
      lastMessage.value = null
      documentsRepository.openDocument(document)
        .onSuccess { selectedAsset.value = it }
        .onFailure { lastMessage.value = it.message ?: "Impossibile aprire il documento selezionato." }
      isLoadingPreview.value = false
    }
  }

  fun download(document: DocumentItem) {
    viewModelScope.launch {
      documentsRepository.queueDownload(document)
        .onSuccess { lastMessage.value = "Download avviato per ${document.title}" }
        .onFailure { lastMessage.value = it.message ?: "Download fallito." }
    }
  }

  fun dismissPreview() {
    selectedDocument.value = null
    selectedAsset.value = null
  }

  fun clearMessage() {
    lastMessage.value = null
  }

  fun showMessage(message: String) {
    lastMessage.value = message
  }

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      documentsRepository.refreshDocuments(force = force)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare documenti e pagelle." }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: DocumentsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val schoolbookCount = remember(state.schoolbooks) { state.schoolbooks.sumOf { it.books.size } }
  val selectedAssetBytes = remember(state.selectedAsset?.base64Content) {
    state.selectedAsset?.base64Content?.let(::decodePreviewContent)
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
          title = "Documenti",
          subtitle = "Pagelle, documenti autenticati e libri di testo con preview locale o apertura esterna quando serve.",
          onBack = onBack,
          actions = {
            IconButton(onClick = viewModel::refresh) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
            }
          },
        )
      }
      if (state.isLoadingPreview) {
        item {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
      if (!state.lastMessage.isNullOrBlank()) {
        item {
          RegisterListRow(
            title = "Stato documenti",
            subtitle = state.lastMessage.orEmpty(),
            tone = ExpressiveTone.Warning,
            badge = { StatusBadge("AVVISO", tone = ExpressiveTone.Warning) },
          )
        }
        item {
          TextButton(onClick = viewModel::clearMessage) {
            Text("Nascondi messaggio")
          }
        }
      }
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricTile(
            label = "Documenti",
            value = state.documents.size.toString(),
            detail = "Voci recuperate dalle API ufficiali.",
            modifier = Modifier.fillMaxWidth(),
          )
          MetricTile(
            label = "Corsi",
            value = state.schoolbooks.size.toString(),
            detail = "Raggruppamenti libri disponibili.",
            modifier = Modifier.fillMaxWidth(),
            tone = ExpressiveTone.Info,
          )
          MetricTile(
            label = "Libri",
            value = schoolbookCount.toString(),
            detail = "Testi associati ai corsi.",
            modifier = Modifier.fillMaxWidth(),
            tone = ExpressiveTone.Success,
          )
        }
      }
      item {
        ExpressiveAccentLabel("Pagelle e documenti")
      }
      if (state.documents.isEmpty()) {
        item {
          EmptyState(
            title = "Nessun documento disponibile",
            detail = "Pagelle, moduli e documenti compariranno qui appena le API ufficiali li espongono in modo leggibile.",
          )
        }
      } else {
        items(state.documents, key = { it.id }) { document ->
          RegisterListRow(
            title = document.title,
            subtitle = document.detail.ifBlank { document.capabilityState.detail ?: "Documento ufficiale" },
            meta = document.capabilityState.detail,
            tone = capabilityTone(document.capabilityState),
            onClick = { viewModel.preview(document) },
            badge = {
              StatusBadge(
                label = capabilityBadgeLabel(document.capabilityState),
                tone = capabilityTone(document.capabilityState),
              )
            },
            leading = {
              Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            },
          )
        }
      }
      if (state.schoolbooks.isNotEmpty()) {
        item {
          ExpressiveAccentLabel("Libri di testo")
        }
        items(state.schoolbooks, key = { it.id }) { course ->
          ExpressiveEditorialCard {
            Text(
              text = course.title,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${course.books.size} libri associati",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            course.books.take(4).forEach { book ->
              Text(
                text = buildString {
                  append(book.subject.ifBlank { "Materia" })
                  append(" / ")
                  append(book.title)
                  book.author?.takeIf(String::isNotBlank)?.let {
                    append(" / ")
                    append(it)
                  }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (course.books.size > 4) {
              Text(
                text = "+${course.books.size - 4} altri libri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }

  val selectedDocument = state.selectedDocument
  val selectedAsset = state.selectedAsset
  if (selectedDocument != null && selectedAsset != null) {
    DocumentPreviewDialog(
      asset = selectedAsset,
      onDismiss = viewModel::dismissPreview,
      onDownload = { viewModel.download(selectedDocument) },
      onOpenFile = {
        val payload = selectedAssetBytes ?: return@DocumentPreviewDialog
        openPreviewFile(
          context = context,
          displayName = selectedAsset.fileName ?: selectedDocument.title,
          mimeType = selectedAsset.mimeType,
          bytes = payload,
        ).onFailure { error ->
          viewModel.showMessage(error.message ?: "Non riesco ad aprire questo file sul dispositivo.")
        }
      },
      onOpenLink = {
        selectedAsset.sourceUrl?.let { url ->
          startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), null)
        }
      },
    )
  }
}

@Composable
private fun DocumentPreviewDialog(
  asset: DocumentAsset,
  onDismiss: () -> Unit,
  onDownload: () -> Unit,
  onOpenFile: () -> Unit,
  onOpenLink: () -> Unit,
) {
  val bytes = remember(asset.base64Content) { decodePreviewContent(asset.base64Content) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .fillMaxHeight(0.96f),
      color = MaterialTheme.colorScheme.background,
    ) {
      LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        item {
          ExpressiveTopHeader(
            title = asset.title,
            subtitle = asset.capabilityState.detail ?: "Documento autenticato pronto per preview, apertura o download.",
            actions = {
              TextButton(onClick = onDismiss) {
                Text("Chiudi")
              }
            },
          )
        }
        item {
          PreviewActions(
            onOpenFile = onOpenFile,
            onOpenLink = onOpenLink,
            onDownload = onDownload,
            canOpenFile = bytes != null,
            canOpenLink = !asset.sourceUrl.isNullOrBlank(),
          )
        }
        item {
          DocumentPreviewBody(asset = asset, bytes = bytes)
        }
      }
    }
  }
}

@Composable
private fun PreviewActions(
  onOpenFile: () -> Unit,
  onOpenLink: () -> Unit,
  onDownload: () -> Unit,
  canOpenFile: Boolean,
  canOpenLink: Boolean,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    TextButton(onClick = onOpenFile, enabled = canOpenFile) {
      Text("Apri file")
    }
    TextButton(onClick = onOpenLink, enabled = canOpenLink) {
      Text("Apri link")
    }
    TextButton(onClick = onDownload) {
      Text("Scarica")
    }
  }
}

@Composable
private fun DocumentPreviewBody(
  asset: DocumentAsset,
  bytes: ByteArray?,
) {
  val textPreview = asset.textPreview
  when {
    asset.mimeType == "text/html" && bytes != null -> {
      val html = remember(bytes) { bytes.decodeToString() }
      ExpressiveEditorialCard {
        AndroidView(
          modifier = Modifier
            .fillMaxWidth()
            .height(480.dp),
          factory = { context ->
            WebView(context).apply {
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              webChromeClient = WebChromeClient()
              webViewClient = WebViewClient()
              loadDataWithBaseURL(asset.sourceUrl, html, "text/html", "utf-8", null)
            }
          },
          update = { webView ->
            webView.loadDataWithBaseURL(asset.sourceUrl, html, "text/html", "utf-8", null)
          },
        )
      }
    }

    asset.mimeType?.startsWith("image/") == true && bytes != null -> {
      val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
      if (bitmap != null) {
        ExpressiveEditorialCard {
          Image(
            modifier = Modifier.fillMaxWidth(),
            bitmap = bitmap.asImageBitmap(),
            contentDescription = asset.title,
          )
        }
      } else {
        EmptyState(
          title = "Anteprima immagine non disponibile",
          detail = "Il documento e stato scaricato, ma il dispositivo non ha generato correttamente la preview.",
        )
      }
    }

    textPreview != null -> {
      ExpressiveEditorialCard {
        Text(
          text = textPreview,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    asset.mimeType == "application/pdf" && bytes != null -> {
      EmptyState(
        title = "PDF pronto",
        detail = "Il documento e disponibile: usa 'Apri file' per visualizzarlo con un lettore PDF del dispositivo.",
      )
    }

    else -> {
      EmptyState(
        title = "Preview non disponibile",
        detail = if (!asset.sourceUrl.isNullOrBlank()) {
          "L'API ufficiale ha restituito un'apertura esterna. Puoi continuare con 'Apri link'."
        } else {
          "Il documento e pronto per il download locale, ma questo formato non offre una preview inline affidabile."
        },
      )
    }
  }
}

private fun capabilityTone(state: CapabilityState): ExpressiveTone {
  return when (state.status) {
    CapabilityStatus.AVAILABLE -> ExpressiveTone.Success
    CapabilityStatus.EXTERNAL_ONLY -> ExpressiveTone.Info
    CapabilityStatus.EMPTY -> ExpressiveTone.Warning
    CapabilityStatus.UNAVAILABLE -> ExpressiveTone.Danger
  }
}

private fun capabilityBadgeLabel(state: CapabilityState): String {
  return when (state.status) {
    CapabilityStatus.AVAILABLE -> "PRONTO"
    CapabilityStatus.EXTERNAL_ONLY -> "ESTERNO"
    CapabilityStatus.EMPTY -> "VUOTO"
    CapabilityStatus.UNAVAILABLE -> "BLOCCATO"
  }
}
