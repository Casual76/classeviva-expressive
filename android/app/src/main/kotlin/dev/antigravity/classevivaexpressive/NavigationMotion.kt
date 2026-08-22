package dev.antigravity.classevivaexpressive

/**
 * How one destination gives way to the next.
 *
 * Only two shapes of movement exist in the app, which is the point: a navigation system that offers
 * a different animation for every pair of screens is a navigation system the user cannot read.
 */
internal enum class RouteMotionKind {
  /**
   * Between the tabs of the bottom bar. They are peers — neither is "inside" the other — so nothing
   * slides. A cross-fade says "same level, different subject".
   */
  TopLevelSwitch,

  /**
   * A detail arriving on top of what came before: it slides in from the trailing edge while the
   * screen underneath eases away and dims. Reversing it on the way back is what makes the back
   * gesture feel like it is undoing the push rather than playing a second, unrelated animation.
   */
  Push,
}

internal data class RouteMotionDecision(val kind: RouteMotionKind)

internal fun normalizeRoute(route: String?): String? {
  return route
    ?.substringBefore("?")
    ?.substringBefore("/")
    ?.takeIf { it.isNotBlank() }
}

internal fun decideRouteMotion(
  fromRoute: String?,
  toRoute: String?,
): RouteMotionDecision {
  val from = normalizeRoute(fromRoute)
  val to = normalizeRoute(toRoute)
  return if (from in topLevelRoutes && to in topLevelRoutes) {
    RouteMotionDecision(RouteMotionKind.TopLevelSwitch)
  } else {
    RouteMotionDecision(RouteMotionKind.Push)
  }
}
