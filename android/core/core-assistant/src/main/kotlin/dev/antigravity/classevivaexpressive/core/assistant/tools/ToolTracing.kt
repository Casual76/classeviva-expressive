package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * Una chiamata a uno strumento com'e' andata davvero: cosa ha chiesto il modello, quanto ci ha
 * messo, se e' finita bene e come cominciava la risposta. E' quello che serve quando una risposta
 * e' sbagliata: "uno strumento ha fallito" non basta a capire quale, perche', e con che argomenti.
 */
data class AssistantToolTrace(
  val name: String,
  val args: String,
  val millis: Long,
  val ok: Boolean,
  val chars: Int,
  val preview: String,
)

/**
 * Avvolge uno strumento e annota ogni chiamata nel contesto della domanda. Le tracce finiscono
 * nella telemetria dello scambio e si leggono nella conversazione, sul telefono: la diagnosi di
 * una risposta storta non deve richiedere un cavo.
 */
class TracedTool(private val inner: AiTool<AssistantToolContext>) : AiTool<AssistantToolContext> by inner {

  private val sourceSection = mapOf("voti_elenco" to "grades", "voti_media" to "grades", "impegni" to "agenda",
    "lezioni_svolte" to "lessons", "comunicazioni_cerca" to "communications", "assenze_elenco" to "absences",
    "note_disciplinari" to "notes", "materiali_cerca" to "materials", "documenti" to "documents")[inner.name]

  override val parameters: JsonObject get() {
    if (sourceSection == null) return inner.parameters
    val extra = RegistroDatiTool().parameters["properties"] as JsonObject
    return JsonObject(inner.parameters + ("properties" to JsonObject(extra.filterKeys { it != "sezione" } + (inner.parameters["properties"] as JsonObject))))
  }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput =
    execute(args, ctx, null)

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext, execution: dev.antigravity.fluidengine.ai.tools.ToolExecution): ToolOutput =
    execute(args, ctx, execution)

  private suspend fun execute(args: JsonObject, ctx: AssistantToolContext, execution: dev.antigravity.fluidengine.ai.tools.ToolExecution?): ToolOutput {
    dev.antigravity.fluidengine.ai.tools.ToolArgumentValidator.error(args, parameters)?.let { return ToolOutput.error(it) }
    val started = System.currentTimeMillis()
    val output = try {
      if (sourceSection != null && args.keys.any { it in (RegistroDatiTool().parameters["properties"] as JsonObject).keys && it !in (inner.parameters["properties"] as JsonObject).keys }) {
        val props = (RegistroDatiTool().parameters["properties"] as JsonObject).keys
        val unsupported = args.keys - props
        if (unsupported.isNotEmpty()) return ToolOutput.error("filtri non supportati per lettura scoped: ${unsupported.joinToString()}; usa registro_dati")
        val forwarded = JsonObject(args.filterKeys { it in props } + ("sezione" to kotlinx.serialization.json.JsonPrimitive(sourceSection)))
        RegistroDatiTool().run(forwarded, ctx)
      } else if (execution == null) inner.run(args, ctx) else inner.run(args, ctx, execution)
    } catch (e: CancellationException) {
      ctx.trace(AssistantToolTrace(inner.name, compact(args), System.currentTimeMillis() - started, ok = false, chars = 0, preview = "fermato prima di finire"))
      throw e
    } catch (e: Throwable) {
      ctx.trace(AssistantToolTrace(inner.name, compact(args), System.currentTimeMillis() - started, ok = false, chars = 0, preview = "eccezione: ${e.message ?: e::class.simpleName}"))
      throw e
    }
    ctx.trace(
      AssistantToolTrace(
        name = inner.name,
        args = compact(args),
        millis = System.currentTimeMillis() - started,
        ok = !output.text.startsWith("errore"),
        chars = output.text.length,
        preview = output.text.take(PREVIEW_CHARS),
      ),
    )
    return if (output.result != null) output else output.copy(result = dev.antigravity.fluidengine.ai.tools.ToolResultDetails(
      dev.antigravity.fluidengine.ai.tools.ToolOutcome.UNCERTAIN,
      error = dev.antigravity.fluidengine.ai.tools.ToolError("legacy_evidence_unavailable", "Usa registro_dati per copertura, pagine e freschezza verificabili"),
    ))
  }

  private fun compact(args: JsonObject): String = args.toString().take(ARGS_CHARS)

  companion object {
    const val ARGS_CHARS = 160
    const val PREVIEW_CHARS = 240
  }
}
