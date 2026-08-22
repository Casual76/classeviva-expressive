package dev.antigravity.classevivaexpressive.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
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
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearFallbackEvent
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRepository
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearSelectionPolicy
import dev.antigravity.classevivaexpressive.core.domain.model.ThemeMode
import dev.antigravity.classevivaexpressive.core.domain.model.TemplateSlot
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import dev.antigravity.classevivaexpressive.core.domain.model.UserSession
import java.time.LocalDate
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
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
private val SettingsNotificationsGradesKey = booleanPreferencesKey("notifications_grades")
private val SettingsNotificationsAgendaKey = booleanPreferencesKey("notifications_agenda")
private val SettingsNotificationsNotesKey = booleanPreferencesKey("notifications_notes")
private val SettingsNotificationsTestKey = booleanPreferencesKey("notifications_test")
private val SettingsNotificationsLiveTimetableKey = booleanPreferencesKey("notifications_live_timetable")
private val SettingsPeriodicSyncKey = booleanPreferencesKey("periodic_sync")
private val SettingsNetworkConfigKey = stringPreferencesKey("network_config")
private val SettingsIgnoredStableUpdateVersionKey = stringPreferencesKey("ignored_stable_update_version")
private val SelectedSchoolYearKey = stringPreferencesKey("selected_school_year")
private val SchoolYearFallbackEventsKey = stringPreferencesKey("school_year_fallback_events")
private val TimetableTemplatesKey = stringPreferencesKey("timetable_templates")

@Singleton
class SettingsStore internal constructor(
  private val dataStore: DataStore<Preferences>,
) {
  constructor(@ApplicationContext context: Context) : this(
    dataStore = PreferenceDataStoreFactory.create(
      produceFile = { context.preferencesDataStoreFile("classeviva_settings.preferences_pb") },
    ),
  )

  private val json = Json { ignoreUnknownKeys = true }

  val settings: Flow<AppSettings> = dataStore.data.map(::decodeSettings)

  suspend fun update(transform: (AppSettings) -> AppSettings) {
    dataStore.edit { prefs ->
      // Decode and transform inside DataStore's serialized edit transaction. Computing `next`
      // before entering edit allowed two unrelated rapid changes to overwrite one another.
      prefs.replaceWith(transform(decodeSettings(prefs)))
    }
  }

  private fun decodeSettings(prefs: Preferences): AppSettings {
    val networkConfig = prefs[SettingsNetworkConfigKey]?.let {
      runCatching {
        json.decodeFromString<dev.antigravity.classevivaexpressive.core.domain.model.NetworkConfig>(it)
      }.getOrNull()
    } ?: dev.antigravity.classevivaexpressive.core.domain.model.NetworkConfig()

    return AppSettings(
      themeMode = prefs[SettingsThemeModeKey]
        ?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
        ?: ThemeMode.SYSTEM,
      accentMode = prefs[SettingsAccentModeKey]
        ?.let { stored -> runCatching { AccentMode.valueOf(stored) }.getOrNull() }
        ?: AccentMode.BRAND,
      customAccentName = prefs[SettingsCustomAccentKey] ?: "expressive",
      dynamicColorEnabled = prefs[SettingsDynamicColorKey] ?: true,
      amoledEnabled = prefs[SettingsAmoledKey] ?: false,
      notificationPreferences = NotificationPreferences(
        enabled = prefs[SettingsNotificationsEnabledKey] ?: true,
        homework = prefs[SettingsNotificationsHomeworkKey] ?: true,
        communications = prefs[SettingsNotificationsCommunicationsKey] ?: true,
        absences = prefs[SettingsNotificationsAbsencesKey] ?: true,
        grades = prefs[SettingsNotificationsGradesKey] ?: true,
        agenda = prefs[SettingsNotificationsAgendaKey] ?: true,
        notes = prefs[SettingsNotificationsNotesKey] ?: true,
        test = prefs[SettingsNotificationsTestKey] ?: true,
        liveTimetable = prefs[SettingsNotificationsLiveTimetableKey] ?: true,
      ),
      periodicSyncEnabled = prefs[SettingsPeriodicSyncKey] ?: true,
      networkConfig = networkConfig,
      ignoredStableUpdateVersion = prefs[SettingsIgnoredStableUpdateVersionKey] ?: "",
    )
  }

  private fun MutablePreferences.replaceWith(next: AppSettings) {
    this[SettingsThemeModeKey] = next.themeMode.name
    this[SettingsAccentModeKey] = next.accentMode.name
    this[SettingsCustomAccentKey] = next.customAccentName
    this[SettingsDynamicColorKey] = next.dynamicColorEnabled
    this[SettingsAmoledKey] = next.amoledEnabled
    this[SettingsNotificationsEnabledKey] = next.notificationPreferences.enabled
    this[SettingsNotificationsHomeworkKey] = next.notificationPreferences.homework
    this[SettingsNotificationsCommunicationsKey] = next.notificationPreferences.communications
    this[SettingsNotificationsAbsencesKey] = next.notificationPreferences.absences
    this[SettingsNotificationsGradesKey] = next.notificationPreferences.grades
    this[SettingsNotificationsAgendaKey] = next.notificationPreferences.agenda
    this[SettingsNotificationsNotesKey] = next.notificationPreferences.notes
    this[SettingsNotificationsTestKey] = next.notificationPreferences.test
    this[SettingsNotificationsLiveTimetableKey] = next.notificationPreferences.liveTimetable
    this[SettingsPeriodicSyncKey] = next.periodicSyncEnabled
    this[SettingsNetworkConfigKey] = json.encodeToString(next.networkConfig)
    this[SettingsIgnoredStableUpdateVersionKey] = next.ignoredStableUpdateVersion
  }

  suspend fun readSettings(): AppSettings = settings.first()

  suspend fun writeSettings(settings: AppSettings) {
    update { settings }
  }
}

