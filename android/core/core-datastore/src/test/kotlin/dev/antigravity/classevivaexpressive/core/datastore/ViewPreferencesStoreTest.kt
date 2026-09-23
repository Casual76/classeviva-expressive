package dev.antigravity.classevivaexpressive.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.classevivaexpressive.core.domain.model.AgendaViewMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewPreferencesStoreTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun agenda_startsOnTheMonth_andRemembersTheWeek() = runTest {
    val store = ViewPreferencesStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "view.preferences_pb") },
      ),
    )

    assertEquals(AgendaViewMode.MONTH, store.observeAgendaViewMode().first())
    store.setAgendaViewMode(AgendaViewMode.WEEK)
    // Un collettore nuovo, come alla riapertura dell'app.
    assertEquals(AgendaViewMode.WEEK, store.observeAgendaViewMode().first())
  }
}
