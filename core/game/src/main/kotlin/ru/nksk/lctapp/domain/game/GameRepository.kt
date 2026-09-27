package ru.nksk.lctapp.domain.game

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.CampaignReconciliation
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.RestoreGuard
import ru.nksk.lctapp.domain.history.HistorySourceGuard
import ru.nksk.lctapp.domain.pet.withStarterAccessoryOwnership

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
    fun observeHistory(): Flow<List<AuditEntry>> = flowOf(emptyList())
    /** A non-null sourceGuard is checked atomically with insertion, including retries after restore. */
    suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard? = null) { error("History storage is unavailable") }
    suspend fun pendingOutbox(limit: Int = 100): List<AuditEntry> = emptyList()
    suspend fun acknowledgeOutbox(ids: Set<String>) { error("History storage is unavailable") }
    suspend fun exportSnapshot(): GameSnapshot = error("Snapshot storage is unavailable")
    suspend fun restoreSnapshot(snapshot: GameSnapshot, expected: RestoreGuard): GameState =
        error("Snapshot storage is unavailable")
}
