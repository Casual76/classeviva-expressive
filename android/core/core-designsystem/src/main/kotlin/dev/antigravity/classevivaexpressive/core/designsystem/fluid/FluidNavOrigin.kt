package dev.antigravity.classevivaexpressive.core.designsystem.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Remembers *where* a navigation started.
 *
 * A screen that grows out of the row you tapped, and shrinks back into it when you leave, is the
 * difference between "a new page appeared" and "I opened this thing". The effect needs one piece of
 * information the navigation graph does not carry: the point on screen the gesture came from.
 *
 * Every [fluidPressable] and [fluidRowPressable] reports its own centre here as it is tapped, so no
 * call site has to opt in. The navigation code then claims that point for the route it is about to
 * open, and the route's enter and exit transitions anchor themselves to it. A route opened from
 * somewhere that never reported a point — a deep link, a back-press from the system — falls back to
 * the centre of the screen, which is the right answer for a navigation with no visible origin.
 */
@Stable
class FluidOriginTracker {

  /** The most recent tap, waiting to be claimed by whatever navigation it triggers. */
  private var pending: TransformOrigin? = null

  private val byRoute = mutableMapOf<String, TransformOrigin>()

  /** Called from the press handlers. Cheap, and overwritten constantly by design. */
  fun record(origin: TransformOrigin) {
    pending = origin
  }

  /**
   * Binds the pending tap to [route].
   *
   * Call this immediately before navigating. The point is stored per route rather than on a stack
   * because the same screen is reachable from several places, and it should shrink back into
   * whichever one actually opened it this time.
   */
  fun claim(route: String) {
    val key = routeKey(route) ?: return
    byRoute[key] = pending ?: TransformOrigin.Center
    pending = null
  }

  /**
   * Takes the pending tap without binding it to a route.
   *
   * For in-screen navigation — a settings pane, a detail sheet — where there is no route to key on
   * and the caller keeps the anchor itself for as long as the pane is open.
   */
  fun consumePending(): TransformOrigin? {
    val origin = pending
    pending = null
    return origin
  }

  fun originFor(route: String?): TransformOrigin =
    routeKey(route)?.let { byRoute[it] } ?: TransformOrigin.Center

  private fun routeKey(route: String?): String? =
    route?.substringBefore("?")?.substringBefore("/")?.takeIf { it.isNotBlank() }
}

val LocalFluidOriginTracker: ProvidableCompositionLocal<FluidOriginTracker?> =
  staticCompositionLocalOf { null }

@Composable
fun rememberFluidOriginTracker(): FluidOriginTracker = remember { FluidOriginTracker() }
