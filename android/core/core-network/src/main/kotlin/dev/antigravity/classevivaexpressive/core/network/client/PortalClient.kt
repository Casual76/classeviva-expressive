package dev.antigravity.classevivaexpressive.core.network.client

import android.webkit.CookieManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val PortalLoginUrl = "https://web.spaggiari.eu/home/app/default/login.php"

data class PortalFile(
  val bytes: ByteArray,
  val mimeType: String?,
  val fileName: String?,
  val cookieHeader: String?,
)

data class PortalUpload(
  val fileName: String,
  val mimeType: String?,
  val bytes: ByteArray,
)

enum class PortalActionType {
  ACKNOWLEDGE,
  REPLY,
  JOIN,
  FILE_UPLOAD,
  JUSTIFY,
}

class InMemoryCookieJar : CookieJar {
  private val store = mutableMapOf<String, MutableList<Cookie>>()

  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    val existing = store.getOrPut(url.host) { mutableListOf() }
    cookies.forEach { cookie ->
      existing.removeAll { it.name == cookie.name && it.path == cookie.path }
      existing += cookie
    }
  }

  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    val cookies = store[url.host].orEmpty()
    return cookies.filter { cookie ->
      val valid = cookie.expiresAt > System.currentTimeMillis()
      val pathMatches = url.encodedPath.startsWith(cookie.path)
      valid && pathMatches
    }
  }

  fun cookieHeader(url: String): String? {
    val cookies = loadForRequest(url.toHttpUrl())
    return cookies.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.name}=${it.value}" }
  }

  fun hosts(): Set<String> = store.keys
}

