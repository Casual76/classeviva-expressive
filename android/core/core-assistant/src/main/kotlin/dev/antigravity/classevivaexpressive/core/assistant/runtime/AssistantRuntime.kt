package dev.antigravity.classevivaexpressive.core.assistant.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantConversationsRepository
import dev.antigravity.classevivaexpressive.core.assistant.db.AssistantTotals
import dev.antigravity.classevivaexpressive.core.assistant.service.AssistantForegroundService
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.fluidengine.ai.keys.AiKeyStore
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.AiSettingsStore
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.orchestrator.AiConfirmationGate
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantFailure
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.ai.provider.ProviderFactory
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.speech.AndroidPcmSource
import dev.antigravity.fluidengine.ai.speech.SpeechCapture
import dev.antigravity.fluidengine.ai.speech.Transcriber
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Una domanda da far partire: in quale conversazione (null = nuova), cosa, come e' arrivata. */
data class AssistantRequest(val conversationId: Long?, val question: String, val mode: AskMode)

/**
 * Lo stato dell'assistente per tutto il processo: la UI lo osserva, il service lo alimenta. Vive
 * quanto l'app; una domanda parte da qui ([submit]) e viene eseguita dal service in primo piano,
 * cosi' sopravvive alla chiusura dell'app. La voce invece si ascolta qui, perche' il microfono ha
 * senso solo con l'app davanti.
 */
