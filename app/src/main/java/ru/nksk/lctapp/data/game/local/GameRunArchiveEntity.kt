package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

/** An immutable, checksummed historical document independent of the mutable current-world tables. */
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

@Dao
internal interface GameRunArchiveDao {
    @Query("SELECT * FROM GAME_RUN_ARCHIVE ORDER BY position")
    suspend fun read(): List<GameRunArchiveEntity>
    @Query("SELECT * FROM GAME_RUN_ARCHIVE WHERE run_id = :runId")
    suspend fun find(runId: String): GameRunArchiveEntity?
    @Query("SELECT * FROM GAME_RUN_ARCHIVE WHERE restart_request_id = :requestId")
    suspend fun forRestart(requestId: String): GameRunArchiveEntity?
    @Insert suspend fun insert(archive: GameRunArchiveEntity)
    @Query("DELETE FROM GAME_RUN_ARCHIVE") suspend fun clear()
}
