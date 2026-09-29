package ru.nksk.lctapp.domain.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import ru.nksk.lctapp.domain.backend.ParentRewardApplication
import ru.nksk.lctapp.domain.game.GameState

/** Cloud backup contains the current aggregate, never local audit checkpoints or archived worlds. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class WorldSnapshot(
    val worldFormatVersion: Int = 1,
    val runId: String,
    val state: GameState,
    /** Server evidence cursor; it can differ from the local Room sequence after a world-only restore. */
    val historySequence: Long,
    val generation: String,
    val parentRewards: List<ParentRewardApplication> = emptyList(),
    val predecessors: List<CloudWorldAncestor> = emptyList(),
    val checksum: String,
    /** Compatibility marker produced only when reading a validated full archive before format 4. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val legacyBudgetModel: Boolean = false,
) {
    init {
        require(worldFormatVersion == 1 && runId.isNotBlank() && generation.isNotBlank())
        require(historySequence >= 0 && checksum.isNotBlank())
        require(parentRewards.all { it.reward.gameRunId == runId })
        require(parentRewards.map { it.reward.rewardId }.distinct().size == parentRewards.size)
        require(parentRewards.map { it.receipt.applicationId }.distinct().size == parentRewards.size)
        require(parentRewards.map { it.reward.profileId }.distinct().size <= 1)
        require(predecessors.none { it.runId == runId })
        require(predecessors.map { it.runId }.distinct().size == predecessors.size)
    }
}

/** Restart lineage only; no previous world's state or journal travels in a cloud upload. */
@Serializable
data class CloudWorldAncestor(val runId: String, val generation: String, val historySequence: Long) {
    init { require(runId.isNotBlank() && generation.isNotBlank() && historySequence >= 0) }
}

/** Explicitly marks missing remote history. Receipts prove prior grants, never fabricated child evidence. */
@Serializable
data class WorldRestoreBaseline(
    val sourceHistorySequence: Long,
    val sourceGeneration: String,
    val sourceChecksum: String,
    val restoreRequestId: String,
    val parentRewards: List<ParentRewardApplication> = emptyList(),
    val predecessors: List<CloudWorldAncestor> = emptyList(),
) {
    init {
        require(sourceHistorySequence >= 0)
        require(listOf(sourceGeneration, sourceChecksum, restoreRequestId).all(String::isNotBlank))
        require(parentRewards.map { it.reward.rewardId }.distinct().size == parentRewards.size)
    }
}

/** A coherent capture; [world] uses transport sequences, guards continue using local sequences. */
data class CloudWorldRead(
    val world: WorldSnapshot,
    val localHistorySequence: Long,
    val latestHistoryId: String?,
    val baselineSequence: Long? = null,
    val sourceHistorySequence: Long = 0,
) {
    val generation: String get() = world.generation
    init {
        require(localHistorySequence >= 0 && sourceHistorySequence >= 0)
        require(baselineSequence == null || baselineSequence in 1..localHistorySequence)
        require(world.historySequence == transportSequence(localHistorySequence))
    }
    fun transportSequence(localSequence: Long): Long = baselineSequence?.let {
        require(localSequence >= it) { "Evidence precedes the restored world baseline" }
        Math.addExact(sourceHistorySequence, localSequence - it)
    } ?: localSequence
}

/** Implementations may lazily decode immutable Room rows; no full world export is involved. */
data class CloudEvidenceRead(val head: CloudWorldRead, val history: List<AuditEntry>)

data class CloudRestoreReceipt(
    val runId: String,
    val generation: String,
    val restoreRequestId: String,
    val sourceChecksum: String,
)

