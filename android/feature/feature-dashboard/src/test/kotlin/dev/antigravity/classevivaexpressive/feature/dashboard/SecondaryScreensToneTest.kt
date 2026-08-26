package dev.antigravity.classevivaexpressive.feature.dashboard

import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityState
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityStatus
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentItem
import dev.antigravity.classevivaexpressive.core.domain.model.DocumentKind
import dev.antigravity.classevivaexpressive.core.domain.model.MaterialItem
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryScreensToneTest {

  private val today = LocalDate.of(2026, 3, 10)

  @Test
  fun homeworkDue_coversEveryEdge() {
    assertEquals(HomeworkDue.Overdue, homeworkDue(today.minusDays(1).toString(), today))
    assertEquals(HomeworkDue.Today, homeworkDue(today.toString(), today))
    assertEquals(HomeworkDue.Tomorrow, homeworkDue(today.plusDays(1).toString(), today))
    assertEquals(HomeworkDue.Soon, homeworkDue(today.plusDays(6).toString(), today))
    assertEquals(HomeworkDue.Soon, homeworkDue(today.plusDays(7).toString(), today))
    assertEquals(HomeworkDue.Later, homeworkDue(today.plusDays(8).toString(), today))
    assertEquals(HomeworkDue.Unknown, homeworkDue(null, today))
    assertEquals(HomeworkDue.Unknown, homeworkDue("", today))
    assertEquals(HomeworkDue.Unknown, homeworkDue("non-una-data", today))
  }

  @Test
  fun onlyWhatCanStillBeDoneInTimeIsUrgent() {
    // Prima erano tutti Warning, cioe' nessuno lo era. Uno scaduto non e' un'urgenza: e' storia.
    assertEquals(FluidTone.Warning, HomeworkDue.Today.tone())
    assertEquals(FluidTone.Warning, HomeworkDue.Tomorrow.tone())
    assertEquals(FluidTone.Info, HomeworkDue.Soon.tone())
    assertEquals(FluidTone.Neutral, HomeworkDue.Overdue.tone())
    assertEquals(FluidTone.Neutral, HomeworkDue.Later.tone())
    assertEquals(FluidTone.Neutral, HomeworkDue.Unknown.tone())
  }

  @Test
  fun homeworkBadgeSaysWhen() {
    assertEquals("SCADUTO", HomeworkDue.Overdue.badgeLabel(today.minusDays(1).toString(), today))
    assertEquals("OGGI", HomeworkDue.Today.badgeLabel(today.toString(), today))
    assertEquals("DOMANI", HomeworkDue.Tomorrow.badgeLabel(today.plusDays(1).toString(), today))
    assertEquals("FRA 3 GG", HomeworkDue.Soon.badgeLabel(today.plusDays(3).toString(), today))
    assertEquals("COMPITO", HomeworkDue.Unknown.badgeLabel(null, today))
  }

  @Test
  fun materialToneDistinguishesLinkFileAndUnavailable() {
    assertEquals(FluidTone.Info, material(type = "link").materialTone())
    assertEquals("LINK", material(type = "link").materialBadgeLabel())
    assertEquals(FluidTone.Neutral, material(type = "file").materialTone())
    assertEquals("FILE", material(type = "file").materialBadgeLabel())
    val gone = material(type = "file", status = CapabilityStatus.UNAVAILABLE)
    assertEquals(FluidTone.Warning, gone.materialTone())
    assertEquals("NON DISPONIBILE", gone.materialBadgeLabel())
  }

  @Test
  fun aReportCardIsAnOutcomeAndADocumentIsADocument() {
    assertEquals(FluidTone.Success, document(DocumentKind.SCHOOL_REPORT).documentTone())
    assertEquals("PAGELLA", document(DocumentKind.SCHOOL_REPORT).documentBadgeLabel())
    assertEquals(FluidTone.Neutral, document(DocumentKind.DOCUMENT).documentTone())
    assertEquals("DOCUMENTO", document(DocumentKind.DOCUMENT).documentBadgeLabel())
  }

  private fun material(
    type: String,
    status: CapabilityStatus = CapabilityStatus.AVAILABLE,
  ) = MaterialItem(
    id = "m",
    teacherId = "t",
    teacherName = "Prof",
    folderId = "f",
    folderName = "Cartella",
    title = "Titolo",
    objectId = "o",
    objectType = type,
    sharedAt = "2026-03-01",
    capabilityState = CapabilityState(status = status),
  )

  private fun document(kind: DocumentKind) = DocumentItem(
    id = "d",
    title = "Titolo",
    detail = "Dettaglio",
    kind = kind,
  )
}
