package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.fluidengine.ai.tools.*
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Args.int
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** One bounded data API across all source areas; refresh is technical and never opens UI. */
class RegistroDatiTool : AiTool<AssistantToolContext> {
  override val name = "registro_dati"
  override val group = RegistroToolGroup.APP
  override val description = "Dati tipizzati paginati del registro, anche storici: cache o refresh tecnico senza cambiare anno/account. Riusa nextCursor con gli stessi filtri; aggregati su tutte le righe corrispondenti."
  override val parameters = Schema.obj(mapOf(
    "sezione" to Schema.str("fonte", SchoolDataQuery.Sections.sorted()),
    "anno" to Schema.str("anno scolastico YYYY-YYYY; default selezione corrente"),
    "account" to Schema.str("username autenticato; altri account non disponibili"),
    "studente" to Schema.str("ID studente autenticato"),
    "da" to Schema.str("inizio ISO yyyy-MM-dd"), "a" to Schema.str("fine ISO yyyy-MM-dd"),
    "materia" to Schema.str("nome esatto della materia"), "periodo" to Schema.str("codice periodo, solo voti"),
    "cerca" to Schema.str("testo da cercare nei record"),
    "cursore" to Schema.str("nextCursor precedente"), "limite" to Schema.int("massimo righe per pagina; le pagine lunghe si leggono in blocchi recuperabili", 1, 100),
    "dettaglio_offset" to Schema.int("offset caratteri della stessa pagina; usa il valore restituito", 0, Int.MAX_VALUE),
    "dettaglio_hash" to Schema.str("hash restituito per riprendere la stessa pagina senza perdita"),
    "aggiorna" to Schema.bool("refresh tecnico esplicito, solo prima pagina"),
  ), required = listOf("sezione"))
  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    ToolArgumentValidator.error(args, parameters)?.let { return failure("invalid_arguments", it) }
    val access = ctx.schoolData ?: return failure("data_unavailable", "accesso dati non disponibile")
    return try {
      val year = args.str("anno")?.let {
        require(Regex("^[0-9]{4}-[0-9]{4}$").matches(it)) { "invalid_year" }
        SchoolYearRef(it.take(4).toInt(), it.takeLast(4).toInt())
      } ?: ctx.schoolYear.observeSelectedSchoolYear().first()
      val query = SchoolDataQuery(args.str("sezione")!!, year, args.str("account"), args.str("studente"),
        args.str("da"), args.str("a"), args.str("materia"), args.str("periodo"), args.str("cursore"),
        args.int("limite") ?: 20, (args["aggiorna"] as? JsonPrimitive)?.booleanOrNull == true, args.str("cerca"))
      val page = access.read(query)
      val raw = page.records.joinToString("\n") { it.toString() }
      val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
      val offset = args.int("dettaglio_offset") ?: 0
      require(offset == 0 || (args.str("dettaglio_hash") == hash && !query.refresh)) { "detail_changed_or_invalid" }
      require(offset == 0 || offset < raw.length) { "invalid_detail_offset" }
      val end = minOf(raw.length, offset + 1500)
      val moreDetail = end < raw.length
      val nextCursor = if (moreDetail) null else page.nextCursor
      val data = buildJsonObject {
        put("records", JsonArray(page.records)); put("aggregate", page.aggregate)
        put("account", page.account); put("student", page.student); put("year", page.year)
        put("section", page.section); put("retrieval", if(page.refreshed) "refreshed" else "cached")
        page.authValid?.let { put("authValid", it) }
        put("returned", page.records.size); put("textSummarized", moreDetail || offset > 0)
      }
      val text = "${page.section}: ${page.records.size} di ${page.total}; aggregati completi=${page.aggregate}.\n" +
        (if (moreDetail) "Pagina suddivisa: ripeti stessi filtri/cursore/limite, aggiorna=false, dettaglio_offset=$end, dettaglio_hash=$hash. Non avanzare pagina prima di completarla.\n"
        else nextCursor?.let { "Prossima pagina: cursore=$it; dettaglio_offset=0 (stessi filtri).\n" } ?: "Fine elenco disponibile.\n") +
        "JSON pagina, caratteri $offset..$end di ${raw.length}:\n" + raw.substring(offset, end)
      ToolOutput(text,
        result = ToolResultDetails(if(page.complete == true && !page.stale && nextCursor == null && !moreDetail) ToolOutcome.COMPLETED else ToolOutcome.PARTIAL,
          data = data, entities = page.records.mapNotNull { row -> (row["id"] as? JsonPrimitive)?.contentOrNull?.let { ToolEntityRef("cv", page.section, it) } },
          sources = listOf(ToolSourceRef("cv:${page.student}:${page.year}:${page.section}", account = page.account)),
          coverage = ToolCoverage(page.complete, page.from, page.to),
          freshness = ToolFreshness(page.observedAt, page.syncedAt, page.stale),
          pagination = ToolPagination(nextCursor, moreDetail || nextCursor != null, page.total.toLong())))
    } catch(e: kotlinx.coroutines.CancellationException) { throw e }
      catch(e: SchoolDataUnavailable) { failure(e.code, e.code, e.retryable) }
      catch(e: java.time.DateTimeException) { failure("invalid_arguments", "data non valida") }
      catch(e: IllegalArgumentException) { failure("invalid_arguments", e.message ?: "argomenti non validi") }
  }
  private fun failure(code: String, message: String, retry: Boolean = false) = ToolOutput("errore: $message",
    result = ToolResultDetails(ToolOutcome.ERROR, error = ToolError(code, message, retry)))
}
