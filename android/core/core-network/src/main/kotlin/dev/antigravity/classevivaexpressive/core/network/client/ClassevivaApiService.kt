package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

internal const val SkipAuthHeader = "X-Skip-Auth"
internal const val SkipRefreshHeader = "X-Skip-Refresh"
internal const val SkipAuthHeaderValue = "$SkipAuthHeader: true"
internal const val SkipRefreshHeaderValue = "$SkipRefreshHeader: true"

data class LoginRequestDto(
  @SerializedName("uid") val uid: String,
  @SerializedName("pass") val password: String,
  @SerializedName("ident") val ident: String? = null,
  @SerializedName("app") val app: String = "CVVS",
  @SerializedName("login") val login: String = "0",
  @SerializedName("multipleToken") val multipleToken: String = "multiple",
)

data class LoginResponseDto(
  @SerializedName("token") val token: String? = null,
  @SerializedName("ident") val ident: String? = null,
  @SerializedName("usrId") val userId: String? = null,
  @SerializedName("userId") val alternateUserId: String? = null,
  @SerializedName("firstName") val firstName: String? = null,
  @SerializedName("lastName") val lastName: String? = null,
)

data class StatusPayloadDto(
  @SerializedName("remains") val remains: Int? = null,
)

data class StatusResponseDto(
  @SerializedName("status") val status: StatusPayloadDto? = null,
)

interface ClassevivaAuthService {
  @POST("v1/auth/login")
  @Headers(SkipAuthHeaderValue, SkipRefreshHeaderValue)
  fun login(@Body body: LoginRequestDto): Call<LoginResponseDto>

  @GET("v1/auth/status")
  @Headers(SkipAuthHeaderValue, SkipRefreshHeaderValue)
  fun checkStatus(@Header("Z-Auth-Token") token: String): Call<StatusResponseDto>
}

interface ClassevivaApiService {
  @GET("v1/students/{id}/card")
  suspend fun getStudentCard(@Path("id") studentId: String): JsonObject

  @GET("v1/students/{id}/grades")
  suspend fun getGrades(@Path("id") studentId: String): JsonObject

  @GET("v1/students/{id}/absences/details")
  suspend fun getAbsences(@Path("id") studentId: String): JsonObject

  @GET("v1/students/{id}/absences/details/{begin}")
  suspend fun getAbsencesFrom(
    @Path("id") studentId: String,
    @Path("begin") begin: String,
  ): JsonObject

  @GET("v1/students/{id}/absences/details/{begin}/{end}")
  suspend fun getAbsencesInRange(
    @Path("id") studentId: String,
    @Path("begin") begin: String,
    @Path("end") end: String,
  ): JsonObject

  @GET("v1/students/{id}/agenda/all/{begin}/{end}")
  suspend fun getAgenda(
    @Path("id") studentId: String,
    @Path("begin") begin: String,
    @Path("end") end: String,
  ): JsonObject

  @GET("v1/students/{id}/lessons/today")
  suspend fun getLessonsToday(@Path("id") studentId: String): JsonObject

  @GET("v1/students/{id}/lessons/{day}")
  suspend fun getLessonsByDay(
    @Path("id") studentId: String,
    @Path("day") day: String,
  ): JsonObject

  @GET("v1/students/{id}/lessons/{start}/{end}")
  suspend fun getLessonsInRange(
    @Path("id") studentId: String,
    @Path("start") start: String,
    @Path("end") end: String,
  ): JsonObject

  @GET("v1/students/{id}/noticeboard")
  suspend fun getNoticeboard(@Path("id") studentId: String): JsonObject

  @POST("v1/students/{id}/noticeboard/read/{evtCode}/{pubId}/101")
  suspend fun readNoticeboard(
    @Path("id") studentId: String,
    @Path("evtCode") evtCode: String,
    @Path("pubId") pubId: String,
    @Body body: JsonObject = JsonObject(),
  ): JsonObject

  @Streaming
  @GET("v1/students/{id}/noticeboard/attach/{evtCode}/{pubId}/101")
  suspend fun downloadNoticeboardAttachment(
    @Path("id") studentId: String,
    @Path("evtCode") evtCode: String,
    @Path("pubId") pubId: String,
  ): ResponseBody

  @GET("v1/students/{id}/notes/all")
  suspend fun getNotes(@Path("id") studentId: String): JsonObject

  @POST("v1/students/{id}/notes/{type}/read/{noteId}")
  suspend fun readNote(
    @Path("id") studentId: String,
    @Path("type") type: String,
    @Path("noteId") noteId: String,
    @Body body: JsonObject = JsonObject(),
  ): JsonObject

  @GET("v1/students/{id}/didactics")
  suspend fun getDidactics(@Path("id") studentId: String): JsonObject

  @Streaming
  @GET("v1/students/{id}/didactics/item/{itemId}")
  suspend fun getDidacticsItem(
    @Path("id") studentId: String,
    @Path("itemId") itemId: String,
  ): ResponseBody

  @GET("v1/students/{id}/periods")
  suspend fun getPeriods(@Path("id") studentId: String): JsonObject

  @GET("v1/students/{id}/subjects")
  suspend fun getSubjects(@Path("id") studentId: String): JsonObject

  @GET("v1/students/{id}/homeworks")
  suspend fun getHomeworks(@Path("id") studentId: String): JsonObject

  @POST("v1/students/{id}/documents")
  suspend fun getDocuments(
    @Path("id") studentId: String,
    @Body body: JsonObject = JsonObject(),
  ): JsonObject

  @POST("v1/students/{id}/documents/check/{documentId}")
  suspend fun checkDocument(
    @Path("id") studentId: String,
    @Path("documentId") documentId: String,
    @Body body: JsonObject = JsonObject(),
  ): JsonObject

  @Streaming
  @GET("v1/students/{id}/documents/read/{documentId}")
  suspend fun readDocument(
    @Path("id") studentId: String,
    @Path("documentId") documentId: String,
  ): ResponseBody

  @GET("v1/students/{id}/schoolbooks")
  suspend fun getSchoolbooks(@Path("id") studentId: String): JsonObject

  @Streaming
  @GET
  suspend fun downloadByUrl(@Url url: String): ResponseBody
}
