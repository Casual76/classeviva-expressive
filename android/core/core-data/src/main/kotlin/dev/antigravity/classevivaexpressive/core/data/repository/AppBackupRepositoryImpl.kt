package dev.antigravity.classevivaexpressive.core.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventDao
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventEntity
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeDao
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SimulatedGradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SimulationDao
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreDao
import dev.antigravity.classevivaexpressive.core.database.database.StudentScoreSnapshotEntity
import dev.antigravity.classevivaexpressive.core.database.database.SubjectGoalDao
import dev.antigravity.classevivaexpressive.core.database.database.SubjectGoalEntity
import dev.antigravity.classevivaexpressive.core.datastore.SchoolYearStore
import dev.antigravity.classevivaexpressive.core.datastore.SettingsStore
import dev.antigravity.classevivaexpressive.core.datastore.TimetableTemplateStore
import dev.antigravity.classevivaexpressive.core.domain.model.AppBackupImportSummary
import dev.antigravity.classevivaexpressive.core.domain.model.AppBackupRepository
import dev.antigravity.classevivaexpressive.core.domain.model.AppSettings
import dev.antigravity.classevivaexpressive.core.domain.model.SchoolYearRef
import dev.antigravity.classevivaexpressive.core.domain.model.TimetableTemplate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val BackupVersion = 1

@Serializable
private data class AppBackupPayload(
  val version: Int = BackupVersion,
  val exportedAtEpochMillis: Long = 0L,
  val settings: AppSettings = AppSettings(),
  val selectedSchoolYearId: String? = null,
  val timetableTemplates: Map<String, TimetableTemplate> = emptyMap(),
  val customEvents: List<CustomEventEntity> = emptyList(),
  val simulatedGrades: List<SimulatedGradeEntity> = emptyList(),
  val seenGrades: List<SeenGradeEntity> = emptyList(),
  val subjectGoals: List<SubjectGoalEntity> = emptyList(),
  val scoreSnapshots: List<StudentScoreSnapshotEntity> = emptyList(),
)

@Singleton
class DefaultAppBackupRepository @Inject constructor(
  private val json: Json,
  private val settingsStore: SettingsStore,
  private val schoolYearStore: SchoolYearStore,
  private val timetableTemplateStore: TimetableTemplateStore,
  private val customEventDao: CustomEventDao,
  private val simulationDao: SimulationDao,
  private val seenGradeDao: SeenGradeDao,
  private val subjectGoalDao: SubjectGoalDao,
  private val studentScoreDao: StudentScoreDao,
) : AppBackupRepository {
  override suspend fun exportBackup(): Result<String> = runCatching {
    json.encodeToString(
      AppBackupPayload(
        exportedAtEpochMillis = System.currentTimeMillis(),
        settings = settingsStore.readSettings(),
        selectedSchoolYearId = schoolYearStore.selectedSchoolYear().id,
        timetableTemplates = timetableTemplateStore.readAllTemplates(),
        customEvents = customEventDao.getAll(),
        simulatedGrades = simulationDao.getAll(),
        seenGrades = seenGradeDao.getAll(),
        subjectGoals = subjectGoalDao.getAll(),
        scoreSnapshots = studentScoreDao.getAll(),
      ),
    )
  }

  override suspend fun importBackup(payload: String): Result<AppBackupImportSummary> = runCatching {
    val backup = json.decodeFromString<AppBackupPayload>(payload)
    require(backup.version <= BackupVersion) { "Backup creato con una versione non supportata." }

    settingsStore.writeSettings(backup.settings)
    backup.selectedSchoolYearId?.toSchoolYearRefOrNull()?.let { schoolYearStore.selectSchoolYear(it) }

    val mergedTemplates = timetableTemplateStore.readAllTemplates() + backup.timetableTemplates
    timetableTemplateStore.writeAllTemplates(mergedTemplates)

    customEventDao.upsertAll(backup.customEvents)
    simulationDao.upsertAll(backup.simulatedGrades)
    seenGradeDao.upsertAll(backup.seenGrades)
    subjectGoalDao.upsertAll(backup.subjectGoals)
    studentScoreDao.upsertAll(backup.scoreSnapshots)

    AppBackupImportSummary(
      settingsImported = true,
      timetableTemplates = backup.timetableTemplates.size,
      customEvents = backup.customEvents.size,
      simulatedGrades = backup.simulatedGrades.size,
      seenGrades = backup.seenGrades.size,
      subjectGoals = backup.subjectGoals.size,
      scoreSnapshots = backup.scoreSnapshots.size,
    )
  }

  private fun String.toSchoolYearRefOrNull(): SchoolYearRef? {
    val parts = split("-")
    if (parts.size != 2) return null
    return SchoolYearRef(
      startYear = parts[0].toIntOrNull() ?: return null,
      endYear = parts[1].toIntOrNull() ?: return null,
    )
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBackupModule {
  @Binds
  abstract fun bindAppBackupRepository(impl: DefaultAppBackupRepository): AppBackupRepository
}
