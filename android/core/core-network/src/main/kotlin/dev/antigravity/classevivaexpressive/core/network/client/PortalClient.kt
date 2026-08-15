package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.domain.model.AttachmentPayload
import dev.antigravity.classevivaexpressive.core.domain.model.PortalCookieDto
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import android.util.Base64

private const val PortalLoginUrl = "https://web.spaggiari.eu/home/app/default/login.php"
private const val PortalBaseUrl = "https://web.spaggiari.eu"
private const val PortalNoticeboardUrl = "$PortalBaseUrl/sif/app/default/bacheca_personale.php"
private const val PortalUserAgent = "CVVS/std/4.1.7 Android/10"
const val PortalMeetingsUrl = "https://web.spaggiari.eu/fml/app/default/colloqui.php"

class NetworkDocumentStream internal constructor(
  private val body: ResponseBody,
  val fileName: String?,
  val mimeType: String?,
) : Closeable {
  fun byteStream(): InputStream = body.byteStream()

  override fun close() = body.close()
}

data class PortalNoticeboardDetail(
  val content: String?,
  val acknowledgeUrl: String? = null,
  val replyUrl: String? = null,
  val joinUrl: String? = null,
  val fileUploadUrl: String? = null,
)

internal data class PortalNoticeboardSubmission(
  val action: String,
  val relationId: String,
  val textFieldName: String? = null,
  val fileFieldName: String? = null,
)

private data class PortalNoticeboardActionCandidate(
  val selector: String,
  val action: String,
  val textFieldName: String? = null,
  val fileFieldName: String? = null,
)

internal fun findPortalNoticeboardSubmission(
  doc: Document,
  keywords: List<String>,
  textValue: String? = null,
  fileAttachment: AttachmentPayload? = null,
): PortalNoticeboardSubmission? {
  val normalizedKeywords = keywords.map { it.lowercase() }
  val candidates = when {
    fileAttachment != null || normalizedKeywords.any { it in listOf("carica", "upload", "allega", "file") } -> listOf(
      PortalNoticeboardActionCandidate(
        selector = ".rispondi_file",
        action = "insert_risposta_file",
        fileFieldName = "file_risposta",
      ),
    )
    textValue != null || normalizedKeywords.any { it in listOf("rispondi", "risposta", "reply", "invia") } -> listOf(
      PortalNoticeboardActionCandidate(
        selector = ".rispondi_testo",
        action = "insert_risposta_testo",
        textFieldName = "testo_risposta",
      ),
    )
    normalizedKeywords.any { it in listOf("adesione", "aderisci", "join", "partecipa") } -> listOf(
      PortalNoticeboardActionCandidate(".rispondi_bottone", "insert_risposta_bottone"),
      PortalNoticeboardActionCandidate(".rispondi_conferma", "insert_risposta_conferma"),
    )
    normalizedKeywords.any { it in listOf("conferma", "presa", "visione", "sign", "firma", "ack") } -> listOf(
      PortalNoticeboardActionCandidate(".rispondi_conferma", "insert_risposta_conferma"),
      PortalNoticeboardActionCandidate(".rispondi_bottone", "insert_risposta_bottone"),
    )
    else -> emptyList()
  }

  return candidates.firstNotNullOfOrNull { candidate ->
    val relationId = doc.selectFirst("${candidate.selector}[relazione_id]")
      ?.attr("relazione_id")
      ?.takeIf(String::isNotBlank)
      ?: return@firstNotNullOfOrNull null
    PortalNoticeboardSubmission(
      action = candidate.action,
      relationId = relationId,
      textFieldName = candidate.textFieldName,
      fileFieldName = candidate.fileFieldName,
    )
  }
}

