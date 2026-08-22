package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The backdrop every glass surface in the app samples.
 *
 * Hoisted to the app root and applied by each screen to its own scrolling body, so that the screen
 * chrome and the app-level tab bar sample exactly the content — and never each other, which is what
 * a naive "blur the whole window" setup ends up doing.
 */
val LocalGlassBackdrop: ProvidableCompositionLocal<GlassBackdropState?> =
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

  /**
   * How far below the control row the bar's blur ramps out.
   *
   * Purely visual: the bar is drawn as an overlay, so this height never pushes content around. It
   * exists so the material has room to reach zero instead of stopping at a line.
   */
  val FadeExtent: Dp = 26.dp

  /** How far the list has to scroll before the top material is at full strength. */
  val ShieldDistance: Dp = 14.dp

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
  val ownBackdrop = rememberGlassBackdrop()
  val backdrop = LocalGlassBackdrop.current ?: ownBackdrop
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val topBarHeight = FluidScreenDefaults.topBarHeight()
  val bottomPadding = FluidScreenDefaults.bottomContentPadding(extraBottomPadding)

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
  val shieldDistancePx = with(density) { FluidScreenDefaults.ShieldDistance.toPx() }
  val glassIntensity = remember(listState, shieldDistancePx) {
    derivedStateOf {
      val scrolled = if (listState.firstVisibleItemIndex > 0) {
        1f
      } else {
        (listState.firstVisibleItemScrollOffset / shieldDistancePx).coerceIn(0f, 1f)
      }
      maxOf(collapseProgress.value, scrolled)
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
      ?: return@derivedStateOf 1f
    // Expressed relative to the viewport's own top edge so the result does not depend on whether
    // item offsets are reported before or after the list's content padding.
    val titleBottom = (titleItem.offset + titleItem.size - info.viewportStartOffset).toFloat()
    ((topBarHeightPx + collapseDistancePx - titleBottom) / collapseDistancePx).coerceIn(0f, 1f)
  }
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

  Column(
    modifier = Modifier
      .fillMaxWidth()
      // Taller than the controls it holds. The extra strip below the row carries the blur's ramp to
      // zero, so the material has somewhere to end — and because the bar is an overlay rather than
      // part of the layout, the extra height costs the content nothing.
      .height(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScreenDefaults.FadeExtent)
      .glassSurface(
        state = backdrop,
        tint = tint,
        edge = GlassEdge.None,
        falloff = GlassFalloff.FadeDown,
        intensity = { glassIntensity.value },
      ),
  ) {
    Spacer(Modifier.height(statusBar))
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(FluidScreenDefaults.ControlRowHeight)
        // Tap-the-bar-to-scroll-up covers the control row only. The fade strip below it is purely
        // visual and sits over live content, so it must not swallow taps meant for the list.
        .selectable(
          selected = false,
          interactionSource = interactionSource,
          indication = null,
          onClick = onTapTitle,
        ),
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

/** Provides the app-wide backdrop and tab-bar allowance to every screen below. */
@Composable
fun ProvideFluidChrome(
  backdrop: GlassBackdropState,
  bottomInset: Dp,
  scrollToTop: FluidScrollToTopBus,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalGlassBackdrop provides backdrop,
    LocalFluidBottomInset provides bottomInset,
    LocalFluidScrollToTop provides scrollToTop,
    content = content,
  )
}
