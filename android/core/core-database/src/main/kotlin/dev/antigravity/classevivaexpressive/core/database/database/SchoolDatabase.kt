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

@Database(
  entities = [
    SnapshotCacheEntity::class,
    CustomEventEntity::class,
    SimulatedGradeEntity::class,
    StudentScoreSnapshotEntity::class,
    DownloadRecordEntity::class,
    SeenGradeEntity::class,
    SubjectGoalEntity::class,
  ],
  version = 2,
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
}
