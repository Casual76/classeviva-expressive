package dev.antigravity.classevivaexpressive.feature.materials

import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
  val isRefreshing: Boolean = false,
)

private data class MaterialFolderGroup(
  val key: String,
  val folderName: String,
  val teacherName: String,
  val items: List<MaterialItem>,
)

@HiltViewModel
class MaterialsViewModel @Inject constructor(
  private val materialsRepository: MaterialsRepository,
) : ViewModel() {
  private val selectedItem = MutableStateFlow<MaterialItem?>(null)
  private val selectedAsset = MutableStateFlow<MaterialAsset?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isLoadingPreview = MutableStateFlow(false)
  private val isRefreshing = MutableStateFlow(false)

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
    isRefreshing,
  ) { content, message, loading, refreshing ->
    val (materials, item, asset) = content
    MaterialsUiState(
      materials = materials,
      selectedItem = item,
      selectedAsset = asset,
      lastMessage = message,
      isLoadingPreview = loading,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaterialsUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  fun open(item: MaterialItem) {
    viewModelScope.launch {
      selectedItem.value = item
      selectedAsset.value = null
      lastMessage.value = null
      isLoadingPreview.value = true
      materialsRepository.openAsset(item)
        .onSuccess { selectedAsset.value = it }
        .onFailure {
          lastMessage.value = it.message ?: "Impossibile aprire il contenuto selezionato."
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

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      materialsRepository.refreshMaterials(force = force)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare i materiali." }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsRoute(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: MaterialsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var query by rememberSaveable { mutableStateOf("") }
  val groups = remember(state.materials, query) { buildMaterialGroups(state.materials, query) }
  val folderCount = groups.size
  val materialCount = state.materials.size
  val fileCount = remember(state.materials) { state.materials.count { materialTypeLabel(it) == "File" } }
  val selectedBytes = remember(state.selectedAsset?.base64Content) {
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
        title = "Materiale didattico",
        subtitle = "Cartelle del portale, contenuti condivisi e apertura controllata dei documenti.",
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
          title = "Aggiornamento materiale",
          subtitle = state.lastMessage.orEmpty(),
          meta = "Le altre cartelle restano consultabili anche con dati parziali.",
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
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile(
          label = "Cartelle",
          value = folderCount.toString(),
          detail = "Raggruppate per docente e cartella.",
          modifier = Modifier.weight(1f),
        )
        MetricTile(
          label = "Risorse",
          value = materialCount.toString(),
          detail = "Contenuti recuperati dal portale.",
          modifier = Modifier.weight(1f),
        )
        MetricTile(
          label = "File",
          value = fileCount.toString(),
          detail = "Documenti, PDF o allegati scaricabili.",
          modifier = Modifier.weight(1f),
          tone = ExpressiveTone.Info,
        )
      }
    }
    item {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Cerca titolo, cartella o docente") },
        singleLine = true,
      )
    }
    item {
      ExpressiveAccentLabel("Anteprima")
    }
    item {
      when (val selectedItem = state.selectedItem) {
        null -> EmptyState(
          title = "Nessun contenuto selezionato",
          detail = "Scegli un materiale dalla lista per vedere stato di apertura, anteprima e allegati.",
        )

        else -> {
          val selectedAsset = state.selectedAsset
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RegisterListRow(
              title = selectedItem.title,
              subtitle = selectedItem.teacherName.ifBlank { "Docente non disponibile" },
              eyebrow = selectedItem.folderName,
              meta = selectedItem.capabilityState.detail ?: selectedItem.sharedAt,
              tone = capabilityTone(selectedItem.capabilityState),
              badge = {
                StatusBadge(
                  label = materialTypeLabel(selectedItem),
                  tone = capabilityTone(selectedItem.capabilityState),
                )
              },
              leading = {
                Icon(
                  Icons.Rounded.Folder,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
            )
            if (selectedAsset != null) {
              RegisterListRow(
                title = selectedAsset.capabilityState.label.ifBlank { "Contenuto pronto" },
                subtitle = selectedAsset.capabilityState.detail ?: "Il contenuto e pronto per apertura o anteprima.",
                meta = selectedAsset.mimeType ?: "Mime type non disponibile",
                tone = capabilityTone(selectedAsset.capabilityState),
                badge = {
                  StatusBadge(
                    label = capabilityBadgeLabel(selectedAsset.capabilityState),
                    tone = capabilityTone(selectedAsset.capabilityState),
                  )
                },
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                  onClick = {
                    val payload = selectedBytes
                    if (payload != null) {
                      openPreviewFile(
                        context = context,
                        displayName = selectedAsset.fileName ?: selectedItem.title,
                        mimeType = selectedAsset.mimeType,
                        bytes = payload,
                      ).onFailure { error ->
                        viewModel.showMessage(error.message ?: "Non riesco ad aprire questo file sul dispositivo.")
                      }
                    }
                  },
                  enabled = selectedBytes != null,
                ) {
                  Text("Apri file")
                }
                TextButton(
                  onClick = {
                    selectedAsset.sourceUrl?.let { url ->
                      startActivity(
                        context,
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                        null,
                      )
                    }
                  },
                  enabled = !selectedAsset.sourceUrl.isNullOrBlank(),
                ) {
                  Text("Apri link")
                }
                TextButton(onClick = viewModel::dismissPreview) {
                  Text("Chiudi")
                }
              }
              MaterialPreviewBody(
                asset = selectedAsset,
                bytes = selectedBytes,
              )
              if (selectedItem.attachments.isNotEmpty()) {
                ExpressiveAccentLabel("Allegati")
                selectedItem.attachments.forEach { attachment ->
                  RegisterListRow(
                    title = attachment.name,
                    subtitle = attachment.mimeType ?: "Allegato",
                    meta = if (attachment.portalOnly) "Richiede passaggio portale" else "Download diretto disponibile",
                    tone = if (attachment.portalOnly) ExpressiveTone.Warning else ExpressiveTone.Neutral,
                    onClick = { viewModel.download(attachment) },
                    badge = {
                      StatusBadge(
                        label = if (attachment.portalOnly) "PORTALE" else "DOWNLOAD",
                        tone = if (attachment.portalOnly) ExpressiveTone.Warning else ExpressiveTone.Info,
                      )
                    },
                    leading = {
                      Icon(
                        Icons.Rounded.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    },
                  )
                }
              }
            } else if (!state.isLoadingPreview) {
              EmptyState(
                title = "Anteprima non disponibile",
                detail = "Questo contenuto non ha restituito una preview affidabile oppure richiede apertura esterna.",
              )
            }
          }
        }
      }
    }
    item {
      ExpressiveAccentLabel("Cartelle")
    }
    if (groups.isEmpty()) {
      item {
        EmptyState(
          title = "Materiale non disponibile",
          detail = "Nessun contenuto corrisponde ai filtri correnti o il portale non ha restituito materiali.",
        )
      }
    } else {
      groups.forEach { group ->
        item(key = "folder-${group.key}") {
          RegisterListRow(
            title = group.folderName,
            subtitle = group.teacherName.ifBlank { "Docente non disponibile" },
            meta = "${group.items.size} contenuti disponibili",
            tone = ExpressiveTone.Neutral,
            badge = { StatusBadge("CARTELLA") },
            leading = {
              Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            },
          )
        }
        items(group.items, key = { it.id }) { item ->
          RegisterListRow(
            title = item.title,
            subtitle = item.teacherName.ifBlank { "Docente non disponibile" },
            eyebrow = item.folderName,
            meta = item.capabilityState.detail ?: item.sharedAt,
            tone = if (state.selectedItem?.id == item.id) ExpressiveTone.Info else capabilityTone(item.capabilityState),
            onClick = { viewModel.open(item) },
            badge = {
              StatusBadge(
                label = materialTypeLabel(item),
                tone = if (state.selectedItem?.id == item.id) ExpressiveTone.Info else capabilityTone(item.capabilityState),
              )
            },
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
            .height(420.dp),
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
          detail = "Il file e disponibile ma il dispositivo non ha generato correttamente l'anteprima.",
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
        title = "PDF pronto per l'apertura",
        detail = "Il materiale e stato recuperato correttamente. Usa 'Apri file' per visualizzarlo con un'app compatibile.",
      )
    }

    else -> {
      EmptyState(
        title = "Anteprima non disponibile",
        detail = if (!asset.sourceUrl.isNullOrBlank()) {
          "Il portale ha restituito solo un'apertura esterna. Usa 'Apri link' per continuare."
        } else {
          "Il materiale e disponibile come file, ma il formato non offre una preview inline affidabile."
        },
      )
    }
  }
}

private fun buildMaterialGroups(
  materials: List<MaterialItem>,
  query: String,
): List<MaterialFolderGroup> {
  val normalized = query.trim().lowercase()
  return materials
    .groupBy { "${it.folderId}:${it.teacherId}" }
    .map { (key, items) ->
      val first = items.first()
      MaterialFolderGroup(
        key = key,
        folderName = first.folderName,
        teacherName = first.teacherName,
        items = items.sortedBy { it.title },
      )
    }
    .mapNotNull { group ->
      val filteredItems = if (normalized.isBlank()) {
        group.items
      } else {
        group.items.filter { item ->
          listOf(item.title, item.teacherName, item.folderName, item.objectType)
            .joinToString(" ")
            .lowercase()
            .contains(normalized)
        }
      }
      group.takeIf { filteredItems.isNotEmpty() }?.copy(items = filteredItems)
    }
    .sortedWith(compareBy(MaterialFolderGroup::teacherName, MaterialFolderGroup::folderName))
}

private fun materialTypeLabel(item: MaterialItem): String {
  val value = item.objectType.lowercase()
  return when {
    "link" in value -> "Link"
    "video" in value -> "Video"
    "pdf" in value || "file" in value -> "File"
    else -> item.objectType.ifBlank { "Contenuto" }
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
    CapabilityStatus.AVAILABLE -> "DISPONIBILE"
    CapabilityStatus.EXTERNAL_ONLY -> "ESTERNA"
    CapabilityStatus.EMPTY -> "VUOTO"
    CapabilityStatus.UNAVAILABLE -> "NON DISP."
  }
}
