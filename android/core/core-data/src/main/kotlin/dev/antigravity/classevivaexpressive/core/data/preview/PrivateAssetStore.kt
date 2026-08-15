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

/** Stores authenticated school assets in app-private, account-isolated storage. */
@Singleton
class PrivateAssetStore @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  private val root: File
    get() = File(context.filesDir, RootDirectory)

  @Synchronized
  fun store(
    studentId: String,
    schoolYearId: String,
    assetId: String,
    displayName: String,
    mimeType: String?,
    input: InputStream,
    validateBeforeCommit: () -> Unit = {},
  ): String {
    require(studentId.isNotBlank()) { "studentId is required for private asset storage" }
    val accountDirectory = File(root, privateAccountPathPart(studentId))
    val yearDirectory = File(accountDirectory, stablePathPart(schoolYearId)).apply { mkdirs() }
    val target = File(
      yearDirectory,
      "${stablePathPart(assetId)}-${sanitizeFileName(displayName, mimeType)}",
    )
    validateBeforeCommit()
    if (target.isFile && target.length() > 0L) return contentUri(target)
    val temporary = File(yearDirectory, "${target.name}.part")
    try {
      input.use { source ->
        temporary.outputStream().buffered().use(source::copyTo)
      }
      validateBeforeCommit()
      check(temporary.renameTo(target) || run {
        temporary.copyTo(target, overwrite = true)
        temporary.delete()
      }) { "Impossibile salvare il file nella cache privata." }
      return contentUri(target)
    } finally {
      temporary.delete()
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

  fun clearStudent(studentId: String) {
    if (studentId.isBlank()) return
    val accountDirectory = File(root, privateAccountPathPart(studentId))
    if (accountDirectory.canonicalFile.parentFile == root.canonicalFile) {
      accountDirectory.deleteRecursively()
    }
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
    val targetStudentId = studentId?.takeIf(String::isNotBlank) ?: return
    val accountPath = privateAccountPathPart(targetStudentId)
    val downloadPrefix = "$targetStudentId::"
    val activeDownloadIds = downloadRecordDao.getByPrefix(downloadPrefix)
      .mapNotNull { record -> record.id.removePrefix(downloadPrefix).toLongOrNull() }
    if (activeDownloadIds.isNotEmpty()) {
      downloadManager.remove(*activeDownloadIds.toLongArray())
    }
    privateAssetStore.clearStudent(targetStudentId)
    File(context.filesDir, "attachment_cache/$accountPath").deleteRecursively()
    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
      ?.resolve(accountPath)
      ?.deleteRecursively()
    attachmentCacheDao.deleteByPrefix("$targetStudentId::")
    downloadRecordDao.deleteByPrefix(downloadPrefix)
  }
}

internal fun privateAccountPathPart(studentId: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(studentId.encodeToByteArray())
  return digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