@Singleton
class SchoolYearStore internal constructor(
  private val dataStore: DataStore<Preferences>,
  private val todayProvider: () -> LocalDate,
  private val nowEpochMillisProvider: () -> Long,
) : SchoolYearRepository {
  constructor(@ApplicationContext context: Context) : this(
    dataStore = PreferenceDataStoreFactory.create(
      produceFile = { context.preferencesDataStoreFile("classeviva_school_year.preferences_pb") },
    ),
    todayProvider = LocalDate::now,
    nowEpochMillisProvider = System::currentTimeMillis,
  )

  private val json = Json { ignoreUnknownKeys = true }

  override fun observeSelectedSchoolYear(): Flow<SchoolYearRef> {
    return dataStore.data.map { prefs ->
      selectedSchoolYear(prefs, todayProvider())
    }.distinctUntilChanged()
  }

  override fun observeAvailableSchoolYears(): Flow<List<SchoolYearRef>> = flow {
    // Re-evaluate the date for every collection instead of freezing it for the process lifetime.
    emit(availableYears(todayProvider()))
  }

  override fun observeFallbackEvents(): Flow<SchoolYearFallbackEvent> = flow {
    val emittedIds = mutableSetOf<String>()
    dataStore.data.map(::decodeFallbackEvents).collect { pending ->
      pending.forEach { event ->
        if (emittedIds.add(event.id)) emit(event)
      }
    }
  }

  override suspend fun selectSchoolYear(year: SchoolYearRef) {
    dataStore.edit { prefs ->
      prefs[SelectedSchoolYearKey] = year.id
    }
  }

  override suspend fun selectAutomaticFallback(requested: SchoolYearRef): SchoolYearFallbackEvent? {
    var applied: SchoolYearFallbackEvent? = null
    dataStore.edit { prefs ->
      val today = todayProvider()
      val currentSelection = selectedSchoolYear(prefs, today)
      if (currentSelection.id != requested.id) return@edit

      val fallback = SchoolYearSelectionPolicy.automaticFallback(
        requested = requested,
        available = availableYears(today),
      ) ?: return@edit
      val event = SchoolYearFallbackEvent(
        id = "school-year:${requested.id}:${fallback.id}:${nowEpochMillisProvider()}",
        requested = requested,
        selected = fallback,
      )
      val pending = decodeFallbackEvents(prefs)
      prefs[SelectedSchoolYearKey] = fallback.id
      prefs[SchoolYearFallbackEventsKey] = json.encodeToString((pending + event).takeLast(MaxPendingFallbackEvents))
      applied = event
    }
    return applied
  }

  override suspend fun acknowledgeFallbackEvent(id: String) {
    dataStore.edit { prefs ->
      val remaining = decodeFallbackEvents(prefs).filterNot { it.id == id }
      if (remaining.isEmpty()) {
        prefs.remove(SchoolYearFallbackEventsKey)
      } else {
        prefs[SchoolYearFallbackEventsKey] = json.encodeToString(remaining)
      }
    }
  }

  suspend fun selectedSchoolYear(): SchoolYearRef = observeSelectedSchoolYear().first()

  fun currentSchoolYearRef(): SchoolYearRef = SchoolYearSelectionPolicy.current(todayProvider())

  /**
   * The years a student can switch between.
   *
   * The upcoming year is offered only during the summer. From September it becomes the current year,
   * so adding yet another year would expose a school year that is almost twelve months away.
   */
  private fun availableYears(today: LocalDate): List<SchoolYearRef> = SchoolYearSelectionPolicy.available(today)

  private fun selectedSchoolYear(prefs: Preferences, today: LocalDate): SchoolYearRef {
    val current = SchoolYearSelectionPolicy.current(today)
    val selected = prefs[SelectedSchoolYearKey]?.let(::decodeSchoolYearToken) ?: current
    return availableYears(today).firstOrNull { it.id == selected.id } ?: current
  }

  private fun decodeFallbackEvents(prefs: Preferences): List<SchoolYearFallbackEvent> {
    val encoded = prefs[SchoolYearFallbackEventsKey] ?: return emptyList()
    return runCatching { json.decodeFromString<List<SchoolYearFallbackEvent>>(encoded) }.getOrDefault(emptyList())
  }

  private fun decodeSchoolYearToken(value: String): SchoolYearRef? {
    val pieces = value.split("-")
    if (pieces.size != 2) return null
    val start = pieces[0].toIntOrNull() ?: return null
    val end = pieces[1].toIntOrNull() ?: return null
    return SchoolYearRef(startYear = start, endYear = end)
  }

  private companion object {
    const val MaxPendingFallbackEvents = 8
  }
}

