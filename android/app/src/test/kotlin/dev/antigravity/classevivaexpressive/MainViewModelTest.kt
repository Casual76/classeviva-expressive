package dev.antigravity.classevivaexpressive

import android.content.Context
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.data.sync.SyncWorkScheduler
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

  @Before fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(SyncWorkScheduler)
    every { SyncWorkScheduler.schedule(any()) } returns Unit
    every { SyncWorkScheduler.cancel(any()) } returns Unit
  }

  @After fun tearDown() {
    unmockkObject(SyncWorkScheduler)
    Dispatchers.resetMain()
  }

  @Test
  fun manualUpdateCheck_ignoresDismissedVersionFilter() = runTest {
    val update = update(version = "6.0.4")
    val appUpdateRepository = RecordingAppUpdateRepository(update)
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = FakeSettingsRepository(
        AppSettings(ignoredStableUpdateVersion = update.version),
      ),
      appUpdateRepository = appUpdateRepository,
      context = mockk<Context>(relaxed = true),
    )
    testScheduler.advanceUntilIdle()

    viewModel.uiState.test {
      awaitItem()

      viewModel.checkUpdate(showNoUpdateMessage = true)

      val state = awaitItem()
      assertEquals("", appUpdateRepository.ignoredVersions.last())
      assertSame(update, state.availableUpdate)
      assertEquals("Aggiornamento ${update.version} disponibile.", state.updateCheckMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun update(version: String) = AvailableAppUpdate(
    version = version,
    changelog = "Bugfix updater.",
    releaseTag = "stable-classeviva-expressive-v$version",
    apkAsset = "classeviva-expressive-$version.apk",
    downloadUrl = "https://example.test/classeviva-expressive-$version.apk",
    sizeBytes = 42L,
  )
}

private class FakeAuthRepository : AuthRepository {
  override val session = MutableStateFlow<UserSession?>(null)
  override suspend fun restore(): UserSession? = null
  override suspend fun login(username: String, password: String): Result<UserSession> {
    return Result.failure(UnsupportedOperationException())
  }
  override suspend fun logout() = Unit
}

private class FakeSettingsRepository(
  settings: AppSettings,
) : SettingsRepository {
  private val settings = MutableStateFlow(settings)

  override fun observeSettings(): Flow<AppSettings> = settings
  override fun observeNotificationRuntimeState(): Flow<NotificationRuntimeState> {
    return flowOf(NotificationRuntimeState())
  }
  override suspend fun updateThemeMode(mode: dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode) = Unit
  override suspend fun updateAccentMode(mode: dev.antigravity.classevivaexpressive.core.domain.model.AccentMode) = Unit
  override suspend fun updateCustomAccent(name: String) = Unit
  override suspend fun setDynamicColorEnabled(enabled: Boolean) = Unit
  override suspend fun setAmoledEnabled(enabled: Boolean) = Unit
  override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
  override suspend fun setPeriodicSyncEnabled(enabled: Boolean) = Unit
  override suspend fun updateNotificationPreferences(
    preferences: dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences,
  ) = Unit
  override suspend fun setNotificationCategoryEnabled(channelId: String, enabled: Boolean) = Unit
  override suspend fun refreshNotificationRuntimeState() = Unit
  override suspend fun sendTestNotification(): Result<Unit> = Result.success(Unit)
  override suspend fun sendTestNotificationForChannel(channelId: String): Result<Unit> = Result.success(Unit)
  override suspend fun updateGatewayBaseUrl(url: String) = Unit
  override suspend fun refreshLiveTimetable() = Unit
  override suspend fun ignoreStableUpdateVersion(version: String) {
    settings.value = settings.value.copy(ignoredStableUpdateVersion = version)
  }
}

private class RecordingAppUpdateRepository(
  private val update: AvailableAppUpdate?,
) : AppUpdateRepository {
  val ignoredVersions = mutableListOf<String>()

  override suspend fun checkForStableUpdate(
    currentVersionName: String,
    ignoredVersion: String,
  ): Result<AvailableAppUpdate?> {
    ignoredVersions += ignoredVersion
    return Result.success(update.takeUnless { it?.version == ignoredVersion })
  }

  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> {
    return flowOf(AppUpdateInstallState.Installed(update.apkAsset))
  }
}
