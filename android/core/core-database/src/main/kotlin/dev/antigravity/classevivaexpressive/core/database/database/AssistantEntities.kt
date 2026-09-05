package dev.antigravity.classevivaexpressive.core.database.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Le conversazioni dell'assistente. Tre tabelle senza chiavi esterne: le cancellazioni le fa il
 * repository, in ordine, e i messaggi non hanno mai bisogno di sapere della conversazione piu'
 * di quanto dica l'indice.
 */
@Entity(tableName = "assistant_conversations", indices = [Index("studentId")])
data class AssistantConversationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val studentId: String,
  val title: String,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
  val lastProvider: String? = null,
)

@Entity(tableName = "assistant_messages", indices = [Index("conversationId")])
data class AssistantMessageEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val conversationId: Long,
  /** USER o ASSISTANT. */
  val role: String,
  val text: String,
  val chipsJson: String? = null,
  /** PENDING, STREAMING, DONE, FAILED, CANCELLED. */
  val status: String,
  val failureKind: String? = null,
  val createdAtEpochMillis: Long,
)

/** Uno scambio con la sua telemetria: modelli, passi, token, costo, strumenti. */
@Entity(tableName = "assistant_runs", indices = [Index("conversationId"), Index("messageId")])
data class AssistantRunEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val conversationId: Long,
  val messageId: Long,
  val startedAtEpochMillis: Long,
  val finishedAtEpochMillis: Long?,
  val steps: Int,
  val provider: String?,
  val routerModel: String?,
  val chatModel: String?,
  val deepModel: String?,
  val tierReached: String?,
  val groupsJson: String?,
  val promptTokens: Int?,
  val completionTokens: Int?,
  val costUsd: Double?,
  val waitedSeconds: Int,
  val toolTracesJson: String?,
  /** ok, failed, cancelled. */
  val outcome: String,
  val error: String?,
)

@Dao
interface AssistantConversationDao {
  @Query("SELECT * FROM assistant_conversations WHERE studentId = :studentId ORDER BY updatedAtEpochMillis DESC")
  fun observeByStudent(studentId: String): Flow<List<AssistantConversationEntity>>

  @Query("SELECT * FROM assistant_conversations WHERE id = :id")
  fun observe(id: Long): Flow<AssistantConversationEntity?>

  @Query("SELECT * FROM assistant_conversations WHERE id = :id")
  suspend fun get(id: Long): AssistantConversationEntity?

  @Insert
  suspend fun insert(entity: AssistantConversationEntity): Long

  @Update
  suspend fun update(entity: AssistantConversationEntity)

  @Query("DELETE FROM assistant_conversations WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM assistant_conversations WHERE studentId = :studentId")
  suspend fun deleteByStudent(studentId: String)
}

@Dao
interface AssistantMessageDao {
  @Query("SELECT * FROM assistant_messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC, id ASC")
  fun observeByConversation(conversationId: Long): Flow<List<AssistantMessageEntity>>

  @Query("SELECT * FROM assistant_messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC, id ASC")
  suspend fun listByConversation(conversationId: Long): List<AssistantMessageEntity>

  @Query("SELECT * FROM assistant_messages WHERE id = :id")
  suspend fun get(id: Long): AssistantMessageEntity?

  @Insert
  suspend fun insert(entity: AssistantMessageEntity): Long

  @Update
  suspend fun update(entity: AssistantMessageEntity)

  @Query("UPDATE assistant_messages SET status = :status, failureKind = :failureKind WHERE status IN (:staleStatuses)")
  suspend fun failStale(status: String, failureKind: String, staleStatuses: List<String>)

  @Query("DELETE FROM assistant_messages WHERE conversationId = :conversationId")
  suspend fun deleteByConversation(conversationId: Long)

  @Query("DELETE FROM assistant_messages WHERE conversationId IN (SELECT id FROM assistant_conversations WHERE studentId = :studentId)")
  suspend fun deleteByStudent(studentId: String)
}

@Dao
interface AssistantRunDao {
  @Query("SELECT * FROM assistant_runs WHERE conversationId = :conversationId ORDER BY startedAtEpochMillis ASC")
  fun observeByConversation(conversationId: Long): Flow<List<AssistantRunEntity>>

  @Query("SELECT * FROM assistant_runs ORDER BY startedAtEpochMillis DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<AssistantRunEntity>>

  @Query("SELECT COUNT(*) FROM assistant_runs")
  fun observeCount(): Flow<Int>

  @Query("SELECT COALESCE(SUM(promptTokens), 0) + COALESCE(SUM(completionTokens), 0) FROM assistant_runs")
  fun observeTotalTokens(): Flow<Long>

  @Query("SELECT COALESCE(SUM(costUsd), 0.0) FROM assistant_runs")
  fun observeTotalCost(): Flow<Double>

  @Insert
  suspend fun insert(entity: AssistantRunEntity): Long

  @Update
  suspend fun update(entity: AssistantRunEntity)

  @Query("DELETE FROM assistant_runs WHERE conversationId = :conversationId")
  suspend fun deleteByConversation(conversationId: Long)

  @Query("DELETE FROM assistant_runs WHERE conversationId IN (SELECT id FROM assistant_conversations WHERE studentId = :studentId)")
  suspend fun deleteByStudent(studentId: String)
}
