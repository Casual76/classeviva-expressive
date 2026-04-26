package dev.antigravity.classevivaexpressive.feature.dashboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressivePillTabs
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentAsset
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Homework
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkDetail
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolbookCourse
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComparison
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// MATERIALS
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class MaterialsViewModel @Inject constructor(
  private val materialsRepository: MaterialsRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)

  val state = materialsRepository.observeMaterials()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val refreshing = isRefreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      isRefreshing.value = true
      materialsRepository.refreshMaterials(force = true)
      isRefreshing.value = false
    }
  }

  fun openAsset(
    item: MaterialItem,
    onUrl: (String) -> Unit,
    onTextPreview: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch {
      materialsRepository.openAsset(item)
        .onSuccess { asset ->
          val url = asset.sourceUrl
          val preview = asset.textPreview
          when {
            url != null -> onUrl(url)
            preview != null -> onTextPreview(preview)
            else -> onError("Nessun contenuto disponibile")
          }
        }
        .onFailure { onError(it.message ?: "Errore") }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsRoute(
  onBack: (() -> Unit)? = null,
  viewModel: MaterialsViewModel = hiltViewModel(),
) {
  val items by viewModel.state.collectAsStateWithLifecycle()
  val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()
  var selectedItem by remember { mutableStateOf<MaterialItem?>(null) }
  var assetPreviewText by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Didattica",
        subtitle = "Materiali condivisi dai docenti, link a risorse esterne e file da scaricare.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      if (items.isEmpty() && !isRefreshing) {
        EmptyState(
          title = "Nessun materiale",
          detail = "Non ci sono ancora file o link condivisi dai tuoi professori.",
          modifier = Modifier.padding(20.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(items, key = { it.id }) { item ->
            RegisterListRow(
              title = item.title,
              subtitle = item.teacherName,
              eyebrow = item.folderName,
              meta = item.sharedAt,
              tone = ExpressiveTone.Info,
              onClick = { selectedItem = item },
              badge = {
                StatusBadge(if (item.objectType == "link") "LINK" else "FILE", tone = ExpressiveTone.Info)
              },
            )
          }
        }
      }
    }
  }

  selectedItem?.let { item ->
    ModalBottomSheet(onDismissRequest = { selectedItem = null; assetPreviewText = null }) {
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
        Button(
          onClick = {
            viewModel.openAsset(
              item = item,
              onUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
              onTextPreview = { text -> assetPreviewText = text },
              onError = {},
            )
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            if (item.objectType == "link") Icons.Rounded.Link else Icons.Rounded.Download,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
          )
          Text(if (item.objectType == "link") "Vai al link" else "Scarica file")
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
  onBack: (() -> Unit)? = null,
  viewModel: HomeworkViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Compiti",
        subtitle = "Compiti assegnati dai docenti con data di consegna e dettaglio.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      if (state.homeworks.isEmpty() && !state.isRefreshing) {
        EmptyState(
          title = "Nessun compito",
          detail = "Non ci sono compiti assegnati al momento.",
          modifier = Modifier.padding(20.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(state.homeworks, key = { it.id }) { item ->
            RegisterListRow(
              title = item.subject,
              subtitle = item.description,
              eyebrow = "SCADENZA",
              meta = item.dueDate,
              tone = ExpressiveTone.Warning,
              onClick = { viewModel.selectHomework(item) },
              badge = { StatusBadge("COMPITO", tone = ExpressiveTone.Warning) },
            )
          }
        }
      }
    }
  }

  state.selectedHomework?.let { hw ->
    ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = hw.subject, style = MaterialTheme.typography.headlineSmall)
        if (state.isLoadingDetail) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.selectedDetail?.let { detail ->
          Text(text = detail.fullText, style = MaterialTheme.typography.bodyMedium)
          detail.assignedDate?.let {
            Text(
              text = "Assegnato: $it",
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

// ─────────────────────────────────────────────────────────────────────────────
// MEETINGS (COLLOQUI) — placeholder mantenuto
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsRoute(onBack: (() -> Unit)? = null) {
  Scaffold(
    topBar = { ExpressiveTopHeader(title = "Colloqui", onBack = onBack) },
  ) { padding ->
    EmptyState(
      title = "In arrivo",
      detail = "Questa sezione sarà disponibile a breve.",
      modifier = Modifier.padding(padding).padding(20.dp),
    )
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// DOCUMENTS (DOCUMENTI E LIBRI)
// ─────────────────────────────────────────────────────────────────────────────

data class DocumentsUiState(
  val documents: List<DocumentItem> = emptyList(),
  val schoolbookCourses: List<SchoolbookCourse> = emptyList(),
  val isRefreshing: Boolean = false,
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val isOpeningDocument: Boolean = false,
  val lastError: String? = null,
)

private data class DocumentsUiExtras(
  val isRefreshing: Boolean = false,
  val selectedDocument: DocumentItem? = null,
  val selectedAsset: DocumentAsset? = null,
  val isOpeningDocument: Boolean = false,
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
    extras,
  ) { docs, books, ex ->
    DocumentsUiState(docs, books, ex.isRefreshing, ex.selectedDocument, ex.selectedAsset, ex.isOpeningDocument, ex.lastError)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

  init {
    refresh(force = false, showIndicator = false)
  }

  fun refresh() = refresh(force = true, showIndicator = true)

  fun openDocument(doc: DocumentItem) {
    extras.update { it.copy(selectedDocument = doc, selectedAsset = null, lastError = null) }
    viewModelScope.launch {
      extras.update { it.copy(isOpeningDocument = true) }
      documentsRepository.openDocument(doc)
        .onSuccess { asset -> extras.update { it.copy(selectedAsset = asset) } }
        .onFailure { e -> extras.update { it.copy(lastError = e.message ?: "Errore apertura documento") } }
      extras.update { it.copy(isOpeningDocument = false) }
    }
  }

  fun dismissDocument() {
    extras.update { it.copy(selectedDocument = null, selectedAsset = null, lastError = null) }
  }

  fun queueDownload(doc: DocumentItem) = viewModelScope.launch {
    documentsRepository.queueDownload(doc)
  }

  private fun refresh(force: Boolean, showIndicator: Boolean) = viewModelScope.launch {
    if (showIndicator) extras.update { it.copy(isRefreshing = true) }
    documentsRepository.refreshDocuments(force)
    extras.update { it.copy(isRefreshing = false) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsRoute(
  onBack: (() -> Unit)? = null,
  viewModel: DocumentsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable { mutableStateOf("Documenti") }
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Documenti e libri",
        subtitle = "Documenti della scuola, pagelle e libri scolastici adottati.",
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          ExpressivePillTabs(
            options = listOf("Documenti", "Libri"),
            selected = selectedTab,
            onSelect = { selectedTab = it },
          )
        }

        if (selectedTab == "Documenti") {
          if (state.documents.isEmpty() && !state.isRefreshing) {
            item {
              EmptyState(
                title = "Nessun documento",
                detail = "Non ci sono ancora documenti disponibili.",
              )
            }
          } else {
            items(state.documents, key = { it.id }) { doc ->
              RegisterListRow(
                title = doc.title,
                subtitle = doc.detail,
                tone = ExpressiveTone.Info,
                onClick = { viewModel.openDocument(doc) },
                badge = { StatusBadge("DOCUMENTO", tone = ExpressiveTone.Info) },
              )
            }
          }
        } else {
          if (state.schoolbookCourses.isEmpty() && !state.isRefreshing) {
            item {
              EmptyState(
                title = "Nessun libro",
                detail = "Non ci sono libri scolastici disponibili per quest'anno.",
              )
            }
          } else {
            state.schoolbookCourses.forEach { course ->
              item(key = "header_${course.id}") {
                ExpressiveAccentLabel(course.title)
              }
              items(course.books, key = { it.id }) { book ->
                val bookTone = when {
                  book.alreadyOwned -> ExpressiveTone.Success
                  book.toBuy -> ExpressiveTone.Warning
                  else -> ExpressiveTone.Neutral
                }
                val bookBadge = when {
                  book.alreadyOwned -> "POSSEDUTO"
                  book.toBuy -> "DA ACQUISTARE"
                  else -> "INFO"
                }
                RegisterListRow(
                  title = book.title,
                  subtitle = book.author ?: "—",
                  eyebrow = book.subject,
                  meta = "ISBN: ${book.isbn}",
                  tone = bookTone,
                  badge = { StatusBadge(bookBadge, tone = bookTone) },
                )
              }
            }
          }
        }
      }
    }
  }

  state.selectedDocument?.let { doc ->
    ModalBottomSheet(onDismissRequest = viewModel::dismissDocument) {
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
              CircularProgressIndicator()
            }
          }
          state.selectedAsset != null -> {
            val asset = state.selectedAsset!!
            asset.textPreview?.let {
              Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            asset.sourceUrl?.let { url ->
              Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Apri documento")
              }
            }
            if (asset.fileName != null) {
              OutlinedButton(
                onClick = { viewModel.queueDownload(doc) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Scarica")
              }
            }
          }
          state.lastError != null -> {
            val errorMsg = state.lastError!!
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Button(
              onClick = { viewModel.openDocument(doc) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Riprova")
            }
          }
          else -> {
            Button(
              onClick = { viewModel.openDocument(doc) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Apri")
            }
          }
        }
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
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  LaunchedEffect(initialImportPayload) {
    if (!initialImportPayload.isNullOrBlank()) {
      viewModel.importPayload(initialImportPayload)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = "Media studente",
        subtitle = "Punteggio composito calcolato su media voti, frequenza e costanza.",
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    },
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.fillMaxSize().padding(paddingValues),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        state.currentScore?.let { score ->
          item {
            ExpressiveHeroCard(
              title = "${score.score.roundToInt()}/100",
              subtitle = score.label,
            )
          }
          if (score.components.isNotEmpty()) {
            item { ExpressiveAccentLabel("Componenti") }
            items(score.components, key = { it.title }) { component ->
              MetricTile(
                label = component.title,
                value = "%.1f / %.0f".format(component.value, component.maxValue),
                detail = "Peso ${(component.weight * 100).roundToInt()}%",
              )
            }
          }
        } ?: item {
          EmptyState(
            title = "Punteggio non disponibile",
            detail = "Il punteggio verrà calcolato dopo il primo aggiornamento dei dati.",
          )
        }

        item { ExpressiveAccentLabel("Azioni") }
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
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
              enabled = state.currentScore != null && !state.isExporting,
            ) {
              Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
              Text(if (state.isExporting) "Esportando..." else "Esporta punteggio")
            }
            OutlinedButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (text.isNotBlank()) viewModel.importPayload(text)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Importa da clipboard")
            }
          }
        }

        if (state.snapshots.size > 1) {
          item { ExpressiveAccentLabel("Storico") }
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
            RegisterListRow(
              title = "${snap.score.roundToInt()}/100",
              subtitle = snap.label,
              meta = dateLabel,
              tone = ExpressiveTone.Neutral,
            )
          }
        }

        state.lastMessage?.let { msg ->
          item {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }

  state.importResult?.let { comparison ->
    ModalBottomSheet(onDismissRequest = viewModel::dismissImport) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Confronto punteggio", style = MaterialTheme.typography.headlineSmall)
        MetricTile(
          label = "Punteggio corrente",
          value = "${comparison.current.score.roundToInt()}/100",
          detail = comparison.current.label,
          tone = ExpressiveTone.Info,
        )
        MetricTile(
          label = "Punteggio importato",
          value = "${comparison.imported.score.roundToInt()}/100",
          detail = comparison.imported.label,
          tone = ExpressiveTone.Neutral,
        )
        val diffTone = when {
          comparison.difference > 0 -> ExpressiveTone.Success
          comparison.difference < 0 -> ExpressiveTone.Danger
          else -> ExpressiveTone.Neutral
        }
        MetricTile(
          label = "Differenza",
          value = "${if (comparison.difference >= 0) "+" else ""}${"%.1f".format(comparison.difference)}",
          detail = if (comparison.difference > 0) "In miglioramento" else if (comparison.difference < 0) "In peggioramento" else "Invariato",
          tone = diffTone,
        )
      }
    }
  }
}
