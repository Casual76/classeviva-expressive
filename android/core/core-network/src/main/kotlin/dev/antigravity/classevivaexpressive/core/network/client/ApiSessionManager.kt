package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.CieCredentials
import dev.antigravity.classevivaexpressive.core.datastore.AccountSessionLease
import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ApiSessionManager @Inject constructor(
  private val sessionStore: SessionStorage,
  @param:Named("authService") private val authService: ClassevivaAuthService,
) {
  private val refreshLock = Any()

  fun currentSession(): UserSession? = sessionStore.readCurrentSession()

  fun isCurrentAccount(lease: AccountSessionLease): Boolean = sessionStore.accountGuard.isCurrent(lease)

  fun canUseRest(): Boolean = sessionStore.readAuthSnapshot()?.authenticationState?.rest == RestAuthenticationState.READY

  suspend fun persistAuthenticatedSession(session: UserSession, password: String) = withContext(Dispatchers.IO) {
    sessionStore.writeAuthSnapshot(
      StoredAuthSnapshot(session = session, credentials = StoredCredentials(session.username, password)),
    )
  }

  /** A network outage does not turn a saved account into a logout. */
  suspend fun restoreValidSession(): UserSession? = withContext(Dispatchers.IO) {
    val current = sessionStore.readCurrentSession() ?: return@withContext null
    if (!canUseRest()) return@withContext current
    val status = runCatching { authService.checkStatus(current.token).execute() }.getOrNull()
      ?: return@withContext sessionStore.readCurrentSession()
    if (status.code() == 401 || status.code() == 403) refreshTokenBlocking(current.token, checkStatus = false)
    sessionStore.readCurrentSession()
  }

  fun refreshTokenBlocking(failedToken: String? = null): String? =
    refreshTokenBlocking(failedToken, checkStatus = true)

  /** The comparison and renewal share a lock, while the commit still checks the account snapshot. */
  private fun refreshTokenBlocking(failedToken: String?, checkStatus: Boolean): String? = synchronized(refreshLock) {
    val capture = sessionStore.captureAuthSnapshot()
    val lease = capture.lease
    val captured = capture.snapshot ?: return null
    if (!sessionStore.accountGuard.isCurrent(lease)) return null
    val current = captured.session ?: return null
    if (captured.authenticationState.rest != RestAuthenticationState.READY) return null
    if (!failedToken.isNullOrBlank() && current.token.isNotBlank() && current.token != failedToken) return current.token
    if (current.authMethod == AuthMethod.CIE) return refreshCie(captured, lease)

    if (checkStatus && current.token.isNotBlank()) {
      val status = runCatching { authService.checkStatus(current.token).execute() }.getOrNull() ?: return null
      if (status.isSuccessful) return current.token
      if (status.code() != 401 && status.code() != 403) return null
    }
    val credentials = captured.credentials ?: return markReauthenticationRequired(captured, lease)
    val response = runCatching {
      authService.login(LoginRequestDto(uid = credentials.username, password = credentials.password)).execute()
    }.getOrNull() ?: return null
    if (!response.isSuccessful) {
      if (response.code() == 401 || response.code() == 403) markReauthenticationRequired(captured, lease)
      return null
    }
    val body = response.body() ?: return null
    val token = body.token?.takeIf(String::isNotBlank) ?: return null
    val returnedId = normalizeStudentId(body.ident) ?: normalizeStudentId(body.userId) ?: normalizeStudentId(body.alternateUserId)
    if (returnedId != null && returnedId != current.studentId) return markReauthenticationRequired(captured, lease)
    val updated = captured.copy(session = current.copy(token = token))
    return if (commitRefresh(captured, updated, lease)) token else null
  }

  private fun refreshCie(captured: StoredAuthSnapshot, lease: AccountSessionLease): String? {
    val current = captured.session ?: return null
    val cie = captured.cie ?: return markReauthenticationRequired(captured, lease)
    val refresh = cie.refreshToken?.takeIf(String::isNotBlank) ?: return markReauthenticationRequired(captured, lease)
    val response = runCatching { authService.spidRefresh(CieProtocol.refreshBody(cie.pa, refresh)).execute() }
      .getOrNull() ?: return null
    if (!response.isSuccessful) {
      val code = CieProtocol.errorCode(runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault(""))
      val rejected = response.code() == 401 || response.code() == 403 ||
        (response.code() == 422 && code in setOf("RFTokenNotValid", "InvalidPAToken"))
      if (rejected) markReauthenticationRequired(captured, lease)
      return null
    }
    val body = response.body() ?: return null
    val token = body.token?.takeIf(String::isNotBlank) ?: return null
    val expiresAt = CieProtocol.expiresAt(body.expiresAt) ?: return null
    if (body.ident != null && body.ident != cie.ident) return markReauthenticationRequired(captured, lease)
    val updated = captured.copy(
      session = current.copy(token = token),
      cie = CieCredentials(
        pa = cie.pa,
        refreshToken = response.headers()[CieProtocol.RefreshHeader]?.takeIf(String::isNotBlank) ?: refresh,
        ident = cie.ident,
        expiresAt = expiresAt,
      ),
    )
    return if (commitRefresh(captured, updated, lease)) token else null
  }

  private fun markReauthenticationRequired(captured: StoredAuthSnapshot, lease: AccountSessionLease): String? {
    commitRefresh(
      captured,
      captured.copy(authenticationState = captured.authenticationState.copy(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)),
      lease,
    )
    return null
  }

  /** Merge independent cookie rotation, never a superseded login or a terminal state change. */
  private fun commitRefresh(captured: StoredAuthSnapshot, updated: StoredAuthSnapshot, lease: AccountSessionLease): Boolean {
    if (sessionStore.compareAndSetAuthSnapshot(captured, updated, lease)) return true
    val latest = sessionStore.readAuthSnapshot() ?: return false
    if (latest.session != captured.session || latest.cie != captured.cie || latest.credentials != captured.credentials ||
      latest.authenticationState.rest != captured.authenticationState.rest) return false
    return sessionStore.compareAndSetAuthSnapshot(
      latest,
      updated.copy(portal = latest.portal, authenticationState = latest.authenticationState.copy(rest = updated.authenticationState.rest)),
      lease,
    )
  }
}
