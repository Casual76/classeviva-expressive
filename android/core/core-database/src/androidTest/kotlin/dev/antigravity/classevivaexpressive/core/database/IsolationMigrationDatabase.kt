package dev.antigravity.classevivaexpressive.core.database

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "snapshot_cache")
data class IsolationSnapshotV10(
  @PrimaryKey val cacheKey: String,
  val payload: String,
  val updatedAtEpochMillis: Long,
)

@Entity(tableName = "sync_metadata", primaryKeys = ["studentId", "schoolYearId", "section"])
data class IsolationSyncMetadataV10(
  val studentId: String,
  val schoolYearId: String,
  val section: String,
  val lastAttemptAtEpochMillis: Long,
  val lastSuccessAtEpochMillis: Long?,
  val lastError: String?,
)

@Entity(tableName = "materials", primaryKeys = ["studentId", "schoolYearId", "id"])
data class IsolationMaterialV10(
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
  val capabilityState: String,
  val attachments: String,
)

@Entity(tableName = "documents", primaryKeys = ["studentId", "schoolYearId", "id"])
data class IsolationDocumentV10(
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
  val capabilityState: String,
)

@Database(
  entities = [
    IsolationSnapshotV10::class,
    IsolationSyncMetadataV10::class,
    IsolationMaterialV10::class,
    IsolationDocumentV10::class,
  ],
  version = 10,
  exportSchema = true,
)
abstract class IsolationMigrationDatabase : RoomDatabase()
