package dev.antigravity.classevivaexpressive.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.first

class SchoolOverviewWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition

  override val sizeMode: SizeMode = SizeMode.Responsive(
    setOf(
      DpSize(180.dp, 110.dp),
      DpSize(180.dp, 180.dp),
      DpSize(300.dp, 110.dp),
      DpSize(300.dp, 180.dp),
      DpSize(300.dp, 240.dp),
    ),
  )

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
      .toSchoolWidgetPreferences()
    val entryPoint = EntryPointAccessors.fromApplication(
      context.applicationContext,
      SchoolWidgetEntryPoint::class.java,
    )
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

    provideContent {
      SchoolOverviewWidgetContent(
        context = context,
        model = model,
      )
    }
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SchoolWidgetEntryPoint {
  fun dashboardRepository(): DashboardRepository
  fun authRepository(): AuthRepository
}

@Composable
private fun SchoolOverviewWidgetContent(
  context: Context,
  model: SchoolWidgetModel,
) {
  val size = LocalSize.current
  val compact = size.width < 240.dp || size.height < 150.dp
  val expanded = size.height >= 220.dp && size.width >= 280.dp
  val medium = !compact
  val itemLimit = when {
    expanded -> 4
    medium -> 2
    else -> 1
  }

  Box(
    modifier = GlanceModifier
      .fillMaxSize()
      .background(SurfaceColor)
      .cornerRadius(22.dp)
      .clickable(launchAction(context, WidgetDeepLinks.home()))
      .padding(if (compact) 12.dp else 14.dp),
  ) {
    Column(
      modifier = GlanceModifier.fillMaxSize(),
      verticalAlignment = Alignment.Top,
    ) {
      HeaderRow(context = context, model = model, compact = compact)
      Spacer(GlanceModifier.height(if (compact) 8.dp else 10.dp))
      when (model.status) {
        WidgetStatus.LOGGED_OUT,
        WidgetStatus.EMPTY,
        -> EmptyState(model)
        WidgetStatus.CONTENT -> ContentState(
          context = context,
          model = model,
          compact = compact,
          expanded = expanded,
          itemLimit = itemLimit,
        )
      }
    }
  }
}

@Composable
private fun HeaderRow(
  context: Context,
  model: SchoolWidgetModel,
  compact: Boolean,
) {
  Row(
    modifier = GlanceModifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = model.header,
        style = TextStyle(
          color = ColorProvider(OnSurfaceColor),
          fontSize = if (compact) 15.sp else 17.sp,
          fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
      )
      Text(
        text = model.syncLabel,
        style = TextStyle(
          color = ColorProvider(if (model.syncWarning) WarningColor else MutedColor),
          fontSize = 11.sp,
        ),
        maxLines = 1,
      )
    }
    Spacer(GlanceModifier.width(8.dp))
    Chip(
      label = if (compact) "Sync" else "Aggiorna",
      compact = compact,
      modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
    )
  }
}

