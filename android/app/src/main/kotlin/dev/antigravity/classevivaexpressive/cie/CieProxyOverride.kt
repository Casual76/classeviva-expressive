package dev.antigravity.classevivaexpressive.cie

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** The override intentionally remains installed after the listening socket has been closed. */
internal object CieProxyOverride {
  suspend fun install(port: Int, supported: () -> Boolean = { true }) {
    check(supported()) { "Il filtro di rete WebView non è disponibile" }
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
      throw IllegalStateException("Il filtro di rete WebView non è disponibile")
    }
    val config = ProxyConfig.Builder()
      .addProxyRule("http://127.0.0.1:$port") // All schemes; never add a DIRECT fallback.
      .removeImplicitRules() // Includes loopback/link-local; neither may bypass the fence.
      .build()
    withTimeout(5_000L) {
      suspendCancellableCoroutine { continuation ->
        ProxyController.getInstance().setProxyOverride(config, Executor { it.run() }) {
          if (continuation.isActive) continuation.resume(Unit)
        }
      }
    }
  }
}
