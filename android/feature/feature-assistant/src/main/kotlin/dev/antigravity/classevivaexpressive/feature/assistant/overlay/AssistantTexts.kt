package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.classevivaexpressive.core.assistant.prompt.AssistantChips
import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts

/**
 * Le parole dell'assistente del registro: quelle dell'engine (`engine-ai-ui`) piu' il dominio, cioe'
 * le frasi dei gruppi di strumenti e i chip che portano a una pagina dell'app. Le tabelle dei
 * fallimenti e degli stati stanno nell'engine; qui restano solo le cose che l'engine non puo' sapere.
 */
object AssistantTexts {

  /** Le parole per `FluidAssistantOverlay`, con la riga di stato dei gruppi del registro. */
  val strings = FluidAssistantStrings(
    askPlaceholder = "Chiedi al registro…",
    // L'app non ha l'interruttore della riserva automatica: un limite non puo' consigliare di accenderlo.
    failoverSwitch = false,
    failNoKeys = "Nessuna chiave verificata: apri le impostazioni dell'assistente.",
    statusFor = { key, _ -> groupStatus(key) },
  )

  private fun groupStatus(key: String): String? = when (key) {
    RegistroToolGroup.VOTI.statusKey -> "Guardo i voti…"
    RegistroToolGroup.AGENDA.statusKey -> "Guardo l'agenda…"
    RegistroToolGroup.ORARIO.statusKey -> "Guardo l'orario…"
    RegistroToolGroup.BACHECA.statusKey -> "Leggo la bacheca…"
    RegistroToolGroup.ASSENZE.statusKey -> "Controllo le assenze…"
    RegistroToolGroup.STATISTICHE.statusKey -> "Faccio i conti…"
    RegistroToolGroup.DIDATTICA.statusKey -> "Cerco fra i materiali…"
    RegistroToolGroup.APP.statusKey -> "Agisco nell'app…"
    else -> null
  }

  fun status(key: String, tier: ModelTier = ModelTier.CHAT): String = FluidAssistantTexts.status(key, tier, strings)

  fun failure(kind: FailureKind, retryAfterSec: Int? = null): String =
    FluidAssistantTexts.failure(kind, strings, retryAfterSec)

  fun statusLine(state: AssistantState): String? = FluidAssistantTexts.statusLine(state, strings)

  fun chipLabel(chip: AnswerChip): String = when (chip.id) {
    AssistantChips.PAGE -> AppPage.fromId(chip.value)?.label ?: chip.value.orEmpty()
    AssistantChips.GRADE -> "Apri il voto"
    AssistantChips.COMMUNICATION -> "Apri la comunicazione"
    AssistantChips.HOMEWORK -> "Apri il compito"
    else -> chip.value ?: chip.id
  }

  /** Dove porta un chip: la pagina, e il dettaglio se il chip ne ha uno. */
  fun chipTarget(chip: AnswerChip): Pair<AppPage, String?>? = when (chip.id) {
    AssistantChips.PAGE -> AppPage.fromId(chip.value)?.let { it to null }
    AssistantChips.GRADE -> chip.value?.let { AppPage.VOTI to it }
    AssistantChips.COMMUNICATION -> chip.value?.let { AppPage.BACHECA to it }
    AssistantChips.HOMEWORK -> chip.value?.let { AppPage.COMPITI to it }
    else -> null
  }
}
