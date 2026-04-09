package dev.antigravity.classevivaexpressive.core.network.client

import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaCategory
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.NoticeboardActionType
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkParsersTest {
  @Test
  fun normalizeCommunicationDetail_preservesPortalUrlsForGateway() {
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
    assertNull(detail.communication.attachments.first().url)
    assertTrue(detail.communication.attachments.first().portalOnly)
    assertEquals(CapabilityStatus.AVAILABLE, detail.communication.capabilityState.status)
  }

  @Test
  fun normalizeCommunicationDetail_keepsOfficialRestUrls() {
    val payload = Json.parseToJsonElement(
      """
      {
        "item": { "text": "Dettaglio completo della circolare" },
        "confirmAction": { "href": "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/read/CIR/99/101" },
        "replyAction": { "url": "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/reply/CIR/99/101" },
        "attachments": [
          {
            "id": "pdf",
            "name": "circolare.pdf",
            "link": "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/attach/CIR/99/101",
            "mimeType": "application/pdf"
          }
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
    )

    val detail = normalizeCommunicationDetail(payload, base)

    assertEquals(
      "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/read/CIR/99/101",
      detail.acknowledgeUrl,
    )
    assertEquals(
      "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/reply/CIR/99/101",
      detail.replyUrl,
    )
    assertEquals(
      "https://web.spaggiari.eu/rest/v1/students/55/noticeboard/attach/CIR/99/101",
      detail.communication.attachments.first().url,
    )
    assertFalse(detail.communication.attachments.first().portalOnly)
    assertEquals(CapabilityStatus.AVAILABLE, detail.communication.capabilityState.status)
  }

  @Test
  fun normalizeAbsence_preservesPortalJustificationUrlsForGateway() {
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
    assertTrue(absence.canJustify)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/justify.php?id=44", absence.justifyUrl)
    assertEquals("https://web.spaggiari.eu/sgv/app/default/absence.php?id=44", absence.detailUrl)
  }

  @Test
  fun normalizeCommunication_marksAckActionsAsAvailableWhenHandledInApp() {
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
    assertNull(communication.actions.first().url)
    assertEquals(CapabilityStatus.AVAILABLE, communication.capabilityState.status)
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
  fun normalizeAbsence_prefersEvtCodeMappingOverText() {
    val late = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "1",
          "evtDate": "20260325",
          "evtCode": "RTD",
          "tipo": "assenza"
        }
        """.trimIndent(),
      ),
    )
    val exit = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "2",
          "evtDate": "20260326",
          "evtCode": "USC",
          "tipo": "assenza"
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
  fun normalizeMaterialAsset_marksNonOfficialSourceAsUnavailable() {
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

    assertEquals(CapabilityStatus.UNAVAILABLE, asset.capabilityState.status)
    assertEquals("https://example.com/materiale", asset.sourceUrl)
  }

  @Test
  fun normalizeMaterialAsset_keepsOfficialSourceWhenPreviewIsMissing() {
    val asset = normalizeMaterialAsset(
      item = dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem(
        id = "m1",
        teacherId = "t1",
        teacherName = "Docente",
        folderId = "f1",
        folderName = "Cartella",
        title = "Download ufficiale",
        objectId = "obj",
        objectType = "link",
        sharedAt = "2026-03-20",
        capabilityState = dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState(),
      ),
      mimeType = null,
      base64Content = null,
      textPreview = null,
      sourceUrl = "https://web.spaggiari.eu/rest/v1/students/55/didactics/item/10",
    )

    assertEquals(CapabilityStatus.EXTERNAL_ONLY, asset.capabilityState.status)
    assertEquals("https://web.spaggiari.eu/rest/v1/students/55/didactics/item/10", asset.sourceUrl)
  }

  // ─── normalizeGrade ───────────────────────────────────────────────────────

  @Test
  fun normalizeGrade_parsesNumericGrade() {
    val grade = normalizeGrade(
      Json.parseToJsonElement(
        """
        {
          "evtId": "g1",
          "subjectDesc": "Matematica",
          "decimalValue": 7.5,
          "evtDate": "2026-03-20",
          "componentDesc": "Scritto"
        }
        """.trimIndent(),
      ),
    )

    assertEquals("g1", grade.id)
    assertEquals("Matematica", grade.subject)
    assertEquals(7.5, grade.numericValue)
    assertEquals("2026-03-20", grade.date)
    assertEquals("Scritto", grade.type)
  }

  @Test
  fun normalizeGrade_handlesTextualGradeWithNullNumericValue() {
    val grade = normalizeGrade(
      Json.parseToJsonElement(
        """
        {
          "evtId": "g2",
          "subjectDesc": "Ed. Fisica",
          "displayValue": "NC",
          "evtDate": "2026-03-21",
          "componentDesc": "Pratico"
        }
        """.trimIndent(),
      ),
    )

    assertNull(grade.numericValue)
    assertEquals("NC", grade.valueLabel)
  }

  @Test
  fun normalizeGrade_parsesWeightFactor() {
    val grade = normalizeGrade(
      Json.parseToJsonElement(
        """
        {
          "evtId": "g3",
          "subjectDesc": "Fisica",
          "decimalValue": 8.0,
          "weightFactor": 2.0,
          "evtDate": "2026-03-22"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(2.0, grade.weight)
  }

  @Test
  fun normalizeGrade_hasPeriodCodeNullWhenMissing() {
    val grade = normalizeGrade(
      Json.parseToJsonElement(
        """
        {
          "evtId": "g4",
          "subjectDesc": "Storia",
          "decimalValue": 6.0,
          "evtDate": "2026-03-20"
        }
        """.trimIndent(),
      ),
    )

    assertNull(grade.periodCode)
  }

  // ─── normalizeLesson ──────────────────────────────────────────────────────

  @Test
  fun normalizeLesson_parsesStandardLesson() {
    val lesson = normalizeLesson(
      Json.parseToJsonElement(
        """
        {
          "lessonId": "l1",
          "subjectDesc": "Matematica",
          "data": "2026-03-20",
          "lessonHour": "2",
          "duration": 60,
          "argomento": "Equazioni di secondo grado"
        }
        """.trimIndent(),
      ),
    )

    assertEquals("l1", lesson.id)
    assertEquals("Matematica", lesson.subject)
    assertEquals("2026-03-20", lesson.date)
    assertEquals(60, lesson.durationMinutes)
    assertEquals("Equazioni di secondo grado", lesson.topic)
  }

  @Test
  fun normalizeLesson_fallsBackToDefaultDurationWhenMissing() {
    val lesson = normalizeLesson(
      Json.parseToJsonElement(
        """
        {
          "lessonId": "l2",
          "subjectDesc": "Storia",
          "data": "2026-03-20"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(60, lesson.durationMinutes)
  }

  @Test
  fun normalizeLesson_handlesDateTimeBeginAsDate() {
    val lesson = normalizeLesson(
      Json.parseToJsonElement(
        """
        {
          "evtId": "l3",
          "subjectDesc": "Fisica",
          "evtDatetimeBegin": "2026-03-21T08:00:00+02:00"
        }
        """.trimIndent(),
      ),
    )

    assertEquals("2026-03-21", lesson.date)
    assertEquals("08:00", lesson.time)
  }

  // ─── normalizeHomework ────────────────────────────────────────────────────

  @Test
  fun normalizeHomework_parsesDueDateAndDescription() {
    val hw = normalizeHomework(
      Json.parseToJsonElement(
        """
        {
          "hwId": "hw1",
          "subjectDesc": "Matematica",
          "contenuto": "Esercizi pag. 45-47",
          "dataConsegna": "2026-03-25"
        }
        """.trimIndent(),
      ),
    )

    assertEquals("hw1", hw.id)
    assertEquals("Matematica", hw.subject)
    assertEquals("Esercizi pag. 45-47", hw.description)
    assertEquals("2026-03-25", hw.dueDate)
  }

  @Test
  fun normalizeHomework_hasEmptyAttachmentsByDefault() {
    val hw = normalizeHomework(
      Json.parseToJsonElement(
        """
        {
          "hwId": "hw2",
          "subjectDesc": "Fisica",
          "contenuto": "Studio teoria",
          "dataConsegna": "2026-03-26"
        }
        """.trimIndent(),
      ),
    )

    assertTrue(hw.attachments.isEmpty())
  }

  // ─── normalizeAbsence (additional cases) ─────────────────────────────────

  @Test
  fun normalizeAbsence_parsesJustifiedAbsence() {
    val absence = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "a1",
          "evtDate": "20260310",
          "tipo": "assenza",
          "giustificata": true
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AbsenceType.ABSENCE, absence.type)
    assertTrue(absence.justified)
  }

  @Test
  fun normalizeAbsence_parsesExitWithHour() {
    val exit = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "a2",
          "evtDate": "20260311",
          "tipo": "uscita anticipata",
          "evtHPos": 5,
          "giustificata": false
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AbsenceType.EXIT, exit.type)
    assertEquals(5, exit.hours)
    assertFalse(exit.justified)
  }

  @Test
  fun normalizeAbsence_parsesLateEntryNotJustified() {
    val late = normalizeAbsence(
      Json.parseToJsonElement(
        """
        {
          "evtId": "a3",
          "evtDate": "20260312",
          "tipo": "ritardo",
          "giustificata": false
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AbsenceType.LATE, late.type)
    assertFalse(late.justified)
  }

  // ─── normalizeAgendaItem (additional cases) ───────────────────────────────

  @Test
  fun normalizeAgendaItem_detectsHomeworkCategoryFromTitle() {
    val item = normalizeAgendaItem(
      Json.parseToJsonElement(
        """
        {
          "evtId": "ag1",
          "title": "Compito in classe di algebra",
          "evtDatetimeBegin": "2026-03-20T09:00:00+02:00",
          "subjectDesc": "Matematica"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AgendaCategory.HOMEWORK, item.category)
    assertEquals("2026-03-20", item.date)
  }

  @Test
  fun normalizeAgendaItem_detectsAssessmentCategoryFromTitle() {
    val item = normalizeAgendaItem(
      Json.parseToJsonElement(
        """
        {
          "evtId": "ag2",
          "title": "Verifica scritta di chimica organica",
          "evtDatetimeBegin": "2026-03-25T10:00:00+02:00"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AgendaCategory.ASSESSMENT, item.category)
  }

  @Test
  fun normalizeAgendaItem_defaultsToEventCategoryForUnknownTitle() {
    val item = normalizeAgendaItem(
      Json.parseToJsonElement(
        """
        {
          "evtId": "ag3",
          "title": "Uscita didattica al museo",
          "evtDatetimeBegin": "2026-04-01T08:30:00+02:00"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(AgendaCategory.EVENT, item.category)
  }

  // ─── normalizeDocument ───────────────────────────────────────────────────

  @Test
  fun normalizeDocument_marksAvailableWhenOfficialViewUrlPresent() {
    val doc = normalizeDocument(
      Json.parseToJsonElement(
        """
        {
          "desc": "Pagella digitale",
          "viewLink": "https://web.spaggiari.eu/rest/v1/students/55/documents/read/123"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(CapabilityStatus.AVAILABLE, doc.capabilityState.status)
    assertNotNull(doc.viewUrl)
  }

  @Test
  fun normalizeDocument_marksUnavailableWhenNoOfficialUrl() {
    val doc = normalizeDocument(
      Json.parseToJsonElement(
        """
        {
          "desc": "Documento esterno",
          "viewLink": "https://example.com/doc.pdf"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(CapabilityStatus.UNAVAILABLE, doc.capabilityState.status)
    assertNull(doc.viewUrl)
  }

  // ─── normalizeSchoolbookCourse ────────────────────────────────────────────

  @Test
  fun normalizeSchoolbookCourse_parsesMultipleBooks() {
    val course = normalizeSchoolbookCourse(
      Json.parseToJsonElement(
        """
        {
          "courseId": "c1",
          "courseDesc": "Corso di Matematica",
          "books": {
            "books": [
              {
                "bookId": "b1",
                "isbnCode": "978-0-00-000000-1",
                "title": "Matematica.blu",
                "subjectDesc": "Matematica",
                "price": 22.50,
                "toBuy": true
              },
              {
                "bookId": "b2",
                "isbnCode": "978-0-00-000000-2",
                "title": "Quaderno di esercizi",
                "subjectDesc": "Matematica",
                "price": 8.90,
                "toBuy": false
              }
            ]
          }
        }
        """.trimIndent(),
      ),
    )

    assertEquals("c1", course.id)
    assertEquals(2, course.books.size)
    assertEquals("Matematica.blu", course.books[0].title)
    assertEquals("978-0-00-000000-1", course.books[0].isbn)
    assertTrue(course.books[0].toBuy)
    assertFalse(course.books[1].toBuy)
  }

  @Test
  fun normalizeSchoolbookCourse_returnsEmptyBooksListWhenNoBooksPresent() {
    val course = normalizeSchoolbookCourse(
      Json.parseToJsonElement(
        """
        {
          "courseId": "c2",
          "courseDesc": "Corso vuoto"
        }
        """.trimIndent(),
      ),
    )

    assertTrue(course.books.isEmpty())
  }

  // ─── normalizeDocumentAsset ───────────────────────────────────────────────

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
