package ru.nksk.lctapp.data.game

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.engine.CompletedGoalProject
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.data.game.local.*
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.CampaignReconciliation
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.withStarterAccessoryOwnership
import ru.nksk.lctapp.domain.backend.*
import java.util.UUID

internal class RoomGameRepository @Inject constructor(private val database: GameDatabase,
    private val parentRewardPolicy: ParentRewardPolicy = ParentRewardPolicy()) : GameRepository {
    private val dao = database.gameStateDao()
    private val history = database.gameHistoryDao()
    private val finance = database.financialProgressDao()
    private val archives = database.gameRunArchiveDao()
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

    override suspend fun reconcileCampaign(reconciliation: (GameState) -> CampaignReconciliation): GameState = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val next = reconciliation(current).applyTo(current)
        if (next == current) return@withLiveBudgetWrite current
        val run = ensureBaseline(current)
        val reboundPeriodId = current.financial.currentPeriod?.takeIf {
            it.goalId != next.financial.currentPeriod?.goalId
        }?.id
        persistGame(next, reboundPeriodId)
        val saved = checkNotNull(readInTransaction())
        appendAudit(AuditEntry("campaign-reconciliation:${UUID.randomUUID()}", nextSequence(), run.runId,
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

    private suspend fun persistGame(next: GameState, reboundPeriodId: String? = null) {
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
        writeChildren(next, reboundPeriodId)
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

    private suspend fun writeChildren(state: GameState, reboundPeriodId: String? = null) {
        writeFinancial(state.financial, reboundPeriodId)
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

    private suspend fun writeFinancial(progress: FinancialProgress, reboundPeriodId: String? = null) {
        val oldPeriods = finance.periods(CURRENT_GAME_ID)
        require(progress.periods.size >= oldPeriods.size && progress.periods.take(oldPeriods.size).map { it.id } == oldPeriods.map { it.id }) {
            "A regular write cannot delete or reorder financial periods"
        }
        oldPeriods.forEachIndexed { index, old ->
            val next = progress.periods[index].toEntity(index)
            if (old.id == reboundPeriodId) {
                require(old.closedDay == null && old.id == progress.currentPeriodId && next.goalId != old.goalId &&
                    next == old.copy(goalId = next.goalId, imported = true)) {
                    "Campaign compatibility may only rebind the current open period"
                }
            } else {
                require(next.goalId == old.goalId && next.ordinal == old.ordinal && next.startedDay == old.startedDay &&
                    next.openingAvailable == old.openingAvailable && next.openingSavings == old.openingSavings && next.imported == old.imported) {
                    "Financial period origins are immutable"
                }
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

    override suspend fun latestHistoryId(): String? = withLiveBudgetRead { history.latestId() }

    override suspend fun readFacts(eventIds: Set<String>): HistoryFactLookup? = withContext(Dispatchers.IO) {
        withLiveBudgetRead {
            val run = history.run(CURRENT_GAME_ID) ?: return@withLiveBudgetRead null
            val sequence = history.sequence()
            val decoded = mutableMapOf<String, AuditEntry>()
            val facts = eventIds.mapNotNull { eventId ->
                val auditId = history.factAudit(eventId) ?: return@mapNotNull null
                val audit = decoded[auditId] ?: checkNotNull(history.find(auditId)).decode().also {
                    decoded[auditId] = it
                }
                audit.facts.single { it.eventId == eventId }
            }
            HistoryFactLookup(run.runId, sequence, facts)
        }
    }

    override suspend fun readDayHistory(day: Int): List<AuditEntry>? = withContext(Dispatchers.IO) {
        require(day > 0)
        withLiveBudgetRead {
            val state = readInTransaction() ?: return@withLiveBudgetRead null
            if (!history.hasCoherentOrder()) return@withLiveBudgetRead null
            val latest = history.latestCheckpoint()?.decode()?.after
            if (latest != null && HistoryCodec.encodeState(state) != HistoryCodec.encodeState(latest))
                return@withLiveBudgetRead null
            history.commandsForDay(day).map { it.decode() }
        }
    }

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

    override fun observeHistorySequence(): Flow<Long> = database.invalidationTracker.createFlow("GAME_AUDIT")
        .map { withLiveBudgetRead { history.sequence() } }

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

    override suspend fun applyParentRewards(profileId: String, gameRunId: String, rewards: List<ParentRewardDto>,
        expectedRestoreGeneration: String): List<ParentRewardReceiptDto> = withLiveBudgetWrite {
        require(profileId.isNotBlank() && gameRunId.isNotBlank() && expectedRestoreGeneration.isNotBlank())
        var current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        if (run.runId != gameRunId || localGameGeneration(run.runId, history.latestRestoreId(run.runId)) != expectedRestoreGeneration)
            throw ParentRewardTargetChangedException()
        require(rewards.all { it.profileId == profileId && it.gameRunId == gameRunId }) { "Parent reward belongs to another profile or game run" }
        history.firstParentReward(run.runId)?.decode()?.parentReward?.let {
            require(it.reward.profileId == profileId) { "Parent rewards are already bound to another profile" }
        }
        val unique = rewards.groupBy { it.rewardId }.map { (id, copies) ->
            if (copies.any { it != copies.first() }) throw ParentRewardConflictException(id)
            copies.first()
        }
        fun entryId(rewardId: String) = "parent-reward:" + HistoryCodec.sha256(
            listOf(run.runId, rewardId).joinToString("") { "${it.length}:$it" })
        // Validate every previously committed identity before applying any new grant in this batch.
        val previous = unique.mapNotNull { reward -> history.find(entryId(reward.rewardId))?.decode()?.let { entry ->
            val application = entry.parentReward
            if (application == null || application.reward != reward) throw ParentRewardConflictException(reward.rewardId)
            reward.rewardId to application.receipt
        } }.toMap()
        val accessories = database.storyContentDao().readItem().filter { it.category == "ACCESSORY" }.map { it.id }.toSet()
        val receipts = mutableListOf<ParentRewardReceiptDto>()
        for (reward in unique) {
            val priorReceipt = previous[reward.rewardId]
            if (priorReceipt != null) { receipts += priorReceipt; continue }
            val applicationId = "parent-application:${UUID.randomUUID()}"
            val change = parentRewardPolicy.apply(current, reward, applicationId, accessories) ?: continue
            CanonicalLedger.validate(current, change.state, change.operations)
            val sequence = nextSequence()
            val identity = entryId(reward.rewardId)
            val receipt = ParentRewardReceiptDto(reward.rewardId, applicationId, identity, sequence, change.outcome)
            if (change.state != current) persistGame(change.state)
            val saved = checkNotNull(readInTransaction())
            val fact = AnalyticsFact("$applicationId:fact", run.runId, applicationId, applicationId, sequence,
                FactDetail.Interaction("parent_reward:${change.outcome.name}"),
                DecisionContext(day = saved.engine?.day), actor = AnalyticsActor.PARENT,
                contextFamily = "parent_reward", contentVersion = "parent-reward-v1",
                gameRulesVersion = saved.engine?.rulesId ?: "not-started")
            appendAudit(AuditEntry(identity, sequence, run.runId, AuditType.PARENT_REWARD,
                before = current, after = saved, facts = listOf(fact), operations = change.operations,
                parentReward = ParentRewardApplication(reward, receipt)))
            current = saved
            receipts += receipt
        }
        receipts
    }

    override suspend fun exportSnapshot(): GameSnapshot = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        HistoryCodec.snapshot(run.runId, current, history.read().map { it.decode() }, archives.read().map { it.decodeArchive() })
    }

    override suspend fun archivedRuns(): List<ArchivedGameRunSummary> = withLiveBudgetRead {
        archives.read().map { row -> row.decodeArchive().snapshot.let {
            ArchivedGameRunSummary(it.runId, it.state.pet.name, it.state.engine?.day, it.history.size)
        } }
    }

    override suspend fun archivedRun(runId: String): GameSnapshot? = withLiveBudgetRead {
        archives.find(runId)?.decodeArchive()?.snapshot
    }

    override suspend fun restartCampaign(request: CampaignRestartRequest,
        transform: (GameState, GameState?) -> GameState): GameState = withLiveBudgetWrite {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val run = ensureBaseline(current)
        archives.forRestart(request.id)?.decodeArchive()?.let { prior ->
            require(prior.snapshot.runId == request.expectedRunId &&
                prior.snapshot.state.engine?.revision == request.expectedEngineRevision &&
                prior.snapshot.historySequence == request.expectedHistorySequence) { "Conflicting rewind identity" }
            if (prior.nextRunId != run.runId) throw CampaignRestartConflictException()
            return@withLiveBudgetWrite current
        }
        if (run.runId != request.expectedRunId || current.engine?.revision != request.expectedEngineRevision ||
            history.sequence() != request.expectedHistorySequence) throw CampaignRestartConflictException()
        val previousHistory = history.read().map { it.decode() }
        val previous = HistoryCodec.snapshot(run.runId, current, previousHistory)
        HistoryCodec.validate(previous)
        val initial = previousHistory.firstOrNull { it.type == AuditType.INITIALIZED }?.after
        val next = transform(current, initial)
        require(next.economy.hasValidLiveBudget()) { "Invalid new-game budget" }
        val nextRunId = UUID.randomUUID().toString()
        archives.insert(GameRunArchiveEntity(run.runId, archives.read().size, request.id,
            nextRunId, HistoryCodec.encodeSnapshot(previous)))
        finance.clearPractice(); finance.clearCursor(); finance.clearPlans(); finance.clearPeriods()
        history.clearOutbox(); history.clearFacts(); history.clearAudit(); history.clearRun()
        persistGame(next)
        database.onboardingDraftDao().clear()
        history.insertRun(GameRunEntity(CURRENT_GAME_ID, nextRunId))
        val saved = checkNotNull(readInTransaction())
        check(HistoryCodec.encodeState(saved) == HistoryCodec.encodeState(next)) { "New run was not preserved completely" }
        appendAudit(AuditEntry("initialize:$nextRunId", 1, nextRunId, AuditType.INITIALIZED, after = saved))
        saved
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
            archives.clear()
            snapshot.archivedRuns.forEachIndexed { position, archive ->
                archives.insert(GameRunArchiveEntity(archive.snapshot.runId, position, archive.restartRequestId,
                    archive.nextRunId, HistoryCodec.encodeSnapshot(archive.snapshot)))
            }
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

    private fun GameRunArchiveEntity.decodeArchive(): ArchivedGameRun {
        val snapshot = HistoryCodec.decodeSnapshot(snapshotPayload)
        check(snapshot.runId == runId) { "Archived snapshot does not match its index" }
        return ArchivedGameRun(restartRequestId, nextRunId, snapshot)
    }

}
