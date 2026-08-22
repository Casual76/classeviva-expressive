package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Immutable
data class FluidTabItem(
  val route: String,
  val label: String,
  val icon: ImageVector,
)

object FluidTabBarDefaults {
  val Height = 56.dp
  val HorizontalMargin = 14.dp
  val BottomMargin = 8.dp
  val RailWidth = 84.dp

  /** Vertical space a screen must leave free so its content clears the floating bar. */
  val ContentInset = Height + BottomMargin
}

/**
 * The floating tab bar: a capsule of frosted glass that content scrolls *under* rather than being
 * cut off above.
 *
 * Selection is carried by colour and by a short pop on the icon — there is no sliding pill. A filled
 * indicator behind the active tab is a Material signature, and on glass it is actively harmful: an
 * opaque shape sitting on a translucent panel flattens the material back into a solid bar, which is
 * the one thing the effect exists to avoid.
 */
@Composable
fun FluidTabBar(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  val tint = GlassDefaults.floatingTint()

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(FluidTabBarDefaults.Height)
      .glassSurface(
        state = backdrop,
        tint = tint,
        shape = FluidCapsuleShape,
        edge = GlassEdge.None,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEach { item ->
      FluidTabItemContent(
        item = item,
        selected = item.route == selectedRoute,
        modifier = Modifier.weight(1f),
        onClick = {
          if (item.route == selectedRoute) onReselect(item) else onSelect(item)
        },
      )
    }
  }
}

@Composable
private fun FluidTabItemContent(
  item: FluidTabItem,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val contentColor by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    animationSpec = FluidMotion.color(),
    label = "fluid tab colour",
  )

  // A small, quickly-settled pop on the icon when a tab becomes active. Big enough to notice out of
  // the corner of the eye, small enough that repeated switching never looks busy.
  val pop = remember { Animatable(1f) }
  LaunchedEffect(selected) {
    if (selected) {
      pop.animateTo(1.16f, FluidMotion.instant())
      pop.animateTo(1f, FluidMotion.expressive())
    } else {
      pop.animateTo(1f, FluidMotion.snappy())
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("top_level_${item.route}")
      .semantics {
        this.role = Role.Tab
        this.selected = selected
      }
      .fluidPressable(onClick = onClick, pressedScale = 0.90f),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Icon(
        imageVector = item.icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier
          .size(23.dp)
          .graphicsLayer {
            val value = pop.value
            scaleX = value
            scaleY = value
          },
      )
      Text(
        text = item.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Wide-screen variant: the same material and indicator, stood on its side. */
@Composable
fun FluidTabRail(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
  val tint = GlassDefaults.floatingTint()
  val pillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
  val density = LocalDensity.current

  var columnHeightPx by remember { mutableFloatStateOf(0f) }
  val pillTop = remember { Animatable(0f) }
  var pillPlaced by remember { mutableStateOf(false) }
  val itemHeightPx = if (items.isEmpty()) 0f else columnHeightPx / items.size

  LaunchedEffect(selectedIndex, itemHeightPx) {
    if (itemHeightPx <= 0f || selectedIndex < 0) return@LaunchedEffect
    val target = selectedIndex * itemHeightPx
    if (!pillPlaced) {
      pillTop.snapTo(target)
      pillPlaced = true
    } else {
      pillTop.animateTo(target, FluidMotion.standard())
    }
  }

  val pillInsetPx = with(density) { 8.dp.toPx() }
  val pillRadiusPx = with(density) { 20.dp.toPx() }

  Column(
    modifier = modifier
      .width(FluidTabBarDefaults.RailWidth)
      .glassSurface(
        state = backdrop,
        tint = tint,
        shape = RoundedCornerShape(28.dp),
        edge = GlassEdge.None,
      )
      .onSizeChanged { columnHeightPx = it.height.toFloat() }
      .drawBehind {
        if (!pillPlaced || selectedIndex < 0 || itemHeightPx <= 0f) return@drawBehind
        drawRoundRect(
          color = pillColor,
          topLeft = Offset(pillInsetPx, pillTop.value + pillInsetPx),
          size = Size(size.width - pillInsetPx * 2, itemHeightPx - pillInsetPx * 2),
          cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx),
        )
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items.forEach { item ->
      Box(modifier = Modifier.fillMaxWidth().height(FluidTabBarDefaults.Height)) {
        FluidTabItemContent(
          item = item,
          selected = item.route == selectedRoute,
          modifier = Modifier.fillMaxSize(),
          onClick = {
            if (item.route == selectedRoute) onReselect(item) else onSelect(item)
          },
        )
      }
    }
  }
}

/** Padding a floating tab bar needs around itself. */
@Composable
fun fluidTabBarPadding(): androidx.compose.foundation.layout.PaddingValues =
  androidx.compose.foundation.layout.PaddingValues(
    horizontal = FluidTabBarDefaults.HorizontalMargin,
    vertical = FluidTabBarDefaults.BottomMargin,
  )
