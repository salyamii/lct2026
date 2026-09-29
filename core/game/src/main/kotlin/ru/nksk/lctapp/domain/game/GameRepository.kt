package ru.nksk.lctapp.domain.game

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.CampaignReconciliation
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.HistoryFactLookup
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.GameSnapshotHead
import ru.nksk.lctapp.domain.history.CloudWorldRead
import ru.nksk.lctapp.domain.history.CloudEvidenceRead
import ru.nksk.lctapp.domain.history.CloudRestoreReceipt
import ru.nksk.lctapp.domain.history.WorldSnapshot
import ru.nksk.lctapp.domain.history.toCloudWorldRead
import ru.nksk.lctapp.domain.history.RestoreGuard
import ru.nksk.lctapp.domain.history.HistorySourceGuard
import ru.nksk.lctapp.domain.history.CampaignRestartRequest
import ru.nksk.lctapp.domain.history.ArchivedGameRunSummary
import ru.nksk.lctapp.domain.pet.withStarterAccessoryOwnership
import ru.nksk.lctapp.domain.backend.ParentRewardDto
import ru.nksk.lctapp.domain.backend.ParentRewardReceiptDto

/** The only write boundary for the saved game. Storage errors propagate without resetting it. */
interface GameRepository {
    fun observe(): Flow<GameState?>
    suspend fun read(): GameState?
    suspend fun initializeIfAbsent(initial: GameState): GameState

    /** Reconcile old starter ownership after its definitions are installed. Storage may recover the initial choice from history. */
    suspend fun synchronizeStarterAccessory(): GameState = update { current ->
        val next = current.withStarterAccessoryOwnership()
        if (next == current) current else next.copy(engine = next.engine?.copy(
            revision = Math.addExact(next.engine.revision, 1L),
        ))
    }

    /**
     * Reads the latest aggregate inside the write transaction, then atomically stores the result.
     * Transform must be fast, pure, and derive changes from its argument, never a cached UI copy.
     * Gameplay operations own their guards and retry/occurrence semantics; this is not an event engine.
     */
    suspend fun update(transform: (GameState) -> GameState): GameState

    /** Resolve legacy chapter bindings from the latest save; only this narrow patch may rebind its open period. */
    suspend fun reconcileCampaign(reconciliation: (GameState) -> CampaignReconciliation): GameState =
        update { current -> reconciliation(current).applyTo(current) }

    /** State, audit checkpoints, decision facts and delivery receipt commit together. */
    suspend fun commit(request: EngineRequest, context: DecisionContext? = null,
        contentFingerprint: String? = null,
        facts: (before: GameState, after: GameState, runId: String, sequence: Long) -> List<AnalyticsFact> = { _, _, _, _ -> emptyList() },
        transform: (GameState) -> GameState): GameState = update(transform)

    /** Durable rejected intent; it is never an applied command or a financial outcome. */
    suspend fun recordRejected(request: EngineRequest, reasonName: String, contentFingerprint: String? = null) { }

