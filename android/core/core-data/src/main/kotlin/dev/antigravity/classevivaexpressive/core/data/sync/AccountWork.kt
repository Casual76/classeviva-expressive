package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.datastore.AccountSessionLease
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import dev.antigravity.classevivaexpressive.core.network.client.ClassevivaNetworkException
import dev.antigravity.classevivaexpressive.core.network.client.withExpectedAccount
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Immutable identity for one operation, independent of transparent REST token rotation. */
internal data class AccountWork(
  val session: UserSession,
  val lease: AccountSessionLease,
  val requireReady: Boolean = true,
)

internal fun SessionStore.captureAccountWork(allowOffline: Boolean = false, expectedAuthenticationEpoch: Long? = null): AccountWork {
  val captured = captureAuthSnapshot()
  if (expectedAuthenticationEpoch != null && captured.authenticationEpoch != expectedAuthenticationEpoch) {
    throw ClassevivaNetworkException("La sessione è cambiata; ripeti l'operazione.")
  }
  val session = captured.snapshot?.session ?: throw ClassevivaNetworkException("Sessione assente.")
  val work = AccountWork(session, captured.lease, requireReady = !allowOffline)
  if (!accountGuard.isCurrent(work.lease, work.requireReady)) {
    throw ClassevivaNetworkException("Accedi di nuovo per aggiornare i dati. I dati salvati restano disponibili.")
  }
  return work
}

/** Network awaits stay outside the gate; only the admitted final writes/effects belong here. */
internal suspend fun <T> SessionStore.commitAccountWork(work: AccountWork, block: suspend () -> T): T = withExpectedAccount(work.lease) {
  var admitted = false
  val result = accountGuard.withCurrentAccount(work.lease, work.requireReady) {
    currentCoroutineContext().ensureActive()
    admitted = true
    block()
  }
  if (!admitted) throw ClassevivaNetworkException("La sessione è cambiata; l'operazione precedente è stata annullata.")
  @Suppress("UNCHECKED_CAST")
  result as T
}

internal suspend fun <T> withAccountWork(work: AccountWork, block: suspend () -> T): T =
  withExpectedAccount(work.lease) { block() }
