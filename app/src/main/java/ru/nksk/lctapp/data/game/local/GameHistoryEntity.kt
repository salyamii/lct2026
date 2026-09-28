package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

@Entity(tableName = "GAME_RUN", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
], indices = [Index(value = ["run_id"], unique = true)])
internal data class GameRunEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "run_id") val runId: String,
)

/** Immutable historical document; the header is indexed independently of its snapshots. */
@Entity(tableName = "GAME_AUDIT", foreignKeys = [
    ForeignKey(entity = GameRunEntity::class, parentColumns = ["run_id"], childColumns = ["run_id"]),
], indices = [Index(value = ["run_id", "sequence"], unique = true)])
internal data class GameAuditEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "payload") val payload: String,
)

@Entity(tableName = "AUDIT_FACT_ID", foreignKeys = [
    ForeignKey(entity = GameAuditEntity::class, parentColumns = ["id"], childColumns = ["audit_id"]),
], indices = [Index(value = ["audit_id"])])
internal data class AuditFactIdEntity(
    @PrimaryKey @ColumnInfo(name = "fact_id") val factId: String,
    @ColumnInfo(name = "audit_id") val auditId: String,
)

@Entity(tableName = "AUDIT_OUTBOX", foreignKeys = [
    ForeignKey(entity = GameAuditEntity::class, parentColumns = ["id"], childColumns = ["audit_id"]),
])
internal data class AuditOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "audit_id") val auditId: String,
    @ColumnInfo(name = "acknowledged", defaultValue = "0") val acknowledged: Boolean = false,
)

@Dao
internal interface GameHistoryDao {
    @Query("SELECT * FROM GAME_RUN WHERE game_state_id = :gameId")
    suspend fun run(gameId: String): GameRunEntity?
    @Insert suspend fun insertRun(row: GameRunEntity)
    @Query("SELECT * FROM GAME_AUDIT ORDER BY sequence")
    suspend fun read(): List<GameAuditEntity>
    @Query("SELECT * FROM GAME_AUDIT WHERE id = :id")
    suspend fun find(id: String): GameAuditEntity?
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND type = 'INITIALIZED' ORDER BY sequence LIMIT 1")
    suspend fun initialization(runId: String): GameAuditEntity?
    @Query("SELECT id FROM GAME_AUDIT WHERE run_id = :runId AND type = 'RESTORED' ORDER BY sequence DESC LIMIT 1")
    suspend fun latestRestoreId(runId: String): String?
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND type = 'PARENT_REWARD' ORDER BY sequence LIMIT 1")
    suspend fun firstParentReward(runId: String): GameAuditEntity?
    @Query("SELECT COALESCE(MAX(sequence), 0) FROM GAME_AUDIT")
    suspend fun sequence(): Long
    @Insert suspend fun insert(row: GameAuditEntity)
    @Insert suspend fun insertFacts(rows: List<AuditFactIdEntity>)
    @Query("SELECT audit_id FROM AUDIT_FACT_ID WHERE fact_id = :id")
    suspend fun factAudit(id: String): String?
    @Insert suspend fun insertOutbox(row: AuditOutboxEntity)
    @Query("SELECT audit.* FROM GAME_AUDIT audit INNER JOIN AUDIT_OUTBOX box ON box.audit_id = audit.id WHERE box.acknowledged = 0 ORDER BY audit.sequence LIMIT :limit")
    suspend fun pending(limit: Int): List<GameAuditEntity>
    @Query("UPDATE AUDIT_OUTBOX SET acknowledged = 1 WHERE audit_id IN (:ids)")
    suspend fun acknowledge(ids: List<String>)
    @Query("DELETE FROM AUDIT_OUTBOX") suspend fun clearOutbox()
    @Query("DELETE FROM AUDIT_FACT_ID") suspend fun clearFacts()
    @Query("DELETE FROM GAME_AUDIT") suspend fun clearAudit()
    @Query("DELETE FROM GAME_RUN") suspend fun clearRun()
}
