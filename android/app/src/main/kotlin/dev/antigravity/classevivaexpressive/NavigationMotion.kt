package dev.antigravity.classevivaexpressive

import androidx.lifecycle.SavedStateHandle
import dev.antigravity.classevivaexpressive.core.designsystem.theme.MotionOrigin

/**
 * How one destination gives way to the next.
 *
 * Two shapes of movement, and that is the whole vocabulary. The app previously offered three, one
 * of which existed only to describe an animation that could not be relied on to run; a navigation
 * system that offers a different animation for every pair of screens is a navigation system nobody
 * can read.
 */
internal enum class RouteMotionKind {
  /**
   * Between the tabs of the bottom bar. They are peers — neither is "inside" the other — so nothing
   * expands and nothing recedes; both pages step sideways together, in the direction the bar itself
   * implies, and the arriving one keeps the departing one covered the whole way.
   */
  TopLevelSwitch,

  /**
   * Anything opening *out of* something else: a row, a card, a call to action, a bar button.
   *
   * The destination grows from wherever the finger went down and the page it covers recedes very
   * slightly underneath it, losing focus as it goes. Reversed exactly on the way back, which is
   * what lets the predictive-back gesture scrub it frame for frame.
   */
  Expand,
}

/**
 * @param direction where the arriving peer sits in the tab order relative to the one it replaces:
 *   `+1` later, `-1` earlier, `0` when the pair has no order to speak of. Only meaningful for
 *   [RouteMotionKind.TopLevelSwitch].
 */
internal data class RouteMotionDecision(
  val kind: RouteMotionKind,
  val direction: Int = 0,
)

private const val MotionOriginXKey = "route-motion:origin-x"
private const val MotionOriginYKey = "route-motion:origin-y"
private const val MotionKindKey = "route-motion:kind"

/**
 * Records that this entry was opened by touching something, and where.
 *
 * Writes only Bundle-safe primitives, so a restored back stack can never hold a stale UI object.
 */
internal fun SavedStateHandle.writeExpandMotion(origin: MotionOrigin) {
  this[MotionOriginXKey] = origin.fractionX
  this[MotionOriginYKey] = origin.fractionY
  this[MotionKindKey] = RouteMotionKind.Expand.name
}

/** Records that this entry was reached by stepping sideways along the tab bar. */
internal fun SavedStateHandle.writePeerMotion() {
  remove<Float>(MotionOriginXKey)
  remove<Float>(MotionOriginYKey)
  this[MotionKindKey] = RouteMotionKind.TopLevelSwitch.name
}

/**
 * The origin an entry was opened from, or the centre when there was no gesture behind it — a deep
 * link, a notification or a back stack restored after process death.
 */
internal fun SavedStateHandle.readMotionOrigin(): MotionOrigin {
  val x = get<Float>(MotionOriginXKey) ?: return MotionOrigin.Center
  val y = get<Float>(MotionOriginYKey) ?: return MotionOrigin.Center
  if (!x.isFinite() || !y.isFinite()) return MotionOrigin.Center
  return MotionOrigin(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/** How this entry was reached, or null when nothing recorded it. */
internal fun SavedStateHandle.readMotionKind(): RouteMotionKind? {
  val name = get<String>(MotionKindKey) ?: return null
  return RouteMotionKind.entries.firstOrNull { it.name == name }
}

internal fun normalizeRoute(route: String?): String? {
  return route
    ?.substringBefore("?")
    ?.substringBefore("/")
    ?.takeIf { it.isNotBlank() }
}

/**
 * @param requestedKind how the entry that is joining or leaving the stack was reached. Two tabs are
 *   peers, but *how* you got between them is the thing that has to move: tapping the bar is a step
 *   sideways, while tapping the average on Home is that card opening into the page behind it. Only
 *   the route pair could be consulted before, so every such card arrived with a tab bar's shrug.
 */
internal fun decideRouteMotion(
  fromRoute: String?,
  toRoute: String?,
  requestedKind: RouteMotionKind?,
): RouteMotionDecision {
  val from = normalizeRoute(fromRoute)
  val to = normalizeRoute(toRoute)
  if (from !in topLevelRoutes || to !in topLevelRoutes) {
    return RouteMotionDecision(RouteMotionKind.Expand)
  }
  if (requestedKind == RouteMotionKind.Expand) {
    return RouteMotionDecision(RouteMotionKind.Expand)
  }
  return RouteMotionDecision(
    kind = RouteMotionKind.TopLevelSwitch,
    direction = peerDirection(from, to),
  )
}

/**
 * Which way a peer switch travels, read straight off the order the tabs are declared in.
 *
 * Derived rather than recorded on purpose: a direction stored on an entry would have to be correct
 * for the forward move *and* for the pop that undoes it, and there is no moment at which both can
 * be written. The tab order already knows, and it knows in both directions.
 */
internal fun peerDirection(fromRoute: String?, toRoute: String?): Int {
  val fromIndex = topLevelRouteOrder.indexOf(fromRoute)
  val toIndex = topLevelRouteOrder.indexOf(toRoute)
  if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return 0
  return if (toIndex > fromIndex) 1 else -1
}
