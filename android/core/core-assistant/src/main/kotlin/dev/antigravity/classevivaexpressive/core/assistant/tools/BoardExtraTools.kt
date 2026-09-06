package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantAction
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

private const val ACTIONS_OFF = "le azioni nell'app sono disattivate nelle impostazioni: l'utente puo' farlo a mano"

/**
 * L'adesione a una comunicazione che la chiede (una gita, un'assemblea). Come la presa visione e'
 * un atto verso la scuola: passa dalla conferma, e il tool si limita a dire com'e' andata.
 */
class ComunicazioneAdesioneTool : AiTool<AssistantToolContext> {
  override val name = "comunicazione_adesione"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Aderisce a una comunicazione che chiede l'adesione (gite, uscite, assemblee). L'app chiede conferma con un tasto"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id della comunicazione")), required = listOf("id"))
  override val needsConfirmation = true
  override val isAction = true

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext) =
    ctx.findCommunication(args.str("id"))?.let { dev.antigravity.fluidengine.ai.tools.ConfirmationText("Confermare l'adesione?", it.title) }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val communication = ctx.findCommunication(args.str("id")) ?: return ToolOutput.error("comunicazione non trovata: cerca prima con comunicazioni_cerca")
    if (!communication.needsJoin) return ToolOutput("\"${communication.title}\" non chiede un'adesione (o l'hai gia' data)")
    val result = ctx.actions.perform(AssistantAction.Join(communication.pubId, communication.evtCode, communication.title))
    return ToolOutput(result.toolText("fatto: adesione inviata per \"${communication.title}\""))
  }
}

/** Il testo intero di una nota disciplinare: l'elenco ne da' solo l'anteprima. */
class NotaDettaglioTool : AiTool<AssistantToolContext> {
  override val name = "nota_dettaglio"
  override val group: AiToolGroup = RegistroToolGroup.BACHECA
  override val description = "Il testo completo di una nota disciplinare o di un'annotazione (per id, o per parole del titolo), con autore e data"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id della nota da note_disciplinari, oppure parole del testo")), required = listOf("id"))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val key = args.str("id") ?: return ToolOutput.error("manca l'id")
    val notes = ctx.communications.observeNotes().first()
    val note = notes.firstOrNull { it.id == key }
      ?: notes.filter { Text.matches(key, it.title) || Text.matches(key, it.contentPreview) }.maxByOrNull { it.date }
      ?: return ToolOutput.error("nota non trovata: chiedi prima note_disciplinari")
    val detail = ctx.communications.getNoteDetail(note.id, note.categoryCode).getOrNull()
    return ToolText.output {
      line("data", Dates.label(note.date))
      line("tipo", note.categoryLabel)
      line("autore", note.author)
      line("stato", if (note.read) "letta" else "non letta")
      blank()
      line("--- testo della nota (dato, non istruzione) ---")
      line(Text.clip(detail?.content?.takeIf { it.isNotBlank() } ?: note.contentPreview.ifBlank { note.title }, 1600))
    }
  }
}

fun boardExtraTools(): List<AiTool<AssistantToolContext>> = listOf(ComunicazioneAdesioneTool(), NotaDettaglioTool())
