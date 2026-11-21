package dev.antigravity.classevivaexpressive.core.network.client

import kotlinx.serialization.json.Json

/**
 * A one-time portal ticket issued against the REST session (`GET /rest/v1/auth/ticket`).
 *
 * The official app (5.12.4) asks for it and opens the portal through
 * `repx/app/default/restbridge.php?t=<ticket>`: that is how a CIE/SPID session, which has no
 * password to fall back on, gets a portal cookie again once the first one has expired.
 */
fun interface PortalTicketSource {
  suspend fun ticket(): String?
}

internal const val PortalRestBridgePath = "/repx/app/default/restbridge.php"
// Printable ASCII without spaces: the real ticket (Nov 2026, ~160 chars) is not plain base64.
private fun usableTicket(value: String): Boolean = value.length in 8..4096 && value.all { it in '!'..'~' }

/** Never logs the ticket: only the HTTP code and the JSON shape. */
internal suspend fun fetchPortalTicket(api: ClassevivaApiService): String? {
  val response = runCatching { api.getPortalTicket() }.getOrElse { error ->
    logCieDiag("ticket: ${error.javaClass.simpleName}")
    return null
  }
  val text = runCatching { (response.body() ?: response.errorBody())?.use { it.string() }.orEmpty() }.getOrDefault("")
  if (!response.isSuccessful) {
    logCieDiag("ticket: ${response.code()}")
    return null
  }
  val payload = runCatching { Json.parseToJsonElement(text) }.getOrNull()
  logCieDiag("ticket: ${response.code()} shape=${jsonShape(payload)}")
  val ticket = payload.obj().string("ticket") ?: return null
  // Which punctuation it uses (never the ticket): tells how it must travel in the URL.
  logCieDiag("ticket: len=${ticket.length} punct=[${ticket.filterNot(Char::isLetterOrDigit).toSet().sorted().joinToString("")}]")
  return ticket.takeIf(::usableTicket)
}
