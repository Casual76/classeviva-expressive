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
import dev.antigravity.classevivaexpressive.core.designsystem.theme.SubjectMark
import dev.antigravity.classevivaexpressive.core.designsystem.theme.asReadableSubject
import dev.antigravity.classevivaexpressive.core.designsystem.theme.rememberMinuteTicker
import dev.antigravity.classevivaexpressive.core.designsystem.theme.subjectPalette
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.fluidContentColumns
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import java.time.LocalTime
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
import dev.antigravity.fluidengine.ui.fluid.FluidColumnsDefaults
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
  val span = minuteSpan()
  val timeRangeLabel = if (span != null) "${span.start.clockLabel()} - ${span.end.clockLabel()}" else time
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
    val columns = metrics.columns()
    if (columns > 1) {
      // Su uno schermo largo la home e' due pannelli: la giornata a sinistra — cosa c'e' adesso e
      // cosa viene dopo — e a destra quello che aspetta, voti, impegni e bacheca. Un elemento solo,
      // perche' i due pannelli si leggono insieme e non scorrono ciascuno per conto suo.
      item(key = "dashboard:tablet") {
        DashboardTabletPanes(
          snapshot = snapshot,
          columns = columns,
          contentWidth = metrics.contentWidth,
          recentGrades = recentGrades,
          unseenGradeIds = unseenGradeIds,
          upcomingItems = upcomingItems,
          unreadCommunications = unreadCommunications,
          onOpenGrade = openGrade,
          onNavigateGrades = onNavigateGrades,
          onNavigateAgenda = onNavigateAgenda,
          onNavigateLessons = onNavigateLessons,
          onNavigateCommunications = onNavigateCommunications,
        )
      }
    } else {
      item {
        FeatureHero(
          identity = FeatureIdentity.Overview,
          eyebrow = "La tua giornata",
          value = snapshot.todayLessons.size.toString(),
          label = if (snapshot.todayLessons.size == 1) "lezione oggi" else "lezioni oggi",
          icon = Icons.Rounded.Today,
        )
      }
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
      item {
        FluidQuickAction(label = "Apri orario", onClick = onNavigateLessons)
      }
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
  modifier: Modifier = Modifier,
  detail: String? = null,
  action: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // Tutte le testate alla stessa altezza, con o senza un'azione accanto: in colonne si leggono
    // su una riga sola, e una che scende di mezzo tasto sembra un errore di impaginazione.
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = DashboardSectionHeaderHeight),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidSectionHeader(title, modifier = Modifier.weight(1f), detail = detail)
      action?.invoke()
    }
    content()
  }
}

@Composable
private fun TodayLessonRow(lesson: Lesson, live: Boolean = false) {
  val presentation = remember(lesson) { lesson.toDashboardPresentation() }
  FluidListRow(
    title = lesson.subject.asReadableSubject(),
    subtitle = presentation.subtitle,
    eyebrow = presentation.timeRangeLabel,
    meta = listOfNotNull(
      lesson.teacher?.takeIf(String::isNotBlank),
    ).joinToString(" / "),
    // Il colore della riga e' quello della materia, sul segno: la piastrella resta neutra, e che
    // la lezione sia firmata lo dice gia' il badge.
    tone = FluidTone.Neutral,
    leading = { SubjectMark(lesson.subject) },
    badge = {
      FluidStatusBadge(
        label = presentation.badgeLabel,
        tone = presentation.badgeTone,
      )
    },
    selected = live,
  )
}

/**
 * La home di un tablet.
 *
 * A sinistra la giornata: la lezione in corso (o la prossima) piena del colore della sua materia, e
 * sotto le lezioni di oggi con la riga di adesso accesa. A destra quello che aspetta: i voti recenti
 * in fila, gli impegni in arrivo e la bacheca, con i numeri che prima erano tessere a parte detti
 * nelle loro testate. In orizzontale la destra e' larga il doppio e in arrivo e bacheca stanno
 * affiancate; in verticale i due pannelli sono uguali e la destra impila.
 *
 * Il minuto batte solo qui: sul telefono la home non ha niente che cambi da sola.
 */
