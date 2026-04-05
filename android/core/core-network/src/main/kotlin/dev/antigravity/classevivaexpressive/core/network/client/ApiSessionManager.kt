package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ApiSessionManager @Inject constructor(
  private val sessionStore: SessionStorage,
  @Named("authService") private val authService: ClassevivaAuthService,
) {
  private val refreshLock = Any()

  fun currentSession(): UserSession? = sessionStore.readCurrentSession()

  suspend fun persistAuthenticatedSession(
    session: UserSession,
    password: String,
  ) = withContext(Dispatchers.IO) {
    sessionStore.writeCredentials(username = session.username, password = password)
    sessionStore.writeSession(session)
  }

  suspend fun restoreValidSession(): UserSession? = withContext(Dispatchers.IO) {
    val current = sessionStore.readCurrentSession() ?: return@withContext null
    if (isTokenValid(current.token)) {
      return@withContext current
    }
    refreshTokenBlocking(failedToken = current.token)
    sessionStore.readCurrentSession()
  }

  fun refreshTokenBlocking(failedToken: String? = null): String? = synchronized(refreshLock) {
    val current = sessionStore.readCurrentSession() ?: return null
    if (!failedToken.isNullOrBlank() && current.token.isNotBlank() && current.token != failedToken) {
      return current.token
    }
    if (current.token.isNotBlank() && isTokenValid(current.token)) {
      return current.token
    }

    val credentials = sessionStore.readStoredCredentials() ?: return null
    val response = runCatching {
      authService.login(LoginRequestDto(uid = credentials.username, password = credentials.password)).execute()
    }.getOrNull() ?: return null

    if (!response.isSuccessful) {
      sessionStore.clear()
      return null
    }

    val body = response.body() ?: return null
    val refreshedToken = body.token?.takeIf(String::isNotBlank) ?: return null
    val refreshedSession = current.copy(
      token = refreshedToken,
      studentId = current.studentId.ifBlank {
        normalizeStudentId(body.ident)
          ?: normalizeStudentId(body.userId)
          ?: normalizeStudentId(body.alternateUserId)
          ?: current.studentId
      },
      username = credentials.username,
    )
    sessionStore.writeSession(refreshedSession)
    return refreshedToken
  }

  private fun isTokenValid(token: String): Boolean {
    if (token.isBlank()) return false
    val response = runCatching { authService.checkStatus(token).execute() }.getOrNull() ?: return false
    return response.isSuccessful
  }
}
