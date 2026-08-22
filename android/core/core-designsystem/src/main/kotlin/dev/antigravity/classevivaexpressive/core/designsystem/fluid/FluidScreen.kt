package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Coordinates chrome that lives above the navigation host without sharing render layers between
 * destinations.
 *
 * Every [FluidScreen] owns its own [GlassBackdropState]. Screens register that state while composed;
 * the most recently registered destination becomes the source sampled by the floating tab bar. A
 * predictive-back cancellation simply disposes the briefly revealed destination and restores the
 * previous registration, so no two screens ever write into the same graphics-layer instances.
 */
@Stable
class FluidChromeController internal constructor(
  private val bottomBarVelocityThresholdPx: Float,
) {
  private val backdrops = LinkedHashMap<Any, GlassBackdropState>()
  private val _activeBackdrop = mutableStateOf<GlassBackdropState?>(null)
  private val _bottomBarOffsetPx = mutableFloatStateOf(0f)
  private var bottomBarTravelPx = 0f

  val activeBackdrop: State<GlassBackdropState?> = _activeBackdrop
  val bottomBarOffsetPx: State<Float> = _bottomBarOffsetPx

  internal fun registerBackdrop(key: Any, backdrop: GlassBackdropState) {
    backdrops.remove(key)
    backdrops[key] = backdrop
    _activeBackdrop.value = backdrop
    revealBottomBar()
  }

  internal fun unregisterBackdrop(key: Any) {
    val removed = backdrops.remove(key) ?: return
    if (_activeBackdrop.value === removed) {
      _activeBackdrop.value = backdrops.entries.lastOrNull()?.value
    }
  }

  fun updateBottomBarTravel(travelPx: Float) {
    bottomBarTravelPx = travelPx.coerceAtLeast(0f)
    _bottomBarOffsetPx.floatValue = _bottomBarOffsetPx.floatValue
      .coerceIn(0f, bottomBarTravelPx)
  }

  fun onBottomBarScroll(availableY: Float) {
    _bottomBarOffsetPx.floatValue = calculateBottomBarOffset(
      currentOffsetPx = _bottomBarOffsetPx.floatValue,
      availableY = availableY,
      travelPx = bottomBarTravelPx,
    )
  }

  fun revealBottomBar() {
    _bottomBarOffsetPx.floatValue = 0f
  }

  suspend fun settleBottomBar(velocityY: Float) {
    val start = _bottomBarOffsetPx.floatValue
    val target = calculateBottomBarSettleTarget(
      currentOffsetPx = start,
      travelPx = bottomBarTravelPx,
      velocityY = velocityY,
      velocityThresholdPx = bottomBarVelocityThresholdPx,
    )
    if (start == target) return
    animate(
      initialValue = start,
      targetValue = target,
      animationSpec = FluidMotion.snappy(),
    ) { value, _ ->
      _bottomBarOffsetPx.floatValue = value.coerceIn(0f, bottomBarTravelPx)
    }
  }
}

@Composable
fun rememberFluidChromeController(): FluidChromeController {
  val density = LocalDensity.current.density
  val velocityThresholdPx = bottomBarVelocityThresholdPx(density)
  return remember(velocityThresholdPx) {
    FluidChromeController(bottomBarVelocityThresholdPx = velocityThresholdPx)
  }
}

@Composable
fun rememberFluidChromeScrollConnection(
  controller: FluidChromeController,
  enabled: Boolean,
): NestedScrollConnection = remember(controller, enabled) {
  object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      if (enabled) controller.onBottomBarScroll(available.y)
      return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      if (enabled) controller.settleBottomBar(consumed.y + available.y)
      return Velocity.Zero
    }
  }
}

internal fun calculateBottomBarOffset(
  currentOffsetPx: Float,
  availableY: Float,
  travelPx: Float,
): Float {
  if (travelPx <= 0f) return 0f
  // Nested-scroll Y is negative while content moves towards the end of the list.
  return (currentOffsetPx - availableY).coerceIn(0f, travelPx)
}

internal fun calculateBottomBarSettleTarget(
  currentOffsetPx: Float,
  travelPx: Float,
  velocityY: Float,
  velocityThresholdPx: Float,
): Float {
  if (travelPx <= 0f) return 0f
  val threshold = velocityThresholdPx.coerceAtLeast(0f)
  return when {
    velocityY < -threshold -> travelPx
    velocityY > threshold -> 0f
    currentOffsetPx >= travelPx * 0.5f -> travelPx
    else -> 0f
  }
}

