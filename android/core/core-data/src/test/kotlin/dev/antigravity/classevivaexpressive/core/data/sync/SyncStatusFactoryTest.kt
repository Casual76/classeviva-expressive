package dev.antigravity.classevivaexpressive.core.data.sync

import dev.antigravity.classevivaexpressive.core.data.repository.AgendaSection
import dev.antigravity.classevivaexpressive.core.data.repository.CommunicationsSection
import dev.antigravity.classevivaexpressive.core.data.repository.GradesSection
import dev.antigravity.classevivaexpressive.core.data.repository.LessonsSection
import dev.antigravity.classevivaexpressive.core.data.repository.SchoolbooksSection
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusFactoryTest {

  private val previous = SyncStatus(lastSuccessfulSyncEpochMillis = 100L)

  @Test
  fun completed_withNoFailures_isIdleAndSaysNothing() {
    val status = SyncStatusFactory.completed(
      failures = emptyMap(),
      previous = previous,
      completedAtEpochMillis = 500L,
    )

    assertEquals(SyncState.IDLE, status.state)
    assertEquals(500L, status.lastSuccessfulSyncEpochMillis)
    assertEquals(null, status.message)
  }

  /**
   * One thing going wrong usually takes several sections down with it. Five copies of the same
   * sentence are not five facts, and the section list on its own was not a fact at all: it named
   * what did not arrive and never what stopped it.
   */
  @Test
  fun completed_saysTheReasonOnceAndListsWhatItTookDown() {
    val status = SyncStatusFactory.completed(
      failures = mapOf(
        GradesSection to "Sessione scaduta.",
        LessonsSection to "Sessione scaduta.",
        AgendaSection to "Sessione scaduta.",
      ),
      previous = previous,
      completedAtEpochMillis = 500L,
    )

    assertEquals(SyncState.PARTIAL, status.state)
    assertEquals("Sessione scaduta: voti, lezioni, agenda.", status.message)
    assertEquals(listOf(GradesSection, LessonsSection, AgendaSection), status.failedSections)
    // A partial refresh is not a successful one: the age shown stays the age of the last good sync.
    assertEquals(100L, status.lastSuccessfulSyncEpochMillis)
  }

  @Test
  fun completed_keepsDistinctReasonsApart() {
    val status = SyncStatusFactory.completed(
      failures = mapOf(
        GradesSection to "Sessione scaduta.",
        CommunicationsSection to "Nessuna connessione a Internet.",
      ),
      previous = previous,
      completedAtEpochMillis = 500L,
    )

    assertEquals(
      "Sessione scaduta: voti. Nessuna connessione a Internet: bacheca.",
      status.message,
    )
  }

  /** A failure the network layer could not name still has to name the sections it applies to. */
  @Test
  fun completed_fallsBackWhenTheReasonIsTheGenericOne() {
    val status = SyncStatusFactory.completed(
      failures = mapOf(GradesSection to GenericSyncFailure),
      previous = previous,
      completedAtEpochMillis = 500L,
    )

    assertEquals("Aggiornamento non riuscito: voti.", status.message)
  }

  /**
   * A section the registro does not publish for the chosen year is not a failure: nothing went
   * wrong and asking again cannot help. Reporting it as one is what makes a working app look
   * broken, so it stays out of the failed list and out of the error state.
   */
  @Test
  fun completed_reportsYearUnavailabilityWithoutCallingItAFailure() {
    val status = SyncStatusFactory.completed(
      failures = emptyMap(),
      previous = previous,
      completedAtEpochMillis = 500L,
      unavailableSections = listOf(GradesSection, SchoolbooksSection),
    )

    assertEquals(SyncState.IDLE, status.state)
    assertEquals(emptyList<String>(), status.failedSections)
    assertTrue(status.message!!.contains("voti, libri"))
    assertTrue(status.message!!.contains("anno scolastico selezionato"))
  }

  @Test
  fun completed_reportsBothWhenSomethingFailedAndSomethingIsUnpublished() {
    val status = SyncStatusFactory.completed(
      failures = mapOf(CommunicationsSection to "Sessione scaduta."),
      previous = previous,
      completedAtEpochMillis = 500L,
      unavailableSections = listOf(GradesSection),
    )

    assertEquals(SyncState.PARTIAL, status.state)
    assertTrue(status.message!!.startsWith("Sessione scaduta: bacheca."))
    assertTrue(status.message!!.contains("Il registro non pubblica voti"))
  }
}
