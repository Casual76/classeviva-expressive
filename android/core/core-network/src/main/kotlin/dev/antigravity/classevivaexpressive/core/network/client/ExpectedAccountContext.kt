package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.AccountSessionLease
import java.io.IOException
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

private val expectedAccount = ThreadLocal<AccountSessionLease>()

/** Keeps a data operation bound to its original account across repository and client boundaries. */
suspend fun <T> withExpectedAccount(lease: AccountSessionLease, block: suspend () -> T): T {
  val enclosing = expectedAccount.get()
  if (enclosing != null && enclosing != lease) {
    throw ClassevivaNetworkException("La sessione è cambiata. Ripeti l'operazione.")
  }
  return withContext(expectedAccount.asContextElement(lease)) { block() }
}

internal fun expectedAccountLease(): AccountSessionLease? = expectedAccount.get()

/**
 * Retrofit creates its Call on the caller's coroutine thread, then executes it asynchronously.
 * Stamp the request here: coroutine ThreadLocals are not inherited by OkHttp dispatcher threads.
 */
internal class ExpectedAccountCallFactory(private val delegate: Call.Factory) : Call.Factory {
  override fun newCall(request: Request): Call {
    val expected = expectedAccountLease() ?: return delegate.newCall(request)
    val existing = request.tag(RestRequestAccount::class.java)
    if (existing != null && existing.lease != expected) throw IOException("La sessione è cambiata.")
    return delegate.newCall(
      request.newBuilder().tag(RestRequestAccount::class.java, RestRequestAccount(expected)).build(),
    )
  }
}
