package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantAction
import dev.antigravity.classevivaexpressive.core.assistant.math.GradeMath
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.slotFingerprint
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.int
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.ConfirmationText
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

private const val ACTIONS_OFF = "le azioni nell'app sono disattivate nelle impostazioni: l'utente puo' farlo a mano"

/** Quale anno scolastico l'app sta leggendo, e come cambiarlo. */
class AnnoScolasticoTool : AiTool<AssistantToolContext> {
  override val name = "anno_scolastico"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Quale anno scolastico l'app sta mostrando e quali sono disponibili; con \"seleziona\" passa a un altro anno (l'app chiede conferma e ricarica tutto)"
  override val parameters = Schema.obj(mapOf("seleziona" to Schema.str("l'anno a cui passare, es. 2025-26 o 2025; vuoto per sapere solo quello attuale")))
  override val needsConfirmation = true
  override val isAction = true

  private suspend fun target(args: JsonObject, ctx: AssistantToolContext): SchoolYearRef? {
    val raw = args.str("seleziona")?.trim() ?: return null
    val available = ctx.schoolYear.observeAvailableSchoolYears().first()
    val digits = Regex("\\d{4}").find(raw)?.value?.toIntOrNull()
    return available.firstOrNull { it.id == raw || it.label == raw } ?: digits?.let { year -> available.firstOrNull { it.startYear == year } }
  }

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext): ConfirmationText? =
    target(args, ctx)?.let { ConfirmationText("Passare all'anno ${it.label}?", "L'app ricarica voti, agenda e orario dell'anno scelto.") }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val selected = ctx.schoolYear.observeSelectedSchoolYear().first()
    val available = ctx.schoolYear.observeAvailableSchoolYears().first()
    val requested = args.str("seleziona")
    if (requested == null) {
      return ToolText.output {
        line("anno in uso", selected.label)
        line("anni disponibili", available.joinToString(", ") { it.label })
        line("nota", "per cambiarlo: anno_scolastico(seleziona=\"${available.lastOrNull()?.label ?: selected.label}\")")
      }
    }
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val year = target(args, ctx) ?: return ToolOutput.error("anno \"$requested\" non disponibile; ci sono: ${available.joinToString(", ") { it.label }}")
    if (year == selected) return ToolOutput("l'app sta gia' mostrando l'anno ${year.label}")
    val result = ctx.actions.perform(AssistantAction.SelectSchoolYear(year))
    return ToolOutput(result.toolText("fatto: ora l'app mostra l'anno ${year.label}"))
  }
}

/** Il pallino del voto nuovo: segnarlo visto e' un gesto dell'app, non tocca il registro. */
class VotoSegnaVistoTool : AiTool<AssistantToolContext> {
  override val name = "voto_segna_visto"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Segna come visto un voto nuovo (toglie il pallino nell'app): uno per id, o tutti quelli non ancora visti"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id del voto da voti_elenco; vuoto con tutti=si per segnarli tutti"), "tutti" to Schema.str("si per segnare visti tutti i voti nuovi")))
  override val isAction = true

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val all = Text.normalize(args.str("tutti").orEmpty()).let { it == "si" || it == "true" || it == "tutti" }
    val grades = ctx.grades.observeGrades().first()
    val seen = ctx.grades.observeSeenGradeStates().first().map { it.gradeId }.toSet()
    if (all) {
      val unseen = grades.filter { it.id !in seen }
      if (unseen.isEmpty()) return ToolOutput("non c'erano voti nuovi da segnare")
      unseen.forEach { ctx.actions.perform(AssistantAction.MarkGradeSeen(it.id, it.subject)) }
      return ToolOutput("fatto: ${unseen.size} voti segnati come visti")
    }
    val id = args.str("id") ?: return ToolOutput.error("dammi l'id del voto, o tutti=si")
    val grade = grades.firstOrNull { it.id == id } ?: return ToolOutput.error("voto \"$id\" non trovato: prendi l'id da voti_elenco")
    if (grade.id in seen) return ToolOutput("quel voto (${grade.subject} ${grade.valueLabel}) era gia' segnato visto")
    val result = ctx.actions.perform(AssistantAction.MarkGradeSeen(grade.id, "${grade.subject} ${grade.valueLabel}"))
    return ToolOutput(result.toolText("fatto: ${grade.subject} ${grade.valueLabel} segnato visto"))
  }
}

