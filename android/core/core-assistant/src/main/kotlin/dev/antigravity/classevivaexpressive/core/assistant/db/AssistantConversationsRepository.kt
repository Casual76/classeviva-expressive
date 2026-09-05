package dev.antigravity.classevivaexpressive.core.assistant.db

import dev.antigravity.classevivaexpressive.core.database.database.AssistantConversationDao
import dev.antigravity.classevivaexpressive.core.database.database.AssistantConversationEntity
import dev.antigravity.classevivaexpressive.core.database.database.AssistantMessageDao
import dev.antigravity.classevivaexpressive.core.database.database.AssistantMessageEntity
import dev.antigravity.classevivaexpressive.core.database.database.AssistantRunDao
import dev.antigravity.classevivaexpressive.core.database.database.AssistantRunEntity
import dev.antigravity.fluidengine.ai.orchestrator.AiRequestLog
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.Exchange
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.classevivaexpressive.core.assistant.tools.AssistantToolTrace
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class MessageRole { USER, ASSISTANT }

enum class MessageStatus { PENDING, STREAMING, DONE, FAILED, CANCELLED }

data class AssistantConversation(
  val id: Long,
  val title: String,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val lastProvider: ProviderId?,
)

data class AssistantMessage(
  val id: Long,
  val conversationId: Long,
  val role: MessageRole,
  val text: String,
  val chips: List<AnswerChip>,
  val status: MessageStatus,
  val failureKind: FailureKind?,
  val createdAtMillis: Long,
)

/** La traccia di uno scambio: quanto e' costato, con cosa, in quanti passi. */
data class AssistantRun(
  val id: Long,
  val conversationId: Long,
  val messageId: Long,
  val startedAtMillis: Long,
  val finishedAtMillis: Long?,
  val steps: Int,
  val provider: ProviderId?,
  val routerModel: String?,
  val chatModel: String?,
  val deepModel: String?,
  val tierReached: ModelTier?,
  val groups: List<String>,
  val promptTokens: Int?,
  val completionTokens: Int?,
  val costUsd: Double?,
  val waitedSeconds: Int,
  val tools: List<AssistantToolTrace>,
  val outcome: String,
  val error: String?,
) {
  val totalTokens: Int? get() = if (promptTokens == null && completionTokens == null) null else (promptTokens ?: 0) + (completionTokens ?: 0)
  val durationMillis: Long? get() = finishedAtMillis?.let { it - startedAtMillis }
}

data class AssistantTotals(val conversations: Int, val runs: Int, val tokens: Long, val costUsd: Double)

@Serializable
private data class ChipJson(val id: String, val value: String? = null)

@Serializable
private data class TraceJson(val name: String, val millis: Long, val ok: Boolean, val chars: Int, val args: String = "", val preview: String = "")

/**
 * Le conversazioni su disco: una riga per conversazione, una per messaggio, una per scambio con la
 * sua telemetria. Il traffico dei tool non si salva (si ricostruisce solo la coppia domanda/risposta):
 * basta per continuare una conversazione il giorno dopo, e non pesa.
 */
