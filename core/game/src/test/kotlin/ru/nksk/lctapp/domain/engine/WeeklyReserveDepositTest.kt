package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class WeeklyReserveDepositTest {
    @Test fun onlyRemainingReserveMovesWhileNewIncomeAndOtherAllocationsStayAvailable() {
        val before = EconomyOperations.weekly(EconomyState(BudgetPlan(6, 7, 8, 9), savingsBalance = 20), "week:8", 100)
        val after = EconomyOperations.depositWeeklyReserve(before, "week:8", 0, 9)
        assertEquals(121L, after.availableBalance)
        assertEquals(29L, after.savingsBalance)
        assertEquals(before.balance, after.balance)
        assertEquals(BudgetPlan(6, 7, 8, 0), after.plan)
        assertEquals(after.plan, after.displayPlan)
        assertEquals(100L, after.unallocated)
        assertEquals(100L, EconomyOperations.allocationRemaining(after))
        assertEquals(before.planning!!.copy(revision = 1, draft = after.plan, baseAmount = 121), after.planning)
        assertEquals(after.availableBalance, after.displayPlan.total + after.unallocated)
    }

    @Test fun receiptAndExactShownReserveAreRequiredBeforeAnyMoneyCanMove() {
        val before = weeklyEconomy()
        val unavailable = listOf(
            before.copy(planning = null),
            before.copy(planning = before.planning!!.copy(reason = BudgetPlanningReason.INITIAL)),
            before.copy(planning = before.planning!!.copy(reason = BudgetPlanningReason.MANUAL)),
            before.copy(planning = before.planning!!.copy(reason = BudgetPlanningReason.MIGRATION)),
            EconomyOperations.startAllocation(before, "week:8", 0),
        )
        for (state in unavailable) assertThrows(EconomyViolation::class.java) {
            EconomyOperations.depositWeeklyReserve(state, "week:8", state.planning?.revision ?: 0, 12)
        }
        for (amount in listOf(-1L, 0L, 11L, 13L, 112L)) assertThrows(EconomyViolation::class.java) {
            EconomyOperations.depositWeeklyReserve(before, "week:8", 0, amount)
        }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.depositWeeklyReserve(before, "another", 0, 12) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.depositWeeklyReserve(before, "week:8", 1, 12) }
        val mismatchedDraft = before.copy(planning = before.planning!!.copy(draft = BudgetPlan(35, 6, 0, 11)))
        assertThrows(EconomyViolation::class.java) { EconomyOperations.depositWeeklyReserve(mismatchedDraft, "week:8", 0, 12) }
        assertEquals(20L, before.savingsBalance)
    }

    @Test fun transferStaysOnReceiptAndLaterConfirmationDoesNotRepeatIt() = runTest {
        val f = Fixture()
        val before = f.repo.value
        val request = f.request()
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        val moved = f.repo.value
        assertEquals(140L, moved.economy.availableBalance)
        assertEquals(32L, moved.economy.savingsBalance)
        assertEquals(BudgetPlanningStage.RECEIPT, moved.economy.planning!!.stage)
        assertEquals(1L, moved.economy.planning!!.revision)
        assertEquals(before.financial.plans, moved.financial.plans)
        assertEquals(before.engine!!.journal, moved.engine!!.journal)
        assertEquals(before.engine.steps, moved.engine.steps)
        assertEquals(before.engine.energy, moved.engine.energy)
        assertEquals(before.story, moved.story)
        assertEquals(listOf(LedgerEntry("reserve:deposit", LedgerKind.DEPOSIT, 12)), f.repo.entries.single().operations)

        assertEquals(EngineResult.Blocked(BlockReason.BudgetPlanningRequired), f.engine.dispatch(
            EngineRequest("ordinary-deposit", moved.engine.revision, EngineCommand.DepositSavings(1))))
        assertTrue(f.engine.dispatch(EngineRequest("allocate", moved.engine.revision,
            EngineCommand.StartBudgetAllocation("week:8", 1))) is EngineResult.Applied)
        assertTrue(f.engine.dispatch(EngineRequest("needs", f.repo.value.engine!!.revision,
            EngineCommand.ChangeBudgetAllocation("week:8", 2, BudgetSection.NEEDS, amount = 135))) is EngineResult.Applied)
        assertTrue(f.engine.dispatch(EngineRequest("confirm", f.repo.value.engine!!.revision,
            EngineCommand.ConfirmBudget("week:8", 3))) is EngineResult.Applied)
        assertEquals(32L, f.repo.value.economy.savingsBalance)
        assertEquals(140L, f.repo.value.economy.availableBalance)
        assertNull(f.repo.value.economy.planning)
        assertEquals(12L, f.repo.entries.flatMap { it.operations }.sumOf { it.amount })
    }

    @Test fun retryAfterLostReplyIsIdempotentAndANewRequestCannotRepeatTheTransfer() = runTest {
        val f = Fixture()
        val request = f.request()
        f.repo.loseNextReply = true
        assertTrue(runCatching { f.engine.dispatch(request) }.exceptionOrNull() is IllegalStateException)
        val moved = f.repo.value
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(moved, f.repo.value)
        assertEquals(1, f.repo.entries.size)
        val repeated = request.copy(id = "again", expectedRevision = moved.engine!!.revision,
            command = EngineCommand.DepositWeeklyReserve("week:8", 1, 12))
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(repeated))
        assertEquals(moved, f.repo.value)
        assertEquals(1, f.repo.entries.size)
    }

    @Test fun failureBeforeCommitPreservesBothAccountsAndTheReceiptForRetry() = runTest {
        val f = Fixture()
        val before = f.repo.value
        val request = f.request()
        f.repo.failNextCommit = true
        assertTrue(runCatching { f.engine.dispatch(request) }.exceptionOrNull() is IllegalStateException)
        assertEquals(before, f.repo.value)
        assertTrue(f.repo.entries.isEmpty())
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(32L, f.repo.value.economy.savingsBalance)
        assertEquals(1, f.repo.entries.size)
    }

    @Test fun reopeningOrReadingDoesNotTransferAndSavedCommandRoundTripsAndReplays() = runTest {
        val f = Fixture()
        val before = f.repo.value
        f.engine.synchronizeStoryAge()
        assertEquals(before, f.repo.read())
        assertEquals(before.economy.plan, EconomyOperations.proposedPlan(before.economy))
        assertTrue(f.repo.entries.isEmpty())
        val request = f.request()
        val decoded = Json { classDiscriminator = "_type" }.decodeFromString<EngineRequest>(HistoryCodec.encodeRequest(request))
        assertEquals(request, decoded)
        val replayed = f.engine.transition(before, decoded)
        assertEquals(before, f.repo.value)
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(replayed, f.repo.value)
        val snapshot = HistoryCodec.snapshot("run", f.repo.value, f.repo.entries)
        assertEquals(snapshot, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)))
    }

    private class Fixture {
        val repo = Repository(GameState(PetState("PLAIN", PetVisualState.NORMAL), weeklyEconomy(),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList(), selectedGoalId = "goal",
            engine = EngineState("weekly-reserve", 0, 8, DayPhase.RUNNING, 0, 5, false, null,
                72, emptyList(), emptyList(), journal = listOf(
                    DayJournalEntry("week:income", DayJournalKind.WEEKLY_INCOME, "weekly-reserve", 100)))))
        val engine = GameEngine(repo, EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("meal", 5, null))),
            EngineRules("weekly-reserve", 5, 3, 1))
        fun request() = EngineRequest("reserve", repo.value.engine!!.revision,
            EngineCommand.DepositWeeklyReserve("week:8", 0, 12))
    }

    private class Repository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        val value get() = flow.value
        val entries = mutableListOf<AuditEntry>()
        var failNextCommit = false
        var loseNextReply = false
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { flow.value = it }
        override suspend fun readHistory() = entries.toList()
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            if (entries.any { it.request?.id == request.id }) return value
            val before = value
            val after = transform(before)
            val sequence = entries.size.toLong() + 1
            val entry = AuditEntry(request.id, sequence, "run", AuditType.COMMAND, request, context, before, after,
                facts(before, after, "run", sequence), CanonicalLedger.fromTransition(before, after, request),
                contentFingerprint = contentFingerprint)
            if (failNextCommit) { failNextCommit = false; error("Write failed before commit") }
            entries += entry
            flow.value = after
            if (loseNextReply) { loseNextReply = false; error("Committed but reply was lost") }
            return after
        }
    }

    companion object {
        private fun weeklyEconomy() = EconomyOperations.weekly(
            EconomyState(BudgetPlan(35, 5, 0, 12), savingsBalance = 20), "week:8", 100)
    }
}
