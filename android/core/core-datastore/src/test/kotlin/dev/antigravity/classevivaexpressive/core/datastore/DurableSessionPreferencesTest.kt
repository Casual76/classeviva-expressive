package dev.antigravity.classevivaexpressive.core.datastore

import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import java.util.ArrayDeque
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DurableSessionPreferencesTest {
  private val oldSnapshot = StoredAuthSnapshot(
    session = UserSession("old-token", "123", "student", StudentProfile()),
    credentials = StoredCredentials("student", "old-password"),
  )
  private val json = Json { encodeDefaults = true }

  @Test fun failedCommitRestoresBothCacheAndDiskBeforeAnotherStoreCanOpen() {
    val original = managedValues(snapshot = json.encodeToString(oldSnapshot))
    val access = CacheBeforeDiskPreferences(original)
    val monitor = SessionPersistenceMonitor()
    val persistence = RollbackSessionPersistence(access, monitor)
    val store = SessionStore(persistence)
    access.results.addAll(listOf(false, true))

    val result = runCatching { store.writeAuthSnapshot(oldSnapshot.copy(session = oldSnapshot.session!!.copy(token = "undurable-token"))) }

    assertTrue(result.isFailure)
    assertEquals(original, access.cache)
    assertEquals(original, access.disk)
    assertEquals(oldSnapshot, store.readAuthSnapshot())
    assertEquals(oldSnapshot, SessionStore(RollbackSessionPersistence(access, monitor)).readAuthSnapshot())
    assertEquals(2, access.writes)
  }

  @Test fun failedMigrationRestoresAllLegacyValuesIncludingCache() {
    val original = managedValues(
      legacySession = json.encodeToString(oldSnapshot.session!!),
      legacyUsername = "student", legacyPassword = "old-password",
    )
    val access = CacheBeforeDiskPreferences(original)
    val monitor = SessionPersistenceMonitor()
    access.results.addAll(listOf(false, true))

    val failed = SessionStore(RollbackSessionPersistence(access, monitor))

    assertEquals(RestAuthenticationState.STORAGE_UNAVAILABLE, failed.authenticationState.value.rest)
    assertEquals(original, access.cache)
    assertEquals(original, access.disk)
    val recovered = SessionStore(RollbackSessionPersistence(access, monitor))
    assertEquals(oldSnapshot, recovered.readAuthSnapshot())
    assertNull(access.cache["session_payload"])
    assertNull(access.cache["credential_password"])
  }

  @Test fun failedRollbackBlocksProcessCacheReadsAcrossWrapperReopen() {
    val original = managedValues(snapshot = json.encodeToString(oldSnapshot))
    val access = CacheBeforeDiskPreferences(original)
    val monitor = SessionPersistenceMonitor()
    val store = SessionStore(RollbackSessionPersistence(access, monitor))
    access.results.addAll(listOf(false, false))
    assertTrue(runCatching { store.writeAuthSnapshot(oldSnapshot.copy(session = oldSnapshot.session!!.copy(token = "unconfirmed"))) }.isFailure)

    val reopened = SessionStore(RollbackSessionPersistence(access, monitor))
    assertEquals(RestAuthenticationState.STORAGE_UNAVAILABLE, reopened.authenticationState.value.rest)
    assertNull(reopened.readCurrentSession())
    assertEquals(oldSnapshot, store.readAuthSnapshot())

    // A newly authenticated candidate can recover the boundary only with a verified full commit.
    val recovered = oldSnapshot.copy(session = oldSnapshot.session!!.copy(token = "verified-new"))
    reopened.writeAuthSnapshot(recovered)
    assertEquals(recovered, SessionStore(RollbackSessionPersistence(access, monitor)).readAuthSnapshot())
  }

  @Test fun exceptionAfterCacheMutationAlsoRestoresPreviousDurableImage() {
    val original = managedValues(snapshot = json.encodeToString(oldSnapshot))
    val access = CacheBeforeDiskPreferences(original).apply { throwAfterNextCacheWrite = true }
    val monitor = SessionPersistenceMonitor()
    val persistence = RollbackSessionPersistence(access, monitor)
    assertFalse(persistence.commit("candidate"))
    assertEquals(original, access.cache)
    assertEquals(original, access.disk)
    assertEquals(json.encodeToString(oldSnapshot), persistence.readSnapshot())
  }

  @Test fun recoveryRollbackUsesKnownDurableImageInsteadOfUncertainProcessCache() {
    val original = managedValues(snapshot = json.encodeToString(oldSnapshot))
    val access = CacheBeforeDiskPreferences(original)
    val monitor = SessionPersistenceMonitor()
    val persistence = RollbackSessionPersistence(access, monitor)
    access.results.add(false)
    access.throwBeforeWrites.add(2) // The attempted rollback never reaches its cache mutation.
    assertFalse(persistence.commit("first-unconfirmed-candidate"))
    assertEquals("first-unconfirmed-candidate", access.cache["auth_snapshot_v1"])
    assertTrue(runCatching { persistence.readSnapshot() }.isFailure)

    access.results.addAll(listOf(false, true))
    assertFalse(persistence.commit("second-unconfirmed-candidate"))
    assertEquals(original, access.cache)
    assertEquals(original, access.disk)
    assertEquals(json.encodeToString(oldSnapshot), persistence.readSnapshot())
  }

  private fun managedValues(
    snapshot: String? = null,
    legacySession: String? = null,
    legacyUsername: String? = null,
    legacyPassword: String? = null,
  ): Map<String, String?> = mapOf(
    "auth_snapshot_v1" to snapshot,
    "session_payload" to legacySession,
    "credential_username" to legacyUsername,
    "credential_password" to legacyPassword,
  )

  /** Models the platform ordering: cache mutation happens before the disk result is known. */
  private class CacheBeforeDiskPreferences(original: Map<String, String?>) : SessionPreferenceAccess {
    var cache = original.toMap()
    var disk = original.toMap()
    val results = ArrayDeque<Boolean>()
    val throwBeforeWrites = mutableSetOf<Int>()
    var writes = 0
    var throwAfterNextCacheWrite = false
    override fun readValues(): Map<String, String?> = cache
    override fun commitValues(values: Map<String, String?>): Boolean {
      writes++
      if (throwBeforeWrites.remove(writes)) throw IllegalStateException("write failed before cache mutation")
      cache = values.toMap()
      if (throwAfterNextCacheWrite) {
        throwAfterNextCacheWrite = false
        throw IllegalStateException("write failed after cache mutation")
      }
      val success = if (results.isEmpty()) true else results.removeFirst()
      if (success) disk = values.toMap()
      return success
    }
  }
}
