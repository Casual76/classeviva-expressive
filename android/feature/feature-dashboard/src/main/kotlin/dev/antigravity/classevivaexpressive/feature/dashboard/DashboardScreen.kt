package dev.antigravity.classevivaexpressive.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureHero
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FeatureIdentity
import dev.antigravity.classevivaexpressive.core.designsystem.theme.GradeCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.GradeDetailContent
import dev.antigravity.classevivaexpressive.core.designsystem.theme.gradeDateLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.nearDayLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.gradePaneTint
import dev.antigravity.classevivaexpressive.core.designsystem.theme.fluidGlassGroups
import dev.antigravity.classevivaexpressive.core.designsystem.theme.FluidGlassGroup
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ambient
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardStat
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.Lesson
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.fluidExpandOrigin
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidColumnSection
import dev.antigravity.fluidengine.ui.fluid.FluidColumnsDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidColumns
import dev.antigravity.fluidengine.ui.fluid.rememberFluidScreenMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidVividCard
import dev.antigravity.fluidengine.ui.fluid.FluidVividColors
import dev.antigravity.fluidengine.ui.fluid.rememberCurrentDate
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidQuickAction
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidSyncAction
import dev.antigravity.fluidengine.ui.theme.FluidSyncNotice
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.classevivaexpressive.core.designsystem.theme.lastSyncLabel
import dev.antigravity.classevivaexpressive.core.designsystem.theme.noticeMessage
import dev.antigravity.classevivaexpressive.core.designsystem.theme.toFluid

data class DashboardUiState(
  val snapshot: DashboardSnapshot = DashboardSnapshot(),
  val isRefreshing: Boolean = false,
)