/** Toglie un evento personale dall'agenda: solo i propri, quelli del registro non si toccano. */
class EventoRimuoviTool : AiTool<AssistantToolContext> {
  override val name = "evento_rimuovi"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Toglie dall'agenda un evento personale aggiunto dallo studente (per id o per titolo). L'app chiede conferma. Gli impegni del registro non si possono togliere"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id dell'evento, o parole del titolo")), required = listOf("id"))
  override val needsConfirmation = true
  override val isAction = true

  private suspend fun find(args: JsonObject, ctx: AssistantToolContext) = args.str("id")?.let { key ->
    val events = ctx.agenda.observeCustomEvents().first()
    events.firstOrNull { it.id == key } ?: events.filter { Text.matches(key, it.title) }.maxByOrNull { it.date }
  }

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext): ConfirmationText? =
    find(args, ctx)?.let { ConfirmationText("Togliere dall'agenda?", "${it.title} · ${Dates.label(it.date)}") }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val event = find(args, ctx) ?: return ToolOutput.error("evento personale non trovato: guarda eventi_personali (gli impegni del registro non si tolgono)")
    val result = ctx.actions.perform(AssistantAction.RemoveCustomEvent(event.id, event.title))
    return ToolOutput(result.toolText("fatto: \"${event.title}\" tolto dall'agenda"))
  }
}

class ObiettivoRimuoviTool : AiTool<AssistantToolContext> {
  override val name = "obiettivo_rimuovi"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Toglie l'obiettivo di media salvato per una materia. L'app chiede conferma"
  override val parameters = Schema.obj(mapOf("materia" to Schema.str("nome della materia"), "periodo" to Schema.str("primo, secondo, corrente; vuoto = quello salvato")), required = listOf("materia"))
  override val needsConfirmation = true
  override val isAction = true

  private suspend fun find(args: JsonObject, ctx: AssistantToolContext) = args.str("materia")?.let { raw ->
    val goals = ctx.grades.observeSubjectGoals().first()
    goals.firstOrNull { Text.normalize(it.subject) == Text.normalize(raw) } ?: goals.firstOrNull { Text.matches(raw, it.subject) }
  }

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext): ConfirmationText? =
    find(args, ctx)?.let { ConfirmationText("Togliere l'obiettivo?", "${it.subject}: media ${GradeMath.format(it.targetAverage)}") }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val goal = find(args, ctx) ?: return ToolOutput.error("nessun obiettivo salvato per quella materia: guarda obiettivi")
    val period = ctx.matchPeriod(args.str("periodo")) ?: goal.periodCode?.let { code -> ctx.grades.observePeriods().first().firstOrNull { it.code == code } }
    val result = ctx.actions.perform(AssistantAction.RemoveGoal(goal.subject, goal.periodCode, period?.label))
    return ToolOutput(result.toolText("fatto: obiettivo di ${goal.subject} rimosso"))
  }
}

/**
 * Corregge una casella dell'orario che l'app ha imparato sbagliata. Resta sul telefono: il registro
 * non espone l'orario ufficiale a tutte le scuole, e l'app lo ricostruisce dalle lezioni firmate.
 */
class OrarioModificaTool : AiTool<AssistantToolContext> {
  override val name = "orario_modifica"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Corregge una casella dell'orario settimanale appreso dall'app (giorno + ora → materia, docente, aula). Vale solo dentro l'app, non cambia il registro. Chiede conferma"
  override val parameters = Schema.obj(
    mapOf(
      "giorno" to Schema.str("il giorno della settimana (lunedi, martedi, ...)"),
      "ora" to Schema.str("l'ora di inizio come compare nell'orario, es. 08:00"),
      "materia" to Schema.str("la materia giusta"),
      "docente" to Schema.str("il docente (facoltativo)"),
      "aula" to Schema.str("l'aula (facoltativa)"),
      "durata_minuti" to Schema.int("la durata in minuti (default: quella della casella, o 60)", 15, 240),
    ),
    required = listOf("giorno", "ora", "materia"),
  )
  override val needsConfirmation = true
  override val isAction = true

