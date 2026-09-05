package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantActionSink
import dev.antigravity.classevivaexpressive.core.assistant.attachments.AttachmentReader
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Period
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StatsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import dev.antigravity.fluidengine.ai.provider.ModelCapabilities
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Cosa un tool puo' toccare mentre gira: i repository del registro (che leggono da Room, quindi
 * anche offline), l'ora, le azioni. Costruito per ogni domanda dal runtime, con le capacita' del
 * modello profondo che decidono come restituire un allegato.
 */
class AssistantToolContext(
  val grades: GradesRepository,
  val agenda: AgendaRepository,
  val homework: HomeworkRepository,
  val lessons: LessonsRepository,
  val communications: CommunicationsRepository,
  val absences: AbsencesRepository,
  val stats: StatsRepository,
  val studentScore: StudentScoreRepository,
  val materials: MaterialsRepository,
  val documents: DocumentsRepository,
  val dashboard: DashboardRepository,
  val settings: SettingsRepository,
  val timetable: PredictiveTimetableUseCase,
  val attachments: AttachmentReader,
  val zone: ZoneId,
  val today: LocalDate,
  val actionsEnabled: Boolean,
  val actions: AssistantActionSink,
  /** Cosa il modello profondo del provider in uso sa prendere: decide la forma di un allegato. */
  val deepCapabilities: ModelCapabilities,
) {
  private val traceList = java.util.Collections.synchronizedList(mutableListOf<AssistantToolTrace>())

  /** Le chiamate agli strumenti di questa domanda, nell'ordine in cui sono finite. */
  val traces: List<AssistantToolTrace> get() = synchronized(traceList) { traceList.toList() }

  fun trace(trace: AssistantToolTrace) {
    traceList += trace
  }

  /** Il periodo in corso oggi, se i periodi del registro lo dicono. */
  suspend fun currentPeriod(): Period? {
    val periods = grades.observePeriods().first()
    return periods.firstOrNull { period ->
      val start = Dates.parseAppDate(period.startDate)
      val end = Dates.parseAppDate(period.endDate)
      start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
    }
  }

  /** Un periodo come lo dice lo studente: "primo", "1", "trimestre", il codice, l'etichetta. */
  suspend fun matchPeriod(query: String?): Period? {
    val raw = query?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val periods = grades.observePeriods().first().sortedBy { it.order }
    val normalized = Text.normalize(raw)
    if (normalized == "corrente" || normalized == "attuale" || normalized == "questo") return currentPeriod()
    periods.firstOrNull { Text.normalize(it.code) == normalized || Text.normalize(it.label) == normalized }?.let { return it }
    val ordinal = when {
      normalized.startsWith("prim") || normalized == "1" || normalized == "i" -> 1
      normalized.startsWith("second") || normalized == "2" || normalized == "ii" -> 2
      normalized.startsWith("terz") || normalized == "3" || normalized == "iii" -> 3
      normalized.startsWith("ultim") || normalized.startsWith("final") -> periods.size
      else -> null
    }
    if (ordinal != null) return periods.getOrNull(ordinal - 1)
    return periods.firstOrNull { Text.matches(normalized, it.label) || Text.matches(normalized, it.description) }
  }
}
