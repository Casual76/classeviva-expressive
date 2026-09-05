package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.DocumentKind
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

class MaterialiCercaTool : AiTool<AssistantToolContext> {
  override val name = "materiali_cerca"
  override val group: AiToolGroup = RegistroToolGroup.DIDATTICA
  override val description = "I materiali didattici condivisi dai docenti (file, link, cartelle), cercando per titolo, cartella o docente; dal piu' recente"
  override val parameters = Schema.obj(mapOf("testo" to Schema.str("parole nel titolo, nella cartella o nel nome del docente; vuoto per gli ultimi")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val query = args.str("testo")
    val items = ctx.materials.observeMaterials().first()
      .filter { query == null || Text.matches(query, it.title) || Text.matches(query, it.folderName) || Text.matches(query, it.teacherName) }
      .sortedByDescending { it.sharedAt }
    if (items.isEmpty()) return ToolOutput("nessun materiale didattico" + (query?.let { " per \"$it\"" } ?: ""))
    return ToolText.output {
      line("materiali", items.size)
      items.take(20).forEach { m ->
        line("${Dates.label(m.sharedAt)} · ${m.teacherName} · ${m.folderName} · ${Text.clip(m.title, 80)} · ${m.objectType}${if (m.attachments.isNotEmpty()) " · ${m.attachments.size} file" else ""}")
      }
      if (items.size > 20) line("… altri ${items.size - 20}")
    }
  }
}

class DocumentiTool : AiTool<AssistantToolContext> {
  override val name = "documenti"
  override val group: AiToolGroup = RegistroToolGroup.DIDATTICA
  override val description = "I documenti della scuola per lo studente: pagelle, certificati e simili (i titoli; si aprono dall'app)"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val documents = ctx.documents.observeDocuments().first()
    if (documents.isEmpty()) return ToolOutput("nessun documento disponibile")
    return ToolText.output {
      documents.forEach { d ->
        line("${if (d.kind == DocumentKind.SCHOOL_REPORT) "pagella" else "documento"} · ${d.title}${if (d.detail.isNotBlank()) " · ${Text.clip(d.detail, 80)}" else ""}")
      }
    }
  }
}

class LibriDiTestoTool : AiTool<AssistantToolContext> {
  override val name = "libri_di_testo"
  override val group: AiToolGroup = RegistroToolGroup.DIDATTICA
  override val description = "I libri di testo adottati, per materia, con autore, editore, ISBN e se sono da comprare"
  override val parameters = Schema.obj(mapOf("materia" to Schema.str("nome della materia; vuoto per tutti")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val courses = ctx.documents.observeSchoolbooks().first()
    val books = courses.flatMap { it.books }
    if (books.isEmpty()) return ToolOutput("nessun libro di testo nel registro")
    val subjectArg = args.str("materia")
    val subject = Subjects.match(subjectArg, books.map { it.subject })
    if (subjectArg != null && subject == null) return ToolOutput.error("materia \"$subjectArg\" non trovata fra i libri")
    val chosen = books.filter { subject == null || Text.normalize(it.subject) == Text.normalize(subject) }
    return ToolText.output {
      line("libri", chosen.size)
      chosen.take(30).forEach { b ->
        line(
          "${b.subject} · ${b.title}${b.volume?.let { " $it" } ?: ""}${b.author?.let { " · $it" } ?: ""}${b.publisher?.let { " · $it" } ?: ""}" +
            (b.price?.let { " · ${String.format(java.util.Locale.ITALIAN, "%.2f", it)} €" } ?: "") +
            (if (b.toBuy) " · da comprare" else "") + (if (b.newAdoption) " · nuova adozione" else "") + " · ISBN ${b.isbn}",
        )
      }
    }
  }
}
