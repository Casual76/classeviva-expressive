package dev.antigravity.classevivaexpressive.core.assistant.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.ModelCapabilities
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cio' che di un allegato arriva al modello, nella forma migliore che il modello regge. */
sealed interface AttachmentContent {
  /** Il documento intero, per un modello (o un provider) che legge i PDF. */
  class Document(val part: ContentPart.Document, val pages: Int, val sizeLabel: String) : AttachmentContent

  /** Il testo estratto sul telefono, per chi non legge i documenti. */
  class Text(val part: ContentPart.Text, val pages: Int, val truncated: Boolean) : AttachmentContent

  /** Le pagine di una scansione, rasterizzate, per un modello che vede. */
  class Images(val parts: List<ContentPart.Image>, val truncated: Boolean) : AttachmentContent

  class Unreadable(val reason: String) : AttachmentContent
}

/**
 * Legge un allegato scaricato e decide come passarlo al modello: PDF intero se il modello profondo
 * legge i documenti, altrimenti il testo estratto con pdfbox; se il PDF e' una scansione senza testo,
 * le pagine come immagini per un modello che vede; immagini e testi come sono. Un documento troppo
 * grande per la richiesta si riduce al testo.
 */
@Singleton
class AttachmentReader @Inject constructor(@ApplicationContext private val context: Context) {

  private val initialized = AtomicBoolean(false)

  private fun ensurePdfBox() {
    if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
  }

  suspend fun read(path: String, name: String, mime: String?, pages: IntRange?, capabilities: ModelCapabilities): AttachmentContent =
    withContext(Dispatchers.IO) {
      val file = File(path)
      if (!file.exists() || file.length() == 0L) return@withContext AttachmentContent.Unreadable("file non trovato")
      when (kindOf(name, mime)) {
        Kind.PDF -> readPdf(file, name, pages, capabilities)
        Kind.IMAGE -> if (capabilities.vision) {
          AttachmentContent.Images(listOf(ContentPart.Image(file.readBytes(), mimeOf(name, mime))), truncated = false)
        } else {
          AttachmentContent.Unreadable("e' un'immagine e il modello in uso non vede le immagini")
        }
        Kind.TEXT -> {
          val text = file.readText().let { if (mimeOf(name, mime).contains("html")) stripHtml(it) else it }
          AttachmentContent.Text(ContentPart.Text(text.take(MAX_TEXT_CHARS)), pages = 1, truncated = text.length > MAX_TEXT_CHARS)
        }
        Kind.OTHER -> AttachmentContent.Unreadable("formato non supportato (${mimeOf(name, mime)}): si apre solo dall'app")
      }
    }

  private fun readPdf(file: File, name: String, pages: IntRange?, capabilities: ModelCapabilities): AttachmentContent {
    val bytes = file.readBytes()
    val pageCount = runCatching { countPages(file) }.getOrDefault(0)
    if (capabilities.documents && bytes.size <= MAX_DOCUMENT_BYTES && pages == null) {
      return AttachmentContent.Document(ContentPart.Document(bytes, "application/pdf", name), pageCount, sizeLabel(bytes.size))
    }
    val text = runCatching { extractText(file, pages) }.getOrDefault("")
    if (text.isNotBlank()) {
      return AttachmentContent.Text(ContentPart.Text(text.take(MAX_TEXT_CHARS)), pageCount, truncated = text.length > MAX_TEXT_CHARS)
    }
    if (capabilities.vision) {
      val images = runCatching { renderPages(file, pages) }.getOrDefault(emptyList())
      if (images.isNotEmpty()) {
        return AttachmentContent.Images(images.map { ContentPart.Image(it, "image/jpeg") }, truncated = pageCount > images.size)
      }
    }
    return AttachmentContent.Unreadable(
      if (pageCount > 0) "e' una scansione senza testo e il modello in uso non vede le immagini" else "il PDF non si legge",
    )
  }

  fun extractText(file: File, pages: IntRange?): String {
    ensurePdfBox()
    PDDocument.load(file).use { document ->
      val stripper = PDFTextStripper()
      stripper.sortByPosition = true
      val total = document.numberOfPages
      val first = (pages?.first ?: 1).coerceIn(1, maxOf(1, total))
      val last = (pages?.last ?: (first + MAX_TEXT_PAGES - 1)).coerceIn(first, maxOf(1, total))
      stripper.startPage = first
      stripper.endPage = last
      return stripper.getText(document).replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }
  }

  private fun countPages(file: File): Int {
    ensurePdfBox()
    return PDDocument.load(file).use { it.numberOfPages }
  }

  fun renderPages(file: File, pages: IntRange?): List<ByteArray> {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
      PdfRenderer(descriptor).use { renderer ->
        val total = renderer.pageCount
        val first = ((pages?.first ?: 1) - 1).coerceIn(0, maxOf(0, total - 1))
        val last = minOf(total - 1, (pages?.last?.minus(1) ?: (first + MAX_IMAGE_PAGES - 1)), first + MAX_IMAGE_PAGES - 1)
        return (first..last).map { index ->
          renderer.openPage(index).use { page ->
            val scale = RENDER_WIDTH.toFloat() / page.width
            val width = RENDER_WIDTH
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ByteArrayOutputStream().use { out ->
              bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
              bitmap.recycle()
              out.toByteArray()
            }
          }
        }
      }
    }
  }

  private enum class Kind { PDF, IMAGE, TEXT, OTHER }

  private fun kindOf(name: String, mime: String?): Kind {
    val m = mimeOf(name, mime)
    return when {
      m == "application/pdf" -> Kind.PDF
      m.startsWith("image/") -> Kind.IMAGE
      m.startsWith("text/") -> Kind.TEXT
      else -> Kind.OTHER
    }
  }

  fun mimeOf(name: String, mime: String?): String {
    mime?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "application/octet-stream" }?.let { return it }
    return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
      "pdf" -> "application/pdf"
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      "gif" -> "image/gif"
      "txt" -> "text/plain"
      "htm", "html" -> "text/html"
      "csv" -> "text/csv"
      "md" -> "text/markdown"
      "doc" -> "application/msword"
      "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      "xls" -> "application/vnd.ms-excel"
      "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      else -> "application/octet-stream"
    }
  }

  private fun stripHtml(html: String): String =
    html.replace(Regex("<(script|style)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), " ")
      .replace(Regex("<br\\s*/?>|</p>|</div>|</li>|</tr>|</h\\d>"), "\n")
      .replace(Regex("<[^>]+>"), " ")
      .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
      .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n\n").trim()

  private fun sizeLabel(bytes: Int): String = when {
    bytes >= 1_000_000 -> String.format(Locale.ITALIAN, "%.1f MB", bytes / 1_000_000.0)
    else -> "${bytes / 1_000} kB"
  }

  companion object {
    /** Oltre questo il PDF non viaggia intero: si passa al testo. Gemini accetta 20 MB di richiesta, ma il base64 pesa un terzo in piu'. */
    const val MAX_DOCUMENT_BYTES = 6 * 1024 * 1024
    const val MAX_TEXT_CHARS = 12_000
    const val MAX_TEXT_PAGES = 8
    const val MAX_IMAGE_PAGES = 4
    const val RENDER_WIDTH = 1024
    const val JPEG_QUALITY = 72
  }
}
