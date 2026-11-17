package dev.antigravity.classevivaexpressive

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A notification/widget command, including the identity of the data it refers to. */
internal data class AppLink(
  val route: String,
  val studentId: String? = null,
  val schoolYearId: String? = null,
) {
  fun scopeMismatch(currentStudentId: String, currentSchoolYearId: String): AppLinkScopeMismatch? = when {
    studentId != null && studentId != currentStudentId -> AppLinkScopeMismatch.STUDENT
    schoolYearId != null && schoolYearId != currentSchoolYearId -> AppLinkScopeMismatch.SCHOOL_YEAR
    else -> null
  }
}

internal enum class AppLinkScopeMismatch { STUDENT, SCHOOL_YEAR }

/**
 * Resolve the command explicitly. Overlapping generic and parameterized Navigation deep links can
 * match the same URI while discarding its item arguments. A tap must retain every identity field,
 * and a note must open the Notes tab even when no individual note was included in a summary.
 */
internal fun resolveAppLink(rawUri: String): AppLink? = runCatching {
  val uri = URI(rawUri)
  if (!uri.scheme.equals("classevivaexpressive", ignoreCase = true) || uri.rawAuthority != "open") return null
  val parameters = linkedMapOf<String, String>()
  uri.rawQuery?.split('&')?.filter(String::isNotEmpty)?.forEach { parameter ->
    val key = URLDecoder.decode(parameter.substringBefore('='), "UTF-8")
    val value = URLDecoder.decode(parameter.substringAfter('=', ""), "UTF-8")
    // Two different IDs for one field have no well-defined destination.
    if (parameters.containsKey(key)) return null
    parameters[key] = value
  }
  fun value(key: String): String? = parameters[key]?.takeIf(String::isNotBlank)
  fun route(base: String, vararg keys: String): String {
    val query = keys.mapNotNull { key -> value(key)?.let { "$key=${encodeAppLinkArgument(it)}" } }
    return base + if (query.isEmpty()) "" else query.joinToString("&", prefix = if ('?' in base) "&" else "?")
  }
  val target = when (uri.rawPath) {
    "/home" -> "home"
    "/grades" -> route("grades", "gradeId")
    "/agenda" -> route("agenda", "agendaId", "date")
    "/homework" -> route("homework", "homeworkId")
    "/communications" -> route("communications", "tab", "pubId", "evtCode")
    "/notes" -> route("communications?tab=notes", "noteId", "categoryCode")
    "/absences" -> route("absences", "absenceId")
    "/lessons" -> "lessons"
    "/meetings" -> "meetings"
    "/settings" -> "settings"
    "/assistant" -> value("conversationId")?.let { "assistant/${encodeAppLinkArgument(it)}" } ?: "assistant"
    else -> return null
  }
  AppLink(target, value("studentId"), value("schoolYearId"))
}.getOrNull()

private fun encodeAppLinkArgument(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
