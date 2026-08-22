package dev.antigravity.classevivaexpressive.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun update_preservesIgnoredStableUpdateVersionInsideTransaction() = runTest {
    val store = SettingsStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "ignored-version.preferences_pb") },
      ),
    )

    store.writeSettings(
      AppSettings(
        ignoredStableUpdateVersion = "6.2.0",
      ),
    )
    store.update { current -> current.copy(themeMode = ThemeMode.DARK) }

    val persisted = store.settings.first()
    assertEquals(ThemeMode.DARK, persisted.themeMode)
    assertEquals("6.2.0", persisted.ignoredStableUpdateVersion)
  }

  @Test
  fun emptyStore_usesDomainCustomAccentDefault() = runTest {
    val store = SettingsStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "defaults.preferences_pb") },
      ),
    )

    assertEquals(AppSettings().customAccentName, store.settings.first().customAccentName)
  }

  @Test
  fun dynamicColorSelection_remainsCoherentForAFreshCollectorAfterAnotherWrite() = runTest {
    val store = SettingsStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "dynamic-color.preferences_pb") },
      ),
    )

    store.update { current ->
      current.copy(
        accentMode = AccentMode.DYNAMIC,
        dynamicColorEnabled = true,
      )
    }
    store.update { current -> current.copy(themeMode = ThemeMode.DARK) }

    // `settings` is cold: this is a new subscription, matching the collector restart that happens
    // when the app returns from the background.
    val resumed = store.settings.first()
    assertEquals(AccentMode.DYNAMIC, resumed.accentMode)
    assertTrue(resumed.dynamicColorEnabled)
    assertEquals(ThemeMode.DARK, resumed.themeMode)
  }
}
