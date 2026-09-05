package dev.antigravity.classevivaexpressive.core.assistant.tools

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSupportTest {

  private val today = LocalDate.of(2026, 9, 5) // sabato

  @Test
  fun `le date si leggono in ogni forma che il modello usa`() {
    assertEquals(today, Dates.parse("oggi", today))
    assertEquals(today.plusDays(1), Dates.parse("domani", today))
    assertEquals(today.minusDays(1), Dates.parse("ieri", today))
    assertEquals(LocalDate.of(2026, 9, 12), Dates.parse("2026-09-12", today))
    assertEquals(LocalDate.of(2026, 9, 12), Dates.parse("12/09/2026", today))
    // lunedi' prossimo da un sabato: il 7
    assertEquals(LocalDate.of(2026, 9, 7), Dates.parse("lunedì", today))
    assertEquals(LocalDate.of(2026, 9, 7), Dates.parse("lunedi prossimo", today))
    // sabato: oggi stesso; "sabato prossimo" la settimana dopo
    assertEquals(today, Dates.parse("sabato", today))
    assertEquals(today.plusWeeks(1), Dates.parse("sabato prossimo", today))
    assertEquals(LocalDate.of(2026, 9, 4), Dates.parse("venerdi scorso", today))
    assertNull(Dates.parse("boh", today))
    assertNull(Dates.parse(null, today))
  }

  @Test
  fun `l'intervallo ha un default e mette in ordine gli estremi`() {
    val range = Dates.range(null, null, today, defaultDays = 7)
    assertEquals(today, range.start)
    assertEquals(today.plusDays(7), range.endInclusive)
    val swapped = Dates.range("2026-09-20", "2026-09-10", today, defaultDays = 7)
    assertEquals(LocalDate.of(2026, 9, 10), swapped.start)
    assertEquals(LocalDate.of(2026, 9, 20), swapped.endInclusive)
  }

  @Test
  fun `le etichette portano il giorno della settimana`() {
    assertEquals("2026-09-05 (sab)", Dates.label(today))
    assertEquals("2026-09-05 (sab)", Dates.label("2026-09-05T10:00:00"))
    assertEquals("non una data", Dates.label("non una data"))
  }

  @Test
  fun `le materie si trovano da come le dice lo studente`() {
    val subjects = listOf("MATEMATICA", "LINGUA E CULTURA INGLESE", "SCIENZE MOTORIE E SPORTIVE", "STORIA", "FISICA")
    assertEquals("MATEMATICA", Subjects.match("mate", subjects))
    assertEquals("MATEMATICA", Subjects.match("Matematica", subjects))
    assertEquals("LINGUA E CULTURA INGLESE", Subjects.match("inglese", subjects))
    assertEquals("SCIENZE MOTORIE E SPORTIVE", Subjects.match("ginnastica", subjects))
    assertEquals("SCIENZE MOTORIE E SPORTIVE", Subjects.match("scienze motorie", subjects))
    assertEquals("FISICA", Subjects.match("fis", subjects))
    assertNull(Subjects.match("chimica", subjects))
    assertNull(Subjects.match("", subjects))
  }

  @Test
  fun `il confronto fra testi ignora accenti e maiuscole`() {
    assertTrue(Text.matches("gita", "Circolare n. 12 - GITA a Firenze"))
    assertTrue(Text.matches("assemblea istituto", "Assemblea d'Istituto del 5 marzo"))
    assertTrue(!Text.matches("sciopero", "Circolare gita"))
    assertEquals("perche", Text.normalize("Perché"))
  }

  @Test
  fun `il peso del registro si legge in tutte le sue forme`() {
    fun g(w: Double?) = dev.antigravity.classevivaexpressive.core.domain.model.Grade("i", "S", "7", 7.0, date = "2026-01-01", type = "S", weight = w)
    assertEquals(1.0, GradeWeights.effective(g(null)), 1e-9)
    assertEquals(0.5, GradeWeights.effective(g(0.5)), 1e-9)
    assertEquals(0.0, GradeWeights.effective(g(0.0)), 1e-9)
    assertEquals(0.5, GradeWeights.effective(g(50.0)), 1e-9)
    assertEquals("0.5", GradeWeights.format(0.5))
    assertEquals("1", GradeWeights.format(1.0))
  }
}
