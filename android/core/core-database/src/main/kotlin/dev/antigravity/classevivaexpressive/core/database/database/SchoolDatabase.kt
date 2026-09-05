package dev.antigravity.classevivaexpressive.core.database.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "snapshot_cache")
data class SnapshotCacheEntity(
  @PrimaryKey val cacheKey: String,
  val payload: String,
  val updatedAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "sync_metadata", primaryKeys = ["studentId", "schoolYearId", "section"])
data class SyncMetadataEntity(
  val studentId: String,
  val schoolYearId: String,
  val section: String,
  val lastAttemptAtEpochMillis: Long,
  val lastSuccessAtEpochMillis: Long?,
  val lastError: String?,
)

@Serializable
@Entity(tableName = "custom_events")
data class CustomEventEntity(
  @PrimaryKey val id: String,
  val payload: String,
  val date: String,
  val time: String?,
  val createdAt: String? = null,
)

@Serializable
@Entity(tableName = "student_score_snapshots")
data class StudentScoreSnapshotEntity(
  @PrimaryKey val id: String,
  val payload: String,
  val createdAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "download_records")
data class DownloadRecordEntity(
  @PrimaryKey val id: String,
  val sourceUrl: String,
  val displayName: String,
  val mimeType: String?,
  val status: String,
  val localUri: String? = null,
  val updatedAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "seen_grades")
data class SeenGradeEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val gradeId: String,
  val seenAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "subject_goals")
data class SubjectGoalEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val subject: String,
  val periodCode: String?,
  val targetAverage: Double,
  val updatedAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "change_history")
data class ChangeHistoryEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val schoolYearId: String,
  val itemKind: String,
  val itemId: String,
  val recordedAtEpochMillis: Long,
  val payload: String,
)

@Serializable
@Entity(tableName = "grades")
data class GradeEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val schoolYearId: String,
  val subject: String,
  val valueLabel: String,
  val numericValue: Double?,
  val description: String?,
  val date: String,
  val type: String,
  val weight: Double?,
  val notes: String?,
  val period: String?,
  val periodCode: String?,
  val teacher: String?,
  val color: String?,
  val firstSeenAtMs: Long? = null,
)

@Serializable
@Entity(tableName = "agenda_items")
data class AgendaItemEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val schoolYearId: String,
  val title: String,
  val subtitle: String,
  val date: String,
  val time: String?,
  val detail: String?,
  val subject: String?,
  val teacher: String? = null,
  val category: String,
  val sharePayload: String?,
  val createdAt: String? = null,
  val firstSeenAtMs: Long? = null,
)

@Serializable
@Entity(tableName = "absences")
data class AbsenceEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val schoolYearId: String,
  val date: String,
  val type: String,
  val hours: Int? = null,
  val justified: Boolean,
  val canJustify: Boolean,
  val justificationDate: String?,
  val justificationReason: String?,
  val justifyUrl: String?,
  val detailUrl: String?,
)

@Serializable
@Entity(tableName = "communications")
data class CommunicationEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val schoolYearId: String,
  val pubId: String,
  val evtCode: String,
  val title: String,
  val contentPreview: String,
  val sender: String,
  val date: String,
  val read: Boolean,
  val attachments: String, // JSON
  val category: String?,
  val needsAck: Boolean,
  val needsReply: Boolean,
  val needsJoin: Boolean,
  val needsFile: Boolean,
  val actions: String, // JSON
  val noticeboardAttachments: String, // JSON
  val capabilityState: String, // JSON
)

@Serializable
@Entity(tableName = "attachment_cache")
data class AttachmentCacheEntity(
  @PrimaryKey val urlKey: String,
  val sourceUrl: String,
  val localPath: String,
  val fileName: String,
  val mimeType: String?,
  val downloadedAtMs: Long,
  val lastAccessedMs: Long,
)

@Serializable
@Entity(tableName = "materials", primaryKeys = ["studentId", "schoolYearId", "id"])
data class MaterialEntity(
  val id: String,
  val studentId: String,
  val schoolYearId: String,
  val teacherId: String,
  val teacherName: String,
  val folderId: String,
  val folderName: String,
  val title: String,
  val objectId: String,
  val objectType: String,
  val sharedAt: String,
  val capabilityState: String, // JSON
  val attachments: String, // JSON
)

