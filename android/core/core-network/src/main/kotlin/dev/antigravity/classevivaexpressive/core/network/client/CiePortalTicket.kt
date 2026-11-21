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
private val ticketPattern = Regex("[A-Za-z0-9._~+/=-]{8,4096}")

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
  return payload.obj().string("ticket")?.takeIf(ticketPattern::matches)
}
