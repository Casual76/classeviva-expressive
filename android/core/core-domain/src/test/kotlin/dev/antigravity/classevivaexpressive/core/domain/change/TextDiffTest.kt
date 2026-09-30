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
  fun anAppendedLinkIsOneAdditionEvenWhenItsDotsMatchTheOldText() {
    val segments = TextDiff.words(
      "UPLOAD ON CLASSROOM.",
      "UPLOAD ON CLASSROOM: https://classroom.google.com/w/NTQ3/t/all",
    )

    assertEquals(
      listOf(
        DiffSegment(DiffKind.SAME, "UPLOAD ON CLASSROOM"),
        DiffSegment(DiffKind.REMOVED, "."),
        DiffSegment(DiffKind.ADDED, ": https://classroom.google.com/w/NTQ3/t/all"),
      ),
      segments,
    )
  }

  @Test
  fun neighbouringReplacementsReadAsOneRemovalThenOneAddition() {
    val segments = TextDiff.words("es 290 291 per domani", "es 311 312 per domani")

    assertEquals(
      listOf(
        DiffSegment(DiffKind.SAME, "es "),
        DiffSegment(DiffKind.REMOVED, "290 291"),
        DiffSegment(DiffKind.ADDED, "311 312"),
        DiffSegment(DiffKind.SAME, " per domani"),
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