/** Separately versioned strict contract. Canonical state encoding is shared with local persistence. */
object WorldSnapshotCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; classDiscriminator = "_type" }

    fun create(runId: String, state: GameState, historySequence: Long, generation: String,
        parentRewards: List<ParentRewardApplication> = emptyList(),
        predecessors: List<CloudWorldAncestor> = emptyList()): WorldSnapshot {
        val unsigned = WorldSnapshot(runId = runId, state = canonicalState(state), historySequence = historySequence,
            generation = generation, parentRewards = parentRewards, predecessors = predecessors, checksum = "pending")
        return unsigned.copy(checksum = checksum(unsigned))
    }

    fun encode(snapshot: WorldSnapshot): String {
        validate(snapshot)
        return json.encodeToString(snapshot.copy(state = canonicalState(snapshot.state)))
    }

    fun decode(value: String): WorldSnapshot = json.decodeFromString<WorldSnapshot>(value).also(::validate)
    fun isCurrentWorld(value: String): Boolean = "worldFormatVersion" in SnapshotJsonSlices.objectFields(value)
    fun decodeBaseline(value: String): WorldRestoreBaseline = json.decodeFromString(value)
    fun encodeBaseline(value: WorldRestoreBaseline): String = json.encodeToString(value)
    fun decodeParentReward(value: String): ParentRewardApplication = json.decodeFromString(value)

    fun validate(snapshot: WorldSnapshot) {
        require(snapshot.checksum == checksum(snapshot)) { "World snapshot checksum mismatch" }
    }

    /** Read compatibility only: a previous full cloud archive becomes a compact current-world candidate. */
    fun fromLegacy(snapshot: GameSnapshot): WorldSnapshot {
        HistoryCodec.validate(snapshot)
        val world = snapshot.toCloudWorldRead().world
        return if (snapshot.formatVersion < 4) withLegacyBudgetModel(world) else world
    }

    fun withLegacyBudgetModel(snapshot: WorldSnapshot): WorldSnapshot = snapshot.copy(legacyBudgetModel = true).let {
        it.copy(checksum = checksum(it))
    }

    private fun checksum(snapshot: WorldSnapshot): String = HistoryCodec.sha256(
        json.encodeToString(snapshot.copy(state = canonicalState(snapshot.state), checksum = "unsigned")),
    )
    private fun canonicalState(state: GameState): GameState = HistoryCodec.decodeState(HistoryCodec.encodeState(state))
}

/** Compatibility for in-memory repositories and legacy download conversion, not the production read path. */
fun GameSnapshot.toCloudWorldRead(): CloudWorldRead {
    val baselineEntry = history.lastOrNull { it.worldRestore != null }
    val baseline = baselineEntry?.worldRestore
    val rewards = linkedMapOf<String, ParentRewardApplication>()
    baseline?.parentRewards.orEmpty().forEach { rewards[it.reward.rewardId] = it }
    history.forEach { entry ->
        if (entry.sequence > (baselineEntry?.sequence ?: 0)) entry.parentReward?.let {
            val previous = rewards.putIfAbsent(it.reward.rewardId, it)
            require(previous == null || previous == it) { "Conflicting parent reward receipt" }
        }
    }
    val predecessors = linkedMapOf<String, CloudWorldAncestor>()
    baseline?.predecessors.orEmpty().forEach { predecessors[it.runId] = it }
    var nextRun = runId
    val visited = hashSetOf(runId)
    while (true) {
        val archive = archivedRuns.firstOrNull { it.nextRunId == nextRun } ?: break
        val prior = archive.snapshot
        require(visited.add(prior.runId)) { "Cyclic archived game runs" }
        val priorBaseline = prior.history.lastOrNull { it.worldRestore != null }
        priorBaseline?.worldRestore?.predecessors.orEmpty().forEach { inherited ->
            predecessors.putIfAbsent(inherited.runId, inherited)
        }
        val sequence = priorBaseline?.let { Math.addExact(checkNotNull(it.worldRestore).sourceHistorySequence,
            prior.historySequence - it.sequence) } ?: prior.historySequence
        predecessors[prior.runId] = CloudWorldAncestor(prior.runId, prior.localGeneration(), sequence)
        nextRun = prior.runId
    }
    val sequence = baselineEntry?.let { Math.addExact(checkNotNull(baseline).sourceHistorySequence,
        historySequence - it.sequence) } ?: historySequence
    return CloudWorldRead(WorldSnapshotCodec.create(runId, state, sequence, localGeneration(),
        rewards.values.toList(), predecessors.values.toList()), historySequence, history.lastOrNull()?.id,
        baselineEntry?.sequence, baseline?.sourceHistorySequence ?: 0)
}
