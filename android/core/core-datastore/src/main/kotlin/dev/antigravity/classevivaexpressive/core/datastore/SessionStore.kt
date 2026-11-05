package dev.antigravity.classevivaexpressive.core.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.AuthMethod
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import java.util.WeakHashMap
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AuthSnapshotLease(
  val snapshot: StoredAuthSnapshot?,
  val lease: AccountSessionLease,
  val authenticationEpoch: Long,
)

interface SessionStorage {
  fun readCurrentSession(): UserSession?
  fun writeSession(session: UserSession)
  fun readStoredCredentials(): StoredCredentials?
  fun writeCredentials(username: String, password: String)
  fun clear()

  /** Compatibility for existing test doubles; the production store overrides all snapshot operations. */
  val accountGuard: AccountSessionGuard get() = LegacySessionGuards.forStorage(this)
  fun readAuthSnapshot(): StoredAuthSnapshot? = readCurrentSession()?.let {
    StoredAuthSnapshot(session = it, credentials = readStoredCredentials())
  }
  /** Legacy test-double default; production overrides this with a non-blocking atomic publication. */
  fun captureAuthSnapshot(): AuthSnapshotLease = accountGuard.mutateBlocking {
    AuthSnapshotLease(readAuthSnapshot(), accountGuard.capture(), accountGuard.authenticationEpoch())
  }
  fun writeAuthSnapshot(snapshot: StoredAuthSnapshot) {
    require(snapshot.cie == null && snapshot.portal == null) { "Snapshot CIE non supportato da questo archivio." }
    if (snapshot.session == null) clear() else {
      snapshot.credentials?.let { writeCredentials(it.username, it.password) }
      writeSession(snapshot.session)
    }
  }
  fun compareAndSetAuthSnapshot(
    expected: StoredAuthSnapshot?,
    updated: StoredAuthSnapshot,
    lease: AccountSessionLease? = null,
  ): Boolean = synchronized(this) {
    if (readAuthSnapshot() != expected || (lease != null && !accountGuard.isCurrent(lease, requireReady = false))) false else {
      writeAuthSnapshot(updated)
      true
    }
  }
}

private object LegacySessionGuards {
  private val guards = WeakHashMap<SessionStorage, AccountSessionGuard>()
  @Synchronized fun forStorage(storage: SessionStorage): AccountSessionGuard = guards.getOrPut(storage) {
    AccountSessionGuard().also { guard -> guard.accept(storage.readAuthSnapshot()) }
  }
}

/**
 * Injectable persistence boundary, including Android-free tests of network/session integration.
 * Production bindings must use encrypted persistence; the Context constructor is the production path.
 */
interface SecureSessionPersistence {
  fun readSnapshot(): String?
  fun readLegacySession(): String?
  fun readLegacyCredentials(): StoredCredentials?
  /** Commit one snapshot and removal of old keys in the same durable storage transaction. */
  fun commit(snapshot: String?): Boolean
}

private class AndroidSecureSessionPersistence(context: Context) : SecureSessionPersistence {
  private val preferences by lazy {
    EncryptedSharedPreferences.create(
      context,
      "classeviva_secure_session",
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }
  private val durable = RollbackSessionPersistence(
    access = object : SessionPreferenceAccess {
      override fun readValues(): Map<String, String?> = SessionPreferenceKeys.associateWith { preferences.getString(it, null) }
      override fun commitValues(values: Map<String, String?>): Boolean = preferences.edit().apply {
        SessionPreferenceKeys.forEach { key ->
          values[key]?.let { putString(key, it) } ?: remove(key)
        }
      }.commit()
    },
    monitor = AndroidSessionPersistenceMonitors.forFile(context.applicationInfo.dataDir + "/shared_prefs/classeviva_secure_session.xml"),
  )
  override fun readSnapshot(): String? = durable.readSnapshot()
  override fun readLegacySession(): String? = durable.readLegacySession()
  override fun readLegacyCredentials(): StoredCredentials? = durable.readLegacyCredentials()
  override fun commit(snapshot: String?): Boolean = durable.commit(snapshot)
}

/** Generic on purpose: raw serialization/crypto errors can contain pieces of a secret payload. */
class SecureSessionStorageException : IllegalStateException("Impossibile leggere o salvare l'accesso protetto. Riprova.")

@Singleton
class SessionStore(private val persistence: SecureSessionPersistence) : SessionStorage {
  constructor(@ApplicationContext context: Context) : this(AndroidSecureSessionPersistence(context))

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  override val accountGuard = AccountSessionGuard()
  // Readers, including UI entry points, never wait for the commit gate. The complete durable
  // publication is one volatile value; separate reads of snapshot and guard could mix accounts.
  @Volatile private var publication = AuthSnapshotLease(null, accountGuard.capture(), accountGuard.authenticationEpoch())
  private val snapshot: StoredAuthSnapshot? get() = publication.snapshot
  private val sessionFlow = MutableStateFlow<UserSession?>(null)
  private val authenticationFlow = MutableStateFlow(AuthenticationState())
  val session: StateFlow<UserSession?> = sessionFlow
  val authenticationState: StateFlow<AuthenticationState> = authenticationFlow

  init {
    accountGuard.mutateBlocking {
      try {
        val stored = persistence.readSnapshot()
        val loaded = if (stored != null) {
          json.decodeFromString<StoredAuthSnapshot>(stored).also { it.validate() }
        } else {
          migrateLegacySnapshot()
        }
        publish(loaded)
      } catch (_: Exception) {
        // Never delete an archive on an opening/decryption/parsing failure.
        authenticationFlow.value = AuthenticationState(rest = RestAuthenticationState.STORAGE_UNAVAILABLE)
      }
    }
  }

