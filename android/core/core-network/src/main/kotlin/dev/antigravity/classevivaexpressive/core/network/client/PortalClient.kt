package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.domain.model.AttachmentPayload
import dev.antigravity.classevivaexpressive.core.domain.model.PortalCookieDto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import android.util.Base64

private const val PortalLoginUrl = "https://web.spaggiari.eu/home/app/default/login.php"
private const val PortalBaseUrl = "https://web.spaggiari.eu"
const val PortalMeetingsUrl = "https://web.spaggiari.eu/fml/app/default/colloqui.php"

@Singleton
class PortalClient @Inject constructor(
  private val sessionStorage: SessionStorage,
  private val loggingInterceptor: HttpLoggingInterceptor,
) {
  private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

  private val cookieJar = object : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
      cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
        removeAll { existing -> cookies.any { it.name == existing.name } }
        addAll(cookies)
      }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
      cookieStore[url.host]?.toList() ?: emptyList()
  }

  private val portalHttpClient = OkHttpClient.Builder()
    .cookieJar(cookieJar)
    .addInterceptor(loggingInterceptor)
    .followRedirects(true)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  private suspend fun ensurePortalSession() {
    val hasSession = cookieStore["web.spaggiari.eu"]?.any {
      it.name.contains("PHPSESSID", ignoreCase = true) ||
        it.name.contains("cvv", ignoreCase = true) ||
        it.name.contains("sess", ignoreCase = true)
    } == true

    if (hasSession) return

    val credentials = sessionStorage.readStoredCredentials()
      ?: throw ClassevivaNetworkException("Credenziali non disponibili per il portale.")

    withContext(Dispatchers.IO) {
      val loginPageResponse = portalHttpClient.newCall(
        Request.Builder()
          .url(PortalLoginUrl)
          .header("User-Agent", UserAgent)
          .build()
      ).execute()

      val loginHtml = loginPageResponse.body?.string()
        ?: throw ClassevivaNetworkException("Pagina di login portale non raggiungibile.")

      val doc = Jsoup.parse(loginHtml, PortalLoginUrl)
      val form = doc.selectFirst("form")
        ?: throw ClassevivaNetworkException("Form di login portale non trovato.")

      val formAction = form.absUrl("action").takeIf(String::isNotBlank) ?: PortalLoginUrl

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
          .url(formAction)
          .header("User-Agent", UserAgent)
          .post(formBody.build())
          .build()
      ).execute().close()
    }
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
        Request.Builder().url(candidate).header("User-Agent", UserAgent).build()
      ).execute()
      val html = response.body?.string() ?: continue
      val responseUrl = response.request.url.toString()
      val doc = Jsoup.parse(html, responseUrl)

      val form = findMatchingForm(doc, formKeywords)
      if (form != null) {
        submitForm(form, responseUrl, formKeywords, textValue, fileAttachment)
        return@withContext
      }

      val link = findMatchingLink(doc, formKeywords, responseUrl)
      if (link != null) {
        portalHttpClient.newCall(
          Request.Builder().url(link).header("User-Agent", UserAgent).build()
        ).execute().close()
        return@withContext
      }
    }

    if (textValue == null && fileAttachment == null) {
      portalHttpClient.newCall(
        Request.Builder().url(pageUrl).header("User-Agent", UserAgent).build()
      ).execute().close()
      return@withContext
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
      Request.Builder().url(PortalLoginUrl).header("User-Agent", UserAgent).build()
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
          Request.Builder().url(href).header("User-Agent", UserAgent).build()
        ).execute()
        val html = response.body?.string() ?: continue
        return html to response.request.url.toString()
      }
    }
    return null
  }

  private fun findMatchingForm(doc: Document, keywords: List<String>): Element? {
    return doc.select("form").firstOrNull { form ->
      val text = (form.text() + " " + form.attr("action")).lowercase()
      keywords.any { text.contains(it) }
    } ?: doc.selectFirst("form")
  }

  private fun findMatchingLink(doc: Document, keywords: List<String>, baseUrl: String): String? {
    return doc.select("a[href]").firstOrNull { link ->
      val text = (link.text() + " " + link.attr("href")).lowercase()
      keywords.any { text.contains(it) }
    }?.absUrl("href")?.takeIf(String::isNotBlank)
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
      multipart.addFormDataPart("file", fileAttachment.fileName, bytes.toRequestBody(mediaType))
      portalHttpClient.newCall(
        Request.Builder().url(action).header("User-Agent", UserAgent).post(multipart.build()).build()
      ).execute().close()
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
      portalHttpClient.newCall(
        Request.Builder().url(action).header("User-Agent", UserAgent).post(formBody.build()).build()
      ).execute().close()
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
