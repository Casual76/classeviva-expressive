package dev.antigravity.classevivaexpressive.feature.materials

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
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Folder
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
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialAsset
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.RemoteAttachment
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MaterialsUiState(
  val materials: List<MaterialItem> = emptyList(),
  val selectedItem: MaterialItem? = null,
  val selectedAsset: MaterialAsset? = null,
  val lastMessage: String? = null,
  val isLoadingPreview: Boolean = false,
)

@HiltViewModel
class MaterialsViewModel @Inject constructor(
  private val materialsRepository: MaterialsRepository,
) : ViewModel() {
  private val selectedItem = MutableStateFlow<MaterialItem?>(null)
  private val selectedAsset = MutableStateFlow<MaterialAsset?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isLoadingPreview = MutableStateFlow(false)

  private val contentState = combine(
    materialsRepository.observeMaterials(),
    selectedItem,
    selectedAsset,
  ) { materials, item, asset ->
    Triple(materials, item, asset)
  }

  val state = combine(
    contentState,
    lastMessage,
    isLoadingPreview,
  ) { content, message, loading ->
    val (materials, item, asset) = content
    MaterialsUiState(
      materials = materials,
      selectedItem = item,
      selectedAsset = asset,
      lastMessage = message,
      isLoadingPreview = loading,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaterialsUiState())

  init {
    viewModelScope.launch { materialsRepository.refreshMaterials() }
  }

  fun refresh() {
    viewModelScope.launch { materialsRepository.refreshMaterials(force = true) }
  }

  fun open(item: MaterialItem) {
    viewModelScope.launch {
      isLoadingPreview.value = true
      materialsRepository.openAsset(item)
        .onSuccess {
          selectedItem.value = item
          selectedAsset.value = it
        }
        .onFailure {
          lastMessage.value = it.message ?: "Impossibile aprire il materiale."
        }
      isLoadingPreview.value = false
    }
  }

  fun download(attachment: RemoteAttachment) {
    viewModelScope.launch {
      materialsRepository.queueDownload(attachment)
        .onSuccess { lastMessage.value = "Download avviato per ${attachment.name}" }
        .onFailure { lastMessage.value = it.message ?: "Download fallito." }
    }
  }

  fun dismissPreview() {
    selectedItem.value = null
    selectedAsset.value = null
  }

  fun clearMessage() {
    lastMessage.value = null
  }

  fun showMessage(message: String) {
    lastMessage.value = message
  }
}

@Composable
fun MaterialsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: MaterialsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val grouped = remember(state.materials) { state.materials.groupBy { it.teacherName.uppercase() }.toSortedMap() }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveTopHeader(
        title = "School material",
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
    if (grouped.isEmpty()) {
      item {
        EmptyState(
          title = "No materials yet",
          detail = "Quando i docenti condividono cartelle o file li vedrai raggruppati qui per docente.",
        )
      }
    } else {
      grouped.forEach { (teacher, materials) ->
        item(key = "teacher-$teacher") {
          ExpressiveAccentLabel(teacher)
        }
        items(materials, key = { it.id }) { material ->
          ExpressiveSimpleListRow(
            title = material.folderName,
            subtitle = material.sharedAt,
            meta = listOf(
              material.title,
              "${material.attachments.size} allegati",
            ).joinToString(" • "),
            onClick = { viewModel.open(material) },
            trailing = { Icon(Icons.Rounded.Folder, contentDescription = null) },
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
      item {
        TextButton(onClick = viewModel::clearMessage) {
          Text("Nascondi messaggio")
        }
      }
    }
  }

  val selectedItem = state.selectedItem
  val selectedAsset = state.selectedAsset
  if (selectedItem != null && selectedAsset != null) {
    val bytes = remember(selectedAsset.base64Content) { decodePreviewContent(selectedAsset.base64Content) }
    MaterialPreviewDialog(
      item = selectedItem,
      asset = selectedAsset,
      onDismiss = viewModel::dismissPreview,
      onOpenFile = {
        val payload = bytes ?: return@MaterialPreviewDialog
        openPreviewFile(
          context = context,
          displayName = selectedAsset.fileName ?: selectedItem.title,
          mimeType = selectedAsset.mimeType,
          bytes = payload,
        ).onFailure { error ->
          viewModel.showMessage(error.message ?: "Non riesco ad aprire questo file sul dispositivo.")
        }
      },
      onDownloadAttachment = viewModel::download,
    )
  }
}

@Composable
private fun MaterialPreviewDialog(
  item: MaterialItem,
  asset: MaterialAsset,
  onDismiss: () -> Unit,
  onOpenFile: () -> Unit,
  onDownloadAttachment: (RemoteAttachment) -> Unit,
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
            title = item.title,
            subtitle = "${item.teacherName} • ${item.folderName}",
          )
        }
        item {
          TextButton(onClick = onOpenFile, enabled = bytes != null) {
            Text("Apri file")
          }
        }
        item {
          MaterialPreviewBody(asset = asset, bytes = bytes)
        }
        if (item.attachments.isNotEmpty()) {
          item {
            ExpressiveAccentLabel("Attachments")
          }
          items(item.attachments, key = { it.id }) { attachment ->
            ExpressiveSimpleListRow(
              title = attachment.name,
              subtitle = attachment.mimeType ?: "Attachment",
              meta = if (attachment.portalOnly) "Richiede passaggio portale" else "Download diretto",
              onClick = { onDownloadAttachment(attachment) },
              trailing = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MaterialPreviewBody(
  asset: MaterialAsset,
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
              loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
          },
          update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
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
          detail = "Il file è disponibile ma la preview non è stata generata correttamente.",
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
        detail = "Questo materiale è stato recuperato con successo. Usa 'Apri file' per visualizzarlo con una app compatibile.",
      )
    }

    else -> {
      EmptyState(
        title = "Preview unavailable",
        detail = "Il materiale è disponibile come file, ma il formato non ha una preview inline affidabile.",
      )
    }
  }
}
