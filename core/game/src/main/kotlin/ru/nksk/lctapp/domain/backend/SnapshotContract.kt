package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec

/** Snapshot JSON stays opaque to transport; reserializing its internals would break old checksums. */
@Serializable
data class SnapshotUploadRequest(
    val uploadId: String,
    val expectedServerRevision: Long?,
    val gameRunId: String,
    val throughHistorySequence: Long,
    val currentContentFingerprint: String,
    val snapshotFormatVersion: Int,
    val checksum: String,
    val snapshotJson: String,
    val schemaVersion: Int = 1,
)

@Serializable
data class SnapshotUploadResponse(val uploadId: String, val gameRunId: String, val serverRevision: Long, val checksum: String)

@Serializable
data class SnapshotDownloadResponse(
    val gameRunId: String,
    val serverRevision: Long,
    val currentContentFingerprint: String,
    val snapshotJson: String,
    val schemaVersion: Int = 1,
)

fun snapshotUploadRequest(snapshot: GameSnapshot, uploadId: String, expectedServerRevision: Long?,
    currentContentFingerprint: String): SnapshotUploadRequest {
    require(uploadId.isNotBlank() && currentContentFingerprint.isNotBlank())
    require(expectedServerRevision == null || expectedServerRevision >= 1)
    HistoryCodec.validate(snapshot)
    return SnapshotUploadRequest(uploadId, expectedServerRevision, snapshot.runId, snapshot.historySequence,
        currentContentFingerprint, snapshot.formatVersion, snapshot.checksum, HistoryCodec.encodeSnapshot(snapshot))
}
