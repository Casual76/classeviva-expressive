package dev.antigravity.classevivaexpressive.cie

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A private, single-attempt browser. PA and cookies are exchanged here, never in ActivityResult
 * extras (the Android result registry can persist pending extras across process recreation).
 */
@AndroidEntryPoint
class CieLoginActivity : ComponentActivity() {
  @Inject lateinit var authRepository: AuthRepository
  @Inject lateinit var settingsRepository: SettingsRepository

  class Contract : ActivityResultContract<Long, CieLoginResult>() {
    override fun createIntent(context: Context, input: Long): Intent =
      Intent(context, CieLoginActivity::class.java).putExtra(AttemptEpoch, input)

    override fun parseResult(resultCode: Int, intent: Intent?): CieLoginResult {
      val outcome = runCatching { intent?.getStringExtra(Outcome) }.getOrNull()?.let { name -> CieLoginOutcome.entries.firstOrNull { it.name == name } }
        ?: CieLoginOutcome.Cancelled
      val nominal = if (outcome == CieLoginOutcome.Success && resultCode != Activity.RESULT_OK) CieLoginOutcome.Failed else outcome
      val cleaned = runCatching { intent?.getBooleanExtra(CleanupComplete, false) == true }.getOrDefault(false)
      return CieLoginResult(nominal, cleaned)
    }
  }

