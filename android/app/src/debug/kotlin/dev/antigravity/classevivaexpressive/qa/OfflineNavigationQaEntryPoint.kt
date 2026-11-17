package dev.antigravity.classevivaexpressive.qa

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SessionStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore

/** Debug-only access for instrumented, isolated offline navigation fixtures. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineNavigationQaEntryPoint {
  fun sessionStore(): SessionStore
  fun settingsStore(): SettingsStore
  fun schoolYearStore(): SchoolYearStore
  fun gradeDao(): GradeDao
}
