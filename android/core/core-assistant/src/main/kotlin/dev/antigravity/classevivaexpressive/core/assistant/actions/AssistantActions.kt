package dev.antigravity.classevivaexpressive.core.assistant.actions

import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode

/** Le pagine dell'app che l'assistente puo' aprire; l'id e' la parola che il modello usa. */
enum class AppPage(val id: String, val label: String) {
  HOME("home", "Home"),
  VOTI("voti", "Voti"),
  AGENDA("agenda", "Agenda"),
  BACHECA("bacheca", "Bacheca"),
  ORARIO("orario", "Orario"),
  COMPITI("compiti", "Compiti"),
  ASSENZE("assenze", "Assenze"),
  NOTE("note", "Note disciplinari"),
  DIDATTICA("didattica", "Didattica"),
  DOCUMENTI("documenti", "Documenti e libri"),
  PROFESSORI("professori", "Professori"),
  PUNTEGGIO("punteggio", "Punteggio studente"),
  IMPOSTAZIONI("impostazioni", "Impostazioni"),
  ASSISTENTE("assistente", "Assistente"),
  ;

  companion object {
    fun fromId(id: String?): AppPage? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
  }
}

/** Le impostazioni che l'assistente puo' cambiare: aspetto, notifiche, sincronizzazione. Niente account. */
enum class AssistantSetting(val id: String, val hint: String) {
  TEMA("tema", "sistema | chiaro | scuro | amoled"),
  COLORE_DINAMICO("colore_dinamico", "si | no (i colori del telefono)"),
  ACCENTO("accento", "brand | dinamico | expressive | ember | ocean | jade"),
  AMOLED("amoled", "si | no (nero puro nel tema scuro)"),
  NOTIFICHE("notifiche", "si | no (tutte)"),
  NOTIFICHE_VOTI("notifiche_voti", "si | no"),
  NOTIFICHE_COMPITI("notifiche_compiti", "si | no"),
  NOTIFICHE_AGENDA("notifiche_agenda", "si | no"),
  NOTIFICHE_COMUNICAZIONI("notifiche_comunicazioni", "si | no"),
  NOTIFICHE_ASSENZE("notifiche_assenze", "si | no"),
  NOTIFICHE_NOTE("notifiche_note", "si | no"),
  ORARIO_LIVE("orario_live", "si | no (la notifica dell'orario in corso)"),
  SYNC_PERIODICA("sync_periodica", "si | no"),
  ;

  companion object {
    fun fromId(id: String?): AssistantSetting? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
  }
}

/** Le sezioni che `aggiorna_dati` puo' rinfrescare dalla rete. */
enum class RefreshSection(val id: String) {
  TUTTO("tutto"), VOTI("voti"), AGENDA("agenda"), COMPITI("compiti"), ORARIO("orario"), BACHECA("bacheca"), ASSENZE("assenze"), DIDATTICA("didattica"), DOCUMENTI("documenti"),
  ;

  companion object {
    fun fromId(id: String?): RefreshSection = entries.firstOrNull { it.id == id?.trim()?.lowercase() } ?: TUTTO
  }
}

/**
 * Cio' che un tool chiede all'app di fare. I tool restano puri: descrivono l'azione, l'esecutore
 * (nel runtime) la fa, e le scritture che contano passano da una conferma a schermo.
 */
sealed interface AssistantAction {
  val needsConfirmation: Boolean
  val title: String
  val detail: String?

  data class Open(val page: AppPage, val itemId: String? = null) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Apri ${page.label}"
    override val detail get() = itemId
  }

  data class SetTheme(val mode: ThemeMode) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Tema: ${mode.name.lowercase()}"
    override val detail: String? get() = null
  }

  data class SetAccent(val mode: AccentMode, val presetName: String?) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Accento: ${presetName ?: mode.name.lowercase()}"
    override val detail: String? get() = null
  }

  data class SetDynamicColor(val enabled: Boolean) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Colore dinamico: ${if (enabled) "si'" else "no"}"
    override val detail: String? get() = null
  }

  data class SetAmoled(val enabled: Boolean) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "AMOLED: ${if (enabled) "si'" else "no"}"
    override val detail: String? get() = null
  }

  data class SetNotifications(val enabled: Boolean) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Notifiche: ${if (enabled) "attive" else "spente"}"
    override val detail: String? get() = null
  }

  data class SetNotificationCategory(val channelId: String, val label: String, val enabled: Boolean) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Notifiche $label: ${if (enabled) "attive" else "spente"}"
    override val detail: String? get() = null
  }

  data class SetPeriodicSync(val enabled: Boolean) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Sincronizzazione periodica: ${if (enabled) "attiva" else "spenta"}"
    override val detail: String? get() = null
  }

  data object MarkAllRead : AssistantAction {
    override val needsConfirmation = false
    override val title = "Segna tutte le comunicazioni come lette"
    override val detail: String? = null
  }

  data class MarkRead(val communicationId: String, val communicationTitle: String) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Segna come letta"
    override val detail get() = communicationTitle
  }

  /** Presa visione: un atto verso la scuola, quindi con conferma e col titolo per intero. */
  data class Acknowledge(val pubId: String, val evtCode: String, val communicationTitle: String) : AssistantAction {
    override val needsConfirmation = true
    override val title get() = "Confermare la presa visione?"
    override val detail get() = communicationTitle
  }

  data class AddCustomEvent(val event: CustomEvent) : AssistantAction {
    override val needsConfirmation = true
    override val title get() = "Aggiungere in agenda?"
    override val detail get() = buildString {
      append(event.title)
      if (event.subject.isNotBlank()) append(" · ").append(event.subject)
      append(" · ").append(event.date)
      event.time?.let { append(" ").append(it) }
    }
  }

  data class SaveGoal(val subject: String, val periodCode: String?, val periodLabel: String?, val target: Double) : AssistantAction {
    override val needsConfirmation = true
    override val title get() = "Salvare l'obiettivo?"
    override val detail get() = "$subject: media $target" + (periodLabel?.let { " nel $it" } ?: "")
  }

  data class Refresh(val section: RefreshSection) : AssistantAction {
    override val needsConfirmation = false
    override val title get() = "Aggiorna ${section.id}"
    override val detail: String? get() = null
  }
}

enum class ActionOutcome { DONE, REJECTED, TIMEOUT, UNAVAILABLE, FAILED }

/** Com'e' andata, in parole che tornano al modello come risultato dello strumento. */
data class ActionResult(val outcome: ActionOutcome, val message: String? = null) {
  fun toolText(done: String): String = when (outcome) {
    ActionOutcome.DONE -> message ?: done
    ActionOutcome.REJECTED -> "l'utente ha annullato"
    ActionOutcome.TIMEOUT -> "nessuna conferma dall'utente: non fatto"
    ActionOutcome.UNAVAILABLE -> "le azioni nell'app sono disattivate nelle impostazioni: l'utente puo' farlo a mano"
    ActionOutcome.FAILED -> "non riuscito: ${message ?: "errore"}"
  }
}

interface AssistantActionSink {
  suspend fun perform(action: AssistantAction): ActionResult

  object Disabled : AssistantActionSink {
    override suspend fun perform(action: AssistantAction) = ActionResult(ActionOutcome.UNAVAILABLE)
  }
}

/** Una pagina da aprire, verso `MainApp`, che e' l'unico a tenere il NavController. */
data class NavigationRequest(val page: AppPage, val itemId: String? = null)
