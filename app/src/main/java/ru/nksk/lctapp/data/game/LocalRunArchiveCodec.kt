package ru.nksk.lctapp.data.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec

/**
 * Room-only archive envelope, not a transport snapshot. Archiving signs the current world
 * and its cursor without decoding the immutable audit rows copied by the same transaction.
 * Full history validation and the transport signature are deferred until an archive is read.
 * Existing signed snapshot headers and their original audit documents are never rewritten.
 */
internal object LocalRunArchiveCodec {
    private const val StorageVersion = 1
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    @Serializable
    private data class StoredHeader(
        val archiveStorageVersion: Int,
        val runId: String,
        val state: JsonElement,
        val historySequence: Long,
        val checksum: String,
    )

    fun create(runId: String, state: GameState, historySequence: Long): String {
        require(runId.isNotBlank() && historySequence >= 0)
        val encodedState = HistoryCodec.encodeState(state)
        return json.encodeToString(StoredHeader(StorageVersion, runId,
            json.parseToJsonElement(encodedState), historySequence,
            checksum(runId, encodedState, historySequence)))
    }

    fun decode(payload: String): Header {
        val fields = json.parseToJsonElement(payload).jsonObject
        if ("archiveStorageVersion" !in fields) {
            // Earlier Room v23 archives retain their full-history signature and format version.
            val legacy = HistoryCodec.decodeArchiveHeader(payload)
            return Header(legacy.runId, legacy.state, legacy.historySequence, legacy)
        }
        val stored = json.decodeFromJsonElement<StoredHeader>(fields)
        require(stored.archiveStorageVersion == StorageVersion) { "Unsupported local archive format" }
        require(stored.runId.isNotBlank() && stored.historySequence >= 0)
        val state = HistoryCodec.decodeState(stored.state.toString())
        require(stored.checksum == checksum(stored.runId, HistoryCodec.encodeState(state), stored.historySequence)) {
            "Local archive header checksum mismatch"
        }
        return Header(stored.runId, state, stored.historySequence)
    }

    internal class Header internal constructor(
        val runId: String,
        val state: GameState,
        val historySequence: Long,
        private val legacy: GameSnapshot? = null,
    ) {
        /** Materialization is explicit: metadata listing and rewind retries never read history. */
        fun snapshot(history: List<AuditEntry>): GameSnapshot {
            require((history.lastOrNull()?.sequence ?: 0L) == historySequence) {
                "Archived history sequence mismatch"
            }
            val snapshot = legacy?.copy(history = history) ?: HistoryCodec.snapshot(runId, state, history)
            HistoryCodec.validate(snapshot)
            return snapshot
        }
    }

    private fun checksum(runId: String, encodedState: String, historySequence: Long): String =
        HistoryCodec.sha256("local-run-archive\n$StorageVersion\n${JsonPrimitive(runId)}\n$historySequence\n$encodedState")
}
