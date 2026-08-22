package dev.antigravity.classevivaexpressive.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SchoolYearStoreTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun currentAndAvailableYears_areRecalculatedFromTheProvidedDate() = runTest {
    var today = LocalDate.of(2026, 8, 31)
    val store = SchoolYearStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "recalculated.preferences_pb") },
      ),
      todayProvider = { today },
      nowEpochMillisProvider = { 1L },
    )

    assertEquals(SchoolYearRef(2025, 2026), store.currentSchoolYearRef())
    assertEquals(
      listOf(SchoolYearRef(2026, 2027), SchoolYearRef(2025, 2026)),
      store.observeAvailableSchoolYears().first(),
    )

    today = LocalDate.of(2026, 9, 1)

    assertEquals(SchoolYearRef(2026, 2027), store.currentSchoolYearRef())
    assertEquals(
      listOf(SchoolYearRef(2026, 2027), SchoolYearRef(2025, 2026)),
      store.observeAvailableSchoolYears().first(),
    )
  }

  @Test
  fun legacySelectionTwoYearsBehind_isNormalisedToTheSupportedCurrentYear() = runTest {
    val store = SchoolYearStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "legacy-selection.preferences_pb") },
      ),
      todayProvider = { LocalDate.of(2026, 8, 22) },
      nowEpochMillisProvider = { 1L },
    )
    store.selectSchoolYear(SchoolYearRef(2024, 2025))

    assertEquals(SchoolYearRef(2025, 2026), store.observeSelectedSchoolYear().first())
    assertEquals(
      listOf(SchoolYearRef(2026, 2027), SchoolYearRef(2025, 2026)),
      store.observeAvailableSchoolYears().first(),
    )
  }

  @Test
  fun automaticFallback_isAtomicDurableAcknowledgedAndCannotRepeatFromTarget() = runTest {
    val requested = SchoolYearRef(2026, 2027)
    val fallback = SchoolYearRef(2025, 2026)
    val store = SchoolYearStore(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "fallback.preferences_pb") },
      ),
      todayProvider = { LocalDate.of(2026, 8, 22) },
      nowEpochMillisProvider = { 1234L },
    )
    store.selectSchoolYear(requested)

    val event = store.selectAutomaticFallback(requested)

    assertNotNull(event)
    assertEquals(requested, event?.requested)
    assertEquals(fallback, event?.selected)
    assertEquals(fallback, store.observeSelectedSchoolYear().first())
    assertNull(store.selectAutomaticFallback(fallback))
    assertEquals(event, store.observeFallbackEvents().first())

    store.acknowledgeFallbackEvent(event!!.id)

    assertNull(withTimeoutOrNull(100) { store.observeFallbackEvents().first() })
  }
}