  private fun weekday(raw: String?): DayOfWeek? = when (Text.normalize(raw.orEmpty()).trim('\'')) {
    "lunedi" -> DayOfWeek.MONDAY
    "martedi" -> DayOfWeek.TUESDAY
    "mercoledi" -> DayOfWeek.WEDNESDAY
    "giovedi" -> DayOfWeek.THURSDAY
    "venerdi" -> DayOfWeek.FRIDAY
    "sabato" -> DayOfWeek.SATURDAY
    "domenica" -> DayOfWeek.SUNDAY
    else -> null
  }

  private suspend fun plan(args: JsonObject, ctx: AssistantToolContext): Pair<TemplateSlot, String>? {
    val day = weekday(args.str("giorno")) ?: return null
    val time = args.str("ora")?.takeIf { Regex("^\\d{1,2}:\\d{2}$").matches(it) } ?: return null
    val subject = args.str("materia") ?: return null
    val template = ctx.lessons.observeTimetableTemplate().first().withOverridesApplied()
    val existing = template.slots.firstOrNull { it.dayOfWeek == day.value && it.time == time }
    val slot = TemplateSlot(
      dayOfWeek = day.value,
      time = time,
      endTime = existing?.endTime,
      durationMinutes = args.int("durata_minuti") ?: existing?.durationMinutes ?: 60,
      subject = subject,
      teacher = args.str("docente") ?: existing?.teacher,
      room = args.str("aula") ?: existing?.room,
      confidence = 1f,
      sampleCount = existing?.sampleCount ?: 0,
      confirmed = true,
    )
    val what = "${Dates.longDay(day)} $time: ${existing?.subject?.let { "$it → " } ?: ""}$subject"
    return slot to what
  }

  override suspend fun describe(args: JsonObject, ctx: AssistantToolContext): ConfirmationText? =
    plan(args, ctx)?.let { (_, what) -> ConfirmationText("Correggere l'orario?", what) }

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    if (!ctx.actionsEnabled) return ToolOutput(ACTIONS_OFF)
    val (slot, what) = plan(args, ctx) ?: return ToolOutput.error("servono giorno (lunedi...), ora (hh:mm) e materia")
    val result = ctx.actions.perform(AssistantAction.SaveSlotOverride(slot.slotFingerprint(), slot, what))
    return ToolOutput(result.toolText("fatto: orario corretto ($what). Vale solo nell'app"))
  }
}

/**
 * Cosa questa scuola espone davvero. Il registro non e' uguale per tutti: dire "non ci sono
 * materiali" quando la scuola non li pubblica affatto e' una risposta sbagliata.
 */
class FunzioniDisponibiliTool : AiTool<AssistantToolContext> {
  override val name = "funzioni_disponibili"
  override val group: AiToolGroup = RegistroToolGroup.APP
  override val description = "Quali funzioni del registro questa scuola espone davvero (voti, agenda, bacheca, materiali, colloqui...) e quali no. Da usare quando una sezione risulta sempre vuota"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val matrix = ctx.capabilities.observeCapabilityMatrix().first()
    if (matrix.isEmpty()) return ToolOutput("l'app non ha ancora capito cosa espone questa scuola: succede prima del primo accesso completo")
    val (ok, off) = matrix.partition { it.enabled && it.mode != FeatureCapabilityMode.UNSUPPORTED }
    return ToolText.output {
      line("disponibili", ok.joinToString(", ") { it.label.ifBlank { it.feature.name.lowercase() } })
      if (off.isNotEmpty()) {
        line("non disponibili in questa scuola", off.joinToString(", ") { it.label.ifBlank { it.feature.name.lowercase() } })
        off.mapNotNull { c -> c.detail?.takeIf { it.isNotBlank() }?.let { "${c.label.ifBlank { c.feature.name.lowercase() }}: $it" } }.take(5).forEach { line(it) }
      }
      line("nota", "se una sezione e' fra le non disponibili, e' vuota per la scuola, non per un errore dell'app")
    }
  }
}

fun appExtraTools(): List<AiTool<AssistantToolContext>> = listOf(
  AnnoScolasticoTool(), VotoSegnaVistoTool(), EventoRimuoviTool(), ObiettivoRimuoviTool(), OrarioModificaTool(), FunzioniDisponibiliTool(),
)
