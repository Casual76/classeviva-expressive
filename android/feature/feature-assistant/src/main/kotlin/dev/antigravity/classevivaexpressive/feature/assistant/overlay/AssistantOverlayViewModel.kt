package dev.antigravity.classevivaexpressive.feature.assistant.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.assistant.actions.NavigationRequest
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantActionExecutor
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantRuntime
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Il ponte fra l'overlay e il runtime: lo stato da osservare e i pochi verbi (chiedi, ascolta,
 * ferma, conferma). Vive quanto l'Activity, ma tutto cio' che conta sta nel runtime, che vive
 * quanto il processo: chiudere e riaprire l'overlay non perde niente.
 */
@HiltViewModel
class AssistantOverlayViewModel @Inject constructor(
  private val runtime: AssistantRuntime,
  private val executor: AssistantActionExecutor,
) : ViewModel() {

  val state: StateFlow<AssistantState> = runtime.state
  val micLevel: StateFlow<MicLevel> = runtime.micLevel
  val pending: StateFlow<PendingConfirmation?> = runtime.pendingConfirmation
  val activeConversationId: StateFlow<Long?> = runtime.activeConversationId
  val navigation: StateFlow<NavigationRequest?> = executor.navigation

  val enabled: StateFlow<Boolean> = runtime.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
  val settings: StateFlow<AiSettings> = runtime.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettings())

  /** Come e' arrivata l'ultima domanda: decide se la risposta va letta ad alta voce. */
  @Volatile var lastMode: AskMode = AskMode.TEXT
    private set

  val isBusy: Boolean get() = runtime.isBusy

  fun askText(question: String) {
    lastMode = AskMode.TEXT
    runtime.submit(runtime.activeConversationId.value, question, AskMode.TEXT)
  }

  fun askVoice() {
    lastMode = AskMode.VOICE
    runtime.startListening(runtime.activeConversationId.value)
  }

  fun stopListening() = runtime.stopListening()

  fun cancel() = runtime.cancel()

  fun resolve(id: Long, confirmed: Boolean) = runtime.resolveConfirmation(id, confirmed)

  /** La card si chiude: lo stato torna a tacere, la conversazione resta quella. */
  fun dismiss() = runtime.reset()

  /** La freccia circolare: la prossima domanda apre una conversazione nuova. */
  fun newConversation() {
    runtime.selectConversation(null)
    runtime.reset()
  }

  fun consumeNavigation(): NavigationRequest? = executor.consumeNavigation()

  fun setForeground(foreground: Boolean) {
    runtime.appInForeground = foreground
  }
}
