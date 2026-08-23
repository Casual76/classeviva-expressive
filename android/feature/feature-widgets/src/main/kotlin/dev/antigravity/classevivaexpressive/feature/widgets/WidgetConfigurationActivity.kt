package dev.antigravity.classevivaexpressive.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.EntryPointAccessors
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidButton
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidButtonStyle
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidMotionPolicyProvider
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidScreen
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSectionFootnote
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSectionHeader
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSegmentedControl
import dev.antigravity.classevivaexpressive.core.designsystem.fluid.FluidSwitch
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveCard
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveListDivider
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ExpressiveListGroup
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
  private val appWidgetId: Int
    get() = intent?.extras?.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID,
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val widgetId = appWidgetId
    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    setResult(Activity.RESULT_CANCELED, resultValue)
    if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    setContent {
      var preferences by remember { mutableStateOf<SchoolWidgetPreferences?>(null) }
      LaunchedEffect(widgetId) {
        val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(widgetId)
        preferences = getAppWidgetState(
          this@WidgetConfigurationActivity,
          PreferencesGlanceStateDefinition,
          glanceId,
        ).toSchoolWidgetPreferences()
      }

      // The configuration screen is reached from the launcher, not from the app, so it has no theme
      // handed down to it. Reading the stored settings is what keeps a user on AMOLED from being
      // shown a white sheet the one time they open this screen.
      val settings by produceState(initialValue = AppSettings()) {
        val entryPoint = EntryPointAccessors.fromApplication(
          applicationContext,
          SchoolWidgetEntryPoint::class.java,
        )
        runCatching { entryPoint.settingsRepository().observeSettings().collect { value = it } }
      }

      ClassevivaExpressiveTheme(settings = settings) {
        FluidMotionPolicyProvider {
          WidgetConfigurationScreen(
            preferences = preferences ?: SchoolWidgetPreferences(),
            onPreferencesChange = { preferences = it },
            onCancel = { finish() },
            onSave = { next ->
              lifecycleScope.launch {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity)
                  .getGlanceIdBy(widgetId)
                updateAppWidgetState(this@WidgetConfigurationActivity, glanceId) { prefs ->
                  prefs.write(next.copy(refreshing = false, lastRefreshError = ""))
                }
                SchoolOverviewWidget().update(this@WidgetConfigurationActivity, glanceId)
                setResult(Activity.RESULT_OK, resultValue)
                finish()
              }
            },
          )
        }
      }
    }
  }
}

/**
 * The widget's own settings screen, built out of the same parts as the app's.
 *
 * It used to be a Material `Scaffold` with a small top bar and boxed sections, which is the one
 * screen of the app that still looked like the version before the redesign — and it is the screen
 * someone sees while deciding whether to keep the widget at all.
 */
@Composable
private fun WidgetConfigurationScreen(
  preferences: SchoolWidgetPreferences,
  onPreferencesChange: (SchoolWidgetPreferences) -> Unit,
  onCancel: () -> Unit,
  onSave: (SchoolWidgetPreferences) -> Unit,
) {
  FluidScreen(
    title = "Classeviva Oggi",
    subtitle = "Cosa mostra il widget sulla schermata home.",
    onBack = onCancel,
    itemSpacing = 12.dp,
  ) {
    item { FluidSectionHeader(title = "Periodo") }
    item {
      ExpressiveCard {
        SegmentedSetting(
          label = "Compiti",
          options = listOf(1, 3, 7),
          selected = preferences.homeworkDays,
          valueLabel = { "$it giorni" },
          onSelected = { onPreferencesChange(preferences.copy(homeworkDays = it)) },
        )
        SegmentedSetting(
          label = "Verifiche",
          options = listOf(7, 14, 30),
          selected = preferences.assessmentDays,
          valueLabel = { "$it giorni" },
          onSelected = { onPreferencesChange(preferences.copy(assessmentDays = it)) },
        )
      }
    }

    item { FluidSectionHeader(title = "Sezioni") }
    item {
      ExpressiveListGroup {
        val toggles = listOf(
          Triple("Compiti", preferences.showHomework) { value: Boolean ->
            onPreferencesChange(preferences.copy(showHomework = value))
          },
          Triple("Verifiche", preferences.showAssessments) { value: Boolean ->
            onPreferencesChange(preferences.copy(showAssessments = value))
          },
          Triple("Altri eventi", preferences.showOtherEvents) { value: Boolean ->
            onPreferencesChange(preferences.copy(showOtherEvents = value))
          },
          Triple("Voti", preferences.showGrades) { value: Boolean ->
            onPreferencesChange(preferences.copy(showGrades = value))
          },
          Triple("Comunicazioni", preferences.showCommunications) { value: Boolean ->
            onPreferencesChange(preferences.copy(showCommunications = value))
          },
        )
        toggles.forEachIndexed { index, (label, checked, onChange) ->
          if (index > 0) {
            ExpressiveListDivider()
          }
          ToggleRow(label = label, checked = checked, onCheckedChange = onChange)
        }
      }
    }

    item { FluidSectionHeader(title = "Privacy") }
    item {
      ExpressiveCard {
        FluidSegmentedControl(
          options = WidgetPrivacyMode.entries.toList(),
          selected = preferences.privacyMode,
          onSelect = { onPreferencesChange(preferences.copy(privacyMode = it)) },
          modifier = Modifier.fillMaxWidth(),
          label = { it.label() },
        )
      }
    }
    item {
      FluidSectionFootnote(
        text = "In modalità discreta il widget dice che c'è qualcosa da leggere, senza mostrare " +
          "titoli, materie o voti a chi guarda la schermata home.",
      )
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FluidButton(
          text = "Salva",
          onClick = { onSave(preferences) },
          style = FluidButtonStyle.Filled,
          fillWidth = true,
        )
        FluidButton(
          text = "Annulla",
          onClick = onCancel,
          style = FluidButtonStyle.Plain,
          fillWidth = true,
        )
      }
    }
  }
}

@Composable
private fun SegmentedSetting(
  label: String,
  options: List<Int>,
  selected: Int,
  valueLabel: (Int) -> String,
  onSelected: (Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = label, style = MaterialTheme.typography.titleMedium)
    FluidSegmentedControl(
      options = options,
      selected = selected,
      onSelect = onSelected,
      modifier = Modifier.fillMaxWidth(),
      label = valueLabel,
    )
  }
}

@Composable
private fun ToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {}
      .toggleable(
        value = checked,
        role = Role.Switch,
        onValueChange = onCheckedChange,
      )
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.weight(1f),
    )
    // The row owns the switch's semantics and its 48dp target, so the switch itself is decorative.
    FluidSwitch(checked = checked, onCheckedChange = null)
  }
}

private fun WidgetPrivacyMode.label(): String = when (this) {
  WidgetPrivacyMode.FULL -> "Completa"
  WidgetPrivacyMode.DISCREET -> "Discreta"
}