internal data class DashboardLessonPresentation(
  val subtitle: String,
  val timeRangeLabel: String,
  val tone: FluidTone,
  val badgeLabel: String,
  val badgeTone: FluidTone,
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
    tone = if (isSigned || topicText.isNotBlank()) FluidTone.Success else FluidTone.Neutral,
    badgeLabel = if (isSigned) "FIRMATA" else "${durationMinutes} min",
    badgeTone = if (isSigned) FluidTone.Success else FluidTone.Info,
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

@OptIn(ExperimentalMaterial3Api::class)
/**
 * What the home screen's docked bar cycles through: the three numbers the page is built around,
 * and the overall average when there is one. A zero is never offered — nothing changes about a
 * screen by telling you it has nothing.
 */
/** How far ahead the home bar is willing to call something "in arrivo". */
private const val DashboardLookaheadDays = 7L

/**
 * What the docked bar says about this screen once the title has left it.
 *
 * Everything on Home that is waiting for the student, said in the order it would be dealt with, and
 * nothing else. It used to read out the dashboard's own stat tiles, which are already on the page a
 * few hundred pixels below — repeating them in the bar spent the cycle on things the eye was
 * already looking at.
 */
internal fun buildDashboardFacets(
  snapshot: DashboardSnapshot,
  today: LocalDate,
): List<String> = buildList {
  val unseenGrades = snapshot.unseenGrades.size
  if (unseenGrades > 0) {
    add(if (unseenGrades == 1) "1 voto nuovo" else "$unseenGrades voti nuovi")
  }
  val unread = snapshot.unreadCommunications.size
  if (unread > 0) add(if (unread == 1) "1 da leggere" else "$unread da leggere")
  val until = today.plusDays(DashboardLookaheadDays).toString()
  val assessments = snapshot.upcomingItems.count {
    it.category == AgendaCategory.ASSESSMENT && it.date in today.toString()..until
  }
  if (assessments > 0) {
    add(if (assessments == 1) "1 verifica" else "$assessments verifiche")
  }
  val average = snapshot.averageLabel
  if (average.isNotBlank() && average != "--") add("Media $average")
}

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
  val unseenGradeIds = remember(snapshot.unseenGrades) { snapshot.unseenGrades.mapTo(mutableSetOf()) { it.id } }

  val firstName = snapshot.profile.name.takeIf { it.isNotBlank() }?.split(" ")?.firstOrNull()?.replaceFirstChar { it.titlecase() } ?: "Studente"
  val titleText = snapshot.headline.ifBlank { "Ciao, $firstName" }
  val facetToday = rememberCurrentDate()
  val titleFacets = remember(snapshot, facetToday) {
    buildDashboardFacets(snapshot, facetToday)
  }

  // Il voto si apre qui, non altrove: toccarlo in home portava alla scheda Voti e apriva il
  // pop-up li'. Due schermate per guardare un numero.
  var openedGrade by remember { mutableStateOf<Grade?>(null) }
  var gradeOrigin by remember { mutableStateOf<Rect?>(null) }
  val metrics = rememberFluidScreenMetrics()
  val openGrade: (Grade, Rect?) -> Unit = { grade, bounds ->
    gradeOrigin = bounds
    openedGrade = grade
  }

  FluidScreen(
    modifier = modifier,
    title = titleText,
    // Una pagina di sezioni, non una colonna di testo: su un tablet in orizzontale si allarga e le
    // sezioni si mettono in colonna. Sul telefono e in verticale non cambia niente.
    contentMaxWidth = FluidColumnsDefaults.WideContentMaxWidth,
    metrics = metrics,
    ambient = FeatureIdentity.Overview.ambient(),
    subtitle = snapshot.syncStatus.lastSyncLabel(),
    titleFacets = titleFacets,
    actions = {
      FluidSyncAction(status = snapshot.syncStatus.toFluid(), onRetry = viewModel::refresh)
      FluidBarAction(
        icon = Icons.Rounded.Refresh,
        contentDescription = "Aggiorna",
        onClick = viewModel::refresh,
      )
    },
    isRefreshing = state.isRefreshing,
    onRefresh = viewModel::refresh,
    itemSpacing = 18.dp,
  ) {
    // Whatever the sync could not deliver, said where the missing data would have been. Reserved
    // only when there is something to say, so an ordinary page keeps its first item at the top.
    if (snapshot.syncStatus.noticeMessage() != null) {
      item {
        FluidSyncNotice(status = snapshot.syncStatus.toFluid(), onRetry = viewModel::refresh)
      }
    }
    item {
      FeatureHero(
        identity = FeatureIdentity.Overview,
        eyebrow = "La tua giornata",
        value = snapshot.todayLessons.size.toString(),
        label = if (snapshot.todayLessons.size == 1) "lezione oggi" else "lezioni oggi",
        icon = Icons.Rounded.Today,
      )
    }
    // Le metriche che vivevano dentro il pannello editoriale: ora sono superfici della pagina,
    // sotto la fascia, con lo stesso peso delle altre.
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FluidMetricTile(
          label = "Media",
          value = snapshot.averageNumeric?.let { snapshot.averageLabel } ?: "--",
          detail = "generale",
          modifier = Modifier.weight(1f),
          tone = FluidTone.Primary,
          onClick = onNavigateGrades,
          glass = true,
        )
        FluidMetricTile(
          label = "Voti nuovi",
          value = snapshot.unseenGrades.size.toString(),
          detail = "da vedere",
          modifier = Modifier.weight(1f),
          onClick = onNavigateGrades,
          glass = true,
        )
        FluidMetricTile(
          label = "Bacheca",
          value = snapshot.unreadCommunications.size.toString(),
          detail = "non lette",
          modifier = Modifier.weight(1f),
          onClick = onNavigateCommunications,
          glass = true,
        )
      }
    }
    val columns = metrics.columns()
    // In colonne l'orario si apre dalla sezione delle lezioni, dove sta la cosa di cui parla; da
    // solo in una riga larga undici centimetri era un bottone smarrito.
    if (columns <= 1) {
      item {
        FluidQuickAction(label = "Apri orario", onClick = onNavigateLessons)
      }
    }

    if (columns > 1) {
      // Su uno schermo largo le quattro sezioni stanno una accanto all'altra, a muratura: la
      // giornata si legge in una schermata invece che scorrendo quattro telefoni impilati.
      fluidColumns(
        key = "dashboard:columns",
        columns = columns,
        sections = listOfNotNull(
          FluidColumnSection(key = "dashboard:lessons") {
            DashboardSection(
              title = "Lezioni di oggi",
              action = { FluidQuickAction(label = "Apri orario", onClick = onNavigateLessons) },
            ) {
              if (snapshot.todayLessons.isEmpty()) {
                FluidEmptyState(
                  title = "Nessuna lezione oggi",
                  detail = "L'orario della settimana resta a un tocco.",
                )
              } else {
                FluidGlassGroup(snapshot.todayLessons) { lesson -> TodayLessonRow(lesson) }
              }
            }
          },
          FluidColumnSection(key = "dashboard:recent-grades") {
            DashboardSection("Voti recenti") {
              if (recentGrades.isEmpty()) {
                NoRecentGrades()
              } else {
                RecentGradesPager(
                  grades = recentGrades,
                  unseenGradeIds = unseenGradeIds,
                  onOpenGrade = openGrade,
                  onNavigateGrades = onNavigateGrades,
                )
              }
            }
          },
          FluidColumnSection(key = "dashboard:upcoming") {
            DashboardSection("In arrivo") {
              if (upcomingItems.isEmpty()) {
                NoUpcomingItems()
              } else {
                FluidGlassGroup(upcomingItems) { item -> UpcomingRow(item, onNavigateAgenda) }
              }
            }
          },
          FluidColumnSection(key = "dashboard:board") {
            DashboardSection("Bacheca") {
              if (unreadCommunications.isEmpty()) {
                NoUrgentCommunications()
              } else {
                FluidGlassGroup(unreadCommunications) { communication ->
                  UnreadCommunicationRow(communication, onNavigateCommunications)
                }
              }
            }
          },
        ),
      )
    } else {
      if (snapshot.todayLessons.isNotEmpty()) {
        item { FluidSectionHeader("Lezioni di oggi") }
        fluidGlassGroups(snapshot.todayLessons) { lesson -> TodayLessonRow(lesson) }
      }
      item { FluidSectionHeader("Voti recenti") }
      if (recentGrades.isEmpty()) {
        item { NoRecentGrades() }
      } else {
        item(key = "dashboard:grades") {
          RecentGradesPager(
            grades = recentGrades,
            unseenGradeIds = unseenGradeIds,
            onOpenGrade = openGrade,
            onNavigateGrades = onNavigateGrades,
          )
        }
      }
      item { FluidSectionHeader("In arrivo") }
      if (upcomingItems.isEmpty()) {
        item { NoUpcomingItems() }
      } else {
        fluidGlassGroups(upcomingItems) { item -> UpcomingRow(item, onNavigateAgenda) }
      }
      item { FluidSectionHeader("Bacheca") }
      if (unreadCommunications.isEmpty()) {
        item { NoUrgentCommunications() }
      } else {
        fluidGlassGroups(unreadCommunications) { communication ->
          UnreadCommunicationRow(communication, onNavigateCommunications)
        }
      }
    }
  }

  // Dichiarato sempre, visibile a comando: un portale dentro un `?.let` si smonterebbe alla
  // chiusura e porterebbe via l'animazione di uscita.
  FluidGlassModalPortal(
    item = openedGrade,
    onDismissRequest = { openedGrade = null },
    origin = { gradeOrigin },
    paneTitle = "Dettaglio voto",
    paneTint = openedGrade?.let { gradePaneTint(it) },
  ) { grade ->
    GradeDetailContent(grade = grade)
  }
}

