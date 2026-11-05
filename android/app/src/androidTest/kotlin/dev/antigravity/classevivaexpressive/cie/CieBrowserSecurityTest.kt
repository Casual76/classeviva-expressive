package dev.antigravity.classevivaexpressive.cie

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CieBrowserSecurityTest {
  @get:Rule val composeRule = createComposeRule()
  private lateinit var fence: CieConnectProxy

  @Before fun installNetworkFence() {
    fence = CieConnectProxy()
    runBlocking { withContext(Dispatchers.Main) { CieProxyOverride.install(fence.port) } }
  }

  @After fun closeNetworkFence() { if (::fence.isInitialized) fence.close() }

  @Test fun aMainFrameFormPostToAnotherOriginIsBlockedBeforeNetwork() {
    ServerSocket(0).use { server ->
      server.soTimeout = 500
      val blocked = CountDownLatch(1)
      val browser = AtomicReference<WebView>()
      composeRule.setContent {
        AndroidView(factory = { context -> WebView(context).apply {
          hardenForCie()
          settings.blockNetworkLoads = false // The override was acknowledged by the fixture setup.
          webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
              interceptCieRequest(request) { blocked.countDown() }
          }
          browser.set(this)
        } }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
      }
      composeRule.runOnIdle {
        browser.get().loadDataWithBaseURL("https://web.spaggiari.eu/qa-fixture", """
          <html><body><form id="probe" method="post" action="http://127.0.0.1:${server.localPort}/forbidden">
          <input name="probe" value="synthetic-marker"></form>
          <script>window.onload = () => document.getElementById('probe').submit();</script></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
      }
      assertTrue("WebView must pass form POST through the request filter", blocked.await(5, TimeUnit.SECONDS))
      try { server.accept().use { fail("The forbidden POST reached the network") } }
      catch (_: SocketTimeoutException) { }
    }
  }

  @Test fun scriptPopupCannotOpenAnotherBrowserWindowOrReachItsNetworkTarget() {
    ServerSocket(0).use { server ->
      server.soTimeout = 500
      val loaded = CountDownLatch(1)
      val evaluated = CountDownLatch(1)
      val result = AtomicReference<String>()
      val browser = AtomicReference<WebView>()
      composeRule.setContent {
        AndroidView(factory = { context -> WebView(context).apply {
          hardenForCie()
          settings.blockNetworkLoads = false
          webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
              interceptCieRequest(request) { }
            override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
          }
          browser.set(this)
          loadDataWithBaseURL("https://web.spaggiari.eu/qa-fixture", "<html><body>Fixture</body></html>", "text/html", "UTF-8", null)
        } }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
      }
      assertTrue(loaded.await(5, TimeUnit.SECONDS))
      composeRule.runOnIdle {
        browser.get().evaluateJavascript("window.open('http://127.0.0.1:${server.localPort}/popup') === null") {
          result.set(it)
          evaluated.countDown()
        }
      }
      assertTrue(evaluated.await(5, TimeUnit.SECONDS))
      assertEquals("true", result.get())
      try { server.accept().use { fail("The popup reached its network target") } }
      catch (_: SocketTimeoutException) { }
    }
  }

  @Test fun privateActivityContractCarriesOnlyAnOpaqueEpochAndSanitizedOutcome() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val contract = CieLoginActivity.Contract()
    val request = contract.createIntent(context, 42L)
    assertEquals(setOf("cie_attempt_epoch"), request.extras!!.keySet())
    assertEquals(42L, request.getLongExtra("cie_attempt_epoch", -1))
    assertFalse(context.packageManager.getActivityInfo(ComponentName(context, CieLoginActivity::class.java), 0).exported)
    val success = Intent().putExtra("cie_outcome", CieLoginOutcome.Success.name).putExtra("cie_cleanup_complete", true)
    assertEquals(CieLoginResult(CieLoginOutcome.Success, true), contract.parseResult(Activity.RESULT_OK, success))
    assertEquals(CieLoginResult(CieLoginOutcome.Failed, true), contract.parseResult(Activity.RESULT_CANCELED, success))
    assertEquals(CieLoginResult(CieLoginOutcome.Cancelled, false), contract.parseResult(Activity.RESULT_CANCELED, null))
    val incomplete = Intent().putExtra("cie_outcome", CieLoginOutcome.Success.name).putExtra("cie_cleanup_complete", false)
    assertEquals(CieLoginResult(CieLoginOutcome.Success, false), contract.parseResult(Activity.RESULT_OK, incomplete))
    val cancelled = Intent().putExtra("cie_outcome", CieLoginOutcome.Cancelled.name).putExtra("cie_cleanup_complete", false)
    assertEquals(CieLoginResult(CieLoginOutcome.Cancelled, false), contract.parseResult(Activity.RESULT_CANCELED, cancelled))
  }
}
