package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import java.time.OffsetDateTime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/** A candidate is not published until its complete encrypted snapshot has been committed. */
class CieLoginResult(
  val token: String,
  val studentId: String,
  val ident: String,
  val pa: String,
  val refreshToken: String?,
  val expiresAtEpochMillis: Long,
  val profileHint: StudentProfile,
) {
  override fun toString(): String = "CieLoginResult([redacted])"
}

internal object CieProtocol {
  const val RefreshHeader = "Z-Spid-Rauth"
  private val StudentIdentity = Regex("^S([0-9]+)[A-Z]?$")

  /** The identity prefix is a role, not decoration to strip from a parent's identifier. */
  fun studentId(ident: String?): String? = ident?.let { StudentIdentity.matchEntire(it)?.groupValues?.get(1) }

  fun loginBody(pa: String): RequestBody = JsonObject().apply {
    addProperty("pa", pa)
    add("rf", JsonNull.INSTANCE)
  }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

  fun refreshBody(pa: String, refresh: String): RequestBody = JsonObject().apply {
    addProperty("pa", pa)
    addProperty("rf", refresh)
  }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

  fun expiresAt(value: String?): Long? = value?.let {
    runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
  }

  fun loginResult(response: Response<LoginResponseDto>, pa: String): CieLoginResult {
    if (!response.isSuccessful) throw ClassevivaNetworkException("Accesso CIE non riuscito (HTTP ${response.code()}).")
    val body = response.body() ?: throw ClassevivaNetworkException("Risposta CIE non valida. Ripeti l'accesso.")
    val token = body.token?.takeIf(String::isNotBlank)
      ?: throw ClassevivaNetworkException("Risposta CIE senza token. Ripeti l'accesso.")
    val ident = body.ident.orEmpty()
    val studentId = studentId(ident)
      ?: throw ClassevivaNetworkException("Questo profilo CIE non è un profilo studente supportato.")
    val expiresAt = expiresAt(body.expiresAt)
      ?: throw ClassevivaNetworkException("Risposta CIE senza scadenza valida. Ripeti l'accesso.")
    return CieLoginResult(
      token = token,
      studentId = studentId,
      ident = ident,
      pa = pa,
      refreshToken = response.headers()[RefreshHeader]?.takeIf(String::isNotBlank),
      expiresAtEpochMillis = expiresAt,
      profileHint = StudentProfile(id = studentId, name = body.firstName.orEmpty(), surname = body.lastName.orEmpty()),
    )
  }

  /** Only known protocol codes influence state; never expose server text or token fragments. */
  fun errorCode(text: String): String? {
    val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return null
    val allowed = setOf("PATokenNotYetExpired", "RFTokenNotValid", "InvalidPAToken")
    return sequenceOf("code", "error", "errorCode", "info").mapNotNull { key ->
      runCatching { root.get(key)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()
    }.firstOrNull { it in allowed }
  }
}