@Serializable
private data class StoredTimetableTemplates(
  val templates: Map<String, TimetableTemplate> = emptyMap(),
)

@Singleton
class TimetableTemplateStore(@ApplicationContext context: Context) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val dataStore = PreferenceDataStoreFactory.create(
    produceFile = { context.preferencesDataStoreFile("classeviva_timetable.preferences_pb") },
  )

  fun observeTemplate(schoolYearId: String): Flow<TimetableTemplate> {
    return dataStore.data.map { prefs ->
      val stored = prefs[TimetableTemplatesKey]?.let {
        runCatching { json.decodeFromString<StoredTimetableTemplates>(it) }.getOrNull()
      } ?: StoredTimetableTemplates()
      stored.templates[schoolYearId] ?: TimetableTemplate()
    }
  }

  suspend fun writeTemplate(schoolYearId: String, template: TimetableTemplate) {
    dataStore.edit { prefs ->
      val stored = prefs[TimetableTemplatesKey]?.let {
        runCatching { json.decodeFromString<StoredTimetableTemplates>(it) }.getOrNull()
      } ?: StoredTimetableTemplates()
      prefs[TimetableTemplatesKey] = json.encodeToString(
        stored.copy(
          templates = stored.templates + (schoolYearId to template),
        ),
      )
    }
  }

  suspend fun writeOverride(schoolYearId: String, fingerprint: String, slot: TemplateSlot) {
    dataStore.edit { prefs ->
      val stored = prefs[TimetableTemplatesKey]?.let {
        runCatching { json.decodeFromString<StoredTimetableTemplates>(it) }.getOrNull()
      } ?: StoredTimetableTemplates()
      val existing = stored.templates[schoolYearId] ?: TimetableTemplate()
      val updated = existing.copy(manualOverrides = existing.manualOverrides + (fingerprint to slot))
      prefs[TimetableTemplatesKey] = json.encodeToString(
        stored.copy(templates = stored.templates + (schoolYearId to updated)),
      )
    }
  }

  suspend fun deleteOverride(schoolYearId: String, fingerprint: String) {
    dataStore.edit { prefs ->
      val stored = prefs[TimetableTemplatesKey]?.let {
        runCatching { json.decodeFromString<StoredTimetableTemplates>(it) }.getOrNull()
      } ?: return@edit
      val existing = stored.templates[schoolYearId] ?: return@edit
      val updated = existing.copy(manualOverrides = existing.manualOverrides - fingerprint)
      prefs[TimetableTemplatesKey] = json.encodeToString(
        stored.copy(templates = stored.templates + (schoolYearId to updated)),
      )
    }
  }

  suspend fun readAllTemplates(): Map<String, TimetableTemplate> {
    val prefs = dataStore.data.first()
    val stored = prefs[TimetableTemplatesKey]?.let {
      runCatching { json.decodeFromString<StoredTimetableTemplates>(it) }.getOrNull()
    } ?: StoredTimetableTemplates()
    return stored.templates
  }

  suspend fun writeAllTemplates(templates: Map<String, TimetableTemplate>) {
    dataStore.edit { prefs ->
      prefs[TimetableTemplatesKey] = json.encodeToString(StoredTimetableTemplates(templates = templates))
    }
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
  fun provideTimetableTemplateStore(@ApplicationContext context: Context): TimetableTemplateStore =
    TimetableTemplateStore(context)

  @Provides
  @Singleton
  fun provideSessionStorage(sessionStore: SessionStore): SessionStorage = sessionStore

}
