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
   * recedes as if it were a parent. The incoming opaque page makes a short ordered settle in the
   * real tab direction, keeping the relationship legible without ever cross-fading readable text.
   */
  TopLevelSwitch,

  /**
   * A detail arriving on top of what came before: it slides in from the trailing edge while the
   * screen underneath eases away spatially. Reversing it on the way back is what makes the back
   * gesture feel like it is undoing the push rather than playing a second, unrelated animation.
   */
  Push,
}

internal enum class RouteMotionDirection(val sign: Int) {
  Backward(-1),
  Forward(1),
}

internal data class RouteMotionDecision(
  val kind: RouteMotionKind,
  val direction: RouteMotionDirection = RouteMotionDirection.Forward,
)

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
    val fromIndex = topLevelRouteOrder.indexOf(from)
    val toIndex = topLevelRouteOrder.indexOf(to)
    RouteMotionDecision(
      kind = RouteMotionKind.TopLevelSwitch,
      direction = if (toIndex < fromIndex) {
        RouteMotionDirection.Backward
      } else {
        RouteMotionDirection.Forward
      },
    )
  } else {
    RouteMotionDecision(RouteMotionKind.Push)
  }
}
