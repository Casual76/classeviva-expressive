package dev.antigravity.classevivaexpressive.core.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val ClassevivaManifestUrl =
  "https://raw.githubusercontent.com/Casual76/classeviva-expressive/main/manifest.json"
private const val GithubApiBaseUrl = "https://api.github.com"
private const val UserAgent = "ClassevivaExpressiveUpdater/5.7.0"

@Singleton
class PampaAppUpdateRepository @Inject constructor(
  private val remoteDataSource: PampaUpdateRemoteDataSource,
  private val installer: AppUpdateInstaller,
) : AppUpdateRepository {
  override suspend fun checkForStableUpdate(
    currentVersionName: String,
    ignoredVersion: String,
  ): Result<AvailableAppUpdate?> = runCatching {
    val update = remoteDataSource.fetchStableUpdate()
    when {
      update.version == ignoredVersion -> null
      !isStableVersionNewer(update.version, currentVersionName) -> null
      else -> update
    }
  }

  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> = installer.install(update)
}

interface PampaUpdateRemoteDataSource {
  suspend fun fetchStableUpdate(): AvailableAppUpdate
}

@Singleton
class HttpPampaUpdateRemoteDataSource @Inject constructor(
  private val json: Json,
) : PampaUpdateRemoteDataSource {
  override suspend fun fetchStableUpdate(): AvailableAppUpdate {
    val manifest = json.decodeFromString<PampaAppManifest>(readText(ClassevivaManifestUrl))
    val app = manifest.app
    val stable = app.stable ?: error("Nessuna release stable disponibile.")
    val repository = app.repository
    val downloadUrl = "https://github.com/${repository.repoOwner}/${repository.repoName}/releases/download/${stable.releaseTag}/${stable.apkAsset}"
    
    return AvailableAppUpdate(
      version = stable.version,
      changelog = stable.changelog,
      releaseTag = stable.releaseTag,
      apkAsset = stable.apkAsset,
      downloadUrl = downloadUrl,
      sizeBytes = stable.sizeBytes,
    )
  }

  private suspend fun readText(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 25_000
      instanceFollowRedirects = true
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", UserAgent)
    }
    try {
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (code !in 200..299) {
        error("Richiesta aggiornamenti fallita ($code).")
      }
      body
    } finally {
      connection.disconnect()
    }
  }

  private fun githubReleaseUrl(owner: String, repo: String, tag: String): String {
    val encodedTag = URLEncoder.encode(tag, Charsets.UTF_8.name()).replace("+", "%20")
    return "$GithubApiBaseUrl/repos/$owner/$repo/releases/tags/$encodedTag"
  }
}

interface AppUpdateInstaller {
  fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>
}

