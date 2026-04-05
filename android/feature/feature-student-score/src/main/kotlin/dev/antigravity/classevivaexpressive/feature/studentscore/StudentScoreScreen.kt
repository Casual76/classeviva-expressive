package dev.antigravity.classevivaexpressive.feature.studentscore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.AppListItem
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.QuickAction
import dev.antigravity.classevivaexpressive.core.designsystem.theme.SectionTitle
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComparison
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val italianLocale: Locale = Locale.forLanguageTag("it-IT")

data class StudentScoreUiState(
  val current: StudentScoreSnapshot? = null,
  val snapshots: List<StudentScoreSnapshot> = emptyList(),
  val comparison: StudentScoreComparison? = null,
  val lastMessage: String? = null,
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class StudentScoreViewModel @Inject constructor(
  private val studentScoreRepository: StudentScoreRepository,
) : ViewModel() {
  private val comparison = MutableStateFlow<StudentScoreComparison?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)
  private val isRefreshing = MutableStateFlow(false)

  val state = combine(
    studentScoreRepository.observeCurrentScore(),
    studentScoreRepository.observeSnapshots(),
    comparison,
    lastMessage,
    isRefreshing,
  ) { current, snapshots, compared, message, refreshing ->
    StudentScoreUiState(
      current = current,
      snapshots = snapshots,
      comparison = compared,
      lastMessage = message,
      isRefreshing = refreshing,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentScoreUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  fun importPayload(payload: String) {
    viewModelScope.launch {
      studentScoreRepository.importPayload(payload)
        .onSuccess {
          comparison.value = it
          lastMessage.value = null
        }
        .onFailure { lastMessage.value = it.message ?: "Payload Student Score non valido." }
    }
  }

  fun clearComparison() {
    comparison.value = null
  }

  fun clearMessage() {
    lastMessage.value = null
  }

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      studentScoreRepository.refreshStudentScore(force = force)
        .onFailure { lastMessage.value = it.message ?: "Impossibile aggiornare lo Student Score." }
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentScoreRoute(
  initialImportPayload: String? = null,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  viewModel: StudentScoreViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showImportDialog by rememberSaveable { mutableStateOf(false) }
  var manualPayload by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(initialImportPayload) {
    initialImportPayload?.takeIf(String::isNotBlank)?.let(viewModel::importPayload)
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
          title = "Student Score",
          subtitle = "Riepilogo sintetico locale, confrontabile via payload o deep link importato.",
          onBack = onBack,
          actions = {
            IconButton(onClick = viewModel::refresh) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
            }
          },
        )
      }
      item {
        ExpressiveHeroCard(
          title = state.current?.let { "Score ${it.score.format1()}" } ?: "Score non disponibile",
          subtitle = state.current?.label ?: "Aggiorna l'app per generare il riepilogo corrente.",
        )
      }
      item {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickAction(label = "Aggiorna", onClick = viewModel::refresh)
          QuickAction(label = "Importa", onClick = { showImportDialog = true })
          state.current?.takeIf { it.sharePayload.isNotBlank() }?.let { current ->
            QuickAction(
              label = "Condividi",
              onClick = {
                shareStudentScore(
                  context = context,
                  payload = current.sharePayload,
                )
              },
            )
          }
        }
      }
      item {
        SectionTitle(
          eyebrow = "Breakdown",
          title = "Componenti del punteggio",
        )
      }
      val current = state.current
      if (current == null) {
        item {
          EmptyState(
            title = "Score non disponibile",
            detail = "Sincronizza dati e statistiche per calcolare il riepilogo corrente.",
          )
        }
      } else {
        items(current.components, key = { it.title }) { component ->
          AppListItem(
            title = component.title,
            subtitle = "${component.value.format1()} / ${component.maxValue.format1()}",
            supporting = "Peso ${(component.weight * 100).toInt()}%",
          )
        }
      }
      state.comparison?.let { comparison ->
        item {
          SectionTitle(
            eyebrow = "Confronto",
            title = "Differenza con score importato",
          )
        }
        item {
          ExpressiveCard(highlighted = comparison.difference < 0) {
            Text("Attuale: ${comparison.current.score.format1()} / ${comparison.current.label}")
            Text("Importato: ${comparison.imported.score.format1()} / ${comparison.imported.label}")
            Text(
              text = "Delta: ${if (comparison.difference >= 0) "+" else ""}${comparison.difference.format1()}",
              color = if (comparison.difference >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = viewModel::clearComparison) {
              Text("Chiudi confronto")
            }
          }
        }
      }
      item {
        SectionTitle(
          eyebrow = "Storico",
          title = "Snapshot salvati",
        )
      }
      if (state.snapshots.isEmpty()) {
        item {
          EmptyState(
            title = "Nessuno snapshot salvato",
            detail = "Dopo i refresh verranno archiviate copie locali dello Student Score.",
          )
        }
      } else {
        items(state.snapshots, key = { it.computedAtEpochMillis }) { snapshot ->
          AppListItem(
            title = "${snapshot.score.format1()} / ${snapshot.label}",
            subtitle = snapshot.computedAtEpochMillis.toReadableDateTime(),
            supporting = "${snapshot.components.size} componenti",
          )
        }
      }
      state.lastMessage?.let { message ->
        item {
          ExpressiveCard(highlighted = true) {
            Text(message)
            TextButton(onClick = viewModel::clearMessage) {
              Text("Nascondi messaggio")
            }
          }
        }
      }
    }
  }

  if (showImportDialog) {
    AlertDialog(
      onDismissRequest = { showImportDialog = false },
      title = { Text("Importa Student Score") },
      text = {
        OutlinedTextField(
          value = manualPayload,
          onValueChange = { manualPayload = it },
          label = { Text("Payload o deep link") },
          minLines = 4,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.importPayload(normalizeImportPayload(manualPayload))
            showImportDialog = false
          },
          enabled = manualPayload.isNotBlank(),
        ) {
          Text("Importa")
        }
      },
      dismissButton = {
        TextButton(onClick = { showImportDialog = false }) {
          Text("Annulla")
        }
      },
    )
  }
}

private fun shareStudentScore(
  context: android.content.Context,
  payload: String,
) {
  val deepLink = "classevivaexpressive://student-score/import?payload=${Uri.encode(payload)}"
  val intent = Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, deepLink)
  startActivity(context, Intent.createChooser(intent, "Condividi Student Score"), null)
}

private fun normalizeImportPayload(raw: String): String {
  val trimmed = raw.trim()
  val deepLinkPayload = runCatching { Uri.parse(trimmed).getQueryParameter("payload") }.getOrNull()
  return deepLinkPayload?.takeIf(String::isNotBlank) ?: trimmed.substringAfter("payload=", trimmed)
}

private fun Long.toReadableDateTime(): String {
  return Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", italianLocale))
}

private fun Double.format1(): String = String.format(italianLocale, "%.1f", this)
