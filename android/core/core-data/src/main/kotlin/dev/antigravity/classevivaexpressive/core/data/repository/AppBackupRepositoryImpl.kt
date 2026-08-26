package dev.antigravity.classevivaexpressive.core.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.antigravity.classevivaexpressive.core.database.database.BackupTransactionRunner
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryDao
import dev.antigravity.classevivaexpressive.core.database.database.ChangeHistoryEntity
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventDao
import dev.antigravity.classevivaexpressive.core.database.database.CustomEventEntity
import dev.antigravity.classevivaexpressive.core.database.database.GradeDao
import dev.antigravity.classevivaexpressive.core.database.database.GradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeDao
import dev.antigravity.classevivaexpressive.core.database.database.SeenGradeEntity
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheDao
import dev.antigravity.classevivaexpressive.core.database.database.SnapshotCacheEntity
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

/**
 * 1: impostazioni, orari, eventi, voti visti, obiettivi, punteggi.
 * 2: i voti veri, la loro storia, e i periodi e le materie che servono a leggerli.
 */
private const val BackupVersion = 2

/**
 * Un voto dentro il file di backup.
 *
 * **Non e' [GradeEntity], ed e' il punto.** Il resto di questo payload serializza le entity di Room
 * cosi' come sono, il che significa che il formato del file *e'* lo schema del database: il giorno
 * in cui qualcuno aggiunge una colonna non-nullable, ogni backup fatto prima diventa illeggibile, e
 * se ne accorge l'utente nel momento in cui sta cercando di recuperare i propri dati. Con un tipo
 * separato quella colonna non compila finche' non le si da' un valore in [toEntity]: la domanda la
 * fa il compilatore, non il decoder.
 *
 * Ogni campo che non sia identita' ha un default, cosi' un file scritto da una versione futura che
 * ne omette qualcuno continua a decodificare.
 */
@Serializable
internal data class GradeBackupEntry(
  val id: String,
  val studentId: String,
  val schoolYearId: String,
  val subject: String,
  val valueLabel: String,
  val date: String,
  val type: String,
  val numericValue: Double? = null,
  val description: String? = null,
  val weight: Double? = null,
  val notes: String? = null,
  val period: String? = null,
  val periodCode: String? = null,
  val teacher: String? = null,
  val color: String? = null,
  /** Quando il voto e' comparso la prima volta: e' cio' che distingue "gia' visto" da "nuovo". */
  val firstSeenAtMs: Long? = null,
)

/**
 * Una versione precedente di un voto.
 *
 * `itemKind` non c'e' di proposito: la lista si chiama `gradeHistory` e contiene storia di voti.
 * Memorizzare una costante in ogni riga e' memorizzare rumore; al restore si riscrive.
 */
@Serializable
internal data class GradeHistoryBackupEntry(
  val id: String,
  val studentId: String,
  val schoolYearId: String,
  val itemId: String,
  val recordedAtEpochMillis: Long,
  val payload: String,
)

/**
 * I periodi e le materie di un anno.
 *
 * Sono metadati dei voti, non un'altra funzione dell'app: la schermata Voti filtra ed etichetta per
 * periodo, e una lista dell'anno scorso senza i suoi periodi e' una lista che non si riesce a
 * leggere.
 *
 * Porta la tripla e il payload grezzo invece della riga di cache, cosi' il file non dipende dal
 * *formato della chiave*, che e' un dettaglio interno gia' cambiato una volta.
 */
@Serializable
internal data class GradeMetadataBackupEntry(
  val studentId: String,
  val schoolYearId: String,
  val section: String,
  val payload: String,
  val updatedAtEpochMillis: Long = 0L,
)

@Serializable
private data class AppBackupPayload(
  val version: Int = BackupVersion,
  val exportedAtEpochMillis: Long = 0L,
  val settings: AppSettings = AppSettings(),
  val selectedSchoolYearId: String? = null,
  val timetableTemplates: Map<String, TimetableTemplate> = emptyMap(),
  val customEvents: List<CustomEventEntity> = emptyList(),
  val seenGrades: List<SeenGradeEntity> = emptyList(),
  val subjectGoals: List<SubjectGoalEntity> = emptyList(),
  val scoreSnapshots: List<StudentScoreSnapshotEntity> = emptyList(),
  // Dalla versione 2. Ogni campo ha un default, quindi un file v1 decodifica qui dentro con le
  // liste vuote: non serve un parser separato per la versione vecchia.
  val exportedByStudentId: String? = null,
  val grades: List<GradeBackupEntry> = emptyList(),
  val gradeHistory: List<GradeHistoryBackupEntry> = emptyList(),
  val gradeMetadata: List<GradeMetadataBackupEntry> = emptyList(),
)