@Serializable
@Entity(tableName = "documents", primaryKeys = ["studentId", "schoolYearId", "id"])
data class DocumentEntity(
  val id: String,
  val studentId: String,
  val schoolYearId: String,
  val title: String,
  val detail: String,
  val kind: String,
  val remoteHash: String?,
  val restReadUrl: String?,
  val portalViewUrl: String?,
  val portalConfirmUrl: String?,
  val viewUrl: String?,
  val confirmUrl: String?,
  val capabilityState: String, // JSON
)

@Serializable
@Entity(tableName = "read_notes", primaryKeys = ["studentId", "noteId"])
data class ReadNoteEntity(
  val studentId: String,
  val noteId: String,
  val readAtMs: Long,
)

@Dao
interface SnapshotCacheDao {
  @Query("SELECT * FROM snapshot_cache WHERE cacheKey = :key LIMIT 1")
  fun observeByKey(key: String): Flow<SnapshotCacheEntity?>

  @Query("SELECT * FROM snapshot_cache WHERE cacheKey = :key LIMIT 1")
  suspend fun getByKey(key: String): SnapshotCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SnapshotCacheEntity)

  @Query("DELETE FROM snapshot_cache WHERE cacheKey LIKE :prefix || '%'")
  suspend fun deleteByPrefix(prefix: String)

  /**
   * Le voci di cache richieste, e solo quelle.
   *
   * Non c'e' un `getAll()` apposta: questa tabella tiene anche materiali, documenti e bacheca, che
   * possono essere megabyte. Chi fa un backup dei voti chiede le chiavi che gli servono.
   */
  @Query("SELECT * FROM snapshot_cache WHERE cacheKey IN (:keys)")
  suspend fun getByKeys(keys: List<String>): List<SnapshotCacheEntity>
}

@Dao
interface SyncMetadataDao {
  @Query("SELECT * FROM sync_metadata WHERE studentId = :studentId AND schoolYearId = :schoolYearId AND section = :section LIMIT 1")
  fun observe(studentId: String, schoolYearId: String, section: String): Flow<SyncMetadataEntity?>

  @Query("SELECT * FROM sync_metadata WHERE studentId = :studentId AND schoolYearId = :schoolYearId AND section = :section LIMIT 1")
  suspend fun get(studentId: String, schoolYearId: String, section: String): SyncMetadataEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SyncMetadataEntity)

  @Query("DELETE FROM sync_metadata WHERE studentId = :studentId")
  suspend fun deleteByStudent(studentId: String)
}

@Dao
interface CustomEventDao {
  @Query("SELECT * FROM custom_events ORDER BY date ASC, time ASC")
  fun observeAll(): Flow<List<CustomEventEntity>>

  @Query("SELECT * FROM custom_events ORDER BY date ASC, time ASC")
  suspend fun getAll(): List<CustomEventEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CustomEventEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<CustomEventEntity>)

  @Query("DELETE FROM custom_events WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface StudentScoreDao {
  @Query("SELECT * FROM student_score_snapshots ORDER BY createdAtEpochMillis DESC")
  fun observeAll(): Flow<List<StudentScoreSnapshotEntity>>

  @Query("SELECT * FROM student_score_snapshots ORDER BY createdAtEpochMillis DESC")
  suspend fun getAll(): List<StudentScoreSnapshotEntity>

  @Query("SELECT * FROM student_score_snapshots ORDER BY createdAtEpochMillis DESC LIMIT 1")
  fun observeLatest(): Flow<StudentScoreSnapshotEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: StudentScoreSnapshotEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<StudentScoreSnapshotEntity>)
}

@Dao
interface DownloadRecordDao {
  @Query("SELECT * FROM download_records ORDER BY updatedAtEpochMillis DESC")
  fun observeAll(): Flow<List<DownloadRecordEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: DownloadRecordEntity)

  @Query("SELECT * FROM download_records WHERE id LIKE :prefix || '%'")
  suspend fun getByPrefix(prefix: String): List<DownloadRecordEntity>

  @Query("DELETE FROM download_records")
  suspend fun clearAll()

  @Query("DELETE FROM download_records WHERE id LIKE :prefix || '%'")
  suspend fun deleteByPrefix(prefix: String)
}

