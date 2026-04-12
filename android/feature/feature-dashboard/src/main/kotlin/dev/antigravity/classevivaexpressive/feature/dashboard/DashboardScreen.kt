package dev.antigravity.classevivaexpressive.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveAccentLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveHeroCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTopHeader
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MetricTile
import dev.antigravity.classevivaexpressive.core.designsystem.theme.RegisterListRow
import dev.antigravity.classevivaexpressive.core.designsystem.theme.StatusBadge
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
  val snapshot: DashboardSnapshot = DashboardSnapshot(),
  val isRefreshing: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
  private val dashboardRepository: DashboardRepository,
) : ViewModel() {
  private val isRefreshing = MutableStateFlow(false)

  val state = combine(
    dashboardRepository.observeDashboard(),
    isRefreshing,
  ) { snapshot, refreshing ->
    DashboardUiState(snapshot = snapshot, isRefreshing = refreshing)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

  init {
    requestRefresh(force = false, showIndicator = false)
  }

  fun refresh() {
    requestRefresh(force = true, showIndicator = true)
  }

  private fun requestRefresh(force: Boolean, showIndicator: Boolean) {
    viewModelScope.launch {
      if (showIndicator) {
        isRefreshing.value = true
      }
      dashboardRepository.refreshDashboard(force = force)
      isRefreshing.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
  onNavigateGrades: () -> Unit,
  onNavigateAgenda: () -> Unit,
  onNavigateLessons: () -> Unit,
  onNavigateCommunications: () -> Unit,
  onOpenGrade: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DashboardViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snapshot = state.snapshot
  val recentGrades = remember(snapshot.recentGrades) { snapshot.recentGrades.take(4) }
  val upcomingItems = remember(snapshot.upcomingItems) { snapshot.upcomingItems.take(4) }
  val unreadCommunications = remember(snapshot.unreadCommunications) { snapshot.unreadCommunications.take(3) }

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
          title = snapshot.headline.ifBlank { "Oggi" },
          subtitle = snapshot.subheadline.ifBlank { "Lezioni, voti e bacheca restano in primo piano." },
          actions = {
            IconButton(onClick = viewModel::refresh) {
              Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
            }
          },
        )
      }
      item {
        val firstName = snapshot.profile.name.split(" ").firstOrNull() ?: "studente"
        ExpressiveHeroCard(
          title = "Ciao, $firstName!",
          subtitle = snapshot.subheadline.ifBlank { "Lezioni, voti e bacheca restano in primo piano." },
        )
      }
      if (snapshot.averageNumeric != null) {
        item {
          RegisterListRow(
            title = "Media Generale",
            subtitle = "La tua media calcolata su tutte le materie.",
            eyebrow = "Andamento",
            tone = dev.antigravity.classevivaexpressive.core.designsystem.theme.gradeTone(snapshot.averageNumeric),
            onClick = onNavigateGrades,
            badge = {
              StatusBadge(
                label = snapshot.averageLabel,
                tone = dev.antigravity.classevivaexpressive.core.designsystem.theme.gradeTone(snapshot.averageNumeric),
              )
            },
          )
        }
      }
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MetricTile(
            label = "Lezioni oggi",
            value = snapshot.todayLessons.size.toString(),
            detail = "Tocca per aprire l'orario",
            tone = ExpressiveTone.Info,
            modifier = Modifier.weight(1f),
          )
          MetricTile(
            label = "Voti nuovi",
            value = snapshot.unseenGrades.size.toString(),
            detail = "Valutazioni non ancora aperte",
            tone = if (snapshot.unseenGrades.isNotEmpty()) ExpressiveTone.Primary else ExpressiveTone.Neutral,
            modifier = Modifier.weight(1f),
          )
          MetricTile(
            label = "Bacheca",
            value = snapshot.unreadCommunications.size.toString(),
            detail = "Comunicazioni da leggere",
            tone = if (snapshot.unreadCommunications.isNotEmpty()) ExpressiveTone.Warning else ExpressiveTone.Neutral,
            modifier = Modifier.weight(1f),
          )
        }
      }
      item { ExpressiveAccentLabel("Scorciatoie") }
      item {
        RegisterListRow(
          title = "Apri voti",
          subtitle = "Vai subito alle valutazioni recenti e non viste.",
          eyebrow = "Voti",
          tone = ExpressiveTone.Primary,
          onClick = onNavigateGrades,
          badge = { StatusBadge("VOTI", tone = ExpressiveTone.Primary) },
        )
      }
      item {
        RegisterListRow(
          title = "Apri agenda",
          subtitle = "Compiti, verifiche ed eventi del giorno selezionato.",
          eyebrow = "Agenda",
          tone = ExpressiveTone.Success,
          onClick = onNavigateAgenda,
          badge = { StatusBadge("AGENDA", tone = ExpressiveTone.Success) },
        )
      }
      item {
        RegisterListRow(
          title = "Apri orario",
          subtitle = "Lezioni del giorno e settimana corrente.",
          eyebrow = "Lezioni",
          tone = ExpressiveTone.Info,
          onClick = onNavigateLessons,
          badge = { StatusBadge("ORARIO", tone = ExpressiveTone.Info) },
        )
      }
      item {
        RegisterListRow(
          title = "Apri bacheca",
          subtitle = "Comunicazioni, note e azioni da completare.",
          eyebrow = "Bacheca",
          tone = ExpressiveTone.Warning,
          onClick = onNavigateCommunications,
          badge = { StatusBadge("BACHECA", tone = ExpressiveTone.Warning) },
        )
      }
      item { ExpressiveAccentLabel("Voti recenti") }
      if (recentGrades.isEmpty()) {
        item {
          EmptyState(
            title = "Nessun voto disponibile",
            detail = "I voti recenti appariranno qui dopo la prossima sincronizzazione.",
          )
        }
      } else {
        items(recentGrades, key = { it.id }) { grade ->
          RegisterListRow(
            title = grade.subject,
            subtitle = grade.type.ifBlank { "Valutazione" },
            eyebrow = grade.date,
            meta = grade.description ?: grade.notes,
            tone = if (snapshot.unseenGrades.any { it.id == grade.id }) ExpressiveTone.Primary else ExpressiveTone.Neutral,
            onClick = { onOpenGrade(grade.id) },
            badge = {
              StatusBadge(
                label = grade.valueLabel,
                tone = if (snapshot.unseenGrades.any { it.id == grade.id }) ExpressiveTone.Primary else ExpressiveTone.Neutral,
              )
            },
          )
        }
      }
      item { ExpressiveAccentLabel("In arrivo") }
      if (upcomingItems.isEmpty()) {
        item {
          EmptyState(
            title = "Nessun elemento imminente",
            detail = "I prossimi compiti, verifiche o eventi appariranno qui.",
          )
        }
      } else {
        items(upcomingItems, key = { it.id }) { item ->
          RegisterListRow(
            title = item.title,
            subtitle = item.subtitle,
            eyebrow = item.date,
            meta = item.detail,
            tone = ExpressiveTone.Success,
            onClick = onNavigateAgenda,
            badge = { StatusBadge("AGENDA", tone = ExpressiveTone.Success) },
          )
        }
      }
      item { ExpressiveAccentLabel("Bacheca") }
      if (unreadCommunications.isEmpty()) {
        item {
          EmptyState(
            title = "Nessuna comunicazione urgente",
            detail = "I nuovi avvisi della scuola appariranno qui.",
          )
        }
      } else {
        items(unreadCommunications, key = { it.id }) { communication ->
          RegisterListRow(
            title = communication.title,
            subtitle = communication.sender,
            eyebrow = communication.date,
            meta = communication.contentPreview,
            tone = ExpressiveTone.Warning,
            onClick = onNavigateCommunications,
            badge = { StatusBadge("NUOVA", tone = ExpressiveTone.Warning) },
          )
        }
      }
      if (!snapshot.syncStatus.message.isNullOrBlank()) {
        item {
          Text(text = snapshot.syncStatus.message.orEmpty())
        }
      }
    }
  }
}
