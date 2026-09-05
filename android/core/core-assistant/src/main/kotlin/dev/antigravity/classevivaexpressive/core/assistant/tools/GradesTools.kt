package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.math.GradeMath
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.double
import dev.antigravity.fluidengine.ai.tools.Args.int
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/** I voti filtrati come li chiede il modello: materia (con nome risolto), periodo, intervallo di date. */
internal suspend fun AssistantToolContext.filteredGrades(
  subjectArg: String?,
  periodArg: String?,
  from: String?,
  to: String?,
): Triple<List<Grade>, String?, String?> {
  val all = grades.observeGrades().first()
  val subject = Subjects.match(subjectArg, all.map { it.subject } + grades.observeSubjects().first().map { it.description })
  if (subjectArg != null && subject == null) return Triple(emptyList(), null, "materia \"$subjectArg\" non trovata")
  val period = matchPeriod(periodArg)
  if (periodArg != null && period == null) return Triple(emptyList(), subject, "periodo \"$periodArg\" non trovato")
  var list = all
  if (subject != null) list = list.filter { Text.normalize(it.subject) == Text.normalize(subject) }
  if (period != null) {
    val start = Dates.parseAppDate(period.startDate)
    val end = Dates.parseAppDate(period.endDate)
    list = list.filter { grade ->
      val code = grade.periodCode
      if (code != null) code == period.code
      else {
        val date = Dates.parseAppDate(grade.date)
        date != null && start != null && end != null && !date.isBefore(start) && !date.isAfter(end)
      }
    }
  }
  if (from != null || to != null) {
    val range = Dates.range(from, to ?: if (from != null) "oggi" else null, today, defaultDays = 0)
    list = list.filter { grade -> Dates.parseAppDate(grade.date)?.let { it in range } == true }
  }
  return Triple(list.sortedByDescending { it.date }, subject, null)
}

