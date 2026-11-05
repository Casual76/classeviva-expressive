package dev.antigravity.classevivaexpressive.cie

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CieBrowserCleanupTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun failedCacheCleanupStillAttemptsCookieErasureAndClosesTheAttempt() {
    val browser = AtomicReference<FailingCacheWebView>()
    val owner = Any()
    composeRule.setContent {
      AndroidView(factory = { context -> FailingCacheWebView(context).apply { hardenForCie(); browser.set(this) } },
        modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
    }
    runBlocking {
      withContext(Dispatchers.Main) {
        assertTrue(CieBrowserSession.prepare(owner, browser.get(), {}))
      }
    }
    val cookieSet = CountDownLatch(1)
    composeRule.runOnIdle {
      CookieManager.getInstance().setCookie(CieNavigationPolicy.PortalOrigin, "cie-qa-cleanup=synthetic; Secure; Path=/") { cookieSet.countDown() }
    }
    assertTrue(cookieSet.await(2, TimeUnit.SECONDS))
    composeRule.runOnIdle {
      assertTrue(CookieManager.getInstance().getCookie(CieNavigationPolicy.PortalOrigin).orEmpty().contains("cie-qa-cleanup"))
      browser.get().failCache = true
    }
    val cleanup = runCatching {
      runBlocking { withContext(Dispatchers.Main) { CieBrowserSession.finish(owner, browser.get()) } }
    }
    assertTrue("The cleanup failure must be represented to the Activity", cleanup.isFailure)
    composeRule.runOnIdle {
      assertTrue(browser.get().settings.blockNetworkLoads)
      assertFalse(CookieManager.getInstance().getCookie(CieNavigationPolicy.PortalOrigin).orEmpty().contains("cie-qa-cleanup"))
    }
    // The old owner is released, but a new attempt cannot bypass the same failing cleanup.
    val retry = runCatching {
      runBlocking { withContext(Dispatchers.Main) { CieBrowserSession.prepare(Any(), browser.get(), {}) } }
    }
    assertTrue(retry.isFailure)
    composeRule.runOnIdle { assertTrue(browser.get().settings.blockNetworkLoads) }
  }

  private class FailingCacheWebView(context: Context) : WebView(context) {
    var failCache = false
    override fun clearCache(includeDiskFiles: Boolean) {
      if (failCache) throw IllegalStateException("Synthetic cache failure")
      super.clearCache(includeDiskFiles)
    }
  }
}
