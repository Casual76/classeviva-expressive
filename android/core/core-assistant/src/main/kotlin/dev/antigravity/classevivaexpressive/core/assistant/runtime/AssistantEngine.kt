package dev.antigravity.classevivaexpressive.core.assistant.runtime

import dev.antigravity.classevivaexpressive.core.assistant.attachments.AttachmentReader
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantConversationsRepository
import dev.antigravity.classevivaexpressive.core.assistant.prompt.AssistantChips
import dev.antigravity.classevivaexpressive.core.assistant.prompt.PreRouter
import dev.antigravity.classevivaexpressive.core.assistant.prompt.PromptBuilder
import dev.antigravity.classevivaexpressive.core.assistant.prompt.PromptContext
import dev.antigravity.classevivaexpressive.core.assistant.tools.AssistantToolContext
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
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
import dev.antigravity.fluidengine.ai.keys.AiSettingsStore
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestrator
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AskInput
import dev.antigravity.fluidengine.ai.orchestrator.AssistantFailure
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.Conversation
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Com'e' finita una domanda, per la notifica e per chi ha chiamato. */
data class ExecutionResult(val conversationId: Long, val question: String, val answer: String?, val failure: FailureKind?, val cancelled: Boolean)

/**
 * Una domanda dall'inizio alla fine: la conversazione su disco, il contesto dei tool, il prompt,
 * il pre-router, l'orchestratore dell'engine, e ogni passo scritto in Room man mano — cosi' un
 * processo che muore lascia una domanda "fallita" e non un buco, e chi riapre l'app trova la
 * risposta o il lavoro in corso.
 */
