package dev.antigravity.classevivaexpressive.core.data.repository

import dev.antigravity.classevivaexpressive.core.domain.model.FeatureCapabilityMode
import dev.antigravity.classevivaexpressive.core.domain.model.RegistroFeature
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityResolverTest {

  private val currentYear = SchoolYearRef(startYear = 2025, endYear = 2026)
  private val previousYear = SchoolYearRef(startYear = 2024, endYear = 2025)

  private fun buildResolver(
    selectedYear: SchoolYearRef = currentYear,
  ): DefaultCapabilityResolver {
    val schoolYearStore = mockk<SchoolYearStore>()
    every { schoolYearStore.observeSelectedSchoolYear() } returns flowOf(selectedYear)
    return DefaultCapabilityResolver(schoolYearStore)
  }

  @Test
  fun gradesFeature_isDirectRestForCurrentYear() = runTest {
    val resolver = buildResolver(selectedYear = currentYear)

    val matrix = resolver.observeCapabilityMatrix().first()
    val grades = matrix.first { it.feature == RegistroFeature.GRADES }

    assertEquals(FeatureCapabilityMode.DIRECT_REST, grades.mode)
    assertTrue(grades.enabled)
  }

  @Test
  fun gradesFeature_staysDirectRestForPreviousYear() = runTest {
    val resolver = buildResolver(selectedYear = previousYear)

    val matrix = resolver.observeCapabilityMatrix().first()
    val grades = matrix.first { it.feature == RegistroFeature.GRADES }

    assertEquals(FeatureCapabilityMode.DIRECT_REST, grades.mode)
    assertTrue(grades.enabled)
  }

  @Test
  fun absenceJustificationsFeature_isPortalMode() = runTest {
    val resolver = buildResolver()

    val matrix = resolver.observeCapabilityMatrix().first()
    val justifications = matrix.first { it.feature == RegistroFeature.ABSENCE_JUSTIFICATIONS }

    assertEquals(FeatureCapabilityMode.DIRECT_PORTAL, justifications.mode)
    assertTrue(justifications.enabled)
  }

  @Test
  fun noticeboardReplyFeature_isPortalMode() = runTest {
    val resolver = buildResolver()

    val matrix = resolver.observeCapabilityMatrix().first()
    val reply = matrix.first { it.feature == RegistroFeature.NOTICEBOARD_REPLY }

    assertEquals(FeatureCapabilityMode.DIRECT_PORTAL, reply.mode)
    assertTrue(reply.enabled)
  }

  @Test
  fun loginSessionFeature_isAlwaysDirectRest() = runTest {
    val resolver = buildResolver()

    val capability = resolver.observeCapability(RegistroFeature.LOGIN_SESSION).first()

    assertEquals(FeatureCapabilityMode.DIRECT_REST, capability.mode)
    assertTrue(capability.enabled)
  }

  @Test
  fun notificationsFeature_isAlwaysDirectRest() = runTest {
    val resolver = buildResolver()

    val capability = resolver.observeCapability(RegistroFeature.NOTIFICATIONS).first()

    assertEquals(FeatureCapabilityMode.DIRECT_REST, capability.mode)
    assertTrue(capability.enabled)
  }

  @Test
  fun sportelloFeature_isTenantOptional() = runTest {
    val resolver = buildResolver()

    val matrix = resolver.observeCapabilityMatrix().first()
    val sportello = matrix.first { it.feature == RegistroFeature.SPORTELLO }

    assertEquals(FeatureCapabilityMode.TENANT_OPTIONAL, sportello.mode)
  }

  @Test
  fun meetingsFeature_isPortalMode() = runTest {
    val resolver = buildResolver()

    val capability = resolver.observeCapability(RegistroFeature.MEETINGS).first()

    assertEquals(FeatureCapabilityMode.DIRECT_PORTAL, capability.mode)
    assertTrue(capability.enabled)
  }

  @Test
  fun questionnairesFeature_isTenantOptional() = runTest {
    val resolver = buildResolver()

    val capability = resolver.observeCapability(RegistroFeature.QUESTIONNAIRES).first()

    assertEquals(FeatureCapabilityMode.TENANT_OPTIONAL, capability.mode)
  }
}