@Singleton
class AssistantRuntime @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settingsStore: AiSettingsStore,
  private val keyStore: AiKeyStore,
  private val providers: ProviderFactory,
  private val gate: AiConfirmationGate,
  private val conversations: AssistantConversationsRepository,
  private val auth: AuthRepository,
  private val grades: GradesRepository,
) {

  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val stateFlow = MutableStateFlow<AssistantState>(AssistantState.Idle)
  val state: StateFlow<AssistantState> = stateFlow

  /** Il livello del microfono viaggia per conto suo: cinquanta volte al secondo, e lo legge solo l'aureola. */
  private val micLevelFlow = MutableStateFlow(MicLevel())
  val micLevel: StateFlow<MicLevel> = micLevelFlow

  private val activeConversation = MutableStateFlow<Long?>(null)
  val activeConversationId: StateFlow<Long?> = activeConversation

  val pendingConfirmation: StateFlow<PendingConfirmation?> = gate.current

  /** Acceso = interruttore attivo e almeno una chiave verificata: la condizione per mostrare qualsiasi cosa. */
  val enabled: Flow<Boolean> = combine(settingsStore.settings, keyStore.anyVerified) { s, anyKey -> s.enabled && anyKey }

  val settings: Flow<AiSettings> = settingsStore.settings

  /** Vero mentre un'Activity dell'app e' davanti: decide se la risposta va anche in notifica. */
  @Volatile var appInForeground: Boolean = false

  /** Il gettone della voce: cresce a ogni tocco, chi lo consuma fa partire l'ascolto una volta sola. */
  private val voiceTokens = AtomicLong(0)

  @Volatile private var pending: AssistantRequest? = null
  @Volatile internal var currentJob: Job? = null
  @Volatile private var listening: SpeechCapture? = null
  @Volatile private var voiceJob: Job? = null

  init {
    // Al riavvio una risposta rimasta a meta' non deve sembrare ancora in corso; al logout le
    // conversazioni di quello studente se ne vanno con il resto dei suoi dati.
    scope.launch { runCatching { conversations.failStale() } }
    scope.launch {
      var previous: String? = auth.session.value?.studentId
      auth.session.collect { session ->
        val current = session?.studentId
        if (current == null && previous != null) {
          cancel()
          runCatching { conversations.deleteAll(previous!!) }
          activeConversation.value = null
          stateFlow.value = AssistantState.Idle
        }
        previous = current
      }
    }
  }

  val isBusy: Boolean get() = stateFlow.value.isBusy

  /** Fa partire una domanda scritta: il service la esegue e la porta a termine anche ad app chiusa. */
  fun submit(conversationId: Long?, question: String, mode: AskMode = AskMode.TEXT) {
    if (isBusy) cancel()
    enqueue(conversationId, question, mode)
  }

  /**
   * Mette in coda e fa partire il service, senza fermare niente: e' la strada della voce, che
   * arriva qui dal proprio job mentre lo stato e' ancora "trascrivo" — un `cancel()` a quel punto
   * fermerebbe se stesso.
   */
  private fun enqueue(conversationId: Long?, question: String, mode: AskMode) {
    val text = question.trim()
    if (text.isEmpty()) return
    pending = AssistantRequest(conversationId, text, mode)
    activeConversation.value = conversationId
    stateFlow.value = AssistantState.Working(text, 0, 1, "thinking", 0, providersFirstId())
    ContextCompat.startForegroundService(context, Intent(context, AssistantForegroundService::class.java))
  }

  internal fun takePendingRequest(): AssistantRequest? {
    val request = pending
    pending = null
    return request
  }

  internal fun setState(state: AssistantState) {
    stateFlow.value = state
  }

  /** Quale conversazione continua la prossima domanda; null = se ne apre una nuova. */
  fun selectConversation(id: Long?) {
    activeConversation.value = id
  }

  internal fun setActiveConversation(id: Long?) = selectConversation(id)

  fun resolveConfirmation(id: Long, confirmed: Boolean) = gate.resolve(id, confirmed)

  /** Ferma tutto: ascolto, domanda in corso, conferma in attesa. Lo stato lo scrive chi viene fermato. */
  fun cancel() {
    listening?.stopNow()
    voiceJob?.cancel()
    gate.cancel()
    currentJob?.cancel(CancellationException("fermato dall'utente"))
    if (stateFlow.value.isBusy && currentJob == null) stateFlow.value = AssistantState.Cancelled(null, null)
  }

  /** Torna al silenzio: dopo una risposta letta, un errore visto, una card chiusa. */
  fun reset() {
    if (!isBusy) stateFlow.value = AssistantState.Idle
  }

  /** Il tocco sul tasto: ascolta, trascrive, e fa partire la domanda come se fosse stata scritta. */
  fun startListening(conversationId: Long?) {
    if (isBusy) cancel()
    val token = voiceTokens.incrementAndGet()
    activeConversation.value = conversationId
    voiceJob = scope.launch {
      try {
        val question = listen(token) ?: return@launch
        enqueue(conversationId, question, AskMode.VOICE)
      } catch (e: CancellationException) {
        stateFlow.value = AssistantState.Cancelled(null, null)
      } catch (e: AssistantFailure) {
        stateFlow.value = AssistantState.Failed(null, e.kind, e.error, e.retryAfterSec, null)
      } catch (e: Throwable) {
        stateFlow.value = AssistantState.Failed(null, FailureKind.UNKNOWN, e as? AiError, null, null)
      }
    }
  }

  /** Il secondo tocco mentre ascolta: si chiude la cattura e si trascrive quello che c'e'. */
  fun stopListening() {
    listening?.stopNow()
  }

  private suspend fun listen(token: Long): String? {
    val capture = SpeechCapture(AndroidPcmSource(context))
    listening = capture
    val dir = File(context.cacheDir, "ai").apply { mkdirs() }
    val file = File(dir, "ask-$token-${System.currentTimeMillis()}.wav")
    var result: String? = null
    try {
      stateFlow.value = AssistantState.Listening(0L)
      micLevelFlow.value = MicLevel()
      capture.record(file).collect { event ->
        when (event) {
          is SpeechCapture.Event.Level -> {
            micLevelFlow.value = MicLevel(event.level, event.speaking)
            val elapsed = event.elapsedMillis / 1000 * 1000
            val shown = stateFlow.value
            if (shown !is AssistantState.Listening || shown.elapsedMillis != elapsed) stateFlow.value = AssistantState.Listening(elapsed)
          }
          SpeechCapture.Event.SpeechStarted -> Unit
          is SpeechCapture.Event.Empty -> stateFlow.value = AssistantState.HeardNothing
          is SpeechCapture.Event.Failed -> throw AssistantFailure(FailureKind.MICROPHONE, null)
          is SpeechCapture.Event.Finished -> {
            stateFlow.value = AssistantState.Transcribing
            val transcriber = Transcriber { providers.ordered(ProviderFactory.Kind.STT) }
            val vocabulary = runCatching { grades.observeSubjects().first() }.getOrDefault(emptyList())
              .flatMap { listOf(it.description) + it.teachers }
            val hint = Transcriber.hint(
              "Domande sul registro elettronico di uno studente: voti, media, compiti, verifiche, interrogazioni, circolari, assenze, orario, professori.",
              vocabulary,
            )
            val transcription = try {
              transcriber.transcribe(event.file, "it", hint)
            } catch (e: CancellationException) {
              throw e
            } catch (e: AiError.Unauthorized) {
              throw AssistantFailure(FailureKind.UNAUTHORIZED, e)
            } catch (e: Throwable) {
              throw AssistantFailure(FailureKind.TRANSCRIPTION, e as? AiError)
            }
            result = transcription.text.takeIf { it.isNotBlank() }
            if (result == null) stateFlow.value = AssistantState.HeardNothing
          }
        }
      }
    } finally {
      listening = null
      micLevelFlow.value = MicLevel()
      runCatching { file.delete() }
    }
    return result
  }

  private fun providersFirstId(): ProviderId = ProviderId.defaultOrder.first()

  /** I totali per la diagnostica delle impostazioni: conversazioni, scambi, token, spesa. */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun totals(): Flow<AssistantTotals?> =
    auth.session.map { it?.studentId }.distinctUntilChanged().flatMapLatest { id ->
      if (id == null) flowOf(null) else conversations.observeTotals(id)
    }
}
