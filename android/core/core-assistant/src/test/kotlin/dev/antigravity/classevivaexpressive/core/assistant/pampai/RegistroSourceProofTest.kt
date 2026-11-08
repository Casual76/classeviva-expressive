package dev.antigravity.classevivaexpressive.core.assistant.pampai

import dev.antigravity.classevivaexpressive.core.assistant.tools.*
import dev.antigravity.classevivaexpressive.core.datastore.AuthSnapshotLease
import dev.antigravity.classevivaexpressive.core.datastore.AccountSessionLease
import dev.antigravity.classevivaexpressive.core.datastore.StoredAuthSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.*
import dev.antigravity.fluidengine.ai.tools.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RegistroSourceProofTest {
    @Test fun actualRegistryRejectsSameAccountReauthWithoutRelyingOnAuthenticationEpoch() = runBlocking {
        val ctx = mockk<AssistantToolContext>(relaxed = true)
        val auth = mockk<AuthRepository>()
        val access = mockk<SchoolDataAccess>()
        val session = UserSession("synthetic-unused", "student", "account", StudentProfile(id = "student"))
        every { ctx.auth } returns auth
        every { ctx.sourceProofCaller } returns "signed.caller"
        every { ctx.schoolData } returns access
        every { auth.session } returns MutableStateFlow(session)
        every { auth.dataEpoch() } returns 7
        every { auth.authenticationEpoch() } returns 1
        val snapshot = mockk<StoredAuthSnapshot>()
        every { snapshot.authenticationState } returns AuthenticationState(rest = RestAuthenticationState.READY)
        var publication = AuthSnapshotLease(snapshot, AccountSessionLease(7, "student"), 1)
        val registry = registroSourceProofRegistry(AllTools.registry(), "synthetic.ai.tools") { publication }
        val advertised = registry.find("source_capabilities")!!.run(buildJsonObject {}, ctx).result!!.data!!
        val cap = advertised["capability"]!!
        val samePublication = registry.find("source_capabilities")!!.run(buildJsonObject {}, ctx).result!!.data!!
        assertEquals(advertised["authorization"], samePublication["authorization"])
        assertNotEquals(advertised["capability"], samePublication["capability"])
        val args = buildJsonObject {
            put("version", 1); put("capability", cap); put("tool", "registro_dati")
            put("arguments", buildJsonObject {
                put("account", "account"); put("studente", "student"); put("anno", "2026-2027"); put("sezione", "agenda")
            })
        }
        val receipt = registry.find("source_lease")!!.run(args, ctx).result!!.data!!
        assertEquals(advertised["authorization"], receipt["authorization"])
        val lease = receipt["lease"]!!
        val request = JsonObject(args + ("lease" to lease))
        // Equal non-secret epochs and identity, new real publication object: old proof is revoked.
        publication = AuthSnapshotLease(snapshot, AccountSessionLease(7, "student"), 1)
        val reauthenticated = registry.find("source_capabilities")!!.run(buildJsonObject {}, ctx).result!!.data!!
        assertNotEquals(advertised["authorization"], reauthenticated["authorization"])
        assertEquals(advertised["incarnation"], reauthenticated["incarnation"])
        val result = registry.find("source_read")!!.run(request, ctx).result!!
        assertEquals(ToolOutcome.ERROR, result.outcome)
        assertEquals("source_capability_invalid", result.error!!.code)
        coVerify(exactly = 0) { access.read(any()) }
        verify(exactly = 0) { auth.authenticationEpoch() }
    }
    @Test fun actualRegistryDoesNotAdvertiseMutationAsProofRead() {
        val registry = registroSourceProofRegistry(AllTools.registry(), "synthetic.ai.tools") { error("no auth read for catalog") }
        for (name in listOf("source_capabilities", "source_lease", "source_validate", "source_read")) {
            val tool = registry.find(name)!!
            assertFalse(tool.isAction); assertFalse(tool.needsConfirmation)
        }
    }
}
