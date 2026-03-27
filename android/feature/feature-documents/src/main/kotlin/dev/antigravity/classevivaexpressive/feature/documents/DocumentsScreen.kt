package dev.antigravity.classevivaexpressive.feature.documents

import android.graphics.BitmapFactory
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveSimpleListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
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
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
  private val documentsRepository: DocumentsRepository,
) : ViewModel() {
  private val selectedDocument = MutableStateFlow<DocumentItem?>(null)
  private val selectedAsset = MutableStateFlow<DocumentAsset?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isLoadingPreview = MutableStateFlow(false)

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
  ) { content, message, loading ->
    DocumentsUiState(
      documents = content.documents,
      schoolbooks = content.schoolbooks,
      selectedDocument = content.document,
      selectedAsset = content.asset,
      lastMessage = message,
      isLoadingPreview = loading,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

  init {
    viewModelScope.launch { documentsRepository.refreshDocuments() }
  }

  fun refresh() {
    viewModelScope.launch { documentsRepository.refreshDocuments(force = true) }
  }

  fun preview(document: DocumentItem) {
    viewModelScope.launch {
      isLoadingPreview.value = true
      documentsRepository.openDocument(document)
        .onSuccess {
          selectedDocument.value = document
          selectedAsset.value = it
        }
        .onFailure {
          lastMessage.value = it.message ?: "Impossibile aprire il documento."
        }
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

  private data class PreviewBundle(
    val documents: List<DocumentItem>,
    val schoolbooks: List<SchoolbookCourse>,
    val document: DocumentItem?,
    val asset: DocumentAsset?,
  )
}

@Composable
fun DocumentsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: DocumentsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "Final grades",
        onBack = onBack,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
          }
        },
      )
    }
    if (state.isLoadingPreview) {
      item {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
    item {
      ExpressiveAccentLabel("School reports")
    }
    if (state.documents.isEmpty()) {
      item {
        EmptyState(
          title = "No documents available",
          detail = "Pagelle, modulistica e allegati compariranno qui appena il portale o l’API li espongono.",
        )
      }
    } else {
      items(state.documents, key = { it.id }) { document ->
        ExpressiveSimpleListRow(
          title = document.title,
          subtitle = document.detail,
          meta = document.capabilityState.label.takeIf { it.isNotBlank() },
          onClick = { viewModel.preview(document) },
          trailing = { Icon(Icons.Rounded.Description, contentDescription = null) },
        )
      }
    }
    if (state.schoolbooks.isNotEmpty()) {
      item {
        ExpressiveAccentLabel("Documents")
      }
      items(state.schoolbooks, key = { it.id }) { course ->
        ExpressiveSimpleListRow(
          title = course.title,
          subtitle = "${course.books.size} books",
          meta = course.books.joinToString(" • ") { it.title },
        )
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

  val selectedDocument = state.selectedDocument
  val selectedAsset = state.selectedAsset
  if (selectedDocument != null && selectedAsset != null) {
    val bytes = remember(selectedAsset.base64Content) { decodePreviewContent(selectedAsset.base64Content) }
    DocumentPreviewDialog(
      asset = selectedAsset,
      onDismiss = viewModel::dismissPreview,
      onDownload = { viewModel.download(selectedDocument) },
      onOpenFile = {
        val payload = bytes ?: return@DocumentPreviewDialog
        openPreviewFile(
          context = context,
          displayName = selectedAsset.fileName ?: selectedDocument.title,
          mimeType = selectedAsset.mimeType,
          bytes = payload,
        ).onFailure { error ->
          viewModel.showMessage(error.message ?: "Non riesco ad aprire questo file sul dispositivo.")
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
            subtitle = asset.capabilityState.detail ?: "Documento autenticato pronto per preview o apertura.",
          )
        }
        item {
          RowActions(
            onOpenFile = onOpenFile,
            onDownload = onDownload,
            canOpen = bytes != null,
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
private fun RowActions(
  onOpenFile: () -> Unit,
  onDownload: () -> Unit,
  canOpen: Boolean,
) {
  LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    item {
      TextButton(onClick = onOpenFile, enabled = canOpen) {
        Text("Apri file")
      }
    }
    item {
      TextButton(onClick = onDownload) {
        Text("Scarica")
      }
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
          title = "Image preview unavailable",
          detail = "Il file è disponibile ma l’anteprima non è stata generata correttamente.",
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
        title = "PDF ready to open",
        detail = "Il documento è stato recuperato correttamente. Usa 'Apri file' per visualizzarlo con la tua app PDF.",
      )
    }

    else -> {
      EmptyState(
        title = "Preview unavailable",
        detail = "Il file è pronto per apertura esterna o download locale, ma il formato non ha una preview inline affidabile.",
      )
    }
  }
}
