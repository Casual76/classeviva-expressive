package dev.antigravity.classevivaexpressive

import java.io.Serializable

internal enum class RouteMotionKind {
  TopLevelSwitch,
  SharedContainer,
  FallbackScale,
}

internal data class RouteMotionDecision(
  val kind: RouteMotionKind,
  val sharedKey: String? = null,
)

internal const val MotionOriginStateKey = "route_motion_origin"

/**
 * Entry-scoped origin for route motion. It is stored on each NavBackStackEntry instead of in
 * composition-global state, so restored tabs and predictive back retain their own motion context.
 */
internal enum class MotionOrigin(
  val wireName: String,
  val sharedKey: String? = null,
) : Serializable {
  NavigationPill("navigation_pill"),
  DeepLink("deep_link"),
  DashboardGrades("dashboard_grades", RouteSharedKeys.DashboardGrades),
  DashboardCommunications("dashboard_communications", RouteSharedKeys.DashboardCommunications),
  HubLessons("hub_lessons", RouteSharedKeys.HubLessons),
  HubAbsences("hub_absences", RouteSharedKeys.HubAbsences),
  HubMaterials("hub_materials", RouteSharedKeys.HubMaterials),
  HubHomework("hub_homework", RouteSharedKeys.HubHomework),
  HubDocuments("hub_documents", RouteSharedKeys.HubDocuments),
  HubProfessors("hub_professors", RouteSharedKeys.HubProfessors),
  HubMeetings("hub_meetings", RouteSharedKeys.HubMeetings),
  HubSettings("hub_settings", RouteSharedKeys.HubSettings),
  HubNotes("hub_notes", RouteSharedKeys.HubNotes),
  ;

  companion object {
    fun fromWireName(value: String?): MotionOrigin? = entries.firstOrNull { it.wireName == value }
  }
}

internal object RouteSharedKeys {
  const val DashboardGrades = "dashboard:grades"
  const val DashboardCommunications = "dashboard:communications"
  const val HubLessons = "hub:lessons"
  const val HubAbsences = "hub:absences"
  const val HubMaterials = "hub:materials"
  const val HubHomework = "hub:homework"
  const val HubDocuments = "hub:documents"
  const val HubProfessors = "hub:professors"
  const val HubMeetings = "hub:meetings"
  const val HubSettings = "hub:settings"
  const val HubNotes = "hub:notes"

  fun forDestinationBase(route: String?): String? = when (normalizeRoute(route)) {
    "grades" -> DashboardGrades
    "communications" -> DashboardCommunications
    "lessons" -> HubLessons
    "absences" -> HubAbsences
    "materials" -> HubMaterials
    "homework" -> HubHomework
    "documents" -> HubDocuments
    "professors" -> HubProfessors
    "meetings" -> HubMeetings
    "settings" -> HubSettings
    "notes" -> HubNotes
    else -> null
  }

  fun forMoreHubDestination(route: String): String? = when (normalizeRoute(route)) {
    "lessons" -> HubLessons
    "absences" -> HubAbsences
    "materials" -> HubMaterials
    "homework" -> HubHomework
    "documents" -> HubDocuments
    "professors" -> HubProfessors
    "meetings" -> HubMeetings
    "settings" -> HubSettings
    "notes" -> HubNotes
    else -> null
  }
}

internal fun normalizeRoute(route: String?): String? {
  return route
    ?.substringBefore("?")
    ?.substringBefore("/")
    ?.takeIf { it.isNotBlank() }
}

internal fun decideRouteMotion(
  fromRoute: String?,
  toRoute: String?,
  motionOrigin: MotionOrigin? = null,
): RouteMotionDecision {
  val from = normalizeRoute(fromRoute)
  val to = normalizeRoute(toRoute)
  val fromSharedKey = RouteSharedKeys.forDestinationBase(from)
  val toSharedKey = RouteSharedKeys.forDestinationBase(to)
  val requestedSharedKey = motionOrigin?.sharedKey

  return when {
    requestedSharedKey != null && (requestedSharedKey == fromSharedKey || requestedSharedKey == toSharedKey) -> {
      RouteMotionDecision(RouteMotionKind.SharedContainer, requestedSharedKey)
    }

    from in topLevelRoutes && to in topLevelRoutes -> {
      RouteMotionDecision(RouteMotionKind.TopLevelSwitch)
    }

    else -> RouteMotionDecision(RouteMotionKind.FallbackScale)
  }
}
