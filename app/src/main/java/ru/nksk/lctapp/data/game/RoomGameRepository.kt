package ru.nksk.lctapp.data.game

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nksk.lctapp.domain.engine.CompletedGoalProject
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.data.game.local.*
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.withStarterAccessoryOwnership
import java.util.UUID

internal class RoomGameRepository @Inject constructor(private val database: GameDatabase) : GameRepository {
    private val dao = database.gameStateDao()
    private val history = database.gameHistoryDao()
    private val finance = database.financialProgressDao()
    private val budgetPreparation = Mutex()
    @Volatile private var budgetModelReady = false

    override fun observe(): Flow<GameState?> = database.invalidationTracker
        .createFlow("GAME_STATE", "BUDGET_PLANNING", "OWNED_ITEM", "PLAYER_DECISION", "ENGINE_STATE", "ENGINE_EVENT", "ENGINE_DEED", "MINI_GAME_COMPLETION", "GOAL_SELECTION", "SAVING_GOAL_SELECTION", "COMPLETED_GOAL_PROJECT", "DAY_JOURNAL", "FINANCIAL_PERIOD", "FINANCIAL_CURSOR", "BUDGET_PLAN_REVISION", "FINANCIAL_PRACTICE", "EVENT_EXPOSURE")
        .map { read() }
        .distinctUntilChanged()

    override suspend fun read(): GameState? = withLiveBudgetRead { readInTransaction() }

    override suspend fun initializeIfAbsent(initial: GameState): GameState = withLiveBudgetWrite {
        readInTransaction() ?: run {
            dao.insertState(initial.toEntity())
            writeChildren(initial)
            database.onboardingDraftDao().clear()
            checkNotNull(readInTransaction()).also { saved ->
                val run = ensureRun()
                appendAudit(AuditEntry("initialize:${run.runId}", 1, run.runId, AuditType.INITIALIZED, after = saved))
            }
        }
    }

