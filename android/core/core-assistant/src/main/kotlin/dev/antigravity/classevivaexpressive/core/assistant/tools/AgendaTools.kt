package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.int
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

private fun AgendaCategory.label(): String = when (this) {
  AgendaCategory.LESSON -> "lezione"
  AgendaCategory.HOMEWORK -> "compito"
  AgendaCategory.ASSESSMENT -> "verifica/interrogazione"
  AgendaCategory.EVENT -> "evento"
  AgendaCategory.CUSTOM -> "personale"
}

private fun categoryOf(raw: String?): AgendaCategory? {
  val t = Text.normalize(raw ?: return null)
  return when {
    t.startsWith("verific") || t.startsWith("interrog") || t.startsWith("test") || t.startsWith("valutaz") -> AgendaCategory.ASSESSMENT
    t.startsWith("compit") -> AgendaCategory.HOMEWORK
    t.startsWith("event") -> AgendaCategory.EVENT
    t.startsWith("personal") || t.startsWith("mio") || t.startsWith("mie") -> AgendaCategory.CUSTOM
    t.startsWith("lezion") -> AgendaCategory.LESSON
    else -> null
  }
}

internal fun AgendaItem.toolLine(): String = buildString {
  append(Dates.label(date))
  time?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
  append(" · ").append(category.label())
  subject?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
  append(" · ").append(Text.clip(title, 90))
  if (subtitle.isNotBlank() && !title.contains(subtitle)) append(" · ").append(Text.clip(subtitle, 60))
  teacher?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
  append(" · id ").append(id)
}

class ImpegniTool : AiTool<AssistantToolContext> {
  override val name = "impegni"
  override val group: AiToolGroup = RegistroToolGroup.AGENDA
  override val description = "Gli impegni in agenda in un intervallo di date (default: i prossimi 7 giorni): verifiche, interrogazioni, compiti, eventi, eventi personali"
  override val parameters = Schema.obj(
    mapOf(
      "da" to Schema.str("data di inizio (aaaa-mm-gg, oggi, domani, lunedi...); vuoto = oggi"),
      "a" to Schema.str("data di fine; vuoto = 7 giorni dopo l'inizio"),
      "tipo" to Schema.str("filtro: verifica, compito, evento, personale; vuoto per tutti"),
      "materia" to Schema.str("nome della materia, vuoto per tutte"),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val range = Dates.range(args.str("da"), args.str("a"), ctx.today, defaultDays = 7)
    val category = categoryOf(args.str("tipo"))
    val subjectArg = args.str("materia")
    val items = ctx.agenda.observeAgenda().first()
    val subject = Subjects.match(subjectArg, items.mapNotNull { it.subject })
    if (subjectArg != null && subject == null) return ToolOutput.error("materia \"$subjectArg\" non trovata in agenda")
    val filtered = items
      .filter { item -> Dates.parseAppDate(item.date)?.let { it in range } == true }
      .filter { category == null || it.category == category }
      .filter { subject == null || Text.normalize(it.subject.orEmpty()) == Text.normalize(subject) }
      .sortedWith(compareBy({ it.date }, { it.time ?: "" }))
    return ToolText.output {
      line("intervallo", "${Dates.label(range.start)} → ${Dates.label(range.endInclusive)}")
      line("impegni", filtered.size)
      if (filtered.isEmpty()) line("nessun impegno in agenda in questo intervallo")
      filtered.take(30).forEach { line(it.toolLine()) }
      if (filtered.size > 30) line("… altri ${filtered.size - 30}")
    }
  }
}

class VerificheProssimeTool : AiTool<AssistantToolContext> {
  override val name = "verifiche_prossime"
  override val group: AiToolGroup = RegistroToolGroup.AGENDA
  override val description = "Le verifiche e interrogazioni in arrivo nei prossimi giorni (default 14), dalla piu' vicina"
  override val parameters = Schema.obj(mapOf("giorni" to Schema.int("quanti giorni guardare avanti", 1, 120)))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val days = (args.int("giorni") ?: 14).toLong()
    val range = ctx.today..ctx.today.plusDays(days)
    val items = ctx.agenda.observeAgenda().first()
      .filter { it.category == AgendaCategory.ASSESSMENT }
      .filter { item -> Dates.parseAppDate(item.date)?.let { it in range } == true }
      .sortedWith(compareBy({ it.date }, { it.time ?: "" }))
    return ToolText.output {
      line("fino a", Dates.label(range.endInclusive))
      line("verifiche in arrivo", items.size)
      if (items.isEmpty()) line("nessuna verifica o interrogazione segnata in agenda in questo intervallo")
      items.take(25).forEach { line(it.toolLine()) }
    }
  }
}

