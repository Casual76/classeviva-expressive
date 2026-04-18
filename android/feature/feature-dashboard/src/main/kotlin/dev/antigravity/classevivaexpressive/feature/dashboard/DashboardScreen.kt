package dev.antigravity.classevivaexpressive.feature.dashboard

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.bouncyClickable
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DashboardRoute(
  onNavigateGrades: () -> Unit,
  onNavigateAgenda: () -> Unit,
  onNavigateLessons: () -> Unit,
  onNavigateCommunications: () -> Unit,
  onOpenGrade: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DashboardViewModel = hiltViewModel(),
  sharedTransitionScope: SharedTransitionScope? = null,
  animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snapshot = state.snapshot
  val recentGrades = remember(snapshot.recentGrades) { snapshot.recentGrades.take(4) }
  val upcomingItems = remember(snapshot.upcomingItems) { snapshot.upcomingItems.take(4) }
  val unreadCommunications = remember(snapshot.unreadCommunications) { snapshot.unreadCommunications.take(3) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val firstName = snapshot.profile.name.takeIf { it.isNotBlank() }?.split(" ")?.firstOrNull()?.replaceFirstChar { it.titlecase() } ?: "Studente"
  val titleText = snapshot.headline.ifBlank { "Ciao, $firstName" }
  Scaffold(
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ExpressiveTopHeader(
        title = titleText,
        subtitle = snapshot.subheadline.ifBlank { "Lezioni, voti e bacheca restano in primo piano." },
        scrollBehavior = scrollBehavior,
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
          }
        },
      )
    }
  ) { paddingValues ->
    PullToRefreshBox(
      modifier = Modifier.padding(paddingValues).fillMaxSize(),
      isRefreshing = state.isRefreshing,
      onRefresh = viewModel::refresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().animateContentSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
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
              modifier = Modifier.animateItem()
            )
          }
        }
        item { ExpressiveAccentLabel("Ultime novità", modifier = Modifier.animateItem()) }
        item {
          dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard(
              modifier = Modifier.fillMaxWidth().animateItem(),
          ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                MetricTile(
                  label = "Voti nuovi",
                  value = snapshot.unseenGrades.size.toString(),
                  detail = "Da aprire",
                  tone = if (snapshot.unseenGrades.isNotEmpty()) ExpressiveTone.Primary else ExpressiveTone.Neutral,
                  modifier = Modifier.weight(1f).bouncyClickable(onClick = onNavigateGrades),
                )
                MetricTile(
                  label = "Bacheca",
                  value = snapshot.unreadCommunications.size.toString(),
                  detail = "Non lette",
                  tone = if (snapshot.unreadCommunications.isNotEmpty()) ExpressiveTone.Warning else ExpressiveTone.Neutral,
                  modifier = Modifier.weight(1f).bouncyClickable(onClick = onNavigateCommunications),
                )
                MetricTile(
                  label = "Note non lette",
                  value = snapshot.highlightedNotes.size.toString(),
                  detail = "Avvisi",
                  tone = if (snapshot.highlightedNotes.isNotEmpty()) ExpressiveTone.Danger else ExpressiveTone.Neutral,
                  modifier = Modifier.weight(1f),
                )
              }
          }
        }
        
        item { ExpressiveAccentLabel("Lezioni di oggi", modifier = Modifier.animateItem()) }
        if (snapshot.todayLessons.isEmpty()) {
            item {
                EmptyState(
                  title = "Nessuna lezione oggi",
                  detail = "Non ci sono lezioni previste o registrate per la giornata odierna.",
                  modifier = Modifier.animateItem()
                )
            }
        } else {
            items(snapshot.todayLessons, key = { it.id }) { lesson ->
                RegisterListRow(
                  title = lesson.subject,
                  subtitle = lesson.topic?.takeIf(String::isNotBlank) ?: "Argomento non disponibile",
                  eyebrow = "${java.time.LocalTime.parse(lesson.time).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} - ${java.time.LocalTime.parse(lesson.time).plusMinutes(lesson.durationMinutes.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                  meta = listOfNotNull(
                    lesson.teacher?.takeIf(String::isNotBlank),
                  ).joinToString(" / "),
                  tone = if (lesson.topic.isNullOrBlank()) ExpressiveTone.Neutral else ExpressiveTone.Success,
                  badge = {
                    StatusBadge(
                      label = "${lesson.durationMinutes} min",
                      tone = ExpressiveTone.Info,
                    )
                  },
                  modifier = Modifier.animateItem()
                )
            }
        }
        item { ExpressiveAccentLabel("Voti recenti", modifier = Modifier.animateItem()) }
        if (recentGrades.isEmpty()) {
          item {
            EmptyState(
              title = "Nessun voto disponibile",
              detail = "I voti recenti appariranno qui dopo la prossima sincronizzazione.",
              modifier = Modifier.animateItem()
            )
          }
        } else {
          items(recentGrades, key = { it.id }) { grade ->
            val isUnseen = snapshot.unseenGrades.any { it.id == grade.id }
            val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
              with(sharedTransitionScope) {
                Modifier.sharedElement(
                  rememberSharedContentState(key = "grade-${grade.id}"),
                  animatedVisibilityScope = animatedVisibilityScope
                )
              }
            } else Modifier

            RegisterListRow(
              title = grade.subject,
              subtitle = grade.type.ifBlank { "Valutazione" },
              eyebrow = grade.date,
              meta = grade.description ?: grade.notes,
              tone = if (isUnseen) ExpressiveTone.Primary else ExpressiveTone.Neutral,
              onClick = { onOpenGrade(grade.id) },
              badge = {
                StatusBadge(
                  label = grade.valueLabel,
                  tone = if (isUnseen) ExpressiveTone.Primary else ExpressiveTone.Neutral,
                )
              },
              modifier = Modifier.animateItem().then(sharedModifier)
            )
          }
        }
        item { ExpressiveAccentLabel("In arrivo", modifier = Modifier.animateItem()) }
        if (upcomingItems.isEmpty()) {
          item {
            EmptyState(
              title = "Nessun elemento imminente",
              detail = "I prossimi compiti, verifiche o eventi appariranno qui.",
              modifier = Modifier.animateItem()
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
              modifier = Modifier.animateItem()
            )
          }
        }
        item { ExpressiveAccentLabel("Bacheca", modifier = Modifier.animateItem()) }
        if (unreadCommunications.isEmpty()) {
          item {
            EmptyState(
              title = "Nessuna comunicazione urgente",
              detail = "I nuovi avvisi della scuola appariranno qui.",
              modifier = Modifier.animateItem()
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
              modifier = Modifier.animateItem()
            )
          }
        }
        if (!snapshot.syncStatus.message.isNullOrBlank()) {
          item {
            Text(
              text = snapshot.syncStatus.message.orEmpty(),
              modifier = Modifier.animateItem()
            )
          }
        }
        item {
          Spacer(Modifier.height(80.dp))
        }
      }
    }
  }
}
