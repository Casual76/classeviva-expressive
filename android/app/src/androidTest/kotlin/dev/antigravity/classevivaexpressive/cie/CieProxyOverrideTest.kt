package dev.antigravity.classevivaexpressive.cie

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Local network fixtures only: no provider connection and no certificate/TLS override. */
class CieProxyOverrideTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun httpsDestinationCannotBypassConnectPolicyOrFallBackToDirectAfterStop() {
    ServerSocket(0).use { forbidden ->
      forbidden.soTimeout = 500
      val browser = AtomicReference<WebView>()
      val error = AtomicReference(CountDownLatch(1))
      CieConnectProxy().use { fence ->
        install(fence.port)
        composeRule.setContent {
          AndroidView(factory = { context -> WebView(context).apply {
            hardenForCie()
            settings.blockNetworkLoads = false
            // Intentionally no URL interception: this test isolates the actual proxy boundary.
            webViewClient = object : WebViewClient() {
              override fun onReceivedError(view: WebView, request: WebResourceRequest, detail: WebResourceError) {
                if (request.isForMainFrame) error.get().countDown()
              }
            }
            browser.set(this)
          } }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
        }
        composeRule.runOnIdle { browser.get().loadUrl("https://localhost:${forbidden.localPort}/blocked") }
        assertTrue("The production CONNECT fence must refuse the origin", error.get().await(5, TimeUnit.SECONDS))
        assertNoConnection(forbidden)

        fence.close() // The override remains installed; there is no DIRECT fallback.
        error.set(CountDownLatch(1))
        composeRule.runOnIdle { browser.get().loadUrl("https://localhost:${forbidden.localPort}/after-stop") }
        assertTrue("A stopped proxy must remain closed to the live WebView", error.get().await(5, TimeUnit.SECONDS))
        assertNoConnection(forbidden)
      }
    }
  }

  @Test fun missingProxyFeatureKeepsTheBrowserBlockedBeforeAnyLoad() {
    ServerSocket(0).use { forbidden ->
      forbidden.soTimeout = 500
      val blocked = CountDownLatch(1)
      val browser = AtomicReference<WebView>()
      composeRule.setContent {
        AndroidView(factory = { context -> WebView(context).apply {
          hardenForCie()
          webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, detail: WebResourceError) {
              if (request.isForMainFrame) blocked.countDown()
            }
          }
          browser.set(this)
        } }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
      }
      // Deterministically force the unsupported branch even on a recent emulator provider.
      val outcome = runCatching {
        runBlocking { withContext(Dispatchers.Main) { CieProxyOverride.install(1, supported = { false }) } }
      }
      assertTrue(outcome.exceptionOrNull() is IllegalStateException)
      composeRule.runOnIdle {
        assertTrue(browser.get().settings.blockNetworkLoads)
        browser.get().loadUrl("https://localhost:${forbidden.localPort}/unsupported")
      }
      assertTrue(blocked.await(5, TimeUnit.SECONDS))
      assertNoConnection(forbidden)
    }
  }

  @Test fun subresourceRedirectUsesTheOverrideWithoutAnImplicitLoopbackBypass() {
    redirectFixture(post = false)
  }

  @Test fun post307KeepsItsMethodButCannotSendItsBodyToTheRedirectDestination() {
    redirectFixture(post = true)
  }

  private fun redirectFixture(post: Boolean) {
    ServerSocket(0).use { forbidden ->
      forbidden.soTimeout = 500
      HttpRedirectFixture(forbidden.localPort).use { fixture ->
        install(fixture.port)
        composeRule.setContent {
          AndroidView(factory = { context -> WebView(context).apply {
            hardenForCie()
            settings.blockNetworkLoads = false
            // HTTP is used ONLY by this fixture to demonstrate WebView redirect routing without
            // a fake CA or bypassing TLS. Production CieConnectProxy never forwards HTTP.
            webViewClient = WebViewClient()
            val html = if (post) """
              <html><body><form id="fixture" method="post" action="http://localhost/redirect">
              <input name="probe" value="synthetic-marker"></form>
              <script>window.onload = () => document.getElementById('fixture').submit();</script></body></html>
            """ else "<html><body><img src=\"http://localhost/redirect\"></body></html>"
            loadDataWithBaseURL("http://localhost/fixture", html, "text/html", "UTF-8", null)
          } }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
        }
        assertTrue("The initial request must reach the override", fixture.initial.await(5, TimeUnit.SECONDS))
        assertTrue("Redirect destinations must also reach the override, including localhost", fixture.redirected.await(5, TimeUnit.SECONDS))
        assertEquals(if (post) "POST" else "GET", fixture.redirectMethod.get())
        if (post) assertTrue(fixture.initialBody.get().contains("probe=synthetic-marker"))
        assertNoConnection(forbidden)
      }
    }
  }

  private fun install(port: Int) = runBlocking {
    withContext(Dispatchers.Main) { CieProxyOverride.install(port) }
  }

  private fun assertNoConnection(server: ServerSocket) {
    try { server.accept().use { fail("The forbidden destination received a socket") } }
    catch (_: SocketTimeoutException) { }
  }

  /** Test-only HTTP origin fixture; it sends redirects/denials and never creates upstream sockets. */
  private class HttpRedirectFixture(private val forbiddenPort: Int) : Closeable {
    private val server = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)) }
    val port = server.localPort
    val initial = CountDownLatch(1)
    val redirected = CountDownLatch(1)
    val redirectMethod = AtomicReference<String>()
    val initialBody = AtomicReference("")
    private val stopped = AtomicBoolean()
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()
    private val worker = thread(isDaemon = true, name = "cie-redirect-fixture") {
      while (!stopped.get()) {
        val socket = try { server.accept() } catch (_: Exception) { break }
        sockets += socket
        socket.use { connection ->
          try {
            connection.soTimeout = 2_000
            val header = readHeader(connection.getInputStream())
            val line = header.substringBefore("\r\n").split(' ')
            if (line.size < 2) return@use
            if (line[1] == "http://localhost/redirect") {
              val size = header.lineSequence().firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
              check(size in 0..1_024)
              val body = ByteArray(size)
              var offset = 0
              while (offset < size) {
                val count = connection.getInputStream().read(body, offset, size - offset)
                check(count > 0)
                offset += count
              }
              initialBody.set(body.toString(US_ASCII))
              connection.getOutputStream().write(("HTTP/1.1 ${if (line[0] == "POST") "307 Temporary Redirect" else "302 Found"}\r\n" +
                "Location: http://localhost:$forbiddenPort/forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(US_ASCII))
              initial.countDown()
            } else if (line[1] == "http://localhost:$forbiddenPort/forbidden") {
              redirectMethod.set(line[0])
              connection.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(US_ASCII))
              redirected.countDown()
            } else connection.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(US_ASCII))
          } catch (_: Exception) { }
        }
        sockets -= socket
      }
    }

    private fun readHeader(input: InputStream): String {
      val result = StringBuilder()
      while (!result.endsWith("\r\n\r\n") && result.length < 8_192) {
        val next = input.read()
        check(next >= 0)
        result.append(next.toChar())
      }
      return result.toString()
    }

    override fun close() {
      stopped.set(true)
      runCatching { server.close() }
      sockets.forEach { runCatching { it.close() } }
      worker.interrupt()
    }
  }
}
