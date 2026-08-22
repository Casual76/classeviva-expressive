package dev.antigravity.classevivaexpressive

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationMotionTest {

  @Test
  fun topLevelSibling_movesForwardInTabOrder() {
    val decision = decideRouteMotion(fromRoute = "home", toRoute = "grades")

    assertEquals(RouteMotionKind.TopLevelSwitch, decision.kind)
    assertEquals(RouteMotionDirection.Forward, decision.direction)
  }

  @Test
  fun topLevelSibling_normalizesArgumentsAndKeepsDirection() {
    val decision = decideRouteMotion(fromRoute = "home", toRoute = "communications?tab=board")

    assertEquals(RouteMotionKind.TopLevelSwitch, decision.kind)
    assertEquals(RouteMotionDirection.Forward, decision.direction)
  }

  @Test
  fun topLevelSibling_movesBackwardInTabOrder() {
    val decision = decideRouteMotion(fromRoute = "more", toRoute = "agenda")

    assertEquals(RouteMotionKind.TopLevelSwitch, decision.kind)
    assertEquals(RouteMotionDirection.Backward, decision.direction)
  }

  @Test
  fun tabToDetail_pushes() {
    val decision = decideRouteMotion(fromRoute = "more", toRoute = "settings")

    assertEquals(RouteMotionKind.Push, decision.kind)
  }

  @Test
  fun bugReport_isHierarchicalInBothDirections() {
    val openDecision = decideRouteMotion(fromRoute = "more", toRoute = BugReportRoute)
    val backDecision = decideRouteMotion(fromRoute = BugReportRoute, toRoute = "more")

    assertEquals(RouteMotionKind.Push, openDecision.kind)
    assertEquals(RouteMotionKind.Push, backDecision.kind)
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

  @Test
  fun gradeRequest_isOneShotPerBackStackEntry() {
    assertEquals("g1", pendingGradeRequest(requestedGradeId = "g1", consumedGradeId = null))
    assertEquals(null, pendingGradeRequest(requestedGradeId = "g1", consumedGradeId = "g1"))
    assertEquals("g2", pendingGradeRequest(requestedGradeId = "g2", consumedGradeId = "g1"))
  }
}