internal fun bottomBarVelocityThresholdPx(density: Float): Float =
  BottomBarVelocityThresholdDpPerSecond * density.coerceAtLeast(0f)

private const val BottomBarVelocityThresholdDpPerSecond = 200f

val LocalFluidChromeController: ProvidableCompositionLocal<FluidChromeController?> =
  staticCompositionLocalOf { null }

/**
 * Lets the shell ask whichever screen is on top to return to its top.
 *
 * Re-tapping the active tab is the gesture people reach for when they want to get back to the start
 * of a long list, and its absence is felt as the list being "stuck" at the bottom.
 */
@Stable
class FluidScrollToTopBus {
  internal val signal = mutableIntStateOf(0)

  fun request() {
    signal.intValue++
  }
}

val LocalFluidScrollToTop: ProvidableCompositionLocal<FluidScrollToTopBus?> =
  staticCompositionLocalOf { null }

/**
 * Room a screen must leave at the bottom for the floating tab bar. Provided by the app shell so no
 * screen has to know whether it is currently hosted under one.
 */
val LocalFluidBottomInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

object FluidScreenDefaults {
  /** Height of the control row of the bar, matching the 44pt UIKit navigation bar. */
  val ControlRowHeight: Dp = 44.dp

  /** Travel over which the compact bar materialises once the large title reaches it. */
  val CollapseDistance: Dp = 30.dp

  /** Initial travel kept optically clear, so a tiny touch does not flash the material on. */
  val ShieldDeadZone: Dp = 8.dp

  /** Travel after the dead zone over which the top material reaches full strength. */
  val ShieldRampDistance: Dp = 64.dp

  /** Soft tail below the 44 dp control row; it removes the hard edge of a rectangular top bar. */
  val GlassFadeTail: Dp = 26.dp

  val HorizontalPadding: Dp = 20.dp
  val ItemSpacing: Dp = 14.dp
  val TitleBottomSpacing: Dp = 10.dp
  val ContentBottomSpacing: Dp = 28.dp

  /** Total height of the top chrome, including the status bar it sits under. */
  @Composable
  fun topBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + ControlRowHeight

