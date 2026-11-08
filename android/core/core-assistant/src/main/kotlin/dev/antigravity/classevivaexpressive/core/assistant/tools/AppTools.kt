package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.actions.ActionOutcome
import dev.antigravity.classevivaexpressive.core.assistant.actions.AppPage
import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantAction
import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantSetting
import dev.antigravity.classevivaexpressive.core.assistant.actions.RefreshSection
import dev.antigravity.classevivaexpressive.core.assistant.math.GradeMath
import dev.antigravity.classevivaexpressive.core.data.notifications.AbsencesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.AgendaChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.CommunicationsChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.GradesChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.HomeworkChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.LiveTimetableChannelId
import dev.antigravity.classevivaexpressive.core.data.notifications.NotesChannelId
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.CustomEvent
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.double
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

private const val ACTIONS_OFF = "le azioni nell'app sono disattivate nelle impostazioni: l'utente puo' farlo a mano"

private fun yes(raw: String?): Boolean? {
  val t = Text.normalize(raw ?: return null)
  return when (t) {
    "si", "sì", "s", "vero", "true", "on", "attiva", "attivo", "acceso", "abilita", "1" -> true
    "no", "n", "falso", "false", "off", "disattiva", "spento", "disabilita", "0" -> false
    else -> null
  }
}

class ApriTool : AiTool<AssistantToolContext> {
  override val name = "apri"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Apre una pagina dell'app, o il dettaglio di un voto / comunicazione / compito dato il suo id"
  override val isAction = true
  override val parameters = Schema.obj(
    mapOf(
      "pagina" to Schema.str("la pagina", AppPage.entries.map { it.id }),
      "id" to Schema.str("l'id del voto (pagina voti), della comunicazione (bacheca) o del compito (compiti) da aprire nel dettaglio"),
    ),
    required = listOf("pagina"),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val page = AppPage.fromId(args.str("pagina")) ?: return ToolOutput.error("pagina sconosciuta; le pagine sono: ${AppPage.entries.joinToString(", ") { it.id }}")
    val result = ctx.actions.perform(AssistantAction.Open(page, args.str("id")))
    return ToolOutput(result.toolText("aperta la pagina ${page.label}"))
  }
}

