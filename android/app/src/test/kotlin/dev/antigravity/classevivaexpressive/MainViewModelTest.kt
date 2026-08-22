package dev.antigravity.classevivaexpressive

import android.content.Context
import android.os.SystemClock
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.data.sync.SyncWorkScheduler
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidNotificationTone
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardRepository
import dev.antigravity.classevivaexpressive.core.domain.model.DashboardSnapshot
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearFallbackEvent
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearSelectionPolicy
import dev.antigravity.classevivaexpressive.core.domain.model.SettingsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile
import dev.antigravity.classevivaexpressive.core.domain.model.SyncState
import dev.antigravity.classevivaexpressive.core.domain.model.SyncStatus
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
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
    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } returns 1_000L
  }

  @After fun tearDown() {
    unmockkObject(SyncWorkScheduler)
    unmockkStatic(SystemClock::class)
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
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = FakeDashboardRepository(),
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

  @Test
  fun notificationPermissionResult_refreshesRuntimeStateAndLiveTimetableWhenGranted() = runTest {
    val settingsRepository = FakeSettingsRepository(AppSettings())
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(session()),
      settingsRepository = settingsRepository,
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = FakeDashboardRepository(),
      context = mockk<Context>(relaxed = true),
    )
    testScheduler.advanceUntilIdle()
    settingsRepository.resetCounters()

    viewModel.onNotificationPermissionResult(granted = true)
    testScheduler.advanceUntilIdle()

    assertEquals(1, settingsRepository.notificationRuntimeRefreshCount)
    assertEquals(1, settingsRepository.liveTimetableRefreshCount)
  }

  @Test
  fun notificationPermissionResult_refreshesRuntimeStateOnlyWhenDenied() = runTest {
    val settingsRepository = FakeSettingsRepository(AppSettings())
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = settingsRepository,
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = FakeDashboardRepository(),
      context = mockk<Context>(relaxed = true),
    )
    testScheduler.advanceUntilIdle()
    settingsRepository.resetCounters()

    viewModel.onNotificationPermissionResult(granted = false)
    testScheduler.advanceUntilIdle()

    assertEquals(1, settingsRepository.notificationRuntimeRefreshCount)
    assertEquals(0, settingsRepository.liveTimetableRefreshCount)
  }

  @Test
  fun schoolYearFallback_becomesGlobalNoticeAndCanBeAcknowledged() = runTest {
    val schoolYears = FakeSchoolYearRepository()
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = FakeSettingsRepository(AppSettings()),
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = schoolYears,
      dashboardRepository = FakeDashboardRepository(),
      context = mockk<Context>(relaxed = true),
    )
    val event = SchoolYearFallbackEvent(
      id = "school-year:2026-2027:2025-2026",
      requested = SchoolYearRef(2026, 2027),
      selected = SchoolYearRef(2025, 2026),
    )

    viewModel.inAppNotifications.test {
      schoolYears.emitFallback(event)
      val notice = awaitItem()

      assertEquals(true, notice.id.endsWith(event.id))
      assertEquals(true, notice.message.contains(event.requested.label))
      assertEquals(true, notice.message.contains(event.selected.label))

      viewModel.acknowledgeInAppNotification(notice.id)
      testScheduler.advanceUntilIdle()
      assertEquals(listOf(event.id), schoolYears.acknowledgedIds)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * A refresh that fails has to say so where the user is looking. Before this the whole report was
   * the colour of a glyph in the corner of the bar.
   */
  @Test
  fun failedSync_isAnnouncedWithItsOwnMessage() = runTest {
    val dashboard = FakeDashboardRepository()
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = FakeSettingsRepository(AppSettings()),
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = dashboard,
      context = mockk<Context>(relaxed = true),
    )

    viewModel.inAppNotifications.test {
      dashboard.publish(
        SyncStatus(
          state = SyncState.PARTIAL,
          message = "Aggiornamento incompleto: bacheca",
          failedSections = listOf("communications"),
        ),
      )

      val notice = awaitItem()
      assertEquals("Aggiornamento incompleto: bacheca", notice.message)
      assertEquals(FluidNotificationTone.Warning, notice.tone)

      // Nothing durable is behind it, so acknowledging must not ask the store to forget an event
      // it never recorded.
      viewModel.acknowledgeInAppNotification(notice.id)
      testScheduler.advanceUntilIdle()
      cancelAndIgnoreRemainingEvents()
    }
  }

  /** A successful sync is not news, and neither is the status the app happened to start with. */
  @Test
  fun successfulSync_saysNothing() = runTest {
    val dashboard = FakeDashboardRepository()
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = FakeSettingsRepository(AppSettings()),
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = dashboard,
      context = mockk<Context>(relaxed = true),
    )

    viewModel.inAppNotifications.test {
      dashboard.publish(SyncStatus(state = SyncState.SYNCING, message = "Sincronizzazione in corso"))
      dashboard.publish(
        SyncStatus(state = SyncState.IDLE, lastSuccessfulSyncEpochMillis = 1_000L),
      )
      testScheduler.advanceUntilIdle()

      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * Showing last year's noticeboard beside an empty Voti with nothing said about it is the app
   * looking broken in the one situation where it is doing exactly what the registro allows.
   */
  @Test
  fun choosingAnArchivedYear_saysWhatWillBeMissing() = runTest {
    val schoolYears = FakeSchoolYearRepository()
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = FakeSettingsRepository(AppSettings()),
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = schoolYears,
      dashboardRepository = FakeDashboardRepository(),
      context = mockk<Context>(relaxed = true),
    )
    val archived = SchoolYearRef.previousOf(SchoolYearSelectionPolicy.current(LocalDate.now()))

    viewModel.inAppNotifications.test {
      schoolYears.select(archived)

      val notice = awaitItem()
      assertEquals(true, notice.title.contains(archived.label))
      assertEquals(true, notice.message.contains("voti"))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun choosingTheCurrentYearAgain_saysNothing() = runTest {
    val current = SchoolYearSelectionPolicy.current(LocalDate.now())
    val schoolYears = FakeSchoolYearRepository(SchoolYearRef.previousOf(current))
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = FakeSettingsRepository(AppSettings()),
      appUpdateRepository = RecordingAppUpdateRepository(update = null),
      schoolYearRepository = schoolYears,
      dashboardRepository = FakeDashboardRepository(),
      context = mockk<Context>(relaxed = true),
    )

    viewModel.inAppNotifications.test {
      schoolYears.select(current)
      testScheduler.advanceUntilIdle()

      expectNoEvents()
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

  private fun session() = UserSession(
    token = "token",
    studentId = "student",
    username = "studente",
    profile = StudentProfile(name = "Studente"),
  )
}

private class FakeAuthRepository(
  initialSession: UserSession? = null,
) : AuthRepository {
  override val session = MutableStateFlow(initialSession)
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
  var notificationRuntimeRefreshCount = 0
    private set
  var liveTimetableRefreshCount = 0
    private set

  fun resetCounters() {
    notificationRuntimeRefreshCount = 0
    liveTimetableRefreshCount = 0
  }

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
  override suspend fun refreshNotificationRuntimeState() {
    notificationRuntimeRefreshCount += 1
  }
  override suspend fun sendTestNotification(): Result<Unit> = Result.success(Unit)
  override suspend fun sendTestNotificationForChannel(channelId: String): Result<Unit> = Result.success(Unit)
  override suspend fun updateGatewayBaseUrl(url: String) = Unit
  override suspend fun refreshLiveTimetable() {
    liveTimetableRefreshCount += 1
  }
  override suspend fun ignoreStableUpdateVersion(version: String) {
    settings.value = settings.value.copy(ignoredStableUpdateVersion = version)
  }
}

private class FakeSchoolYearRepository(
  initialYear: SchoolYearRef = SchoolYearSelectionPolicy.current(LocalDate.now()),
) : SchoolYearRepository {
  private val fallbacks = MutableSharedFlow<SchoolYearFallbackEvent>(extraBufferCapacity = 1)
  private val selected = MutableStateFlow(initialYear)
  val acknowledgedIds = mutableListOf<String>()

  suspend fun emitFallback(event: SchoolYearFallbackEvent) {
    fallbacks.emit(event)
  }

  fun select(year: SchoolYearRef) {
    selected.value = year
  }

  override fun observeSelectedSchoolYear(): Flow<SchoolYearRef> = selected

  override fun observeAvailableSchoolYears(): Flow<List<SchoolYearRef>> =
    flowOf(listOf(SchoolYearRef(2025, 2026)))

  override fun observeFallbackEvents(): Flow<SchoolYearFallbackEvent> = fallbacks

  override suspend fun selectSchoolYear(year: SchoolYearRef) = Unit

  override suspend fun selectAutomaticFallback(requested: SchoolYearRef): SchoolYearFallbackEvent? = null

  override suspend fun acknowledgeFallbackEvent(id: String) {
    acknowledgedIds += id
  }
}

private class FakeDashboardRepository : DashboardRepository {
  private val snapshots = MutableStateFlow(DashboardSnapshot())

  fun publish(status: SyncStatus) {
    snapshots.value = snapshots.value.copy(syncStatus = status)
  }

  override fun observeDashboard(): Flow<DashboardSnapshot> = snapshots

  override suspend fun refreshDashboard(force: Boolean): Result<DashboardSnapshot> =
    Result.success(snapshots.value)
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
