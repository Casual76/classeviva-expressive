package dev.antigravity.classevivaexpressive.core.assistant.tools

import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.classevivaexpressive.core.assistant.actions.*
import dev.antigravity.fluidengine.ai.tools.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SchoolDataToolsTest {
  private val ctx = mockk<AssistantToolContext>(relaxed = true)
  @Test fun malformedEventArgumentsHaveNoEffects() = runTest {
    val tool = AgendaAggiungiEventoTool()
    assertEquals(ToolOutcome.ERROR, tool.run(buildJsonObject { put("titolo", "x"); put("data", JsonArray(emptyList())) }, ctx).result?.outcome)
    coVerify(exactly = 0) { ctx.actions.perform(any()) }
  }
  @Test fun eventIdentityReusesOperationAndOnlyReadbackCompletes() = runTest {
    val auth = mockk<AuthRepository>(); val agenda = mockk<AgendaRepository>(); val sink = mockk<AssistantActionSink>()
    val events = mutableListOf<CustomEvent>()
    every { ctx.actionsEnabled } returns true; every { ctx.auth } returns auth
    every { ctx.actions } returns sink; every { ctx.agenda } returns agenda
    every { ctx.today } returns LocalDate.of(2026,11,7)
    every { auth.authenticationEpoch() } returns 1
    every { auth.dataEpoch() } returns 1
    every { auth.session } returns MutableStateFlow(UserSession("token", "student", "account", StudentProfile(id="student")))
    every { agenda.observeCustomEvents() } answers { flowOf(events.toList()) }
    coEvery { sink.perform(any()) } coAnswers {
      val event = (firstArg<AssistantAction>() as AssistantAction.AddCustomEvent).event
      events.removeAll { it.id == event.id }; events += event
      ActionResult(ActionOutcome.DONE)
    }
    val args = buildJsonObject { put("titolo", "Study"); put("data", "2026-11-08") }
    val tool = TracedTool(AgendaAggiungiEventoTool())
    val first = tool.run(args, ctx, ToolExecution("stable-operation"))
    val second = tool.run(args, ctx, ToolExecution("stable-operation"))
    assertEquals(ToolOutcome.COMPLETED, first.result?.outcome)
    assertEquals(first.result?.entities, second.result?.entities)
    assertEquals("stable-operation", first.result?.operation?.id)
    assertEquals(1, events.size)
    events.clear()
    coEvery { sink.perform(any()) } returns ActionResult(ActionOutcome.DONE)
    assertEquals(ToolOutcome.UNCERTAIN, tool.run(args, ctx, ToolExecution("unverified")).result?.outcome)
    every { agenda.observeCustomEvents() } returns kotlinx.coroutines.flow.flow { throw java.io.IOException("readback unavailable") }
    val unreadable = tool.run(args, ctx, ToolExecution("readback-error")).result!!
    assertEquals(ToolOutcome.UNCERTAIN, unreadable.outcome)
    assertEquals("readback-error", unreadable.operation?.id)
    assertTrue(unreadable.entities.isEmpty())
  }
  @Test fun malformedDataArgumentsDoNotRetrieve() = runTest {
    val access = mockk<SchoolDataAccess>(); every { ctx.schoolData } returns access
    val result = RegistroDatiTool().run(buildJsonObject { put("sezione", "grades"); put("limite", -1) }, ctx)
    assertEquals(ToolOutcome.ERROR, result.result?.outcome)
    coVerify(exactly = 0) { access.read(any()) }
  }
  @Test fun typedPageRetainsIdsCoverageAndActualRemainingCursor() = runTest {
    val access = mockk<SchoolDataAccess>(); every { ctx.schoolData } returns access
    val row = buildJsonObject { put("id", "source-1"); put("date", "2026-11-07") }
    coEvery { access.read(any()) } returns SchoolDataPage(listOf(row), 2, "snapshot:1",
      buildJsonObject { put("count", 2) }, "account", "student", "2026-2027", "agenda",
      100, 200, false, true, true, true, "2026-09-01", "2027-08-31")
    val result = RegistroDatiTool().run(buildJsonObject { put("sezione", "agenda"); put("anno", "2026-2027") }, ctx).result!!
    assertEquals(ToolOutcome.PARTIAL, result.outcome)
    assertEquals("source-1", result.entities.single().id)
    assertEquals("snapshot:1", result.pagination?.nextCursor)
    assertEquals(2L, result.pagination?.total)
    assertEquals(true, result.coverage?.complete)
    assertEquals(100L, result.freshness?.syncedAtMillis)
    assertEquals("refreshed", result.data!!["retrieval"]!!.jsonPrimitive.content)
  }

  @Test fun everyAdvertisedAddedFilterAloneRoutesThroughActualRegistry() = runTest {
    val access = mockk<SchoolDataAccess>(); every { ctx.schoolData } returns access
    every { ctx.schoolYear.observeSelectedSchoolYear() } returns flowOf(SchoolYearRef(2026, 2027))
    coEvery { access.read(any()) } throws SchoolDataUnavailable("filter_reached")
    val values = mapOf("anno" to JsonPrimitive("2026-2027"), "account" to JsonPrimitive("account"),
      "studente" to JsonPrimitive("student"), "da" to JsonPrimitive("2026-10-01"), "a" to JsonPrimitive("2026-11-01"),
      "materia" to JsonPrimitive("Math"), "periodo" to JsonPrimitive("1"), "cerca" to JsonPrimitive("algebra"),
      "cursore" to JsonPrimitive("cursor"), "limite" to JsonPrimitive(1), "aggiorna" to JsonPrimitive(false),
      "dettaglio_offset" to JsonPrimitive(0), "dettaglio_hash" to JsonPrimitive("hash"))
    val registry = AllTools.registry()
    for (name in listOf("voti_elenco", "voti_media", "impegni", "lezioni_svolte", "comunicazioni_cerca",
      "assenze_elenco", "note_disciplinari", "materiali_cerca", "documenti")) {
      val tool = registry.find(name)!!
      val native = when(name) {
        "voti_elenco" -> VotiElencoTool(); "voti_media" -> VotiMediaTool(); "impegni" -> ImpegniTool()
        "lezioni_svolte" -> LezioniSvolteTool(); "comunicazioni_cerca" -> ComunicazioniCercaTool()
        "assenze_elenco" -> AssenzeElencoTool(); "note_disciplinari" -> NoteDisciplinariTool()
        "materiali_cerca" -> MaterialiCercaTool(); else -> DocumentiTool()
      }
      val nativeKeys = (native.parameters["properties"] as JsonObject).keys
      for ((key,value) in values.filterKeys { it !in nativeKeys }) {
        val out = tool.run(JsonObject(mapOf(key to value)), ctx)
        assertEquals("$name/$key silently ignored", "filter_reached", out.result?.error?.code)
      }
    }
  }
  @Test fun oversizedRecordHasRecoverableFragmentsAndCannotAdvanceEarly() = runTest {
    val access = mockk<SchoolDataAccess>(); every { ctx.schoolData } returns access
    val row = buildJsonObject { put("id", "large"); put("content", "x".repeat(6000)) }
    coEvery { access.read(any()) } returns SchoolDataPage(listOf(row), 2, "next:1", buildJsonObject { put("weightedAverage", 7.125) },
      "account", "student", "2026-2027", "agenda", 1, 2, false, false, true, true, null, null)
    val tool = RegistroDatiTool()
    var args = buildJsonObject { put("sezione", "agenda"); put("anno", "2026-2027") }
    val recovered = StringBuilder()
    do {
      val out = tool.run(args, ctx)
      assertTrue(out.modelText(4000).length <= 4000)
      
      val fragment = out.text.substringAfter("di ${row.toString().length}:\n")
      if (fragment.isEmpty()) fail(out.text)
      recovered.append(fragment)
      if (out.result?.pagination?.hasMore == true && out.result?.pagination?.nextCursor == null) {
        val offset = Regex("dettaglio_offset=([0-9]+)").find(out.text)!!.groupValues[1].toInt()
        val hash = Regex("dettaglio_hash=([a-f0-9]+)").find(out.text)!!.groupValues[1]
        args = JsonObject(args + mapOf("dettaglio_offset" to JsonPrimitive(offset), "dettaglio_hash" to JsonPrimitive(hash)))
      } else {
        assertEquals("next:1", out.result?.pagination?.nextCursor)
        assertEquals(row.toString(), recovered.toString()); break
      }
    } while(true)
  }

  @Test fun aliasChangeDuringActionCannotClaimCompletionWithPreservedAuthEpoch() = runTest {
    val auth = mockk<AuthRepository>(); val sink = mockk<AssistantActionSink>(); var dataEpoch = 7L
    every { ctx.actionsEnabled } returns true; every { ctx.auth } returns auth; every { ctx.actions } returns sink
    every { auth.authenticationEpoch() } returns 3
    every { auth.dataEpoch() } answers { dataEpoch }
    every { auth.session } returns MutableStateFlow(UserSession("fixture", "student", "account", StudentProfile(id="student")))
    every { ctx.today } returns LocalDate.of(2026,11,7)
    coEvery { sink.perform(any()) } coAnswers {
      val action = firstArg<AssistantAction>() as AssistantAction.AddCustomEvent
      assertEquals(7L, action.expectedDataEpoch); assertEquals(3L, action.expectedAuthenticationEpoch)
      dataEpoch = 8L
      ActionResult(ActionOutcome.DONE)
    }
    val out = AllTools.registry().find("agenda_aggiungi_evento")!!.run(buildJsonObject {
      put("titolo", "Study"); put("data", "2026-11-08")
    }, ctx, ToolExecution("alias-switch"))
    assertEquals(ToolOutcome.ERROR, out.result?.outcome)
    assertTrue(out.text.contains("account_changed"))
  }

  @Test fun nativeCommunicationArgumentCombinationsRetainNativeFiltersAndLimits() = runTest {
    val access = mockk<SchoolDataAccess>(); every { ctx.schoolData } returns access
    every { ctx.today } returns LocalDate.of(2026, 11, 7)
    val rows = (1..15).map { Communication(id = "c$it", pubId = "p$it", evtCode = "NT", title = "Gita $it", contentPreview = "", sender = "School", date = "2026-11-07", read = false) }
    every { ctx.communications.observeCommunications() } returns flowOf(rows)
    val native = ComunicazioniCercaTool()
    val wrapped = AllTools.registry().find("comunicazioni_cerca")!!
    for (args in listOf(buildJsonObject { put("testo", "gita"); put("limite", 12) },
      buildJsonObject { put("testo", "gita"); put("limite", 3); put("solo_non_lette", true); put("da", "2026-11-01"); put("a", "2026-11-07") })) {
      val result = wrapped.run(args, ctx)
      assertEquals(native.run(args, ctx).text, result.text)
      assertEquals(ToolOutcome.UNCERTAIN, result.result?.outcome)
    }
    val mixed = wrapped.run(buildJsonObject { put("testo", "gita"); put("limite", 12); put("anno", "2026-2027") }, ctx)
    assertTrue(mixed.text.startsWith("errore"))
    coVerify(exactly = 0) { access.read(any()) }
  }

}