@Dao
interface SeenGradeDao {
  @Query("SELECT * FROM seen_grades WHERE studentId = :studentId ORDER BY seenAtEpochMillis DESC")
  fun observeByStudent(studentId: String): Flow<List<SeenGradeEntity>>

  @Query("SELECT * FROM seen_grades ORDER BY seenAtEpochMillis DESC")
  suspend fun getAll(): List<SeenGradeEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SeenGradeEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<SeenGradeEntity>)
}

@Dao
interface SubjectGoalDao {
  @Query("SELECT * FROM subject_goals WHERE studentId = :studentId ORDER BY subject ASC, periodCode ASC")
  fun observeByStudent(studentId: String): Flow<List<SubjectGoalEntity>>

  @Query("SELECT * FROM subject_goals ORDER BY subject ASC, periodCode ASC")
  suspend fun getAll(): List<SubjectGoalEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SubjectGoalEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<SubjectGoalEntity>)

  @Query("DELETE FROM subject_goals WHERE studentId = :studentId AND subject = :subject AND ((periodCode IS NULL AND :periodCode IS NULL) OR periodCode = :periodCode)")
  suspend fun delete(studentId: String, subject: String, periodCode: String?)
}

@Dao
interface ChangeHistoryDao {
  @Query("SELECT * FROM change_history WHERE studentId = :studentId AND schoolYearId = :schoolYearId AND itemKind = :itemKind ORDER BY recordedAtEpochMillis DESC")
  fun observeByYearAndKind(studentId: String, schoolYearId: String, itemKind: String): Flow<List<ChangeHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<ChangeHistoryEntity>)

  /** Tutta la storia di un tipo, per il backup. */
  @Query("SELECT * FROM change_history WHERE itemKind = :itemKind ORDER BY recordedAtEpochMillis ASC")
  suspend fun getAllByKind(itemKind: String): List<ChangeHistoryEntity>
}

