package dev.antigravity.classevivaexpressive.core.data

import dev.antigravity.classevivaexpressive.core.datastore.SecureSessionPersistence
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession

internal class MemorySessionPersistence : SecureSessionPersistence {
  private var raw: String? = null
  var failCommit = false
  override fun readSnapshot(): String? = raw
  override fun readLegacySession(): String? = null
  override fun readLegacyCredentials(): StoredCredentials? = null
  override fun commit(snapshot: String?): Boolean {
    if (failCommit) return false
    raw = snapshot
    return true
  }
}

internal fun testSessionStore(session: UserSession? = UserSession("token", "student-1", "student", StudentProfile(id = "student-1"))): SessionStore =
  SessionStore(MemorySessionPersistence()).also { store ->
    session?.let { store.writeAuthSnapshot(StoredAuthSnapshot(session = it, credentials = StoredCredentials("student", "password"))) }
  }
