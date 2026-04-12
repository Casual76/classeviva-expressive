package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.NetworkConfig
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityResolverTest {

  private val currentYear = SchoolYearRef(startYear = 2025, endYear = 2026)
  private val previousYear = SchoolYearRef(startYear = 2024, endYear = 2025)

  private fun buildResolver(
    selectedYear: SchoolYearRef = currentYear,
    gatewayConfigured: Boolean = false,
  ): DefaultCapabilityResolver {
    val schoolYearStore = mockk<SchoolYearStore>()
    val settingsStore = mockk<SettingsStore>()

    every { schoolYearStore.observeSelectedSchoolYear() } returns flowOf(selectedYear)
    every { schoolYearStore.currentSchoolYearRef() } returns currentYear
    every { settingsStore.settings } returns flowOf(
      AppSettings(
        networkConfig = NetworkConfig(
          gatewayBaseUrl = if (gatewayConfigured) "http://gateway" else ""
        )
      )
    )

    return DefaultCapabilityResolver(schoolYearStore, settingsStore)
  }

  @Test
  fun gradesFeature_isDirectRestForCurrentYear() = runTest {
    val resolver = buildResolver(selectedYear = currentYear, gatewayConfigured = false)

    val matrix = resolver.observeCapabilityMatrix().first()
    val grades = matrix.first { it.feature == RegistroFeature.GRADES }

    assertEquals(FeatureCapabilityMode.DIRECT_REST, grades.mode)
    assertTrue(grades.enabled)
  }

  @Test
  fun gradesFeature_requiresGatewayForPreviousYear() = runTest {
    val resolver = buildResolver(selectedYear = previousYear, gatewayConfigured = true)

    val matrix = resolver.observeCapabilityMatrix().first()
    val grades = matrix.first { it.feature == RegistroFeature.GRADES }

    assertEquals(FeatureCapabilityMode.GATEWAY, grades.mode)
  }

  @Test
  fun gradesFeature_isGatewayModeButDisabledWhenPreviousYearAndNoGateway() = runTest {
    val resolver = buildResolver(selectedYear = previousYear, gatewayConfigured = false)

    val matrix = resolver.observeCapabilityMatrix().first()
    val grades = matrix.first { it.feature == RegistroFeature.GRADES }

    assertEquals(FeatureCapabilityMode.GATEWAY, grades.mode)
    assertFalse(grades.enabled)
  }

  @Test
  fun absenceJustificationsFeature_isGatewayMode() = runTest {
    val resolver = buildResolver(gatewayConfigured = true)

    val matrix = resolver.observeCapabilityMatrix().first()
    val justifications = matrix.first { it.feature == RegistroFeature.ABSENCE_JUSTIFICATIONS }

    assertEquals(FeatureCapabilityMode.GATEWAY, justifications.mode)
    assertTrue(justifications.enabled)
  }

  @Test
  fun absenceJustificationsFeature_isDisabledWhenGatewayNotConfigured() = runTest {
    val resolver = buildResolver(gatewayConfigured = false)

    val matrix = resolver.observeCapabilityMatrix().first()
    val justifications = matrix.first { it.feature == RegistroFeature.ABSENCE_JUSTIFICATIONS }

    assertEquals(FeatureCapabilityMode.GATEWAY, justifications.mode)
    assertFalse(justifications.enabled)
  }

  @Test
  fun noticeboardReplyFeature_isGatewayMode() = runTest {
    val resolver = buildResolver(gatewayConfigured = true)

    val matrix = resolver.observeCapabilityMatrix().first()
    val reply = matrix.first { it.feature == RegistroFeature.NOTICEBOARD_REPLY }

    assertEquals(FeatureCapabilityMode.GATEWAY, reply.mode)
    assertTrue(reply.enabled)
  }

  @Test
  fun loginSessionFeature_isAlwaysDirectRest() = runTest {
    val resolver = buildResolver(gatewayConfigured = false)

    val capability = resolver.observeCapability(RegistroFeature.LOGIN_SESSION).first()

    assertEquals(FeatureCapabilityMode.DIRECT_REST, capability.mode)
    assertTrue(capability.enabled)
  }

  @Test
  fun notificationsFeature_isAlwaysDirectRest() = runTest {
    val resolver = buildResolver(gatewayConfigured = false)

    val capability = resolver.observeCapability(RegistroFeature.NOTIFICATIONS).first()

    assertEquals(FeatureCapabilityMode.DIRECT_REST, capability.mode)
    assertTrue(capability.enabled)
  }

  @Test
  fun sportelloFeature_isTenantOptional() = runTest {
    val resolver = buildResolver(gatewayConfigured = false)

    val matrix = resolver.observeCapabilityMatrix().first()
    val sportello = matrix.first { it.feature == RegistroFeature.SPORTELLO }

    assertEquals(FeatureCapabilityMode.TENANT_OPTIONAL, sportello.mode)
  }

  @Test
  fun meetingsFeature_isGatewayMode() = runTest {
    val resolver = buildResolver(gatewayConfigured = true)

    val capability = resolver.observeCapability(RegistroFeature.MEETINGS).first()

    assertEquals(FeatureCapabilityMode.GATEWAY, capability.mode)
    assertTrue(capability.enabled)
  }

  @Test
  fun observeCapability_returnsUnsupportedForUnknownFeature() = runTest {
    val resolver = buildResolver()

    // QUESTIONNAIRES is in the matrix but we can verify it returns TENANT_OPTIONAL
    val capability = resolver.observeCapability(RegistroFeature.QUESTIONNAIRES).first()

    assertEquals(FeatureCapabilityMode.TENANT_OPTIONAL, capability.mode)
  }
}
