package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.first

class SchoolOverviewWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition

  override val sizeMode: SizeMode = SizeMode.Responsive(
    setOf(
      DpSize(180.dp, 110.dp),
      DpSize(180.dp, 180.dp),
      DpSize(260.dp, 110.dp),
      DpSize(260.dp, 180.dp),
      DpSize(300.dp, 240.dp),
      DpSize(300.dp, 300.dp),
    ),
  )

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
      .toSchoolWidgetPreferences()
    val entryPoint = EntryPointAccessors.fromApplication(
      context.applicationContext,
      SchoolWidgetEntryPoint::class.java,
    )
    // The widget is painted before anyone can react to something going wrong, so a failure to read
    // the settings falls back to the defaults rather than leaving the home screen with a blank cell.
    val settings = runCatching { entryPoint.settingsRepository().observeSettings().first() }
      .getOrDefault(AppSettings())
    val hasSession = entryPoint.authRepository().session.value != null
    val snapshot = if (hasSession) {
      runCatching { entryPoint.dashboardRepository().observeDashboard().first() }
        .getOrElse { error ->
          DashboardSnapshot(
            syncStatus = SyncStatus(
              state = SyncState.ERROR,
              message = error.message,
            ),
          )
        }
    } else {
      DashboardSnapshot()
    }
    val model = SchoolWidgetMapper.map(
      snapshot = snapshot,
      preferences = preferences,
      hasSession = hasSession,
    )
    val palette = widgetPalette(context, settings)

    provideContent {
      SchoolOverviewWidgetContent(
        context = context,
        model = model,
        palette = palette,
      )
    }
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SchoolWidgetEntryPoint {
  fun dashboardRepository(): DashboardRepository
  fun authRepository(): AuthRepository
  fun settingsRepository(): SettingsRepository
}

/**
 * The overview, in the shape the app gives a grouped list: a quiet background, one rounded card
 * holding the rows, hairlines inset to where the text starts, and the category carried on the icon
 * tile rather than on the row behind it.
 */
@Composable
private fun SchoolOverviewWidgetContent(
  context: Context,
  model: SchoolWidgetModel,
  palette: WidgetPalette,
) {
  val counters = model.counters(palette)
  val layout = resolveWidgetLayout(size = LocalSize.current, hasCounters = counters.isNotEmpty())

  Box(
    modifier = GlanceModifier
      .fillMaxSize()
      .background(palette.background)
      .cornerRadius(WidgetShape.Container)
      .clickable(launchAction(context, WidgetDeepLinks.home()))
      .padding(layout.padding),
  ) {
    Column(
      modifier = GlanceModifier.fillMaxSize(),
      verticalAlignment = Alignment.Top,
    ) {
      Header(model = model, palette = palette, layout = layout)
      Spacer(GlanceModifier.height(WidgetMetrics.Gap))
      when (model.status) {
        WidgetStatus.LOGGED_OUT -> MessageCard(
          icon = R.drawable.ic_widget_alert,
          tone = palette.grades,
          message = model.emptyMessage,
          detail = model.lastRefreshError.takeIf { it.isNotBlank() && !layout.compact },
          palette = palette,
          layout = layout,
        )
        WidgetStatus.EMPTY -> MessageCard(
          icon = R.drawable.ic_widget_event,
          tone = palette.event,
          message = model.emptyMessage,
          detail = model.lastRefreshError.takeIf { it.isNotBlank() && !layout.compact },
          palette = palette,
          layout = layout,
        )
        WidgetStatus.CONTENT -> {
          val rows = model.upcoming.take(layout.rowLimit)
          if (rows.isEmpty()) {
            MessageCard(
              icon = R.drawable.ic_widget_event,
              tone = palette.event,
              message = "Nessun impegno in arrivo",
              detail = null,
              palette = palette,
              layout = layout,
            )
          } else {
            UpcomingGroup(
              context = context,
              items = rows,
              palette = palette,
              layout = layout,
            )
          }
          if (layout.showCounters) {
            Spacer(GlanceModifier.height(WidgetMetrics.Gap))
            CounterRow(context = context, counters = counters, layout = layout)
          }
        }
      }
    }
  }
}

