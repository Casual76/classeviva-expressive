package dev.antigravity.classevivaexpressive.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.EmptyState
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveEditorialCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveMiniChart
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveStatusDot
import dev.antigravity.classevivaexpressive.core.designsystem.theme.scoreColor
import dev.antigravity.classevivaexpressive.core.designsystem.theme.subjectPalette
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
  private val dashboardRepository: DashboardRepository,
) : ViewModel() {
  val state = dashboardRepository.observeDashboard()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardSnapshot())

  init {
    viewModelScope.launch { dashboardRepository.refreshDashboard() }
  }
}

@Composable
fun DashboardRoute(
  onNavigateGrades: () -> Unit,
  onNavigateAgenda: () -> Unit,
  onNavigateLessons: () -> Unit,
  onNavigateMore: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DashboardViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val chartPoints = remember(state) {
    state.recentGrades.mapNotNull { it.numericValue?.toFloat() }.take(6).takeIf { it.size >= 4 } ?: run {
      listOf(
        state.todayLessons.size.toFloat(),
        state.upcomingItems.size.toFloat(),
        state.unreadCommunications.size.toFloat(),
        state.highlightedNotes.size.toFloat(),
        state.recentAbsences.size.toFloat(),
        state.schoolDocuments.size.toFloat(),
      )
    }.ifEmpty { listOf(3f, 1f, 4f, 4f, 3f, 0f) }
  }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      DashboardHero(
        headline = state.headline.ifBlank { "Good evening, ${state.profile.name.ifBlank { "Alessio" }}." },
        dateLabel = state.subheadline.ifBlank { state.profile.schoolYear.ifBlank { "Today" } },
        points = chartPoints,
      )
    }
    item { SectionTitle("Last grades") }
    if (state.recentGrades.isEmpty()) {
      item {
        EmptyState(
          title = "No recent grades",
          detail = "Recent grades will appear here after sync.",
          modifier = Modifier.padding(horizontal = 20.dp),
        )
      }
    } else {
      items(state.recentGrades.take(3), key = { it.id }) { grade ->
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surfaceContainer,
          onClick = onNavigateGrades,
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ExpressiveStatusDot(color = scoreColor(grade.numericValue), modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = grade.subject,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = grade.date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = grade.valueLabel,
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
    item { SectionTitle("Last lessons") }
    item {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(state.todayLessons.take(4), key = { it.id }) { lesson ->
          LessonHighlightCard(
            title = lesson.subject ?: lesson.title,
            subtitle = lesson.title,
            detail = lesson.time ?: "1H",
            onClick = onNavigateLessons,
          )
        }
      }
    }
    item { SectionTitle("Next events") }
    if (state.upcomingItems.isEmpty()) {
      item {
        EmptyState(
          title = "Nothing urgent right now",
          detail = "Homework, tests and events will appear here in priority order.",
          modifier = Modifier.padding(horizontal = 20.dp),
        )
      }
    } else {
      items(state.upcomingItems.take(4), key = { it.id }) { item ->
        ExpressiveEditorialCard(
          modifier = Modifier.padding(horizontal = 20.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = item.detail ?: item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
    if (!state.syncStatus.message.isNullOrBlank()) {
      item {
        Text(
          text = state.syncStatus.message.orEmpty(),
          modifier = Modifier.padding(horizontal = 20.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun DashboardHero(
  headline: String,
  dateLabel: String,
  points: List<Float>,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(264.dp)
      .background(MaterialTheme.colorScheme.primary),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 22.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = headline,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = dateLabel,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    ExpressiveEditorialCard(
      modifier = Modifier
        .padding(horizontal = 20.dp)
        .align(Alignment.BottomCenter),
    ) {
      ExpressiveMiniChart(
        points = points,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.height(148.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { label ->
          Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(
  title: String,
) {
  Text(
    text = title,
    modifier = Modifier.padding(horizontal = 20.dp),
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    fontWeight = FontWeight.Medium,
  )
}

@Composable
private fun LessonHighlightCard(
  title: String,
  subtitle: String,
  detail: String,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier
      .width(210.dp)
      .height(136.dp),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
    onClick = onClick,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .background(Color.White.copy(alpha = 0.96f), shape = MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
      ) {
        ExpressiveStatusDot(color = subjectPalette(title), modifier = Modifier.size(20.dp))
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.92f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = detail,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.86f),
      )
    }
  }
}

@Composable
private fun IconBadge() {
  Box(
    modifier = Modifier
      .size(44.dp)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Rounded.CalendarMonth,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
