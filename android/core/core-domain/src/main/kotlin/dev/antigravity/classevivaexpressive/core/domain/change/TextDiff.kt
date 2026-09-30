package dev.antigravity.classevivaexpressive.core.domain.change

enum class DiffKind { SAME, ADDED, REMOVED }

data class DiffSegment(
  val kind: DiffKind,
  val text: String,
)

/**
 * Cosa e' cambiato in un testo, parola per parola.
 *
 * Le parole si confrontano senza badare alle maiuscole — il registro scrive spesso tutto in
 * maiuscolo, e rimettere in minuscolo un titolo non e' una modifica che interessa — e la
 * punteggiatura e' un pezzo a se': "283" diventato "283, 311" e' un'aggiunta, non una parola tolta
 * e due messe. Il testo che si mostra per le parti uguali e' quello nuovo.
 */
object TextDiff {
  /**
   * Oltre questa lunghezza (in pezzi: parole, spazi, punteggiatura) il confronto costerebbe troppo
   * per un telefono, e un testo cosi' lungo e cambiato si legge meglio come prima e dopo.
   */
  private const val MaxTokens = 700

  /** Oltre questa quota di testo cambiato i segni si leggono peggio di due blocchi interi. */
  const val RewriteShare = 0.6f

  private val TokenPattern = Regex("[\\p{L}\\p{N}]+|[^\\p{L}\\p{N}\\s]+|\\s+")

  fun words(before: String, after: String): List<DiffSegment> {
    if (before == after) return if (after.isEmpty()) emptyList() else listOf(DiffSegment(DiffKind.SAME, after))
    if (before.isBlank()) return listOf(DiffSegment(DiffKind.ADDED, after))
    if (after.isBlank()) return listOf(DiffSegment(DiffKind.REMOVED, before))

    val old = tokens(before)
    val new = tokens(after)
    if (old.size > MaxTokens || new.size > MaxTokens) {
      return listOf(DiffSegment(DiffKind.REMOVED, before), DiffSegment(DiffKind.ADDED, after))
    }

    // Lunghezza della sottosequenza comune piu' lunga, dal fondo: cosi' la ricostruzione va avanti.
    val lcs = Array(old.size + 1) { IntArray(new.size + 1) }
    for (i in old.indices.reversed()) {
      for (j in new.indices.reversed()) {
        lcs[i][j] = if (same(old[i], new[j])) {
          lcs[i + 1][j + 1] + 1
        } else {
          maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
      }
    }

    val segments = mutableListOf<DiffSegment>()
    fun push(kind: DiffKind, text: String) {
      val last = segments.lastOrNull()
      if (last != null && last.kind == kind) {
        segments[segments.lastIndex] = last.copy(text = last.text + text)
      } else {
        segments += DiffSegment(kind, text)
      }
    }
    var i = 0
    var j = 0
    while (i < old.size && j < new.size) {
      when {
        same(old[i], new[j]) -> {
          push(DiffKind.SAME, new[j])
          i++
          j++
        }
        lcs[i + 1][j] >= lcs[i][j + 1] -> push(DiffKind.REMOVED, old[i++])
        else -> push(DiffKind.ADDED, new[j++])
      }
    }
    while (i < old.size) push(DiffKind.REMOVED, old[i++])
    while (j < new.size) push(DiffKind.ADDED, new[j++])
    return segments
  }

  /** Quanto del testo e' cambiato, da 0 a 1, contando i caratteri tolti e aggiunti. */
  fun changedShare(segments: List<DiffSegment>): Float {
    val total = segments.sumOf { it.text.length }
    if (total == 0) return 0f
    val changed = segments.filter { it.kind != DiffKind.SAME }.sumOf { it.text.length }
    return changed.toFloat() / total
  }

  private fun tokens(text: String): List<String> = TokenPattern.findAll(text).map { it.value }.toList()

  private fun same(first: String, second: String): Boolean {
    if (first.isBlank() && second.isBlank()) return true
    return first.equals(second, ignoreCase = true)
  }
}
