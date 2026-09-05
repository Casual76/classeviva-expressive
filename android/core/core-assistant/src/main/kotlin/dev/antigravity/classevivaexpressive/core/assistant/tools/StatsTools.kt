package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.math.GradeMath
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

class StatisticheTool : AiTool<AssistantToolContext> {
  override val name = "statistiche"
  override val group: AiToolGroup = RegistroToolGroup.STATISTICHE
  override val description = "Le statistiche dell'anno calcolate dall'app: media generale, distribuzione dei voti, andamento recente, assenze, carico di lavoro"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val stats = ctx.stats.observeStats().first()
    return ToolText.output {
      line("media generale", GradeMath.format(stats.overallAverage))
      val d = stats.gradeDistribution
      line("distribuzione", "insufficienti ${d.insufficient}, sufficienti ${d.sufficient}, buoni ${d.good}, molto buoni ${d.veryGood}, eccellenti ${d.excellent}")
      if (stats.gradeTrend.isNotEmpty()) line("ultimi voti in ordine", stats.gradeTrend.joinToString(", ") { "${it.label} ${GradeMath.format(it.value)}" })
      if (stats.absenceBreakdown.isNotEmpty()) line("presenze", stats.absenceBreakdown.entries.joinToString(", ") { "${it.key.lowercase()}: ${it.value}" })
      if (stats.workloadBreakdown.isNotEmpty()) line("agenda per tipo", stats.workloadBreakdown.entries.joinToString(", ") { "${it.key.lowercase()}: ${it.value}" })
      val best = stats.subjectSummaries.filter { it.average != null }.sortedByDescending { it.average }
      if (best.isNotEmpty()) {
        line("materie migliori", best.take(3).joinToString(", ") { "${it.subject} ${GradeMath.format(it.average)}" })
        line("materie piu' deboli", best.takeLast(3).reversed().joinToString(", ") { "${it.subject} ${GradeMath.format(it.average)}" })
      }
    }
  }
}

class ProfessoreTool : AiTool<AssistantToolContext> {
  override val name = "professore"
  override val group: AiToolGroup = RegistroToolGroup.STATISTICHE
  override val description = "Cosa il registro sa di un docente: materie, lezioni firmate, presenza, quanti voti ha dato e con che media, tipo di verifica piu' usato; senza nome, l'elenco dei docenti"
  override val parameters = Schema.obj(mapOf("nome" to Schema.str("cognome o nome del docente (anche parziale); vuoto per l'elenco")))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val lessons = ctx.lessons.observeLessons().first()
    val grades = ctx.grades.observeGrades().first()
    val subjects = ctx.grades.observeSubjects().first()
    val teachers = (lessons.mapNotNull { it.teacher } + grades.mapNotNull { it.teacher } + subjects.flatMap { it.teachers })
      .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { Text.normalize(it) }
    val query = args.str("nome")
    if (query == null) {
      if (teachers.isEmpty()) return ToolOutput("nessun docente nel registro (dati non ancora sincronizzati?)")
      return ToolText.output {
        teachers.sorted().forEach { teacher ->
          val taught = (lessons.filter { it.teacher?.let { t -> Text.normalize(t) == Text.normalize(teacher) } == true }.map { it.subject } +
            subjects.filter { s -> s.teachers.any { Text.normalize(it) == Text.normalize(teacher) } }.map { it.description }).distinct()
          line("$teacher: ${taught.joinToString(", ").ifBlank { "materia non nota" }}")
        }
      }
    }
    val teacher = teachers.firstOrNull { Text.normalize(it) == Text.normalize(query) }
      ?: teachers.firstOrNull { Text.matches(query, it) }
      ?: return ToolOutput.error("docente \"$query\" non trovato; docenti noti: ${teachers.take(20).joinToString(", ")}")
    val own = lessons.filter { it.teacher?.let { t -> Text.normalize(t) == Text.normalize(teacher) } == true }
    val ownGrades = grades.filter { it.teacher?.let { t -> Text.normalize(t) == Text.normalize(teacher) } == true }
    val taught = (own.map { it.subject } + subjects.filter { s -> s.teachers.any { Text.normalize(it) == Text.normalize(teacher) } }.map { it.description }).distinct()
    return ToolText.output {
      line("docente", teacher)
      line("materie", taught.joinToString(", ").ifBlank { "—" })
      line("lezioni firmate", own.size)
      if (own.isNotEmpty()) {
        val days = own.map { it.date }.distinct().size
        line("giorni con lezione", days)
        line("lezioni con argomento scritto", "${own.count { !it.topic.isNullOrBlank() }} su ${own.size}")
        own.maxByOrNull { it.date }?.let { line("ultima lezione", "${Dates.label(it.date)}${it.topic?.let { t -> " · ${Text.clip(t, 80)}" } ?: ""}") }
      }
      if (ownGrades.isNotEmpty()) {
        val summary = GradeMath.summary(ownGrades)
        line("voti dati", ownGrades.size)
        line("media dei voti dati", GradeMath.format(summary.simple))
        ownGrades.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.let { line("tipo di valutazione piu' usato", "${it.key} (${it.value})") }
        line("peso medio dei voti", GradeWeights.format(ownGrades.map { GradeWeights.effective(it) }.average()))
      } else {
        line("voti dati", "nessuno con il suo nome")
      }
    }
  }
}

class PunteggioStudenteTool : AiTool<AssistantToolContext> {
  override val name = "punteggio_studente"
  override val group: AiToolGroup = RegistroToolGroup.STATISTICHE
  override val description = "Il punteggio studente dell'app (un indice giocoso su voti, presenze e regolarita') con le sue componenti"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val score = ctx.studentScore.observeCurrentScore().first() ?: return ToolOutput("punteggio non ancora calcolato: si apre dalla pagina Punteggio studente")
    return ToolText.output {
      line("punteggio", "${GradeMath.format(score.score)} · ${score.label}")
      score.components.forEach { c -> line("${c.title}: ${GradeMath.format(c.value)} su ${GradeMath.format(c.maxValue)} (peso ${GradeMath.format(c.weight)})") }
    }
  }
}