@Composable
private fun Header(
  model: SchoolWidgetModel,
  palette: WidgetPalette,
  layout: WidgetLayout,
) {
  Row(
    modifier = GlanceModifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = model.header,
        style = widgetTextStyle(
          color = palette.onSurface,
          size = if (layout.compact) 15.sp else 17.sp,
          weight = FontWeight.Bold,
        ),
        maxLines = 1,
      )
      if (layout.showSyncLine) {
        Text(
          text = model.syncLabel,
          style = widgetTextStyle(
            color = if (model.syncWarning) palette.attention else palette.onSurfaceVariant,
            size = 12.sp,
          ),
          maxLines = 1,
        )
      }
    }
    Spacer(GlanceModifier.width(10.dp))
    RefreshButton(palette = palette, layout = layout)
  }
}

/**
 * The refresh affordance is a tinted circle rather than the labelled chip it used to be.
 *
 * A widget has one primary action — open the app — and the chip competed with it for both space and
 * attention; a bar-button-sized glyph reads as secondary, which is what it is.
 */
@Composable
private fun RefreshButton(
  palette: WidgetPalette,
  layout: WidgetLayout,
) {
  val diameter = if (layout.compact) 30.dp else 36.dp
  Box(
    modifier = GlanceModifier
      .size(diameter)
      .background(palette.accentContainer)
      // Half the diameter, not the diameter: an outline radius larger than the shorter side is
      // undefined territory for the host's outline provider rather than "more round".
      .cornerRadius(diameter / 2)
      .semantics { contentDescription = "Aggiorna" }
      .clickable(actionRunCallback<RefreshWidgetAction>()),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      provider = ImageProvider(R.drawable.ic_widget_refresh),
      contentDescription = null,
      modifier = GlanceModifier.size(if (layout.compact) 15.dp else 18.dp),
      colorFilter = ColorFilter.tint(palette.onAccentContainer),
    )
  }
}

@Composable
private fun UpcomingGroup(
  context: Context,
  items: List<WidgetUpcomingItem>,
  palette: WidgetPalette,
  layout: WidgetLayout,
) {
  Column(
    modifier = GlanceModifier
      .fillMaxWidth()
      .background(palette.card)
      .cornerRadius(WidgetShape.Group),
  ) {
    items.forEachIndexed { index, item ->
      if (index > 0) {
        Hairline(palette = palette, layout = layout)
      }
      UpcomingRow(context = context, item = item, palette = palette, layout = layout)
    }
  }
}

@Composable
private fun UpcomingRow(
  context: Context,
  item: WidgetUpcomingItem,
  palette: WidgetPalette,
  layout: WidgetLayout,
) {
  val tone = palette.toneFor(item.type)
  Row(
    modifier = GlanceModifier
      .fillMaxWidth()
      .clickable(launchAction(context, item.deepLink))
      .padding(
        horizontal = if (layout.compact) 10.dp else 12.dp,
        vertical = if (layout.compact) 7.dp else 9.dp,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconTile(icon = item.type.iconRes(), tone = tone, compact = layout.compact)
    Spacer(GlanceModifier.width(if (layout.compact) 8.dp else 10.dp))
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = item.title,
        style = widgetTextStyle(
          color = palette.onSurface,
          size = if (layout.compact) 13.sp else 15.sp,
          weight = FontWeight.Medium,
        ),
        maxLines = 1,
      )
      if (!layout.compact && item.subtitle.isNotBlank()) {
        Text(
          text = item.subtitle,
          style = widgetTextStyle(color = palette.onSurfaceVariant, size = 12.sp),
          maxLines = 1,
        )
      }
    }
    Spacer(GlanceModifier.width(8.dp))
    Text(
      text = item.dateLabel,
      style = widgetTextStyle(
        color = tone.content,
        size = if (layout.compact) 11.sp else 12.sp,
        weight = FontWeight.Bold,
      ),
      maxLines = 1,
    )
  }
}

/**
 * The separator between two rows, inset to where the row's text starts.
 *
 * Glance modifiers are a set of properties rather than an ordered chain, so a padding and a
 * background declared on the same element would still paint edge to edge: the inset has to come
 * from a wrapper.
 */
@Composable
private fun Hairline(
  palette: WidgetPalette,
  layout: WidgetLayout,
) {
  val inset = if (layout.compact) 42.dp else 52.dp
  Box(modifier = GlanceModifier.fillMaxWidth().padding(start = inset)) {
    Box(
      modifier = GlanceModifier
        .fillMaxWidth()
        .height(1.dp)
        .background(palette.hairline),
    ) {}
  }
}

