package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

/** Immutable snapshot header; its ordered history lives in GAME_RUN_ARCHIVE_AUDIT. */
@Entity(tableName = "GAME_RUN_ARCHIVE", indices = [
    Index(value = ["position"], unique = true),
    Index(value = ["restart_request_id"], unique = true),
    Index(value = ["next_run_id"], unique = true),
])
internal data class GameRunArchiveEntity(
    @PrimaryKey @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "restart_request_id") val restartRequestId: String,
    @ColumnInfo(name = "next_run_id") val nextRunId: String,
    @ColumnInfo(name = "snapshot_payload") val snapshotPayload: String,
)

/** One historical document, independent of the active GAME_RUN and its mutable world. */
@Entity(tableName = "GAME_RUN_ARCHIVE_AUDIT", primaryKeys = ["archive_run_id", "sequence"], foreignKeys = [
    ForeignKey(entity = GameRunArchiveEntity::class, parentColumns = ["run_id"],
        childColumns = ["archive_run_id"], onDelete = ForeignKey.CASCADE),
])
internal data class GameRunArchiveAuditEntity(
    @ColumnInfo(name = "archive_run_id") val archiveRunId: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "payload") val payload: String,
)

internal data class CloudArchiveHead(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "next_run_id") val nextRunId: String,
    @ColumnInfo(name = "history_sequence") val historySequence: Long,
    @ColumnInfo(name = "restore_id") val restoreId: String?,
    @ColumnInfo(name = "baseline_sequence") val baselineSequence: Long?,
    @ColumnInfo(name = "baseline_payload") val baselinePayload: String?,
)

internal data class ArchivedCloudRestoreRow(
    @ColumnInfo(name = "run_id") val runId: String,
    val id: String,
    val payload: String,
)

@Dao
internal interface GameRunArchiveDao {
    @Query("SELECT next_run_id FROM GAME_RUN_ARCHIVE ORDER BY position DESC LIMIT 1")
    suspend fun latestNextRunId(): String?
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM GAME_RUN_ARCHIVE")
    suspend fun nextPosition(): Int

    @Query("SELECT * FROM GAME_RUN_ARCHIVE ORDER BY position")
    suspend fun read(): List<GameRunArchiveEntity>
    @Query("SELECT * FROM GAME_RUN_ARCHIVE WHERE run_id = :runId")
    suspend fun find(runId: String): GameRunArchiveEntity?
    @Query("SELECT * FROM GAME_RUN_ARCHIVE WHERE restart_request_id = :requestId")
    suspend fun forRestart(requestId: String): GameRunArchiveEntity?
    @Query("""SELECT run_id, next_run_id,
        json_extract(snapshot_payload, '$.historySequence') AS history_sequence,
        (SELECT json_extract(payload, '$.id') FROM GAME_RUN_ARCHIVE_AUDIT
            WHERE archive_run_id = run_id AND json_extract(payload, '$.type') = 'RESTORED'
            ORDER BY sequence DESC LIMIT 1) AS restore_id,
        (SELECT sequence FROM GAME_RUN_ARCHIVE_AUDIT
            WHERE archive_run_id = run_id AND json_type(payload, '$.worldRestore') = 'object'
            ORDER BY sequence DESC LIMIT 1) AS baseline_sequence,
        (SELECT json_extract(payload, '$.worldRestore') FROM GAME_RUN_ARCHIVE_AUDIT
            WHERE archive_run_id = run_id AND json_type(payload, '$.worldRestore') = 'object'
            ORDER BY sequence DESC LIMIT 1) AS baseline_payload
        FROM GAME_RUN_ARCHIVE WHERE next_run_id = :nextRunId""")
    suspend fun predecessorHead(nextRunId: String): CloudArchiveHead?
    @Query("""SELECT archive_run_id AS run_id, json_extract(payload, '$.id') AS id,
        json_extract(payload, '$.worldRestore') AS payload FROM GAME_RUN_ARCHIVE_AUDIT
        WHERE json_extract(payload, '$.worldRestore.restoreRequestId') = :requestId LIMIT 1""")
    suspend fun cloudRestore(requestId: String): ArchivedCloudRestoreRow?
    @Query("SELECT EXISTS(SELECT 1 FROM GAME_RUN_ARCHIVE WHERE run_id = :runId)")
    suspend fun contains(runId: String): Boolean
    @Insert suspend fun insert(archive: GameRunArchiveEntity)
    @Query("SELECT * FROM GAME_RUN_ARCHIVE_AUDIT WHERE archive_run_id = :runId ORDER BY sequence")
    suspend fun readHistory(runId: String): List<GameRunArchiveAuditEntity>
    @Query("SELECT * FROM GAME_RUN_ARCHIVE_AUDIT WHERE archive_run_id = :runId AND sequence > :afterSequence ORDER BY sequence LIMIT :limit")
    suspend fun readHistoryPage(runId: String, afterSequence: Long, limit: Int): List<GameRunArchiveAuditEntity>
    @Query("INSERT INTO GAME_RUN_ARCHIVE_AUDIT(archive_run_id, sequence, payload) SELECT run_id, sequence, payload FROM GAME_AUDIT WHERE run_id = :runId")
    suspend fun insertActiveHistory(runId: String)
    @Insert suspend fun insertHistory(rows: List<GameRunArchiveAuditEntity>)
    @Query("DELETE FROM GAME_RUN_ARCHIVE") suspend fun clear()
}
