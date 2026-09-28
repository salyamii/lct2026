package ru.nksk.lctapp.domain.backend

import kotlinx.coroutines.flow.StateFlow

enum class CloudSyncPhase { IDLE, SYNCING, OFFLINE, ERROR, CONFLICT }
enum class SkillSyncPhase { IDLE, SYNCING, UNAVAILABLE, OFFLINE, ERROR }

data class CloudSyncState(
    val phase: CloudSyncPhase = CloudSyncPhase.IDLE,
    val lastSyncedAt: String? = null,
    val message: String? = null,
    val skills: SkillAssessmentsResponse? = null,
    /** Local restore generation owning [skills]; transport-only, never inferred from a server run ID. */
    val skillsGeneration: String? = null,
    val skillsPhase: SkillSyncPhase = SkillSyncPhase.IDLE,
)

enum class CloudSyncResult { SUCCESS, RETRY, NEEDS_ATTENTION, NO_GAME }

/** Display-only preview. The validated archive and local restore guard stay in the repository. */
data class CloudRestorePreview(
    val id: String,
    val petName: String,
    val day: Int?,
    val availableCoins: Long,
    val savingsCoins: Long,
)

interface CloudSyncRepository {
    val state: StateFlow<CloudSyncState>
    suspend fun synchronize(): CloudSyncResult
    /** Refresh current-run evidence and server assessments without backup, restore or reward delivery. */
    suspend fun refreshSkills(): CloudSyncResult
    suspend fun prepareRestore(): CloudRestorePreview
    suspend fun restore(previewId: String)
    fun dismissRestore(previewId: String)
}