@Singleton
class DefaultAppBackupRepository @Inject constructor(
  private val json: Json,
  private val settingsStore: SettingsStore,
  private val schoolYearStore: SchoolYearStore,
  private val timetableTemplateStore: TimetableTemplateStore,
  private val customEventDao: CustomEventDao,
  private val seenGradeDao: SeenGradeDao,
  private val subjectGoalDao: SubjectGoalDao,
  private val studentScoreDao: StudentScoreDao,
  private val gradeDao: GradeDao,
  private val changeHistoryDao: ChangeHistoryDao,
  private val snapshotCacheDao: SnapshotCacheDao,
  private val transactionRunner: BackupTransactionRunner,
) : AppBackupRepository {

  /**
   * Il backup, come stringa.
   *
   * Il payload si costruisce intero in memoria. Con due anni di voti sono qualche centinaio di
   * kilobyte e non e' un problema; se un giorno qualcuno arrivasse a qualche megabyte la risposta e'
   * `Json.encodeToStream` sull'`OutputStream` che la schermata ha gia' in mano — ed e' **questa
   * firma** a dover cambiare, non il resto.
   */
  override suspend fun exportBackup(): Result<String> = runCatching {
    val grades = gradeDao.getAll()
    json.encodeToString(
      AppBackupPayload(
        exportedAtEpochMillis = System.currentTimeMillis(),
        settings = settingsStore.readSettings(),
        selectedSchoolYearId = schoolYearStore.selectedSchoolYear().id,
        timetableTemplates = timetableTemplateStore.readAllTemplates(),
        customEvents = customEventDao.getAll(),
        seenGrades = seenGradeDao.getAll(),
        subjectGoals = subjectGoalDao.getAll(),
        scoreSnapshots = studentScoreDao.getAll(),
        exportedByStudentId = grades.firstOrNull()?.studentId,
        grades = grades.map(GradeEntity::toBackup),
        gradeHistory = changeHistoryDao.getAllByKind(HistoryKindGrade)
          .map(ChangeHistoryEntity::toGradeBackup),
        gradeMetadata = gradeMetadataFor(grades),
      ),
    )
  }

  override suspend fun importBackup(payload: String): Result<AppBackupImportSummary> = runCatching {
    val backup = json.decodeFromString<AppBackupPayload>(payload)
    require(backup.version in 1..BackupVersion) { "Backup creato con una versione non supportata." }

    // DataStore prima, e fuori dalla transazione: Room non lo sa annullare, e fingere che tutto sia
    // atomico sarebbe peggio che dirlo. Sono scritture idempotenti, quindi un secondo tentativo dopo
    // un errore piu' avanti non fa danni.
    settingsStore.writeSettings(backup.settings)
    backup.selectedSchoolYearId?.toSchoolYearRefOrNull()?.let { schoolYearStore.selectSchoolYear(it) }
    val mergedTemplates = timetableTemplateStore.readAllTemplates() + backup.timetableTemplates
    timetableTemplateStore.writeAllTemplates(mergedTemplates)

    val gradeEntities = backup.grades.map(GradeBackupEntry::toEntity)

    transactionRunner.inTransaction {
      customEventDao.upsertAll(backup.customEvents)
      seenGradeDao.upsertAll(backup.seenGrades)
      subjectGoalDao.upsertAll(backup.subjectGoals)
      studentScoreDao.upsertAll(backup.scoreSnapshots)
      gradeDao.upsertAll(gradeEntities)
      changeHistoryDao.upsertAll(backup.gradeHistory.map(GradeHistoryBackupEntry::toEntity))
      backup.gradeMetadata.forEach { metadata -> snapshotCacheDao.upsert(metadata.toEntity()) }
      rebuildGradesSnapshot(gradeEntities)
    }

    val ownerStudentId = backup.exportedByStudentId
    AppBackupImportSummary(
      settingsImported = true,
      timetableTemplates = backup.timetableTemplates.size,
      customEvents = backup.customEvents.size,
      seenGrades = backup.seenGrades.size,
      subjectGoals = backup.subjectGoals.size,
      scoreSnapshots = backup.scoreSnapshots.size,
      grades = backup.grades.size,
      gradeHistory = backup.gradeHistory.size,
      gradeSchoolYears = backup.grades.map { it.schoolYearId }.distinct().sorted(),
      // Le righe si scrivono comunque: ogni query e' gia' filtrata per studentId, quindi i voti di
      // un altro profilo sono invisibili e innocui, e un restore non deve mai buttare via dati. Ma
      // si contano, cosi' chi importa il backup di qualcun altro capisce perche' non li vede.
      skippedForeignStudentGrades = if (ownerStudentId == null) {
        0
      } else {
        backup.grades.count { it.studentId != ownerStudentId }
      },
    )
  }

  /** Periodi e materie di ogni anno che compare fra i voti esportati. */
  private suspend fun gradeMetadataFor(grades: List<GradeEntity>): List<GradeMetadataBackupEntry> {
    val wanted = grades
      .map { it.studentId to it.schoolYearId }
      .distinct()
      .flatMap { (studentId, yearId) ->
        val year = yearId.toSchoolYearRefOrNull() ?: return@flatMap emptyList()
        GradeMetadataSections.map { section ->
          MetadataRequest(
            studentId = studentId,
            schoolYearId = yearId,
            section = section,
            cacheKey = yearScopedCacheKey(studentId, section, year),
          )
        }
      }
    if (wanted.isEmpty()) return emptyList()
    val byKey = snapshotCacheDao.getByKeys(wanted.map { it.cacheKey }).associateBy { it.cacheKey }
    return wanted.mapNotNull { request ->
      val cached = byKey[request.cacheKey] ?: return@mapNotNull null
      GradeMetadataBackupEntry(
        studentId = request.studentId,
        schoolYearId = request.schoolYearId,
        section = request.section,
        payload = cached.payload,
        updatedAtEpochMillis = cached.updatedAtEpochMillis,
      )
    }
  }

  /**
   * Lo snapshot dei voti si **ricostruisce**, non si copia dal file.
   *
   * Il diff delle notifiche confronta lo snapshot di prima con quello di dopo la sincronizzazione.
   * Senza snapshot, la prima sincronizzazione dopo un restore troverebbe tutto nuovo e notificherebbe
   * ogni voto dell'anno; con quello salvato nel file, che puo' essere di settimane fa, notificherebbe
   * una finestra che dipende da quando e' stato fatto il backup. Sintetizzandolo da cio' che si e'
   * appena scritto, il "prima" combacia sempre con quello che l'utente ha davanti — e il file resta
   * la meta', perche' lo snapshot sarebbe la stessa lista una seconda volta.
   */
  private suspend fun rebuildGradesSnapshot(grades: List<GradeEntity>) {
    val now = System.currentTimeMillis()
    grades.groupBy { it.studentId to it.schoolYearId }.forEach { (key, entities) ->
      val (studentId, yearId) = key
      val year = yearId.toSchoolYearRefOrNull() ?: return@forEach
      snapshotCacheDao.upsert(
        SnapshotCacheEntity(
          cacheKey = yearScopedCacheKey(studentId, GradesSection, year),
          payload = json.encodeToString(entities.map { it.toGrade() }),
          updatedAtEpochMillis = now,
        ),
      )
    }
  }

  private fun GradeMetadataBackupEntry.toEntity(): SnapshotCacheEntity {
    val year = schoolYearId.toSchoolYearRefOrNull()
    val key = if (year != null) {
      yearScopedCacheKey(studentId, section, year)
    } else {
      "$studentId::$schoolYearId::$section"
    }
    return SnapshotCacheEntity(
      cacheKey = key,
      payload = payload,
      updatedAtEpochMillis = updatedAtEpochMillis,
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

  private data class MetadataRequest(
    val studentId: String,
    val schoolYearId: String,
    val section: String,
    val cacheKey: String,
  )
}

private val GradeMetadataSections = listOf(PeriodsSection, SubjectsSection)

internal fun GradeEntity.toBackup(): GradeBackupEntry = GradeBackupEntry(
  id = id,
  studentId = studentId,
  schoolYearId = schoolYearId,
  subject = subject,
  valueLabel = valueLabel,
  date = date,
  type = type,
  numericValue = numericValue,
  description = description,
  weight = weight,
  notes = notes,
  period = period,
  periodCode = periodCode,
  teacher = teacher,
  color = color,
  firstSeenAtMs = firstSeenAtMs,
)

internal fun GradeBackupEntry.toEntity(): GradeEntity = GradeEntity(
  id = id,
  studentId = studentId,
  schoolYearId = schoolYearId,
  subject = subject,
  valueLabel = valueLabel,
  numericValue = numericValue,
  description = description,
  date = date,
  type = type,
  weight = weight,
  notes = notes,
  period = period,
  periodCode = periodCode,
  teacher = teacher,
  color = color,
  firstSeenAtMs = firstSeenAtMs,
)

internal fun ChangeHistoryEntity.toGradeBackup(): GradeHistoryBackupEntry = GradeHistoryBackupEntry(
  id = id,
  studentId = studentId,
  schoolYearId = schoolYearId,
  itemId = itemId,
  recordedAtEpochMillis = recordedAtEpochMillis,
  payload = payload,
)

internal fun GradeHistoryBackupEntry.toEntity(): ChangeHistoryEntity = ChangeHistoryEntity(
  id = id,
  studentId = studentId,
  schoolYearId = schoolYearId,
  itemKind = HistoryKindGrade,
  itemId = itemId,
  recordedAtEpochMillis = recordedAtEpochMillis,
  payload = payload,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBackupModule {
  @Binds
  abstract fun bindAppBackupRepository(impl: DefaultAppBackupRepository): AppBackupRepository
}