@Composable
private fun IconTile(
  icon: Int,
  tone: WidgetTone,
  compact: Boolean,
) {
  val tile = if (compact) 24.dp else 30.dp
  Box(
    modifier = GlanceModifier
      .size(tile)
      .background(tone.container)
      .cornerRadius(WidgetShape.Tile),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      provider = ImageProvider(icon),
      contentDescription = null,
      modifier = GlanceModifier.size(if (compact) 14.dp else 17.dp),
      colorFilter = ColorFilter.tint(tone.content),
    )
  }
}

@Composable
private fun MessageCard(
  icon: Int,
  tone: WidgetTone,
  message: String,
  detail: String?,
  palette: WidgetPalette,
  layout: WidgetLayout,
) {
  Row(
    modifier = GlanceModifier
      .fillMaxWidth()
      .background(palette.card)
      .cornerRadius(WidgetShape.Group)
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconTile(icon = icon, tone = tone, compact = layout.compact)
    Spacer(GlanceModifier.width(10.dp))
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = message,
        style = widgetTextStyle(
          color = palette.onSurface,
          size = 13.sp,
          weight = FontWeight.Medium,
        ),
        maxLines = 2,
      )
      if (detail != null) {
        Text(
          text = detail,
          style = widgetTextStyle(color = palette.attention, size = 11.sp),
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CounterRow(
  context: Context,
  counters: List<WidgetCounter>,
  layout: WidgetLayout,
) {
  Row(modifier = GlanceModifier.fillMaxWidth()) {
    counters.forEachIndexed { index, counter ->
      if (index > 0) {
        Spacer(GlanceModifier.width(8.dp))
      }
      CounterPill(
        counter = counter,
        compact = layout.compact,
        modifier = GlanceModifier
          .defaultWeight()
          .clickable(launchAction(context, counter.deepLink)),
      )
    }
  }
}

@Composable
private fun CounterPill(
  counter: WidgetCounter,
  compact: Boolean,
  modifier: GlanceModifier,
) {
  Row(
    modifier = modifier
      .background(counter.tone.container)
      .cornerRadius(WidgetShape.Pill)
      .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = counter.value.toString(),
      style = widgetTextStyle(
        color = counter.tone.content,
        size = if (compact) 15.sp else 17.sp,
        weight = FontWeight.Bold,
      ),
      maxLines = 1,
    )
    Spacer(GlanceModifier.width(6.dp))
    Text(
      text = counter.label,
      style = widgetTextStyle(color = counter.tone.content, size = 12.sp),
      maxLines = 1,
    )
  }
}

private data class WidgetCounter(
  val label: String,
  val value: Int,
  val tone: WidgetTone,
  val deepLink: String,
)

/**
 * A counter is drawn only when it has something to report: an empty "0 Voti" pill spends a whole
 * row of a small widget saying nothing, and the layout budget gives that space back to the list.
 */
private fun SchoolWidgetModel.counters(palette: WidgetPalette): List<WidgetCounter> = buildList {
  if (status != WidgetStatus.CONTENT) return@buildList
  if (unseenGradeCount > 0) {
    add(
      WidgetCounter(
        label = "Voti",
        value = unseenGradeCount,
        tone = palette.grades,
        deepLink = WidgetDeepLinks.grades(),
      ),
    )
  }
  if (unreadCommunicationCount > 0) {
    add(
      WidgetCounter(
        label = "Bacheca",
        value = unreadCommunicationCount,
        tone = palette.board,
        deepLink = WidgetDeepLinks.communications(),
      ),
    )
  }
}

private fun WidgetUpcomingType.iconRes(): Int = when (this) {
  WidgetUpcomingType.HOMEWORK -> R.drawable.ic_widget_homework
  WidgetUpcomingType.ASSESSMENT -> R.drawable.ic_widget_assessment
  WidgetUpcomingType.EVENT -> R.drawable.ic_widget_event
}

private fun widgetTextStyle(
  color: ColorProvider,
  size: TextUnit,
  weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(color = color, fontSize = size, fontWeight = weight)

/**
 * The radii, in the app's ladder.
 *
 * Glance can only ask the host for a rounded outline, and only from Android 12 on; below that the
 * corners stay square and the colours carry the identity on their own.
 */
private object WidgetShape {
  val Container = 24.dp
  val Group = 20.dp
  val Tile = 9.dp
  val Pill = 18.dp
}

private fun launchAction(context: Context, uri: String) = actionStartActivity(
  context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
    action = Intent.ACTION_VIEW
    data = Uri.parse(uri)
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  } ?: Intent(Intent.ACTION_VIEW, Uri.parse(uri)),
)
