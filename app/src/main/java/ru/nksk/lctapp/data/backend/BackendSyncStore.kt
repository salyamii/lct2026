package ru.nksk.lctapp.data.backend

import androidx.room3.withWriteTransaction
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Durable request bodies are kept in Room, not in UI state or device preferences. */
internal interface BackendSyncStore {
    suspend fun read(profileId: String): BackendSyncStateEntity?
    suspend fun save(state: BackendSyncStateEntity)
    suspend fun pending(profileId: String, kind: String): PendingBackendRequestEntity?
    suspend fun stage(request: PendingBackendRequestEntity)
    suspend fun complete(state: BackendSyncStateEntity, kind: String, requestId: String)
    suspend fun replaceAfterRestore(state: BackendSyncStateEntity)
    suspend fun replaceAfterRestart(state: BackendSyncStateEntity)
    suspend fun clear(profileId: String, kind: String, requestId: String)
}

@Singleton
internal class RoomBackendSyncStore @Inject constructor(private val database: GameDatabase) : BackendSyncStore {
    private val dao get() = database.backendSyncDao()
    override suspend fun read(profileId: String) = dao.readState(profileId)
    override suspend fun save(state: BackendSyncStateEntity) = dao.upsertState(state)
    override suspend fun pending(profileId: String, kind: String) = dao.readPending(profileId, kind)
    override suspend fun stage(request: PendingBackendRequestEntity) = dao.upsertPending(request)
    override suspend fun clear(profileId: String, kind: String, requestId: String) = dao.deletePending(profileId, kind, requestId)
    override suspend fun complete(state: BackendSyncStateEntity, kind: String, requestId: String) {
        database.withWriteTransaction {
            dao.upsertState(state)
            dao.deletePending(state.profileId, kind, requestId)
        }
    }
    override suspend fun replaceAfterRestore(state: BackendSyncStateEntity) {
        database.withWriteTransaction {
            dao.upsertState(state)
            listOf("snapshot", "analytics", "ack", "restore").forEach { kind ->
                dao.readPending(state.profileId, kind)?.let { dao.deletePending(it.profileId, it.kind, it.requestId) }
            }
        }
    }
    override suspend fun replaceAfterRestart(state: BackendSyncStateEntity) {
        database.withWriteTransaction {
            // A restart keeps the previous world in the archive. Lost HTTP replies must be
            // recovered using their frozen IDs before moving the transport to the new run.
            check(listOf("snapshot", "analytics", "ack", "restore").all {
                dao.readPending(state.profileId, it) == null
            }) { "Previous game run still has an unacknowledged request" }
            dao.upsertState(state)
        }
    }
}
