package dev.antigravity.classevivaexpressive.core.assistant.runtime

import dev.antigravity.classevivaexpressive.core.assistant.actions.ActionOutcome
import dev.antigravity.classevivaexpressive.core.assistant.actions.ActionResult
import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantAction
import dev.antigravity.classevivaexpressive.core.assistant.actions.AssistantActionSink
import dev.antigravity.classevivaexpressive.core.assistant.actions.NavigationRequest
import dev.antigravity.classevivaexpressive.core.assistant.actions.RefreshSection
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.HomeworkRepository
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.fluidengine.ai.orchestrator.AiConfirmationGate
import dev.antigravity.fluidengine.ai.orchestrator.ConfirmationOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Chi fa davvero le cose che i tool chiedono. Le letture stanno nei tool; qui ci sono solo le
 * scritture e la navigazione, e le scritture che contano passano prima dal cancello delle
 * conferme: il tasto lo mostra la UI, l'esito torna al modello come testo.
 */
@Singleton
class AssistantActionExecutor @Inject constructor(
  private val gate: AiConfirmationGate,
  private val settings: SettingsRepository,
  private val communications: CommunicationsRepository,
  private val agenda: AgendaRepository,
  private val grades: GradesRepository,
  private val homework: HomeworkRepository,
  private val lessons: LessonsRepository,
  private val absences: AbsencesRepository,
  private val materials: MaterialsRepository,
  private val documents: DocumentsRepository,
  private val dashboard: DashboardRepository,
) : AssistantActionSink {

  private val navigationFlow = MutableStateFlow<NavigationRequest?>(null)

  /** L'ultima pagina chiesta e non ancora aperta: `MainApp` la consuma con [consumeNavigation]. */
  val navigation: StateFlow<NavigationRequest?> = navigationFlow

  fun consumeNavigation(): NavigationRequest? {
    val request = navigationFlow.value
    navigationFlow.value = null
    return request
  }

  override suspend fun perform(action: AssistantAction): ActionResult {
    if (action.needsConfirmation) {
      when (gate.ask(action.title, action.detail)) {
        ConfirmationOutcome.CONFIRMED -> Unit
        ConfirmationOutcome.REJECTED -> return ActionResult(ActionOutcome.REJECTED)
        ConfirmationOutcome.TIMEOUT -> return ActionResult(ActionOutcome.TIMEOUT)
      }
    }
    return try {
      execute(action)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      ActionResult(ActionOutcome.FAILED, e.message)
    }
  }

  private suspend fun execute(action: AssistantAction): ActionResult = when (action) {
    is AssistantAction.Open -> {
      navigationFlow.value = NavigationRequest(action.page, action.itemId)
      ActionResult(ActionOutcome.DONE, "aperta la pagina ${action.page.label}${action.itemId?.let { " sul dettaglio $it" } ?: ""}")
    }
    is AssistantAction.SetTheme -> {
      settings.updateThemeMode(action.mode)
      done(action)
    }
    is AssistantAction.SetAccent -> {
      settings.updateAccentMode(action.mode)
      if (action.mode == AccentMode.CUSTOM_PRESET && action.presetName != null) settings.updateCustomAccent(action.presetName)
      done(action)
    }
    is AssistantAction.SetDynamicColor -> {
      settings.setDynamicColorEnabled(action.enabled)
      done(action)
    }
    is AssistantAction.SetAmoled -> {
      settings.setAmoledEnabled(action.enabled)
      done(action)
    }
    is AssistantAction.SetNotifications -> {
      settings.setNotificationsEnabled(action.enabled)
      done(action)
    }
    is AssistantAction.SetNotificationCategory -> {
      settings.setNotificationCategoryEnabled(action.channelId, action.enabled)
      done(action)
    }
    is AssistantAction.SetPeriodicSync -> {
      settings.setPeriodicSyncEnabled(action.enabled)
      done(action)
    }
    AssistantAction.MarkAllRead -> communications.markAllAsRead().fold({ ActionResult(ActionOutcome.DONE) }, { ActionResult(ActionOutcome.FAILED, it.message) })
    is AssistantAction.MarkRead -> communications.markCommunicationRead(action.communicationId).fold({ ActionResult(ActionOutcome.DONE) }, { ActionResult(ActionOutcome.FAILED, it.message) })
    is AssistantAction.Acknowledge -> {
      val detail = communications.getCommunicationDetail(action.pubId, action.evtCode).getOrElse { return ActionResult(ActionOutcome.FAILED, it.message) }
      communications.acknowledgeCommunication(detail).fold({ ActionResult(ActionOutcome.DONE) }, { ActionResult(ActionOutcome.FAILED, it.message) })
    }
    is AssistantAction.AddCustomEvent -> {
      agenda.addCustomEvent(action.event)
      ActionResult(ActionOutcome.DONE)
    }
    is AssistantAction.SaveGoal -> {
      grades.saveSubjectGoal(action.subject, action.periodCode, action.target)
      ActionResult(ActionOutcome.DONE)
    }
    is AssistantAction.Refresh -> refresh(action.section)
  }

  private fun done(action: AssistantAction) = ActionResult(ActionOutcome.DONE, "fatto: ${action.title}")

  private suspend fun refresh(section: RefreshSection): ActionResult {
    val failures = mutableListOf<String>()
    suspend fun run(name: String, block: suspend () -> Result<*>) {
      block().onFailure { failures += "$name (${it.message ?: "errore"})" }
    }
    when (section) {
      RefreshSection.TUTTO -> {
        run("dashboard") { dashboard.refreshDashboard(force = true) }
        run("voti") { grades.refreshGrades(force = true) }
        run("agenda") { agenda.refreshAgenda(force = true) }
        run("compiti") { homework.refreshHomeworks(force = true) }
        run("orario") { lessons.refreshLessons(force = true) }
        run("bacheca") { communications.refreshCommunications(force = true) }
        run("assenze") { absences.refreshAbsences(force = true) }
      }
      RefreshSection.VOTI -> run("voti") { grades.refreshGrades(force = true) }
      RefreshSection.AGENDA -> run("agenda") { agenda.refreshAgenda(force = true) }
      RefreshSection.COMPITI -> run("compiti") { homework.refreshHomeworks(force = true) }
      RefreshSection.ORARIO -> run("orario") { lessons.refreshLessons(force = true) }
      RefreshSection.BACHECA -> run("bacheca") { communications.refreshCommunications(force = true) }
      RefreshSection.ASSENZE -> run("assenze") { absences.refreshAbsences(force = true) }
      RefreshSection.DIDATTICA -> run("didattica") { materials.refreshMaterials(force = true) }
      RefreshSection.DOCUMENTI -> run("documenti") { documents.refreshDocuments(force = true) }
    }
    return if (failures.isEmpty()) ActionResult(ActionOutcome.DONE, "dati aggiornati (${section.id})")
    else ActionResult(ActionOutcome.FAILED, "non aggiornati: ${failures.joinToString(", ")}")
  }
}