@Singleton
class AssistantEngine @Inject constructor(
  private val runtime: AssistantRuntime,
  private val orchestrator: AiOrchestrator<AssistantToolContext>,
  private val providers: ProviderFactory,
  private val settingsStore: AiSettingsStore,
  private val conversations: AssistantConversationsRepository,
  private val executor: AssistantActionExecutor,
  private val attachments: AttachmentReader,
  private val timetable: PredictiveTimetableUseCase,
  private val auth: AuthRepository,
  private val schoolYear: SchoolYearRepository,
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
) {

  /** Le conversazioni in memoria per processo: il traffico tool dell'ultima domanda vive qui, non su disco. */
  private val memory = ConcurrentHashMap<Long, Conversation>()

  /**
   * Il lavoro gira sotto un [Job] figlio: e' quello che [AssistantRuntime.cancel] ferma. Fermarlo non
   * cancella chi ha chiamato — il service deve ancora chiudere la notifica e fermarsi — e un
   * `coroutineScope` cancellato rilancerebbe comunque la sua `CancellationException` dopo il `catch`:
   * qui la si assorbe e si risponde "fermata", salvo che sia il chiamante stesso a essere finito.
   */
  suspend fun execute(request: AssistantRequest): ExecutionResult {
    val job = Job(currentCoroutineContext()[Job])
    runtime.currentJob = job
    return try {
      withContext(job) { run(request) }
    } catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      ExecutionResult(request.conversationId ?: -1L, request.question, null, null, cancelled = true)
    } finally {
      job.complete()
      if (runtime.currentJob === job) runtime.currentJob = null
    }
  }

  @OptIn(FlowPreview::class)
  private suspend fun run(request: AssistantRequest): ExecutionResult = coroutineScope {
    val now = System.currentTimeMillis()
    val question = request.question
    val studentId = auth.session.value?.studentId
    if (studentId == null) {
      runtime.setState(AssistantState.Failed(question, FailureKind.UNKNOWN, null, null, null))
      return@coroutineScope ExecutionResult(request.conversationId ?: -1L, question, null, FailureKind.UNKNOWN, cancelled = false)
    }
    val conversationId = request.conversationId?.takeIf { conversations.conversation(it) != null }
      ?: conversations.createConversation(studentId, question, now)
    runtime.setActiveConversation(conversationId)
    conversations.addUserMessage(conversationId, question, now)
    val messageId = conversations.addPendingAssistantMessage(conversationId, now + 1)

    // Il testo parziale finisce su disco ogni 300 ms: chi riapre l'app a meta' lo trova.
    val persister = launch(Dispatchers.IO) {
      runtime.state.filterIsInstance<AssistantState.Answering>().sample(300).collect { conversations.updatePartial(messageId, it.partial) }
    }
    try {
      val conversation = memory.getOrPut(conversationId) { rebuild(conversationId, now) }
      conversation.lastActivityMillis = now
      val settings = settingsStore.current()
      val ordered = providers.ordered(ProviderFactory.Kind.CHAT)
      if (ordered.isEmpty()) throw AssistantFailure(FailureKind.NO_KEYS, null)
      val first = ordered.first()
      runtime.setState(AssistantState.Classifying(question, first.provider.id))
      val zone = ZoneId.systemDefault()
      val today = LocalDate.now(zone)
      val toolContext = AssistantToolContext(
        grades = grades, agenda = agenda, homework = homework, lessons = lessons, communications = communications,
        absences = absences, stats = stats, studentScore = studentScore, materials = materials, documents = documents,
        dashboard = dashboard, settings = appSettings, timetable = timetable, attachments = attachments,
        zone = zone, today = today, actionsEnabled = settings.actionsEnabled,
        actions = if (settings.actionsEnabled) executor else dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantActionSink.Disabled,
        deepCapabilities = first.capabilities(first.model(ModelTier.DEEP)),
      )
      val prompt = PromptBuilder.build(promptContext(today, settings.actionsEnabled, request))
      val pre = PreRouter.decide(question, settings.actionsEnabled)
      val input = AskInput(
        question = question,
        mode = request.mode,
        language = "it",
        settings = settings,
        providers = ordered,
        toolContext = toolContext,
        systemPrompt = prompt,
        conversation = conversation,
        actionsEnabled = settings.actionsEnabled,
        preselectedGroups = if (pre.confident) pre.groups else null,
        routerHint = if (pre.confident) emptySet() else pre.groups,
        deepRequested = pre.deep,
        chipFilter = { chip -> AssistantChips.accepts(chip.id, chip.value) },
        attachmentFallback = { part -> fallbackText(part) },
      )
      val result = orchestrator.ask(input, runtime.mutableState())
      persister.cancel()
      val finished = System.currentTimeMillis()
      conversations.complete(messageId, result.answer, result.chips)
      conversations.addRun(conversationId, messageId, result.log, finished, "ok", null)
      conversations.touch(conversationId, finished, result.provider)
      runtime.setState(
        AssistantState.Done(
          question = question, answer = result.answer, chips = result.chips, provider = result.provider, mode = request.mode,
          usage = result.usage, toolsUsed = result.toolsUsed, durationMillis = result.log.durationMillis, tierReached = result.tierReached,
        ),
      )
      ExecutionResult(conversationId, question, result.answer, null, cancelled = false)
    } catch (e: CancellationException) {
      persister.cancel()
      val partial = (runtime.state.value as? AssistantState.Answering)?.partial
      withContext(kotlinx.coroutines.NonCancellable) {
        conversations.cancel(messageId, partial)
        conversations.addFailedRun(conversationId, messageId, now, System.currentTimeMillis(), "cancelled", null)
        conversations.touch(conversationId, System.currentTimeMillis(), null)
      }
      runtime.setState(AssistantState.Cancelled(question, partial))
      ExecutionResult(conversationId, question, null, null, cancelled = true)
    } catch (e: AssistantFailure) {
      persister.cancel()
      val partial = (runtime.state.value as? AssistantState.Answering)?.partial
      conversations.fail(messageId, e.kind, partial)
      conversations.addFailedRun(conversationId, messageId, now, System.currentTimeMillis(), "failed", e.error?.message ?: e.kind.name)
      conversations.touch(conversationId, System.currentTimeMillis(), null)
      runtime.setState(AssistantState.Failed(question, e.kind, e.error, e.retryAfterSec, partial))
      ExecutionResult(conversationId, question, null, e.kind, cancelled = false)
    } catch (e: Throwable) {
      persister.cancel()
      conversations.fail(messageId, FailureKind.UNKNOWN, null)
      conversations.addFailedRun(conversationId, messageId, now, System.currentTimeMillis(), "failed", e.message ?: e::class.simpleName)
      runtime.setState(AssistantState.Failed(question, FailureKind.UNKNOWN, e as? AiError, null, null))
      ExecutionResult(conversationId, question, null, FailureKind.UNKNOWN, cancelled = false)
    }
  }

  private suspend fun rebuild(conversationId: Long, now: Long): Conversation {
    val conversation = Conversation(conversationId, now)
    conversation.exchanges += conversations.exchanges(conversationId, limit = 8)
    return conversation
  }

  private suspend fun promptContext(today: LocalDate, actionsEnabled: Boolean, request: AssistantRequest): PromptContext {
    val snapshot = runCatching { dashboard.observeDashboard().first() }.getOrNull()
    val periods = runCatching { grades.observePeriods().first() }.getOrDefault(emptyList())
    val year = runCatching { schoolYear.observeSelectedSchoolYear().first() }.getOrNull()
      ?: dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef.current(today.year, today.monthValue)
    return PromptContext(
      profile = auth.session.value?.profile ?: snapshot?.profile ?: dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile(),
      schoolYear = year,
      today = today,
      periods = periods,
      unseenGrades = snapshot?.unseenGrades?.size ?: 0,
      unreadCommunications = snapshot?.unreadCommunications?.size ?: 0,
      todayLessons = snapshot?.todayLessons?.size ?: 0,
      upcomingItems = snapshot?.upcomingItems?.size ?: 0,
      actionsEnabled = actionsEnabled,
      mode = request.mode,
      syncStatus = snapshot?.syncStatus ?: dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus(),
    )
  }

  /** Un allegato che il modello non regge: il PDF diventa testo (pdfbox); un'immagine non ha traduzione. */
  private suspend fun fallbackText(part: ContentPart): String? = when (part) {
    is ContentPart.Document -> withContext(Dispatchers.IO) {
      val file = java.io.File.createTempFile("allegato", ".pdf")
      try {
        file.writeBytes(part.bytes)
        runCatching { attachments.extractText(file, null) }.getOrNull()?.takeIf { it.isNotBlank() }
      } finally {
        file.delete()
      }
    }
    is ContentPart.Text -> part.text
    is ContentPart.Image -> null
  }
}

/** L'orchestratore scrive lo stato direttamente: il runtime espone il flusso mutabile solo a chi esegue. */
internal fun AssistantRuntime.mutableState(): kotlinx.coroutines.flow.MutableStateFlow<AssistantState> = state as kotlinx.coroutines.flow.MutableStateFlow<AssistantState>
