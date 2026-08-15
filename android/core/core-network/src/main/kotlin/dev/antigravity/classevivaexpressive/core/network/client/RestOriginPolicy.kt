package dev.antigravity.classevivaexpressive.core.network.client

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val ClassevivaRestBaseUrl = "https://web.spaggiari.eu/rest/"

/** Exact boundary for requests that may carry Classeviva REST credentials. */
internal data class RestOriginPolicy(
  val scheme: String,
  val host: String,
  val port: Int,
  val pathPrefix: String = "/rest/",
) {
  fun allows(url: HttpUrl): Boolean {
    return url.scheme == scheme &&
      url.host == host &&
      url.port == port &&
      url.encodedPath.startsWith(pathPrefix)
  }

  companion object {
    fun fromBaseUrl(baseUrl: HttpUrl): RestOriginPolicy = RestOriginPolicy(
      scheme = baseUrl.scheme,
      host = baseUrl.host,
      port = baseUrl.port,
      pathPrefix = baseUrl.encodedPath.let { path ->
        if (path.endsWith('/')) path else "$path/"
      },
    )
  }
}

internal val OfficialRestOriginPolicy = RestOriginPolicy(
  scheme = "https",
  host = "web.spaggiari.eu",
  port = 443,
)

internal fun isOfficialRestUrl(value: String?): Boolean {
  return value?.toHttpUrlOrNull()?.let(OfficialRestOriginPolicy::allows) == true
}

/** Public web links may leave the app, but must never be mistaken for authenticated REST assets. */
internal fun isSafeExternalMaterialUrl(value: String?): Boolean {
  val url = value?.toHttpUrlOrNull() ?: return false
  return url.scheme == "https" &&
    url.host.isNotBlank() &&
    url.encodedUsername.isEmpty() &&
    url.encodedPassword.isEmpty() &&
    !OfficialRestOriginPolicy.allows(url)
}
