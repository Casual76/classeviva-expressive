package dev.antigravity.classevivaexpressive.feature.widgets

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.datastore.SecureSessionPersistence
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.StoredCredentials
import dev.antigravity.classevivaexpressive.core.domain.model.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Exercises the same combined flow collected throughout a live Glance composition. */
class WidgetAccountContentTest {
  private val today = LocalDate.parse("2026-11-17")
  private val session = UserSession("token", "student-1", "student", StudentProfile(id = "student-1"))
  private val store = SessionStore(object : SecureSessionPersistence {
    private var saved: String? = null
    override fun readSnapshot(): String? = saved
    override fun readLegacySession(): String? = null
    override fun readLegacyCredentials(): StoredCredentials? = null
    override fun commit(snapshot: String?): Boolean { saved = snapshot; return true }
  }).also { it.writeSession(session) }
  private val auth = mockk<AuthRepository>().also {
    every { it.session } returns store.session
    every { it.dataEpoch() } answers { store.accountGuard.capture().epoch }
    every { it.observeDataEpoch() } returns store.dataEpoch
  }
  private val dashboard = mockk<DashboardRepository>()
  private val settings = mockk<SettingsRepository>()
  private val preferences = MutableStateFlow(SchoolWidgetPreferences())
  private val appearances = MutableStateFlow(AppSettings())
  private val dates = MutableStateFlow(today)
  private val snapshots = MutableStateFlow(snapshot("Prima", today.plusDays(1)))

  init {
    every { dashboard.observeDashboard() } returns snapshots
    every { settings.observeSettings() } returns appearances
  }

  private fun presentations() = observeWidgetPresentation(auth, dashboard, settings, preferences, dates)

  @Test
  fun oneActiveCollectorReactsToDashboardPreferencesAppearanceAndCalendar() = runTest {
    presentations().test {
      assertEquals("Prima", awaitWhere { it.content.loaded }.model(auth).upcoming.single().title)
      snapshots.value = snapshot("Aggiornata", today.plusDays(1))
      assertEquals("Aggiornata", awaitWhere { it.content.snapshot.upcomingItems.firstOrNull()?.title == "Aggiornata" }
        .model(auth).upcoming.single().title)

      preferences.value = preferences.value.copy(refreshing = true)
      assertTrue(awaitWhere { it.preferences.refreshing }.model(auth).refreshing)
      preferences.value = preferences.value.copy(refreshing = false, privacyMode = WidgetPrivacyMode.DISCREET)
      val discreet = awaitWhere { !it.preferences.refreshing && it.preferences.privacyMode == WidgetPrivacyMode.DISCREET }
      assertFalse(discreet.model(auth).refreshing)
      assertEquals("Verifica in arrivo", discreet.model(auth).upcoming.single().title)

      appearances.value = appearances.value.copy(themeMode = ThemeMode.DARK)
      assertEquals(ThemeMode.DARK, awaitWhere { it.settings.themeMode == ThemeMode.DARK }.settings.themeMode)
      dates.value = today.plusDays(1)
      assertEquals("Oggi", awaitWhere { it.today == today.plusDays(1) }.model(auth).upcoming.single().dateLabel)
      verify(exactly = 1) { dashboard.observeDashboard() }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun sameStudentLogoutReloginClearsTheOldGenerationBeforeSuspendedNewRead() = runTest {
    val enteredNewRead = CompletableDeferred<Unit>()
    val releaseNewRead = CompletableDeferred<Unit>()
    var collections = 0
    every { dashboard.observeDashboard() } answers {
      flow {
        if (collections++ == 0) emit(snapshot("Account precedente", today)) else {
          enteredNewRead.complete(Unit)
          releaseNewRead.await()
          emit(snapshot("Nuovo accesso", today))
        }
      }
    }
    presentations().test {
      val original = awaitWhere { it.content.loaded }
      assertEquals("Account precedente", original.model(auth).upcoming.single().title)
      // No suspension between these writes: the public session can conflate to the same value.
      store.clear()
      store.writeSession(session)
      assertEquals(WidgetStatus.LOGGED_OUT, original.model(auth).status)
      enteredNewRead.await()
      val blank = awaitWhere { it.content.accountEpoch == store.dataEpoch.value && !it.content.loaded }
      assertTrue(blank.model(auth).upcoming.isEmpty())
      releaseNewRead.complete(Unit)
      val replacement = awaitWhere { it.content.loaded && it.content.accountEpoch == store.dataEpoch.value }
      assertEquals("Nuovo accesso", replacement.model(auth).upcoming.single().title)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun logoutImmediatelyEmitsLoggedOutContentWithoutDashboardOrNetworkRefresh() = runTest {
    presentations().test {
      awaitWhere { it.content.loaded }
      store.clear()
      val loggedOut = awaitWhere { !it.content.hasSession }
      assertEquals(WidgetStatus.LOGGED_OUT, loggedOut.model(auth).status)
      assertTrue(loggedOut.model(auth).upcoming.isEmpty())
      verify(exactly = 1) { dashboard.observeDashboard() }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun transparentTokenRotationKeepsTheActiveDataCollection() = runTest {
    presentations().test {
      val original = awaitWhere { it.content.loaded }
      store.writeSession(session.copy(token = "renewed-token"))
      assertEquals("Prima", original.model(auth).upcoming.single().title)
      verify(exactly = 1) { dashboard.observeDashboard() }
      cancelAndIgnoreRemainingEvents()
    }
  }

  private suspend fun ReceiveTurbine<WidgetPresentation>.awaitWhere(predicate: (WidgetPresentation) -> Boolean): WidgetPresentation {
    while (true) {
      val value = awaitItem()
      if (predicate(value)) return value
    }
  }

  private fun snapshot(title: String, date: LocalDate) = DashboardSnapshot(upcomingItems = listOf(
    AgendaItem(id = "private-event", title = title, subtitle = "", date = date.toString(), category = AgendaCategory.ASSESSMENT),
  ))
}
