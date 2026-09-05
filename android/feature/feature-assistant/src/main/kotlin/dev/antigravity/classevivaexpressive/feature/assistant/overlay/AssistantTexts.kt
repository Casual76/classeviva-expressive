package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.classevivaexpressive.core.assistant.prompt.AssistantChips
import dev.antigravity.classevivaexpressive.core.assistant.tools.RegistroToolGroup
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.provider.ModelTier

/** Da chiavi di stato, errori e chip alle parole: l'unico posto in cui la UI dell'assistente sceglie una frase. */
object AssistantTexts {

  fun status(key: String, tier: ModelTier = ModelTier.CHAT): String {
    val base = when (key) {
      "thinking" -> if (tier == ModelTier.DEEP) "Analizzo con calma…" else "Penso…"
      "more_tools" -> "Mi serve dell'altro…"
      RegistroToolGroup.VOTI.statusKey -> "Guardo i voti…"
      RegistroToolGroup.AGENDA.statusKey -> "Guardo l'agenda…"
      RegistroToolGroup.ORARIO.statusKey -> "Guardo l'orario…"
      RegistroToolGroup.BACHECA.statusKey -> "Leggo la bacheca…"
      RegistroToolGroup.ASSENZE.statusKey -> "Controllo le assenze…"
      RegistroToolGroup.STATISTICHE.statusKey -> "Faccio i conti…"
      RegistroToolGroup.DIDATTICA.statusKey -> "Cerco fra i materiali…"
      RegistroToolGroup.APP.statusKey -> "Agisco nell'app…"
      else -> "Penso…"
    }
    return if (tier == ModelTier.DEEP && key != "thinking") "$base (modello profondo)" else base
  }

  fun failure(kind: FailureKind, retryAfterSec: Int? = null): String = when (kind) {
    FailureKind.NO_KEYS -> "Nessuna chiave verificata: apri le impostazioni dell'assistente."
    FailureKind.UNAUTHORIZED -> "La chiave non e' piu' valida: controllala nelle impostazioni."
    FailureKind.RATE_LIMITED -> if (retryAfterSec != null) "Il servizio e' al limite: riprova fra $retryAfterSec s." else "Il servizio e' al limite di richieste: riprova fra poco."
    FailureKind.NETWORK -> "Niente rete."
    FailureKind.TIMEOUT -> "Ci ha messo troppo: riprova con una domanda piu' semplice."
    FailureKind.BLOCKED -> "Il servizio ha rifiutato la richiesta."
    FailureKind.PROVIDER -> "Il servizio ha risposto con un errore."
    FailureKind.MICROPHONE -> "Il microfono non e' disponibile: chiudi l'app che lo sta usando."
    FailureKind.TRANSCRIPTION -> "Non sono riuscito a trascrivere: riprova."
    FailureKind.UNKNOWN -> "Qualcosa e' andato storto."
  }

  fun statusLine(state: AssistantState): String? = when (state) {
    is AssistantState.Listening -> "Ti ascolto…"
    AssistantState.Transcribing -> "Trascrivo…"
    is AssistantState.Classifying -> "Capisco cosa serve…"
    is AssistantState.Working -> {
      val base = status(state.statusKey, state.tier)
      if (state.statusExtra > 0) "$base (+${state.statusExtra})" else base
    }
    is AssistantState.WaitingRateLimit -> "${state.provider.label} e' al limite: riprovo fra ${state.secondsLeft} s"
    is AssistantState.SwitchingProvider -> "Passo a ${state.to.label}…"
    is AssistantState.Answering -> "Rispondo…"
    is AssistantState.AwaitingConfirmation -> "Serve una conferma"
    AssistantState.HeardNothing -> "Non ho sentito niente"
    is AssistantState.Failed -> failure(state.kind, state.retryAfterSec)
    is AssistantState.Cancelled -> "Fermato"
    is AssistantState.Done -> null
    AssistantState.Idle -> null
  }

  fun collapsed(state: AssistantState): String = when (state) {
    is AssistantState.Done -> "Risposta pronta"
    else -> statusLine(state) ?: "Assistente"
  }

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

  /** Vero se lo stato merita la card espansa di default (una risposta, un errore, una conferma). */
  fun wantsExpanded(state: AssistantState): Boolean = when (state) {
    is AssistantState.Done, is AssistantState.Failed, is AssistantState.AwaitingConfirmation, is AssistantState.Answering -> true
    else -> false
  }
}
