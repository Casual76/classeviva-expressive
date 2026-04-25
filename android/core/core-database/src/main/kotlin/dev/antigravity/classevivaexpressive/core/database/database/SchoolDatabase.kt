package dev.antigravity.classevivaexpressive.core.database.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "snapshot_cache")
data class SnapshotCacheEntity(
  @PrimaryKey val cacheKey: String,
  val payload: String,
  val updatedAtEpochMillis: Long,
)

@Entity(tableName = "custom_events")
data class CustomEventEntity(
  @PrimaryKey val id: String,
  val payload: String,
  val date: String,
  val time: String?,
  val createdAt: String? = null,
)

@Entity(tableName = "simulated_grades")
data class SimulatedGradeEntity(
  @PrimaryKey val id: String,
  val payload: String,
  val subject: String,
  val date: String,
)

@Entity(tableName = "student_score_snapshots")
data class StudentScoreSnapshotEntity(
  @PrimaryKey val id: String,
  val payload: String,
  val createdAtEpochMillis: Long,
)

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

@Entity(tableName = "seen_grades")
data class SeenGradeEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val gradeId: String,
  val seenAtEpochMillis: Long,
)

@Entity(tableName = "subject_goals")
data class SubjectGoalEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val subject: String,
  val periodCode: String?,
  val targetAverage: Double,
  val updatedAtEpochMillis: Long,
)

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
)

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

@Entity(tableName = "materials")
data class MaterialEntity(
  @PrimaryKey val id: String,
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

@Entity(tableName = "documents")
data class DocumentEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val schoolYearId: String,
  val title: String,
  val detail: String,
  val viewUrl: String?,
  val confirmUrl: String?,
  val capabilityState: String, // JSON
)

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
}

@Dao
interface CustomEventDao {
  @Query("SELECT * FROM custom_events ORDER BY date ASC, time ASC")
  fun observeAll(): Flow<List<CustomEventEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CustomEventEntity)

  @Query("DELETE FROM custom_events WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface SimulationDao {
  @Query("SELECT * FROM simulated_grades ORDER BY date ASC")
  fun observeAll(): Flow<List<SimulatedGradeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SimulatedGradeEntity)

  @Query("DELETE FROM simulated_grades WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM simulated_grades")
  suspend fun clearAll()
}

@Dao
interface StudentScoreDao {
  @Query("SELECT * FROM student_score_snapshots ORDER BY createdAtEpochMillis DESC")
  fun observeAll(): Flow<List<StudentScoreSnapshotEntity>>

  @Query("SELECT * FROM student_score_snapshots ORDER BY createdAtEpochMillis DESC LIMIT 1")
  fun observeLatest(): Flow<StudentScoreSnapshotEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: StudentScoreSnapshotEntity)
}

@Dao
interface DownloadRecordDao {
  @Query("SELECT * FROM download_records ORDER BY updatedAtEpochMillis DESC")
  fun observeAll(): Flow<List<DownloadRecordEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: DownloadRecordEntity)
}

@Dao
interface SeenGradeDao {
  @Query("SELECT * FROM seen_grades WHERE studentId = :studentId ORDER BY seenAtEpochMillis DESC")
  fun observeByStudent(studentId: String): Flow<List<SeenGradeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SeenGradeEntity)
}

@Dao
interface SubjectGoalDao {
  @Query("SELECT * FROM subject_goals WHERE studentId = :studentId ORDER BY subject ASC, periodCode ASC")
  fun observeByStudent(studentId: String): Flow<List<SubjectGoalEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SubjectGoalEntity)

  @Query("DELETE FROM subject_goals WHERE studentId = :studentId AND subject = :subject AND ((periodCode IS NULL AND :periodCode IS NULL) OR periodCode = :periodCode)")
  suspend fun delete(studentId: String, subject: String, periodCode: String?)
}

@Dao
interface GradeDao {
  @Query("SELECT * FROM grades WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY date DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<GradeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<GradeEntity>)

  @Query("DELETE FROM grades WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)
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
}

@Dao
interface DocumentDao {
  @Query("SELECT * FROM documents WHERE studentId = :studentId AND schoolYearId = :schoolYearId ORDER BY id DESC")
  fun observeByYear(studentId: String, schoolYearId: String): Flow<List<DocumentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<DocumentEntity>)

  @Query("DELETE FROM documents WHERE studentId = :studentId AND schoolYearId = :schoolYearId")
  suspend fun deleteByYear(studentId: String, schoolYearId: String)
}

@Dao
interface ReadNoteDao {
  @Query("SELECT * FROM read_notes WHERE studentId = :studentId")
  fun observeByStudent(studentId: String): Flow<List<ReadNoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ReadNoteEntity)
}

@Database(
  entities = [
    SnapshotCacheEntity::class,
    CustomEventEntity::class,
    SimulatedGradeEntity::class,
    StudentScoreSnapshotEntity::class,
    DownloadRecordEntity::class,
    SeenGradeEntity::class,
    SubjectGoalEntity::class,
    GradeEntity::class,
    AgendaItemEntity::class,
    AbsenceEntity::class,
    CommunicationEntity::class,
    MaterialEntity::class,
    DocumentEntity::class,
    ReadNoteEntity::class,
  ],
  version = 6,
  exportSchema = false,
)
abstract class SchoolDatabase : RoomDatabase() {
  abstract fun snapshotCacheDao(): SnapshotCacheDao
  abstract fun customEventDao(): CustomEventDao
  abstract fun simulationDao(): SimulationDao
  abstract fun studentScoreDao(): StudentScoreDao
  abstract fun downloadRecordDao(): DownloadRecordDao
  abstract fun seenGradeDao(): SeenGradeDao
  abstract fun subjectGoalDao(): SubjectGoalDao
  abstract fun gradeDao(): GradeDao
  abstract fun agendaDao(): AgendaDao
  abstract fun absenceDao(): AbsenceDao
  abstract fun communicationDao(): CommunicationDao
  abstract fun materialDao(): MaterialDao
  abstract fun documentDao(): DocumentDao
  abstract fun readNoteDao(): ReadNoteDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): SchoolDatabase {
    return Room
      .databaseBuilder(context, SchoolDatabase::class.java, "classeviva_expressive_native.db")
      .fallbackToDestructiveMigration()
      .build()
  }

  @Provides
  fun provideSnapshotCacheDao(database: SchoolDatabase): SnapshotCacheDao = database.snapshotCacheDao()

  @Provides
  fun provideCustomEventDao(database: SchoolDatabase): CustomEventDao = database.customEventDao()

  @Provides
  fun provideSimulationDao(database: SchoolDatabase): SimulationDao = database.simulationDao()

  @Provides
  fun provideStudentScoreDao(database: SchoolDatabase): StudentScoreDao = database.studentScoreDao()

  @Provides
  fun provideDownloadRecordDao(database: SchoolDatabase): DownloadRecordDao = database.downloadRecordDao()

  @Provides
  fun provideSeenGradeDao(database: SchoolDatabase): SeenGradeDao = database.seenGradeDao()

  @Provides
  fun provideSubjectGoalDao(database: SchoolDatabase): SubjectGoalDao = database.subjectGoalDao()

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
  fun provideDocumentDao(database: SchoolDatabase): DocumentDao = database.documentDao()

  @Provides
  fun provideReadNoteDao(database: SchoolDatabase): ReadNoteDao = database.readNoteDao()
}