@Composable
private fun EmptyState(model: SchoolWidgetModel) {
  Column(
    modifier = GlanceModifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = model.emptyMessage,
      style = TextStyle(
        color = ColorProvider(OnSurfaceColor),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
      ),
      maxLines = 2,
    )
    if (model.lastRefreshError.isNotBlank()) {
      Spacer(GlanceModifier.height(6.dp))
      Text(
        text = "Ultimo refresh non riuscito",
        style = TextStyle(color = ColorProvider(WarningColor), fontSize = 11.sp),
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ContentState(
  context: Context,
  model: SchoolWidgetModel,
  compact: Boolean,
  expanded: Boolean,
  itemLimit: Int,
) {
  if (compact) {
    CompactCounters(context = context, model = model)
    return
  }

  Column(modifier = GlanceModifier.fillMaxWidth()) {
    model.upcoming.take(itemLimit).forEach { item ->
      UpcomingRow(context = context, item = item)
      Spacer(GlanceModifier.height(6.dp))
    }
    if (model.upcoming.isEmpty()) {
      Text(
        text = "Nessun impegno in arrivo",
        style = TextStyle(color = ColorProvider(MutedColor), fontSize = 13.sp),
        maxLines = 1,
      )
      Spacer(GlanceModifier.height(8.dp))
    }
    CounterRow(context = context, model = model, compact = false)
    if (expanded) {
      SecondaryLists(context = context, model = model)
    }
  }
}

@Composable
private fun CompactCounters(
  context: Context,
  model: SchoolWidgetModel,
) {
  val next = model.upcoming.firstOrNull()
  if (next != null) {
    UpcomingRow(context = context, item = next)
    Spacer(GlanceModifier.height(8.dp))
  }
  CounterRow(context = context, model = model, compact = true)
}

@Composable
private fun CounterRow(
  context: Context,
  model: SchoolWidgetModel,
  compact: Boolean,
) {
  Row(modifier = GlanceModifier.fillMaxWidth()) {
    MetricChip(
      label = "Voti",
      value = model.unseenGradeCount.toString(),
      compact = compact,
      modifier = GlanceModifier
        .defaultWeight()
        .clickable(launchAction(context, WidgetDeepLinks.grades())),
    )
    Spacer(GlanceModifier.width(6.dp))
    MetricChip(
      label = "Bacheca",
      value = model.unreadCommunicationCount.toString(),
      compact = compact,
      modifier = GlanceModifier
        .defaultWeight()
        .clickable(launchAction(context, WidgetDeepLinks.communications())),
    )
  }
}

@Composable
private fun UpcomingRow(
  context: Context,
  item: WidgetUpcomingItem,
) {
  Row(
    modifier = GlanceModifier
      .fillMaxWidth()
      .background(ContainerColor)
      .cornerRadius(16.dp)
      .clickable(launchAction(context, item.deepLink))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TypeDot(item.type)
    Spacer(GlanceModifier.width(8.dp))
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = item.title,
        style = TextStyle(
          color = ColorProvider(OnSurfaceColor),
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
      )
      Text(
        text = item.subtitle,
        style = TextStyle(color = ColorProvider(MutedColor), fontSize = 11.sp),
        maxLines = 1,
      )
    }
    Text(
      text = item.dateLabel,
      style = TextStyle(
        color = ColorProvider(PrimaryColor),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
      ),
      maxLines = 1,
    )
  }
}

@Composable
private fun SecondaryLists(
  context: Context,
  model: SchoolWidgetModel,
) {
  val grade = model.unseenGrades.firstOrNull()
  val communication = model.unreadCommunications.firstOrNull()
  if (grade == null && communication == null) return

  Spacer(GlanceModifier.height(8.dp))
  Row(modifier = GlanceModifier.fillMaxWidth()) {
    if (grade != null) {
      SmallInfoBox(
        title = grade.valueLabel,
        subtitle = grade.subject,
        modifier = GlanceModifier
          .defaultWeight()
          .clickable(launchAction(context, grade.deepLink)),
      )
    }
    if (grade != null && communication != null) {
      Spacer(GlanceModifier.width(6.dp))
    }
    if (communication != null) {
      SmallInfoBox(
        title = communication.title,
        subtitle = communication.sender,
        modifier = GlanceModifier
          .defaultWeight()
          .clickable(launchAction(context, communication.deepLink)),
      )
    }
  }
}

@Composable
private fun SmallInfoBox(
  title: String,
  subtitle: String,
  modifier: GlanceModifier,
) {
  Column(
    modifier = modifier
      .background(ContainerColor)
      .cornerRadius(14.dp)
      .padding(8.dp),
  ) {
    Text(
      text = title,
      style = TextStyle(
        color = ColorProvider(OnSurfaceColor),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      ),
      maxLines = 1,
    )
    Text(
      text = subtitle,
      style = TextStyle(color = ColorProvider(MutedColor), fontSize = 10.sp),
      maxLines = 1,
    )
  }
}

@Composable
private fun TypeDot(type: WidgetUpcomingType) {
  val color = when (type) {
    WidgetUpcomingType.HOMEWORK -> PrimaryColor
    WidgetUpcomingType.ASSESSMENT -> WarningColor
    WidgetUpcomingType.EVENT -> SecondaryColor
  }
  Box(
    modifier = GlanceModifier
      .size(10.dp)
      .background(color)
      .cornerRadius(8.dp),
  ) {}
}

@Composable
private fun Chip(
  label: String,
  compact: Boolean,
  modifier: GlanceModifier,
) {
  Box(
    modifier = modifier
      .background(PrimaryContainerColor)
      .cornerRadius(18.dp)
      .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = TextStyle(
        color = ColorProvider(PrimaryColor),
        fontSize = if (compact) 13.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      ),
      maxLines = 1,
    )
  }
}

@Composable
private fun MetricChip(
  label: String,
  value: String,
  compact: Boolean,
  modifier: GlanceModifier,
) {
  Row(
    modifier = modifier
      .background(PrimaryContainerColor)
      .cornerRadius(16.dp)
      .padding(horizontal = 9.dp, vertical = if (compact) 7.dp else 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = value,
      style = TextStyle(
        color = ColorProvider(PrimaryColor),
        fontSize = if (compact) 16.sp else 18.sp,
        fontWeight = FontWeight.Bold,
      ),
      maxLines = 1,
    )
    Spacer(GlanceModifier.width(6.dp))
    Text(
      text = label,
      style = TextStyle(color = ColorProvider(OnSurfaceColor), fontSize = 11.sp),
      maxLines = 1,
    )
  }
}

private fun launchAction(context: Context, uri: String) = actionStartActivity(
  context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
    action = Intent.ACTION_VIEW
    data = Uri.parse(uri)
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  } ?: Intent(Intent.ACTION_VIEW, Uri.parse(uri)),
)

private val SurfaceColor = Color(0xFFF8F7F2)
private val ContainerColor = Color(0xFFFFFFFF)
private val PrimaryColor = Color(0xFF176B63)
private val PrimaryContainerColor = Color(0xFFDCECE8)
private val SecondaryColor = Color(0xFF625B71)
private val WarningColor = Color(0xFF9A5B00)
private val OnSurfaceColor = Color(0xFF161D1A)
private val MutedColor = Color(0xFF5F6B66)