/** Una sezione della home in colonna: il titolo e quello che introduce, con l'aria della pagina. */
@Composable
private fun DashboardSection(
  title: String,
  action: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // Tutte le testate alla stessa altezza, con o senza un'azione accanto: in colonne si leggono
    // su una riga sola, e una che scende di mezzo tasto sembra un errore di impaginazione.
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = DashboardSectionHeaderHeight),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidSectionHeader(title, modifier = Modifier.weight(1f))
      action?.invoke()
    }
    content()
  }
}

@Composable
private fun TodayLessonRow(lesson: Lesson) {
  val presentation = remember(lesson) { lesson.toDashboardPresentation() }
  FluidListRow(
    title = lesson.subject,
    subtitle = presentation.subtitle,
    eyebrow = presentation.timeRangeLabel,
    meta = listOfNotNull(
      lesson.teacher?.takeIf(String::isNotBlank),
    ).joinToString(" / "),
    tone = presentation.tone,
    leading = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
    badge = {
      FluidStatusBadge(
        label = presentation.badgeLabel,
        tone = presentation.badgeTone,
      )
    },
  )
}

/**
 * Una rail di card vivide, non righe grigie: in home il voto e' un elemento che sta da solo, e il
 * colore della fascia e' l'informazione che porta.
 *
 * Un voto alla volta, non una rail che scorre libera: era l'unico scorrimento laterale di tutta
 * l'app, e uno scorrimento che si ferma dove capita non somiglia a niente'altro qui. Il pager si
 * aggancia e mostra una carta intera; i puntini dicono gia' che ce n'e' altra. Un bordo colorato che
 * spunta a destra somiglia a una card tagliata male piu' che a un invito a scorrere.
 */
