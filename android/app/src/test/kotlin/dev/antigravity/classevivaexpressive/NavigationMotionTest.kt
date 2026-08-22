package dev.antigravity.classevivaexpressive

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationMotionTest {

  @Test
  fun topLevelSibling_crossFades() {
    val decision = decideRouteMotion(fromRoute = "home", toRoute = "grades")

    assertEquals(RouteMotionKind.TopLevelSwitch, decision.kind)
  }

  @Test
  fun topLevelSibling_crossFadesWhenTargetCarriesArguments() {
    val decision = decideRouteMotion(fromRoute = "home", toRoute = "communications?tab=board")

    assertEquals(RouteMotionKind.TopLevelSwitch, decision.kind)
  }

  @Test
  fun tabToDetail_pushes() {
    val decision = decideRouteMotion(fromRoute = "more", toRoute = "settings")

    assertEquals(RouteMotionKind.Push, decision.kind)
  }

  @Test
  fun detailBackToTab_pushes() {
    val decision = decideRouteMotion(fromRoute = "settings", toRoute = "more")

    assertEquals(RouteMotionKind.Push, decision.kind)
  }

  @Test
  fun detailToDetail_pushes() {
    val decision = decideRouteMotion(fromRoute = "studentScore", toRoute = "settings")

    assertEquals(RouteMotionKind.Push, decision.kind)
  }

  @Test
  fun unknownRoutes_push() {
    val decision = decideRouteMotion(fromRoute = null, toRoute = null)

    assertEquals(RouteMotionKind.Push, decision.kind)
  }

  @Test
  fun routeNormalization_stripsArgumentsAndPath() {
    assertEquals("grades", normalizeRoute("grades?gradeId={gradeId}"))
    assertEquals("student-score", normalizeRoute("student-score/import"))
  }
}
