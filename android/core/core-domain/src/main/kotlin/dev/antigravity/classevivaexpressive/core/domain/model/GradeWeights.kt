package dev.antigravity.classevivaexpressive.core.domain.model

/**
 * Effective register weight shared by grade summaries and assistant calculations. Classeviva
 * sources use both factors (1, 0.5) and percentages (100, 50). Missing weights count as one;
 * explicitly excluded or unusable weights do not contribute to an average.
 */
fun Grade.effectiveWeight(): Double {
  val value = weight ?: return 1.0
  return when {
    !value.isFinite() || value <= 0.0 -> 0.0
    value > 5.0 -> value / 100.0
    else -> value
  }
}

/** Weighted average within a subject; excluded, nonnumeric and invalid marks do not contribute. */
fun subjectGradeAverage(grades: List<Grade>): Double? {
  val numeric = grades.mapNotNull { grade ->
    val value = grade.numericValue?.takeIf(Double::isFinite) ?: return@mapNotNull null
    val weight = grade.effectiveWeight().takeIf { it > 0.0 } ?: return@mapNotNull null
    value to weight
  }
  val totalWeight = numeric.sumOf { it.second }
  if (totalWeight <= 0.0 || !totalWeight.isFinite()) return null
  return (numeric.sumOf { it.first * it.second } / totalWeight).takeIf(Double::isFinite)
}

/** Each subject contributes once, so subjects with more published marks do not dominate the total. */
fun overallGradeAverage(grades: List<Grade>): Double? {
  val subjectAverages = grades.groupBy(Grade::subject).values.mapNotNull(::subjectGradeAverage)
  return subjectAverages.takeIf { it.isNotEmpty() }?.average()?.takeIf(Double::isFinite)
}
