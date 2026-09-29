package ru.nksk.lctapp.domain.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.backend.ParentRewardApplication
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Historical aggregates are separate from the small observable live game state. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class AuditEntry(
    val id: String,
    val sequence: Long,
    val runId: String,
    val type: AuditType,
    val request: EngineRequest? = null,
    val context: DecisionContext? = null,
    val before: GameState? = null,
    val after: GameState? = null,
    val facts: List<AnalyticsFact> = emptyList(),
    val operations: List<LedgerEntry> = emptyList(),
    val formatVersion: Int = HISTORY_FORMAT_VERSION,
    val contentFingerprint: String? = null,
    /** Omitted for old records so decoding/re-encoding keeps their exact historical checksums. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val parentReward: ParentRewardApplication? = null,
    /** A cloud current-world import has an explicit source cursor, without invented historical events. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val worldRestore: WorldRestoreBaseline? = null,
) {
    init {
        require(id.isNotBlank() && runId.isNotBlank() && sequence > 0)
        require(formatVersion == HISTORY_FORMAT_VERSION) { "Unsupported history format" }
        require(facts.map { it.eventId }.distinct().size == facts.size)
        require(operations.map { it.operationId }.distinct().size == operations.size)
        require(contentFingerprint == null || contentFingerprint.isNotBlank())
        require(facts.all { it.gameRunId == runId && it.sequence == sequence })
        require(type != AuditType.COMMAND || (request != null && before != null && after != null))
        require(type != AuditType.COMMAND || facts.all { it.actionId == request?.id })
        require(type != AuditType.FACTS || (before == null && after == null && request == null))
        require(type != AuditType.REJECTED || (request != null && before == null && after == null && operations.isEmpty()))
        require((type == AuditType.PARENT_REWARD) == (parentReward != null))
        require(worldRestore == null || type == AuditType.RESTORED)
        parentReward?.let { application ->
            require(request == null && before != null && after != null)
            require(application.reward.gameRunId == runId)
            require(application.receipt.historyEntryId == id && application.receipt.historySequence == sequence)
            require(facts.all { it.actor == ru.nksk.lctapp.domain.analytics.AnalyticsActor.PARENT })
        }
    }
}

@Serializable
enum class AuditType { INITIALIZED, IMPORTED_BASELINE, COMMAND, TECHNICAL_UPDATE, FACTS, RESTORED, REJECTED, PARENT_REWARD }

/** A transactional lookup of selected facts and their current run/sequence, without world checkpoints. */
data class HistoryFactLookup(val runId: String, val sequence: Long, val facts: List<AnalyticsFact>) {
    init {
        require(runId.isNotBlank() && sequence >= 0)
        require(facts.map { it.eventId }.distinct().size == facts.size)
        require(facts.all { it.gameRunId == runId && it.sequence <= sequence })
    }
}

const val HISTORY_FORMAT_VERSION = 1
/** Format 5 adds a flat list of complete archived runs; formats 1–4 retain their original signatures. */
const val SNAPSHOT_FORMAT_VERSION = 5

/** Global storage sequence closes the gap left by map writes without an engine revision. */
data class RestoreGuard(
    val engineRevision: Long?,
    val historySequence: Long,
    /** Required for an engine snapshot restored into an empty installation. */
    val supportedRulesId: String? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class GameSnapshot(
    val formatVersion: Int = SNAPSHOT_FORMAT_VERSION,
    val runId: String,
    val state: GameState,
    val history: List<AuditEntry>,
    val historySequence: Long,
    val rulesId: String? = state.engine?.rulesId,
    val checksum: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val archivedRuns: List<ArchivedGameRun> = emptyList(),
) {
    init {
        require(formatVersion in 1..SNAPSHOT_FORMAT_VERSION) { "Unsupported snapshot format" }
        require(runId.isNotBlank() && historySequence >= 0 && checksum.isNotBlank())
        require(rulesId == state.engine?.rulesId)
        var previousSequence = 0L
        val historyIds = HashSet<String>()
        history.forEach { entry ->
            require(entry.runId == runId && entry.sequence <= historySequence)
            require(entry.sequence > previousSequence)
            require(historyIds.add(entry.id))
            previousSequence = entry.sequence
        }
        require(formatVersion >= 5 || archivedRuns.isEmpty())
        require(archivedRuns.none { it.snapshot.runId == runId })
        require(archivedRuns.map { it.snapshot.runId }.distinct().size == archivedRuns.size)
        require(archivedRuns.map { it.restartRequestId }.distinct().size == archivedRuns.size)
        require(archivedRuns.map { it.nextRunId }.distinct().size == archivedRuns.size)
    }
}

/** Strict versioned decoding: unknown fields/types never silently become an initial save. */
object HistoryCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; classDiscriminator = "_type" }
    fun encode(entry: AuditEntry): String = json.encodeToString(normalize(entry))
    fun decodeEntry(value: String): AuditEntry = json.decodeFromString(value)
    fun encodeState(state: GameState): String = json.encodeToString(normalize(state))
    fun decodeState(value: String): GameState = json.decodeFromString(value)
    fun encodeRequest(request: EngineRequest): String = json.encodeToString(request.copy(context = request.context?.let(::normalize)))
    fun encodeFact(fact: AnalyticsFact): String = json.encodeToString(fact.copy(context = normalize(fact.context)))
    fun encodeQuestion(question: FinancialQuestion): String = json.encodeToString(question)
    fun decodeQuestion(value: String): FinancialQuestion = json.decodeFromString(value)
    fun encodeSnapshot(snapshot: GameSnapshot): String = buildString {
        writeSnapshot(snapshot) { append(it) }
    }
    fun decodeSnapshot(value: String): GameSnapshot = decodeStoredSnapshot(value).also(::validate)

    /** Room stores canonical entry documents; keep them encoded until a consumer needs one world. */
    fun encodedHistory(entries: List<String>, canonical: Boolean = true,
        checkEntry: (Int, AuditEntry) -> Unit = { _, _ -> }): List<AuditEntry> =
        EncodedAuditHistory(entries.toList(), canonical, checkEntry)

    /**
     * Local archive metadata. Its signature covers the separately stored complete history,
     * so this document is not a standalone transport snapshot and must not pass validate().
     */
    fun encodeArchiveHeader(snapshot: GameSnapshot): String {
        require(snapshot.archivedRuns.isEmpty()) { "An archived run cannot contain other archives" }
        return encodeSnapshot(snapshot.copy(history = emptyList()))
    }

    fun decodeArchiveHeader(value: String): GameSnapshot = decodeStoredSnapshot(value).also {
        require(it.history.isEmpty() && it.archivedRuns.isEmpty()) { "Archive header contains history" }
    }

    /**
     * Sign and check persisted entry documents in bounded pages. Callers must keep
     * state, historySequence and every page in the same read/write transaction. No complete
     * history list or giant snapshot String is created when a campaign is archived.
     */
    suspend fun createArchiveHeader(runId: String, state: GameState, historySequence: Long,
        readPage: suspend (afterSequence: Long, limit: Int) -> List<String>): String {
        require(runId.isNotBlank() && historySequence >= 0)
        val digest = Utf8Digest()
        digest.write("$SNAPSHOT_FORMAT_VERSION\n$runId\n$historySequence\n")
        digest.write(encodeState(state))
        digest.write("\n[")
        val validation = HistoryValidation(runId)
        while (true) {
            val page = readPage(validation.sequence, 16)
            require(page.size <= 16) { "History page exceeds the requested limit" }
            if (page.isEmpty()) break
            page.forEach { encoded ->
                val entry = decodeEntry(encoded)
                require(entry.sequence <= historySequence) { "History changed while archiving" }
                if (validation.sequence > 0) digest.write(",")
                validation.accept(entry)
                // Old persisted rows may omit fields that now have serialized defaults.
                // Match normal snapshot signing and reconstruction, while keeping raw rows
                // unchanged in storage and retaining only this one decoded entry.
                digest.write(encode(entry))
            }
        }
        validation.finish(state, historySequence)
        digest.write("]\n[]")
        return encodeArchiveHeader(GameSnapshot(runId = runId, state = state, history = emptyList(),
            historySequence = historySequence, checksum = digest.finish()))
    }

    /** Decode only the small envelope here; checkpoint objects are released as traversal advances. */
    internal fun decodeStoredSnapshot(value: String): GameSnapshot {
        val fields = SnapshotJsonSlices.objectFields(value)
        val historyRange = fields["history"] ?: error("Snapshot history is missing")
        val archiveRange = fields["archivedRuns"]
        val replacements = listOfNotNull(historyRange, archiveRange).sortedBy { it.first }
        val envelope = buildString {
            var start = 0
            replacements.forEach { range ->
                append(value, start, range.first)
                append("[]")
                start = range.last + 1
            }
            append(value, start, value.length)
        }
        val header = json.decodeFromString<GameSnapshot>(envelope)
        val historySlices = SnapshotJsonSlices.arrayElements(value, historyRange)
        val entries = EncodedAuditHistory(IndexedDecodedList(historySlices.size) { index ->
            value.substring(historySlices[index])
        }, canonical = false)
        val archives = archiveRange?.let { range ->
            val slices = SnapshotJsonSlices.arrayElements(value, range)
            IndexedDecodedList(slices.size) { index ->
                val archived = value.substring(slices[index])
                val parts = SnapshotJsonSlices.objectFields(archived)
                require(parts.keys == setOf("restartRequestId", "nextRunId", "snapshot")) { "Invalid archive fields" }
                ArchivedGameRun(
                    json.decodeFromString(archived.substring(parts.getValue("restartRequestId"))),
                    json.decodeFromString(archived.substring(parts.getValue("nextRunId"))),
                    decodeStoredSnapshot(archived.substring(parts.getValue("snapshot"))),
                )
            }
        } ?: emptyList()
        return header.copy(history = entries, archivedRuns = archives)
    }

    fun snapshot(runId: String, state: GameState, history: List<AuditEntry>, archivedRuns: List<ArchivedGameRun> = emptyList()): GameSnapshot {
        val sequence = history.lastOrNull()?.sequence ?: 0
        return GameSnapshot(runId = runId, state = state, history = history, historySequence = sequence,
            checksum = checksum(runId, state, history, sequence, archivedRuns = archivedRuns), archivedRuns = archivedRuns)
    }

    fun validate(snapshot: GameSnapshot) {
        snapshot.archivedRuns.forEach { validate(it.snapshot) }
        require(snapshot.checksum == checksum(snapshot.runId, snapshot.state, snapshot.history, snapshot.historySequence,
            snapshot.formatVersion, snapshot.archivedRuns)) {
            "Snapshot checksum mismatch"
        }
        val validation = HistoryValidation(snapshot.runId)
        snapshot.history.forEach(validation::accept)
        validation.finish(snapshot.state, snapshot.historySequence)
    }

    /** Only identities and the previous checkpoint survive between entries/pages. */
    private class HistoryValidation(private val runId: String) {
        private val historyIds = HashSet<String>()
        private val factIds = HashSet<String>()
        private val requestIds = HashSet<String>()
        private val operationIds = HashSet<String>()
        private val rewardIds = HashSet<String>()
        private val applicationIds = HashSet<String>()
        private var rewardProfile: String? = null
        private var previous: GameState? = null
        var sequence: Long = 0
            private set

        fun accept(entry: AuditEntry) {
            require(entry.runId == runId) { "History entry belongs to another run" }
            require(entry.sequence == sequence + 1L) { "Snapshot history has gaps" }
            require(historyIds.add(entry.id)) { "Repeated history identity" }
            if (entry.worldRestore != null) {
                rewardIds.clear()
                applicationIds.clear()
                rewardProfile = null
                entry.worldRestore.parentRewards.forEach { reward ->
                    require(reward.reward.gameRunId == entry.runId) { "Restored reward belongs to another run" }
                    require(rewardIds.add(reward.reward.rewardId)) { "Repeated restored reward identity" }
                    require(applicationIds.add(reward.receipt.applicationId)) { "Repeated restored reward application" }
                    require(rewardProfile == null || rewardProfile == reward.reward.profileId) { "Restored rewards belong to different profiles" }
                    rewardProfile = reward.reward.profileId
                }
            }
            entry.facts.forEach { require(factIds.add(it.eventId)) { "Repeated analytics fact identity" } }
            if (entry.type == AuditType.COMMAND) entry.request?.let {
                require(requestIds.add(it.id)) { "Repeated command identity" }
            }
            entry.operations.forEach { require(operationIds.add(it.operationId)) { "Repeated financial receipt identity" } }
            entry.parentReward?.let { reward ->
                require(rewardIds.add(reward.reward.rewardId)) { "Repeated parent reward identity" }
                require(applicationIds.add(reward.receipt.applicationId)) { "Repeated parent reward application" }
                require(rewardProfile == null || rewardProfile == reward.reward.profileId) { "Parent rewards belong to different profiles" }
                rewardProfile = reward.reward.profileId
            }
            if (entry.before != null && previous != null) {
                require(encodeState(entry.before) == encodeState(checkNotNull(previous))) { "Historical checkpoint chain is broken" }
            }
            if (entry.type == AuditType.COMMAND || entry.type == AuditType.PARENT_REWARD) {
                CanonicalLedger.validate(checkNotNull(entry.before), checkNotNull(entry.after), entry.operations)
            }
            if (entry.after != null) previous = entry.after
            sequence = entry.sequence
        }

        fun finish(state: GameState, expectedSequence: Long) {
            require(sequence == expectedSequence) { "Snapshot history sequence mismatch" }
            val tip = previous
            require(tip == null || encodeState(tip) == encodeState(state)) { "Snapshot state differs from history tip" }
        }
    }

    private fun checksum(runId: String, state: GameState, history: List<AuditEntry>, sequence: Long,
        version: Int = SNAPSHOT_FORMAT_VERSION, archivedRuns: List<ArchivedGameRun> = emptyList()): String {
        val digest = Utf8Digest()
        digest.write("$version\n$runId\n$sequence\n")
        digest.write(versioned(encodeState(state), version))
        digest.write("\n")
        writeHistory(history, version, canonicalJson = false, digest::write)
        if (version >= 5) {
            digest.write("\n")
            writeArchives(archivedRuns, digest::write)
        }
        return digest.finish()
    }

    /** Emit one checkpoint at a time; never retain JSON for the whole history or all archives. */
    private fun writeHistory(history: List<AuditEntry>, version: Int, canonicalJson: Boolean,
        write: (String) -> Unit) {
        write("[")
        history.indices.forEach { index ->
            if (index > 0) write(",")
            val encoded = versioned((history as? EncodedAuditHistory)?.canonicalEntry(index) ?: encode(history[index]), version)
            // Snapshot JSON previously passed through JsonElement.toString(), whereas
            // the signed history for formats 3+ used the serializer output directly.
            write(if (canonicalJson) json.parseToJsonElement(encoded).toString() else encoded)
        }
        write("]")
    }

    private fun writeSnapshot(snapshot: GameSnapshot, write: (String) -> Unit) {
        // Let the existing serializer retain field order, defaults and legacy filtering.
        // Only this single state is materialized; history entries are emitted below.
        val header = json.parseToJsonElement(versioned(json.encodeToString(snapshot.copy(
            state = normalize(snapshot.state), history = emptyList(), archivedRuns = emptyList())),
            snapshot.formatVersion)).jsonObject
        write("{")
        header.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) write(",")
            write(JsonPrimitive(key).toString())
            write(":")
            if (key == "history") writeHistory(snapshot.history, snapshot.formatVersion, canonicalJson = true, write)
            else write(value.toString())
        }
        if (snapshot.archivedRuns.isNotEmpty()) {
            write(",\"archivedRuns\":")
            writeArchives(snapshot.archivedRuns, write)
        }
        write("}")
    }

    private fun writeArchives(archivedRuns: List<ArchivedGameRun>, write: (String) -> Unit) {
        write("[")
        archivedRuns.forEachIndexed { index, archive ->
            if (index > 0) write(",")
            write("{\"restartRequestId\":")
            write(JsonPrimitive(archive.restartRequestId).toString())
            write(",\"nextRunId\":")
            write(JsonPrimitive(archive.nextRunId).toString())
            write(",\"snapshot\":")
            // An archived format1/2 keeps its own wire shape inside a format5 parent.
            writeSnapshot(archive.snapshot, write)
            write("}")
        }
        write("]")
    }

    private fun versioned(encoded: String, version: Int): String {
        if (version >= 3) return encoded
        val v2 = legacyV2(json.parseToJsonElement(encoded))
        return (if (version == 1) legacyV1(v2) else v2).toString()
    }

    private fun legacyV2(value: JsonElement): JsonElement = when (value) {
        is JsonArray -> JsonArray(value.map(::legacyV2))
        is JsonObject -> JsonObject(value.entries.mapNotNull { (key, child) ->
            if (key == "selectedSavingItemId") { require(child == JsonNull); null }
            else key to legacyV2(child)
        }.toMap())
        else -> value
    }

    /** The signed v1 shape had none of these fields. Only absent/default legacy data may be omitted. */
    private fun legacyV1(value: JsonElement): JsonElement = when (value) {
        is JsonArray -> JsonArray(value.map(::legacyV1))
        is JsonObject -> JsonObject(value.entries.mapNotNull { (key, child) ->
            when (key) {
                "eventHistory" -> { require(child == JsonArray(emptyList())); null }
                "savingPractice", "reviewEvidence" -> { require(child == JsonNull); null }
                "applications" -> if (value["_type"] == JsonPrimitive("reserve_decision")) {
                    require(child == JsonArray(emptyList())); null
                } else key to legacyV1(child)
                else -> key to legacyV1(child)
            }
        }.toMap())
        else -> value
    }

    // Sets carry no order; Room can return them in a different order after restore.
    // Lists (including repeated inventory and decisions) retain every original position.
    private fun normalize(state: GameState) = state.copy(completedMiniGames = state.completedMiniGames.sorted().toSet())
    private fun normalize(context: DecisionContext) = context.copy(assistance = context.assistance.sortedBy { it.name }.toSet())
    private fun normalize(entry: AuditEntry) = entry.copy(
        request = entry.request?.let { it.copy(context = it.context?.let(::normalize)) },
        context = entry.context?.let(::normalize), before = entry.before?.let(::normalize), after = entry.after?.let(::normalize),
        facts = entry.facts.map { it.copy(context = normalize(it.context)) },
    )

    fun sha256(value: String): String = Utf8Digest().apply { write(value) }.finish()

    /** String.getBytes(UTF_8) semantics without a byte array proportional to the input. */
    private class Utf8Digest {
        private val digest = MessageDigest.getInstance("SHA-256")
        private val bytes = ByteBuffer.allocate(8 * 1024)
        // Target Buffer methods, available on older Android, rather than JDK 9+ covariant overrides.
        private val cursor: Buffer = bytes
        private val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(byteArrayOf('?'.code.toByte()))

        fun write(value: String) {
            // Fragments are complete JSON tokens/documents or newline-separated fields,
            // so a surrogate pair never crosses two calls. It may cross buffer fills.
            val chars = CharBuffer.wrap(value)
            encoder.reset()
            do {
                val result = encoder.encode(chars, bytes, true)
                drain()
                if (result.isError) result.throwException()
            } while (result.isOverflow)
            do {
                val result = encoder.flush(bytes)
                drain()
                if (result.isError) result.throwException()
            } while (result.isOverflow)
        }

        private fun drain() {
            cursor.flip()
            digest.update(bytes)
            cursor.clear()
        }

        fun finish(): String = digest.digest().joinToString("") { "%02x".format(it) }
    }
}
