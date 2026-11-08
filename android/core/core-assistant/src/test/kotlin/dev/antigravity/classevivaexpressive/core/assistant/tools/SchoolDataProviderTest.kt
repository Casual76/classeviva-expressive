package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.*
import dev.antigravity.fluidengine.ai.orchestrator.*
import dev.antigravity.fluidengine.ai.tools.*
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SchoolDataProviderTest {
  @Test fun actualProviderMessagesContainEveryRecordAndWeightedAggregateWithoutPerRowCalls() = runBlocking {
    val context = mockk<AssistantToolContext>(relaxed = true)
    val rows = (1..8).map { buildJsonObject { put("id", "record-$it"); put("date", "2026-11-07"); put("numericValue", it); put("weight", it) } }
    val aggregate = SchoolDataPaging.aggregates(rows)
    val access = object : SchoolDataAccess {
      override suspend fun read(query: SchoolDataQuery): SchoolDataPage {
        val (page, total, next) = SchoolDataPaging.page(query, rows, "snapshot")
        return SchoolDataPage(page, total, next, aggregate, "account", "student", "2026-2027", "grades",
          1, 2, false, false, true, true, null, null)
      }
    }
    every { context.schoolData } returns access
    var cursor: String? = null
    var offset = 0
    val messages = mutableListOf<Message.ToolResult>()
    val provider = object : ChatProvider {
      override val id = ProviderId.GROQ
      override suspend fun complete(request: ChatRequest) = ChatTurn(Message.Assistant("{}"), FinishReason.STOP, null, RateLimitInfo.EMPTY)
      override fun stream(request: ChatRequest): Flow<ChatDelta> = flow {
        val result = request.messages.filterIsInstance<Message.ToolResult>().lastOrNull()
        if (result != null) {
          messages += result
          cursor = Regex("Prossima pagina: cursore=([^;]+)").find(result.content)?.groupValues?.get(1)
        }
        if (result == null || cursor != null) {
          offset++
          val args = buildJsonObject { put("sezione", "grades"); put("anno", "2026-2027"); cursor?.let { put("cursore", it) } }
          emit(ChatDelta.ToolCallPart(0, "call-$offset", "registro_dati", args.toString()))
          emit(ChatDelta.Finish(FinishReason.TOOL_CALLS, null, RateLimitInfo.EMPTY))
        } else {
          emit(ChatDelta.Text("fine")); emit(ChatDelta.Finish(FinishReason.STOP, null, RateLimitInfo.EMPTY))
        }
      }
      override suspend fun listModels() = ModelCatalogue(emptyList(), emptyList())
      override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions) = Transcript("", null)
    }
    val registry = ToolRegistry(listOf(RegistroDatiTool()), RegistroToolGroup.entries)
    val router = AiRouter(RegistroToolGroup.entries, RegistroToolGroup.APP, "test", defaultGroups = listOf(RegistroToolGroup.APP))
    val orchestrator = AiOrchestrator(registry, router, AiDiagnosticsLog(), config = AiOrchestratorConfig(maxRounds = 12))
    orchestrator.ask(AskInput(question = "leggi", mode = AskMode.TEXT, language = "it", settings = AiSettings(),
      providers = listOf(ReadyProvider(provider, "model", "stt", "small")), toolContext = context,
      systemPrompt = "test", conversation = Conversation(1,0), actionsEnabled = true,
      preselectedGroups = setOf(RegistroToolGroup.APP)), MutableStateFlow(AssistantState.Idle))
    assertEquals("Compact records must share the bounded page instead of requiring one call per row", 1, messages.size)
    for (id in 1..8) assertTrue("record $id inaccessible", messages.any { it.content.contains("record-$id") })
    assertTrue(messages.all { it.content.contains("\"weightedAverage\":${aggregate["weightedAverage"]}") })
    assertTrue(messages.all { it.content.length <= 4000 })
  }
}
