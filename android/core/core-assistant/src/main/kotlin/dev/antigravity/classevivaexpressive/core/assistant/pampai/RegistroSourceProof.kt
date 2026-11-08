package dev.antigravity.classevivaexpressive.core.assistant.pampai

import dev.antigravity.classevivaexpressive.core.assistant.tools.AssistantToolContext
import dev.antigravity.classevivaexpressive.core.datastore.AuthSnapshotLease
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.fluidengine.ai.tools.ToolRegistry

/** Bind to existing atomic in-memory publication, without accessing credential fields. */
internal fun registroSourceProofRegistry(base: ToolRegistry<AssistantToolContext>, authority: String,
    publication: () -> AuthSnapshotLease): ToolRegistry<AssistantToolContext> =
    SourceProofRegistry(base, "cv", authority, setOf("registro_dati"), state = { ctx: AssistantToolContext ->
        val auth = ctx.auth
        val captured = publication()
        val before = auth?.dataEpoch()
        val session = auth?.session?.value
        if (auth == null || session == null || before != auth.dataEpoch() ||
            before != captured.lease.epoch ||
            captured.snapshot?.authenticationState?.rest != RestAuthenticationState.READY ||
            captured !== publication()) null
        else ProofState(session.username, session.studentId, "$before:${session.authMethod}", ProofIdentity(captured))
    }, caller = { it.sourceProofCaller }).registry()