@Singleton
class AssistantConversationsRepository @Inject constructor(
  private val conversationDao: AssistantConversationDao,
  private val messageDao: AssistantMessageDao,
  private val runDao: AssistantRunDao,
  private val json: Json,
) {

  fun observeConversations(studentId: String): Flow<List<AssistantConversation>> =
    conversationDao.observeByStudent(studentId).map { list -> list.map { it.toModel() } }

  fun observeConversation(id: Long): Flow<AssistantConversation?> = conversationDao.observe(id).map { it?.toModel() }

  fun observeMessages(conversationId: Long): Flow<List<AssistantMessage>> =
    messageDao.observeByConversation(conversationId).map { list -> list.map { it.toModel() } }

  fun observeRuns(conversationId: Long): Flow<List<AssistantRun>> =
    runDao.observeByConversation(conversationId).map { list -> list.map { it.toModel() } }

  fun observeRecentRuns(limit: Int = 10): Flow<List<AssistantRun>> = runDao.observeRecent(limit).map { list -> list.map { it.toModel() } }

  fun observeTotals(studentId: String): Flow<AssistantTotals> = combine(
    conversationDao.observeByStudent(studentId),
    runDao.observeCount(),
    runDao.observeTotalTokens(),
    runDao.observeTotalCost(),
  ) { conversations, runs, tokens, cost -> AssistantTotals(conversations.size, runs, tokens, cost) }

  suspend fun conversation(id: Long): AssistantConversation? = conversationDao.get(id)?.toModel()

  suspend fun createConversation(studentId: String, title: String, nowMillis: Long): Long =
    conversationDao.insert(AssistantConversationEntity(studentId = studentId, title = title.take(80), createdAtEpochMillis = nowMillis, updatedAtEpochMillis = nowMillis))

  suspend fun touch(conversationId: Long, nowMillis: Long, provider: ProviderId?) {
    val current = conversationDao.get(conversationId) ?: return
    conversationDao.update(current.copy(updatedAtEpochMillis = nowMillis, lastProvider = provider?.id ?: current.lastProvider))
  }

  suspend fun addUserMessage(conversationId: Long, text: String, nowMillis: Long): Long =
    messageDao.insert(AssistantMessageEntity(conversationId = conversationId, role = MessageRole.USER.name, text = text, status = MessageStatus.DONE.name, createdAtEpochMillis = nowMillis))

  suspend fun addPendingAssistantMessage(conversationId: Long, nowMillis: Long): Long =
    messageDao.insert(AssistantMessageEntity(conversationId = conversationId, role = MessageRole.ASSISTANT.name, text = "", status = MessageStatus.PENDING.name, createdAtEpochMillis = nowMillis))

  suspend fun updatePartial(messageId: Long, text: String) {
    val current = messageDao.get(messageId) ?: return
    if (current.status != MessageStatus.PENDING.name && current.status != MessageStatus.STREAMING.name) return
    messageDao.update(current.copy(text = text, status = MessageStatus.STREAMING.name))
  }

  suspend fun complete(messageId: Long, text: String, chips: List<AnswerChip>) {
    val current = messageDao.get(messageId) ?: return
    messageDao.update(current.copy(text = text, chipsJson = json.encodeToString(chips.map { ChipJson(it.id, it.value) }), status = MessageStatus.DONE.name, failureKind = null))
  }

  suspend fun fail(messageId: Long, kind: FailureKind, partial: String?) {
    val current = messageDao.get(messageId) ?: return
    messageDao.update(current.copy(text = partial ?: current.text, status = MessageStatus.FAILED.name, failureKind = kind.name))
  }

  suspend fun cancel(messageId: Long, partial: String?) {
    val current = messageDao.get(messageId) ?: return
    messageDao.update(current.copy(text = partial ?: current.text, status = MessageStatus.CANCELLED.name))
  }

  /** All'avvio: una risposta rimasta a meta' da un processo morto non resta "in corso" per sempre. */
  suspend fun failStale() {
    messageDao.failStale(MessageStatus.FAILED.name, FailureKind.UNKNOWN.name, listOf(MessageStatus.PENDING.name, MessageStatus.STREAMING.name))
  }

  suspend fun addRun(
    conversationId: Long,
    messageId: Long,
    log: AiRequestLog,
    finishedAtMillis: Long,
    outcome: String,
    error: String?,
    traces: List<AssistantToolTrace> = emptyList(),
  ): Long =
    runDao.insert(
      AssistantRunEntity(
        conversationId = conversationId,
        messageId = messageId,
        startedAtEpochMillis = log.startedAtMillis,
        finishedAtEpochMillis = finishedAtMillis,
        steps = log.steps,
        provider = log.provider.id,
        routerModel = log.models[ModelTier.ROUTER],
        chatModel = log.models[ModelTier.CHAT] ?: log.model,
        deepModel = log.models[ModelTier.DEEP],
        tierReached = log.tierReached.name,
        groupsJson = json.encodeToString(log.groups),
        promptTokens = log.usage?.promptTokens,
        completionTokens = log.usage?.completionTokens,
        costUsd = log.usage?.costUsd,
        waitedSeconds = log.waitedSeconds,
        toolTracesJson = json.encodeToString(
          if (traces.isNotEmpty()) traces.map { TraceJson(it.name, it.millis, it.ok, it.chars, it.args, it.preview) }
          else log.tools.map { TraceJson(it.name, it.millis, it.ok, it.chars) },
        ),
        outcome = outcome,
        error = error,
      ),
    )

  /** Un tentativo fallito o fermato, senza log dell'orchestratore: resta traccia di quando e come. */
  suspend fun addFailedRun(
    conversationId: Long,
    messageId: Long,
    startedAtMillis: Long,
    finishedAtMillis: Long,
    outcome: String,
    error: String?,
    traces: List<AssistantToolTrace> = emptyList(),
  ): Long =
    runDao.insert(
      AssistantRunEntity(
        conversationId = conversationId, messageId = messageId, startedAtEpochMillis = startedAtMillis, finishedAtEpochMillis = finishedAtMillis,
        steps = 0, provider = null, routerModel = null, chatModel = null, deepModel = null, tierReached = null, groupsJson = null,
        promptTokens = null, completionTokens = null, costUsd = null, waitedSeconds = 0,
        toolTracesJson = traces.takeIf { it.isNotEmpty() }?.let { list -> json.encodeToString(list.map { TraceJson(it.name, it.millis, it.ok, it.chars, it.args, it.preview) }) },
        outcome = outcome, error = error,
      ),
    )

  /** Le coppie domanda/risposta concluse, per ricostruire la conversazione in memoria del modello. */
  suspend fun exchanges(conversationId: Long, limit: Int): List<Exchange> {
    val messages = messageDao.listByConversation(conversationId)
    val exchanges = mutableListOf<Exchange>()
    var pendingQuestion: AssistantMessageEntity? = null
    messages.forEach { message ->
      when (message.role) {
        MessageRole.USER.name -> pendingQuestion = message
        MessageRole.ASSISTANT.name -> {
          val question = pendingQuestion
          if (question != null && message.status == MessageStatus.DONE.name && message.text.isNotBlank()) {
            exchanges += Exchange(question.text, message.text, decodeChips(message.chipsJson), ProviderId.GROQ, message.createdAtEpochMillis)
          }
          pendingQuestion = null
        }
      }
    }
    return exchanges.takeLast(limit)
  }

  suspend fun delete(conversationId: Long) {
    runDao.deleteByConversation(conversationId)
    messageDao.deleteByConversation(conversationId)
    conversationDao.delete(conversationId)
  }

  suspend fun deleteAll(studentId: String) {
    runDao.deleteByStudent(studentId)
    messageDao.deleteByStudent(studentId)
    conversationDao.deleteByStudent(studentId)
  }

  private fun decodeChips(raw: String?): List<AnswerChip> =
    raw?.let { runCatching { json.decodeFromString<List<ChipJson>>(it) }.getOrNull() }?.map { AnswerChip(it.id, it.value) } ?: emptyList()

  private fun AssistantConversationEntity.toModel() = AssistantConversation(id, title, createdAtEpochMillis, updatedAtEpochMillis, ProviderId.fromId(lastProvider))

  private fun AssistantMessageEntity.toModel() = AssistantMessage(
    id = id,
    conversationId = conversationId,
    role = MessageRole.entries.firstOrNull { it.name == role } ?: MessageRole.ASSISTANT,
    text = text,
    chips = decodeChips(chipsJson),
    status = MessageStatus.entries.firstOrNull { it.name == status } ?: MessageStatus.DONE,
    failureKind = failureKind?.let { name -> FailureKind.entries.firstOrNull { it.name == name } },
    createdAtMillis = createdAtEpochMillis,
  )

  private fun AssistantRunEntity.toModel() = AssistantRun(
    id = id,
    conversationId = conversationId,
    messageId = messageId,
    startedAtMillis = startedAtEpochMillis,
    finishedAtMillis = finishedAtEpochMillis,
    steps = steps,
    provider = ProviderId.fromId(provider),
    routerModel = routerModel,
    chatModel = chatModel,
    deepModel = deepModel,
    tierReached = tierReached?.let { name -> ModelTier.entries.firstOrNull { it.name == name } },
    groups = groupsJson?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList(),
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    costUsd = costUsd,
    waitedSeconds = waitedSeconds,
    tools = toolTracesJson?.let { runCatching { json.decodeFromString<List<TraceJson>>(it) }.getOrNull() }?.map { AssistantToolTrace(it.name, it.args, it.millis, it.ok, it.chars, it.preview) } ?: emptyList(),
    outcome = outcome,
    error = error,
  )
}
