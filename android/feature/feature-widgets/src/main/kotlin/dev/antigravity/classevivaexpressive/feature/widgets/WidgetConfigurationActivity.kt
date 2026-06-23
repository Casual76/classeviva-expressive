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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.antigravity.classevivaexpressive.core.designsystem.theme.ClassevivaExpressiveTheme
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

      ClassevivaExpressiveTheme(settings = AppSettings()) {
        WidgetConfigurationScreen(
          preferences = preferences ?: SchoolWidgetPreferences(),
          onPreferencesChange = { preferences = it },
          onCancel = { finish() },
          onSave = { next ->
            lifecycleScope.launch {
              val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(widgetId)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigurationScreen(
  preferences: SchoolWidgetPreferences,
  onPreferencesChange: (SchoolWidgetPreferences) -> Unit,
  onCancel: () -> Unit,
  onSave: (SchoolWidgetPreferences) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(title = { Text("Classeviva Oggi") })
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      ConfigurationSection(title = "Periodo") {
        SegmentedChoice(
          label = "Compiti",
          options = listOf(1, 3, 7),
          selected = preferences.homeworkDays,
          valueLabel = { "$it giorni" },
          onSelected = { onPreferencesChange(preferences.copy(homeworkDays = it)) },
        )
        SegmentedChoice(
          label = "Verifiche",
          options = listOf(7, 14, 30),
          selected = preferences.assessmentDays,
          valueLabel = { "$it giorni" },
          onSelected = { onPreferencesChange(preferences.copy(assessmentDays = it)) },
        )
      }

      ConfigurationSection(title = "Sezioni") {
        ToggleRow("Compiti", preferences.showHomework) {
          onPreferencesChange(preferences.copy(showHomework = it))
        }
        ToggleRow("Verifiche", preferences.showAssessments) {
          onPreferencesChange(preferences.copy(showAssessments = it))
        }
        ToggleRow("Comunicazioni", preferences.showCommunications) {
          onPreferencesChange(preferences.copy(showCommunications = it))
        }
        ToggleRow("Voti", preferences.showGrades) {
          onPreferencesChange(preferences.copy(showGrades = it))
        }
        ToggleRow("Altri eventi", preferences.showOtherEvents) {
          onPreferencesChange(preferences.copy(showOtherEvents = it))
        }
      }

      ConfigurationSection(title = "Privacy") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = preferences.privacyMode == WidgetPrivacyMode.FULL,
            onClick = { onPreferencesChange(preferences.copy(privacyMode = WidgetPrivacyMode.FULL)) },
            label = { Text("Completa") },
          )
          FilterChip(
            selected = preferences.privacyMode == WidgetPrivacyMode.DISCREET,
            onClick = { onPreferencesChange(preferences.copy(privacyMode = WidgetPrivacyMode.DISCREET)) },
            label = { Text("Discreta") },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onCancel) {
          Text("Annulla")
        }
        Button(onClick = { onSave(preferences) }) {
          Text("Salva")
        }
      }
    }
  }
}

@Composable
private fun ConfigurationSection(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
      )
      content()
    }
  }
}

@Composable
private fun SegmentedChoice(
  label: String,
  options: List<Int>,
  selected: Int,
  valueLabel: (Int) -> String,
  onSelected: (Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      options.forEach { value ->
        FilterChip(
          selected = selected == value,
          onClick = { onSelected(value) },
          label = { Text(valueLabel(value)) },
        )
      }
    }
  }
}

@Composable
private fun ToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
    )
  }
}
