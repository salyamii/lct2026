package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

/** Device transport state, independent of the restored world's immutable reward receipts. */
@Entity(tableName = "BACKEND_SYNC_STATE")
internal data class BackendSyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "backend_url") val backendUrl: String,
    @ColumnInfo(name = "server_revision") val serverRevision: Long? = null,
    @ColumnInfo(name = "last_snapshot_checksum") val lastSnapshotChecksum: String? = null,
    @ColumnInfo(name = "last_analytics_sequence") val lastAnalyticsSequence: Long = 0,
    @ColumnInfo(name = "last_synced_at_epoch_ms") val lastSyncedAtEpochMs: Long? = null,
    @ColumnInfo(name = "skills_payload") val skillsPayload: String? = null,
    @ColumnInfo(name = "game_run_id") val gameRunId: String? = null,
    @ColumnInfo(name = "local_generation") val localGeneration: String? = null,
    @ColumnInfo(name = "reward_fetch_cursor") val rewardFetchCursor: Long? = null,
)

/** Frozen request bytes are a transport document, not a second mutable world model. */
@Entity(tableName = "PENDING_BACKEND_REQUEST", primaryKeys = ["profile_id", "kind"])
internal data class PendingBackendRequestEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "payload") val payload: String,
)

@Dao
internal interface BackendSyncDao {
    @Query("SELECT * FROM BACKEND_SYNC_STATE WHERE profile_id = :profileId")
    suspend fun readState(profileId: String): BackendSyncStateEntity?
    @Upsert suspend fun upsertState(row: BackendSyncStateEntity)

    @Query("SELECT * FROM PENDING_BACKEND_REQUEST WHERE profile_id = :profileId AND kind = :kind")
    suspend fun readPending(profileId: String, kind: String): PendingBackendRequestEntity?
    @Insert suspend fun insertPending(row: PendingBackendRequestEntity)

    /** A retry keeps exactly its original ID and serialized body. */
    @Transaction
    suspend fun upsertPending(row: PendingBackendRequestEntity) {
        val previous = readPending(row.profileId, row.kind)
        if (previous == null) insertPending(row)
        else require(previous == row) { "A pending backend request is immutable until acknowledged" }
    }

    @Query("DELETE FROM PENDING_BACKEND_REQUEST WHERE profile_id = :profileId AND kind = :kind AND request_id = :requestId")
    suspend fun deletePending(profileId: String, kind: String, requestId: String)
}
