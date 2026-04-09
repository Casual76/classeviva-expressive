package dev.antigravity.classevivaexpressive.feature.absences

import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveTone
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import org.junit.Assert.assertEquals
import org.junit.Test

class AbsencesLogicTest {

  // ─── absenceLabel ─────────────────────────────────────────────────────────

  @Test
  fun absenceLabel_returnsAssenzaForAbsenceType() {
    assertEquals("Assenza", absenceLabel(AbsenceType.ABSENCE))
  }

  @Test
  fun absenceLabel_returnsRitardoForLateType() {
    assertEquals("Ritardo", absenceLabel(AbsenceType.LATE))
  }

  @Test
  fun absenceLabel_returnsUscitaForExitType() {
    assertEquals("Uscita anticipata", absenceLabel(AbsenceType.EXIT))
  }

  // ─── badgeLabel ───────────────────────────────────────────────────────────

  @Test
  fun badgeLabel_returnsCorrectLetterForEachType() {
    assertEquals("A", badgeLabel(AbsenceType.ABSENCE))
    assertEquals("R", badgeLabel(AbsenceType.LATE))
    assertEquals("U", badgeLabel(AbsenceType.EXIT))
  }

  // ─── hoursLabel ───────────────────────────────────────────────────────────

  @Test
  fun hoursLabel_returnsIngressoLabelForLate() {
    assertEquals("Ingresso alla 2", hoursLabel(AbsenceType.LATE, 2))
  }

  @Test
  fun hoursLabel_returnsUscitaLabelForExit() {
    assertEquals("Uscita alla 4", hoursLabel(AbsenceType.EXIT, 4))
  }

  @Test
  fun hoursLabel_returnsOraLabelForAbsence() {
    assertEquals("Ora 1", hoursLabel(AbsenceType.ABSENCE, 1))
  }

  // ─── absenceTone ──────────────────────────────────────────────────────────

  @Test
  fun absenceTone_returnsNeutralForJustifiedAbsence() {
    val absence = AbsenceRecord(
      id = "1",
      date = "2026-03-20",
      type = AbsenceType.ABSENCE,
      justified = true,
    )
    assertEquals(ExpressiveTone.Neutral, absenceTone(absence))
  }

  @Test
  fun absenceTone_returnsDangerForUnjustifiedAbsence() {
    val absence = AbsenceRecord(
      id = "2",
      date = "2026-03-20",
      type = AbsenceType.ABSENCE,
      justified = false,
    )
    assertEquals(ExpressiveTone.Danger, absenceTone(absence))
  }

  @Test
  fun absenceTone_returnsWarningForUnjustifiedLateEntry() {
    val absence = AbsenceRecord(
      id = "3",
      date = "2026-03-21",
      type = AbsenceType.LATE,
      justified = false,
    )
    assertEquals(ExpressiveTone.Warning, absenceTone(absence))
  }

  @Test
  fun absenceTone_returnsWarningForUnjustifiedExit() {
    val absence = AbsenceRecord(
      id = "4",
      date = "2026-03-22",
      type = AbsenceType.EXIT,
      justified = false,
    )
    assertEquals(ExpressiveTone.Warning, absenceTone(absence))
  }
}
