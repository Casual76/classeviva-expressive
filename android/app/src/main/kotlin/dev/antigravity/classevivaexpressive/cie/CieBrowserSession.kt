package dev.antigravity.classevivaexpressive.cie

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** WebView storage belongs to the app process, not to one view. All clearing is serialized. */
internal object CieBrowserSession {
  private val mutex = Mutex()
  private var owner: Any? = null
  private var cancelOwner: (() -> Unit)? = null
  private var networkFence: CieConnectProxy? = null

  suspend fun prepare(candidate: Any, view: WebView, onCancel: () -> Unit): Boolean =
    withContext(Dispatchers.Main.immediate) {
      mutex.withLock {
        if (owner != null) return@withLock false
        owner = candidate
        cancelOwner = onCancel
        try {
          clear(view)
          // Socket bind/listen runs off the UI thread; assigning under the held mutex also lets
          // the catch path close it if cancellation wins while returning from Dispatchers.IO.
          withContext(Dispatchers.IO) { networkFence = CieConnectProxy() }
          val fence = checkNotNull(networkFence)
          CieProxyOverride.install(fence.port)
          // No page may load before ProxyController has acknowledged the override.
          view.settings.blockNetworkLoads = false
          true
        } catch (failure: Exception) {
          runCatching { view.settings.blockNetworkLoads = true }
          networkFence?.close()
          networkFence = null
          owner = null
          cancelOwner = null
          throw failure
        }
      }
    }

  suspend fun finish(candidate: Any, view: WebView?) = withContext(NonCancellable + Dispatchers.Main.immediate) {
    mutex.withLock {
      if (owner !== candidate) return@withLock
      runCatching { view?.settings?.blockNetworkLoads = true }
      networkFence?.close()
      networkFence = null
      // Never clearProxyOverride: old requests must hit a closed port, not regain direct egress.
      try { clear(view) } finally {
        owner = null
        cancelOwner = null
      }
    }
  }

  /** Called after cold restore/logout; it cannot erase the provider state of a live attempt. */
  suspend fun clearIdle(context: Context, cancelActive: Boolean = false) = withContext(Dispatchers.Main.immediate) {
    if (cancelActive) cancelOwner?.invoke()
    mutex.withLock {
      if (owner != null) return@withLock
      val view = WebView(context.applicationContext)
      try {
        view.settings.blockNetworkLoads = true
        clear(view)
      } finally { runCatching { view.destroy() } }
    }
  }

  private suspend fun clear(view: WebView?) {
    // A broken renderer/cache must not prevent the remaining stores from being erased.
    var failed = false
    fun attempt(action: () -> Unit) { if (runCatching(action).isFailure) failed = true }
    attempt { view?.stopLoading() }
    attempt { view?.clearHistory() }
    attempt { view?.clearCache(true) }
    attempt { WebStorage.getInstance().deleteAllData() }
    val cookies = runCatching { CookieManager.getInstance() }.getOrNull()
    if (cookies == null) failed = true else {
      if (runCatching {
        withTimeout(5_000L) {
          suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
          }
        }
      }.isFailure) failed = true
      attempt { cookies.flush() }
    }
    if (failed) throw IllegalStateException("Pulizia del browser non riuscita")
  }
}
