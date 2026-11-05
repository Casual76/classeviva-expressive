package dev.antigravity.classevivaexpressive.core.datastore

import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.PortalAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import kotlinx.serialization.Serializable

@Serializable
data class StoredCredentials(val username: String, val password: String) {
  override fun toString(): String = "StoredCredentials(redacted)"
}

@Serializable
data class CieCredentials(
  val pa: String,
  val refreshToken: String?,
  val ident: String,
  /** Expiry from the authenticated server response, as epoch milliseconds. */
  val expiresAt: Long,
) {
  override fun toString(): String = "CieCredentials(redacted)"
}

/** Unknown WebView attributes stay unknown; subsequent Set-Cookie responses retain real metadata. */
@Serializable
data class StoredPortalCookie(
  val name: String,
  val value: String,
  val domain: String = "web.spaggiari.eu",
  val path: String = "/",
  val secure: Boolean = true,
  val httpOnly: Boolean? = null,
  val hostOnly: Boolean = true,
  val expiresAt: Long? = null,
  val persistent: Boolean = false,
) {
  override fun toString(): String = "StoredPortalCookie(redacted)"
}

/** Only cookies checked against whoami for this student may become an active portal session. */
@Serializable
data class PortalSessionSnapshot(
  val ident: String,
  val studentId: String,
  val portalRouteId: String,
  val cookies: List<StoredPortalCookie>,
) {
  override fun toString(): String = "PortalSessionSnapshot(redacted)"
}

@Serializable
data class StoredAuthSnapshot(
  val session: UserSession? = null,
  val credentials: StoredCredentials? = null,
  val cie: CieCredentials? = null,
  val portal: PortalSessionSnapshot? = null,
  val authenticationState: AuthenticationState = AuthenticationState(
    rest = if (session == null) RestAuthenticationState.SIGNED_OUT else RestAuthenticationState.READY,
    portal = if (portal == null) PortalAuthenticationState.UNAVAILABLE else PortalAuthenticationState.READY,
  ),
  val schemaVersion: Int = 1,
) {
  internal fun validate() {
    require(schemaVersion == 1) { "Versione archivio di accesso non supportata." }
    require(credentials == null || cie == null) { "Metodi di accesso incompatibili." }
    if (session == null) {
      require(cie == null && portal == null) { "Materiale di accesso senza account." }
      require(authenticationState.rest == RestAuthenticationState.SIGNED_OUT) { "Stato di accesso incoerente." }
      return
    }
    require(session.studentId.isNotBlank()) { "Identità scolastica assente." }
    require(authenticationState.rest != RestAuthenticationState.SIGNED_OUT) { "Stato di accesso incoerente." }
    when (session.authMethod) {
      AuthMethod.PASSWORD -> require(cie == null) { "Materiale CIE incompatibile con le credenziali." }
      AuthMethod.CIE -> {
        require(credentials == null) { "Credenziali incompatibili con la CIE." }
        require(authenticationState.rest != RestAuthenticationState.READY || cie != null) { "Materiale CIE assente." }
        cie?.let {
          require(it.pa.isNotBlank() && it.ident.isNotBlank() && it.expiresAt > 0) { "Materiale CIE incompleto." }
          require(session.ident == it.ident) { "Identità CIE incoerente." }
        }
      }
    }
    portal?.let {
      require(it.studentId == session.studentId && it.ident.isNotBlank() && it.portalRouteId.isNotBlank()) {
        "Identità del portale incoerente."
      }
      require(session.ident == null || it.ident == session.ident) { "Identità del portale diversa dall'account." }
      require(it.cookies.all { cookie ->
        cookie.domain.removePrefix(".") in setOf("web.spaggiari.eu", "spaggiari.eu") && cookie.path.startsWith("/") &&
          cookie.name.isNotBlank() && !cookie.name.any { ch -> ch <= ' ' || ch in ";=," } &&
          !cookie.value.any { ch -> ch < ' ' || ch == ';' }
      }) { "Cookie del portale non ammesso." }
    }
    require(authenticationState.portal != PortalAuthenticationState.READY || portal != null) { "Sessione portale assente." }
  }

  override fun toString(): String = "StoredAuthSnapshot(method=${session?.authMethod}, state=$authenticationState)"
}
