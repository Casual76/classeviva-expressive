package dev.antigravity.classevivaexpressive

internal enum class RouteMotionKind {
  TopLevelSwitch,
  SharedContainer,
  FallbackScale,
}

internal data class RouteMotionDecision(
  val kind: RouteMotionKind,
  val sharedKey: String? = null,
)

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
  requestedSharedKey: String? = null,
): RouteMotionDecision {
  val from = normalizeRoute(fromRoute)
  val to = normalizeRoute(toRoute)
  val fromSharedKey = RouteSharedKeys.forDestinationBase(from)
  val toSharedKey = RouteSharedKeys.forDestinationBase(to)
  val moreHubSharedKey = RouteSharedKeys.forMoreHubDestination(to.orEmpty())

  return when {
    requestedSharedKey != null && (requestedSharedKey == fromSharedKey || requestedSharedKey == toSharedKey) -> {
      RouteMotionDecision(RouteMotionKind.SharedContainer, requestedSharedKey)
    }

    from in topLevelRoutes && to in topLevelRoutes -> {
      RouteMotionDecision(RouteMotionKind.TopLevelSwitch)
    }

    from == "more" && moreHubSharedKey != null -> {
      RouteMotionDecision(RouteMotionKind.SharedContainer, moreHubSharedKey)
    }

    else -> RouteMotionDecision(RouteMotionKind.FallbackScale)
  }
}
