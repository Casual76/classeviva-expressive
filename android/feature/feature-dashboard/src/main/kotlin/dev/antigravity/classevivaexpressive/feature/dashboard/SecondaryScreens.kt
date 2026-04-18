package dev.antigravity.classevivaexpressive.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
              }
            )
          }
        }
      }
    }
  }

  selectedItem?.let { item ->
    ModalBottomSheet(onDismissRequest = { selectedItem = null }) {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = "Condiviso da ${item.teacherName} in ${item.folderName}", style = MaterialTheme.typography.bodyMedium)
        
        Button(
          onClick = { /* TODO: Implement download/open */ },
          modifier = Modifier.fillMaxSize()
        ) {
          Icon(
            if (item.objectType == "link") Icons.Rounded.Link else Icons.Rounded.Download,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
          )
          Text(if (item.objectType == "link") "Vai al link" else "Scarica file")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkRoute(onBack: (() -> Unit)? = null) {
    Scaffold(
        topBar = { ExpressiveTopHeader(title = "Compiti", onBack = onBack) }
    ) { padding ->
        EmptyState(
            title = "In arrivo",
            detail = "Questa sezione sarà disponibile a breve.",
            modifier = Modifier.padding(padding).padding(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsRoute(onBack: (() -> Unit)? = null) {
    Scaffold(
        topBar = { ExpressiveTopHeader(title = "Colloqui", onBack = onBack) }
    ) { padding ->
        EmptyState(
            title = "In arrivo",
            detail = "Questa sezione sarà disponibile a breve.",
            modifier = Modifier.padding(padding).padding(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsRoute(onBack: (() -> Unit)? = null) {
    Scaffold(
        topBar = { ExpressiveTopHeader(title = "Documenti", onBack = onBack) }
    ) { padding ->
        EmptyState(
            title = "In arrivo",
            detail = "Questa sezione sarà disponibile a breve.",
            modifier = Modifier.padding(padding).padding(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScoreRoute(initialImportPayload: String? = null) {
    Scaffold(
        topBar = { ExpressiveTopHeader(title = "Media Studente") }
    ) { padding ->
        EmptyState(
            title = "In arrivo",
            detail = "Questa sezione sarà disponibile a breve.",
            modifier = Modifier.padding(padding).padding(20.dp)
        )
    }
}
