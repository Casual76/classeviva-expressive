package dev.antigravity.classevivaexpressive.core.data.change

import dev.antigravity.classevivaexpressive.core.database.database.AgendaItemEntity
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItem
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaItemVersion
import dev.antigravity.classevivaexpressive.core.domain.model.Grade
import dev.antigravity.classevivaexpressive.core.domain.model.GradeVersion
import kotlin.math.abs

private const val NumberTolerance = 0.0001
private val GenericGradeTypes = setOf("valutazione", "voto")

internal fun GradeEntity.hasMeaningfulChangeComparedTo(
  grade: Grade,
  includeOneSidedText: Boolean = false,
): Boolean = meaningfulChangeReasonsComparedTo(grade, includeOneSidedText).isNotEmpty()

internal fun GradeEntity.hasMeaningfulChangeComparedTo(
  version: GradeVersion,
  includeOneSidedText: Boolean = false,
): Boolean = meaningfulChangeReasonsComparedTo(version, includeOneSidedText).isNotEmpty()

internal fun GradeEntity.meaningfulChangeReasonsComparedTo(
  grade: Grade,
  includeOneSidedText: Boolean = false,
): List<String> = buildList {
  if (gradeValueChanged(numericValue, valueLabel, grade.numericValue, grade.valueLabel)) add("value")
  if (requiredTextChanged(date, grade.date)) add("date")
  if (comparableTextChanged(subject, grade.subject)) add("subject")
  if (coreTextChanged(description, grade.description, includeOneSidedText)) add("description")
  if (coreTextChanged(notes, grade.notes, includeOneSidedText)) add("notes")
  if (comparableTextChanged(significantGradeType(type), significantGradeType(grade.type))) add("type")
  if (comparableNumberChanged(weight, grade.weight)) add("weight")
}

internal fun GradeEntity.meaningfulChangeReasonsComparedTo(
  version: GradeVersion,
  includeOneSidedText: Boolean = false,
): List<String> = buildList {
  if (gradeValueChanged(numericValue, valueLabel, version.numericValue, version.valueLabel)) add("value")
  if (requiredTextChanged(date, version.date)) add("date")
  if (comparableTextChanged(subject, version.subject)) add("subject")
  if (coreTextChanged(description, version.description, includeOneSidedText)) add("description")
  if (coreTextChanged(notes, version.notes, includeOneSidedText)) add("notes")
  if (comparableTextChanged(significantGradeType(type), significantGradeType(version.type))) add("type")
  if (comparableNumberChanged(weight, version.weight)) add("weight")
}

internal fun AgendaItemEntity.hasMeaningfulChangeComparedTo(
  item: AgendaItem,
  includeOneSidedText: Boolean = false,
): Boolean = meaningfulChangeReasonsComparedTo(item, includeOneSidedText).isNotEmpty()

internal fun AgendaItemEntity.hasMeaningfulChangeComparedTo(
  version: AgendaItemVersion,
  includeOneSidedText: Boolean = false,
): Boolean = meaningfulChangeReasonsComparedTo(version, includeOneSidedText).isNotEmpty()

internal fun AgendaItemEntity.meaningfulChangeReasonsComparedTo(
  item: AgendaItem,
  includeOneSidedText: Boolean = false,
): List<String> = buildList {
  if (requiredTextChanged(date, item.date)) add("date")
  if (category != item.category.name) add("category")
  if (comparableTextChanged(time, item.time)) add("time")
  if (comparableTextChanged(agendaSubjectLabel(), item.subject ?: item.subtitle)) add("subject")
  if (coreTextChanged(title, item.title, includeOneSidedText)) add("title")
  if (coreTextChanged(detail, item.detail, includeOneSidedText)) add("detail")
}

internal fun AgendaItemEntity.meaningfulChangeReasonsComparedTo(
  version: AgendaItemVersion,
  includeOneSidedText: Boolean = false,
): List<String> = buildList {
  if (requiredTextChanged(date, version.date)) add("date")
  if (category != version.category.name) add("category")
  if (comparableTextChanged(time, version.time)) add("time")
  if (comparableTextChanged(agendaSubjectLabel(), version.subject ?: version.subtitle)) add("subject")
  if (coreTextChanged(title, version.title, includeOneSidedText)) add("title")
  if (coreTextChanged(detail, version.detail, includeOneSidedText)) add("detail")
}

private fun AgendaItemEntity.agendaSubjectLabel(): String {
  return subject?.takeIf(String::isNotBlank) ?: subtitle
}

private fun gradeValueChanged(
  firstNumber: Double?,
  firstLabel: String?,
  secondNumber: Double?,
  secondLabel: String?,
): Boolean {
  if (firstNumber != null && secondNumber != null) {
    return abs(firstNumber - secondNumber) > NumberTolerance
  }
  val parsedFirst = firstNumber ?: parseGradeValue(firstLabel)
  val parsedSecond = secondNumber ?: parseGradeValue(secondLabel)
  if (parsedFirst != null && parsedSecond != null) {
    return abs(parsedFirst - parsedSecond) > NumberTolerance
  }
  return comparableTextChanged(firstLabel, secondLabel)
}

private fun comparableNumberChanged(first: Double?, second: Double?): Boolean {
  return first != null && second != null && abs(first - second) > NumberTolerance
}

private fun requiredTextChanged(first: String?, second: String?): Boolean {
  val normalizedFirst = normalizedText(first)
  val normalizedSecond = normalizedText(second)
  return normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
}

private fun comparableTextChanged(first: String?, second: String?): Boolean {
  val normalizedFirst = normalizedText(first)
  val normalizedSecond = normalizedText(second)
  return normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
}

private fun coreTextChanged(first: String?, second: String?, includeOneSidedText: Boolean): Boolean {
  val normalizedFirst = normalizedText(first)
  val normalizedSecond = normalizedText(second)
  return if (includeOneSidedText) {
    normalizedFirst != normalizedSecond
  } else {
    normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
  }
}

private fun significantGradeType(type: String?): String? {
  val normalized = normalizedText(type)
  return normalized.takeUnless { it.isBlank() || it in GenericGradeTypes }
}

private fun normalizedText(value: String?): String {
  return value.orEmpty()
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
}

private fun parseGradeValue(label: String?): Double? {
  return normalizedText(label)
    .replace(',', '.')
    .toDoubleOrNull()
}
