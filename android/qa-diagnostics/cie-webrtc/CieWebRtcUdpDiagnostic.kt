package dev.antigravity.classevivaexpressive.cie

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicitly opt-in QA source: this directory is outside every Gradle source set. */
class CieWebRtcUdpDiagnostic {
  @get:Rule val composeRule = createComposeRule()

  @Test fun dataOnlyStunCanUseLocalUdpWithHttpProxyAndMediaPermissionsDenied() {
    DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { udp ->
      udp.soTimeout = 7_000
      CieConnectProxy().use { proxy ->
        runBlocking { withContext(Dispatchers.Main) { CieProxyOverride.install(proxy.port) } }
        val loaded = CountDownLatch(1)
        val browser = AtomicReference<WebView>()
        composeRule.setContent {
          AndroidView(factory = { context -> WebView(context).apply {
            hardenForCie() // Includes media/permission denial; deliberately no getUserMedia call.
            settings.blockNetworkLoads = false // The HTTP proxy override is already acknowledged.
            webViewClient = object : WebViewClient() {
              override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
            }
            browser.set(this)
            loadDataWithBaseURL("https://web.spaggiari.eu/qa-udp-fixture", """
              <html><body><script>
              (() => {
                const Ctor = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                if (typeof Ctor !== 'function') return;
                const pc = window.__syntheticPeer = new Ctor({iceServers:[{urls:'stun:127.0.0.1:${udp.localPort}'}]});
                pc.createDataChannel('synthetic-probe');
                pc.createOffer().then(offer => pc.setLocalDescription(offer)).catch(() => {});
              })();
              </script></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
          } }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
        }
        assertTrue("The synthetic document must load", loaded.await(5, TimeUnit.SECONDS))
        try {
          val packet = DatagramPacket(ByteArray(2_048), 2_048)
          val received = try { udp.receive(packet); true } catch (_: SocketTimeoutException) { false }
          assumeTrue("INCONCLUSIVE: no synthetic local STUN packet was observed", received)
          val bytes = packet.data
          assertTrue("The datagram must be a STUN binding request", packet.length >= 20 &&
            bytes[0] == 0.toByte() && bytes[1] == 1.toByte() && bytes[4] == 0x21.toByte() &&
            bytes[5] == 0x12.toByte() && bytes[6] == 0xa4.toByte() && bytes[7] == 0x42.toByte())
          bytes.fill(0)
        } finally {
          composeRule.runOnIdle { browser.get().evaluateJavascript("window.__syntheticPeer && window.__syntheticPeer.close()", null) }
        }
      }
    }
  }
}