@Dao
interface GradeDao {
  @Query("SELECT * FROM grades WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY date DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<GradeEntity>>

  @Query("SELECT * FROM grades WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun getByYearOnce(studentId: String, schoolYearId: String): List<GradeEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<GradeEntity>)

  @Query("DELETE FROM grades WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)

  /**
   * Tutti i voti di ogni studente e ogni anno.
   *
   * Serve al backup, ed e' l'unica query che ignora l'anno di proposito: il registro non sa
   * restituire i voti di un anno passato, quindi quelli esistono solo qui e un backup che ne
   * salvasse solo l'anno corrente non servirebbe a niente.
   */
  @Query("SELECT * FROM grades ORDER BY schoolYearId ASC, date DESC")
  suspend fun getAll(): List<GradeEntity>
}

@Dao
interface AgendaDao {
  @Query("SELECT * FROM agenda_items WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY date ASC, time ASC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<AgendaItemEntity>>

  @Query("SELECT * FROM agenda_items WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun getByYearOnce(studentId: String, schoolYearId: String): List<AgendaItemEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<AgendaItemEntity>)

  @Query("DELETE FROM agenda_items WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)
}

@Dao
interface AbsenceDao {
  @Query("SELECT * FROM absences WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY date DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<AbsenceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<AbsenceEntity>)

  @Query("DELETE FROM absences WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)
}

@Dao
interface CommunicationDao {
  @Query("SELECT * FROM communications WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY date DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<CommunicationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<CommunicationEntity>)

  @Query("DELETE FROM communications WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)

  @Query("UPDATE communications SET read = 1 WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun markAllRead(studentId: String, schoolYearId: String)

  @Query("UPDATE communications SET read = 1 WHERE id = :id")
  suspend fun markRead(id: String)

  @Query("SELECT id FROM communications WHERE studentId = :studentId AND schoolYearId = :schoolYearId AND read = 1")
  suspend fun getReadIds(studentId: String, schoolYearId: String): List<String>

  @Query("SELECT * FROM communications WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): CommunicationEntity?

  @Query("SELECT * FROM communications WHERE pubId = :pubId AND evtCode = :evtCode LIMIT 1")
  suspend fun getByPubIdAndEvtCode(pubId: String, evtCode: String): CommunicationEntity?

  @Query("SELECT * FROM communications WHERE studentId = :studentId AND schoolYearId = :schoolYearId AND read = 0")
  suspend fun getUnread(studentId: String, schoolYearId: String): List<CommunicationEntity>
}

@Dao
interface MaterialDao {
  @Query("SELECT * FROM materials WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY sharedAt DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<MaterialEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<MaterialEntity>)

  @Query("DELETE FROM materials WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)

  @Transaction
  suspend fun replaceByYear(studentId: String, schoolYearId: String, entities: List<MaterialEntity>) {
    deleteByYear(studentId, schoolYearId)
    if (entities.isNotEmpty()) upsertAll(entities)
  }
}

@Dao
interface DocumentDao {
  @Query("SELECT * FROM documents WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY id DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<DocumentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<DocumentEntity>)

  @Query("DELETE FROM documents WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)

  @Transaction
  suspend fun replaceByYear(studentId: String, schoolYearId: String, entities: List<DocumentEntity>) {
    deleteByYear(studentId, schoolYearId)
    if (entities.isNotEmpty()) upsertAll(entities)
  }
}

@Dao
interface ReadNoteDao {
  @Query("SELECT * FROM read_notes WHERE studentId = :studentId")
  fun observeByStudent(studentId: String): Flow<List<ReadNoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ReadNoteEntity)
}

@Dao
interface AttachmentCacheDao {
  @Query("SELECT * FROM attachment_cache WHERE urlKey = :urlKey LIMIT 1")
  suspend fun getByUrlKey(urlKey: String): AttachmentCacheEntity?

  @Query("SELECT * FROM attachment_cache WHERE lastAccessedMs < :beforeMs")
  suspend fun getExpiredBefore(beforeMs: Long): List<AttachmentCacheEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: AttachmentCacheEntity)

  @Query("DELETE FROM attachment_cache WHERE urlKey = :urlKey")
  suspend fun deleteByUrlKey(urlKey: String)

  @Query("DELETE FROM attachment_cache")
  suspend fun clearAll()

  @Query("DELETE FROM attachment_cache WHERE urlKey LIKE :prefix || '%'")
  suspend fun deleteByPrefix(prefix: String)
}

@Database(
  entities = [
    SnapshotCacheEntity::class,
    SyncMetadataEntity::class,
    CustomEventEntity::class,
    StudentScoreSnapshotEntity::class,
    DownloadRecordEntity::class,
    SeenGradeEntity::class,
    SubjectGoalEntity::class,
    ChangeHistoryEntity::class,
    GradeEntity::class,
    AgendaItemEntity::class,
    AbsenceEntity::class,
    CommunicationEntity::class,
    MaterialEntity::class,
    DocumentEntity::class,
    ReadNoteEntity::class,
    AttachmentCacheEntity::class,
    AssistantConversationEntity::class,
    AssistantMessageEntity::class,
    AssistantRunEntity::class,
  ],
  version = 12,
  exportSchema = true,
  // 11 -> 12 aggiunge solo le tre tabelle dell'assistente: Room la deriva dallo schema esportato.
  autoMigrations = [AutoMigration(from = 11, to = 12)],
)
abstract class SchoolDatabase : RoomDatabase() {
  abstract fun snapshotCacheDao(): SnapshotCacheDao
  abstract fun syncMetadataDao(): SyncMetadataDao
  abstract fun customEventDao(): CustomEventDao
  abstract fun studentScoreDao(): StudentScoreDao
  abstract fun downloadRecordDao(): DownloadRecordDao
  abstract fun seenGradeDao(): SeenGradeDao
  abstract fun subjectGoalDao(): SubjectGoalDao
  abstract fun changeHistoryDao(): ChangeHistoryDao
  abstract fun gradeDao(): GradeDao
  abstract fun agendaDao(): AgendaDao
  abstract fun absenceDao(): AbsenceDao
  abstract fun communicationDao(): CommunicationDao
  abstract fun materialDao(): MaterialDao
  abstract fun documentDao(): DocumentDao
  abstract fun readNoteDao(): ReadNoteDao
  abstract fun attachmentCacheDao(): AttachmentCacheDao
  abstract fun assistantConversationDao(): AssistantConversationDao
  abstract fun assistantMessageDao(): AssistantMessageDao
  abstract fun assistantRunDao(): AssistantRunDao
}

val MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `attachment_cache` (
        `urlKey` TEXT NOT NULL,
        `sourceUrl` TEXT NOT NULL,
        `localPath` TEXT NOT NULL,
        `fileName` TEXT NOT NULL,
        `mimeType` TEXT,
        `downloadedAtMs` INTEGER NOT NULL,
        `lastAccessedMs` INTEGER NOT NULL,
        PRIMARY KEY(`urlKey`)
      )
      """.trimIndent(),
    )
  }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `change_history` (
        `id` TEXT NOT NULL,
        `studentId` TEXT NOT NULL,
        `schoolYearId` TEXT NOT NULL,
        `itemKind` TEXT NOT NULL,
        `itemId` TEXT NOT NULL,
        `recordedAtEpochMillis` INTEGER NOT NULL,
        `payload` TEXT NOT NULL,
        PRIMARY KEY(`id`)
      )
      """.trimIndent(),
    )
  }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE grades ADD COLUMN firstSeenAtMs INTEGER")
  }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `sync_metadata` (
        `studentId` TEXT NOT NULL,
        `schoolYearId` TEXT NOT NULL,
        `section` TEXT NOT NULL,
        `lastAttemptAtEpochMillis` INTEGER NOT NULL,
        `lastSuccessAtEpochMillis` INTEGER,
        `lastError` TEXT,
        PRIMARY KEY(`studentId`, `schoolYearId`, `section`)
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      CREATE TABLE `materials_v10` (
        `id` TEXT NOT NULL,
        `studentId` TEXT NOT NULL,
        `schoolYearId` TEXT NOT NULL,
        `teacherId` TEXT NOT NULL,
        `teacherName` TEXT NOT NULL,
        `folderId` TEXT NOT NULL,
        `folderName` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `objectId` TEXT NOT NULL,
        `objectType` TEXT NOT NULL,
        `sharedAt` TEXT NOT NULL,
        `capabilityState` TEXT NOT NULL,
        `attachments` TEXT NOT NULL,
        PRIMARY KEY(`studentId`, `schoolYearId`, `id`)
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT INTO `materials_v10` (
        `id`, `studentId`, `schoolYearId`, `teacherId`, `teacherName`, `folderId`,
        `folderName`, `title`, `objectId`, `objectType`, `sharedAt`, `capabilityState`, `attachments`
      )
      SELECT `id`, `studentId`, `schoolYearId`, `teacherId`, `teacherName`, `folderId`,
        `folderName`, `title`, `objectId`, `objectType`, `sharedAt`, `capabilityState`, `attachments`
      FROM `materials`
      """.trimIndent(),
    )
    db.execSQL("DROP TABLE `materials`")
    db.execSQL("ALTER TABLE `materials_v10` RENAME TO `materials`")

    db.execSQL(
      """
      CREATE TABLE `documents_v10` (
        `id` TEXT NOT NULL,
        `studentId` TEXT NOT NULL,
        `schoolYearId` TEXT NOT NULL,
        `title` TEXT NOT NULL,
        `detail` TEXT NOT NULL,
        `kind` TEXT NOT NULL,
        `remoteHash` TEXT,
        `restReadUrl` TEXT,
        `portalViewUrl` TEXT,
        `portalConfirmUrl` TEXT,
        `viewUrl` TEXT,
        `confirmUrl` TEXT,
        `capabilityState` TEXT NOT NULL,
        PRIMARY KEY(`studentId`, `schoolYearId`, `id`)
      )
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT INTO `documents_v10` (
        `id`, `studentId`, `schoolYearId`, `title`, `detail`, `kind`, `remoteHash`,
        `restReadUrl`, `portalViewUrl`, `portalConfirmUrl`, `viewUrl`, `confirmUrl`, `capabilityState`
      )
      SELECT 'DOCUMENT::' || `id`, `studentId`, `schoolYearId`, `title`, `detail`, 'DOCUMENT', NULL,
        NULL, NULL, NULL, `viewUrl`, `confirmUrl`, `capabilityState`
      FROM `documents`
      """.trimIndent(),
    )
    db.execSQL("DROP TABLE `documents`")
    db.execSQL("ALTER TABLE `documents_v10` RENAME TO `documents`")

    // Legacy snapshots cannot be attributed to a student and must never cross accounts.
    db.execSQL("DELETE FROM `snapshot_cache`")
  }
}

/**
 * Via il voto simulato, e con lui la sua tabella.
 *
 * La funzione permetteva di inventarsi un voto per vedere che effetto avrebbe fatto sulla media.
 * E' stata tolta perche' in pratica non serviva a nessuno, e una tabella che nessuno legge piu' non
 * si lascia indietro: resterebbe a occupare spazio nel database di ogni installazione e a comparire
 * in ogni schema esportato, senza che una riga di codice sappia piu' cosa farsene.
 *
 * `IF EXISTS` perche' una migrazione deve poter essere rigiocata senza fare danni: chi arriva qui
 * dalla versione 10 la tabella ce l'ha di sicuro, ma un `DROP` che fallisce e' un aggiornamento che
 * fallisce, e non c'e' niente da guadagnare a renderlo fragile.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS `simulated_grades`")
  }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): SchoolDatabase {
    return Room
      .databaseBuilder(context, SchoolDatabase::class.java, "classeviva_expressive_native.db")
      .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
      .build()
  }

  @Provides
  fun provideSnapshotCacheDao(database: SchoolDatabase): SnapshotCacheDao = database.snapshotCacheDao()

  @Provides
  fun provideSyncMetadataDao(database: SchoolDatabase): SyncMetadataDao = database.syncMetadataDao()

  @Provides
  fun provideCustomEventDao(database: SchoolDatabase): CustomEventDao = database.customEventDao()

  @Provides
  fun provideStudentScoreDao(database: SchoolDatabase): StudentScoreDao = database.studentScoreDao()

  @Provides
  fun provideDownloadRecordDao(database: SchoolDatabase): DownloadRecordDao = database.downloadRecordDao()

  @Provides
  fun provideSeenGradeDao(database: SchoolDatabase): SeenGradeDao = database.seenGradeDao()

  @Provides
  fun provideSubjectGoalDao(database: SchoolDatabase): SubjectGoalDao = database.subjectGoalDao()

  @Provides
  fun provideChangeHistoryDao(database: SchoolDatabase): ChangeHistoryDao = database.changeHistoryDao()

  @Provides
  fun provideGradeDao(database: SchoolDatabase): GradeDao = database.gradeDao()

  @Provides
  fun provideAgendaDao(database: SchoolDatabase): AgendaDao = database.agendaDao()

  @Provides
  fun provideAbsenceDao(database: SchoolDatabase): AbsenceDao = database.absenceDao()

  @Provides
  fun provideCommunicationDao(database: SchoolDatabase): CommunicationDao = database.communicationDao()

  @Provides
  fun provideMaterialDao(database: SchoolDatabase): MaterialDao = database.materialDao()

  @Provides
  fun provideAssistantConversationDao(database: SchoolDatabase): AssistantConversationDao = database.assistantConversationDao()

  @Provides
  fun provideAssistantMessageDao(database: SchoolDatabase): AssistantMessageDao = database.assistantMessageDao()

  @Provides
  fun provideAssistantRunDao(database: SchoolDatabase): AssistantRunDao = database.assistantRunDao()

  @Provides
  fun provideDocumentDao(database: SchoolDatabase): DocumentDao = database.documentDao()

  @Provides
  fun provideReadNoteDao(database: SchoolDatabase): ReadNoteDao = database.readNoteDao()

  @Provides
  fun provideAttachmentCacheDao(database: SchoolDatabase): AttachmentCacheDao = database.attachmentCacheDao()
}
