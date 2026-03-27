package dev.antigravity.classevivaexpressive.feature.studentscore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.QuickAction
import dev.antigravity.classevivaexpressive.core.designsystem.theme.SectionTitle
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreComparison
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentScoreUiState(
  val current: StudentScoreSnapshot? = null,
  val snapshots: List<StudentScoreSnapshot> = emptyList(),
  val comparison: StudentScoreComparison? = null,
  val lastMessage: String? = null,
)

@HiltViewModel
class StudentScoreViewModel @Inject constructor(
  private val studentScoreRepository: StudentScoreRepository,
) : ViewModel() {
  private val comparison = MutableStateFlow<StudentScoreComparison?>(null)
  private val lastMessage = MutableStateFlow<String?>(null)

  val state = combine(
    studentScoreRepository.observeCurrentScore(),
    studentScoreRepository.observeSnapshots(),
    comparison,
    lastMessage,
  ) { current, snapshots, compared, message ->
    StudentScoreUiState(
      current = current,
      snapshots = snapshots,
      comparison = compared,
      lastMessage = message,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentScoreUiState())

  init {
    viewModelScope.launch { studentScoreRepository.refreshStudentScore() }
  }

  fun refresh() {
    viewModelScope.launch { studentScoreRepository.refreshStudentScore(force = true) }
  }

  fun importPayload(payload: String) {
    viewModelScope.launch {
      studentScoreRepository.importPayload(payload)
        .onSuccess { comparison.value = it }
        .onFailure { lastMessage.value = it.message ?: "Payload Student Score non valido." }
    }
  }

  fun clearComparison() {
    comparison.value = null
  }

  fun clearMessage() {
    lastMessage.value = null
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentScoreRoute(
  initialImportPayload: String? = null,
  modifier: Modifier = Modifier,
  viewModel: StudentScoreViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showImportDialog by rememberSaveable { mutableStateOf(false) }
  var manualPayload by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(initialImportPayload) {
    initialImportPayload?.takeIf { it.isNotBlank() }?.let(viewModel::importPayload)
  }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      ExpressiveHeroCard(
        title = state.current?.let { "Student Score ${"%.1f".format(it.score)}" } ?: "Student Score",
        subtitle = state.current?.label ?: "Valutazione sintetica locale, esportabile e confrontabile via deep link.",
      )
    }
    item {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        QuickAction(label = "Aggiorna", onClick = viewModel::refresh)
        QuickAction(label = "Importa", onClick = { showImportDialog = true })
        state.current?.let { current ->
          QuickAction(
            label = "Condividi",
            onClick = {
              val deepLink = "classevivaexpressive://student-score/import?payload=${Uri.encode(current.sharePayload)}"
              val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, deepLink)
              startActivity(context, Intent.createChooser(intent, "Condividi Student Score"), null)
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
          detail = "Sincronizza l'app per calcolare il punteggio corrente.",
        )
      }
    } else {
      items(current.components, key = { it.title }) { component ->
        AppListItem(
          title = component.title,
          subtitle = "${"%.1f".format(component.value)} / ${"%.1f".format(component.maxValue)}",
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
          Text("Attuale: ${"%.1f".format(comparison.current.score)} - ${comparison.current.label}")
          Text("Importato: ${"%.1f".format(comparison.imported.score)} - ${comparison.imported.label}")
          Text("Delta: ${if (comparison.difference >= 0) "+" else ""}${"%.1f".format(comparison.difference)}")
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
          title = "Nessuno snapshot ancora",
          detail = "Dopo i refresh verranno salvate copie locali del tuo Student Score.",
        )
      }
    } else {
      items(state.snapshots, key = { it.computedAtEpochMillis }) { snapshot ->
        AppListItem(
          title = "${"%.1f".format(snapshot.score)} - ${snapshot.label}",
          subtitle = snapshot.computedAtEpochMillis.toString(),
          supporting = "${snapshot.components.size} componenti",
        )
      }
    }
    state.lastMessage?.let { message ->
      item { Text(message) }
      item {
        TextButton(onClick = viewModel::clearMessage) {
          Text("Nascondi messaggio")
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
            val normalized = manualPayload.substringAfter("payload=", manualPayload)
            viewModel.importPayload(normalized)
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
