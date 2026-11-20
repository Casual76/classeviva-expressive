package dev.antigravity.classevivaexpressive.cie

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Only the providers participating in this CIE flow may own its main document. */
internal object CieNavigationPolicy {
  const val StartUrl = "https://web.spaggiari.eu/home/app/default/loginsp.php?act=spidLogin&is_app=true&app_package=eu.spaggiari.classevivastudente"
  const val PortalOrigin = "https://web.spaggiari.eu/"
  const val CallbackScheme = "eu.spaggiari.classevivastudente"
  private val documentHosts = setOf("web.spaggiari.eu", "eid.istruzione.it", "idserver.servizicie.interno.gov.it")
  /**
   * The federation spans more hosts than the three entry points: the Ministry's ID Gateway and
   * Spaggiari's SAML endpoints may answer from sibling hosts of the same registrable domain (the
   * assertion consumer after «Prosegui» is one of them). Only these organisations' own domains.
   */
  private val federationDomains = setOf("spaggiari.eu", "istruzione.it", "servizicie.interno.gov.it")
  private val portalDomain = "spaggiari.eu"
  val httpsResourceHosts: Set<String> = documentHosts + setOf("fonts.googleapis.com", "fonts.gstatic.com", "www.gstatic.com", "www.google.com")
  private val hostPattern = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")
  private val paPattern = Regex("[A-Za-z0-9._~+/=-]+")

  private fun parse(value: String): URI? = runCatching { URI(value) }.getOrNull()
  private fun safeHttps(uri: URI): Boolean = uri.scheme == "https" && uri.rawUserInfo == null &&
    uri.port in setOf(-1, 443) && uri.host != null &&
    uri.rawAuthority == uri.host + if (uri.port == 443) ":443" else ""

  private fun inDomain(host: String, domain: String) = host == domain || host.endsWith(".$domain")
  private fun federationHost(host: String?): Boolean = host != null && hostPattern.matches(host) &&
    (host in documentHosts || federationDomains.any { inDomain(host, it) })

  /** The CONNECT fence and the document policy agree on the same set of hosts. */
  fun allowsNetworkHost(host: String): Boolean = host in httpsResourceHosts || federationHost(host)

  fun allowsDocument(value: String): Boolean = parse(value)?.let { safeHttps(it) && federationHost(it.host) } == true
  fun isPortalDocument(value: String): Boolean = parse(value)?.let { uri ->
    safeHttps(uri) && federationHost(uri.host) && inDomain(uri.host, portalDomain)
  } == true
  fun allowsResource(value: String): Boolean {
    if (value.startsWith("data:image/")) return true
    if (value.startsWith("blob:")) return allowsDocument(value.removePrefix("blob:"))
    return parse(value)?.let { safeHttps(it) && allowsNetworkHost(it.host) } == true
  }

  fun isCallback(value: String): Boolean = parse(value)?.scheme == CallbackScheme

  fun callbackPa(value: String, sourceDocument: String?, mainFrame: Boolean): String? =
    callbackRejection(value, sourceDocument, mainFrame).let { (pa, rejection) -> pa.takeIf { rejection == null } }

  /**
   * Returns the PA, or a short reason without any token material. The reason may be shown to the
   * user so that a real-device failure can be reported: it carries at most a host name.
   */
  fun callbackRejection(value: String, sourceDocument: String?, mainFrame: Boolean): Pair<String?, String?> {
    if (!mainFrame) return null to "callback-frame"
    if (sourceDocument == null || !isPortalDocument(sourceDocument)) return null to "callback-fonte-${hostOf(sourceDocument)}"
    val uri = parse(value) ?: return null to "callback-forma"
    if (uri.scheme != CallbackScheme || uri.rawAuthority != "spid-callback" ||
      uri.host != "spid-callback" || uri.port != -1 || uri.rawUserInfo != null ||
      uri.rawFragment != null || !uri.rawPath.isNullOrEmpty()) return null to "callback-forma"
    val pairs = uri.rawQuery?.split('&').orEmpty().map { it.split('=', limit = 2) }
    val tokens = pairs.filter { it.size == 2 && decode(it[0]) == "spidToken" }
    if (tokens.size != 1) return null to "callback-parametri-${pairs.size}"
    val token = decode(tokens.single()[1]) ?: return null to "callback-token"
    return if (validPa(token)) token to null else null to "callback-token-${token.length}"
  }

  fun validPa(value: String): Boolean = value.length in 5..16_384 && paPattern.matches(value)

  fun isCieIdHandoff(value: String): Boolean = parse(value)?.let { uri ->
    safeHttps(uri) && uri.host == "idserver.servizicie.interno.gov.it" &&
      (uri.path.endsWith("/OpenApp") || uri.path in setOf("/idp/login/livello1", "/idp/login/livello2"))
  } == true

  fun allowsCieIdResult(value: String): Boolean = parse(value)?.let {
    safeHttps(it) && it.host == "idserver.servizicie.interno.gov.it" && !isCieIdHandoff(value)
  } == true

  fun providerLabel(value: String): String = parse(value)?.host.let { host ->
    when {
      host == null -> "Accesso con la CIE"
      inDomain(host, "spaggiari.eu") -> "Spaggiari"
      inDomain(host, "istruzione.it") -> "Ministero dell’Istruzione"
      inDomain(host, "servizicie.interno.gov.it") -> "CieID"
      else -> "Accesso con la CIE"
    }
  }

  /** A host name only (never path, query or fragment), reduced to a safe alphabet. */
  fun hostOf(value: String?): String {
    if (value == null) return "nessuna"
    val uri = parse(value)
    val raw = uri?.host ?: uri?.scheme ?: value.substringBefore(':')
    return raw.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }.take(64).ifEmpty { "sconosciuto" }
  }

  // '+' is a literal character of the token, not an encoded space.
  private fun decode(value: String): String? = runCatching {
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
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
