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
        require(history.all { it.runId == runId && it.sequence <= historySequence })
        require(history.zipWithNext().all { (a, b) -> a.sequence < b.sequence })
        require(history.map { it.id }.distinct().size == history.size)
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
    fun encodeSnapshot(snapshot: GameSnapshot): String {
        val value = json.parseToJsonElement(json.encodeToString(snapshot.copy(
            state = normalize(snapshot.state), history = snapshot.history.map(::normalize), archivedRuns = emptyList()))).jsonObject
        // Keep old inner wire versions exactly as their own codec emits them. A parent
        // format5 must not reintroduce new default fields into an archived format1.
        val encoded = if (snapshot.archivedRuns.isEmpty()) value else
            JsonObject(value + ("archivedRuns" to archivePayload(snapshot.archivedRuns)))
        return versioned(encoded.toString(), snapshot.formatVersion)
    }
    fun decodeSnapshot(value: String): GameSnapshot = json.decodeFromString<GameSnapshot>(value).also(::validate)

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
        require(snapshot.historySequence == (snapshot.history.lastOrNull()?.sequence ?: 0))
        require(snapshot.history.withIndex().all { (index, entry) -> entry.sequence == index.toLong() + 1L }) {
            "Snapshot history has gaps"
        }
        val factIds = snapshot.history.flatMap { it.facts }.map { it.eventId }
        require(factIds.distinct().size == factIds.size) { "Repeated analytics fact identity" }
        val requestIds = snapshot.history.filter { it.type == AuditType.COMMAND }.mapNotNull { it.request?.id }
        require(requestIds.distinct().size == requestIds.size) { "Repeated command identity" }
        val operationIds = snapshot.history.flatMap { it.operations }.map { it.operationId }
        require(operationIds.distinct().size == operationIds.size) { "Repeated financial receipt identity" }
        val rewards = snapshot.history.mapNotNull { it.parentReward }
        require(rewards.map { it.reward.rewardId }.distinct().size == rewards.size) { "Repeated parent reward identity" }
        require(rewards.map { it.receipt.applicationId }.distinct().size == rewards.size) { "Repeated parent reward application" }
        require(rewards.map { it.reward.profileId }.distinct().size <= 1) { "Parent rewards belong to different profiles" }
        var previous: GameState? = null
        snapshot.history.forEach { entry ->
            if (entry.before != null && previous != null) {
                require(encodeState(entry.before) == encodeState(checkNotNull(previous))) { "Historical checkpoint chain is broken" }
            }
            if (entry.type == AuditType.COMMAND || entry.type == AuditType.PARENT_REWARD) {
                CanonicalLedger.validate(checkNotNull(entry.before), checkNotNull(entry.after), entry.operations)
            }
            if (entry.after != null) previous = entry.after
        }
        val tip = snapshot.history.lastOrNull { it.after != null }?.after
        require(tip == null || encodeState(tip) == encodeState(snapshot.state)) { "Snapshot state differs from history tip" }
    }

    private fun checksum(runId: String, state: GameState, history: List<AuditEntry>, sequence: Long,
        version: Int = SNAPSHOT_FORMAT_VERSION, archivedRuns: List<ArchivedGameRun> = emptyList()): String {
        val original = "$version\n$runId\n$sequence\n${versioned(encodeState(state), version)}\n${versioned(json.encodeToString(history.map(::normalize)), version)}"
        return sha256(if (version < 5) original else "$original\n${archivePayload(archivedRuns)}")
    }

    private fun archivePayload(archivedRuns: List<ArchivedGameRun>) = JsonArray(archivedRuns.map { archive -> buildJsonObject {
            put("restartRequestId", archive.restartRequestId)
            put("nextRunId", archive.nextRunId)
            put("snapshot", json.parseToJsonElement(encodeSnapshot(archive.snapshot)))
        } })

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

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