  private val terminal = AtomicBoolean()
  private val browserOwner = Any()
  private val handler = Handler(Looper.getMainLooper())
  private var webView: WebView? = null
  private var exchangeJob: Job? = null
  private var attemptEpoch = -1L
  private var deadline = 0L
  private var pageSequence = 0L
  @Volatile private var lastDocument: String? = null
  @Volatile private var paConsumed = false
  private var loading by mutableStateOf(true)
  private var exchanging by mutableStateOf(false)
  private var provider by mutableStateOf("Accesso con la CIE")
  private var appMissing by mutableStateOf(false)
  private var pendingHandoff: String? = null // RAM only; never placed in a Bundle.
  private var ready by mutableStateOf(false)
  private var browserCleanupUncertain = false

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode != CieIdRequest) {
      super.onActivityResult(requestCode, resultCode, data)
      return
    }
    // Deliberately avoid a result registry: a pending URL must not be copied into saved state.
    if (terminal.get() || paConsumed) return
    if (attemptExpired()) { finishWith(CieLoginOutcome.TimedOut); return }
    val returned = data?.getStringExtra("URL")
    if (resultCode == Activity.RESULT_CANCELED) {
      finishWith(CieLoginOutcome.Cancelled)
    } else if (resultCode == Activity.RESULT_OK && returned != null && CieNavigationPolicy.allowsCieIdResult(returned)) {
      pendingHandoff = null
      webView?.loadUrl(returned)
    } else finishWith(CieLoginOutcome.Failed)
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    attemptEpoch = intent.getLongExtra(AttemptEpoch, -1L)
    onBackPressedDispatcher.addCallback(this) { finishWith(CieLoginOutcome.Cancelled) }
    // A WebView state/history bundle may contain callback URLs and PA: never restore it.
    if (savedInstanceState != null || attemptEpoch < 0) {
      lifecycleScope.launch {
        browserCleanupUncertain = runCatching { CieBrowserSession.clearIdle(this@CieLoginActivity) }.isFailure
        finishWith(CieLoginOutcome.Interrupted)
      }
      return
    }
    if (authRepository.authenticationEpoch() != attemptEpoch) {
      finishWith(CieLoginOutcome.Interrupted)
      return
    }

    deadline = SystemClock.elapsedRealtime() + TimeLimitMillis
    val browser = try {
      WebView.setWebContentsDebuggingEnabled(false)
      WebView(this).also { webView = it; it.hardenForCie() }
    } catch (_: Exception) {
      browserCleanupUncertain = true
      finishWith(CieLoginOutcome.Failed)
      return
    }
    browser.setDownloadListener { _, _, _, _, _ -> finishWith(CieLoginOutcome.UnsupportedNavigation) }
    browser.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        navigate(request.url.toString(), request.isForMainFrame)

      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (terminal.get() || paConsumed) return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
        val source = lastDocument
        return interceptCieRequest(request) { url ->
          runOnUiThread {
            if (CieNavigationPolicy.isCallback(url)) acceptCallback(url, source, true)
            else finishWith(CieLoginOutcome.UnsupportedNavigation)
          }
        }
      }

      override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (terminal.get()) return
        if (!CieNavigationPolicy.allowsDocument(url)) {
          view.stopLoading()
          finishWith(CieLoginOutcome.UnsupportedNavigation)
          return
        }
        lastDocument = url
        pageSequence++
        provider = CieNavigationPolicy.providerLabel(url)
        loading = true
      }

      override fun onPageFinished(view: WebView, url: String) {
        if (terminal.get() || paConsumed) return
        loading = false
        if (!CieNavigationPolicy.isPortalDocument(url) || view.url != url || lastDocument != url) return
        val sequence = pageSequence
        view.evaluateJavascript(CompletionScript) { raw ->
          if (terminal.get() || paConsumed || sequence != pageSequence || view.url != url || lastDocument != url || raw.length > 65_536) return@evaluateJavascript
          val json = runCatching { (JSONTokener(raw).nextValue() as? String)?.let(::JSONObject) }.getOrNull()
            ?: return@evaluateJavascript
          val callback = json.optString("callback").takeUnless { it.isBlank() || it == "null" }
          val pa = json.optString("pa").takeUnless { it.isBlank() || it == "null" }
          val callbackPa = callback?.let { CieNavigationPolicy.callbackPa(it, url, true) }
          when {
            callbackPa != null -> exchange(callbackPa)
            pa != null && CieNavigationPolicy.validPa(pa) -> exchange(pa)
            json.optBoolean("error") -> finishWith(CieLoginOutcome.Failed)
          }
        }
      }

      override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
        handler.cancel()
        finishWith(CieLoginOutcome.Failed)
      }

      override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && !terminal.get() && !paConsumed) finishWith(CieLoginOutcome.Failed)
      }

      override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        finishWith(CieLoginOutcome.Failed)
        return true
      }
    }

    setContent {
      val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
      ClassevivaExpressiveTheme(settings = settings) {
        FluidScreenSurface {
          Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text("Accesso con la CIE", style = MaterialTheme.typography.titleMedium)
                Text(provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              FluidButton("Chiudi", { finishWith(CieLoginOutcome.Cancelled) }, style = FluidButtonStyle.Plain)
            }
            if (appMissing) {
              Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Per continuare serve l’app CieID ufficiale.", style = MaterialTheme.typography.bodyLarge)
                FluidButton("Installa CieID", ::openCieIdStore, fillWidth = true)
                FluidButton("Riprova CieID", { pendingHandoff?.let(::openCieId) }, style = FluidButtonStyle.Tinted, fillWidth = true)
              }
            } else {
              Box(Modifier.weight(1f).fillMaxWidth()) {
                if (ready) AndroidView(factory = { browser }, modifier = Modifier.fillMaxSize().testTag("cie_browser"))
                if (!ready || loading || exchanging) {
                  Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FluidLoading()
                    if (exchanging) Text("Sto completando l’accesso…", style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }
            }
          }
        }
      }
    }
    handler.postDelayed({ finishWith(CieLoginOutcome.TimedOut) }, TimeLimitMillis)
    lifecycleScope.launch {
      val prepared = try {
        CieBrowserSession.prepare(browserOwner, browser) { finishWith(CieLoginOutcome.Interrupted) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        browserCleanupUncertain = true
        finishWith(CieLoginOutcome.Failed)
        return@launch
      }
      if (terminal.get()) return@launch
      if (!prepared) finishWith(CieLoginOutcome.Busy) else {
        ready = true
        browser.loadUrl(CieNavigationPolicy.StartUrl)
      }
    }
  }

  private fun navigate(url: String, mainFrame: Boolean): Boolean {
    if (terminal.get() || paConsumed) return true
    if (CieNavigationPolicy.isCallback(url)) {
      acceptCallback(url, lastDocument, mainFrame)
      return true
    }
    if (!mainFrame) return !CieNavigationPolicy.allowsResource(url)
    if (CieNavigationPolicy.isCieIdHandoff(url) && lastDocument?.let(CieNavigationPolicy::allowsDocument) == true) {
      openCieId(url)
      return true
    }
    if (url.startsWith("intent:")) {
      val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
      val target = parsed?.data?.toString()
      if ((parsed?.`package` == null || parsed.`package` == CieIdPackage) && target != null &&
        CieNavigationPolicy.isCieIdHandoff(target) && lastDocument?.let(CieNavigationPolicy::allowsDocument) == true) openCieId(target)
      else finishWith(CieLoginOutcome.UnsupportedNavigation)
      return true
    }
    if (!CieNavigationPolicy.allowsDocument(url)) {
      finishWith(CieLoginOutcome.UnsupportedNavigation)
      return true
    }
    return false
  }

  private fun acceptCallback(url: String, source: String?, mainFrame: Boolean) {
    if (terminal.get() || paConsumed) return
    val pa = CieNavigationPolicy.callbackPa(url, source, mainFrame)
    if (pa == null || source != lastDocument) finishWith(CieLoginOutcome.UnsupportedNavigation) else exchange(pa)
  }

  private fun exchange(pa: String) {
    if (terminal.get() || paConsumed) return
    if (attemptExpired()) { finishWith(CieLoginOutcome.TimedOut); return }
    paConsumed = true
    exchanging = true
    webView?.stopLoading()
    val cookies = try { CookieManager.getInstance().getCookie(CieNavigationPolicy.PortalOrigin).orEmpty() }
      catch (_: Exception) { finishWith(CieLoginOutcome.Failed); return }
    exchangeJob = lifecycleScope.launch {
      try {
        val result = authRepository.loginWithCie(pa, cookies, expectedEpoch = attemptEpoch)
        finishWith(if (result.isSuccess) CieLoginOutcome.Success else CieLoginOutcome.Failed)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        finishWith(CieLoginOutcome.Failed)
      }
    }
  }

  private fun openCieId(url: String) {
    if (terminal.get() || paConsumed || !CieNavigationPolicy.isCieIdHandoff(url)) return
    pendingHandoff = url
    appMissing = false
    try {
      // Official redirect contract: no components, flags or fallback URLs supplied by web content.
      @Suppress("DEPRECATION")
      startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClassName(CieIdPackage, "it.ipzs.cieid.BaseActivity"), CieIdRequest)
    } catch (_: ActivityNotFoundException) { appMissing = true }
      catch (_: Exception) { finishWith(CieLoginOutcome.Failed) }
  }

  private fun openCieIdStore() {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$CieIdPackage"))) }
  }

  private fun finishWith(outcome: CieLoginOutcome) {
    if (!terminal.compareAndSet(false, true)) return
    pendingHandoff = null
    lastDocument = null
    pageSequence++
    handler.removeCallbacksAndMessages(null)
    exchangeJob?.cancel()
    lifecycleScope.launch(NonCancellable) {
      val cleaned = runCatching { CieBrowserSession.finish(browserOwner, webView) }.isSuccess
      val destroyed = disposeBrowser()
      setResult(if (outcome == CieLoginOutcome.Success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
        Intent().putExtra(Outcome, outcome.name).putExtra(CleanupComplete, cleaned && destroyed && !browserCleanupUncertain))
      finish()
    }
  }

  private fun attemptExpired(): Boolean = deadline > 0 && SystemClock.elapsedRealtime() >= deadline

  override fun onResume() {
    super.onResume()
    if (!terminal.get() && attemptExpired()) finishWith(CieLoginOutcome.TimedOut)
  }

  private fun disposeBrowser(): Boolean {
    val browser = webView ?: return true
    webView = null
    val detached = runCatching { (browser.parent as? ViewGroup)?.removeView(browser) }.isSuccess
    val destroyed = runCatching { browser.destroy() }.isSuccess
    return detached && destroyed
  }

  override fun onSaveInstanceState(outState: Bundle) {
    // Framework state only: no WebView.saveState, PA, cookies or provider URL.
    super.onSaveInstanceState(outState)
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    // Rotation/uiMode handled by configChanges keep this instance. Any actual destruction must
    // release its owner, including an unhandled configuration change whose replacement aborts.
    terminal.set(true)
    exchangeJob?.cancel()
    lifecycleScope.launch(NonCancellable) {
      runCatching { CieBrowserSession.finish(browserOwner, webView) }
      disposeBrowser()
    }
    super.onDestroy()
  }

  companion object {
    private const val AttemptEpoch = "cie_attempt_epoch"
    private const val Outcome = "cie_outcome"
    private const val CleanupComplete = "cie_cleanup_complete"
    private const val CieIdRequest = 4107
    private const val CieIdPackage = "it.ipzs.cieid"
    private const val TimeLimitMillis = 15 * 60_000L
    private val CompletionScript = """
      (() => {
        if (location.origin !== 'https://web.spaggiari.eu') return null;
        const token = document.getElementById('spid-patoken');
        const error = document.getElementById('mspidauth-error-code');
        const link = Array.from(document.querySelectorAll('a[href]')).find(a => a.href.startsWith('eu.spaggiari.classevivastudente://spid-callback'));
        return JSON.stringify({pa: token ? token.value : null, callback: link ? link.href : null, error: !!(error && error.value && error.value.trim())});
      })()
    """.trimIndent()
  }
}