@Composable
private fun DashboardTabletPanes(
  snapshot: DashboardSnapshot,
  columns: Int,
  contentWidth: Dp,
  recentGrades: List<Grade>,
  unseenGradeIds: Set<String>,
  upcomingItems: List<AgendaItem>,
  unreadCommunications: List<Communication>,
  onOpenGrade: (Grade, Rect?) -> Unit,
  onNavigateGrades: () -> Unit,
  onNavigateAgenda: () -> Unit,
  onNavigateLessons: () -> Unit,
  onNavigateCommunications: () -> Unit,
) {
  val nowState = rememberMinuteTicker()
  val now: () -> LocalTime = { nowState.value.toLocalTime() }
  val lessons = remember(snapshot.todayLessons) { snapshot.todayLessons.inDayOrder() }
  val moment by remember(lessons) { derivedStateOf { todayMoment(lessons, now()) } }
  val fontScale = LocalDensity.current.fontScale
  val spacing = FluidColumnsDefaults.Spacing
  val rightWidth = if (contentWidth.isSpecified) (contentWidth - spacing) * (columns - 1) / columns else 0.dp
  val today = rememberCurrentDate()

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(spacing),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      TodayHero(lessons = lessons, moment = moment, onOpen = onNavigateLessons)
      DashboardSection(
        title = "Lezioni di oggi",
        action = { FluidQuickAction(label = "Apri orario", onClick = onNavigateLessons) },
      ) {
        if (lessons.isEmpty()) {
          FluidEmptyState(
            title = "Nessuna lezione oggi",
            detail = "L'orario della settimana resta a un tocco.",
          )
        } else {
          val liveIndex = (moment as? TodayMoment.Live)?.index
          FluidGlassGroup(lessons) { lesson -> TodayLessonRow(lesson, live = lessons.indexOf(lesson) == liveIndex) }
        }
      }
    }

    Column(
      modifier = Modifier.weight((columns - 1).toFloat()),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      DashboardSection(
        title = "Voti recenti",
        detail = listOfNotNull(
          snapshot.averageNumeric?.let { "Media ${snapshot.averageLabel}" },
          snapshot.unseenGrades.size.takeIf { it > 0 }?.let { if (it == 1) "1 nuovo" else "$it nuovi" },
        ).joinToString(" · ").ifBlank { null },
        action = { FluidQuickAction(label = "Tutti i voti", onClick = onNavigateGrades) },
      ) {
        if (recentGrades.isEmpty()) {
          NoRecentGrades()
        } else {
          val gradeColumns = fluidContentColumns(rightWidth, DashboardGradeMinWidth * fontScale, 10.dp, 3)
            .coerceAtLeast(2)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            recentGrades.take(gradeColumns).forEach { grade ->
              var cardBounds by remember { mutableStateOf<Rect?>(null) }
              GradeCard(
                valueLabel = grade.valueLabel,
                numericValue = grade.numericValue,
                title = grade.subject,
                modifier = Modifier.weight(1f).fluidExpandOrigin { cardBounds = it },
                subtitle = listOf(grade.type.ifBlank { "Valutazione" }, gradeDateLabel(grade.date)).joinToString(" · "),
                unseen = unseenGradeIds.contains(grade.id),
                onClick = { onOpenGrade(grade, cardBounds) },
              )
            }
            // Meno voti che posti: le card restano larghe quanto le altre, non si allargano a riempire.
            repeat(gradeColumns - recentGrades.size.coerceAtMost(gradeColumns)) { Box(Modifier.weight(1f)) }
          }
        }
      }

      val until = today.plusDays(DashboardLookaheadDays).toString()
      val assessments = snapshot.upcomingItems.count {
        it.category == AgendaCategory.ASSESSMENT && it.date in today.toString()..until
      }
      val upcoming: @Composable (Modifier) -> Unit = { modifier ->
        DashboardSection(
          title = "In arrivo",
          modifier = modifier,
          detail = assessments.takeIf { it > 0 }?.let { if (it == 1) "1 verifica in 7 giorni" else "$it verifiche in 7 giorni" },
        ) {
          if (upcomingItems.isEmpty()) {
            NoUpcomingItems()
          } else {
            FluidGlassGroup(upcomingItems) { item -> UpcomingRow(item, onNavigateAgenda) }
          }
        }
      }
      val board: @Composable (Modifier) -> Unit = { modifier ->
        DashboardSection(
          title = "Bacheca",
          modifier = modifier,
          detail = snapshot.unreadCommunications.size.takeIf { it > 0 }?.let { "$it da leggere" },
        ) {
          if (unreadCommunications.isEmpty()) {
            NoUrgentCommunications()
          } else {
            FluidGlassGroup(unreadCommunications) { communication ->
              UnreadCommunicationRow(communication, onNavigateCommunications)
            }
          }
        }
      }
      if (fluidContentColumns(rightWidth, FluidColumnsDefaults.MinColumn * fontScale, spacing, 2) >= 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
          upcoming(Modifier.weight(1f))
          board(Modifier.weight(1f))
        }
      } else {
        upcoming(Modifier)
        board(Modifier)
      }
    }
  }
}