@Singleton
class ClassevivaPortalClient @Inject constructor(
  private val portalHttpClient: OkHttpClient,
  private val cookieJar: InMemoryCookieJar,
) {
  private val webCookieManager: CookieManager by lazy { CookieManager.getInstance() }

  init {
    webCookieManager.setAcceptCookie(true)
  }

  fun cookieHeader(url: String): String? = cookieJar.cookieHeader(url)

  suspend fun bootstrapSession(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val loginPage = Request.Builder()
        .url(PortalLoginUrl)
        .get()
        .header("User-Agent", UserAgent)
        .build()
      val html = portalHttpClient.newCall(loginPage).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException("Portale non raggiungibile: ${response.code}")
        }
        response.body?.string().orEmpty()
      }
      val document = Jsoup.parse(html, PortalLoginUrl)
      val form = document.selectFirst("form") ?: throw IOException("Form di login portale non trovato.")
      val usernameField = form.select("input").firstOrNull { input ->
        val name = input.attr("name").lowercase()
        name.contains("login") || name.contains("user") || name.contains("uid")
      }?.attr("name") ?: "login"
      val passwordField = form.select("input").firstOrNull { input ->
        val name = input.attr("name").lowercase()
        name.contains("password") || name.contains("pass")
      }?.attr("name") ?: "password"

      val body = FormBody.Builder().apply {
        form.select("input[type=hidden]").forEach { hidden ->
          add(hidden.attr("name"), hidden.attr("value"))
        }
        add(usernameField, username)
        add(passwordField, password)
      }.build()

      val action = form.absUrl("action").ifBlank { PortalLoginUrl }
      val request = Request.Builder()
        .url(action)
        .post(body)
        .header("User-Agent", UserAgent)
        .build()
      portalHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException("Login portale fallito: ${response.code}")
        }
      }
      syncCookiesToWebView()
    }
  }

  suspend fun downloadAuthenticated(url: String): Result<PortalFile> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url(url)
        .get()
        .header("User-Agent", UserAgent)
        .build()
      portalHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException("Download portale fallito: ${response.code}")
        }
        val finalUrl = response.request.url.toString()
        if (isLoginRedirect(finalUrl)) {
          throw IOException("Sessione portale scaduta. Riprova dopo un nuovo accesso.")
        }
        PortalFile(
          bytes = response.body?.bytes() ?: ByteArray(0),
          mimeType = response.header("Content-Type")?.substringBefore(";"),
          fileName = response.header("Content-Disposition")
            ?.substringAfter("filename=", "")
            ?.trim('"')
            ?.takeIf { it.isNotBlank() },
          cookieHeader = cookieHeader(url),
        )
      }
    }
  }

  suspend fun submitCommunicationAction(
    detail: CommunicationDetail,
    actionType: PortalActionType,
    replyText: String? = null,
    upload: PortalUpload? = null,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val directUrl = when (actionType) {
        PortalActionType.ACKNOWLEDGE -> detail.acknowledgeUrl
        PortalActionType.REPLY -> detail.replyUrl
        PortalActionType.JOIN -> detail.joinUrl
        PortalActionType.FILE_UPLOAD -> detail.fileUploadUrl
        PortalActionType.JUSTIFY -> null
      }
      val fallbackPageUrl = detail.portalDetailUrl ?: directUrl
      require(!fallbackPageUrl.isNullOrBlank()) {
        "Il portale non ha esposto un URL operativo per questa comunicazione."
      }
      submitPortalAction(
        pageUrl = fallbackPageUrl,
        directUrl = directUrl,
        actionType = actionType,
        textValue = replyText,
        upload = upload,
      )
      syncCookiesToWebView()
    }
  }

  suspend fun justifyAbsence(record: AbsenceRecord, reason: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val pageUrl = record.justifyUrl ?: record.detailUrl
        ?: throw IOException("Il portale non ha restituito un URL di giustificazione per questa assenza.")
      submitPortalAction(
        pageUrl = pageUrl,
        directUrl = record.justifyUrl,
        actionType = PortalActionType.JUSTIFY,
        textValue = reason,
        upload = null,
      )
      syncCookiesToWebView()
    }
  }

  private fun submitPortalAction(
    pageUrl: String,
    directUrl: String?,
    actionType: PortalActionType,
    textValue: String?,
    upload: PortalUpload?,
  ) {
    val keywords = keywordsFor(actionType)
    val page = loadHtmlDocument(pageUrl)
    findMatchingForm(page, keywords)?.let { form ->
      submitForm(pageUrl, form, keywords, textValue, upload)
      return
    }
    findMatchingLink(page, keywords)?.let { url ->
      performRequest(url = url, method = "GET")
      return
    }

    if (directUrl != null) {
      val directDocument = runCatching { loadHtmlDocument(directUrl) }.getOrNull()
      if (directDocument != null) {
        findMatchingForm(directDocument, keywords)?.let { form ->
          submitForm(directUrl, form, keywords, textValue, upload)
          return
        }
        findMatchingLink(directDocument, keywords)?.let { url ->
          performRequest(url = url, method = "GET")
          return
        }
      }
      if (textValue.isNullOrBlank() && upload == null) {
        performRequest(url = directUrl, method = "GET")
        return
      }
    }

    throw IOException("Azione portale non individuata automaticamente.")
  }

  private fun loadHtmlDocument(url: String): Document {
    val response = performRequest(url = url, method = "GET")
    return Jsoup.parse(response, url)
  }

  private fun performRequest(
    url: String,
    method: String,
    body: RequestBody? = null,
  ): String {
    val builder = Request.Builder()
      .url(url)
      .header("User-Agent", UserAgent)

    when (method.uppercase()) {
      "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
      else -> builder.get()
    }

    return portalHttpClient.newCall(builder.build()).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("Richiesta portale fallita: ${response.code}")
      }
      val payload = response.body?.string().orEmpty()
      val finalUrl = response.request.url.toString()
      if (isLoginRedirect(finalUrl) || looksLikeLoginPage(payload)) {
        throw IOException("Sessione portale scaduta o pagina di login restituita al posto dell'azione richiesta.")
      }
      payload
    }
  }

  private fun findMatchingForm(document: Document, keywords: List<String>): Element? {
    return document.select("form").firstOrNull { form ->
      val text = buildString {
        append(form.attr("id")).append(' ')
        append(form.attr("name")).append(' ')
        append(form.attr("class")).append(' ')
        append(form.attr("action")).append(' ')
        append(form.text())
        form.select("button,input,textarea,label").forEach { append(' ').append(it.outerHtml()) }
      }.lowercase()
      keywords.any { text.contains(it) }
    }
  }

  private fun findMatchingLink(document: Document, keywords: List<String>): String? {
    val candidates = document.select("a[href],button[formaction],input[formaction]")
    return candidates.firstNotNullOfOrNull { element ->
      val text = buildString {
        append(element.text()).append(' ')
        append(element.attr("title")).append(' ')
        append(element.attr("aria-label")).append(' ')
        append(element.attr("id")).append(' ')
        append(element.attr("class")).append(' ')
        append(element.attr("href")).append(' ')
        append(element.attr("formaction"))
      }.lowercase()
      when {
        keywords.any { text.contains(it) } -> {
          val href = element.absUrl("href").ifBlank { element.absUrl("formaction") }
          href.takeIf { it.isNotBlank() }
        }
        else -> null
      }
    }
  }

  private fun submitForm(
    baseUrl: String,
    form: Element,
    keywords: List<String>,
    textValue: String?,
    upload: PortalUpload?,
  ) {
    val actionUrl = form.absUrl("action").ifBlank { baseUrl }
    val method = form.attr("method").ifBlank { "POST" }.uppercase()
    val params = linkedMapOf<String, String>()

    form.select("input").forEach { input ->
      val name = input.attr("name").ifBlank { return@forEach }
      when (input.attr("type").lowercase()) {
        "hidden" -> params[name] = input.`val`()
        "checkbox", "radio" -> if (input.hasAttr("checked")) params[name] = input.`val`().ifBlank { "on" }
        "submit" -> {
          if (matchesKeywords(input.outerHtml(), keywords)) {
            params[name] = input.`val`().ifBlank { "1" }
          }
        }
      }
    }

    form.select("button[name]").firstOrNull { matchesKeywords(it.outerHtml(), keywords) }?.let { button ->
      params[button.attr("name")] = button.`val`().ifBlank { "1" }
    }

    val textField = form.select("textarea,input[type=text],input:not([type])").firstOrNull {
      val key = listOf(it.attr("name"), it.attr("id"), it.attr("placeholder")).joinToString(" ").lowercase()
      listOf("reply", "risp", "test", "note", "motiv", "reason", "message").any(key::contains)
    } ?: form.select("textarea,input[type=text],input:not([type])").firstOrNull()
    if (!textValue.isNullOrBlank() && textField != null) {
      val name = textField.attr("name").ifBlank { textField.attr("id") }
      if (name.isNotBlank()) {
        params[name] = textValue
      }
    }

    if (method == "GET" && upload == null) {
      val urlBuilder = actionUrl.toHttpUrlOrNull()?.newBuilder()
        ?: throw IOException("URL portale non valido: $actionUrl")
      params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
      performRequest(urlBuilder.build().toString(), method = "GET")
      return
    }

    val body = if (upload != null) {
      val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
      params.forEach { (key, value) -> multipart.addFormDataPart(key, value) }
      val fileField = form.select("input[type=file]").firstOrNull()?.attr("name").orEmpty().ifBlank { "file" }
      multipart.addFormDataPart(
        fileField,
        upload.fileName,
        upload.bytes.toRequestBody(upload.mimeType?.toMediaTypeOrNull()),
      )
      multipart.build()
    } else {
      FormBody.Builder().apply {
        params.forEach { (key, value) -> add(key, value) }
      }.build()
    }
    performRequest(url = actionUrl, method = "POST", body = body)
  }

  private fun matchesKeywords(source: String, keywords: List<String>): Boolean {
    val lowered = source.lowercase()
    return keywords.any(lowered::contains)
  }

  private fun keywordsFor(actionType: PortalActionType): List<String> {
    return when (actionType) {
      PortalActionType.ACKNOWLEDGE -> listOf("presa visione", "conferma", "firma", "sign", "ack", "visione")
      PortalActionType.REPLY -> listOf("rispondi", "risposta", "reply", "invia")
      PortalActionType.JOIN -> listOf("adesione", "aderisci", "join", "partecipa")
      PortalActionType.FILE_UPLOAD -> listOf("carica", "upload", "allega", "file")
      PortalActionType.JUSTIFY -> listOf("giustifica", "giustificazione", "justify")
    }
  }

  private fun syncCookiesToWebView() {
    cookieJar.hosts().forEach { host ->
      val header = cookieJar.cookieHeader("https://$host") ?: return@forEach
      header.split("; ").forEach { cookie ->
        webCookieManager.setCookie("https://$host", cookie)
      }
    }
    webCookieManager.flush()
  }

  private fun isLoginRedirect(url: String): Boolean {
    return url.contains("login.php", ignoreCase = true) || url.contains("/login", ignoreCase = true)
  }

  private fun looksLikeLoginPage(payload: String): Boolean {
    val normalized = payload.lowercase()
    return normalized.contains("name=\"password\"") &&
      normalized.contains("form") &&
      normalized.contains("login")
  }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  @Provides
  @Singleton
  fun provideLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }
  }

  @Provides
  @Singleton
  @Named("rest")
  fun provideRestHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(loggingInterceptor)
      .build()
  }

  @Provides
  @Singleton
  fun provideRestClient(json: Json, @Named("rest") httpClient: OkHttpClient): ClassevivaRestClient {
    return ClassevivaRestClient(json = json, httpClient = httpClient)
  }

  @Provides
  @Singleton
  fun providePortalCookieJar(): InMemoryCookieJar = InMemoryCookieJar()

  @Provides
  @Singleton
  @Named("portal")
  fun providePortalHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    cookieJar: InMemoryCookieJar,
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .cookieJar(cookieJar)
      .addInterceptor(loggingInterceptor)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }

  @Provides
  @Singleton
  fun providePortalClient(
    @Named("portal") portalHttpClient: OkHttpClient,
    cookieJar: InMemoryCookieJar,
  ): ClassevivaPortalClient {
    return ClassevivaPortalClient(portalHttpClient = portalHttpClient, cookieJar = cookieJar)
  }
}