@Singleton
class PortalClient private constructor(
  private val sessionStorage: SessionStorage,
  private val loggingInterceptor: HttpLoggingInterceptor,
  private val portalLoginUrl: String,
  private val portalOrigin: HttpUrl,
) {
  @Inject
  constructor(
    sessionStorage: SessionStorage,
    loggingInterceptor: HttpLoggingInterceptor,
  ) : this(
    sessionStorage = sessionStorage,
    loggingInterceptor = loggingInterceptor,
    portalLoginUrl = PortalLoginUrl,
    portalOrigin = PortalLoginUrl.toHttpUrl(),
  )

  internal constructor(
    sessionStorage: SessionStorage,
    loggingInterceptor: HttpLoggingInterceptor,
    portalLoginUrl: String,
  ) : this(
    sessionStorage = sessionStorage,
    loggingInterceptor = loggingInterceptor,
    portalLoginUrl = portalLoginUrl,
    portalOrigin = portalLoginUrl.toHttpUrl(),
  )

  private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

  private val cookieJar = object : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
      cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
        removeAll { existing -> cookies.any { it.name == existing.name } }
        addAll(cookies)
      }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
      val now = System.currentTimeMillis()
      return cookieStore[url.host]
        ?.filter { cookie -> cookie.expiresAt > now && cookie.matches(url) }
        .orEmpty()
    }
  }

  private val portalHttpClient = OkHttpClient.Builder()
    .cookieJar(cookieJar)
    .addInterceptor(loggingInterceptor)
    .addNetworkInterceptor { chain ->
      val requestUrl = chain.request().url
      if (!isPortalOrigin(requestUrl)) {
        throw IOException("Redirect del portale verso un'origine non consentita.")
      }
      chain.proceed(chain.request())
    }
    .followRedirects(true)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  private val portalAssetHttpClient = portalHttpClient.newBuilder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

  private fun isPortalOrigin(url: HttpUrl): Boolean =
    url.scheme == portalOrigin.scheme &&
      url.host == portalOrigin.host &&
      url.port == portalOrigin.port

  private fun hasUsablePortalSessionCookie(): Boolean {
    val rootUrl = portalOrigin.newBuilder().encodedPath("/").query(null).build()
    val now = System.currentTimeMillis()
    return cookieStore[portalOrigin.host]?.any { cookie ->
      cookie.expiresAt > now &&
        cookie.matches(rootUrl) &&
        (
          cookie.name.contains("PHPSESSID", ignoreCase = true) ||
            cookie.name.contains("cvv", ignoreCase = true) ||
            cookie.name.contains("sess", ignoreCase = true)
          )
    } == true
  }

  private suspend fun ensurePortalSession(forceRefresh: Boolean = false) {
    if (forceRefresh) cookieStore.clear()

    if (hasUsablePortalSessionCookie()) return

    val credentials = sessionStorage.readStoredCredentials()
      ?: throw ClassevivaNetworkException("Credenziali non disponibili per il portale.")

    withContext(Dispatchers.IO) {
      val loginHtml = portalHttpClient.newCall(
        Request.Builder()
          .url(portalLoginUrl)
          .header("User-Agent", PortalUserAgent)
          .build()
      ).execute().use { loginPageResponse ->
        if (!loginPageResponse.isSuccessful || !isPortalOrigin(loginPageResponse.request.url)) {
          throw ClassevivaNetworkException("Pagina di login portale non raggiungibile.")
        }
        loginPageResponse.body?.string()
          ?: throw ClassevivaNetworkException("Pagina di login portale non raggiungibile.")
      }

      val doc = Jsoup.parse(loginHtml, portalLoginUrl)
      val form = doc.selectFirst("form")
        ?: throw ClassevivaNetworkException("Form di login portale non trovato.")

      val formAction = form.absUrl("action").takeIf(String::isNotBlank) ?: portalLoginUrl
      val formActionUrl = formAction.toHttpUrlOrNull()
        ?.takeIf(::isPortalOrigin)
        ?: throw ClassevivaNetworkException("Form di login esterno al portale Classeviva.")

      val formBody = FormBody.Builder()
      form.select("input[type=hidden]").forEach { input ->
        val name = input.attr("name").takeIf(String::isNotBlank) ?: return@forEach
        formBody.add(name, input.attr("value"))
      }
      val usernameField = findLoginField(form, listOf("login", "user", "uid")) ?: "login"
      val passwordField = findLoginField(form, listOf("password", "pass")) ?: "password"
      formBody.add(usernameField, credentials.username)
      formBody.add(passwordField, credentials.password)

      portalHttpClient.newCall(
        Request.Builder()
          .url(formActionUrl)
          .header("User-Agent", PortalUserAgent)
          .post(formBody.build())
          .build()
      ).execute().use { loginResponse ->
        if (!loginResponse.isSuccessful || !isPortalOrigin(loginResponse.request.url)) {
          throw ClassevivaNetworkException("Accesso al portale non riuscito (${loginResponse.code}).")
        }
      }
      if (!hasUsablePortalSessionCookie()) {
        throw ClassevivaNetworkException("Il portale non ha creato una sessione valida.")
      }
    }
  }

  suspend fun openSchoolReport(
    viewUrl: String?,
    confirmUrl: String?,
  ): NetworkDocumentStream = withContext(Dispatchers.IO) {
    val suppliedCandidates = listOfNotNull(viewUrl, confirmUrl)
      .mapNotNull { value -> value.trim().takeIf(String::isNotBlank) }
      .distinct()
    val candidates = suppliedCandidates.mapNotNull { value ->
      value.toHttpUrlOrNull()
        ?.takeIf(::isPortalOrigin)
    }
    if (candidates.isEmpty()) {
      val message = if (suppliedCandidates.isEmpty()) {
        "URL pagella non disponibile."
      } else {
        "URL pagella esterno o non valido."
      }
      throw ClassevivaNetworkException(message)
    }
    ensurePortalSession()

    var lastFailure: ClassevivaNetworkException? = null
    for (pass in 0..1) {
      var sessionRejected = false
      candidates.forEach { targetUrl ->
        when (val result = tryOpenSchoolReport(targetUrl)) {
          is SchoolReportAttempt.Success -> return@withContext result.stream
          is SchoolReportAttempt.Failure -> {
            lastFailure = result.error
            sessionRejected = sessionRejected || result.sessionRejected
          }
        }
      }
      if (pass == 0 && sessionRejected) {
        ensurePortalSession(forceRefresh = true)
      } else {
        break
      }
    }
    throw lastFailure ?: ClassevivaNetworkException("Il portale non ha reso disponibile la pagella.")
  }

  private fun tryOpenSchoolReport(targetUrl: HttpUrl): SchoolReportAttempt {
    val response = runCatching {
      portalAssetHttpClient.newCall(
        Request.Builder()
          .url(targetUrl)
          .header("User-Agent", PortalUserAgent)
          .build(),
      ).execute()
    }.getOrElse { error ->
      return SchoolReportAttempt.Failure(
        ClassevivaNetworkException("Download pagella non riuscito.", error),
      )
    }

    val redirectLocation = response.header("Location")?.let { location ->
      targetUrl.resolve(location)
    }
    if (response.isRedirect) {
      val sessionRejected = redirectLocation?.encodedPath?.contains("login", ignoreCase = true) == true
      response.close()
      return SchoolReportAttempt.Failure(
        error = ClassevivaNetworkException("Il portale ha restituito un redirect invece della pagella."),
        sessionRejected = sessionRejected,
      )
    }
    if (!response.isSuccessful || !isPortalOrigin(response.request.url)) {
      val code = response.code
      val sessionRejected = code == 401 || code == 403
      response.close()
      return SchoolReportAttempt.Failure(
        error = ClassevivaNetworkException("Il portale non ha reso disponibile la pagella ($code)."),
        sessionRejected = sessionRejected,
      )
    }

    val body = response.body
    if (body == null || body.contentLength() == 0L) {
      response.close()
      return SchoolReportAttempt.Failure(
        ClassevivaNetworkException("Il portale ha restituito una pagella vuota."),
      )
    }
    val mimeType = body.contentType()?.toString()?.substringBefore(';')?.trim()?.lowercase()
    val preview = runCatching { response.peekBody(8_192).string() }.getOrDefault("")
    val htmlResponse = mimeType == "text/html" ||
      mimeType == "application/xhtml+xml" ||
      looksLikeHtml(preview)
    if (htmlResponse) {
      val sessionRejected = looksLikeLoginPage(preview) ||
        response.request.url.encodedPath.contains("login", ignoreCase = true)
      response.close()
      return SchoolReportAttempt.Failure(
        error = ClassevivaNetworkException("Il portale ha restituito una pagina HTML invece della pagella."),
        sessionRejected = sessionRejected,
      )
    }

    return SchoolReportAttempt.Success(
      NetworkDocumentStream(
        body = body,
        fileName = contentDispositionFileName(response.header("Content-Disposition"))
          ?: targetUrl.pathSegments.lastOrNull()?.takeIf(String::isNotBlank),
        mimeType = mimeType,
      ),
    )
  }

  private fun looksLikeHtml(value: String): Boolean {
    val normalized = value.trimStart().lowercase()
    return normalized.startsWith("<!doctype html") ||
      normalized.startsWith("<html") ||
      normalized.startsWith("<head") ||
      normalized.startsWith("<body") ||
      normalized.startsWith("<form")
  }

  private fun looksLikeLoginPage(value: String): Boolean {
    val normalized = value.lowercase()
    return looksLikeHtml(value) &&
      (normalized.contains("type=\"password\"") ||
        normalized.contains("type='password'") ||
        normalized.contains("name=\"password\"") ||
        normalized.contains("name='password'") ||
        normalized.contains("login"))
  }

  private sealed interface SchoolReportAttempt {
    data class Success(val stream: NetworkDocumentStream) : SchoolReportAttempt

    data class Failure(
      val error: ClassevivaNetworkException,
      val sessionRejected: Boolean = false,
    ) : SchoolReportAttempt
  }

  suspend fun submitPortalAction(
    pageUrl: String,
    formKeywords: List<String>,
    textValue: String? = null,
    fileAttachment: AttachmentPayload? = null,
  ): Unit = withContext(Dispatchers.IO) {
    ensurePortalSession()

    for (candidate in listOf(pageUrl)) {
      val response = portalHttpClient.newCall(
        Request.Builder().url(candidate).header("User-Agent", PortalUserAgent).build()
      ).execute()
      val html = response.body?.string() ?: continue
      val responseUrl = response.request.url.toString()
      val doc = Jsoup.parse(html, responseUrl)

      val noticeboardSubmission = findPortalNoticeboardSubmission(
        doc = doc,
        keywords = formKeywords,
        textValue = textValue,
        fileAttachment = fileAttachment,
      )
      if (noticeboardSubmission != null) {
        submitNoticeboardSubmission(noticeboardSubmission, textValue, fileAttachment)
        return@withContext
      }

      val form = findMatchingForm(doc, formKeywords)
      if (form != null) {
        submitForm(form, responseUrl, formKeywords, textValue, fileAttachment)
        return@withContext
      }

      val link = findMatchingLink(doc, formKeywords, responseUrl)
      if (link != null) {
        executePortalRequest(
          Request.Builder().url(link).header("User-Agent", PortalUserAgent).build()
        )
        return@withContext
      }
    }

    throw ClassevivaNetworkException("Azione portale non riconosciuta per questo tenant.")
  }

  suspend fun justifyAbsence(
    justifyUrl: String?,
    detailUrl: String?,
    reasonText: String?,
    fileAttachment: AttachmentPayload? = null,
  ) {
    val targetUrl = justifyUrl ?: detailUrl
      ?: throw ClassevivaNetworkException("URL giustificazione assenza non disponibile.")
    submitPortalAction(
      pageUrl = targetUrl,
      formKeywords = listOf("giustifica", "giustificazione", "justify", "conferma"),
      textValue = reasonText,
      fileAttachment = fileAttachment,
    )
  }

  suspend fun replyNoticeboard(replyUrl: String, text: String) =
    submitPortalAction(
      pageUrl = replyUrl,
      formKeywords = listOf("rispondi", "risposta", "reply", "invia"),
      textValue = text,
    )

  suspend fun joinNoticeboard(joinUrl: String) =
    submitPortalAction(
      pageUrl = joinUrl,
      formKeywords = listOf("adesione", "aderisci", "join", "partecipa"),
    )

  suspend fun uploadNoticeboard(uploadUrl: String, attachment: AttachmentPayload) =
    submitPortalAction(
      pageUrl = uploadUrl,
      formKeywords = listOf("carica", "upload", "allega", "file"),
      fileAttachment = attachment,
    )

  suspend fun submitHomework(submitUrl: String, text: String?, attachment: AttachmentPayload?) =
    submitPortalAction(
      pageUrl = submitUrl,
      formKeywords = listOf("consegna", "restituisci", "invia", "carica", "upload"),
      textValue = text,
      fileAttachment = attachment,
    )

  suspend fun getMeetingsPageHtml(): Pair<String, String>? = withContext(Dispatchers.IO) {
    ensurePortalSession()
    discoverPortalPage(listOf("colloqui", "ricevimento", "prenot"))
  }

  suspend fun getNoticeboardDetail(pageUrl: String): PortalNoticeboardDetail? = withContext(Dispatchers.IO) {
    ensurePortalSession()
    val response = portalHttpClient.newCall(
      Request.Builder().url(pageUrl).header("User-Agent", PortalUserAgent).build()
    ).execute()
    val html = response.body?.string() ?: return@withContext null
    val responseUrl = response.request.url.toString()
    val doc = Jsoup.parse(html, responseUrl)
    doc.select("script, style, nav, header, footer").remove()
    val content = listOf(
      ".comunicazione",
      ".notice",
      ".cnt",
      ".content",
      "article",
      "main",
      "body",
    ).firstNotNullOfOrNull { selector ->
      doc.selectFirst(selector)?.text()?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.length > 20 }
    }
    PortalNoticeboardDetail(
      content = content,
      acknowledgeUrl = findNoticeboardActionUrl(doc, listOf("conferma", "presa", "visione", "sign", "firma", "ack"))
        ?: responseUrl.takeIf { hasPortalNoticeboardAcceptance(doc) },
      replyUrl = findNoticeboardActionUrl(doc, listOf("rispondi", "risposta", "reply"))
        ?: responseUrl.takeIf { hasPortalNoticeboardClass(doc, ".rispondi_testo") },
      joinUrl = findNoticeboardActionUrl(doc, listOf("adesione", "aderisci", "join", "partecipa"))
        ?: responseUrl.takeIf { hasPortalNoticeboardClass(doc, ".rispondi_bottone") },
      fileUploadUrl = findNoticeboardActionUrl(doc, listOf("carica", "upload", "allega", "file"))
        ?: responseUrl.takeIf { hasPortalNoticeboardClass(doc, ".rispondi_file") },
    )
  }

  fun getSessionCookies(): List<PortalCookieDto> {
    return cookieStore["web.spaggiari.eu"]?.map {
      PortalCookieDto(name = it.name, value = it.value)
    } ?: emptyList()
  }

  fun clearSession() {
    cookieStore.clear()
  }

  private suspend fun discoverPortalPage(keywords: List<String>): Pair<String, String>? {
    val landingResponse = portalHttpClient.newCall(
      Request.Builder().url(PortalLoginUrl).header("User-Agent", PortalUserAgent).build()
    ).execute()
    val landingHtml = landingResponse.body?.string() ?: return null
    val landingUrl = landingResponse.request.url.toString()
    val doc = Jsoup.parse(landingHtml, landingUrl)

    if (soupMatches(doc, keywords)) return landingHtml to landingUrl

    for (element in doc.select("a[href], button[formaction], input[formaction]")) {
      val href = element.absUrl("href").ifBlank { element.absUrl("formaction") }
      if (href.isBlank()) continue
      val elementText = listOf(
        element.text(),
        element.attr("title"),
        element.attr("aria-label"),
        href,
      ).filter(String::isNotBlank).joinToString(" ").lowercase()
      if (keywords.any { elementText.contains(it) }) {
        val response = portalHttpClient.newCall(
          Request.Builder().url(href).header("User-Agent", PortalUserAgent).build()
        ).execute()
        val html = response.body?.string() ?: continue
        return html to response.request.url.toString()
      }
    }
    return null
  }

  private fun findMatchingForm(doc: Document, keywords: List<String>): Element? {
    return doc.select("form").firstOrNull { form ->
      val inputNames = form.select("input, textarea, select, button")
        .joinToString(" ") { "${it.attr("name")} ${it.attr("id")} ${it.attr("value")} ${it.attr("placeholder")}" }
      val text = (form.text() + " " + form.attr("action") + " " + inputNames).lowercase()
      keywords.any { text.contains(it) }
    }
  }

  private fun findMatchingLink(doc: Document, keywords: List<String>, baseUrl: String): String? {
    return doc.select("a[href]").firstOrNull { link ->
      val text = (link.text() + " " + link.attr("href")).lowercase()
      keywords.any { text.contains(it) }
    }?.absUrl("href")?.takeIf(String::isNotBlank)
  }

  private fun findNoticeboardActionUrl(doc: Document, keywords: List<String>): String? {
    return doc.select("a[href], form[action], button[formaction], input[formaction]").firstNotNullOfOrNull { element ->
      val text = listOf(
        element.text(),
        element.attr("value"),
        element.attr("title"),
        element.attr("aria-label"),
        element.attr("href"),
        element.attr("action"),
        element.attr("formaction"),
      ).joinToString(" ").lowercase()
      if (keywords.any { text.contains(it) }) {
        element.absUrl("href")
          .ifBlank { element.absUrl("action") }
          .ifBlank { element.absUrl("formaction") }
          .takeIf(String::isNotBlank)
      } else {
        null
      }
    }
  }

  private fun hasPortalNoticeboardAcceptance(doc: Document): Boolean {
    return hasPortalNoticeboardClass(doc, ".rispondi_conferma") ||
      hasPortalNoticeboardClass(doc, ".rispondi_bottone")
  }

  private fun hasPortalNoticeboardClass(doc: Document, selector: String): Boolean {
    return doc.selectFirst("$selector[relazione_id]") != null
  }

  private fun submitNoticeboardSubmission(
    submission: PortalNoticeboardSubmission,
    textValue: String?,
    fileAttachment: AttachmentPayload?,
  ) {
    if (fileAttachment != null) {
      val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("action", submission.action)
        .addFormDataPart("relazione_id", submission.relationId)
      val bytes = Base64.decode(fileAttachment.base64Content, Base64.DEFAULT)
      val mediaType = (fileAttachment.mimeType ?: "application/octet-stream").toMediaType()
      multipart.addFormDataPart(
        submission.fileFieldName ?: "file_risposta",
        fileAttachment.fileName,
        bytes.toRequestBody(mediaType),
      )
      executePortalRequest(
        Request.Builder().url(PortalNoticeboardUrl).header("User-Agent", PortalUserAgent).post(multipart.build()).build()
      )
      return
    }

    val form = FormBody.Builder()
      .add("action", submission.action)
      .add("relazione_id", submission.relationId)
    if (textValue != null) {
      form.add(submission.textFieldName ?: "testo_risposta", textValue)
    }
    executePortalRequest(
      Request.Builder().url(PortalNoticeboardUrl).header("User-Agent", PortalUserAgent).post(form.build()).build()
    )
  }

  private fun submitForm(
    form: Element,
    baseUrl: String,
    keywords: List<String>,
    textValue: String?,
    fileAttachment: AttachmentPayload?,
  ) {
    val action = form.absUrl("action").ifBlank { baseUrl }

    if (fileAttachment != null) {
      val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
      form.select("input:not([type=file]), textarea, select").forEach { input ->
        val name = input.attr("name").takeIf(String::isNotBlank) ?: return@forEach
        val value = when {
          textValue != null && keywords.any {
            input.attr("name").lowercase().contains(it) ||
              input.attr("placeholder").lowercase().contains(it)
          } -> textValue
          else -> input.`val`()
        }
        multipart.addFormDataPart(name, value)
      }
      val bytes = Base64.decode(fileAttachment.base64Content, Base64.DEFAULT)
      val mediaType = (fileAttachment.mimeType ?: "application/octet-stream").toMediaType()
      val fileFieldName = form.selectFirst("input[type=file][name]")?.attr("name")?.takeIf(String::isNotBlank)
        ?: "file"
      multipart.addFormDataPart(fileFieldName, fileAttachment.fileName, bytes.toRequestBody(mediaType))
      executePortalRequest(
        Request.Builder().url(action).header("User-Agent", PortalUserAgent).post(multipart.build()).build()
      )
    } else {
      val formBody = FormBody.Builder()
      form.select("input, textarea, select").forEach { input ->
        val name = input.attr("name").takeIf(String::isNotBlank) ?: return@forEach
        val value = when {
          textValue != null && keywords.any { input.attr("name").lowercase().contains(it) } -> textValue
          else -> input.`val`()
        }
        formBody.add(name, value)
      }
      executePortalRequest(
        Request.Builder().url(action).header("User-Agent", PortalUserAgent).post(formBody.build()).build()
      )
    }
  }

  private fun executePortalRequest(request: Request) {
    portalHttpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw ClassevivaNetworkException("Il portale Classeviva ha rifiutato l'azione (${response.code}).")
      }
      val finalUrl = response.request.url
      val finalHost = finalUrl.host.lowercase()
      val finalPath = finalUrl.encodedPath.lowercase()
      if (finalHost != "web.spaggiari.eu" || "login" in finalPath) {
        throw ClassevivaNetworkException("Sessione portale scaduta o non valida.")
      }
    }
  }

  private fun soupMatches(doc: Document, keywords: List<String>): Boolean {
    val text = doc.text().lowercase()
    return keywords.any { text.contains(it) }
  }

private fun findLoginField(form: Element, candidates: List<String>): String? {
    for (candidate in candidates) {
      val input = form.selectFirst("input[name*=$candidate], input[id*=$candidate]")
      if (input != null) return input.attr("name").takeIf(String::isNotBlank) ?: input.attr("id")
    }
    return null
  }
}

private fun contentDispositionFileName(value: String?): String? {
  val header = value?.takeIf(String::isNotBlank) ?: return null
  val encoded = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
    .find(header)
    ?.groupValues
    ?.getOrNull(1)
  if (!encoded.isNullOrBlank()) {
    return runCatching {
      URLDecoder.decode(encoded.trim(), StandardCharsets.UTF_8.name())
    }.getOrNull()?.takeIf(String::isNotBlank)
  }
  return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
    .find(header)
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
    ?.takeIf(String::isNotBlank)
}
