package dev.antigravity.classevivaexpressive

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test
import dev.antigravity.fluidengine.ui.theme.MotionOrigin

class NavigationMotionTest {

  @Test
  fun topLevelSiblings_arePeersWhenTheBarIsWhatMoved() {
    assertEquals(
      RouteMotionKind.TopLevelSwitch,
      decideRouteMotion("home", "grades", RouteMotionKind.TopLevelSwitch).kind,
    )
    assertEquals(
      RouteMotionKind.TopLevelSwitch,
      decideRouteMotion("more", "agenda", RouteMotionKind.TopLevelSwitch).kind,
    )
    // Nothing recorded: a restored stack or a deep link, which had no gesture to describe.
    assertEquals(
      RouteMotionKind.TopLevelSwitch,
      decideRouteMotion("home", "grades", requestedKind = null).kind,
    )
  }

  /**
   * The routes are peers; the gesture is not. Tapping the average on Home opens Voti out of that
   * card, and answering it with the tab bar's sideways step is what made the most direct route in
   * the app look like nothing had happened.
   */
  @Test
  fun topLevelSiblings_expandWhenSomethingOnThePageWasTouched() {
    val decision = decideRouteMotion("home", "grades", RouteMotionKind.Expand)

    assertEquals(RouteMotionKind.Expand, decision.kind)
  }

  @Test
  fun peerDirection_followsTheOrderOfTheBar() {
    assertEquals(1, decideRouteMotion("home", "grades", RouteMotionKind.TopLevelSwitch).direction)
    assertEquals(-1, decideRouteMotion("more", "agenda", RouteMotionKind.TopLevelSwitch).direction)
    // Read off the tab order rather than recorded, so the pop that undoes a step is its mirror.
    assertEquals(
      -decideRouteMotion("home", "more", RouteMotionKind.TopLevelSwitch).direction,
      decideRouteMotion("more", "home", RouteMotionKind.TopLevelSwitch).direction,
    )
  }

  @Test
  fun topLevelSibling_normalizesArguments() {
    assertEquals(
      RouteMotionKind.TopLevelSwitch,
      decideRouteMotion("home", "communications?tab=board", RouteMotionKind.TopLevelSwitch).kind,
    )
  }

  /**
   * Every hierarchical destination expands, whatever opened it. There is no second kind to fall
   * back to and therefore no pair of screens that can disagree about how they relate.
   */
  @Test
  fun hierarchicalDestinations_expandInBothDirections() {
    listOf(
      "lessons",
      "homework",
      "materials",
      "documents",
      "notes",
      "absences",
      "meetings",
      "professors",
      "settings",
    ).forEach { route ->
      assertEquals(RouteMotionKind.Expand, decideRouteMotion("more", route, RouteMotionKind.Expand).kind)
      assertEquals(RouteMotionKind.Expand, decideRouteMotion(route, "more", RouteMotionKind.Expand).kind)
    }
  }

  @Test
  fun motionOrigin_roundTripsAsPrimitiveSavedState() {
    val handle = SavedStateHandle()
    val origin = MotionOrigin(0.25f, 0.8f)

    handle.writeExpandMotion(origin)

    assertEquals(origin, handle.readMotionOrigin())
    assertEquals(RouteMotionKind.Expand, handle.readMotionKind())
    // Bundle-safe primitives only, so a stack restored after process death cannot hold a UI object.
    assertEquals(
      setOf(java.lang.Float::class.java, java.lang.String::class.java),
      handle.keys().mapNotNull { handle.get<Any>(it)?.javaClass }.toSet(),
    )
  }

  /** Deep links, notifications and restored stacks have no gesture: they grow from the centre. */
  @Test
  fun missingMotionOrigin_fallsBackToTheCentre() {
    assertEquals(MotionOrigin.Center, SavedStateHandle().readMotionOrigin())
  }

  @Test
  fun outOfRangeMotionOrigin_isClampedRatherThanTrusted() {
    val handle = SavedStateHandle()
    handle.writeExpandMotion(MotionOrigin(1.4f, -0.2f))

    assertEquals(MotionOrigin(1f, 0f), handle.readMotionOrigin())
  }

  /**
   * Reusing an entry for a tab step must forget the point an earlier expansion came out of, or the
   * next sideways move would be told to grow from a card that is no longer on screen.
   */
  @Test
  fun peerMotion_replacesAnyRecordedOrigin() {
    val handle = SavedStateHandle()
    handle.writeExpandMotion(MotionOrigin(0.1f, 0.2f))

    handle.writePeerMotion()

    assertEquals(MotionOrigin.Center, handle.readMotionOrigin())
    assertEquals(RouteMotionKind.TopLevelSwitch, handle.readMotionKind())
  }

  @Test
  fun unrecordedEntry_hasNoOpinionAboutHowItWasReached() {
    assertEquals(null, SavedStateHandle().readMotionKind())
  }

  @Test
  fun bugReport_isHierarchicalInBothDirections() {
    val openDecision = decideRouteMotion(fromRoute = "more", toRoute = BugReportRoute, requestedKind = null)
    val backDecision = decideRouteMotion(fromRoute = BugReportRoute, toRoute = "more", requestedKind = null)

    assertEquals(RouteMotionKind.Expand, openDecision.kind)
    assertEquals(RouteMotionKind.Expand, backDecision.kind)
  }

  @Test
  fun detailBackToTab_pushes() {
    val decision = decideRouteMotion(fromRoute = "settings", toRoute = "more", requestedKind = null)

    assertEquals(RouteMotionKind.Expand, decision.kind)
  }

  @Test
  fun detailToDetail_pushes() {
    val decision = decideRouteMotion(fromRoute = "studentScore", toRoute = "settings", requestedKind = null)

    assertEquals(RouteMotionKind.Expand, decision.kind)
  }

  @Test
  fun unknownRoutes_push() {
    val decision = decideRouteMotion(fromRoute = null, toRoute = null, requestedKind = null)

    assertEquals(RouteMotionKind.Expand, decision.kind)
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

  @Test
  fun agendaRequest_isOneShotPerBackStackEntry() {
    assertEquals("a1", pendingAgendaRequest(requestedAgendaId = "a1", consumedAgendaId = null))
    assertEquals(null, pendingAgendaRequest(requestedAgendaId = "a1", consumedAgendaId = "a1"))
    assertEquals("a2", pendingAgendaRequest(requestedAgendaId = "a2", consumedAgendaId = "a1"))
    assertEquals(null, pendingAgendaRequest(requestedAgendaId = "", consumedAgendaId = null))
  }

  @Test
  fun homeworkRequest_isOneShotPerBackStackEntry() {
    assertEquals("h1", pendingHomeworkRequest(requestedHomeworkId = "h1", consumedHomeworkId = null))
    assertEquals(null, pendingHomeworkRequest(requestedHomeworkId = "h1", consumedHomeworkId = "h1"))
    assertEquals("h2", pendingHomeworkRequest(requestedHomeworkId = "h2", consumedHomeworkId = "h1"))
    assertEquals(null, pendingHomeworkRequest(requestedHomeworkId = "", consumedHomeworkId = null))
  }
}
