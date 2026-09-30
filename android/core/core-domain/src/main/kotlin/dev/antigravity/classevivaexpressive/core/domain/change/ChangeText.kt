package dev.antigravity.classevivaexpressive.core.domain.change

import kotlin.math.abs

/**
 * Quando due valori del registro sono "la stessa cosa".
 *
 * E' la regola unica per chi rileva una modifica durante la sincronizzazione e per chi la mostra:
 * erano due copie, e una cronologia che mostra una versione che la sincronizzazione non avrebbe
 * registrato — o il contrario — e' una cronologia di cui non ci si fida.
 */
object ChangeText {
  private const val NumberTolerance = 0.0001
  private val GenericGradeTypes = setOf("valutazione", "voto")

  fun normalized(value: String?): String {
    return value.orEmpty()
      .trim()
      .lowercase()
      .replace(Regex("\\s+"), " ")
  }

  /**
   * Cambiato solo se c'e' da tutte e due le parti: un campo che una lettura non porta non e' stato
   * cancellato dal docente, e' la risposta del registro che quella volta era piu' povera.
   */
  fun comparableChanged(first: String?, second: String?): Boolean {
    val normalizedFirst = normalized(first)
    val normalizedSecond = normalized(second)
    return normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
  }

  /**
   * Il testo che il docente scrive. Da una parte sola conta solo con [includeOneSidedText]: dopo la
   * prima volta che l'app ha visto l'impegno, un dettaglio che compare o sparisce e' una modifica.
   */
  fun coreChanged(first: String?, second: String?, includeOneSidedText: Boolean): Boolean {
    val normalizedFirst = normalized(first)
    val normalizedSecond = normalized(second)
    return if (includeOneSidedText) {
      normalizedFirst != normalizedSecond
    } else {
      normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() && normalizedFirst != normalizedSecond
    }
  }

  fun numberChanged(first: Double?, second: Double?): Boolean {
    return first != null && second != null && abs(first - second) > NumberTolerance
  }

  /** "Voto" e "Valutazione" non dicono che tipo di prova e': cambiare dall'uno all'altro non conta. */
  fun significantGradeType(type: String?): String? {
    val normalized = normalized(type)
    return normalized.takeUnless { it.isBlank() || it in GenericGradeTypes }
  }

  fun gradeValueChanged(
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
    return comparableChanged(firstLabel, secondLabel)
  }

  private fun parseGradeValue(label: String?): Double? {
    return normalized(label)
      .replace(',', '.')
      .toDoubleOrNull()
  }
}
