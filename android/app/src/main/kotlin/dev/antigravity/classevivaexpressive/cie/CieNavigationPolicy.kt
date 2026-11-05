package dev.antigravity.classevivaexpressive.cie

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Only the providers participating in this CIE flow may own its main document. */
internal object CieNavigationPolicy {
  const val StartUrl = "https://web.spaggiari.eu/home/app/default/loginsp.php?act=spidLogin&is_app=true&app_package=eu.spaggiari.classevivastudente"
  const val PortalOrigin = "https://web.spaggiari.eu/"
  private const val CallbackScheme = "eu.spaggiari.classevivastudente"
  private val documentHosts = setOf("web.spaggiari.eu", "eid.istruzione.it", "idserver.servizicie.interno.gov.it")
  val httpsResourceHosts: Set<String> = documentHosts + setOf("fonts.googleapis.com", "fonts.gstatic.com", "www.gstatic.com", "www.google.com")
  private val paPattern = Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

  private fun parse(value: String): URI? = runCatching { URI(value) }.getOrNull()
  private fun safeHttps(uri: URI): Boolean = uri.scheme == "https" && uri.rawUserInfo == null &&
    uri.port in setOf(-1, 443) && uri.host != null &&
    uri.rawAuthority == uri.host + if (uri.port == 443) ":443" else ""

  fun allowsDocument(value: String): Boolean = parse(value)?.let { safeHttps(it) && it.host in documentHosts } == true
  fun isPortalDocument(value: String): Boolean = parse(value)?.let { safeHttps(it) && it.host == "web.spaggiari.eu" } == true
  fun allowsResource(value: String): Boolean {
    if (value.startsWith("data:image/")) return true
    if (value.startsWith("blob:")) return allowsDocument(value.removePrefix("blob:"))
    return parse(value)?.let { safeHttps(it) && it.host in httpsResourceHosts } == true
  }

  fun isCallback(value: String): Boolean = parse(value)?.scheme == CallbackScheme

  fun callbackPa(value: String, sourceDocument: String?, mainFrame: Boolean): String? {
    if (!mainFrame || sourceDocument == null || !isPortalDocument(sourceDocument)) return null
    val uri = parse(value) ?: return null
    if (uri.scheme != CallbackScheme || uri.rawAuthority != "spid-callback" ||
      uri.host != "spid-callback" || uri.port != -1 || uri.rawUserInfo != null ||
      uri.rawFragment != null || !uri.rawPath.isNullOrEmpty()) return null
    val pairs = uri.rawQuery?.split('&') ?: return null
    if (pairs.size != 1) return null
    val pair = pairs.single().split('=', limit = 2)
    if (pair.size != 2) return null
    val name = decode(pair[0]) ?: return null
    val token = decode(pair[1]) ?: return null
    return token.takeIf { name == "spidToken" && validPa(it) }
  }

  fun validPa(value: String): Boolean = value.length in 5..16_384 && paPattern.matches(value)

  fun isCieIdHandoff(value: String): Boolean = parse(value)?.let { uri ->
    safeHttps(uri) && uri.host == "idserver.servizicie.interno.gov.it" &&
      (uri.path.endsWith("/OpenApp") || uri.path in setOf("/idp/login/livello1", "/idp/login/livello2"))
  } == true

  fun allowsCieIdResult(value: String): Boolean = parse(value)?.let {
    safeHttps(it) && it.host == "idserver.servizicie.interno.gov.it" && !isCieIdHandoff(value)
  } == true

  fun providerLabel(value: String): String = when (parse(value)?.host) {
    "web.spaggiari.eu" -> "Spaggiari"
    "eid.istruzione.it" -> "Ministero dell’Istruzione"
    "idserver.servizicie.interno.gov.it" -> "CieID"
    else -> "Accesso con la CIE"
  }

  private fun decode(value: String): String? = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
  }.getOrNull()
}

/** Values crossing ActivityResult contain no identity, URL, token, cookie or server message. */
enum class CieLoginOutcome(val message: String?) {
  Success(null),
  Cancelled(null),
  Interrupted("L’accesso è stato interrotto. Riapri la CIE per ricominciare."),
  TimedOut("Accesso con la CIE non completato in 15 minuti. Riprova."),
  Failed("Accesso con la CIE non riuscito. Verifica la connessione e l’abilitazione della scuola, poi riprova."),
  UnsupportedNavigation("Il portale ha richiesto un collegamento non previsto. Riprova l’accesso con la CIE."),
  Busy("È già in corso un accesso con la CIE."),
}
