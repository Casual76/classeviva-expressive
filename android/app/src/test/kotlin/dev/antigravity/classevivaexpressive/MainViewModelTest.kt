package dev.antigravity.classevivaexpressive

import android.os.SystemClock
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.data.sync.AccountSyncScheduler
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.AuthRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.RestAuthenticationState
import dev.antigravity.classevivaexpressive.core.domain.model.PortalAuthenticationState
import dev.antigravity.classevivaexpressive.cie.CieLoginOutcome
import dev.antigravity.classevivaexpressive.cie.CieLoginResult
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
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.foundation.UpdateChannel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
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
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

  private lateinit var accountSyncScheduler: AccountSyncScheduler

  @Before fun setUp() {
    Dispatchers.setMain(testDispatcher)
    accountSyncScheduler = mockk(relaxed = true)
    coEvery { accountSyncScheduler.schedule(any(), any()) } returns Unit
    coEvery { accountSyncScheduler.cancel(any()) } returns Unit
    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } returns 1_000L
  }

  @After fun tearDown() {
    unmockkStatic(SystemClock::class)
    Dispatchers.resetMain()
  }

  @Test
  fun logoutCleanupFailureRemainsVisibleAfterTheAccountPageDisappears() = runTest {
    val auth = FakeAuthRepository(session())
    auth.logoutAction = {
      auth.session.value = null
      throw java.io.IOException("private-path-or-account-detail")
    }
    val viewModel = MainViewModel(auth, FakeSettingsRepository(AppSettings()), RecordingAppUpdater(),
      FakeSchoolYearRepository(), FakeDashboardRepository(), accountSyncScheduler)
    testScheduler.advanceUntilIdle()

    // No destination/notification collector exists while the account is being removed.
    viewModel.logout()
    testScheduler.advanceUntilIdle()
    assertEquals(null, auth.session.value)
    viewModel.inAppNotifications.test {
      val notice = awaitItem()
      assertEquals("Pulizia incompleta", notice.title)
      assertEquals("Sei uscito dall’account. La pulizia dei dati locali non è stata completata.", notice.message)
      assertEquals(FluidNotificationTone.Warning, notice.tone)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun browserCleanupWarningDoesNotTurnACompletedLoginIntoAuthenticationFailure() = runTest {
    val original = session()
    val auth = FakeAuthRepository(original)
    val viewModel = MainViewModel(auth, FakeSettingsRepository(AppSettings()), RecordingAppUpdater(),
      FakeSchoolYearRepository(), FakeDashboardRepository(), accountSyncScheduler)
    testScheduler.advanceUntilIdle()
    assertEquals(42L, viewModel.beginCieLogin())

    viewModel.completeCieLogin(CieLoginResult(CieLoginOutcome.Success, browserCleanupComplete = false))
    viewModel.inAppNotifications.test {
      assertEquals("Pulizia del browser incompleta", awaitItem().title)
      cancelAndIgnoreRemainingEvents()
    }
    val state = viewModel.uiState.filter { !it.isLoading }.first()
    assertSame(original, state.session)
    assertEquals(1, state.authenticationSuccessVersion)
    assertEquals(false, state.isAuthenticating)
    assertEquals(null, state.authError)
  }

  @Test
  fun failedDurableLogoutPreservesAccountAndReportsASafeError() = runTest {
    val original = session()
    val auth = FakeAuthRepository(original)
    auth.logoutAction = { throw java.io.IOException("sensitive-storage-detail") }
    val viewModel = MainViewModel(auth, FakeSettingsRepository(AppSettings()), RecordingAppUpdater(),
      FakeSchoolYearRepository(), FakeDashboardRepository(), accountSyncScheduler)

    viewModel.inAppNotifications.test {
      viewModel.logout()
      val notice = awaitItem()
      assertSame(original, auth.session.value)
      assertEquals("Uscita non completata", notice.title)
      assertEquals("Non è stato possibile salvare l’uscita dall’account. Riprova.", notice.message)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aPendingLogoutBlocksDuplicateLogoutAndBothLoginMethods() = runTest {
    val gate = CompletableDeferred<Unit>()
    val auth = FakeAuthRepository(session())
    auth.logoutAction = { gate.await() }
    val viewModel = MainViewModel(auth, FakeSettingsRepository(AppSettings()), RecordingAppUpdater(),
      FakeSchoolYearRepository(), FakeDashboardRepository(), accountSyncScheduler)
    testScheduler.advanceUntilIdle()

    viewModel.uiState.filter { !it.isLoading }.test {
      awaitItem()
      viewModel.logout()
      assertEquals(true, awaitItem().isAuthenticating)
      viewModel.logout()
      viewModel.login("new-student", "synthetic-secret")
      assertEquals(null, viewModel.beginCieLogin())
      assertEquals(1, auth.logoutCount)
      assertEquals(0, auth.loginCount)
      gate.complete(Unit)
      assertEquals(false, awaitItem().isAuthenticating)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun ignoreUpdateVersion_isRememberedSoTheNextCheckDoesNotOfferItAgain() = runTest {
    val settingsRepository = FakeSettingsRepository(AppSettings())
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(),
      settingsRepository = settingsRepository,
      updater = RecordingAppUpdater(),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = FakeDashboardRepository(),
      accountSyncScheduler = accountSyncScheduler,
    )
    testScheduler.advanceUntilIdle()

    viewModel.ignoreUpdateVersion("6.0.4")
    testScheduler.advanceUntilIdle()

    assertEquals("6.0.4", settingsRepository.observeSettings().first().ignoredStableUpdateVersion)
  }

  @Test
  fun notificationPermissionResult_refreshesRuntimeStateAndLiveTimetableWhenGranted() = runTest {
    val settingsRepository = FakeSettingsRepository(AppSettings())
    val viewModel = MainViewModel(
      authRepository = FakeAuthRepository(session()),
      settingsRepository = settingsRepository,
      updater = RecordingAppUpdater(),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = FakeDashboardRepository(),
      accountSyncScheduler = accountSyncScheduler,
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
      updater = RecordingAppUpdater(),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = FakeDashboardRepository(),
      accountSyncScheduler = accountSyncScheduler,
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
      updater = RecordingAppUpdater(),
      schoolYearRepository = schoolYears,
      dashboardRepository = FakeDashboardRepository(),
      accountSyncScheduler = accountSyncScheduler,
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
      updater = RecordingAppUpdater(),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = dashboard,
      accountSyncScheduler = accountSyncScheduler,
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
      updater = RecordingAppUpdater(),
      schoolYearRepository = FakeSchoolYearRepository(),
      dashboardRepository = dashboard,
      accountSyncScheduler = accountSyncScheduler,
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
      updater = RecordingAppUpdater(),
      schoolYearRepository = schoolYears,
      dashboardRepository = FakeDashboardRepository(),
      accountSyncScheduler = accountSyncScheduler,
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
      updater = RecordingAppUpdater(),
      schoolYearRepository = schoolYears,
      dashboardRepository = FakeDashboardRepository(),
      accountSyncScheduler = accountSyncScheduler,
    )

    viewModel.inAppNotifications.test {
      schoolYears.select(current)
      testScheduler.advanceUntilIdle()

      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun cieAndPasswordShareOneBusyOwnerAndCancellationKeepsTheAccount() = runTest {
    val auth = FakeAuthRepository(session())
    val model = mainViewModel(auth)
    model.uiState.test {
      testScheduler.advanceUntilIdle()
      val saved = auth.session.value
      assertEquals(42L, model.beginCieLogin())
      assertEquals(null, model.beginCieLogin())
      model.login("studente", "private-password")
      assertEquals(0, auth.loginCount)
      assertEquals(true, model.uiState.value.isAuthenticating)
      model.completeCieLogin(CieLoginOutcome.Cancelled)
      testScheduler.advanceUntilIdle()
      assertSame(saved, model.uiState.value.session)
      assertEquals(null, model.uiState.value.authError)
      assertEquals(false, model.uiState.value.isAuthenticating)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun duplicatePasswordSubmissionsAndStaleCieResultsCannotReleaseTheBusyOwner() = runTest {
    val auth = FakeAuthRepository().apply { loginGate = CompletableDeferred() }
    val model = mainViewModel(auth)
    model.uiState.test {
      testScheduler.advanceUntilIdle()
      assertEquals(42L, model.beginCieLogin())
      model.completeCieLogin(CieLoginOutcome.Cancelled)
      model.login("studente", "private-password")
      model.login("studente", "private-password")
      model.completeCieLogin(CieLoginOutcome.Cancelled)
      assertEquals(1, auth.loginCount)
      assertEquals(null, model.beginCieLogin())
      assertEquals(true, model.uiState.value.isAuthenticating)
      auth.loginGate!!.complete(Result.failure(Exception("secret-payload")))
      testScheduler.advanceUntilIdle()
      assertEquals(false, model.uiState.value.isAuthenticating)
      assertEquals(false, model.uiState.value.authError.orEmpty().contains("secret-payload"))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun restReauthenticationSuspendsNetworkButPortalExpiryDoesNot() = runTest {
    val auth = FakeAuthRepository(session())
    val settings = FakeSettingsRepository(AppSettings(periodicSyncEnabled = true))
    val model = mainViewModel(auth, settings)
    model.uiState.test {
      testScheduler.advanceUntilIdle()
      clearMocks(accountSyncScheduler, answers = false)
      settings.resetCounters()
      auth.authenticationState.value = AuthenticationState(rest = RestAuthenticationState.REAUTHENTICATION_REQUIRED)
      testScheduler.advanceUntilIdle()
      coVerify(exactly = 1) { accountSyncScheduler.cancel(42L) }
      coVerify(exactly = 0) { accountSyncScheduler.schedule(any(), any()) }
      model.onNotificationPermissionResult(true)
      testScheduler.advanceUntilIdle()
      assertEquals(0, settings.liveTimetableRefreshCount)
      assertSame(auth.session.value, model.uiState.value.session)
      auth.authenticationState.value = AuthenticationState(rest = RestAuthenticationState.READY, portal = PortalAuthenticationState.REAUTHENTICATION_REQUIRED)
      testScheduler.advanceUntilIdle()
      coVerify(exactly = 1) { accountSyncScheduler.schedule(42L, "student") }
      settings.resetCounters()
      model.onNotificationPermissionResult(true)
      testScheduler.advanceUntilIdle()
      assertEquals(1, settings.liveTimetableRefreshCount)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun cieFailureIsGenericAndDoesNotDiscardOfflineIdentity() = runTest {
    val auth = FakeAuthRepository(session())
    val model = mainViewModel(auth)
    model.uiState.test {
      testScheduler.advanceUntilIdle()
      val saved = auth.session.value
      model.beginCieLogin()
      model.completeCieLogin(CieLoginOutcome.TimedOut)
      testScheduler.advanceUntilIdle()
      assertSame(saved, model.uiState.value.session)
      assertEquals(CieLoginOutcome.TimedOut.message, model.uiState.value.authError)
      assertEquals(false, model.uiState.value.isAuthenticating)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun backgroundSchedulingUsesIdentityEpochAndIgnoresSameIdentityTokenRotation() = runTest {
    val auth = FakeAuthRepository(session())
    val model = mainViewModel(auth, FakeSettingsRepository(AppSettings(periodicSyncEnabled = true)))
    model.uiState.test {
      testScheduler.advanceUntilIdle()
      coVerify(exactly = 1) { accountSyncScheduler.schedule(42L, "student") }
      clearMocks(accountSyncScheduler, answers = false)
      auth.session.value = auth.session.value!!.copy(token = "rotated-token")
      testScheduler.advanceUntilIdle()
      coVerify(exactly = 0) { accountSyncScheduler.schedule(any(), any()) }
      coVerify(exactly = 0) { accountSyncScheduler.cancel(any()) }

      auth.epoch = 43L
      auth.session.value = null
      auth.authenticationState.value = AuthenticationState()
      testScheduler.advanceUntilIdle()
      coVerify(atLeast = 1) { accountSyncScheduler.cancel(43L) }
      coVerify(exactly = 0) { accountSyncScheduler.cancel(42L) }
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun mainViewModel(
    auth: FakeAuthRepository,
    settings: FakeSettingsRepository = FakeSettingsRepository(AppSettings()),
  ) = MainViewModel(auth, settings, RecordingAppUpdater(), FakeSchoolYearRepository(), FakeDashboardRepository(), accountSyncScheduler)

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
  override val authenticationState = MutableStateFlow(AuthenticationState(
    rest = if (initialSession == null) RestAuthenticationState.SIGNED_OUT else RestAuthenticationState.READY,
  ))
  var loginCount = 0
  var loginGate: CompletableDeferred<Result<UserSession>>? = null
  var logoutAction: (suspend () -> Unit)? = null
  var logoutCount = 0
  var epoch: Long = 42L
  override fun authenticationEpoch(): Long = epoch
  override suspend fun restore(): UserSession? = null
  override suspend fun login(username: String, password: String): Result<UserSession> {
    loginCount++
    return loginGate?.await() ?: Result.failure(UnsupportedOperationException())
  }
  override suspend fun logout() {
    logoutCount++
    logoutAction?.invoke()
  }
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
  override suspend fun setHapticsEnabled(enabled: Boolean) = Unit
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
  override suspend fun setSubjectColor(key: String, argb: Int?) {
    val colors = settings.value.subjectColors
    settings.value = settings.value.copy(subjectColors = if (argb == null) colors - key else colors + (key to argb))
  }
  override suspend fun resetSubjectColors() {
    settings.value = settings.value.copy(subjectColors = emptyMap())
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

private class RecordingAppUpdater : AppUpdater {
  override suspend fun check(
    currentVersionName: String,
    channel: UpdateChannel,
    ignoredVersion: String,
  ): Result<AvailableAppUpdate?> = Result.success(null)

  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> =
    flowOf(AppUpdateInstallState.Installed(update.apkAsset))
}
