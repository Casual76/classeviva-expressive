package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.math.GradeMath
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.double
import dev.antigravity.fluidengine.ai.tools.Args.int
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * La giornata di scuola in una chiamata sola. Nasce da una domanda che l'assistente riceve
 * continuamente — "com'e' domani?", "cosa ho oggi?" — e che prima costava quattro strumenti
 * (orario, compiti, agenda, voti) e quattro giri: qui e' uno, con le stesse fonti.
 */
class GiornataScuolaTool : AiTool<AssistantToolContext> {
  override val name = "giornata_scuola"
  override val group: AiToolGroup = RegistroToolGroup.GIORNATA
  override val description = "Tutta la giornata di scuola di un giorno (default oggi): le lezioni ora per ora, i compiti in scadenza, le verifiche, i voti presi quel giorno. Per \"cosa ho domani?\", \"com'e' andata oggi?\""
  override val parameters = Schema.obj(mapOf("data" to Schema.str("il giorno (aaaa-mm-gg, oggi, domani, lunedi...); vuoto = oggi")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val date = Dates.parse(args.str("data"), ctx.today) ?: ctx.today
    return ToolText.output(maxChars = 3_500) { day(ctx, date, full = true) }
  }
}

/** Le lezioni, gli impegni e i voti di un giorno, come righe. Condiviso con la settimana. */
private suspend fun ToolText.Builder.day(ctx: AssistantToolContext, date: LocalDate, full: Boolean) {
  val lessons = ctx.lessons.observeLessons().first()
  val template = ctx.lessons.observeTimetableTemplate().first().withOverridesApplied()
  val slots = ctx.timetable.getScheduleForDate(date, lessons, template)
  val agenda = ctx.agenda.observeAgenda().first().filter { item -> Dates.parseAppDate(item.date) == date }
  val homework = ctx.homework.observeHomeworks().first().filter { Dates.parseAppDate(it.dueDate) == date }
  val grades = ctx.grades.observeGrades().first().filter { Dates.parseAppDate(it.date) == date }

  line("giorno", "${Dates.label(date)}, ${Dates.longDay(date.dayOfWeek)}")
  if (slots.isEmpty()) {
    line("lezioni", "nessuna (giorno libero, o orario non ancora appreso dall'app)")
  } else if (full) {
    line("lezioni", slots.size)
    slots.forEach { slot ->
      line(
        "  ${slot.time} · ${slot.subject}" +
          (slot.teacher?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
          (slot.room?.takeIf { it.isNotBlank() }?.let { " · aula $it" } ?: "") +
          (if (slot.isPredicted) " · prevista" else "") +
          (slot.topic?.takeIf { it.isNotBlank() }?.let { " · argomento: ${Text.clip(it, 70)}" } ?: ""),
      )
    }
  } else {
    line("lezioni", "${slots.size}: " + slots.joinToString(", ") { it.subject })
  }

  val assessments = agenda.filter { it.category == AgendaCategory.ASSESSMENT }
  if (assessments.isNotEmpty()) {
    line("verifiche e interrogazioni", assessments.size)
    assessments.forEach { line("  " + it.toolLine()) }
  }
  if (homework.isNotEmpty()) {
    line("compiti per questo giorno", homework.size)
    homework.take(if (full) 12 else 4).forEach { h ->
      line("  ${h.subject} · ${Text.clip(h.description, if (full) 140 else 60)} · id ${h.id}")
    }
  }
  val others = agenda.filter { it.category != AgendaCategory.ASSESSMENT && it.category != AgendaCategory.HOMEWORK }
  if (others.isNotEmpty()) {
    line("altri impegni", others.size)
    others.forEach { line("  " + it.toolLine()) }
  }
  if (grades.isNotEmpty()) {
    line("voti presi quel giorno", grades.size)
    grades.forEach { line("  " + it.toolLine()) }
  }
  if (slots.isEmpty() && agenda.isEmpty() && homework.isEmpty() && grades.isEmpty()) {
    line("niente in programma e niente registrato per questo giorno")
  }
}

class SettimanaScuolaTool : AiTool<AssistantToolContext> {
  override val name = "settimana_scuola"
  override val group: AiToolGroup = RegistroToolGroup.GIORNATA
  override val description = "La settimana di scuola giorno per giorno (default: da oggi ai prossimi 7 giorni): materie, verifiche, compiti in scadenza. Per \"come'e' la settimana?\", \"quante verifiche ho?\""
  override val parameters = Schema.obj(
    mapOf(
      "da" to Schema.str("il primo giorno; vuoto = oggi"),
      "giorni" to Schema.int("quanti giorni (default 7)", 1, 14),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val start = Dates.parse(args.str("da"), ctx.today) ?: ctx.today
    val days = args.int("giorni") ?: 7
    return ToolText.output(maxChars = 3_800) {
      line("settimana", "${Dates.label(start)} → ${Dates.label(start.plusDays((days - 1).toLong()))}")
      (0 until days).forEach { offset ->
        val date = start.plusDays(offset.toLong())
        if (date.dayOfWeek == java.time.DayOfWeek.SUNDAY) return@forEach
        blank()
        day(ctx, date, full = false)
      }
    }
  }
}

/**
 * "Se prendo 7 in matematica, che media faccio?" — il conto lo fa l'app, non il modello: la media
 * simulata usa gli stessi pesi e la stessa formula di quella vera, altrimenti sono due numeri
 * diversi che dicono di essere la stessa cosa.
 */
class MediaSimulaTool : AiTool<AssistantToolContext> {
  override val name = "media_simula"
  override val group: AiToolGroup = RegistroToolGroup.GIORNATA
  override val description = "Simula la media di una materia aggiungendo uno o piu' voti ipotetici: dice la media di adesso e quella che verrebbe. Per \"se prendo 7 che media ho?\", \"quanti 8 mi servono per arrivare a 7?\""
  override val parameters = Schema.obj(
    mapOf(
      "materia" to Schema.str("nome della materia"),
      "voto" to Schema.str("il voto ipotetico, es. 7 oppure 7.5"),
      "quanti" to Schema.int("quante volte quel voto (default 1)", 1, 20),
      "periodo" to Schema.str("primo, secondo, corrente; vuoto per tutto l'anno"),
      "obiettivo" to Schema.str("in alternativa: la media da raggiungere, per sapere quanti voti come quello servono"),
    ),
    required = listOf("materia"),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val (grades, subject, error) = ctx.filteredGrades(args.str("materia"), args.str("periodo"), null, null)
    if (error != null) return ToolOutput.error(error)
    if (subject == null) return ToolOutput.error("materia mancante")
    val summary = GradeMath.summary(grades)
    val value = args.double("voto")
    val target = args.double("obiettivo")
    if (value == null && target == null) return ToolOutput.error("dimmi il voto ipotetico (voto) o la media da raggiungere (obiettivo)")
    return ToolText.output {
      line("materia", subject)
      line("media di adesso", GradeMath.format(summary.simple))
      line("voti contati", summary.counted)
      if (value != null) {
        val times = args.int("quanti") ?: 1
        val simulated = simulate(grades, value, times)
        line("con $times ${if (times == 1) "voto" else "voti"} da ${GradeMath.format(value)}", "media ${GradeMath.format(simulated)}")
        val delta = simulated - (summary.simple ?: simulated)
        line("differenza", (if (delta >= 0) "+" else "") + String.format(java.util.Locale.ROOT, "%.2f", delta))
      }
      if (target != null) {
        val needed = GradeMath.neededForSimple(grades, target)
        line("per arrivare a ${GradeMath.format(target)} con un voto solo", needed?.let { GradeMath.format(it) } ?: "non basta un voto solo")
        value?.let { v ->
          val count = GradeMath.countNeeded(grades, target, v)
          line("quanti voti da ${GradeMath.format(v)} servono", count?.toString() ?: "con quel voto la media non arriva all'obiettivo")
        }
      }
      line("nota", "la simulazione usa la media semplice, quella che l'app mostra")
    }
  }

  /** La media semplice con in piu' [times] voti da [value]: solo i voti con un numero contano. */
  private fun simulate(grades: List<dev.antigravity.classevivaexpressive.core.domain.model.Grade>, value: Double, times: Int): Double {
    val values = grades.mapNotNull { it.numericValue } + List(times) { value }
    return values.average()
  }
}

fun dayTools(): List<AiTool<AssistantToolContext>> = listOf(GiornataScuolaTool(), SettimanaScuolaTool(), MediaSimulaTool())