/**
 * La cosa che conta adesso.
 *
 * In lezione, la lezione, piena del colore della materia: sta da sola in cima alla pagina ed e'
 * quello che si cerca aprendo l'app in classe. Fra un'ora e l'altra, la prossima. A giornata finita
 * (o senza lezioni) torna la fascia della sezione: non c'e' niente di cui dire "adesso".
 * Non si muove: cambia colore quando cambia la lezione, e basta.
 */
@Composable
private fun TodayHero(lessons: List<Lesson>, moment: TodayMoment, onOpen: () -> Unit) {
  when (moment) {
    is TodayMoment.Live -> LessonMomentCard(
      caption = "ADESSO",
      lesson = lessons[moment.index],
      trailing = if (moment.minutesLeft <= 1) "finisce ora" else "ancora ${moment.minutesLeft} min",
      next = moment.next?.let(lessons::getOrNull),
      onOpen = onOpen,
    )
    is TodayMoment.Next -> {
      val lesson = lessons[moment.index]
      LessonMomentCard(
        caption = "PROSSIMA",
        lesson = lesson,
        trailing = if (moment.minutesUntil < 60) {
          "fra ${moment.minutesUntil} min"
        } else {
          "alle ${lesson.minuteSpan()?.start?.clockLabel() ?: lesson.time}"
        },
        next = null,
        onOpen = onOpen,
      )
    }
    TodayMoment.Over, TodayMoment.Empty -> FeatureHero(
      identity = FeatureIdentity.Overview,
      eyebrow = if (moment == TodayMoment.Over) "Giornata finita" else "La tua giornata",
      value = lessons.size.toString(),
      label = if (lessons.size == 1) "lezione oggi" else "lezioni oggi",
      icon = Icons.Rounded.Today,
    )
  }
}

@Composable
private fun LessonMomentCard(
  caption: String,
  lesson: Lesson,
  trailing: String,
  next: Lesson?,
  onOpen: () -> Unit,
) {
  FluidVividCard(colors = subjectPalette().vivid(lesson.subject), onClick = onOpen) {
    val secondary = LocalContentColor.current.copy(alpha = 0.78f)
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = caption,
        style = FluidTextStyles.uppercaseCaption,
        color = secondary,
        modifier = Modifier.weight(1f),
      )
      Text(text = trailing, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = lesson.subject.asReadableSubject(),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = listOfNotNull(
        lesson.toDashboardPresentation().timeRangeLabel,
        lesson.room?.takeIf(String::isNotBlank),
        lesson.teacher?.takeIf(String::isNotBlank),
      ).joinToString(" · "),
      style = MaterialTheme.typography.bodyMedium,
      color = secondary,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (next != null) {
      Spacer(Modifier.height(10.dp))
      Text(
        text = "Poi ${next.subject.asReadableSubject()}, alle ${next.minuteSpan()?.start?.clockLabel() ?: next.time}",
        style = MaterialTheme.typography.bodyMedium,
        color = secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** 545 -> "9:05". */
private fun Int.clockLabel(): String = "%d:%02d".format(this / 60, this % 60)

/** Sotto questa larghezza una card voto spezza il nome della materia a ogni parola. */
private val DashboardGradeMinWidth = 200.dp

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
    detail = "I nuovi voti compariranno qui appena i docenti li registrano.",
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