class VotiElencoTool : AiTool<AssistantToolContext> {
  override val name = "voti_elenco"
  override val group: AiToolGroup = RegistroToolGroup.VOTI
  override val description = "I voti presi, dal piu' recente: per materia, periodo o intervallo di date; con l'id per aprirne il dettaglio"
  override val parameters = Schema.obj(
    mapOf(
      "materia" to Schema.str("nome della materia (anche abbreviato), vuoto per tutte"),
      "periodo" to Schema.str("primo, secondo, corrente, o il codice; vuoto per tutto l'anno"),
      "da" to Schema.str("data di inizio (aaaa-mm-gg, oggi, ieri, lunedi...)"),
      "a" to Schema.str("data di fine"),
      "ultimi" to Schema.int("quanti voti al massimo (default 15)", 1, 40),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val (grades, subject, error) = ctx.filteredGrades(args.str("materia"), args.str("periodo"), args.str("da"), args.str("a"))
    if (error != null) return ToolOutput.error(error)
    val limit = args.int("ultimi") ?: 15
    return ToolText.output {
      line("materia", subject ?: "tutte")
      line("voti trovati", grades.size)
      grades.take(limit).forEach { line(it.toolLine(withSubject = subject == null)) }
      if (grades.size > limit) line("… altri ${grades.size - limit} voti piu' vecchi")
    }
  }
}

class VotiMediaTool : AiTool<AssistantToolContext> {
  override val name = "voti_media"
  override val group: AiToolGroup = RegistroToolGroup.VOTI
  override val description = "La media dei voti calcolata dall'app: generale e per materia, o di una materia (semplice e ponderata, minimo, massimo, ultimo), in un periodo"
  override val parameters = Schema.obj(
    mapOf(
      "materia" to Schema.str("nome della materia, vuoto per la media generale con il dettaglio per materia"),
      "periodo" to Schema.str("primo, secondo, corrente, o il codice; vuoto per tutto l'anno"),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val (grades, subject, error) = ctx.filteredGrades(args.str("materia"), args.str("periodo"), null, null)
    if (error != null) return ToolOutput.error(error)
    if (grades.isEmpty()) return ToolOutput("nessun voto${subject?.let { " in $it" } ?: ""} nel periodo richiesto")
    val goals = ctx.grades.observeSubjectGoals().first()
    return ToolText.output {
      if (subject == null) {
        val summary = GradeMath.summary(grades)
        line("media generale (semplice, come nell'app)", GradeMath.format(summary.simple))
        if (summary.weightedDiffers) line("media generale ponderata col peso dei voti", GradeMath.format(summary.weighted))
        line("voti con valore numerico", "${summary.counted} su ${summary.count}")
        line("insufficienze", summary.insufficient)
        blank()
        line("per materia (media semplice · n. voti · ultimo):")
        grades.groupBy { it.subject }.toSortedMap().forEach { (name, list) ->
          val s = GradeMath.summary(list)
          val goal = goals.firstOrNull { Text.normalize(it.subject) == Text.normalize(name) }
          line("$name: ${GradeMath.format(s.simple)} · ${s.counted} · ultimo ${GradeMath.format(s.last)}" + (goal?.let { " · obiettivo ${GradeMath.format(it.targetAverage)}" } ?: ""))
        }
      } else {
        val summary = GradeMath.summary(grades)
        line("materia", subject)
        line("media semplice (quella dell'app)", GradeMath.format(summary.simple))
        if (summary.weightedDiffers) line("media ponderata col peso dei voti", GradeMath.format(summary.weighted))
        line("voti", "${summary.counted} con valore numerico su ${summary.count}")
        line("minimo", GradeMath.format(summary.min))
        line("massimo", GradeMath.format(summary.max))
        line("ultimo voto", GradeMath.format(summary.last))
        line("insufficienze", summary.insufficient)
        goals.firstOrNull { Text.normalize(it.subject) == Text.normalize(subject) }?.let { line("obiettivo salvato", GradeMath.format(it.targetAverage)) }
        blank()
        line("ultimi voti:")
        grades.take(6).forEach { line(it.toolLine(withSubject = false)) }
      }
    }
  }
}

class VotiServeTool : AiTool<AssistantToolContext> {
  override val name = "voti_serve"
  override val group: AiToolGroup = RegistroToolGroup.VOTI
  override val description = "Che voto serve al prossimo compito perche' la media di una materia arrivi a un obiettivo (calcolo fatto dall'app)"
  override val parameters = Schema.obj(
    mapOf(
      "materia" to Schema.str("nome della materia"),
      "obiettivo" to Schema.str("la media da raggiungere, es. 6 o 7.5"),
      "periodo" to Schema.str("primo, secondo, corrente; vuoto per tutto l'anno"),
      "peso" to Schema.str("peso del prossimo voto, di solito 1"),
    ),
    required = listOf("materia", "obiettivo"),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val target = args.double("obiettivo") ?: return ToolOutput.error("obiettivo mancante")
    val weight = args.double("peso") ?: 1.0
    val (grades, subject, error) = ctx.filteredGrades(args.str("materia"), args.str("periodo"), null, null)
    if (error != null) return ToolOutput.error(error)
    if (subject == null) return ToolOutput.error("serve la materia")
    if (grades.none { it.numericValue != null }) return ToolOutput("nessun voto numerico in $subject: qualsiasi voto fara' media da solo")
    val summary = GradeMath.summary(grades)
    val neededSimple = GradeMath.neededForSimple(grades, target)
    val neededWeighted = GradeMath.neededForWeighted(grades, target, weight)
    return ToolText.output {
      line("materia", subject)
      line("media attuale (semplice)", GradeMath.format(summary.simple))
      line("obiettivo", GradeMath.format(target))
      line("voto necessario al prossimo (media semplice)", GradeMath.format(neededSimple))
      if (summary.weightedDiffers || weight != 1.0) line("voto necessario al prossimo (ponderata, peso ${GradeWeights.format(weight)})", GradeMath.format(neededWeighted))
      val needed = neededSimple ?: 0.0
      when {
        (summary.simple ?: 0.0) >= target -> line("nota", "la media e' gia' all'obiettivo: basta non scendere")
        needed <= 10.0 -> line("nota", "ci si arriva con un voto solo")
        else -> {
          line("nota", "un voto solo non basta (servirebbe ${GradeMath.format(needed)})")
          listOf(7.0, 8.0, 9.0, 10.0).forEach { v ->
            GradeMath.countNeeded(grades, target, v)?.let { count -> line("con voti da ${GradeMath.format(v)}", "ne servono $count di fila") }
          }
        }
      }
    }
  }
}

class VotiDettaglioTool : AiTool<AssistantToolContext> {
  override val name = "voti_dettaglio"
  override val group: AiToolGroup = RegistroToolGroup.VOTI
  override val description = "Tutto di un voto dato il suo id: materia, valore, tipo, peso, data, descrizione, note del docente, modifiche"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id del voto, da voti_elenco")), required = listOf("id"))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val id = args.str("id") ?: return ToolOutput.error("id mancante")
    val grade = ctx.grades.observeGrades().first().firstOrNull { it.id == id } ?: return ToolOutput.error("voto $id non trovato")
    return ToolText.output {
      line("id", grade.id)
      line("materia", grade.subject)
      line("voto", grade.valueLabel + (grade.numericValue?.let { " (${GradeMath.format(it)})" } ?: " (senza valore numerico)"))
      line("tipo", grade.type)
      line("peso", GradeWeights.format(GradeWeights.effective(grade)))
      line("data", Dates.label(grade.date))
      line("periodo", grade.period ?: grade.periodCode)
      line("docente", grade.teacher)
      line("descrizione", grade.description)
      line("note del docente", grade.notes)
      if (grade.history.isNotEmpty()) {
        blank()
        line("modifiche registrate", grade.history.size)
        grade.history.takeLast(3).forEach { line("- ${it.valueLabel} il ${Dates.label(it.date)}${it.description?.let { d -> " · $d" } ?: ""}") }
      }
    }
  }
}

class MaterieTool : AiTool<AssistantToolContext> {
  override val name = "materie"
  override val group: AiToolGroup = RegistroToolGroup.VOTI
  override val description = "Le materie dell'anno con i docenti, quanti voti hanno e la media"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val subjects = ctx.grades.observeSubjects().first().sortedBy { it.order }
    val grades = ctx.grades.observeGrades().first()
    if (subjects.isEmpty() && grades.isEmpty()) return ToolOutput("nessuna materia nel registro (dati non ancora sincronizzati?)")
    return ToolText.output {
      val names = if (subjects.isNotEmpty()) subjects.map { it.description to it.teachers } else grades.map { it.subject }.distinct().map { it to emptyList() }
      names.forEach { (name, teachers) ->
        val own = grades.filter { Text.normalize(it.subject) == Text.normalize(name) }
        val teacherLabel = teachers.ifEmpty { own.mapNotNull { it.teacher }.distinct() }.joinToString(", ")
        line("$name: ${own.size} voti, media ${GradeMath.format(GradeMath.simpleAverage(own))}" + (if (teacherLabel.isNotBlank()) " · $teacherLabel" else ""))
      }
    }
  }
}

class ObiettiviTool : AiTool<AssistantToolContext> {
  override val name = "obiettivi"
  override val group: AiToolGroup = RegistroToolGroup.VOTI
  override val description = "Gli obiettivi di media salvati per materia, con la media attuale e la distanza"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val goals = ctx.grades.observeSubjectGoals().first()
    if (goals.isEmpty()) return ToolOutput("nessun obiettivo salvato (si aggiunge con obiettivo_salva, se le azioni sono attive)")
    val grades = ctx.grades.observeGrades().first()
    return ToolText.output {
      goals.forEach { goal ->
        val own = grades.filter { Text.normalize(it.subject) == Text.normalize(goal.subject) && (goal.periodCode == null || it.periodCode == goal.periodCode) }
        val current = GradeMath.simpleAverage(own)
        val gap = current?.let { goal.targetAverage - it }
        line("${goal.subject}${goal.periodCode?.let { " ($it)" } ?: ""}: obiettivo ${GradeMath.format(goal.targetAverage)}, media ${GradeMath.format(current)}" + (gap?.let { if (it > 0) ", mancano ${GradeMath.format(it)}" else ", raggiunto" } ?: ""))
      }
    }
  }
}
