package dev.antigravity.classevivaexpressive.core.domain.change

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {
  @Test
  fun anAppendedExerciseIsOneAddition() {
    val segments = TextDiff.words("Pag 1371 es 282, 283", "Pag 1371 es 282, 283, 311")

    assertEquals(
      listOf(
        DiffSegment(DiffKind.SAME, "Pag 1371 es 282, 283"),
        DiffSegment(DiffKind.ADDED, ", 311"),
      ),
      segments,
    )
  }

  @Test
  fun aReplacedWordIsRemovedThenAdded() {
    val segments = TextDiff.words("es 282, 283, 290", "es 282, 283, 311")

    assertEquals(
      listOf(
        DiffSegment(DiffKind.SAME, "es 282, 283, "),
        DiffSegment(DiffKind.REMOVED, "290"),
        DiffSegment(DiffKind.ADDED, "311"),
      ),
      segments,
    )
  }

  @Test
  fun changingOnlyTheCaseIsNotAChangeAndKeepsTheNewText() {
    val segments = TextDiff.words("STUDIARE PAG 40", "Studiare pag 40")

    assertEquals(listOf(DiffSegment(DiffKind.SAME, "Studiare pag 40")), segments)
  }

  @Test
  fun emptySidesAreAWholeAdditionOrRemoval() {
    assertEquals(listOf(DiffSegment(DiffKind.ADDED, "Portare il libro")), TextDiff.words("", "Portare il libro"))
    assertEquals(listOf(DiffSegment(DiffKind.REMOVED, "Portare il libro")), TextDiff.words("Portare il libro", " "))
  }

  @Test
  fun veryLongTextsFallBackToBeforeAndAfter() {
    val before = (1..500).joinToString(" ") { "a$it" }
    val after = (1..500).joinToString(" ") { "b$it" }

    val segments = TextDiff.words(before, after)

    assertEquals(listOf(DiffKind.REMOVED, DiffKind.ADDED), segments.map { it.kind })
  }

  @Test
  fun changedShareTellsARewriteFromATouchUp() {
    val touchUp = TextDiff.words("Pag 1371 es 282, 283", "Pag 1371 es 282, 283, 311")
    val rewrite = TextDiff.words("Studiare il capitolo sei", "Portare il compasso e la squadra")

    assertTrue(TextDiff.changedShare(touchUp) < TextDiff.RewriteShare)
    assertTrue(TextDiff.changedShare(rewrite) > TextDiff.RewriteShare)
  }
}