    /** History is loaded explicitly; it never inflates observe() emissions. */
    suspend fun readHistory(): List<AuditEntry> = emptyList()
    /** One committed receipt from the current run, preserving its original before/after and context. */
    suspend fun readCommandReceipt(requestId: String): AuditEntry? {
        require(requestId.isNotBlank())
        return readHistory().lastOrNull { it.type == AuditType.COMMAND && it.request?.id == requestId }
    }
    /** Latest command only; observations/rejections do not invalidate its presentation evidence. */
    suspend fun readLatestCommand(): AuditEntry? = readHistory().lastOrNull { it.type == AuditType.COMMAND }
    /** Relevant expense/recovery documents only; not a truncated or sampled history. */
    suspend fun readExpenseRecoveryHistory(): List<AuditEntry> = readHistory().filter { entry ->
        entry.facts.any { it.detail is ru.nksk.lctapp.domain.analytics.FactDetail.UnexpectedExpense ||
            it.detail is ru.nksk.lctapp.domain.analytics.FactDetail.RecoveryAction }
    }
    /** Complete ordered tail from the confirmation that introduced this plan, including non-command entries. */
    suspend fun readBudgetPlanHistory(planId: String): List<AuditEntry> {
        require(planId.isNotBlank())
        val history = readHistory()
        val anchor = history.firstOrNull { it.request?.command is ru.nksk.lctapp.domain.engine.EngineCommand.ConfirmBudget &&
            it.before?.financial?.plans?.none { plan -> plan.id == planId } == true &&
            it.after?.financial?.plans?.lastOrNull()?.id == planId } ?: return emptyList()
        return history.filter { it.sequence >= anchor.sequence }
    }
    /** Lightweight revision marker for sync; no world snapshots need to be decoded. */
    suspend fun latestHistoryId(): String? = readHistory().lastOrNull()?.id
    /** Only the requested facts plus coherent run/sequence metadata; storage uses its fact-ID index. */
    suspend fun readFacts(eventIds: Set<String>): HistoryFactLookup? {
        val history = readHistory()
        val latest = history.lastOrNull() ?: return null
        return HistoryFactLookup(latest.runId, latest.sequence,
            history.flatMap { it.facts }.filter { it.eventId in eventIds })
    }
    /**
     * Commands begun on one game day, coherent with the current checkpoint, or null
     * if the world/history do not match. Storage implementations filter before decoding.
     * This read never edits, repairs or discards historical records.
     */
    suspend fun readDayHistory(day: Int): List<AuditEntry>? {
        require(day > 0)
        val state = read() ?: return null
        val entries = readHistory()
        val latest = entries.lastOrNull { it.after != null }?.after
        if (latest != null && HistoryCodec.encodeState(state) != HistoryCodec.encodeState(latest)) return null
        if (entries.zipWithNext().any { (a, b) -> a.sequence >= b.sequence || a.runId != b.runId }) return null
        return entries.filter { it.type == AuditType.COMMAND && it.before?.engine?.day == day }
    }
    fun observeHistory(): Flow<List<AuditEntry>> = flowOf(emptyList())
    /** Lightweight wake-up signal; transport acknowledgements are not world changes. */
    fun observeHistorySequence(): Flow<Long> = observeHistory().map { it.lastOrNull()?.sequence ?: 0L }
    /** A non-null sourceGuard is checked atomically with insertion, including retries after restore. */
    suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard? = null) { error("History storage is unavailable") }
    suspend fun pendingOutbox(limit: Int = 100): List<AuditEntry> = emptyList()
    suspend fun acknowledgeOutbox(ids: Set<String>) { error("History storage is unavailable") }
    suspend fun acknowledgeOutboxThrough(runId: String, generation: String, throughSequence: Long): Boolean {
        val head = readCloudWorld() ?: return false
        if (head.world.runId != runId || head.generation != generation || throughSequence > head.localHistorySequence) return false
        acknowledgeOutbox(readHistory().filter { it.runId == runId && it.sequence <= throughSequence }.map { it.id }.toSet())
        return true
    }
    /** Latest-world merge and its receipt are committed together; only returned receipts may be acknowledged. */
    suspend fun applyParentRewards(profileId: String, gameRunId: String, rewards: List<ParentRewardDto>,
        expectedRestoreGeneration: String): List<ParentRewardReceiptDto> = error("Parent reward storage is unavailable")
    suspend fun exportSnapshot(): GameSnapshot = error("Snapshot storage is unavailable")
    /** Coherent current world/run/cursor only; production storage does not export history for this read. */
    suspend fun readSnapshotHead(): GameSnapshotHead? {
        if (read() == null) return null
        val snapshot = exportSnapshot()
        return GameSnapshotHead(snapshot.runId, snapshot.state, snapshot.historySequence)
    }
    /** Cloud transfer reads current state and compact receipts only, never a local history export. */
    suspend fun readCloudWorld(): CloudWorldRead? = if (read() == null) null else exportSnapshot().toCloudWorldRead()
    /** Coherent lazy history source for the separate evidence channel. */
    suspend fun readCloudEvidence(): CloudEvidenceRead? {
        if (read() == null) return null
        val snapshot = exportSnapshot()
        return CloudEvidenceRead(snapshot.toCloudWorldRead(), snapshot.history)
    }
    /** Only for a definitely rejected pending batch of a preserved predecessor, never the ordinary sync path. */
    suspend fun readArchivedCloudEvidence(runId: String): CloudEvidenceRead? = archivedRun(runId)?.let {
        CloudEvidenceRead(it.toCloudWorldRead(), it.history)
    }
    suspend fun restoreCloudWorld(world: WorldSnapshot, expected: RestoreGuard, restoreRequestId: String): GameState =
        error("Cloud world restore storage is unavailable")
    suspend fun findCloudRestoreReceipt(restoreRequestId: String, source: WorldSnapshot? = null): CloudRestoreReceipt? = null
    suspend fun cloudContains(world: WorldSnapshot): Boolean = readCloudWorld()?.world?.checksum == world.checksum
    suspend fun archivedRuns(): List<ArchivedGameRunSummary> = emptyList()
    suspend fun archivedRun(runId: String): GameSnapshot? = null
    /** Archive the completed run and atomically leave durable character setup with no active game. */
    suspend fun prepareCampaignRestart(request: CampaignRestartRequest,
        validateCurrent: (GameState) -> Unit): Unit = error("Campaign setup storage is unavailable")
    /** Archive and new baseline commit atomically; transform validates the latest completed world. */
    suspend fun restartCampaign(request: CampaignRestartRequest,
        transform: (current: GameState, initial: GameState?) -> GameState): GameState =
        error("Campaign restart storage is unavailable")
    suspend fun restoreSnapshot(snapshot: GameSnapshot, expected: RestoreGuard): GameState =
        error("Snapshot storage is unavailable")
}
