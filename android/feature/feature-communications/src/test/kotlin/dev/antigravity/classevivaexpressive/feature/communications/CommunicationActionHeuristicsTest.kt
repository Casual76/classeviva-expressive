package dev.antigravity.classevivaexpressive.feature.communications

import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.CommunicationDetail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationActionHeuristicsTest {

  @Test
  fun shouldShowAcknowledgeAction_staysHiddenForSimpleUnreadMessages() {
    val detail = buildDetail(
      communication = buildCommunication(read = false),
      content = "Messaggio informativo senza presa visione.",
    )

    assertFalse(shouldShowAcknowledgeAction(detail))
  }

  @Test
  fun shouldShowReplyComposer_requiresAnActionableEndpoint() {
    val detail = buildDetail(
      content = "Autorizzazione uscita. Conferma adesione entro domani.",
    )

    assertFalse(shouldShowReplyComposer(detail))
  }

  @Test
  fun shouldShowReplyComposer_usesPortalDetailFallbackWhenReplyIntentIsDetected() {
    val detail = buildDetail(
      content = "Rispondere al questionario entro domani.",
      portalDetailUrl = "https://web.spaggiari.eu/sgv/app/default/notice.php?id=7",
    )

    assertTrue(shouldShowReplyComposer(detail))
  }

  @Test
  fun shouldShowJoinAction_usesPortalDetailFallbackWhenJoinIntentIsDetected() {
    val detail = buildDetail(
      content = "Compilare l'adesione all'uscita entro venerdi.",
      portalDetailUrl = "https://web.spaggiari.eu/sgv/app/default/notice.php?id=7",
    )

    assertTrue(shouldShowJoinAction(detail))
  }

  @Test
  fun shouldShowUploadAction_usesPortalDetailFallbackWhenUploadIntentIsDetected() {
    val detail = buildDetail(
      content = "Caricare il modulo firmato in pdf nella comunicazione.",
      portalDetailUrl = "https://web.spaggiari.eu/sgv/app/default/notice.php?id=7",
    )

    assertTrue(shouldShowUploadAction(detail))
  }

  private fun buildCommunication(
    read: Boolean = true,
    needsAck: Boolean = false,
    needsReply: Boolean = false,
    needsJoin: Boolean = false,
    needsFile: Boolean = false,
  ) = Communication(
    id = "c1",
    pubId = "c1",
    evtCode = "CIR",
    title = "Circolare",
    contentPreview = "Preview",
    sender = "Scuola",
    date = "2026-03-20",
    read = read,
    needsAck = needsAck,
    needsReply = needsReply,
    needsJoin = needsJoin,
    needsFile = needsFile,
    capabilityState = CapabilityState(),
  )

  private fun buildDetail(
    communication: Communication = buildCommunication(),
    content: String = "Contenuto",
    portalDetailUrl: String? = null,
    replyUrl: String? = null,
    joinUrl: String? = null,
    fileUploadUrl: String? = null,
    replyText: String? = null,
  ) = CommunicationDetail(
    communication = communication,
    content = content,
    portalDetailUrl = portalDetailUrl,
    replyUrl = replyUrl,
    joinUrl = joinUrl,
    fileUploadUrl = fileUploadUrl,
    replyText = replyText,
  )
}
