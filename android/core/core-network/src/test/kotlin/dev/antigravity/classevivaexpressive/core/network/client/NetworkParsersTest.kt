package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkParsersTest {
  @Test
  fun normalizeCommunicationDetail_extractsActionUrlsAndAttachments() {
    val payload = Json.parseToJsonElement(
      """
      {
        "item": { "text": "Dettaglio completo della circolare" },
        "detailLink": "/sgv/app/default/notice.php?id=99",
        "confirmAction": { "href": "/sgv/app/default/sign.php?id=99" },
        "replyAction": { "url": "/sgv/app/default/reply.php?id=99" },
        "joinAction": { "link": "/sgv/app/default/join.php?id=99" },
        "uploadAction": { "path": "home/app/default/upload.php?id=99" },
        "attachments": [
          { "id": "pdf", "name": "circolare.pdf", "link": "/files/circolare.pdf", "mimeType": "application/pdf" }
        ]
      }
      """.trimIndent(),
    ).obj()

    val base = Communication(
      id = "99",
      pubId = "99",
      evtCode = "CIR",
      title = "Circolare",
      contentPreview = "Preview",
      sender = "Scuola",
      date = "2026-03-20",
      read = false,
      needsAck = true,
      needsReply = true,
      needsJoin = true,
      needsFile = true,
    )

    val detail = normalizeCommunicationDetail(payload, base)

    assertEquals("Dettaglio completo della circolare", detail.content)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/notice.php?id=99", detail.portalDetailUrl)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/sign.php?id=99", detail.acknowledgeUrl)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/reply.php?id=99", detail.replyUrl)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/join.php?id=99", detail.joinUrl)
    assertEquals("https://web.spaggiari.eu/home/app/default/upload.php?id=99", detail.fileUploadUrl)
    assertEquals(1, detail.communication.attachments.size)
    assertEquals("https://web.spaggiari.eu/files/circolare.pdf", detail.communication.attachments.first().url)
  }

  @Test
  fun normalizeAbsence_extractsJustificationUrls() {
    val absence = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "44",
          "evtDate": "20260325",
          "tipo": "assenza",
          "giustificata": false,
          "justificationLink": { "href": "/sgv/app/default/justify.php?id=44" },
          "detailUrl": "/sgv/app/default/absence.php?id=44"
        }
        """.trimIndent(),
      ),
    )

    assertFalse(absence.justified)
    assertEquals("2026-03-25", absence.date)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/justify.php?id=44", absence.justifyUrl)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/absence.php?id=44", absence.detailUrl)
  }

  @Test
  fun normalizeCommunication_marksActionDrivenItemsAsExternal() {
    val communication = normalizeCommunication(
      Json.parseToJsonElement(
        """
        {
          "id": "7",
          "pubId": "7",
          "evtCode": "AVV",
          "cntTitle": "Avviso",
          "itemText": "Serve una conferma",
          "authorName": "Segreteria",
          "evtDate": "2026-03-10",
          "needSign": true
        }
        """.trimIndent(),
      ),
    )

    assertTrue(communication.needsAck)
    assertEquals(NoticeboardActionType.ACKNOWLEDGE, communication.actions.first().type)
    assertEquals(CapabilityStatus.EXTERNAL_ONLY, communication.capabilityState.status)
  }

  @Test
  fun normalizeAgendaItem_stripsIsoPrefixAndDuplicateDetail() {
    val agenda = normalizeAgendaItem(
      Json.parseToJsonElement(
        """
        {
          "evtId": "a1",
          "title": "2026-03-31T00:00:00+02:00 Verifica scritta di chimica",
          "description": "2026-03-31T00:00:00+02:00 Verifica scritta di chimica",
          "evtDatetimeBegin": "2026-03-31T09:00:00+02:00",
          "subjectDesc": "Chimica"
        }
        """.trimIndent(),
      ),
    )

    assertEquals("Verifica scritta di chimica", agenda.title)
    assertEquals("2026-03-31", agenda.date)
    assertEquals("09:00", agenda.time)
    assertNull(agenda.detail)
  }

  @Test
  fun normalizeAbsence_mapsLateAndExitSeparately() {
    val late = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "1",
          "evtDate": "2026-03-25",
          "tipo": "ritardo"
        }
        """.trimIndent(),
      ),
    )
    val exit = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "2",
          "evtDate": "2026-03-26",
          "tipo": "uscita anticipata"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AbsenceType.LATE, late.type)
    assertEquals(AbsenceType.EXIT, exit.type)
  }

  @Test
  fun normalizeNote_mapsCategoryCodesToProductLabels() {
    val note = normalizeNote(
      Json.parseToJsonElement(
        """
        {
          "evtId": "n1",
          "evtDate": "2026-03-20",
          "evtText": "Richiamo per comportamento"
        }
        """.trimIndent(),
      ).obj(),
      "NTWN",
    )
    val annotation = normalizeNote(
      Json.parseToJsonElement(
        """
        {
          "evtId": "n2",
          "evtDate": "2026-03-20",
          "evtText": "Annotazione generica"
        }
        """.trimIndent(),
      ).obj(),
      "NTCL",
    )

    assertEquals("Richiamo", note.categoryLabel)
    assertEquals("warning", note.severity)
    assertEquals("Annotazione", annotation.categoryLabel)
    assertEquals("info", annotation.severity)
  }

  @Test
  fun normalizeMaterialAsset_keepsExternalSourceWhenPreviewIsMissing() {
    val asset = normalizeMaterialAsset(
      item = dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem(
        id = "m1",
        teacherId = "t1",
        teacherName = "Docente",
        folderId = "f1",
        folderName = "Cartella",
        title = "Link esterno",
        objectId = "obj",
        objectType = "link",
        sharedAt = "2026-03-20",
        capabilityState = dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState(),
      ),
      mimeType = null,
      base64Content = null,
      textPreview = null,
      sourceUrl = "https://example.com/materiale",
    )

    assertEquals(CapabilityStatus.EXTERNAL_ONLY, asset.capabilityState.status)
    assertEquals("https://example.com/materiale", asset.sourceUrl)
  }

  @Test
  fun normalizeDocumentAsset_marksHtmlPreviewAsAvailable() {
    val asset = normalizeDocumentAsset(
      item = DocumentItem(
        id = "doc-1",
        title = "Pagella online",
        detail = "Documento",
      ),
      fileName = "pagella.html",
      mimeType = "text/html",
      base64Content = "PGgxPk9rPC9oMT4=",
      textPreview = "<h1>Ok</h1>",
      sourceUrl = "https://web.spaggiari.eu/sgv/app/default/report.php",
    )

    assertEquals(CapabilityStatus.AVAILABLE, asset.capabilityState.status)
    assertEquals("Anteprima web", asset.capabilityState.label)
    assertEquals("pagella.html", asset.fileName)
  }
}
