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
import dev.antigravity.classevivaexpressive.core.domain.model.NotificationPreferences
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import java.time.LocalDate
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private val SettingsThemeModeKey = stringPreferencesKey("theme_mode")
private val SettingsAccentModeKey = stringPreferencesKey("accent_mode")
private val SettingsCustomAccentKey = stringPreferencesKey("custom_accent")
private val SettingsDynamicColorKey = booleanPreferencesKey("dynamic_color")
private val SettingsAmoledKey = booleanPreferencesKey("amoled")
private val SettingsNotificationsEnabledKey = booleanPreferencesKey("notifications")
private val SettingsNotificationsHomeworkKey = booleanPreferencesKey("notifications_homework")
private val SettingsNotificationsCommunicationsKey = booleanPreferencesKey("notifications_communications")
private val SettingsNotificationsAbsencesKey = booleanPreferencesKey("notifications_absences")
private val SettingsNotificationsTestKey = booleanPreferencesKey("notifications_test")
private val SettingsPeriodicSyncKey = booleanPreferencesKey("periodic_sync")
private val SelectedSchoolYearKey = stringPreferencesKey("selected_school_year")

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
        notificationPreferences = NotificationPreferences(
          enabled = prefs[SettingsNotificationsEnabledKey] ?: true,
          homework = prefs[SettingsNotificationsHomeworkKey] ?: true,
          communications = prefs[SettingsNotificationsCommunicationsKey] ?: true,
          absences = prefs[SettingsNotificationsAbsencesKey] ?: true,
          test = prefs[SettingsNotificationsTestKey] ?: true,
        ),
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
      prefs[SettingsNotificationsEnabledKey] = next.notificationPreferences.enabled
      prefs[SettingsNotificationsHomeworkKey] = next.notificationPreferences.homework
      prefs[SettingsNotificationsCommunicationsKey] = next.notificationPreferences.communications
      prefs[SettingsNotificationsAbsencesKey] = next.notificationPreferences.absences
      prefs[SettingsNotificationsTestKey] = next.notificationPreferences.test
      prefs[SettingsPeriodicSyncKey] = next.periodicSyncEnabled
    }
  }
}

@Singleton
class SchoolYearStore(@ApplicationContext context: Context) : SchoolYearRepository {
  private val dataStore = PreferenceDataStoreFactory.create(
    produceFile = { context.preferencesDataStoreFile("classeviva_school_year.preferences_pb") },
  )

  private val current = currentSchoolYear()

  override fun observeSelectedSchoolYear(): Flow<SchoolYearRef> {
    return dataStore.data.map { prefs ->
      prefs[SelectedSchoolYearKey]?.let(::decodeSchoolYearToken) ?: current
    }.map { selected ->
      availableYears().firstOrNull { it.id == selected.id } ?: current
    }
  }

  override fun observeAvailableSchoolYears(): Flow<List<SchoolYearRef>> = kotlinx.coroutines.flow.flowOf(availableYears())

  override suspend fun selectSchoolYear(year: SchoolYearRef) {
    dataStore.edit { prefs ->
      prefs[SelectedSchoolYearKey] = year.id
    }
  }

  fun currentSchoolYearRef(): SchoolYearRef = current

  private fun availableYears(): List<SchoolYearRef> {
    return listOf(
      current,
      SchoolYearRef(startYear = current.startYear - 1, endYear = current.startYear),
    ).distinctBy { it.id }
  }

  private fun currentSchoolYear(): SchoolYearRef {
    val now = LocalDate.now()
    return SchoolYearRef.current(now.year, now.monthValue)
  }

  private fun decodeSchoolYearToken(value: String): SchoolYearRef? {
    val pieces = value.split("-")
    if (pieces.size != 2) return null
    val start = pieces[0].toIntOrNull() ?: return null
    val end = pieces[1].toIntOrNull() ?: return null
    return SchoolYearRef(startYear = start, endYear = end)
  }
}

data class StoredCredentials(
  val username: String,
  val password: String,
)

interface SessionStorage {
  fun readCurrentSession(): UserSession?
  fun writeSession(session: UserSession)
  fun readStoredCredentials(): StoredCredentials?
  fun writeCredentials(username: String, password: String)
  fun clear()
}

@Singleton
class SessionStore(@ApplicationContext context: Context) : SessionStorage {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val sessionPayloadKey = "session_payload"
  private val usernameKey = "credential_username"
  private val passwordKey = "credential_password"
  private val sharedPreferences = try {
    EncryptedSharedPreferences.create(
      context,
      "classeviva_secure_session",
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  } catch (e: Exception) {
    context.deleteSharedPreferences("classeviva_secure_session")
    EncryptedSharedPreferences.create(
      context,
      "classeviva_secure_session",
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  private val sessionFlow = MutableStateFlow(readCurrentSession())

  init {
    migrateLegacySessionPayload()
  }

  val session: StateFlow<UserSession?> = sessionFlow

  override fun readCurrentSession(): UserSession? {
    val raw = sharedPreferences.getString(sessionPayloadKey, null) ?: return null
    return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
  }

  fun writeSessionSilently(session: UserSession) {
    sharedPreferences.edit().putString(sessionPayloadKey, json.encodeToString(session)).apply()
  }

  override fun writeSession(session: UserSession) {
    writeSessionSilently(session)
    sessionFlow.value = session
  }

  override fun readStoredCredentials(): StoredCredentials? {
    val username = sharedPreferences.getString(usernameKey, null)?.trim().orEmpty()
    val password = sharedPreferences.getString(passwordKey, null).orEmpty()
    if (username.isBlank() || password.isBlank()) return null
    return StoredCredentials(username = username, password = password)
  }

  override fun writeCredentials(username: String, password: String) {
    sharedPreferences.edit()
      .putString(usernameKey, username.trim())
      .putString(passwordKey, password)
      .apply()
  }

  override fun clear() {
    sharedPreferences.edit().clear().apply()
    sessionFlow.value = null
  }

  private fun migrateLegacySessionPayload() {
    if (readStoredCredentials() != null) return
    val raw = sharedPreferences.getString(sessionPayloadKey, null) ?: return
    val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
    val username = root["username"]?.jsonPrimitive?.content?.trim().orEmpty()
    val password = root["password"]?.jsonPrimitive?.content.orEmpty()
    if (username.isBlank() || password.isBlank()) return
    writeCredentials(username = username, password = password)
    readCurrentSession()?.let(::writeSession)
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
  fun provideSchoolYearStore(@ApplicationContext context: Context): SchoolYearStore = SchoolYearStore(context)

  @Provides
  @Singleton
  fun provideSessionStore(@ApplicationContext context: Context): SessionStore = SessionStore(context)

  @Provides
  @Singleton
  fun provideSessionStorage(sessionStore: SessionStore): SessionStorage = sessionStore

  @Provides
  @Singleton
  fun provideSchoolYearRepository(store: SchoolYearStore): SchoolYearRepository = store
}
