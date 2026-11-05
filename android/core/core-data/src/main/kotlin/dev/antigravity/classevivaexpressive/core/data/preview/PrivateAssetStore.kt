package dev.antigravity.classevivaexpressive.core.data.preview

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.classevivaexpressive.core.database.database.AttachmentCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.DownloadRecordDao
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores authenticated school assets in app-private, account-isolated storage. */
@Singleton
class PrivateAssetStore @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val root: File
    get() = File(context.filesDir, RootDirectory)

  suspend fun store(
    studentId: String,
    schoolYearId: String,
    assetId: String,
    displayName: String,
    mimeType: String?,
    input: InputStream,
    validateBeforeCommit: () -> Unit = {},
    admitCommit: suspend (() -> String) -> String = { effect -> effect() },
  ): String = withContext(Dispatchers.IO) {
    require(studentId.isNotBlank()) { "studentId is required for private asset storage" }
    val accountPart = privateAccountPathPart(studentId)
    // Allocation is admitted before copying. Logout can unlink this staging file while the
    // network input is still in flight; no account directory is recreated by an old operation.
    var temporary: File? = null
    var output: java.io.OutputStream? = null
    try {
      admitCommit {
        validateBeforeCommit()
        val staging = File(context.cacheDir, "school_asset_staging").apply { mkdirs() }
        val allocated = File.createTempFile("$accountPart-", ".part", staging)
        temporary = allocated
        // Open under the same admission: an old operation must not reopen a pathname that
        // logout has already unlinked while it was waiting for its network input.
        output = allocated.outputStream().buffered()
        allocated.absolutePath
      }
      input.use { source ->
        checkNotNull(output).use(source::copyTo)
      }
      admitCommit { synchronized(this@PrivateAssetStore) {
        validateBeforeCommit()
        val staged = checkNotNull(temporary)
        val yearDirectory = File(File(root, accountPart), stablePathPart(schoolYearId)).apply { mkdirs() }
        val target = File(yearDirectory, "${stablePathPart(assetId)}-${sanitizeFileName(displayName, mimeType)}")
        if (!target.isFile || target.length() == 0L) {
          check(staged.renameTo(target) || run {
            staged.copyTo(target, overwrite = true)
            staged.delete()
          }) { "Impossibile salvare il file nella cache privata." }
        }
        contentUri(target)
      } }
    } finally {
      runCatching { output?.close() }
      temporary?.delete()
    }
  }

  @Synchronized
  fun find(studentId: String, schoolYearId: String, assetId: String): CachedPrivateAsset? {
    if (studentId.isBlank()) return null
    val yearDirectory = File(File(root, privateAccountPathPart(studentId)), stablePathPart(schoolYearId))
    val prefix = "${stablePathPart(assetId)}-"
    val target = yearDirectory.listFiles()
      ?.filter { file ->
        file.isFile && file.length() > 0L && file.name.startsWith(prefix) && !file.name.endsWith(".part")
      }
      ?.maxByOrNull(File::lastModified)
      ?: return null
    return CachedPrivateAsset(
      contentUri = contentUri(target),
      fileName = target.name.removePrefix(prefix),
    )
  }

  @Synchronized
  fun clearStudent(studentId: String) {
    if (studentId.isBlank()) return
    var deleted = true
    val accountDirectory = File(root, privateAccountPathPart(studentId))
    if (accountDirectory.canonicalFile.parentFile == root.canonicalFile) {
      deleted = accountDirectory.deleteRecursively() && deleted
    }
    File(context.cacheDir, "school_asset_staging").listFiles()
      ?.filter { it.name.startsWith("${privateAccountPathPart(studentId)}-") }
      ?.forEach { deleted = it.delete() && deleted }
    if (!deleted) throw java.io.IOException("Impossibile completare la pulizia dei file privati.")
  }

  @Synchronized
  fun delete(studentId: String, schoolYearId: String, assetId: String) {
    if (studentId.isBlank()) return
    val yearDirectory = File(File(root, privateAccountPathPart(studentId)), stablePathPart(schoolYearId))
    val prefix = "${stablePathPart(assetId)}-"
    yearDirectory.listFiles()
      ?.filter { file -> file.isFile && file.name.startsWith(prefix) }
      ?.forEach(File::delete)
  }

  private fun contentUri(file: File): String = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file,
  ).toString()

  private fun stablePathPart(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
    return digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  private fun sanitizeFileName(displayName: String, mimeType: String?): String {
    val base = displayName.replace(Regex("[^a-zA-Z0-9._-]+"), "_").ifBlank { "asset" }
    if (base.contains('.')) return base
    val extension = when (mimeType?.lowercase()) {
      "application/pdf" -> ".pdf"
      "image/png" -> ".png"
      "image/jpeg" -> ".jpg"
      "image/webp" -> ".webp"
      "text/html" -> ".html"
      "text/plain" -> ".txt"
      else -> ".bin"
    }
    return "$base$extension"
  }

  private companion object {
    const val RootDirectory = "offline_assets"
  }
}

data class CachedPrivateAsset(
  val contentUri: String,
  val fileName: String,
)

@Singleton
class PrivateSessionDataCleaner @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val privateAssetStore: PrivateAssetStore,
  private val attachmentCacheDao: AttachmentCacheDao,
  private val downloadRecordDao: DownloadRecordDao,
  private val downloadManager: DownloadManager,
) {
  suspend fun clear(studentId: String?) {
    runCatching { dev.antigravity.classevivaexpressive.core.data.sync.SyncWorkScheduler.cancel(context) }
    runCatching { context.getSystemService(android.app.NotificationManager::class.java)?.cancelAll() }
    val targetStudentId = studentId?.takeIf(String::isNotBlank) ?: return
    var failure: Exception? = null
    suspend fun attempt(block: suspend () -> Unit) {
      try { block() } catch (error: Exception) {
        if (failure == null) failure = error else failure?.addSuppressed(error)
      }
    }
    val accountPath = privateAccountPathPart(targetStudentId)
    val downloadPrefix = "$targetStudentId::"
    var activeDownloadIds = emptyList<Long>()
    attempt { activeDownloadIds = downloadRecordDao.getByPrefix(downloadPrefix)
      .mapNotNull { record -> record.id.removePrefix(downloadPrefix).toLongOrNull() } }
    if (activeDownloadIds.isNotEmpty()) {
      attempt { downloadManager.remove(*activeDownloadIds.toLongArray()) }
    }
    attempt { privateAssetStore.clearStudent(targetStudentId) }
    attempt {
      if (!File(context.filesDir, "attachment_cache/$accountPath").deleteRecursively()) {
        throw java.io.IOException("Impossibile completare la pulizia degli allegati.")
      }
    }
    attempt {
      if (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.resolve(accountPath)?.deleteRecursively() == false) {
        throw java.io.IOException("Impossibile completare la pulizia dei download.")
      }
    }
    attempt { attachmentCacheDao.deleteByPrefix("$targetStudentId::") }
    attempt { downloadRecordDao.deleteByPrefix(downloadPrefix) }
    failure?.let { throw it }
  }
}

internal fun privateAccountPathPart(studentId: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(studentId.encodeToByteArray())
  return digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
