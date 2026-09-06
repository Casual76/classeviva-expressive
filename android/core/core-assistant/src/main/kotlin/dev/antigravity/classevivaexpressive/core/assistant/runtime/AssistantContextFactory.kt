package dev.antigravity.classevivaexpressive.core.assistant.runtime

import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantActionSink
import dev.antigravity.classevivaexpressive.core.assistant.attachments.AttachmentReader
import dev.antigravity.classevivaexpressive.core.assistant.tools.AssistantToolContext
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StatsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentScoreRepository
import dev.antigravity.classevivaexpressive.core.domain.usecase.PredictiveTimetableUseCase
import dev.antigravity.fluidengine.ai.provider.ModelCapabilities
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Costruisce il contesto con cui girano i tool. Esiste perche' i chiamanti sono due: l'assistente
 * dell'app (che passa dal cancello delle conferme) e i tool federati, che PampAI/Aria esegue nel
 * nostro processo dopo aver chiesto lei all'utente. Le fonti dei dati sono le stesse; a cambiare
 * e' solo chi esegue le azioni e chi ha gia' detto di si'.
 */
@Singleton
class AssistantContextFactory @Inject constructor(
  private val grades: GradesRepository,
  private val agenda: AgendaRepository,
  private val homework: HomeworkRepository,
  private val lessons: LessonsRepository,
  private val communications: CommunicationsRepository,
  private val absences: AbsencesRepository,
  private val stats: StatsRepository,
  private val studentScore: StudentScoreRepository,
  private val materials: MaterialsRepository,
  private val documents: DocumentsRepository,
  private val dashboard: DashboardRepository,
  private val appSettings: SettingsRepository,
  private val timetable: PredictiveTimetableUseCase,
  private val schoolYear: SchoolYearRepository,
  private val capabilities: CapabilityResolver,
  private val attachments: AttachmentReader,
) {

  fun create(
    actionsEnabled: Boolean,
    actions: AssistantActionSink,
    deepCapabilities: ModelCapabilities,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
  ): AssistantToolContext = AssistantToolContext(
    grades = grades,
    agenda = agenda,
    homework = homework,
    lessons = lessons,
    communications = communications,
    absences = absences,
    stats = stats,
    studentScore = studentScore,
    materials = materials,
    documents = documents,
    dashboard = dashboard,
    settings = appSettings,
    timetable = timetable,
    schoolYear = schoolYear,
    capabilities = capabilities,
    attachments = attachments,
    zone = zone,
    today = today,
    actionsEnabled = actionsEnabled,
    actions = if (actionsEnabled) actions else AssistantActionSink.Disabled,
    deepCapabilities = deepCapabilities,
  )
}
