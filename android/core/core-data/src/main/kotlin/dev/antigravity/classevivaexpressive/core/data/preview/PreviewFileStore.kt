package dev.antigravity.classevivaexpressive.core.data.preview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Base64

fun decodePreviewContent(base64Content: String?): ByteArray? {
  val payload = base64Content?.takeIf { it.isNotBlank() } ?: return null
  return runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
}

fun openPreviewFile(
  context: Context,
  displayName: String,
  mimeType: String?,
  bytes: ByteArray,
): Result<Uri> = runCatching {
  val previewDir = File(context.cacheDir, "preview").apply { mkdirs() }
  val target = File(previewDir, sanitizePreviewName(displayName, mimeType))
  target.writeBytes(bytes)
  val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    target,
  )
  val intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(uri, mimeType ?: "*/*")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    context.startActivity(intent)
  } catch (error: ActivityNotFoundException) {
    throw IllegalStateException("Nessuna app compatibile per aprire questo file.", error)
  }
  uri
}

private fun sanitizePreviewName(displayName: String, mimeType: String?): String {
  val base = displayName.replace(Regex("[^a-zA-Z0-9._-]+"), "_").ifBlank { "preview" }
  return when {
    base.contains('.') -> base
    else -> "$base${extensionForMime(mimeType)}"
  }
}

private fun extensionForMime(mimeType: String?): String {
  return when (mimeType?.lowercase()) {
    "application/pdf" -> ".pdf"
    "image/png" -> ".png"
    "image/jpeg" -> ".jpg"
    "image/webp" -> ".webp"
    "text/html" -> ".html"
    "text/plain" -> ".txt"
    else -> ".bin"
  }
}
