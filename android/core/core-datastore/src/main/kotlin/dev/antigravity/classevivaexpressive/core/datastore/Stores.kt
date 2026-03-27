package dev.antigravity.classevivaexpressive.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.domain.model.AccentMode
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val SettingsThemeModeKey = stringPreferencesKey("theme_mode")
private val SettingsAccentModeKey = stringPreferencesKey("accent_mode")
private val SettingsCustomAccentKey = stringPreferencesKey("custom_accent")
private val SettingsDynamicColorKey = booleanPreferencesKey("dynamic_color")
private val SettingsAmoledKey = booleanPreferencesKey("amoled")
private val SettingsNotificationsKey = booleanPreferencesKey("notifications")
private val SettingsPeriodicSyncKey = booleanPreferencesKey("periodic_sync")

@Singleton
class SettingsStore(@ApplicationContext context: Context) {
  private val dataStore = PreferenceDataStoreFactory.create(
    produceFile = { context.preferencesDataStoreFile("classeviva_settings.preferences_pb") },
  )

  val settings: Flow<AppSettings> =
    dataStore.data.map { prefs ->
      AppSettings(
        themeMode = prefs[SettingsThemeModeKey]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
        accentMode = prefs[SettingsAccentModeKey]?.let { AccentMode.valueOf(it) } ?: AccentMode.BRAND,
        customAccentName = prefs[SettingsCustomAccentKey] ?: "ember",
        dynamicColorEnabled = prefs[SettingsDynamicColorKey] ?: true,
        amoledEnabled = prefs[SettingsAmoledKey] ?: false,
        notificationsEnabled = prefs[SettingsNotificationsKey] ?: true,
        periodicSyncEnabled = prefs[SettingsPeriodicSyncKey] ?: true,
      )
    }

  suspend fun update(transform: (AppSettings) -> AppSettings) {
    val current = settings.first()
    val next = transform(current)
    dataStore.edit { prefs ->
      prefs[SettingsThemeModeKey] = next.themeMode.name
      prefs[SettingsAccentModeKey] = next.accentMode.name
      prefs[SettingsCustomAccentKey] = next.customAccentName
      prefs[SettingsDynamicColorKey] = next.dynamicColorEnabled
      prefs[SettingsAmoledKey] = next.amoledEnabled
      prefs[SettingsNotificationsKey] = next.notificationsEnabled
      prefs[SettingsPeriodicSyncKey] = next.periodicSyncEnabled
    }
  }
}

@Singleton
class SessionStore(@ApplicationContext context: Context) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "classeviva_secure_session",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
  )
  private val sessionFlow = MutableStateFlow(readCurrentSession())

  val session: StateFlow<UserSession?> = sessionFlow

  fun readCurrentSession(): UserSession? {
    val raw = sharedPreferences.getString("session_payload", null) ?: return null
    return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
  }

  fun writeSession(session: UserSession) {
    sharedPreferences.edit().putString("session_payload", json.encodeToString(session)).apply()
    sessionFlow.value = session
  }

  fun clear() {
    sharedPreferences.edit().clear().apply()
    sessionFlow.value = null
  }
}

@Module
@InstallIn(SingletonComponent::class)
object StoresModule {
  @Provides
  @Singleton
  fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

  @Provides
  @Singleton
  fun provideSessionStore(@ApplicationContext context: Context): SessionStore = SessionStore(context)
}
