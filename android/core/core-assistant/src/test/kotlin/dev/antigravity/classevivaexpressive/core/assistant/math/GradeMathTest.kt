package dev.antigravity.classevivaexpressive.core.assistant.math

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMathTest {

  private fun grade(value: Double?, weight: Double? = null, date: String = "2026-03-01", label: String = value?.toString() ?: "N.C.") =
    Grade(id = "$value-$weight-$date", subject = "MATEMATICA", valueLabel = label, numericValue = value, date = date, type = "Scritto", weight = weight)

  @Test
  fun `la media semplice e' quella dell'app e ignora i voti senza valore`() {
    val grades = listOf(grade(6.0), grade(8.0), grade(null), grade(7.0))
    assertEquals(7.0, GradeMath.simpleAverage(grades)!!, 1e-9)
    assertNull(GradeMath.simpleAverage(listOf(grade(null))))
  }

  @Test
  fun `la ponderata usa il peso, peso zero non conta, percentuali diventano frazioni`() {
    val grades = listOf(grade(6.0, 1.0), grade(8.0, 0.5), grade(10.0, 0.0), grade(4.0, 50.0))
    // (6*1 + 8*0.5 + 4*0.5) / 2 = 12 / 2 = 6
    assertEquals(6.0, GradeMath.weightedAverage(grades)!!, 1e-9)
    val summary = GradeMath.summary(grades)
    assertEquals(4, summary.count)
    assertEquals(4, summary.counted)
    assertEquals(7.0, summary.simple!!, 1e-9)
    assertTrue(summary.weightedDiffers)
    assertEquals(1, summary.insufficient)
  }

  @Test
  fun `il voto che serve per arrivare a una media si calcola, e si dice quando non basta uno`() {
    val grades = listOf(grade(5.0), grade(6.0))
    // media 5.5; per arrivare a 6 con un terzo voto: 6*3 - 11 = 7
    assertEquals(7.0, GradeMath.neededForSimple(grades, 6.0)!!, 1e-9)
    // per arrivare a 8: 8*3 - 11 = 13 -> non basta un voto
    assertEquals(13.0, GradeMath.neededForSimple(grades, 8.0)!!, 1e-9)
    assertEquals(2, GradeMath.countNeeded(grades, 7.0, 9.0))
    assertNull(GradeMath.countNeeded(grades, 9.0, 8.0))
    assertNull(GradeMath.neededForSimple(listOf(grade(null)), 6.0))
    // ponderata: voti (6, peso 1), obiettivo 7 con prossimo di peso 2: (7*3 - 6)/2 = 7.5
    assertEquals(7.5, GradeMath.neededForWeighted(listOf(grade(6.0, 1.0)), 7.0, 2.0)!!, 1e-9)
  }

  @Test
  fun `i numeri si scrivono senza zeri inutili`() {
    assertEquals("7", GradeMath.format(7.0))
    assertEquals("7.5", GradeMath.format(7.5))
    assertEquals("6.33", GradeMath.format(6.3333))
    assertEquals("—", GradeMath.format(null))
  }
}
