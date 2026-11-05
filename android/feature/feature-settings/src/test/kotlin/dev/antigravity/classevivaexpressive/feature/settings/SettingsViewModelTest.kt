package dev.antigravity.classevivaexpressive.feature.settings

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import app.cash.turbine.test
import dev.antigravity.classevivaexpressive.core.domain.model.AppBackupRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AppBackupImportSummary
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
import dev.antigravity.classevivaexpressive.core.domain.model.GradesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.LessonsRepository
import dev.antigravity.classevivaexpressive.core.domain.model.Subject
import dev.antigravity.classevivaexpressive.core.domain.model.SubjectKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CompletableDeferred
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
  private val appBackupRepository = mockk<AppBackupRepository>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)
  private val gradesRepository = mockk<GradesRepository>(relaxed = true)
  private val lessonsRepository = mockk<LessonsRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun buildViewModel(
    selectedSchoolYears: Flow<SchoolYearRef> = flowOf(SchoolYearRef(2025, 2026)),
  ): SettingsViewModel {
    every { settingsRepository.observeSettings() } returns flowOf(AppSettings())
    every { settingsRepository.observeNotificationRuntimeState() } returns flowOf(dev.antigravity.classevivaexpressive.core.domain.model.NotificationRuntimeState())
    every { authRepository.session } returns MutableStateFlow(null)
    every { schoolYearRepository.observeSelectedSchoolYear() } returns selectedSchoolYears
    every { schoolYearRepository.observeAvailableSchoolYears() } returns flowOf(listOf(SchoolYearRef(2025, 2026)))
    every { capabilityResolver.observeCapabilityMatrix() } returns flowOf(emptyList())
    return SettingsViewModel(settingsRepository, authRepository, schoolYearRepository, capabilityResolver, appBackupRepository, context, gradesRepository, lessonsRepository)
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

    val vm = SettingsViewModel(settingsRepository, authRepository, schoolYearRepository, capabilityResolver, appBackupRepository, context, gradesRepository, lessonsRepository)

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

  @Test
  fun setDynamicColor_enablingRequestsTheAtomicRepositoryTransition() = runTest {
    val vm = buildViewModel()

    vm.setDynamicColor(true)

    coVerify(exactly = 1) { settingsRepository.setDynamicColorEnabled(true) }
    coVerify(exactly = 0) { settingsRepository.updateAccentMode(any()) }
  }

  @Test
  fun setAccentPreset_requestsOneAtomicRepositoryMutation() = runTest {
    val vm = buildViewModel()

    vm.setAccentPreset("ember")

    coVerify(exactly = 1) { settingsRepository.updateCustomAccent("ember") }
    coVerify(exactly = 0) { settingsRepository.updateAccentMode(AccentMode.CUSTOM_PRESET) }
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

  @Test
  fun sendTestNotificationForChannel_setsSuccessMessage() = runTest {
    coEvery { settingsRepository.sendTestNotificationForChannel("voti") } returns Result.success(Unit)

    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.sendTestNotificationForChannel("voti")
      val updated = awaitItem()
      assertEquals("Test notifica «voti» inviato.", updated.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
    coVerify { settingsRepository.sendTestNotificationForChannel("voti") }
  }

  // ─── Anno scolastico ──────────────────────────────────────────────────────

  @Test
  fun selectSchoolYear_usesEffectiveYearInMessageAfterFallback() = runTest {
    val requested = SchoolYearRef(2026, 2027)
    val effective = SchoolYearRef(2025, 2026)
    val selectedSchoolYears = MutableStateFlow(effective)
    coEvery { schoolYearRepository.selectSchoolYear(requested) } answers {
      selectedSchoolYears.value = effective
    }

    val vm = buildViewModel(selectedSchoolYears)

    vm.state.test {
      awaitItem()
      vm.selectSchoolYear(requested)
      testDispatcher.scheduler.advanceUntilIdle()

      assertEquals("Anno scolastico impostato su ${effective.label}.", vm.state.value.lastMessage)
      assertFalse(vm.state.value.isChangingSchoolYear)
      cancelAndIgnoreRemainingEvents()
    }
    coVerify(exactly = 1) { schoolYearRepository.selectSchoolYear(requested) }
  }

  @Test
  fun selectSchoolYear_disablesFurtherChangesUntilAuthoritativeRefreshCompletes() = runTest {
    val requested = SchoolYearRef(2026, 2027)
    val ignoredDuplicate = SchoolYearRef(2025, 2026)
    val selectionCompleted = CompletableDeferred<Unit>()
    coEvery { schoolYearRepository.selectSchoolYear(requested) } coAnswers {
      selectionCompleted.await()
    }
    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.selectSchoolYear(requested)
      assertTrue(awaitItem().isChangingSchoolYear)

      vm.selectSchoolYear(ignoredDuplicate)
      coVerify(exactly = 1) { schoolYearRepository.selectSchoolYear(requested) }
      coVerify(exactly = 0) { schoolYearRepository.selectSchoolYear(ignoredDuplicate) }

      selectionCompleted.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()
      assertFalse(vm.state.value.isChangingSchoolYear)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // ─── Logout ───────────────────────────────────────────────────────────────

  @Test
  fun selectSchoolYear_repositoryRefreshFailureRestoresControlsAndShowsFeedback() = runTest {
    val requested = SchoolYearRef(2026, 2027)
    coEvery { schoolYearRepository.selectSchoolYear(requested) } throws
      IllegalStateException("aggiornamento del registro non disponibile")
    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.selectSchoolYear(requested)
      testDispatcher.scheduler.advanceUntilIdle()

      assertFalse(vm.state.value.isChangingSchoolYear)
      assertEquals(
        "Non è stato possibile cambiare anno scolastico: aggiornamento del registro non disponibile",
        vm.state.value.lastMessage,
      )
      cancelAndIgnoreRemainingEvents()
    }
    coVerify(exactly = 1) { schoolYearRepository.selectSchoolYear(requested) }
  }

  @Test
  fun logout_callsAuthRepository() = runTest {
    val vm = buildViewModel()
    vm.logout()
    coVerify { authRepository.logout() }
  }

  @Test
  fun logoutFailureIsSanitizedInsteadOfEscapingTheViewModelScope() = runTest {
    val vm = buildViewModel()
    coEvery { authRepository.logout() } throws java.io.IOException("private-storage-detail")
    vm.state.test {
      awaitItem()
      vm.logout()
      assertEquals("Sei uscito dall’account. La pulizia dei dati locali non è stata completata.", awaitItem().lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun exportBackupOpensAndWritesSafOnlyInsideTheRepositoryOwnedCallback() = runTest {
    val uri = mockk<Uri>()
    val resolver = mockk<ContentResolver>()
    val output = ByteArrayOutputStream()
    val repositoryEntered = CompletableDeferred<Unit>()
    val allowWrite = CompletableDeferred<Unit>()
    every { context.contentResolver } returns resolver
    every { resolver.openOutputStream(uri) } returns output
    coEvery { appBackupRepository.exportBackupTo(any(), any()) } coAnswers {
      val write = secondArg<suspend (String) -> Unit>()
      repositoryEntered.complete(Unit)
      allowWrite.await()
      write("backup-payload")
      Result.success(Unit)
    }
    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.exportBackup(uri, 42L)
      repositoryEntered.await()
      verify(exactly = 0) { resolver.openOutputStream(any()) }
      allowWrite.complete(Unit)
      assertEquals("Backup esportato correttamente.", awaitItem().lastMessage)
      assertEquals("backup-payload", output.toByteArray().decodeToString())
      cancelAndIgnoreRemainingEvents()
    }
    coVerify(exactly = 1) { appBackupRepository.exportBackupTo(42L, any()) }
    coVerify(exactly = 0) { appBackupRepository.exportBackup() }
  }

  @Test
  fun importBackupOpensAndReadsSafOnlyInsideTheRepositoryOwnedCallback() = runTest {
    val uri = mockk<Uri>()
    val resolver = mockk<ContentResolver>()
    val repositoryEntered = CompletableDeferred<Unit>()
    val allowRead = CompletableDeferred<Unit>()
    every { context.contentResolver } returns resolver
    every { resolver.openInputStream(uri) } returns ByteArrayInputStream("backup-payload".encodeToByteArray())
    coEvery { appBackupRepository.importBackupFrom(any(), any()) } coAnswers {
      val read = secondArg<suspend () -> String>()
      repositoryEntered.complete(Unit)
      allowRead.await()
      assertEquals("backup-payload", read())
      Result.success(AppBackupImportSummary(settingsImported = true))
    }
    val vm = buildViewModel()

    vm.state.test {
      awaitItem()
      vm.importBackup(uri, 42L)
      repositoryEntered.await()
      verify(exactly = 0) { resolver.openInputStream(any()) }
      allowRead.complete(Unit)
      assertTrue(awaitItem().lastMessage.orEmpty().isNotBlank())
      cancelAndIgnoreRemainingEvents()
    }
    coVerify(exactly = 1) { appBackupRepository.importBackupFrom(42L, any()) }
    coVerify(exactly = 0) { appBackupRepository.importBackup(any()) }
  }

  @Test
  fun backupErrorsAreSanitizedAndNeverExposeProviderDetails() = runTest {
    val uri = mockk<Uri>()
    coEvery { appBackupRepository.exportBackupTo(any(), any()) } returns Result.failure(java.io.IOException("content://private/personal-data"))
    coEvery { appBackupRepository.importBackupFrom(any(), any()) } throws java.io.IOException("private-payload-details")
    val vm = buildViewModel()
    vm.state.test {
      awaitItem()
      vm.exportBackup(uri, 42L)
      assertEquals("Esportazione backup non riuscita.", awaitItem().lastMessage)
      vm.importBackup(uri, 42L)
      assertEquals("Importazione backup non riuscita.", awaitItem().lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
    verify(exactly = 0) { context.contentResolver }
  }

  @Test
  fun backupCancellationDoesNotProduceAnErrorMessageOrOpenSaf() = runTest {
    val uri = mockk<Uri>()
    coEvery { appBackupRepository.exportBackupTo(any(), any()) } throws kotlinx.coroutines.CancellationException("cancelled")
    coEvery { appBackupRepository.importBackupFrom(any(), any()) } returns Result.failure(kotlinx.coroutines.CancellationException("cancelled"))
    val vm = buildViewModel()
    vm.state.test {
      awaitItem()
      vm.exportBackup(uri, 42L)
      vm.importBackup(uri, 42L)
      testDispatcher.scheduler.advanceUntilIdle()
      assertEquals(null, vm.state.value.lastMessage)
      cancelAndIgnoreRemainingEvents()
    }
    verify(exactly = 0) { context.contentResolver }
  }

  @Test
  fun backupPickerResultWithoutInMemoryOwnershipIsDiscardedAfterRecreation() = runTest {
    val uri = mockk<Uri>()
    val vm = buildViewModel()
    vm.exportBackup(uri, null)
    vm.importBackup(uri, null)
    vm.exportBackup(null, 42L)
    vm.importBackup(null, 42L)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { appBackupRepository.exportBackupTo(any(), any()) }
    coVerify(exactly = 0) { appBackupRepository.importBackupFrom(any(), any()) }
    verify(exactly = 0) { context.contentResolver }
  }

  @Test
  fun backupPickerResultPassesCapturedEpochWithoutAdoptingCurrentAccountEpoch() = runTest {
    val uri = mockk<Uri>()
    val vm = buildViewModel()
    val session = dev.antigravity.classevivaexpressive.core.domain.model.UserSession(
      "token", "student-a", "student", dev.antigravity.classevivaexpressive.core.domain.model.StudentProfile(id = "student-a"),
    )
    every { authRepository.session } returns MutableStateFlow(session)
    every { authRepository.authenticationEpoch() } returns 42L
    val pickerEpoch = vm.backupAuthenticationEpoch()
    assertEquals(42L, pickerEpoch)
    every { authRepository.authenticationEpoch() } returns 99L
    coEvery { appBackupRepository.exportBackupTo(42L, any()) } returns Result.failure(IllegalStateException("stale"))
    coEvery { appBackupRepository.importBackupFrom(42L, any()) } returns Result.failure(IllegalStateException("stale"))

    vm.exportBackup(uri, pickerEpoch)
    vm.importBackup(uri, pickerEpoch)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { appBackupRepository.exportBackupTo(42L, any()) }
    coVerify(exactly = 1) { appBackupRepository.importBackupFrom(42L, any()) }
    coVerify(exactly = 0) { appBackupRepository.exportBackupTo(99L, any()) }
    coVerify(exactly = 0) { appBackupRepository.importBackupFrom(99L, any()) }
    verify(exactly = 0) { context.contentResolver }
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

  // ─── Colori delle materie ─────────────────────────────────────────────────

  @Test
  fun subjectColorRows_oneRowPerFamily_inRegistryOrder() {
    val rows = subjectColorRows(
      subjects = listOf(
        Subject("2", "DISEGNO E STORIA DELL'ARTE", order = 2),
        Subject("1", "STORIA", order = 1),
        Subject("3", "DIRITTO ED ECONOMIA", order = 3),
      ),
      timetableSubjects = listOf("Storia dell'arte", "FISICA"),
    )
    assertEquals(
      listOf(SubjectKeys.Storia, SubjectKeys.Arte, "x:diritto ed economia", SubjectKeys.Fisica),
      rows.map { it.key },
    )
    assertEquals("Diritto ed economia", rows[2].label)
  }

  @Test
  fun subjectColorRows_beforeAnySync_offersTheDefaultFamilies() {
    val rows = subjectColorRows(emptyList(), emptyList())
    assertTrue(rows.any { it.key == SubjectKeys.Italiano && it.label == "Italiano" })
  }
}