@Singleton
class AndroidAppUpdateInstaller @Inject constructor(
  @ApplicationContext private val context: Context,
) : AppUpdateInstaller {
  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> = channelFlow {
    send(AppUpdateInstallState.Verifying("Preparazione aggiornamento..."))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
      val permissionIntent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(permissionIntent)
      send(
        AppUpdateInstallState.Error(
          "Abilita l'installazione da origini sconosciute per Classeviva Expressive e riprova.",
        ),
      )
      return@channelFlow
    }

    val apkFile = runCatching {
      downloadApk(update) { progress, downloaded, total ->
        trySend(AppUpdateInstallState.Downloading(progress, downloaded, total))
      }
    }.getOrElse { error ->
      send(AppUpdateInstallState.Error(error.message ?: "Download aggiornamento non riuscito."))
      return@channelFlow
    }

    send(AppUpdateInstallState.Verifying("Verifica APK..."))
    val packageInfo = context.packageManager.readArchiveInfo(apkFile.absolutePath)
    if (packageInfo == null) {
      send(AppUpdateInstallState.Error("Android non riesce a leggere l'APK scaricato."))
      return@channelFlow
    }
    if (packageInfo.packageName != context.packageName) {
      send(AppUpdateInstallState.Error("L'APK scaricato non appartiene a questa app."))
      return@channelFlow
    }
    val apkVersion = packageInfo.versionName.orEmpty()
    if (apkVersion.isNotBlank() && apkVersion != update.version) {
      send(AppUpdateInstallState.Error("Versione APK inattesa: $apkVersion."))
      return@channelFlow
    }

    runCatching {
      commitPackageInstallerSession(apkFile, packageInfo) { state -> send(state) }
    }.onFailure { error ->
      send(AppUpdateInstallState.Error(error.message ?: "Installazione aggiornamento non riuscita."))
    }
  }

  private suspend fun downloadApk(
    update: AvailableAppUpdate,
    onProgress: (Float, Long, Long) -> Unit,
  ): File = withContext(Dispatchers.IO) {
    val updateDir = File(context.cacheDir, "app_updates").apply { mkdirs() }
    val target = File(updateDir, update.apkAsset.ifBlank { "classeviva-expressive-${update.version}.apk" })
    val temp = File(updateDir, "${target.name}.part")
    temp.delete()

    var targetDownloadUrl = update.downloadUrl
    try {
      // Attempt to use the API to get the asset URL directly (faster routing)
      val apiUrl = "https://api.github.com/repos/Casual76/classeviva-expressive/releases/tags/${update.releaseTag}"
      val apiConn = URL(apiUrl).openConnection() as HttpURLConnection
      apiConn.setRequestProperty("User-Agent", "ClassevivaExpressiveUpdater")
      if (apiConn.responseCode in 200..299) {
        val response = apiConn.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(response)
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          if (asset.getString("name") == update.apkAsset) {
            val directUrl = asset.optString("url", "")
            if (directUrl.isNotBlank()) {
              targetDownloadUrl = directUrl
              break
            }
          }
        }
      }
      apiConn.disconnect()
    } catch (e: Exception) {
      // Fallback to the manual downloadUrl
    }

    var currentUrl = targetDownloadUrl
    var connection: HttpURLConnection? = null
    try {
      var redirects = 0
      while (true) {
        connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 15_000
          readTimeout = 60_000
          instanceFollowRedirects = false
          setRequestProperty("User-Agent", UserAgent)
          setRequestProperty("Accept", "application/octet-stream")
        }
        val code = connection.responseCode
        if (code in 300..399) {
          val location = connection.getHeaderField("Location")
          if (location != null && redirects < 5) {
            currentUrl = location
            redirects++
            connection.disconnect()
            continue
          }
        }
        if (code !in 200..299) {
          error("Download APK fallito ($code).")
        }
        break
      }

      val finalConnection = connection!!
      val total = finalConnection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
      finalConnection.inputStream.use { input ->
        temp.outputStream().use { output ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var downloaded = 0L
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read
            val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
            onProgress(progress.coerceIn(0f, 1f), downloaded, total)
          }
        }
      }
      if (target.exists()) target.delete()
      if (!temp.renameTo(target)) {
        error("Impossibile finalizzare l'APK scaricato.")
      }
      target
    } finally {
      connection?.disconnect()
      if (temp.exists()) temp.delete()
    }
  }

  private suspend fun commitPackageInstallerSession(
    file: File,
    packageInfo: PackageInfo,
    emitState: suspend (AppUpdateInstallState) -> Unit,
  ) {
    val packageInstaller = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName(packageInfo.packageName)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
      }
    }
    val sessionId = try {
      packageInstaller.createSession(params)
    } catch (exception: Exception) {
      throw IllegalStateException(
        exception.message ?: "Impossibile avviare la sessione di installazione.",
        exception,
      )
    }

    val events = AppUpdateInstallSessionRegistry.register(sessionId)
    try {
      emitState(AppUpdateInstallState.Installing("Installazione aggiornamento..."))
      withContext(Dispatchers.IO) {
        packageInstaller.openSession(sessionId).use { session ->
          file.inputStream().use { input ->
            session.openWrite(file.name, 0, file.length()).use { output ->
              input.copyTo(output)
              session.fsync(output)
            }
          }
          val callbackIntent = Intent(context, AppUpdateInstallResultReceiver::class.java).apply {
            action = AppUpdateInstallResultReceiver.ACTION_INSTALL_STATUS
            putExtra(AppUpdateInstallResultReceiver.EXTRA_SESSION_ID, sessionId)
          }
          val flags = PendingIntentFlags.mutableUpdateCurrent()
          val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            sessionId,
            callbackIntent,
            flags,
          )
          session.commit(pendingIntent.intentSender)
        }
      }
    } catch (error: Exception) {
      runCatching { packageInstaller.abandonSession(sessionId) }
      AppUpdateInstallSessionRegistry.unregister(sessionId)
      throw error
    }

    var terminalEvent: AppUpdateInstallState? = null
    events.takeWhile { event ->
      val keepCollecting = event !is AppUpdateInstallState.Installed && event !is AppUpdateInstallState.Error
      if (!keepCollecting) terminalEvent = event
      keepCollecting
    }.collect { emitState(it) }
    terminalEvent?.let { event ->
      emitState(
        if (event is AppUpdateInstallState.Installed && event.filePath.isBlank()) {
          event.copy(filePath = file.absolutePath)
        } else {
          event
        },
      )
    }
  }
}

class AppUpdateInstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val sessionId = intent.getIntExtra(
      AppUpdateInstallResultReceiver.EXTRA_SESSION_ID,
      intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
    )
    if (sessionId == -1) return

    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        val confirmationIntent = intent.parcelableIntent(Intent.EXTRA_INTENT)
        if (confirmationIntent != null) {
          confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(confirmationIntent)
        }
        AppUpdateInstallSessionRegistry.tryEmit(
          sessionId,
          AppUpdateInstallState.AwaitingUserAction("Conferma l'installazione sul dispositivo."),
        )
      }
      PackageInstaller.STATUS_SUCCESS -> {
        AppUpdateInstallSessionRegistry.tryEmit(
          sessionId,
          AppUpdateInstallState.Installed(""),
        )
      }
      else -> {
        AppUpdateInstallSessionRegistry.tryEmit(
          sessionId,
          AppUpdateInstallState.Error(status.toInstallFailureMessage(statusMessage)),
        )
      }
    }
  }

  companion object {
    const val ACTION_INSTALL_STATUS =
      "dev.antigravity.classevivaexpressive.action.APP_UPDATE_INSTALL_STATUS"
    const val EXTRA_SESSION_ID =
      "dev.antigravity.classevivaexpressive.extra.APP_UPDATE_INSTALL_SESSION_ID"
  }
}

object AppUpdateInstallSessionRegistry {
  private val sessionEvents = ConcurrentHashMap<Int, MutableSharedFlow<AppUpdateInstallState>>()

  fun register(sessionId: Int): SharedFlow<AppUpdateInstallState> {
    val flow = MutableSharedFlow<AppUpdateInstallState>(replay = 8, extraBufferCapacity = 8)
    sessionEvents[sessionId] = flow
    return flow.asSharedFlow()
  }

  fun unregister(sessionId: Int) {
    sessionEvents.remove(sessionId)
  }

  fun tryEmit(sessionId: Int, event: AppUpdateInstallState) {
    sessionEvents[sessionId]?.tryEmit(event)
    if (event is AppUpdateInstallState.Installed || event is AppUpdateInstallState.Error) {
      unregister(sessionId)
    }
  }
}

internal fun isStableVersionNewer(candidate: String, current: String): Boolean {
  if (candidate.contains("-")) return false
  return compareAppVersions(candidate, current) > 0
}

internal fun compareAppVersions(left: String, right: String): Int {
  val leftParts = left.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
  val rightParts = right.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
  val max = maxOf(leftParts.size, rightParts.size)
  for (index in 0 until max) {
    val leftValue = leftParts.getOrElse(index) { 0 }
    val rightValue = rightParts.getOrElse(index) { 0 }
    if (leftValue != rightValue) return leftValue.compareTo(rightValue)
  }
  val leftPreRelease = left.substringAfter("-", "")
  val rightPreRelease = right.substringAfter("-", "")
  return when {
    leftPreRelease.isBlank() && rightPreRelease.isNotBlank() -> 1
    leftPreRelease.isNotBlank() && rightPreRelease.isBlank() -> -1
    else -> leftPreRelease.compareTo(rightPreRelease)
  }
}

@Suppress("DEPRECATION")
private fun PackageManager.readArchiveInfo(filePath: String): PackageInfo? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getPackageArchiveInfo(filePath, PackageManager.PackageInfoFlags.of(0))
  } else {
    getPackageArchiveInfo(filePath, 0)
  }
}

private fun Int.toInstallFailureMessage(systemMessage: String): String = when (this) {
  PackageInstaller.STATUS_FAILURE_ABORTED -> "Installazione annullata dall'utente."
  PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android ha bloccato l'installazione dell'APK."
  PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflitto di firma o versione con l'app installata."
  PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "L'APK non e' compatibile con questo dispositivo."
  PackageInstaller.STATUS_FAILURE_INVALID -> "Android considera l'APK non valido o non installabile."
  PackageInstaller.STATUS_FAILURE_STORAGE -> "Spazio insufficiente per completare l'installazione."
  else -> systemMessage.ifBlank { "Installazione non riuscita." }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableIntent(key: String): Intent? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(key, Intent::class.java)
  } else {
    getParcelableExtra(key) as? Intent
  }
}

private object PendingIntentFlags {
  fun mutableUpdateCurrent(): Int {
    return android.app.PendingIntent.FLAG_UPDATE_CURRENT or
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0
  }
}

@Serializable
private data class PampaAppManifest(
  val app: PampaApp,
)

@Serializable
private data class PampaApp(
  val repository: PampaRepository,
  val stable: PampaVersion? = null,
)

@Serializable
private data class PampaRepository(
  val repoOwner: String,
  val repoName: String,
)

@Serializable
private data class PampaVersion(
  val version: String,
  val changelog: String = "",
  val releaseTag: String,
  val apkAsset: String = "",
  val sizeBytes: Long = 0L,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
  @Binds abstract fun bindAppUpdateRepository(impl: PampaAppUpdateRepository): AppUpdateRepository
  @Binds abstract fun bindPampaUpdateRemoteDataSource(impl: HttpPampaUpdateRemoteDataSource): PampaUpdateRemoteDataSource
  @Binds abstract fun bindAppUpdateInstaller(impl: AndroidAppUpdateInstaller): AppUpdateInstaller
}
