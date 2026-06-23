package dev.antigravity.classevivaexpressive.feature.widgets

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class WidgetPrivacyMode {
  FULL,
  DISCREET,
}

data class SchoolWidgetPreferences(
  val homeworkDays: Int = 3,
  val assessmentDays: Int = 14,
  val showHomework: Boolean = true,
  val showAssessments: Boolean = true,
  val showCommunications: Boolean = true,
  val showGrades: Boolean = true,
  val showOtherEvents: Boolean = true,
  val privacyMode: WidgetPrivacyMode = WidgetPrivacyMode.FULL,
  val refreshing: Boolean = false,
  val lastRefreshError: String = "",
  val lastManualRefreshEpochMillis: Long = 0L,
)

internal object SchoolWidgetPreferenceKeys {
  val HomeworkDays = intPreferencesKey("homework_days")
  val AssessmentDays = intPreferencesKey("assessment_days")
  val ShowHomework = booleanPreferencesKey("show_homework")
  val ShowAssessments = booleanPreferencesKey("show_assessments")
  val ShowCommunications = booleanPreferencesKey("show_communications")
  val ShowGrades = booleanPreferencesKey("show_grades")
  val ShowOtherEvents = booleanPreferencesKey("show_other_events")
  val PrivacyMode = stringPreferencesKey("privacy_mode")
  val Refreshing = booleanPreferencesKey("refreshing")
  val LastRefreshError = stringPreferencesKey("last_refresh_error")
  val LastManualRefreshEpochMillis = longPreferencesKey("last_manual_refresh_epoch_millis")
}

internal fun Preferences.toSchoolWidgetPreferences(): SchoolWidgetPreferences {
  return SchoolWidgetPreferences(
    homeworkDays = (this[SchoolWidgetPreferenceKeys.HomeworkDays] ?: 3).coerceIn(1, 14),
    assessmentDays = (this[SchoolWidgetPreferenceKeys.AssessmentDays] ?: 14).coerceIn(1, 30),
    showHomework = this[SchoolWidgetPreferenceKeys.ShowHomework] ?: true,
    showAssessments = this[SchoolWidgetPreferenceKeys.ShowAssessments] ?: true,
    showCommunications = this[SchoolWidgetPreferenceKeys.ShowCommunications] ?: true,
    showGrades = this[SchoolWidgetPreferenceKeys.ShowGrades] ?: true,
    showOtherEvents = this[SchoolWidgetPreferenceKeys.ShowOtherEvents] ?: true,
    privacyMode = this[SchoolWidgetPreferenceKeys.PrivacyMode]
      ?.let { runCatching { WidgetPrivacyMode.valueOf(it) }.getOrNull() }
      ?: WidgetPrivacyMode.FULL,
    refreshing = this[SchoolWidgetPreferenceKeys.Refreshing] ?: false,
    lastRefreshError = this[SchoolWidgetPreferenceKeys.LastRefreshError].orEmpty(),
    lastManualRefreshEpochMillis = this[SchoolWidgetPreferenceKeys.LastManualRefreshEpochMillis] ?: 0L,
  )
}

internal fun MutablePreferences.write(preferences: SchoolWidgetPreferences) {
  this[SchoolWidgetPreferenceKeys.HomeworkDays] = preferences.homeworkDays.coerceIn(1, 14)
  this[SchoolWidgetPreferenceKeys.AssessmentDays] = preferences.assessmentDays.coerceIn(1, 30)
  this[SchoolWidgetPreferenceKeys.ShowHomework] = preferences.showHomework
  this[SchoolWidgetPreferenceKeys.ShowAssessments] = preferences.showAssessments
  this[SchoolWidgetPreferenceKeys.ShowCommunications] = preferences.showCommunications
  this[SchoolWidgetPreferenceKeys.ShowGrades] = preferences.showGrades
  this[SchoolWidgetPreferenceKeys.ShowOtherEvents] = preferences.showOtherEvents
  this[SchoolWidgetPreferenceKeys.PrivacyMode] = preferences.privacyMode.name
  this[SchoolWidgetPreferenceKeys.Refreshing] = preferences.refreshing
  this[SchoolWidgetPreferenceKeys.LastRefreshError] = preferences.lastRefreshError
  this[SchoolWidgetPreferenceKeys.LastManualRefreshEpochMillis] = preferences.lastManualRefreshEpochMillis
}