  override fun readCurrentSession(): UserSession? = snapshot?.session
  override fun readStoredCredentials(): StoredCredentials? = snapshot?.credentials
  override fun readAuthSnapshot(): StoredAuthSnapshot? = snapshot
  override fun captureAuthSnapshot(): AuthSnapshotLease = publication

  override fun writeAuthSnapshot(snapshot: StoredAuthSnapshot) = accountGuard.mutateBlocking {
    persist(snapshot)
  }

  override fun compareAndSetAuthSnapshot(expected: StoredAuthSnapshot?, updated: StoredAuthSnapshot, lease: AccountSessionLease?): Boolean =
    compareAndSetAuthSnapshotAndCapture(expected, updated, lease) != null

  /** Publish an authenticated candidate and return its admitted epoch without a second capture race. */
  fun compareAndSetAuthSnapshotAndCapture(
    expected: StoredAuthSnapshot?,
    updated: StoredAuthSnapshot,
    lease: AccountSessionLease? = null,
  ): AuthSnapshotLease? =
    accountGuard.mutateBlocking {
      if (snapshot != expected || (lease != null && !accountGuard.isCurrent(lease, requireReady = false))) null else {
        persist(updated)
        publication
      }
    }

  override fun writeSession(session: UserSession) = accountGuard.mutateBlocking {
    persist(updatedSessionSnapshot(session))
  }

  /** Kept for the existing bootstrap, while all secret fields still commit atomically. */
  fun writeSessionSilently(session: UserSession) = accountGuard.mutateBlocking {
    persist(updatedSessionSnapshot(session), publishSession = false)
  }

  override fun writeCredentials(username: String, password: String) = accountGuard.mutateBlocking {
    val previous = snapshot ?: StoredAuthSnapshot()
    require(previous.session?.authMethod != AuthMethod.CIE) { "Usa lo snapshot completo per cambiare metodo di accesso." }
    persist(previous.copy(credentials = StoredCredentials(username.trim(), password), cie = null))
  }

  override fun clear() = accountGuard.mutateBlocking { persist(null, forceInvalidation = true) }

  /** Logout invalidates the account and excludes all old commits until private-data cleanup finishes. */
  suspend fun clearAndRun(cleanup: suspend () -> Unit) = clearAndRunWithPrevious { cleanup() }

  /** The identity being removed and its cleanup target are chosen in the same mutation barrier. */
  suspend fun clearAndRunWithPrevious(
    expectedAuthenticationEpoch: Long? = null,
    cleanup: suspend (StoredAuthSnapshot?) -> Unit,
  ) = accountGuard.mutate {
    if (expectedAuthenticationEpoch != null && accountGuard.authenticationEpoch() != expectedAuthenticationEpoch) {
      throw IllegalStateException("L'account è cambiato. Ripeti l'operazione.")
    }
    val previous = snapshot
    persist(null, forceInvalidation = true)
    cleanup(previous)
  }

  private fun updatedSessionSnapshot(session: UserSession): StoredAuthSnapshot {
    val previous = snapshot
    val sameAccount = previous?.session?.studentId == session.studentId
    return StoredAuthSnapshot(
      session = session,
      credentials = previous?.credentials.takeIf { session.authMethod == AuthMethod.PASSWORD },
      cie = previous?.cie.takeIf { sameAccount && session.authMethod == AuthMethod.CIE },
      portal = previous?.portal.takeIf { sameAccount },
      authenticationState = if (sameAccount) previous!!.authenticationState else AuthenticationState(rest = RestAuthenticationState.READY),
    )
  }

  private fun persist(value: StoredAuthSnapshot?, publishSession: Boolean = true, forceInvalidation: Boolean = false) {
    val canonical: StoredAuthSnapshot?
    try {
      value?.validate()
      val encoded = value?.let { json.encodeToString(it) }
      // Do not retain mutable collections owned by the caller after accepting a durable snapshot.
      canonical = encoded?.let { json.decodeFromString<StoredAuthSnapshot>(it) }
      if (!persistence.commit(encoded)) throw SecureSessionStorageException()
    } catch (_: Exception) {
      // In particular, never publish an in-memory token whose refresh rotation was not persisted.
      throw SecureSessionStorageException()
    }
    publish(canonical, publishSession, forceInvalidation)
  }

  private fun publish(value: StoredAuthSnapshot?, publishSession: Boolean = true, forceInvalidation: Boolean = false) {
    accountGuard.accept(value, forceInvalidation)
    // The gate is held and persistence has succeeded. An earlier reader may keep the coherent
    // old publication, which the guard can reject, but can never receive a lease for another value.
    publication = AuthSnapshotLease(value, accountGuard.capture(), accountGuard.authenticationEpoch())
    if (publishSession) {
      sessionFlow.value = value?.session
      authenticationFlow.value = value?.authenticationState ?: AuthenticationState()
    }
  }

  private fun migrateLegacySnapshot(): StoredAuthSnapshot? {
    val raw = persistence.readLegacySession() ?: return null
    val session = json.decodeFromString<UserSession>(raw)
    val embedded = json.parseToJsonElement(raw) as? JsonObject
    val embeddedUsername = embedded?.get("username")?.jsonPrimitive?.content?.trim().orEmpty()
    val embeddedPassword = embedded?.get("password")?.jsonPrimitive?.content.orEmpty()
    val credentials = persistence.readLegacyCredentials() ?: if (embeddedUsername.isNotBlank() && embeddedPassword.isNotBlank()) {
      StoredCredentials(embeddedUsername, embeddedPassword)
    } else null
    val migrated = StoredAuthSnapshot(session = session, credentials = credentials)
    migrated.validate()
    if (!persistence.commit(json.encodeToString(migrated))) throw SecureSessionStorageException()
    return migrated
  }
}