    override suspend fun synchronizeStarterAccessory(): GameState = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        // An initial checkpoint survives later outfit changes. With no such checkpoint, only a
        // currently worn starter is evidence; neither a shop cosmetic nor all starter options are granted.
        val startingLook = history.initialization(run.runId)?.decode()?.after?.pet?.selectedLookId
            ?: current.pet.selectedLookId
        val reconciled = current.withStarterAccessoryOwnership(startingLook)
        if (reconciled == current) return@withLiveBudgetWrite current
        val next = reconciled.copy(engine = reconciled.engine?.let { engine ->
            engine.copy(revision = Math.addExact(engine.revision, 1L))
        })
        persistGame(next)
        val saved = checkNotNull(readInTransaction())
        appendAudit(AuditEntry("starter-accessory:${UUID.randomUUID()}", nextSequence(), run.runId,
            AuditType.TECHNICAL_UPDATE, before = current, after = saved))
        saved
    }

    override suspend fun update(transform: (GameState) -> GameState): GameState = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        val next = transform(current)
        if (next == current) return@withLiveBudgetWrite current
        persistGame(next)
        val saved = checkNotNull(readInTransaction())
        appendAudit(AuditEntry("technical:${UUID.randomUUID()}", nextSequence(), run.runId,
            AuditType.TECHNICAL_UPDATE, before = current, after = saved))
        saved
    }

    override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
        facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>,
        transform: (GameState) -> GameState): GameState = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        val identity = "command:${run.runId}:${request.id}"
        history.find(identity)?.let { prior ->
            val entry = prior.decode()
            val priorRequest = entry.request
            require(priorRequest != null && HistoryCodec.encodeRequest(priorRequest) == HistoryCodec.encodeRequest(request) &&
                entry.context == context && entry.contentFingerprint == contentFingerprint) { "Conflicting command identity" }
            return@withLiveBudgetWrite current
        }
        val next = transform(current)
        val sequence = nextSequence()
        val committedFacts = facts(current, next, run.runId, sequence).map {
            it.copy(gameRunId = run.runId, sequence = sequence)
        }
        require(committedFacts.all { it.actionId == request.id }) { "Facts must refer to their committed command" }
        persistGame(next)
        val saved = checkNotNull(readInTransaction())
        appendAudit(AuditEntry(identity, sequence, run.runId, AuditType.COMMAND,
            request, context, current, saved, committedFacts, CanonicalLedger.fromTransition(current, saved, request),
            contentFingerprint = contentFingerprint))
        saved
    }

    private suspend fun persistGame(next: GameState) {
        check(dao.updateState(next.toEntity()) == 1) { "Saved game disappeared during update" }
        dao.deleteBudgetPlanning(CURRENT_GAME_ID)
        dao.deleteEventExposure(CURRENT_GAME_ID)
        dao.deleteMiniGameCompletions(CURRENT_GAME_ID)
        dao.deleteGoalSelection(CURRENT_GAME_ID)
        dao.deleteSavingGoalSelection(CURRENT_GAME_ID)
        dao.deleteCompletedGoalProjects(CURRENT_GAME_ID)
        dao.deleteDecisions(CURRENT_GAME_ID)
        dao.deleteOwnedItems(CURRENT_GAME_ID)
        dao.deleteEngineEvents(CURRENT_GAME_ID)
        dao.deleteEngineDeeds(CURRENT_GAME_ID)
        dao.deleteDayJournal(CURRENT_GAME_ID)
        dao.deleteEngine(CURRENT_GAME_ID)
        writeChildren(next)
    }

    private suspend fun readInTransaction(): GameState? {
        val rows = dao.readStates()
        check(rows.size <= 1) { "Multiple saved games are not supported" }
        val row = rows.singleOrNull() ?: return null
        check(row.id == CURRENT_GAME_ID) { "Unknown saved game identity: ${row.id}" }
        return row.toDomain(dao.readDecisions(row.id), dao.readOwnedItems(row.id), dao.readBudgetPlanning(row.id)).copy(
            completedMiniGames = dao.readMiniGameCompletions(row.id).map { it.attemptId }.toSet(),
            selectedGoalId = dao.readGoalSelection(row.id)?.goalId,
            selectedSavingItemId = dao.readSavingGoalSelection(row.id)?.itemId,
            completedGoalProjects = dao.readCompletedGoalProjects(row.id).map { CompletedGoalProject(it.goalId, it.decisionId) },
            engine = dao.readEngine(row.id)?.toDomain(dao.readEngineEvents(row.id), dao.readEngineDeeds(row.id), dao.readDayJournal(row.id)),
            eventHistory = dao.readEventExposure(row.id).map { it.toDomain() },
            financial = FinancialProgress(
                currentPeriodId = finance.cursor(row.id)?.periodId,
                periods = finance.periods(row.id).map { it.toDomain() },
                plans = finance.plans(row.id).map { it.toDomain() },
                practice = finance.practice(row.id)?.let { stored ->
                    HistoryCodec.decodeQuestion(stored.taskPayload).copy(answeredOptionId = stored.answeredOptionId,
                        usedHint = stored.usedHint, attempts = stored.attempts)
                },
            ),
        )
    }

    private suspend fun writeChildren(state: GameState) {
        writeFinancial(state.financial)
        dao.insertEventExposure(state.eventHistory.mapIndexed { position, exposure -> exposure.toEntity(position) })
        state.economy.planning?.let { dao.insertBudgetPlanning(it.toEntity()) }
        state.selectedGoalId?.let { dao.insertGoalSelection(GoalSelectionEntity(CURRENT_GAME_ID, it)) }
        state.selectedSavingItemId?.let { dao.insertSavingGoalSelection(SavingGoalSelectionEntity(CURRENT_GAME_ID, it)) }
        dao.insertMiniGameCompletions(state.completedMiniGames.map { MiniGameCompletionEntity(it, CURRENT_GAME_ID) })
        state.engine?.let { engine ->
            dao.insertEngine(engine.toEntity())
            dao.insertDayJournal(engine.journal.mapIndexed { position, entry ->
                DayJournalEntity(entry.id, CURRENT_GAME_ID, position, entry.kind.name, entry.sourceId, entry.moneyDelta, entry.energyDelta)
            })
            dao.insertEngineDeeds(engine.deeds.mapIndexed { position, offer ->
                EngineDeedEntity(offer.id, CURRENT_GAME_ID, position, offer.eventId, offer.expiresDay, offer.completed)
            })
            dao.insertEngineEvents(engine.events.mapIndexed { position, occurrence ->
                EngineEventEntity(occurrence.id, CURRENT_GAME_ID, position, occurrence.eventId.takeIf { occurrence.deedOfferId == null }, occurrence.status.name, occurrence.deedOfferId)
            })
        }
        dao.insertDecisions(state.story.decisions.mapIndexed { position, decision ->
            PlayerDecisionEntity(decision.id, CURRENT_GAME_ID, position, decision.choiceId)
        })
        dao.insertCompletedGoalProjects(state.completedGoalProjects.map { CompletedGoalProjectEntity(it.decisionId, it.goalId) })
        dao.insertOwnedItems(state.ownedItems.mapIndexed { position, item ->
            OwnedItemEntity(item.id, CURRENT_GAME_ID, position, item.itemId)
        })
    }

    private suspend fun writeFinancial(progress: FinancialProgress) {
        val oldPeriods = finance.periods(CURRENT_GAME_ID)
        require(progress.periods.size >= oldPeriods.size && progress.periods.take(oldPeriods.size).map { it.id } == oldPeriods.map { it.id }) {
            "A regular write cannot delete or reorder financial periods"
        }
        oldPeriods.forEachIndexed { index, old ->
            val next = progress.periods[index].toEntity(index)
            require(next.goalId == old.goalId && next.ordinal == old.ordinal && next.startedDay == old.startedDay &&
                next.openingAvailable == old.openingAvailable && next.openingSavings == old.openingSavings && next.imported == old.imported) {
                "Financial period origins are immutable"
            }
            require(old.closedDay == null || old == next) { "Closed financial periods are immutable" }
        }
        finance.writePeriods(progress.periods.mapIndexed { index, period -> period.toEntity(index) })
        val oldPlans = finance.plans(CURRENT_GAME_ID)
        val newPlans = progress.plans.mapIndexed { index, plan -> plan.toEntity(index) }
        require(newPlans.take(oldPlans.size) == oldPlans) { "Confirmed plans are immutable" }
        finance.appendPlans(newPlans.drop(oldPlans.size))
        finance.writeCursor(FinancialCursorEntity(CURRENT_GAME_ID, progress.currentPeriodId))
        val task = progress.practice
        if (task == null) finance.deletePractice(CURRENT_GAME_ID) else {
            val template = task.copy(answeredOptionId = null, usedHint = false, attempts = 0)
            var encoded = HistoryCodec.encodeQuestion(template)
            finance.practice(CURRENT_GAME_ID)?.let { previous ->
                if (HistoryCodec.decodeQuestion(previous.taskPayload).id == task.id) {
                    // New optional fields decode with defaults. Keep an old question's original
                    // serialized template instead of rejecting every write after an app upgrade.
                    require(HistoryCodec.decodeQuestion(previous.taskPayload) == template) { "A question identity cannot change its answer key" }
                    encoded = previous.taskPayload
                }
            }
            finance.writePractice(FinancialPracticeEntity(CURRENT_GAME_ID, encoded, task.answeredOptionId, task.usedHint, task.attempts))
        }
    }

    override suspend fun readHistory(): List<AuditEntry> = withLiveBudgetRead { history.read().map { it.decode() } }

    override suspend fun recordRejected(request: EngineRequest, reasonName: String, contentFingerprint: String?) {
        require(reasonName.isNotBlank())
        withLiveBudgetWrite {
            val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
            val run = ensureBaseline(current)
            val id = "rejected:${run.runId}:${request.id}:$reasonName"
            history.find(id)?.let { row ->
                val previous = row.decode()
                val previousRequest = previous.request
                require(previous.type == AuditType.REJECTED && previousRequest != null &&
                    HistoryCodec.encodeRequest(previousRequest) == HistoryCodec.encodeRequest(request) &&
                    previous.contentFingerprint == contentFingerprint) { "Conflicting rejected intent identity" }
                return@withLiveBudgetWrite
            }
            val sequence = nextSequence()
            val originalContext = request.context ?: DecisionContext()
            val shownMoney = originalContext.before
            val moneyStillCurrent = shownMoney == null || (shownMoney.available == current.economy.availableBalance &&
                shownMoney.savings == current.economy.savingsBalance)
            val context = originalContext.copy(complete = originalContext.complete && reasonName != "StaleRevision" &&
                request.expectedRevision == current.engine?.revision && moneyStillCurrent)
            val fact = AnalyticsFact("${request.id}:blocked:$reasonName", run.runId, "blocked:${request.id}",
                request.id, sequence, FactDetail.Interaction("blocked:$reasonName"), context,
                contextFamily = "blocked_action", contentVersion = contentFingerprint ?: "unversioned",
                gameRulesVersion = current.engine?.rulesId ?: "not-started")
            appendAudit(AuditEntry(id, sequence, run.runId, AuditType.REJECTED, request = request, context = context,
                facts = listOf(fact), contentFingerprint = contentFingerprint))
        }
    }

    override fun observeHistory(): Flow<List<AuditEntry>> = database.invalidationTracker.createFlow("GAME_AUDIT")
        .map { readHistory() }

    override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
        if (facts.isEmpty()) return
        withLiveBudgetWrite {
            val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
            val run = ensureBaseline(current)
            sourceGuard?.requireMatches(history.read().map { it.decode() })
            require(facts.map { it.eventId }.distinct().size == facts.size) { "Duplicate fact in batch" }
            val sequence = nextSequence()
            val fresh = facts.mapNotNull { fact ->
                require(fact.gameRunId == run.runId || fact.gameRunId == "current") { "Fact belongs to another game run" }
                val normalized = fact.copy(gameRunId = run.runId, sequence = sequence)
                val priorId = history.factAudit(fact.eventId)
                if (priorId == null) normalized else {
                    val previous = checkNotNull(history.find(priorId)).decode().facts.single { it.eventId == fact.eventId }
                    require(HistoryCodec.encodeFact(previous) == HistoryCodec.encodeFact(normalized.copy(sequence = previous.sequence))) {
                        "Conflicting fact identity"
                    }
                    null
                }
            }
            if (fresh.isNotEmpty()) appendAudit(AuditEntry("facts:${UUID.randomUUID()}", sequence, run.runId,
                AuditType.FACTS, facts = fresh))
        }
    }

    override suspend fun pendingOutbox(limit: Int): List<AuditEntry> {
        require(limit in 1..1000)
        return withLiveBudgetRead { history.pending(limit).map { it.decode() } }
    }

    override suspend fun acknowledgeOutbox(ids: Set<String>) = withLiveBudgetWrite {
        if (ids.isNotEmpty()) history.acknowledge(ids.toList())
    }

    override suspend fun exportSnapshot(): GameSnapshot = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        HistoryCodec.snapshot(run.runId, current, history.read().map { it.decode() })
    }

    override suspend fun restoreSnapshot(snapshot: GameSnapshot, expected: RestoreGuard): GameState {
        HistoryCodec.validate(snapshot)
        require(snapshot.formatVersion < 4 || snapshot.state.economy.hasValidLiveBudget()) {
            "Live budget does not reconcile with the available account"
        }
        return withLiveBudgetWrite {
            val current = readInTransaction()
            require(current?.engine?.revision == expected.engineRevision && history.sequence() == expected.historySequence) {
                "Local game changed since restore was requested"
            }
            val currentRules = current?.engine?.rulesId
            if (snapshot.rulesId != null) {
                require((expected.supportedRulesId ?: currentRules) == snapshot.rulesId) { "Incompatible game rules" }
            }
            finance.clearPractice(); finance.clearCursor(); finance.clearPlans(); finance.clearPeriods()
            history.clearOutbox(); history.clearFacts(); history.clearAudit(); history.clearRun()
            if (current == null) {
                dao.insertState(snapshot.state.toEntity())
                writeChildren(snapshot.state)
            } else persistGame(snapshot.state)
            database.onboardingDraftDao().clear()
            history.insertRun(GameRunEntity(CURRENT_GAME_ID, snapshot.runId))
            snapshot.history.forEach { appendAudit(it) }
            val saved = checkNotNull(readInTransaction())
            check(HistoryCodec.encodeState(saved) == HistoryCodec.encodeState(snapshot.state)) {
                "Restored game did not preserve the complete snapshot"
            }
            appendAudit(AuditEntry("restore:${UUID.randomUUID()}", nextSequence(), snapshot.runId,
                AuditType.RESTORED, after = saved))
            if (snapshot.formatVersion < 4) {
                upgradeBudgetInTransaction(saved, snapshot.runId)
            } else saved
        }
    }

    private suspend fun <T> withLiveBudgetRead(block: suspend () -> T): T {
        ensureBudgetModel()
        return database.withReadTransaction { block() }
    }

    private suspend fun <T> withLiveBudgetWrite(block: suspend () -> T): T {
        ensureBudgetModel()
        return database.withWriteTransaction { block() }
    }

    /** Schema migration only marks old rows; the first repository access preserves a complete before-checkpoint atomically. */
    private suspend fun ensureBudgetModel() {
        if (budgetModelReady) return
        budgetPreparation.withLock {
            if (budgetModelReady) return@withLock
            database.withWriteTransaction {
                val rows = dao.readStates()
                check(rows.size <= 1) { "Multiple saved games are not supported" }
                val row = rows.singleOrNull()
                check(row == null || row.budgetModelVersion in 0..1) { "Unsupported budget model" }
                if (row?.budgetModelVersion == 0) {
                    val before = checkNotNull(readInTransaction())
                    val run = ensureBaseline(before)
                    upgradeBudgetInTransaction(before, run.runId)
                }
            }
            budgetModelReady = true
        }
    }

    private suspend fun upgradeBudgetInTransaction(before: GameState, runId: String): GameState {
        val id = "live-budget:${UUID.randomUUID()}"
        val upgraded = upgradeLegacyBudget(before, id)
        // Persist even when a valid active draft needs no changes: toEntity marks this row as live-model storage.
        persistGame(upgraded)
        val saved = checkNotNull(readInTransaction())
        check(saved == upgraded) { "Budget upgrade did not preserve the complete aggregate" }
        appendAudit(AuditEntry(id, nextSequence(), runId, AuditType.TECHNICAL_UPDATE, before = before, after = saved))
        return saved
    }

    private suspend fun ensureRun(): GameRunEntity = history.run(CURRENT_GAME_ID) ?: GameRunEntity(
        CURRENT_GAME_ID, UUID.randomUUID().toString()).also { history.insertRun(it) }

    private suspend fun ensureBaseline(current: GameState): GameRunEntity = ensureRun().also { run ->
        if (history.sequence() == 0L) appendAudit(AuditEntry("baseline:${run.runId}", 1, run.runId,
            AuditType.IMPORTED_BASELINE, after = current))
    }

    private suspend fun nextSequence(): Long = Math.addExact(history.sequence(), 1L)

    private suspend fun appendAudit(entry: AuditEntry) {
        history.insert(GameAuditEntity(entry.id, entry.runId, entry.sequence, entry.type.name,
            entry.formatVersion, HistoryCodec.encode(entry)))
        history.insertFacts(entry.facts.map { AuditFactIdEntity(it.eventId, entry.id) })
        history.insertOutbox(AuditOutboxEntity(entry.id))
    }

    private fun GameAuditEntity.decode(): AuditEntry = HistoryCodec.decodeEntry(payload).also {
        check(it.id == id && it.sequence == sequence && it.runId == runId && it.type.name == type && it.formatVersion == formatVersion) {
            "Historical payload does not match its index"
        }
    }

}
