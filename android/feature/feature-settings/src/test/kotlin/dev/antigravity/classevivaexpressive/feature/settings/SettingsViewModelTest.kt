package dev.antigravity.classevivaexpressive.feature.settings

import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.CapabilityResolver
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapability
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
  private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
  private val authRepository = mockk<AuthRepository>(relaxed = true)
  private val schoolYearRepository = mockk<SchoolYearRepository>(relaxed = true)
  private val capabilityResolver = mockk<CapabilityResolver>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel(): SettingsViewModel {
    every { settingsRepository.observeSettings() } returns flowOf(AppSettings())
    every { settingsRepository.observeNotificationRuntimeState() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState())
    every { authRepository.session } returns MutableStateFlow(null)
    every { schoolYearRepository.observeSelectedSchoolYear() } returns flowOf(SchoolYearRef(2025, 2026))
    every { schoolYearRepository.observeAvailableSchoolYears() } returns flowOf(listOf(SchoolYearRef(2025, 2026)))
    every { capabilityResolver.observeCapabilityMatrix() } returns flowOf(emptyList())
    return SettingsViewModel(settingsRepository, authRepository, schoolYearRepository, capabilityResolver)
  }

  // ─── Caricamento impostazioni ─────────────────────────────────────────────

  @Test
  fun settingsFromRepository_areExposedInState() = runTest {
    val settings = AppSettings(themeMode = ThemeMode.DARK, dynamicColorEnabled = false)
    every { settingsRepository.observeSettings() } returns flowOf(settings)
    every { settingsRepository.observeNotificationRuntimeState() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState())
    every { authRepository.session } returns MutableStateFlow(null)
    every { schoolYearRepository.observeSelectedSchoolYear() } returns flowOf(SchoolYearRef(2025, 2026))
    every { schoolYearRepository.observeAvailableSchoolYears() } returns flowOf(emptyList())
    every { capabilityResolver.observeCapabilityMatrix() } returns flowOf(emptyList())

    val vm = SettingsViewModel(settingsRepository, authRepository, schoolYearRepository, capabilityResolver)

    vm.state.test {
      val state = awaitItem()
      assertEquals(ThemeMode.DARK, state.settings.themeMode)
      assertFalse(state.settings.dynamicColorEnabled)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Tema ─────────────────────────────────────────────────────────────────

  @Test
  fun setThemeMode_callsRepository() = runTest {
    val vm = buildViewModel()
    vm.setThemeMode(ThemeMode.LIGHT)
    coVerify { settingsRepository.updateThemeMode(ThemeMode.LIGHT) }
  }

  @Test
  fun setAmoled_callsRepository() = runTest {
    val vm = buildViewModel()
    vm.setAmoled(true)
    coVerify { settingsRepository.setAmoledEnabled(true) }
  }

  @Test
  fun setDynamicColor_callsRepository() = runTest {
    val vm = buildViewModel()
    vm.setDynamicColor(false)
    coVerify { settingsRepository.setDynamicColorEnabled(false) }
  }

  // ─── Notifiche ────────────────────────────────────────────────────────────

  @Test
  fun setNotifications_callsRepository() = runTest {
    val vm = buildViewModel()
    vm.setNotifications(true)
    coVerify { settingsRepository.setNotificationsEnabled(true) }
  }

  @Test
  fun setPeriodicSync_callsRepository() = runTest {
    val vm = buildViewModel()
    vm.setPeriodicSync(false)
    coVerify { settingsRepository.setPeriodicSyncEnabled(false) }
  }

  @Test
  fun sendTestNotification_setsSuccessMessage() = runTest {
    coEvery { settingsRepository.sendTestNotification() } returns Result.success(Unit)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.sendTestNotification()
      val updated = awaitItem()
      assertEquals("Notifica di test inviata.", updated.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Anno scolastico ──────────────────────────────────────────────────────

  @Test
  fun selectSchoolYear_callsRepositoryAndSetsMessage() = runTest {
    val year = SchoolYearRef(2024, 2025)
    coEvery { schoolYearRepository.selectSchoolYear(year) } returns Unit

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.selectSchoolYear(year)
      val updated = awaitItem()
      assertTrue(updated.lastMessage?.contains(year.label) == true)
      cancelAndIgnoreRemainingEvents()
    }
    coVerify { schoolYearRepository.selectSchoolYear(year) }
  }

  // ─── Logout ───────────────────────────────────────────────────────────────

  @Test
  fun logout_callsAuthRepository() = runTest {
    val vm = buildViewModel()
    vm.logout()
    coVerify { authRepository.logout() }
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  fun refresh_callsNotificationStateRefresh() = runTest {
    val vm = buildViewModel()
    vm.refresh()
    coVerify { settingsRepository.refreshNotificationRuntimeState() }
  }

  @Test
  fun refresh_leavesRefreshingDisabledAfterCompletion() = runTest {
    val vm = buildViewModel()
    vm.refresh()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(vm.state.value.isRefreshing)
  }
}
