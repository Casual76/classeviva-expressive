package dev.antigravity.classevivaexpressive.core.assistant.math

import dev.antigravity.classevivaexpressive.core.assistant.tools.GradeWeights
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import java.util.Locale

/**
 * La matematica dei voti, fatta qui e non dal modello: un modello che calcola una media a mano
 * sbaglia una volta su dieci, e una media sbagliata sul registro e' la cosa piu' grave che
 * l'assistente possa dire. La **media semplice** e' quella che l'app mostra (media dei valori
 * numerici, come in `buildStats`); la **ponderata** usa il peso del registro, e si riporta solo
 * quando e' diversa.
 */
object GradeMath {

  data class Summary(
    val count: Int,
    val counted: Int,
    val simple: Double?,
    val weighted: Double?,
    val min: Double?,
    val max: Double?,
    val last: Double?,
    val insufficient: Int,
  ) {
    /** Vero se la ponderata dice qualcosa di diverso dalla semplice (oltre il centesimo). */
    val weightedDiffers: Boolean get() = simple != null && weighted != null && kotlin.math.abs(simple - weighted) >= 0.005
  }

  fun simpleAverage(grades: List<Grade>): Double? {
    val values = grades.mapNotNull { it.numericValue }
    return values.takeIf { it.isNotEmpty() }?.average()
  }

  fun weightedAverage(grades: List<Grade>): Double? {
    var sum = 0.0
    var weights = 0.0
    grades.forEach { grade ->
      val value = grade.numericValue ?: return@forEach
      val w = GradeWeights.effective(grade)
      if (w <= 0.0) return@forEach
      sum += value * w
      weights += w
    }
    return if (weights > 0.0) sum / weights else null
  }

  fun summary(grades: List<Grade>): Summary {
    val sorted = grades.sortedBy { it.date }
    val values = sorted.mapNotNull { it.numericValue }
    return Summary(
      count = grades.size,
      counted = values.size,
      simple = values.takeIf { it.isNotEmpty() }?.average(),
      weighted = weightedAverage(grades),
      min = values.minOrNull(),
      max = values.maxOrNull(),
      last = sorted.lastOrNull { it.numericValue != null }?.numericValue,
      insufficient = values.count { it < 6.0 },
    )
  }

  /**
   * Il voto che serve al prossimo (di peso [nextWeight]) perche' la media semplice arrivi a
   * [target]. Null senza voti numerici. Puo' uscire dalla scala: sta a chi lo legge dire "non basta
   * un voto solo".
   */
  fun neededForSimple(grades: List<Grade>, target: Double): Double? {
    val values = grades.mapNotNull { it.numericValue }
    if (values.isEmpty()) return null
    return target * (values.size + 1) - values.sum()
  }

  fun neededForWeighted(grades: List<Grade>, target: Double, nextWeight: Double = 1.0): Double? {
    if (nextWeight <= 0.0) return null
    var sum = 0.0
    var weights = 0.0
    grades.forEach { grade ->
      val value = grade.numericValue ?: return@forEach
      val w = GradeWeights.effective(grade)
      if (w <= 0.0) return@forEach
      sum += value * w
      weights += w
    }
    if (weights <= 0.0) return null
    return (target * (weights + nextWeight) - sum) / nextWeight
  }

  /** Quanti voti da [value] servono, uno dopo l'altro, per portare la media semplice a [target]; null se non ci si arriva in 20. */
  fun countNeeded(grades: List<Grade>, target: Double, value: Double): Int? {
    val values = grades.mapNotNull { it.numericValue }
    if (values.isEmpty()) return null
    if (value < target) return null
    var sum = values.sum()
    var n = values.size
    for (k in 1..20) {
      sum += value
      n += 1
      if (sum / n >= target - 1e-9) return k
    }
    return null
  }

  fun format(value: Double?): String {
    if (value == null) return "—"
    val text = String.format(Locale.ROOT, "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
  }
}
