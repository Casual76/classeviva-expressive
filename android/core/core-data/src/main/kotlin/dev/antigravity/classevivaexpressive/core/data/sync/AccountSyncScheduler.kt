package dev.antigravity.classevivaexpressive.core.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** UI configuration can arrive late; only the account that produced it can alter periodic work. */
@Singleton
class AccountSyncScheduler @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val sessionStore: SessionStore,
) {
  suspend fun schedule(expectedIdentityEpoch: Long, studentId: String) = withContext(Dispatchers.IO) {
    val captured = sessionStore.captureAuthSnapshot()
    if (captured.authenticationEpoch != expectedIdentityEpoch || captured.snapshot?.session?.studentId != studentId) return@withContext
    sessionStore.accountGuard.withCurrentAccount(captured.lease) {
      currentCoroutineContext().ensureActive()
      if (sessionStore.accountGuard.authenticationEpoch() == expectedIdentityEpoch) SyncWorkScheduler.schedule(context)
    }
    Unit
  }

  suspend fun cancel(expectedIdentityEpoch: Long) = withContext(Dispatchers.IO) {
    val captured = sessionStore.captureAuthSnapshot()
    if (captured.authenticationEpoch != expectedIdentityEpoch) return@withContext
    sessionStore.accountGuard.withCurrentAccount(captured.lease, requireReady = false) {
      currentCoroutineContext().ensureActive()
      if (sessionStore.accountGuard.authenticationEpoch() == expectedIdentityEpoch) SyncWorkScheduler.cancel(context)
    }
    Unit
  }
}
