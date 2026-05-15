package dev.antigravity.classevivaexpressive.core.data.update

import dev.antigravity.classevivaexpressive.core.domain.model.AppUpdateInstallState
import dev.antigravity.classevivaexpressive.core.domain.model.AvailableAppUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
  @Test
  fun checkForStableUpdate_returnsNullWhenVersionIsAlreadyInstalled() = runTest {
    val repository = repositoryReturning(update(version = "5.7.0"))

    val result = repository.checkForStableUpdate(
      currentVersionName = "5.7.0",
      ignoredVersion = "",
    )

    assertTrue(result.isSuccess)
    assertNull(result.getOrThrow())
  }

  @Test
  fun checkForStableUpdate_returnsStableUpdateWhenRemoteVersionIsNewer() = runTest {
    val update = update(version = "5.7.0")
    val repository = repositoryReturning(update)

    val result = repository.checkForStableUpdate(
      currentVersionName = "5.6.9",
      ignoredVersion = "",
    )

    assertTrue(result.isSuccess)
    assertSame(update, result.getOrThrow())
  }

  @Test
  fun checkForStableUpdate_respectsIgnoredStableVersion() = runTest {
    val repository = repositoryReturning(update(version = "5.7.0"))

    val result = repository.checkForStableUpdate(
      currentVersionName = "5.6.9",
      ignoredVersion = "5.7.0",
    )

    assertTrue(result.isSuccess)
    assertNull(result.getOrThrow())
  }

  @Test
  fun checkForStableUpdate_returnsFailureWhenRemoteAssetLookupFails() = runTest {
    val repository = PampaAppUpdateRepository(
      remoteDataSource = FakeRemoteDataSource { error("Asset APK mancante.") },
      installer = NoopInstaller,
    )

    val result = repository.checkForStableUpdate(
      currentVersionName = "5.6.9",
      ignoredVersion = "",
    )

    assertTrue(result.isFailure)
  }

  @Test
  fun stableVersionComparator_handlesMinorPatchAndBetaVersions() {
    assertTrue(isStableVersionNewer(candidate = "5.7.0", current = "5.6.9"))
    assertTrue(isStableVersionNewer(candidate = "5.6.10", current = "5.6.9"))
    assertFalse(isStableVersionNewer(candidate = "5.7.0-beta.1", current = "5.6.9"))
  }

  private fun repositoryReturning(update: AvailableAppUpdate): PampaAppUpdateRepository {
    return PampaAppUpdateRepository(
      remoteDataSource = FakeRemoteDataSource { update },
      installer = NoopInstaller,
    )
  }

  private fun update(version: String) = AvailableAppUpdate(
    version = version,
    changelog = "Bugfix e updater.",
    releaseTag = "stable-classeviva-expressive-v$version",
    apkAsset = "classeviva-expressive-$version.apk",
    downloadUrl = "https://example.test/classeviva-expressive-$version.apk",
    sizeBytes = 42L,
  )
}

private class FakeRemoteDataSource(
  private val fetch: suspend () -> AvailableAppUpdate,
) : PampaUpdateRemoteDataSource {
  override suspend fun fetchStableUpdate(): AvailableAppUpdate = fetch()
}

private object NoopInstaller : AppUpdateInstaller {
  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> {
    return flowOf(AppUpdateInstallState.Installed(update.apkAsset))
  }
}
