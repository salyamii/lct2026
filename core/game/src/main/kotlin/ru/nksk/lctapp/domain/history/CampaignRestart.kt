package ru.nksk.lctapp.domain.history

import kotlinx.serialization.Serializable

/** A single explicit rewind intent, retained unchanged while a write is uncertain. */
data class CampaignRestartRequest(
    val id: String,
    val expectedRunId: String,
    val expectedEngineRevision: Long?,
    val expectedHistorySequence: Long,
) {
    init { require(id.isNotBlank() && expectedRunId.isNotBlank() && expectedHistorySequence > 0) }
}

/** Archived snapshots contain one run only; the enclosing world owns the flat archive list. */
@Serializable
data class ArchivedGameRun(
    val restartRequestId: String,
    val nextRunId: String,
    val snapshot: GameSnapshot,
) {
    init {
        require(restartRequestId.isNotBlank() && nextRunId.isNotBlank() && nextRunId != snapshot.runId)
        require(snapshot.archivedRuns.isEmpty()) { "Run archives must not nest" }
    }
}

data class ArchivedGameRunSummary(val runId: String, val petName: String, val finalDay: Int?, val historyEntries: Int)

class CampaignRestartConflictException : IllegalStateException("The game changed; review the rewind again")
