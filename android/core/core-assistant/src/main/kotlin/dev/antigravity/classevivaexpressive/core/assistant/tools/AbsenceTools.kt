package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.bool
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

private fun AbsenceType.label(): String = when (this) {
  AbsenceType.ABSENCE -> "assenza"
  AbsenceType.LATE -> "ritardo"
  AbsenceType.EXIT -> "uscita anticipata"
}

private fun typeOf(raw: String?): AbsenceType? {
  val t = Text.normalize(raw ?: return null)
  return when {
    t.startsWith("assen") -> AbsenceType.ABSENCE
    t.startsWith("ritard") -> AbsenceType.LATE
    t.startsWith("uscit") -> AbsenceType.EXIT
    else -> null
  }
}

private fun AbsenceRecord.toolLine(): String = buildString {
  append(Dates.label(date)).append(" · ").append(type.label())
  hours?.let { append(" · ").append(it).append(if (it == 1) " ora" else " ore") }
  append(" · ").append(if (justified) "giustificata" else if (canJustify) "DA GIUSTIFICARE" else "non giustificata")
  justificationReason?.takeIf { it.isNotBlank() }?.let { append(" · ").append(Text.clip(it, 60)) }
  append(" · id ").append(id)
}

class AssenzeElencoTool : AiTool<AssistantToolContext> {
  override val name = "assenze_elenco"
  override val group: AiToolGroup = RegistroToolGroup.ASSENZE
  override val description = "Assenze, ritardi e uscite anticipate dell'anno, dalla piu' recente, con lo stato della giustificazione"
  override val parameters = Schema.obj(
    mapOf(
      "tipo" to Schema.str("assenza, ritardo, uscita; vuoto per tutte"),
      "da" to Schema.str("data di inizio; vuoto = tutto l'anno"),
      "a" to Schema.str("data di fine"),
      "da_giustificare" to Schema.bool("vero per vedere solo quelle ancora da giustificare"),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val type = typeOf(args.str("tipo"))
    val from = Dates.parse(args.str("da"), ctx.today)
    val to = Dates.parse(args.str("a"), ctx.today)
    val onlyPending = args.bool("da_giustificare") ?: false
    val records = ctx.absences.observeAbsences().first()
      .filter { type == null || it.type == type }
      .filter { r -> from == null || Dates.parseAppDate(r.date)?.let { !it.isBefore(from) } == true }
      .filter { r -> to == null || Dates.parseAppDate(r.date)?.let { !it.isAfter(to) } == true }
      .filter { !onlyPending || (!it.justified) }
      .sortedByDescending { it.date }
    return ToolText.output {
      line("trovate", records.size)
      if (records.isEmpty()) line("nessuna" + (type?.let { " ${it.label()}" } ?: " assenza") + " in questo intervallo")
      records.take(25).forEach { line(it.toolLine()) }
      if (records.size > 25) line("… altre ${records.size - 25}")
    }
  }
}

class AssenzeRiepilogoTool : AiTool<AssistantToolContext> {
  override val name = "assenze_riepilogo"
  override val group: AiToolGroup = RegistroToolGroup.ASSENZE
  override val description = "Il riepilogo dell'anno: quante assenze, ritardi e uscite, quante da giustificare, le ore totali, l'ultima"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val records = ctx.absences.observeAbsences().first()
    if (records.isEmpty()) return ToolOutput("nessuna assenza, ritardo o uscita registrata quest'anno")
    return ToolText.output {
      AbsenceType.entries.forEach { type ->
        val own = records.filter { it.type == type }
        if (own.isNotEmpty()) {
          val pending = own.count { !it.justified }
          line("${type.label()}: ${own.size}" + (if (pending > 0) ", $pending da giustificare" else ", tutte giustificate"))
        }
      }
      val hours = records.filter { it.type == AbsenceType.ABSENCE }.sumOf { it.hours ?: 0 }
      if (hours > 0) line("ore di assenza", hours)
      line("da giustificare in tutto", records.count { !it.justified })
      records.maxByOrNull { it.date }?.let { line("ultima", it.toolLine()) }
      val byMonth = records.groupingBy { it.date.take(7) }.eachCount().toSortedMap()
      if (byMonth.size > 1) line("per mese", byMonth.entries.joinToString(", ") { "${it.key}: ${it.value}" })
    }
  }
}