class ImpostazioneTool : AiTool<AssistantToolContext> {
  override val name = "impostazione"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Cambia un'impostazione dell'app: " + AssistantSetting.entries.joinToString("; ") { "${it.id} (${it.hint})" }
  override val isAction = true
  override val parameters = Schema.obj(
    mapOf(
      "chiave" to Schema.str("quale impostazione", AssistantSetting.entries.map { it.id }),
      "valore" to Schema.str("il valore: si/no, oppure il nome (sistema, chiaro, scuro, amoled; brand, dinamico, expressive, ember, ocean, jade)"),
    ),
    required = listOf("chiave", "valore"),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val setting = AssistantSetting.fromId(args.str("chiave")) ?: return ToolOutput.error("impostazione sconosciuta")
    val raw = args.str("valore") ?: return ToolOutput.error("valore mancante")
    val value = Text.normalize(raw)
    val action: AssistantAction = when (setting) {
      AssistantSetting.TEMA -> AssistantAction.SetTheme(
        when {
          value.startsWith("sist") || value == "auto" || value == "automatico" -> ThemeMode.SYSTEM
          value.startsWith("chiar") || value == "light" -> ThemeMode.LIGHT
          value.startsWith("scur") || value == "dark" || value == "notte" -> ThemeMode.DARK
          value.startsWith("amoled") || value.startsWith("nero") -> ThemeMode.AMOLED
          else -> return ToolOutput.error("tema sconosciuto: sistema, chiaro, scuro o amoled")
        },
      )
      AssistantSetting.ACCENTO -> when {
        value.startsWith("brand") || value.startsWith("viola") || value.startsWith("ametist") -> AssistantAction.SetAccent(AccentMode.BRAND, null)
        value.startsWith("dinam") || value.startsWith("telefono") || value.startsWith("sistema") -> AssistantAction.SetAccent(AccentMode.DYNAMIC, null)
        value in setOf("expressive", "blu") -> AssistantAction.SetAccent(AccentMode.CUSTOM_PRESET, "expressive")
        value in setOf("ember", "arancio", "arancione") -> AssistantAction.SetAccent(AccentMode.CUSTOM_PRESET, "ember")
        value in setOf("ocean", "indaco") -> AssistantAction.SetAccent(AccentMode.CUSTOM_PRESET, "ocean")
        value in setOf("jade", "verde") -> AssistantAction.SetAccent(AccentMode.CUSTOM_PRESET, "jade")
        else -> return ToolOutput.error("accento sconosciuto: brand, dinamico, expressive (blu), ember (arancio), ocean (indaco), jade (verde)")
      }
      AssistantSetting.COLORE_DINAMICO -> AssistantAction.SetDynamicColor(yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.AMOLED -> AssistantAction.SetAmoled(yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE -> AssistantAction.SetNotifications(yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE_VOTI -> AssistantAction.SetNotificationCategory(GradesChannelId, "voti", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE_COMPITI -> AssistantAction.SetNotificationCategory(HomeworkChannelId, "compiti", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE_AGENDA -> AssistantAction.SetNotificationCategory(AgendaChannelId, "agenda", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE_COMUNICAZIONI -> AssistantAction.SetNotificationCategory(CommunicationsChannelId, "comunicazioni", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE_ASSENZE -> AssistantAction.SetNotificationCategory(AbsencesChannelId, "assenze", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.NOTIFICHE_NOTE -> AssistantAction.SetNotificationCategory(NotesChannelId, "note", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.ORARIO_LIVE -> AssistantAction.SetNotificationCategory(LiveTimetableChannelId, "orario live", yes(raw) ?: return ToolOutput.error("valore: si o no"))
      AssistantSetting.SYNC_PERIODICA -> AssistantAction.SetPeriodicSync(yes(raw) ?: return ToolOutput.error("valore: si o no"))
    }
    val result = ctx.actions.perform(action)
    return ToolOutput(result.toolText("fatto: ${action.title}"))
  }
}

class BachecaSegnaLetteTool : AiTool<AssistantToolContext> {
  override val name = "bacheca_segna_lette"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Segna come lette le comunicazioni in bacheca: tutte, o una sola dato il suo id"
  override val isAction = true
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id di una comunicazione; vuoto per segnarle tutte")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val ref = args.str("id")
    if (ref == null) {
      val unread = ctx.communications.observeCommunications().first().count { !it.read }
      if (unread == 0) return ToolOutput("non c'erano comunicazioni da leggere")
      val result = ctx.actions.perform(AssistantAction.MarkAllRead)
      return ToolOutput(result.toolText("fatto: $unread comunicazioni segnate come lette"))
    }
    val communication = ctx.findCommunication(ref) ?: return ToolOutput.error("comunicazione non trovata")
    if (communication.read) return ToolOutput("\"${communication.title}\" era gia' letta")
    val result = ctx.actions.perform(AssistantAction.MarkRead(communication.id, communication.title))
    return ToolOutput(result.toolText("fatto: \"${communication.title}\" segnata come letta"))
  }
}

class BachecaPresaVisioneTool : AiTool<AssistantToolContext> {
  override val name = "bacheca_presa_visione"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Conferma la presa visione di una comunicazione che la richiede (un atto verso la scuola: l'app chiede conferma con un tasto)"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id della comunicazione")), required = listOf("id"))
  override val needsConfirmation = true
  override val isAction = true

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext) =
    ctx.findCommunication(args.str("id"))?.let { dev.antigravity.fluidengine.ai.tools.ConfirmationText("Confermare la presa visione?", it.title) }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val communication = ctx.findCommunication(args.str("id")) ?: return ToolOutput.error("comunicazione non trovata")
    if (!communication.needsAck) return ToolOutput("\"${communication.title}\" non richiede una presa visione (o e' gia' stata data)")
    val result = ctx.actions.perform(AssistantAction.Acknowledge(communication.pubId, communication.evtCode, communication.title))
    return ToolOutput(result.toolText("fatto: presa visione confermata per \"${communication.title}\""))
  }
}

class AgendaAggiungiEventoTool : AiTool<AssistantToolContext> {
  override val name = "agenda_aggiungi_evento"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Aggiunge un evento personale all'agenda (l'app chiede conferma con un tasto)"
  override val parameters = Schema.obj(
    mapOf(
      "titolo" to Schema.str("cosa (es. verifica di storia, allenamento)"),
      "data" to Schema.str("il giorno (aaaa-mm-gg, domani, venerdi...)"),
      "ora" to Schema.str("l'ora (hh:mm), facoltativa"),
      "materia" to Schema.str("la materia, facoltativa"),
      "note" to Schema.str("dettagli, facoltativi"),
    ),
    required = listOf("titolo", "data"),
  )
  override val needsConfirmation = true
  override val isAction = true

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext): dev.antigravity.fluidengine.ai.tools.ConfirmationText? {
    val date = Dates.parse(args.str("data"), ctx.today) ?: return null
    return dev.antigravity.fluidengine.ai.tools.ConfirmationText("Aggiungere in agenda?", "${args.str("titolo")} · ${Dates.label(date)}${args.str("ora")?.let { " $it" } ?: ""}")
  }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput =
    run(args, ctx, dev.antigravity.fluidengine.ai.tools.ToolExecution(UUID.randomUUID().toString()))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext, execution: dev.antigravity.fluidengine.ai.tools.ToolExecution): ToolOutput {
    dev.antigravity.fluidengine.ai.tools.ToolArgumentValidator.error(args, parameters)?.let { return ToolOutput.error(it) }
    if (!ctx.actionsEnabled) return ToolOutput.error(ACTIONS_OFF)
    val auth = ctx.auth ?: return ToolOutput.error("auth_missing")
    val dataEpoch = auth.dataEpoch()
    val epoch = auth.authenticationEpoch()
    val session = auth.session.value ?: return ToolOutput.error("auth_missing")
    val title = args.str("titolo")?.trim()?.takeIf { it.isNotEmpty() } ?: return ToolOutput.error("titolo mancante")
    val date = Dates.parse(args.str("data"), ctx.today) ?: return ToolOutput.error("data non valida")
    val time = args.str("ora")
    if (time != null && runCatching { java.time.LocalTime.parse(time) }.isFailure) return ToolOutput.error("ora non valida: hh:mm")
    val identity = "cv-event:" + UUID.nameUUIDFromBytes("${session.username}:${session.studentId}:${execution.operationId}".toByteArray())
    val event = CustomEvent(id = identity, title = title, description = args.str("note").orEmpty(),
      subject = args.str("materia").orEmpty(), date = date.toString(), time = time)
    val result = ctx.actions.perform(AssistantAction.AddCustomEvent(event, epoch, dataEpoch))
    if (auth.authenticationEpoch() != epoch || auth.dataEpoch() != dataEpoch) return ToolOutput.error("account_changed")
    val saved = if(result.outcome == dev.antigravity.classevivaexpressive.core.assistant.actions.ActionOutcome.DONE) {
      try { ctx.agenda.observeCustomEvents().first().firstOrNull { it.id == identity } }
      catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
      catch (_: Exception) { null } // The accepted write still has an uncertain effect if readback fails.
    } else null
    if (auth.authenticationEpoch() != epoch || auth.dataEpoch() != dataEpoch) return ToolOutput.error("account_changed")
    val complete = saved?.copy(createdAt = null) == event
    val outcome = when {
      complete -> dev.antigravity.fluidengine.ai.tools.ToolOutcome.COMPLETED
      result.outcome == dev.antigravity.classevivaexpressive.core.assistant.actions.ActionOutcome.DONE -> dev.antigravity.fluidengine.ai.tools.ToolOutcome.UNCERTAIN
      result.outcome == dev.antigravity.classevivaexpressive.core.assistant.actions.ActionOutcome.FAILED -> dev.antigravity.fluidengine.ai.tools.ToolOutcome.UNCERTAIN
      else -> dev.antigravity.fluidengine.ai.tools.ToolOutcome.ERROR
    }
    val data = saved?.let { kotlinx.serialization.json.buildJsonObject {
      put("id", kotlinx.serialization.json.JsonPrimitive(it.id)); put("title", kotlinx.serialization.json.JsonPrimitive(it.title))
      put("date", kotlinx.serialization.json.JsonPrimitive(it.date)); it.time?.let { t -> put("time", kotlinx.serialization.json.JsonPrimitive(t)) }
      put("description", kotlinx.serialization.json.JsonPrimitive(it.description)); put("subject", kotlinx.serialization.json.JsonPrimitive(it.subject))
      put("readbackVerified", kotlinx.serialization.json.JsonPrimitive(complete))
    } }
    return ToolOutput(if(complete) "Evento ${saved!!.id} salvato e riletto: ${saved.title}, ${saved.date}" else result.toolText("scrittura accettata; rilettura non verificata"),
      result = dev.antigravity.fluidengine.ai.tools.ToolResultDetails(outcome, data = data,
        entities = if(complete) listOf(dev.antigravity.fluidengine.ai.tools.ToolEntityRef("cv", "custom_event", identity)) else emptyList(),
        operation = dev.antigravity.fluidengine.ai.tools.ToolOperationRef(execution.operationId, "cv"),
        error = if(complete) null else dev.antigravity.fluidengine.ai.tools.ToolError("event_unverified", result.message ?: "effetto non verificato")))
  }
}

class ObiettivoSalvaTool : AiTool<AssistantToolContext> {
  override val name = "obiettivo_salva"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Salva un obiettivo di media per una materia (l'app chiede conferma con un tasto)"
  override val parameters = Schema.obj(
    mapOf(
      "materia" to Schema.str("nome della materia"),
      "obiettivo" to Schema.str("la media da raggiungere, es. 7"),
      "periodo" to Schema.str("primo, secondo, corrente; vuoto per tutto l'anno"),
    ),
    required = listOf("materia", "obiettivo"),
  )
  override val needsConfirmation = true
  override val isAction = true

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext): dev.antigravity.fluidengine.ai.tools.ConfirmationText? {
    val target = args.double("obiettivo") ?: return null
    return dev.antigravity.fluidengine.ai.tools.ConfirmationText("Salvare l'obiettivo?", "${args.str("materia")}: media ${GradeMath.format(target)}")
  }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val target = args.double("obiettivo")?.takeIf { it in 1.0..10.0 } ?: return ToolOutput.error("obiettivo non valido (da 1 a 10)")
    val subjectArg = args.str("materia") ?: return ToolOutput.error("materia mancante")
    val known = ctx.grades.observeSubjects().first().map { it.description } + ctx.grades.observeGrades().first().map { it.subject }
    val subject = Subjects.match(subjectArg, known) ?: return ToolOutput.error("materia \"$subjectArg\" non trovata")
    val period = ctx.matchPeriod(args.str("periodo"))
    val result = ctx.actions.perform(AssistantAction.SaveGoal(subject, period?.code, period?.label, target))
    return ToolOutput(result.toolText("fatto: obiettivo ${GradeMath.format(target)} salvato per $subject${period?.let { " (${it.label})" } ?: ""}"))
  }
}

class AggiornaDatiTool : AiTool<AssistantToolContext> {
  override val name = "aggiorna_dati"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Scarica dal registro i dati aggiornati (serve la rete): tutto o una sezione. Usalo se l'utente chiede dati freschi o se i dati sembrano vecchi"
  override val isAction = true
  override val longRunning = true
  override val parameters = Schema.obj(mapOf("sezione" to Schema.str("cosa aggiornare", RefreshSection.entries.map { it.id })))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val section = RefreshSection.fromId(args.str("sezione"))
    val result = ctx.actions.perform(AssistantAction.Refresh(section))
    return ToolOutput(result.toolText("fatto: dati aggiornati (${section.id})"))
  }
}

class StatoSyncTool : AiTool<AssistantToolContext> {
  override val name = "stato_sync"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Quando i dati del registro sono stati sincronizzati l'ultima volta e se qualcosa e' fallito"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val status = ctx.dashboard.observeDashboard().first().syncStatus
    return ToolText.output {
      line(
        "stato",
        when (status.state) {
          SyncState.IDLE -> "sincronizzato"
          SyncState.SYNCING -> "sincronizzazione in corso"
          SyncState.PARTIAL -> "sincronizzazione parziale"
          SyncState.OFFLINE -> "offline"
          SyncState.ERROR -> "errore"
        },
      )
      line("ultima sincronizzazione riuscita", status.lastSuccessfulSyncEpochMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(ctx.zone).toLocalDateTime().withNano(0).toString() })
      if (status.failedSections.isNotEmpty()) line("sezioni fallite", status.failedSections.joinToString(", "))
      status.message?.let { line("messaggio", it) }
      if (status.schoolYearNotStarted) line("nota", "la scuola non ha ancora aperto l'anno scolastico richiesto")
    }
  }
}