class CompitiTool : AiTool<AssistantToolContext> {
  override val name = "compiti"
  override val group: AiToolGroup = RegistroToolGroup.AGENDA
  override val description = "I compiti assegnati con la scadenza, in un intervallo (default: da oggi a 7 giorni), con l'id per il testo completo"
  override val parameters = Schema.obj(
    mapOf(
      "da" to Schema.str("data di inizio della scadenza; vuoto = oggi"),
      "a" to Schema.str("data di fine; vuoto = 7 giorni dopo"),
      "materia" to Schema.str("nome della materia, vuoto per tutte"),
    ),
  )

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val range = Dates.range(args.str("da"), args.str("a"), ctx.today, defaultDays = 7)
    val all = ctx.homework.observeHomeworks().first()
    val subjectArg = args.str("materia")
    val subject = Subjects.match(subjectArg, all.map { it.subject })
    if (subjectArg != null && subject == null) return ToolOutput.error("materia \"$subjectArg\" non trovata fra i compiti")
    val items = all
      .filter { hw -> Dates.parseAppDate(hw.dueDate)?.let { it in range } == true }
      .filter { subject == null || Text.normalize(it.subject) == Text.normalize(subject) }
      .sortedBy { it.dueDate }
    return ToolText.output {
      line("scadenze fra", "${Dates.label(range.start)} e ${Dates.label(range.endInclusive)}")
      line("compiti", items.size)
      if (items.isEmpty()) line("nessun compito con scadenza in questo intervallo")
      items.take(25).forEach { hw ->
        line("${Dates.label(hw.dueDate)} · ${hw.subject} · ${Text.clip(hw.description, 120)}${if (hw.attachments.isNotEmpty()) " · ${hw.attachments.size} allegati" else ""} · id ${hw.id}")
      }
    }
  }
}

class CompitoDettaglioTool : AiTool<AssistantToolContext> {
  override val name = "compito_dettaglio"
  override val group: AiToolGroup = RegistroToolGroup.AGENDA
  override val description = "Il testo completo di un compito dato il suo id, con docente, data di assegnazione e allegati"
  override val parameters = Schema.obj(mapOf("id" to Schema.str("l'id del compito, da compiti")), required = listOf("id"))

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val id = args.str("id") ?: return ToolOutput.error("id mancante")
    val homework = ctx.homework.observeHomeworks().first().firstOrNull { it.id == id } ?: return ToolOutput.error("compito $id non trovato")
    val detail = ctx.homework.getHomeworkDetail(id).getOrNull()
    return ToolText.output {
      line("materia", homework.subject)
      line("scadenza", Dates.label(homework.dueDate))
      line("assegnato il", detail?.assignedDate?.let { Dates.label(it) } ?: homework.createdAt?.let { Dates.label(it) })
      line("docente", detail?.teacher)
      line("testo", Text.clip(detail?.fullText?.takeIf { it.isNotBlank() } ?: homework.description, 1500))
      homework.notes?.takeIf { it.isNotBlank() }?.let { line("note", Text.clip(it, 300)) }
      if (homework.attachments.isNotEmpty()) line("allegati", homework.attachments.joinToString("; ") { it.name })
    }
  }
}

class EventiPersonaliTool : AiTool<AssistantToolContext> {
  override val name = "eventi_personali"
  override val group: AiToolGroup = RegistroToolGroup.AGENDA
  override val description = "Gli eventi personali che lo studente ha aggiunto da solo all'agenda, da oggi in avanti"
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: AssistantToolContext): ToolOutput {
    val events = ctx.agenda.observeCustomEvents().first()
      .filter { event -> Dates.parseAppDate(event.date)?.let { !it.isBefore(ctx.today) } == true }
      .sortedWith(compareBy({ it.date }, { it.time ?: "" }))
    if (events.isEmpty()) return ToolOutput("nessun evento personale in arrivo")
    return ToolText.output {
      events.take(25).forEach { e ->
        line("${Dates.label(e.date)}${e.time?.let { " $it" } ?: ""} · ${e.title}${if (e.subject.isNotBlank()) " · ${e.subject}" else ""}${if (e.description.isNotBlank()) " · ${Text.clip(e.description, 80)}" else ""} · id ${e.id}")
      }
    }
  }
}
