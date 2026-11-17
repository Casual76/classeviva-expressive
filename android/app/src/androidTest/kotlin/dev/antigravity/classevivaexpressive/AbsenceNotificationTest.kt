package dev.antigravity.classevivaexpressive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceJustificationRequest
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceRecord
import dev.antigravity.classevivaexpressive.core.domain.model.AbsenceType
import dev.antigravity.classevivaexpressive.core.domain.model.AbsencesRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesRoute
import dev.antigravity.classevivaexpressive.feature.absences.AbsencesViewModel
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AbsenceNotificationTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun notificationOpensAnAlreadyJustifiedRecordAndRefreshDoesNotReopenIt() {
    val record = AbsenceRecord("old-absence", "2026-11-17", AbsenceType.ABSENCE, justified = true, justificationReason = "Motivazione salvata")
    val records = MutableStateFlow(listOf(record))
    val repository = object : AbsencesRepository {
      override fun observeAbsences() = records
      override suspend fun refreshAbsences(force: Boolean) = Result.success(records.value)
      override suspend fun justifyAbsence(record: AbsenceRecord, reason: String?, request: AbsenceJustificationRequest) = Result.success(records.value)
    }
    lateinit var viewModel: AbsencesViewModel
    composeRule.runOnUiThread { viewModel = AbsencesViewModel(repository) }
    composeRule.setContent {
      ClassevivaExpressiveTheme(AppSettings(dynamicColorEnabled = false)) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            AbsencesRoute(initialAbsenceId = record.id, viewModel = viewModel)
            FluidGlassModalHost(host, backdrop = null)
          }
        }
      }
    }
    composeRule.onNodeWithText("Assenza del 17 nov 2026").assertIsDisplayed()
    composeRule.onNodeWithText("Chiudi").performClick()
    composeRule.runOnIdle { records.value = listOf(record, record.copy(id = "other", date = "2026-11-18")) }
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Assenza del 17 nov 2026").assertDoesNotExist()
  }
}
