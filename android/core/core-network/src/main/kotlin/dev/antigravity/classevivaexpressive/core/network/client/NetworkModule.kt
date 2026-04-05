package dev.antigravity.classevivaexpressive.core.network.client

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.datastore.SessionStorage
import dev.antigravity.classevivaexpressive.core.network.BuildConfig
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val ApiBaseUrl = "https://web.spaggiari.eu/rest/"
private const val GatewayFallbackBaseUrl = "http://127.0.0.1/"
const val DevApiKey = "Tg1NWEwNGIgIC0K"
const val UserAgent = "CVVS/std/4.1.7 Android/10"

private class ClassevivaHeadersInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request().newBuilder()
      .header("User-Agent", UserAgent)
      .header("Z-Dev-ApiKey", DevApiKey)
      .header("Content-Type", "application/json")
      .build()
    return chain.proceed(request)
  }
}

private class AuthTokenInterceptor(
  private val sessionStore: SessionStorage,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()
    if (original.header(SkipAuthHeader) == "true") {
      return chain.proceed(original)
    }

    val token = sessionStore.readCurrentSession()?.token
    val request = original.newBuilder().apply {
      token?.takeIf(String::isNotBlank)?.let { header("Z-Auth-Token", it) }
    }.build()
    return chain.proceed(request)
  }
}

private class GatewayHeadersInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request().newBuilder()
      .header("Content-Type", "application/json")
      .build()
    return chain.proceed(request)
  }
}

private class SessionAuthenticator(
  private val apiSessionManager: ApiSessionManager,
) : Authenticator {
  override fun authenticate(route: Route?, response: Response): Request? {
    if (response.request.header(SkipRefreshHeader) == "true") return null
    if (responseCount(response) >= 2) return null

    val failedToken = response.request.header("Z-Auth-Token")
    val refreshedToken = apiSessionManager.refreshTokenBlocking(failedToken = failedToken) ?: return null
    if (refreshedToken == failedToken) return null

    return response.request.newBuilder()
      .header("Z-Auth-Token", refreshedToken)
      .build()
  }

  private fun responseCount(response: Response): Int {
    var result = 1
    var cursor = response.priorResponse
    while (cursor != null) {
      result += 1
      cursor = cursor.priorResponse
    }
    return result
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
  fun provideGson(): Gson = GsonBuilder().create()

  @Provides
  @Singleton
  fun provideLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }
  }

  @Provides
  @Singleton
  fun provideHeadersInterceptor(): Interceptor = ClassevivaHeadersInterceptor()

  @Provides
  @Singleton
  @Named("gatewayHeaders")
  fun provideGatewayHeadersInterceptor(): Interceptor = GatewayHeadersInterceptor()

  @Provides
  @Singleton
  @Named("authTokenInterceptor")
  fun provideAuthTokenInterceptor(sessionStore: SessionStorage): Interceptor {
    return AuthTokenInterceptor(sessionStore)
  }

  @Provides
  @Singleton
  fun provideSessionAuthenticator(apiSessionManager: ApiSessionManager): Authenticator {
    return SessionAuthenticator(apiSessionManager)
  }

  @Provides
  @Singleton
  @Named("authOkHttp")
  fun provideAuthHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    headersInterceptor: Interceptor,
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(headersInterceptor)
      .addInterceptor(loggingInterceptor)
      .build()
  }

  @Provides
  @Singleton
  @Named("apiOkHttp")
  fun provideApiHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    headersInterceptor: Interceptor,
    @Named("authTokenInterceptor") authTokenInterceptor: Interceptor,
    authenticator: Authenticator,
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(authTokenInterceptor)
      .addInterceptor(headersInterceptor)
      .authenticator(authenticator)
      .addInterceptor(loggingInterceptor)
      .build()
  }

  @Provides
  @Singleton
  @Named("gatewayOkHttp")
  fun provideGatewayHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    @Named("gatewayHeaders") gatewayHeadersInterceptor: Interceptor,
  ): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(gatewayHeadersInterceptor)
      .addInterceptor(loggingInterceptor)
      .build()
  }

  @Provides
  @Singleton
  @Named("authRetrofit")
  fun provideAuthRetrofit(
    gson: Gson,
    @Named("authOkHttp") client: OkHttpClient,
  ): Retrofit {
    return Retrofit.Builder()
      .baseUrl(ApiBaseUrl)
      .client(client)
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build()
  }

  @Provides
  @Singleton
  @Named("apiRetrofit")
  fun provideApiRetrofit(
    gson: Gson,
    @Named("apiOkHttp") client: OkHttpClient,
  ): Retrofit {
    return Retrofit.Builder()
      .baseUrl(ApiBaseUrl)
      .client(client)
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build()
  }

  @Provides
  @Singleton
  @Named("gatewayRetrofit")
  fun provideGatewayRetrofit(
    gson: Gson,
    @Named("gatewayOkHttp") client: OkHttpClient,
  ): Retrofit {
    val baseUrl = BuildConfig.GATEWAY_BASE_URL.takeIf { it.isNotBlank() } ?: GatewayFallbackBaseUrl
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build()
  }

  @Provides
  @Singleton
  @Named("authService")
  fun provideAuthService(@Named("authRetrofit") retrofit: Retrofit): ClassevivaAuthService {
    return retrofit.create(ClassevivaAuthService::class.java)
  }

  @Provides
  @Singleton
  fun provideApiService(@Named("apiRetrofit") retrofit: Retrofit): ClassevivaApiService {
    return retrofit.create(ClassevivaApiService::class.java)
  }

  @Provides
  @Singleton
  fun provideGatewayService(@Named("gatewayRetrofit") retrofit: Retrofit): ClassevivaGatewayService {
    return retrofit.create(ClassevivaGatewayService::class.java)
  }
}
