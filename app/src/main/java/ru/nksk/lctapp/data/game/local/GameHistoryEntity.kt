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

/** Small JSON metadata only; deliberately excludes historical before/after worlds. */
internal data class CloudBaselineRow(val id: String, val sequence: Long, val payload: String)

@Dao
internal interface GameHistoryDao {
    @Query("SELECT * FROM GAME_RUN WHERE game_state_id = :gameId")
    suspend fun run(gameId: String): GameRunEntity?
    @Insert suspend fun insertRun(row: GameRunEntity)
    @Query("SELECT * FROM GAME_AUDIT ORDER BY sequence")
    suspend fun read(): List<GameAuditEntity>
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND sequence > :afterSequence ORDER BY sequence LIMIT :limit")
    suspend fun readPage(runId: String, afterSequence: Long, limit: Int): List<GameAuditEntity>
    @Query("SELECT id FROM GAME_AUDIT ORDER BY sequence DESC LIMIT 1")
    suspend fun latestId(): String?
    // The application always uses BundledSQLiteDriver, including on pre-JSON1 Android versions.
    @Query("SELECT * FROM GAME_AUDIT WHERE type = 'COMMAND' AND json_extract(payload, '$.before.engine.day') = :day ORDER BY sequence")
    suspend fun commandsForDay(day: Int): List<GameAuditEntity>
    @Query("SELECT * FROM GAME_AUDIT WHERE json_type(payload, '$.after') IS NOT NULL AND json_type(payload, '$.after') != 'null' ORDER BY sequence DESC LIMIT 1")
    suspend fun latestCheckpoint(): GameAuditEntity?
    @Query("SELECT COUNT(DISTINCT run_id) <= 1 AND COUNT(*) = COUNT(DISTINCT sequence) FROM GAME_AUDIT")
    suspend fun hasCoherentOrder(): Boolean
    @Query("SELECT * FROM GAME_AUDIT WHERE id = :id")
    suspend fun find(id: String): GameAuditEntity?
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND type = 'COMMAND' ORDER BY sequence DESC LIMIT 1")
    suspend fun latestCommand(runId: String): GameAuditEntity?
    // Fallback for imported/restored receipts whose opaque audit ID predates the canonical command ID.
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND type = 'COMMAND' AND json_extract(payload, '$.request.id') = :requestId ORDER BY sequence DESC LIMIT 1")
    suspend fun commandByRequest(runId: String, requestId: String): GameAuditEntity?
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND EXISTS (SELECT 1 FROM json_each(payload, '$.facts') fact WHERE json_extract(fact.value, '$.detail._type') IN ('unexpected_expense', 'recovery_action')) ORDER BY sequence")
    suspend fun expenseRecoveryHistory(runId: String): List<GameAuditEntity>
    @Query("SELECT MIN(sequence) FROM GAME_AUDIT WHERE run_id = :runId AND type = 'COMMAND' AND json_extract(payload, '$.after.financial.plans[#-1].id') = :planId AND NOT EXISTS (SELECT 1 FROM json_each(payload, '$.before.financial.plans') AS budget_entry WHERE json_extract(budget_entry.value, '$.id') = :planId)")
    suspend fun budgetPlanAnchor(runId: String, planId: String): Long?
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND sequence >= :sequence ORDER BY sequence")
    suspend fun fromSequence(runId: String, sequence: Long): List<GameAuditEntity>
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND type = 'INITIALIZED' ORDER BY sequence LIMIT 1")
    suspend fun initialization(runId: String): GameAuditEntity?
    @Query("SELECT id FROM GAME_AUDIT WHERE run_id = :runId AND type = 'RESTORED' ORDER BY sequence DESC LIMIT 1")
    suspend fun latestRestoreId(runId: String): String?
    @Query("SELECT id FROM GAME_AUDIT WHERE run_id = :runId AND type = 'RESTORED' AND sequence <= :through ORDER BY sequence DESC LIMIT 1")
    suspend fun restoreIdThrough(runId: String, through: Long): String?
    @Query("SELECT id, sequence, json_extract(payload, '$.worldRestore') AS payload FROM GAME_AUDIT WHERE run_id = :runId AND sequence <= :through AND json_type(payload, '$.worldRestore') = 'object' ORDER BY sequence DESC LIMIT 1")
    suspend fun cloudBaseline(runId: String, through: Long): CloudBaselineRow?
    @Query("SELECT id, sequence, json_extract(payload, '$.worldRestore') AS payload FROM GAME_AUDIT WHERE json_extract(payload, '$.worldRestore.restoreRequestId') = :requestId ORDER BY sequence DESC LIMIT 1")
    suspend fun cloudRestore(requestId: String): CloudBaselineRow?
    @Query("SELECT json_extract(payload, '$.parentReward') FROM GAME_AUDIT WHERE run_id = :runId AND type = 'PARENT_REWARD' AND sequence > :after AND sequence <= :through ORDER BY sequence")
    suspend fun parentRewardPayloads(runId: String, after: Long, through: Long): List<String>
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND sequence <= :through AND json_type(payload, '$.after') = 'object' ORDER BY sequence DESC LIMIT 1")
    suspend fun checkpointThrough(runId: String, through: Long): GameAuditEntity?
    @Query("SELECT * FROM GAME_AUDIT WHERE run_id = :runId AND sequence = :sequence")
    suspend fun atSequence(runId: String, sequence: Long): GameAuditEntity?
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
    @Query("UPDATE AUDIT_OUTBOX SET acknowledged = 1 WHERE audit_id IN (SELECT id FROM GAME_AUDIT WHERE run_id = :runId AND sequence <= :through)")
    suspend fun acknowledgeThrough(runId: String, through: Long)
    @Query("DELETE FROM AUDIT_OUTBOX") suspend fun clearOutbox()
    @Query("DELETE FROM AUDIT_FACT_ID") suspend fun clearFacts()
    @Query("DELETE FROM GAME_AUDIT") suspend fun clearAudit()
    @Query("DELETE FROM GAME_RUN") suspend fun clearRun()
}