@Composable
private fun RecentGradesPager(
  grades: List<Grade>,
  unseenGradeIds: Set<String>,
  onOpenGrade: (Grade, Rect?) -> Unit,
  onNavigateGrades: () -> Unit,
) {
  val pagerState = rememberPagerState(pageCount = { grades.size })
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    HorizontalPager(state = pagerState, pageSpacing = 10.dp) { page ->
      val grade = grades[page]
      var cardBounds by remember { mutableStateOf<Rect?>(null) }
      GradeCard(
        valueLabel = grade.valueLabel,
        numericValue = grade.numericValue,
        title = grade.subject,
        // Il rettangolo di cio' che si e' toccato: senza, la finestra nasce dal centro e la card
        // resta li' sotto lo scrim, come se ce ne fossero due. Con l'origine la card *diventa* il
        // pop-up e ci ritorna.
        modifier = Modifier.fluidExpandOrigin { cardBounds = it },
        subtitle = listOf(grade.type.ifBlank { "Valutazione" }, gradeDateLabel(grade.date)).joinToString(" · "),
        unseen = unseenGradeIds.contains(grade.id),
        onClick = { onOpenGrade(grade, cardBounds) },
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PagerDots(count = grades.size, current = pagerState.currentPage)
      FluidQuickAction(label = "Tutti i voti", onClick = onNavigateGrades)
    }
  }
}

private val DashboardSectionHeaderHeight = 48.dp

/**
 * Il genere dell'impegno al posto di «AGENDA»: che venga dall'agenda lo dice gia' la sezione, che
 * sia una verifica e non un compito no — ed e' la prima cosa che si vuole sapere.
 */
private fun AgendaCategory.upcomingBadge(): Pair<String, FluidTone> = when (this) {
  AgendaCategory.ASSESSMENT -> "VERIFICA" to FluidTone.Danger
  AgendaCategory.HOMEWORK -> "COMPITO" to FluidTone.Warning
  AgendaCategory.LESSON -> "LEZIONE" to FluidTone.Neutral
  AgendaCategory.EVENT, AgendaCategory.CUSTOM -> "EVENTO" to FluidTone.Success
}

@Composable
private fun UpcomingRow(item: AgendaItem, onClick: () -> Unit) {
  val (badgeLabel, badgeTone) = item.category.upcomingBadge()
  FluidListRow(
    title = item.title,
    subtitle = item.subtitle,
    eyebrow = listOfNotNull(nearDayLabel(item.date), item.time?.takeIf(String::isNotBlank)).joinToString(" · "),
    meta = item.detail,
    tone = badgeTone,
    leading = { Icon(Icons.Rounded.Event, contentDescription = null) },
    onClick = onClick,
    badge = { FluidStatusBadge(badgeLabel, tone = badgeTone) },
    animatePress = true,
  )
}

@Composable
private fun UnreadCommunicationRow(communication: Communication, onClick: () -> Unit) {
  FluidListRow(
    title = communication.title,
    subtitle = communication.sender,
    eyebrow = nearDayLabel(communication.date),
    meta = communication.contentPreview,
    tone = FluidTone.Warning,
    leading = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
    onClick = onClick,
    badge = { FluidStatusBadge("NUOVA", tone = FluidTone.Warning) },
    animatePress = true,
  )
}

@Composable
private fun NoRecentGrades() {
  FluidEmptyState(
    title = "Nessun voto disponibile",
    detail = "I voti recenti appariranno qui dopo la prossima sincronizzazione.",
  )
}

@Composable
private fun NoUpcomingItems() {
  FluidEmptyState(
    title = "Nessun elemento imminente",
    detail = "I prossimi compiti, verifiche o eventi appariranno qui.",
  )
}

@Composable
private fun NoUrgentCommunications() {
  FluidEmptyState(
    title = "Nessuna comunicazione urgente",
    detail = "I nuovi avvisi della scuola appariranno qui.",
  )
}

/**
 * Dove sei nella pila dei voti recenti.
 *
 * Sostituisce l'unica cosa che la rail diceva bene — che ce n'era altra a destra — ora che si vede
 * una carta alla volta.
 */
@Composable
private fun PagerDots(count: Int, current: Int) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(count) { index ->
      val active = index == current
      Box(
        modifier = Modifier
          .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
          .background(
            color = if (active) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            shape = ContinuousCornerShape(FluidRadius.Small),
          ),
      )
    }
  }
}
