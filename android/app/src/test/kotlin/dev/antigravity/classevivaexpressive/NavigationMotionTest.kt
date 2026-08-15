package dev.antigravity.classevivaexpressive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationMotionTest {

  @Test
  fun topLevelSibling_usesTopLevelSwitch() {
    val decision = decideRouteMotion(fromRoute = "home", toRoute = "grades")

    assertEquals(RouteMotionKind.TopLevelSwitch, decision.kind)
    assertNull(decision.sharedKey)
  }

  @Test
  fun requestedDashboardSharedKey_usesSharedContainer() {
    val decision = decideRouteMotion(
      fromRoute = "home",
      toRoute = "grades",
      motionOrigin = MotionOrigin.DashboardGrades,
    )

    assertEquals(RouteMotionKind.SharedContainer, decision.kind)
    assertEquals(RouteSharedKeys.DashboardGrades, decision.sharedKey)
  }

  @Test
  fun returningFromSharedDestination_keepsSharedContainer() {
    val decision = decideRouteMotion(
      fromRoute = "grades",
      toRoute = "home",
      motionOrigin = MotionOrigin.DashboardGrades,
    )

    assertEquals(RouteMotionKind.SharedContainer, decision.kind)
    assertEquals(RouteSharedKeys.DashboardGrades, decision.sharedKey)
  }

  @Test
  fun moreToHubChild_usesSharedContainer() {
    val decision = decideRouteMotion(
      fromRoute = "more",
      toRoute = "settings",
      motionOrigin = MotionOrigin.HubSettings,
    )

    assertEquals(RouteMotionKind.SharedContainer, decision.kind)
    assertEquals(RouteSharedKeys.HubSettings, decision.sharedKey)
  }

  @Test
  fun destinationSharedKey_mapsCommunicationsRoute() {
    val sharedKey = RouteSharedKeys.forDestinationBase("communications?tab={tab}")

    assertEquals(RouteSharedKeys.DashboardCommunications, sharedKey)
  }

  @Test
  fun unknownRoute_usesFallbackScale() {
    val decision = decideRouteMotion(fromRoute = "studentScore", toRoute = "settings")

    assertEquals(RouteMotionKind.FallbackScale, decision.kind)
    assertNull(decision.sharedKey)
  }

  @Test
  fun routeNormalization_stripsArgumentsAndPath() {
    assertEquals("grades", normalizeRoute("grades?gradeId={gradeId}"))
    assertEquals("student-score", normalizeRoute("student-score/import"))
  }

  @Test
  fun motionOrigin_roundTripsThroughStableWireName() {
    assertEquals(MotionOrigin.HubDocuments, MotionOrigin.fromWireName("hub_documents"))
    assertNull(MotionOrigin.fromWireName("unknown"))
  }
}
