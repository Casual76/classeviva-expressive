package dev.antigravity.classevivaexpressive.feature.assistant.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.classevivaexpressive.core.assistant.actions.NavigationRequest
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantConversation
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantConversationsRepository
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantMessage
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantRun
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantActionExecutor
import dev.antigravity.classevivaexpressive.core.assistant.runtime.AssistantRuntime
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** La lista delle conversazioni dello studente corrente, dalla piu' recente. */
@HiltViewModel
class AssistantHistoryViewModel @Inject constructor(
  private val conversations: AssistantConversationsRepository,
  private val auth: AuthRepository,
  private val runtime: AssistantRuntime,
) : ViewModel() {

  @OptIn(ExperimentalCoroutinesApi::class)
  val items: StateFlow<List<AssistantConversation>> = auth.session.map { it?.studentId }.distinctUntilChanged()
    .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else conversations.observeConversations(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val activeConversationId: StateFlow<Long?> = runtime.activeConversationId

  fun delete(id: Long) = viewModelScope.launch {
    if (runtime.activeConversationId.value == id) {
      if (runtime.isBusy) runtime.cancel()
      runtime.selectConversation(null)
    }
    conversations.delete(id)
  }

  fun deleteAll() = viewModelScope.launch {
    val studentId = auth.session.value?.studentId ?: return@launch
    if (runtime.isBusy) runtime.cancel()
    runtime.selectConversation(null)
    conversations.deleteAll(studentId)
  }
}

data class ConversationUiState(
  val conversation: AssistantConversation? = null,
  val messages: List<AssistantMessage> = emptyList(),
  val runs: Map<Long, AssistantRun> = emptyMap(),
  /** Lo stato vivo del runtime, se sta lavorando proprio su questa conversazione. */
  val live: AssistantState? = null,
  val pending: PendingConfirmation? = null,
)

/**
 * Una conversazione aperta per intero: i messaggi salvati, la telemetria di ogni scambio, e lo
 * stato vivo quando la domanda in corso e' la sua. Con `conversationId` nullo e' una conversazione
 * nuova: nasce alla prima domanda, e da quel momento la schermata la segue.
 */
@HiltViewModel
class AssistantConversationViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val conversations: AssistantConversationsRepository,
  private val runtime: AssistantRuntime,
  private val executor: AssistantActionExecutor,
) : ViewModel() {

  private val id = MutableStateFlow(savedStateHandle.get<String>("conversationId")?.toLongOrNull())
  private var awaitingNew = false

  val conversationId: StateFlow<Long?> = id
  val navigation: StateFlow<NavigationRequest?> = executor.navigation

  init {
    // Una conversazione nuova prende l'id appena il runtime lo crea.
    viewModelScope.launch {
      runtime.activeConversationId.collect { active ->
        if (awaitingNew && active != null && id.value == null) {
          id.value = active
          awaitingNew = false
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<ConversationUiState> = id.flatMapLatest { current ->
    if (current == null) {
      combine(runtime.state, runtime.pendingConfirmation, runtime.activeConversationId) { live, pending, active ->
        ConversationUiState(live = if (awaitingNew || active == null) live.takeIf { it.isBusy } else null, pending = pending)
      }
    } else {
      combine(
        conversations.observeConversation(current),
        conversations.observeMessages(current),
        conversations.observeRuns(current),
        runtime.state,
        runtime.activeConversationId,
        runtime.pendingConfirmation,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        val conversation = values[0] as AssistantConversation?
        @Suppress("UNCHECKED_CAST")
        val messages = values[1] as List<AssistantMessage>
        @Suppress("UNCHECKED_CAST")
        val runs = values[2] as List<AssistantRun>
        val live = values[3] as AssistantState
        val active = values[4] as Long?
        val pending = values[5] as PendingConfirmation?
        ConversationUiState(
          conversation = conversation,
          messages = messages,
          runs = runs.associateBy { it.messageId },
          live = if (active == current) live else null,
          pending = if (active == current) pending else null,
        )
      }
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

  val isBusy: Boolean get() = runtime.isBusy

  fun send(text: String) {
    val current = id.value
    if (current == null) awaitingNew = true
    runtime.selectConversation(current)
    runtime.submit(current, text, AskMode.TEXT)
  }

  fun cancel() = runtime.cancel()

  fun resolve(confirmationId: Long, confirmed: Boolean) = runtime.resolveConfirmation(confirmationId, confirmed)

  fun consumeNavigation(): NavigationRequest? = executor.consumeNavigation()

  /** Aprendo la conversazione, le prossime domande dal tasto continuano qui. */
  fun makeActive() {
    id.value?.let { if (!runtime.isBusy) runtime.selectConversation(it) }
  }
}
