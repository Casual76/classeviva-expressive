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

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val started = System.currentTimeMillis()
    val output = try {
      inner.run(args, ctx)
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
    return output
  }

  private fun compact(args: JsonObject): String = args.toString().take(ARGS_CHARS)

  companion object {
    const val ARGS_CHARS = 160
    const val PREVIEW_CHARS = 240
  }
}
