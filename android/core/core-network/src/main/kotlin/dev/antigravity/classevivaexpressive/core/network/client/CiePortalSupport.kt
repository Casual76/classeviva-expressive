package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.StoredPortalCookie
import kotlinx.serialization.json.JsonElement
import okhttp3.Cookie
import okhttp3.HttpUrl

class ClassevivaPortalReauthenticationException : ClassevivaNetworkException(
  "Accesso al portale scaduto. Accedi di nuovo con la CIE per aggiornare queste funzioni.",
)

internal data class CiePortalIdentity(val ident: String, val routeId: String)

/** The routing ID can differ from the student identity; neither may be guessed from the other. */
internal fun parseCiePortalIdentity(payload: JsonElement): CiePortalIdentity? {
  val root = payload.obj()
  val scopes = listOf(root, root["data"].obj(), root["user"].obj(), root["student"].obj())
  val identities = scopes.mapNotNull { it.string("ident", "identity") }.distinct()
  val ident = identities.singleOrNull()?.takeIf { CieProtocol.studentId(it) != null } ?: return null
  val routeIds = scopes.mapNotNull { it.string("id", "studentId", "usrId", "userId") }
    .filter { it.matches(Regex("[0-9]+")) }.distinct()
  val routeId = routeIds.singleOrNull() ?: return null
  return CiePortalIdentity(ident, routeId)
}

internal fun Cookie.storageKey(): String = "$domain|$path|$name"

internal fun Cookie.toStoredPortalCookie(): StoredPortalCookie = StoredPortalCookie(
  name = name,
  value = value,
  domain = domain,
  path = path,
  secure = secure,
  httpOnly = httpOnly,
  hostOnly = hostOnly,
  expiresAt = expiresAt.takeIf { persistent },
  persistent = persistent,
)

internal fun StoredPortalCookie.toOkHttpCookie(origin: HttpUrl): Cookie? {
  val normalizedDomain = domain.removePrefix(".")
  if (origin.host != normalizedDomain && !(origin.host == "web.spaggiari.eu" && normalizedDomain == "spaggiari.eu")) return null
  if (!path.startsWith("/") || name.isBlank() || expiresAt?.let { it <= System.currentTimeMillis() } == true) return null
  return runCatching {
    Cookie.Builder().name(name).value(value).path(path).apply {
      if (hostOnly) hostOnlyDomain(normalizedDomain) else domain(normalizedDomain)
      if (secure) secure()
      if (httpOnly == true) httpOnly()
      if (persistent) expiresAt?.let { expiresAt(it) }
    }.build()
  }.getOrNull()
}

/** CookieManager exports request cookies, not Set-Cookie attributes. Unknown metadata stays unknown. */
internal fun parseCieCookieHeader(header: String, origin: HttpUrl): List<StoredPortalCookie>? {
  if (header.isBlank() || header.length > 65_536 || header.any { it == '\r' || it == '\n' }) return null
  val cookies = header.split(';').map { entry ->
    val parts = entry.trim().split('=', limit = 2)
    if (parts.size != 2 || parts[0].isBlank()) return null
    StoredPortalCookie(name = parts[0], value = parts[1], domain = origin.host, secure = origin.isHttps)
  }
  if (cookies.map { it.name }.distinct().size != cookies.size) return null
  return cookies.takeIf { it.all { cookie -> cookie.toOkHttpCookie(origin) != null } }
}
