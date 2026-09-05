package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

class OrarioGiornoTool : AiTool<AssistantToolContext> {
  override val name = "orario_giorno"
  override val group: AiToolGroup = RegistroToolGroup.ORARIO
  override val description = "L'orario delle lezioni di un giorno (default oggi): ora per ora, con docente e aula; le ore previste dal modello dell'app sono segnate"
  override val parameters = Schema.obj(mapOf("data" to Schema.str("il giorno (aaaa-mm-gg, oggi, domani, lunedi...); vuoto = oggi")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val date = Dates.parse(args.str("data"), ctx.today) ?: ctx.today
    val lessons = ctx.lessons.observeLessons().first()
    val template = ctx.lessons.observeTimetableTemplate().first().withOverridesApplied()
    val slots = ctx.timetable.getScheduleForDate(date, lessons, template)
    return ToolText.output {
      line("giorno", "${Dates.label(date)}, ${Dates.longDay(date.dayOfWeek)}")
      if (slots.isEmpty()) {
        line("nessuna lezione in orario (giorno libero, o orario non ancora appreso dall'app)")
        return@output
      }
      line("lezioni", slots.size)
      slots.forEach { slot ->
        line(
          "${slot.time} · ${slot.subject}" +
            (slot.teacher?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
            (slot.room?.takeIf { it.isNotBlank() }?.let { " · aula $it" } ?: "") +
            (if (slot.isPredicted) " · prevista" else "") +
            (slot.topic?.takeIf { it.isNotBlank() }?.let { " · argomento: ${Text.clip(it, 80)}" } ?: ""),
        )
      }
    }
  }
}

class OrarioSettimanaTool : AiTool<AssistantToolContext> {
  override val name = "orario_settimana"
  override val group: AiToolGroup = RegistroToolGroup.ORARIO
  override val description = "L'orario settimanale tipo (il modello che l'app ha appreso dalle lezioni, o quello ufficiale): per ogni giorno le materie in ordine"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val template = ctx.lessons.observeTimetableTemplate().first().withOverridesApplied()
    if (template.slots.isEmpty()) return ToolOutput("orario non disponibile: l'app non ha ancora abbastanza lezioni per ricostruirlo")
    return ToolText.output {
      line("origine", if (template.isOfficial) "orario ufficiale" else "appreso da ${template.sampledWeeks} settimane di lezioni")
      template.slotsByDay().toSortedMap().forEach { (day, slots) ->
        line("${Dates.longDay(day)}: " + slots.joinToString("; ") { "${it.time} ${it.subject}" })
      }
      DayOfWeek.entries.filter { !template.hasLessonsOn(it) && it != DayOfWeek.SUNDAY }.takeIf { it.isNotEmpty() }?.let { free ->
        line("giorni senza lezioni", free.joinToString(", ") { Dates.longDay(it) })
      }
    }
  }
}

class LezioniSvolteTool : AiTool<AssistantToolContext> {
  override val name = "lezioni_svolte"
  override val group: AiToolGroup = RegistroToolGroup.ORARIO
  override val description = "Le lezioni svolte con gli argomenti firmati dai docenti, in un intervallo (default: gli ultimi 7 giorni), anche per materia"
  override val parameters = Schema.obj(
    mapOf(
      "materia" to Schema.str("nome della materia, vuoto per tutte"),
      "da" to Schema.str("data di inizio; vuoto = 7 giorni fa"),
      "a" to Schema.str("data di fine; vuoto = oggi"),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val from = Dates.parse(args.str("da"), ctx.today) ?: ctx.today.minusDays(7)
    val to = Dates.parse(args.str("a"), ctx.today) ?: ctx.today
    val range = if (to < from) to..from else from..to
    val all = ctx.lessons.observeLessons().first()
    val subjectArg = args.str("materia")
    val subject = Subjects.match(subjectArg, all.map { it.subject })
    if (subjectArg != null && subject == null) return ToolOutput.error("materia \"$subjectArg\" non trovata fra le lezioni")
    val lessons = all
      .filter { lesson -> Dates.parseAppDate(lesson.date)?.let { it in range } == true }
      .filter { subject == null || Text.normalize(it.subject) == Text.normalize(subject) }
      .sortedWith(compareByDescending<dev.antigravity.classevivaexpressive.core.domain.model.Lesson> { it.date }.thenBy { it.time })
    return ToolText.output {
      line("intervallo", "${Dates.label(range.start)} → ${Dates.label(range.endInclusive)}")
      line("lezioni", lessons.size)
      if (lessons.isEmpty()) line("nessuna lezione registrata in questo intervallo")
      lessons.take(30).forEach { lesson ->
        line(
          "${Dates.label(lesson.date)} ${lesson.time} · ${lesson.subject}" +
            (lesson.teacher?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
            (lesson.topic?.takeIf { it.isNotBlank() }?.let { " · ${Text.clip(it, 100)}" } ?: " · (senza argomento)"),
        )
      }
      if (lessons.size > 30) line("… altre ${lessons.size - 30} lezioni")
    }
  }
}