  /** Bottom padding a scrolling body needs so its last item clears the system and tab bars. */
  @Composable
  fun bottomContentPadding(extra: Dp = 0.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
      LocalFluidBottomInset.current + extra + ContentBottomSpacing
}

private const val TitleItemKey = "fluid:large-title"

/**
 * An edge-to-edge screen in the shape Apple gives its own: content runs the full height of the
 * display, a large title sits at the top of the scroll as ordinary content, and a compact bar of
 * frosted glass materialises over it only once the title has scrolled underneath.
 *
 * Two things follow from the title being a list item rather than a collapsing app bar, and both are
 * the point of the design:
 *
 *  * There is no feedback loop between the bar's height and the list's content padding. A collapsing
 *    `LargeTopAppBar` changes the `Scaffold`'s reported padding as it collapses, so the content
 *    shifts on top of scrolling — the jitter that reads as "the animation is wrong".
 *  * Scrolling to the top always reaches the top. Nothing has to be dragged back open afterwards.
 */
@Composable
fun FluidScreen(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  titleTrailing: (@Composable () -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  listState: LazyListState = rememberLazyListState(),
  isRefreshing: Boolean = false,
  onRefresh: (() -> Unit)? = null,
  horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding,
  itemSpacing: Dp = FluidScreenDefaults.ItemSpacing,
  extraBottomPadding: Dp = 0.dp,
  content: LazyListScope.() -> Unit,
) {
  // A render layer may have only one writer. Keeping this state local is what makes overlapping
  // NavHost destinations (including predictive back) safe: each screen records into its own layers.
  val backdrop = rememberGlassBackdrop()
  val chromeController = LocalFluidChromeController.current
  val chromeRegistration = remember { Any() }
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val topBarHeight = FluidScreenDefaults.topBarHeight()
  val bottomPadding = FluidScreenDefaults.bottomContentPadding(extraBottomPadding)

  DisposableEffect(chromeController, chromeRegistration, backdrop) {
    chromeController?.registerBackdrop(chromeRegistration, backdrop)
    onDispose { chromeController?.unregisterBackdrop(chromeRegistration) }
  }

  LaunchedEffect(chromeController, listState) {
    if (chromeController == null) return@LaunchedEffect
    snapshotFlow {
      listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
      .distinctUntilChanged()
      .collect { isAtTop ->
        if (isAtTop) chromeController.revealBottomBar()
      }
  }

  LocalFluidScrollToTop.current?.let { bus ->
    LaunchedEffect(bus, listState) {
      snapshotFlow { bus.signal.intValue }
        .drop(1)
        .collect { listState.animateScrollToItem(0) }
    }
  }

  val collapseProgress = rememberCollapseProgress(
    listState = listState,
    topBarHeightPx = with(density) { topBarHeight.toPx() },
    collapseDistancePx = with(density) { FluidScreenDefaults.CollapseDistance.toPx() },
  )

  // The material appears as soon as anything passes under the status bar, not when the title has
  // finished leaving. Waiting for the title means a stretch of scrolling where text slides under the
  // clock with nothing behind it — the moment that makes an edge-to-edge layout look unfinished
  // rather than deliberate.
  val shieldDeadZonePx = with(density) { FluidScreenDefaults.ShieldDeadZone.toPx() }
  val shieldRampDistancePx = with(density) { FluidScreenDefaults.ShieldRampDistance.toPx() }
  val glassIntensity = remember(
    listState,
    collapseProgress,
    shieldDeadZonePx,
    shieldRampDistancePx,
  ) {
    derivedStateOf {
      calculateGlassIntensity(
        firstVisibleItemIndex = listState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        collapseProgress = collapseProgress.value,
        deadZonePx = shieldDeadZonePx,
        rampDistancePx = shieldRampDistancePx,
      )
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    val body: @Composable () -> Unit = {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          // The background is painted *inside* the recorded region, not behind it. A snapshot of
          // text on transparency blurs into a faint smear that the sharp original still shows
          // through; the glass has to sample an opaque image to actually hide what is under it.
          .glassBackdropSource(backdrop)
          .background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = topBarHeight,
          bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
      ) {
        item(key = TitleItemKey, contentType = TitleItemKey) {
          FluidLargeTitle(title = title, subtitle = subtitle, trailing = titleTrailing)
        }
        content()
      }
    }

    if (onRefresh != null) {
      val refreshState = rememberPullToRefreshState()
      PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        state = refreshState,
        indicator = {
          // No floating disc: iOS reveals the spinner's spokes as the finger pulls, in place, with
          // nothing behind it. The distance fraction drives how much of the ring is drawn, so the
          // control is literally being dragged into existence rather than sliding in from off-screen.
          FluidSpinner(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = topBarHeight + 10.dp)
              .graphicsLayer {
                val pulled = refreshState.distanceFraction.coerceIn(0f, 1.4f)
                alpha = if (isRefreshing) 1f else (pulled / 0.6f).coerceIn(0f, 1f)
                translationY = pulled * 26.dp.toPx()
              },
            size = 22.dp,
            progress = if (isRefreshing) null else {
              { refreshState.distanceFraction }
            },
          )
        },
        content = { body() },
      )
    } else {
      body()
    }

    FluidTopBar(
      title = title,
      backdrop = backdrop,
      collapseProgress = collapseProgress,
      onBack = onBack,
      actions = actions,
      glassIntensity = glassIntensity,
      onTapTitle = {
        scope.launch { listState.animateScrollToItem(0) }
      },
    )
  }
}

/**
 * How far the compact bar has materialised, from 0 (large title fully clear of it) to 1.
 *
 * Derived from the title item's own measured position rather than from a raw scroll offset, so it
 * stays correct whatever the title's height turns out to be — one line, two lines, or with a
 * subtitle.
 */
@Composable
private fun rememberCollapseProgress(
  listState: LazyListState,
  topBarHeightPx: Float,
  collapseDistancePx: Float,
): State<Float> = remember(listState, topBarHeightPx, collapseDistancePx) {
  derivedStateOf {
    val info = listState.layoutInfo
    val titleItem = info.visibleItemsInfo.firstOrNull { it.key == TitleItemKey }
    // Expressed relative to the viewport's own top edge so the result does not depend on whether
    // item offsets are reported before or after the list's content padding.
    val titleBottom = titleItem?.let {
      (it.offset + it.size - info.viewportStartOffset).toFloat()
    }
    calculateCollapseProgress(
      hasVisibleItems = info.visibleItemsInfo.isNotEmpty(),
      firstVisibleItemIndex = listState.firstVisibleItemIndex,
      titleBottomPx = titleBottom,
      topBarHeightPx = topBarHeightPx,
      collapseDistancePx = collapseDistancePx,
    )
  }
}

internal fun calculateCollapseProgress(
  hasVisibleItems: Boolean,
  firstVisibleItemIndex: Int,
  titleBottomPx: Float?,
  topBarHeightPx: Float,
  collapseDistancePx: Float,
): Float {
  // Before LazyColumn's first layout there is no evidence that the title has collapsed. Returning
  // one here caused the compact title and full-strength blur to flash over every entering screen.
  if (!hasVisibleItems) return 0f
  if (titleBottomPx == null) return if (firstVisibleItemIndex > 0) 1f else 0f
  val distance = collapseDistancePx.coerceAtLeast(1f)
  return ((topBarHeightPx + distance - titleBottomPx) / distance).coerceIn(0f, 1f)
}

internal fun calculateGlassIntensity(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  collapseProgress: Float,
  deadZonePx: Float,
  rampDistancePx: Float,
): Float {
  val scrollProgress = if (firstVisibleItemIndex > 0) {
    1f
  } else {
    val ramp = rampDistancePx.coerceAtLeast(1f)
    ((firstVisibleItemScrollOffset - deadZonePx) / ramp).coerceIn(0f, 1f)
  }
  return maxOf(collapseProgress.coerceIn(0f, 1f), smoothStep(scrollProgress))
}

internal fun smoothStep(value: Float): Float {
  val clamped = value.coerceIn(0f, 1f)
  return clamped * clamped * (3f - 2f * clamped)
}

@Composable
private fun FluidLargeTitle(
  title: String,
  subtitle: String?,
  trailing: (@Composable () -> Unit)?,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 4.dp, bottom = FluidScreenDefaults.TitleBottomSpacing),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        modifier = Modifier.weight(1f, fill = false),
        // 34sp Bold: the iOS navigation large title, exactly. Its negative tracking is what stops
        // a heading this size from looking like a banner.
        style = MaterialTheme.typography.displayLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      trailing?.invoke()
    }
    subtitle?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun FluidTopBar(
  title: String,
  backdrop: GlassBackdropState,
  collapseProgress: State<Float>,
  glassIntensity: State<Float>,
  onBack: (() -> Unit)?,
  actions: @Composable RowScope.() -> Unit,
  onTapTitle: () -> Unit,
) {
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val tint = GlassDefaults.barTint()
  val interactionSource = remember { MutableInteractionSource() }
  val titleShift = with(LocalDensity.current) { 11.dp.toPx() }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScreenDefaults.GlassFadeTail)
      .glassSurface(
        state = backdrop,
        tint = tint,
        edge = GlassEdge.None,
        falloff = GlassFalloff.FadeDown,
        intensity = { glassIntensity.value },
      ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(statusBar + FluidScreenDefaults.ControlRowHeight),
    ) {
      Spacer(Modifier.height(statusBar))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(FluidScreenDefaults.ControlRowHeight)
        // Tap-the-bar-to-scroll-up covers the control row only and is exposed as a named action,
        // never as the anonymous unchecked selection control that `selectable` would create.
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          role = Role.Button,
          onClick = onTapTitle,
        )
        .semantics { contentDescription = "Torna all'inizio" },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = title,
        modifier = Modifier
          .padding(horizontal = 68.dp)
          .graphicsLayer {
            // The compact title only starts arriving in the back half of the collapse, so it never
            // overlaps the large title it is replacing.
            val appear = ((collapseProgress.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
            alpha = appear
            translationY = (1f - appear) * titleShift
          },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (onBack != null) {
            FluidBackButton(onBack)
          } else {
            Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
          }
          Spacer(Modifier.weight(1f))
          Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
          )
          Spacer(Modifier.width(6.dp))
        }
      }
    }
  }
}

@Composable
private fun FluidBackButton(onBack: () -> Unit) {
  IconButton(
    onClick = onBack,
    modifier = Modifier.padding(start = 6.dp),
    colors = IconButtonDefaults.iconButtonColors(
      contentColor = MaterialTheme.colorScheme.primary,
    ),
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
      contentDescription = "Indietro",
      modifier = Modifier.size(20.dp),
    )
  }
}

/**
 * An action in the top bar. Tinted rather than boxed, the way a UIKit bar button item is, so the
 * bar stays visually quiet until the glass appears behind it.
 */
@Composable
fun FluidBarAction(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    colors = IconButtonDefaults.iconButtonColors(
      contentColor = MaterialTheme.colorScheme.primary,
    ),
  ) {
    Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
  }
}

/** Provides the chrome registry, tab-bar allowance and scroll-to-top bus to every screen below. */
@Composable
fun ProvideFluidChrome(
  controller: FluidChromeController,
  bottomInset: Dp,
  scrollToTop: FluidScrollToTopBus,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalFluidChromeController provides controller,
    LocalFluidBottomInset provides bottomInset,
    LocalFluidScrollToTop provides scrollToTop,
    content = content,
  )
}
