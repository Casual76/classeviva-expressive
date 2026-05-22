package dev.antigravity.classevivaexpressive.feature.dashboard

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
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

internal data class DashboardLessonPresentation(
  val subtitle: String,
  val timeRangeLabel: String,
  val tone: ExpressiveTone,
  val badgeLabel: String,
  val badgeTone: ExpressiveTone,
)

internal fun Lesson.toDashboardPresentation(): DashboardLessonPresentation {
  val topicText = topic?.trim().orEmpty()
  val start = runCatching { java.time.LocalTime.parse(time) }.getOrNull()
  val timeRangeLabel = if (start != null) {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    "${start.format(formatter)} - ${start.plusMinutes(durationMinutes.toLong()).format(formatter)}"
  } else {
    time
  }
  return DashboardLessonPresentation(
    subtitle = when {
      topicText.isNotBlank() -> topicText
      isSigned -> "Lezione firmata senza argomento"
      else -> "Argomento non disponibile"
    },
    timeRangeLabel = timeRangeLabel,
    tone = if (isSigned || topicText.isNotBlank()) ExpressiveTone.Success else ExpressiveTone.Neutral,
    badgeLabel = if (isSigned) "FIRMATA" else "${durationMinutes} min",
    badgeTone = if (isSigned) ExpressiveTone.Success else ExpressiveTone.Info,
  )
}

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
  val unseenGradeIds = remember(snapshot.unseenGrades) { snapshot.unseenGrades.mapTo(mutableSetOf()) { it.id } }
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
        modifier = Modifier.fillMaxSize(),
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
              animatePress = true
            )
          }
        }
        item { ExpressiveAccentLabel("Ultime novità") }
        item {
          dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard(
              modifier = Modifier.fillMaxWidth(),
              animateContent = false,
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
                  modifier = Modifier.weight(1f),
                  onClick = onNavigateGrades,
                  animatePress = true,
                )
                MetricTile(
                  label = "Bacheca",
                  value = snapshot.unreadCommunications.size.toString(),
                  detail = "Non lette",
                  tone = if (snapshot.unreadCommunications.isNotEmpty()) ExpressiveTone.Warning else ExpressiveTone.Neutral,
                  modifier = Modifier.weight(1f),
                  onClick = onNavigateCommunications,
                  animatePress = true,
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
        
        item { ExpressiveAccentLabel("Lezioni di oggi") }
        if (snapshot.todayLessons.isEmpty()) {
            item {
                EmptyState(
                  title = "Nessuna lezione oggi",
                  detail = "Non ci sono lezioni previste o registrate per la giornata odierna.",
                )
            }
        } else {
            items(snapshot.todayLessons, key = { it.id }) { lesson ->
                val presentation = remember(lesson) { lesson.toDashboardPresentation() }
                RegisterListRow(
                  title = lesson.subject,
                  subtitle = presentation.subtitle,
                  eyebrow = presentation.timeRangeLabel,
                  meta = listOfNotNull(
                    lesson.teacher?.takeIf(String::isNotBlank),
                  ).joinToString(" / "),
                  tone = presentation.tone,
                  badge = {
                    StatusBadge(
                      label = presentation.badgeLabel,
                      tone = presentation.badgeTone,
                    )
                  },
                )
            }
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
            val isUnseen = unseenGradeIds.contains(grade.id)

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
              animatePress = true
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
              animatePress = true
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
              animatePress = true
            )
          }
        }
        if (!snapshot.syncStatus.message.isNullOrBlank()) {
          item {
            Text(
              text = snapshot.syncStatus.message.orEmpty(),
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
