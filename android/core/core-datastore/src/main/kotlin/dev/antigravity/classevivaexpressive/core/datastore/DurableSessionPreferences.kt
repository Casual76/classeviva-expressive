package dev.antigravity.classevivaexpressive.core.datastore

internal val SessionPreferenceKeys = listOf("auth_snapshot_v1", "session_payload", "credential_username", "credential_password")

/** SharedPreferences updates its process cache even when its disk commit later fails. */
internal interface SessionPreferenceAccess {
  fun readValues(): Map<String, String?>
  fun commitValues(values: Map<String, String?>): Boolean
}

/** Shared by every Android wrapper of the same encrypted preferences file in this process. */
internal class SessionPersistenceMonitor {
  val lock = Any()
  var rollbackImage: Map<String, String?>? = null
}

/**
 * A failed commit is not a rollback in SharedPreferences. Restore all managed keys explicitly.
 * If restoration is also unconfirmed, block process-cache reads rather than publish uncertain data.
 * This honors the platform's commit result; it cannot provide stronger guarantees than the filesystem.
 */
internal class RollbackSessionPersistence(
  private val access: SessionPreferenceAccess,
  private val monitor: SessionPersistenceMonitor,
) : SecureSessionPersistence {
  private fun readValues(): Map<String, String?> = synchronized(monitor.lock) {
    if (monitor.rollbackImage != null) throw SecureSessionStorageException()
    access.readValues()
  }

  override fun readSnapshot(): String? = readValues()["auth_snapshot_v1"]
  override fun readLegacySession(): String? = readValues()["session_payload"]
  override fun readLegacyCredentials(): StoredCredentials? {
    val values = readValues()
    val username = values["credential_username"]?.trim().orEmpty()
    val password = values["credential_password"].orEmpty()
    return if (username.isBlank() || password.isBlank()) null else StoredCredentials(username, password)
  }

  override fun commit(snapshot: String?): Boolean = synchronized(monitor.lock) {
    // Keep the original rollback image across failed recovery attempts; current cache may be unsafe.
    val before = monitor.rollbackImage ?: access.readValues().toMap()
    val candidate = SessionPreferenceKeys.associateWith { key -> if (key == "auth_snapshot_v1") snapshot else null }
    if (runCatching { access.commitValues(candidate) }.getOrDefault(false)) {
      monitor.rollbackImage = null
      return@synchronized true
    }
    monitor.rollbackImage = before
    if (runCatching { access.commitValues(before) }.getOrDefault(false)) monitor.rollbackImage = null
    false
  }
}

internal object AndroidSessionPersistenceMonitors {
  private val monitors = mutableMapOf<String, SessionPersistenceMonitor>()
  @Synchronized fun forFile(path: String): SessionPersistenceMonitor = monitors.getOrPut(path) { SessionPersistenceMonitor() }
}
