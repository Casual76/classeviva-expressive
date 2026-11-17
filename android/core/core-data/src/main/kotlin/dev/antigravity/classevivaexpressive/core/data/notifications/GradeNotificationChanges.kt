package dev.antigravity.classevivaexpressive.core.data.notifications

import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import kotlin.math.abs

internal data class GradeNotificationChanges(val newItems: List<Grade>, val updatedItems: List<Grade>)

internal fun gradeNotificationChanges(before: List<Grade>, after: List<Grade>): GradeNotificationChanges {
  val known = before.associateBy(Grade::id)
  return GradeNotificationChanges(
    newItems = after.filter { it.id !in known },
    updatedItems = after.filter { grade ->
      known[grade.id]?.let { grade.hasMeaningfulNotificationChangeComparedTo(it) } == true
    },
  )
}

private fun Grade.hasMeaningfulNotificationChangeComparedTo(previous: Grade): Boolean {
  if (gradeValueChanged(previous.numericValue, previous.valueLabel, numericValue, valueLabel)) return true
  if (requiredTextChanged(previous.date, date)) return true
  if (comparableTextChanged(previous.subject, subject)) return true
  if (comparableTextChanged(significantGradeType(previous.type), significantGradeType(type))) return true
  if (comparableNumberChanged(previous.weight, weight)) return true
  if (coreTextChanged(previous.description, description)) return true
  if (coreTextChanged(previous.notes, notes)) return true
  return false
}

private fun gradeValueChanged(
  firstNumber: Double?,
  firstLabel: String?,
  secondNumber: Double?,
  secondLabel: String?,
): Boolean {
  if (firstNumber != null && secondNumber != null) {
    return abs(firstNumber - secondNumber) > 0.0001
  }
  val parsedFirst = firstNumber ?: parseGradeValue(firstLabel)
  val parsedSecond = secondNumber ?: parseGradeValue(secondLabel)
  if (parsedFirst != null && parsedSecond != null) {
    return abs(parsedFirst - parsedSecond) > 0.0001
  }
  return comparableTextChanged(firstLabel, secondLabel)
}

private fun comparableNumberChanged(first: Double?, second: Double?): Boolean {
  return first != null && second != null && abs(first - second) > 0.0001
}

private fun requiredTextChanged(first: String?, second: String?): Boolean {
  val normalizedFirst = normalizedNotificationText(first)
  val normalizedSecond = normalizedNotificationText(second)
  return normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
}

private fun comparableTextChanged(first: String?, second: String?): Boolean {
  val normalizedFirst = normalizedNotificationText(first)
  val normalizedSecond = normalizedNotificationText(second)
  return normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
}

private fun coreTextChanged(first: String?, second: String?): Boolean {
  return normalizedNotificationText(first) != normalizedNotificationText(second)
}

private fun significantGradeType(type: String?): String? {
  val normalized = normalizedNotificationText(type)
  return normalized.takeUnless { it.isBlank() || it == "valutazione" || it == "voto" }
}

private fun normalizedNotificationText(value: String?): String {
  return value.orEmpty()
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
}

private fun parseGradeValue(label: String?): Double? {
  return normalizedNotificationText(label)
    .replace(',', '.')
    .toDoubleOrNull()
}
