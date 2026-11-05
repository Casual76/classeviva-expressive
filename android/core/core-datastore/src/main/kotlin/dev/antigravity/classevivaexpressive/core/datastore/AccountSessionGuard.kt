package dev.antigravity.classevivaexpressive.core.datastore

import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** An account epoch survives token rotation, but not logout/re-entry or terminal REST rejection. */
data class AccountSessionLease(val epoch: Long, val studentId: String?) {
  override fun toString(): String = "AccountSessionLease(epoch=$epoch)"
}

/**
 * Serializes admission of suspended cache writes and external effects with session invalidation.
 * Blocks must not call storage mutation methods or launch unstructured work: this gate is not reentrant.
 * Network I/O belongs outside the gate; only the final checked commit/effect belongs inside it.
 */
class AccountSessionGuard {
  private data class State(
    val epoch: Long = 0,
    val studentId: String? = null,
    val ready: Boolean = false,
    val authenticationEpoch: Long = 0,
  )
  private val mutex = Mutex()
  @Volatile private var state = State()

  fun capture(): AccountSessionLease = state.let { AccountSessionLease(it.epoch, it.studentId) }

  /** A provider login survives expiry of the old credentials, but never logout or a new identity. */
  fun authenticationEpoch(): Long = state.authenticationEpoch

  fun isCurrent(lease: AccountSessionLease, requireReady: Boolean = true): Boolean {
    val current = state
    return current.epoch == lease.epoch && current.studentId == lease.studentId &&
      (!requireReady || current.ready)
  }

  suspend fun <T> withCurrentAccount(
    lease: AccountSessionLease,
    requireReady: Boolean = true,
    block: suspend () -> T,
  ): T? = mutex.withLock {
    if (!isCurrent(lease, requireReady)) null else block()
  }

  internal fun <T> mutateBlocking(block: () -> T): T = runBlocking { mutex.withLock { block() } }

  internal suspend fun <T> mutate(block: suspend () -> T): T = mutex.withLock { block() }

  /** Called only while the mutation gate is held, after a successful durable write. */
  internal fun accept(snapshot: StoredAuthSnapshot?, forceInvalidation: Boolean = false) {
    val previous = state
    val studentId = snapshot?.session?.studentId
    val ready = snapshot?.authenticationState?.rest == RestAuthenticationState.READY
    val identityChanged = forceInvalidation || previous.studentId != studentId
    val changed = identityChanged || previous.ready != ready
    state = State(
      epoch = if (changed) previous.epoch + 1 else previous.epoch,
      studentId = studentId,
      ready = ready,
      authenticationEpoch = if (identityChanged) previous.authenticationEpoch + 1 else previous.authenticationEpoch,
    )
  }
}
