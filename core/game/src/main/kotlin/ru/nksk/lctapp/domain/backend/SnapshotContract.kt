package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.WorldSnapshot
import ru.nksk.lctapp.domain.history.WorldSnapshotCodec

/** Snapshot JSON stays opaque to transport; reserializing its internals would break old checksums. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SnapshotUploadRequest(
    val deviceId: String,
    val uploadId: String,
    val expectedServerRevision: Long?,
    val gameRunId: String,
    val throughHistorySequence: Long,
    val currentContentFingerprint: String,
    val snapshotFormatVersion: Int,
    val checksum: String,
    val snapshotJson: String,
    val schemaVersion: Int = 1,
    /** Absent in frozen legacy bodies; their exact retry representation remains unchanged. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadKind: String? = null,
) {
    init { require(deviceId.isNotBlank()) }
}

@Serializable
data class SnapshotDownloadRequest(val deviceId: String, val schemaVersion: Int = 1) {
    init { require(deviceId.isNotBlank()) }
}

@Serializable
data class SnapshotUploadResponse(val uploadId: String, val gameRunId: String, val serverRevision: Long, val checksum: String)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SnapshotDownloadResponse(
    val gameRunId: String,
    val serverRevision: Long,
    val currentContentFingerprint: String,
    val snapshotJson: String,
    val schemaVersion: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadKind: String? = null,
)

fun snapshotUploadRequest(deviceId: String, snapshot: GameSnapshot, uploadId: String, expectedServerRevision: Long?,
    currentContentFingerprint: String): SnapshotUploadRequest {
    require(uploadId.isNotBlank() && currentContentFingerprint.isNotBlank())
    require(expectedServerRevision == null || expectedServerRevision >= 1)
    HistoryCodec.validate(snapshot)
    return SnapshotUploadRequest(deviceId, uploadId, expectedServerRevision, snapshot.runId, snapshot.historySequence,
        currentContentFingerprint, snapshot.formatVersion, snapshot.checksum, HistoryCodec.encodeSnapshot(snapshot))
}

fun snapshotUploadRequest(deviceId: String, snapshot: WorldSnapshot, uploadId: String, expectedServerRevision: Long?,
    currentContentFingerprint: String): SnapshotUploadRequest {
    require(uploadId.isNotBlank() && currentContentFingerprint.isNotBlank())
    require(expectedServerRevision == null || expectedServerRevision >= 1)
    WorldSnapshotCodec.validate(snapshot)
    return SnapshotUploadRequest(deviceId, uploadId, expectedServerRevision, snapshot.runId, snapshot.historySequence,
        currentContentFingerprint, snapshot.worldFormatVersion, snapshot.checksum, WorldSnapshotCodec.encode(snapshot),
        payloadKind = "CURRENT_WORLD")
}

/** Legacy full archives are accepted on download only; every newly staged upload uses CURRENT_WORLD. */
fun SnapshotDownloadResponse.worldSnapshot(): WorldSnapshot {
    require(schemaVersion == 1 && serverRevision >= 1 && currentContentFingerprint.isNotBlank()) { "Invalid snapshot envelope" }
    val snapshot = when (payloadKind) {
        "CURRENT_WORLD" -> WorldSnapshotCodec.decode(snapshotJson)
        null -> if (WorldSnapshotCodec.isCurrentWorld(snapshotJson)) WorldSnapshotCodec.decode(snapshotJson)
            else WorldSnapshotCodec.fromLegacy(HistoryCodec.decodeSnapshot(snapshotJson))
        else -> error("Unsupported snapshot payload kind")
    }
    require(snapshot.runId == gameRunId) { "Snapshot run mismatch" }
    return snapshot
}
