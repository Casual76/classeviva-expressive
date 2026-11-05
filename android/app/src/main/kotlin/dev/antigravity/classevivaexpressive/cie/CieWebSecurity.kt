package dev.antigravity.classevivaexpressive.cie

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.hardenForCie() {
  isSaveEnabled = false
  isSaveFromParentEnabled = false
  importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
  settings.apply {
    blockNetworkLoads = true // Released only after the network fence is installed and acknowledged.
    javaScriptEnabled = true // Required by the provider; no JavaScript/native bridge is installed.
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    @Suppress("DEPRECATION")
    allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    cacheMode = WebSettings.LOAD_NO_CACHE
    setSupportMultipleWindows(false)
    javaScriptCanOpenWindowsAutomatically = false
    @Suppress("DEPRECATION")
    saveFormData = false
    userAgentString = userAgentString.replace("; wv", "")
  }
  CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
  webChromeClient = CieWebChromeClient()
}

internal class CieWebChromeClient : WebChromeClient() {
  override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
  override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
  override fun onPermissionRequest(request: PermissionRequest) = request.deny()
  override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
  override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
    filePathCallback?.onReceiveValue(null)
    return true
  }
}

/** Initial request/frame policy. Redirect egress is independently fenced by the CONNECT proxy. */
internal fun interceptCieRequest(request: WebResourceRequest, onForbiddenMainFrame: (String) -> Unit): WebResourceResponse? {
  val url = request.url.toString()
  val allowed = if (request.isForMainFrame) CieNavigationPolicy.allowsDocument(url) else CieNavigationPolicy.allowsResource(url)
  if (allowed) return null
  if (request.isForMainFrame) onForbiddenMainFrame(url)
  return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
