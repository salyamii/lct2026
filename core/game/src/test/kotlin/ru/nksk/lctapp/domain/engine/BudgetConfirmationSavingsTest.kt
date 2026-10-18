package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class BudgetConfirmationSavingsTest {
    @Test fun confirmationKeepsOriginalIntentAndRecordsOneRealDepositWithoutIncomeOrSpending() = runTest {
        for (reason in BudgetPlanningReason.entries) {
            val f = Fixture(reason = reason)
            val before = f.state
            val request = f.confirmRequest()
            assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
            val after = f.state
            assertEquals(80L, after.economy.availableBalance)
            assertEquals(50L, after.economy.savingsBalance)
            assertEquals(before.economy.balance, after.economy.balance)
            assertEquals(BudgetPlan(35, 20, 0, 25), after.economy.plan)
            assertNull(after.economy.planning)
            assertEquals(before.economy.displayPlan, after.financial.plans.single().allocation)
            assertEquals(100L, after.financial.plans.single().availableBasis)
            assertEquals(listOf(LedgerEntry("confirm:deposit", LedgerKind.DEPOSIT, 20)), f.repo.entries.single().operations)
            assertEquals(before.engine!!.steps, after.engine!!.steps)
            assertEquals(before.engine.journal, after.engine.journal)
            val period = checkNotNull(after.financial.currentPeriod)
            assertEquals(20L, period.deposited)
            assertEquals(0L, period.income)
            assertEquals(0L, period.spentAvailable)
            assertEquals(0L, period.spentSavings)
            val contribution = checkNotNull(period.savingPractice).contributions.single()
            assertEquals("confirm:deposit", contribution.operationId)
            assertEquals(20L, contribution.retained)
            assertTrue(contribution.independent)
            assertTrue(contribution.needsCovered)
            val summary = FinancialProgressionPolicy.summary(checkNotNull(period.savingPractice))
            assertEquals(1, summary.creditedDecisions)
            assertFalse(summary.regular)
            assertEquals(1, f.repo.entries.single().facts.count { it.detail is FactDetail.SavingMovement })
        }
    }

    @Test fun successfulRetryAfterLostReplyNeverMovesTheSameAmountTwice() = runTest {
        val f = Fixture()
        val request = f.confirmRequest()
        f.repo.loseNextReply = true
        assertTrue(runCatching { f.engine.dispatch(request) }.exceptionOrNull() is IllegalStateException)
        val committed = f.state
        assertEquals(50L, committed.economy.savingsBalance)
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(committed, f.state)
        assertEquals(1, f.repo.entries.size)
        assertEquals(1, f.state.financial.plans.size)
        assertEquals(1, f.state.financial.currentPeriod!!.savingPractice!!.contributions.size)
    }

    @Test fun failedCommitLeavesMoneyDraftAndFinancialHistoryTogetherForTheRetry() = runTest {
        val f = Fixture()
        val request = f.confirmRequest()
        val before = f.state
        f.repo.failNextCommit = true
        assertTrue(runCatching { f.engine.dispatch(request) }.exceptionOrNull() is IllegalStateException)
        assertEquals(before, f.state)
        assertTrue(f.repo.entries.isEmpty())
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        assertEquals(50L, f.state.economy.savingsBalance)
        assertEquals(1, f.repo.entries.size)
    }

    @Test fun manualEditingAfterConfirmationDoesNotReintroduceAlreadyDepositedMoney() = runTest {
        val f = Fixture()
        f.engine.dispatch(f.confirmRequest())
        val confirmed = f.state.economy
        assertTrue(f.engine.dispatch(EngineRequest("edit", f.state.engine!!.revision,
            EngineCommand.ChangeBudgetAllocation("edit", 0, BudgetSection.RESERVE, amount = 25, startManual = true))) is EngineResult.Applied)
        assertEquals(0L, f.state.economy.displayPlan.savings)
        val draft = checkNotNull(f.state.economy.planning)
        assertTrue(f.engine.dispatch(EngineRequest("confirm-again", f.state.engine!!.revision,
            EngineCommand.ConfirmBudget(draft.id, draft.revision))) is EngineResult.Applied)
        assertEquals(confirmed, f.state.economy)
        assertEquals(20L, f.state.financial.currentPeriod!!.deposited)
        assertTrue(f.repo.entries.last().operations.isEmpty())
        assertEquals(listOf(20L, 0L), f.state.financial.plans.map { it.allocation.savings })
    }

    @Test fun zeroSavingAndUnknownContextDoNotInventSavingEvidence() = runTest {
        val zero = Fixture(allocation = BudgetPlan(35, 20, 0, 45))
        zero.engine.dispatch(zero.confirmRequest())
        assertEquals(30L, zero.state.economy.savingsBalance)
        assertTrue(zero.repo.entries.single().operations.isEmpty())
        assertTrue(zero.state.financial.currentPeriod!!.savingPractice!!.contributions.isEmpty())

        for (context in listOf<DecisionContext?>(null, DecisionContext(informationPresented = true, complete = true,
            before = FinancialPosition(100, 30, 35), assistance = setOf(Assistance.HINT)))) {
            val f = Fixture()
            f.engine.dispatch(f.confirmRequest().copy(context = context))
            val practice = checkNotNull(f.state.financial.currentPeriod!!.savingPractice)
            assertEquals(20L, practice.contributions.single().amount)
            assertFalse(practice.contributions.single().independent)
            assertEquals(0, FinancialProgressionPolicy.summary(practice).creditedDecisions)
        }
    }

    @Test fun invalidAndStaleConfirmationsCannotMoveMoney() = runTest {
        val f = Fixture()
        val before = f.state
        val request = f.confirmRequest()
        val stale = request.copy(command = EngineCommand.ConfirmBudget("draft", 1))
        assertEquals(BlockReason.StaleRevision, (f.engine.dispatch(stale) as EngineResult.Blocked).reason)
        assertEquals(before, f.state)
        assertTrue(f.repo.entries.isEmpty())
        f.repo.update { current -> current.copy(economy = current.economy.copy(unallocated = 5,
            planning = current.economy.planning!!.copy(draft = BudgetPlan(35, 20, 15, 25)))) }
        val incomplete = f.state
        assertTrue(f.engine.dispatch(request) is EngineResult.Blocked)
        assertEquals(incomplete, f.state)
        assertTrue(f.repo.entries.isEmpty())
    }

    @Test fun savingDuringPlanRecoveryUsesTheOriginalBasisAndPreservesTheReviewEvidence() = runTest {
        val f = Fixture(reason = BudgetPlanningReason.MANUAL)
        f.repo.update { current ->
            val adopted = FinancialPeriods.adopt(current)
            adopted.copy(financial = adopted.financial.copy(periods = adopted.financial.periods.map {
                it.copy(reviewEvidence = PeriodReviewEvidence("review", null, false, true))
            }))
        }
        f.engine.dispatch(f.confirmRequest())
        val period = checkNotNull(f.state.financial.currentPeriod)
        assertEquals("confirm:plan", period.reviewEvidence!!.recoveryPlanRevisionId)
        assertTrue(FinancialProgressionPolicy.reviewReady(period.reviewEvidence))
        assertEquals(100L, f.state.financial.plans.single().allocation.total)
        assertEquals(80L, f.state.economy.availableBalance)
    }

    @Test fun reopeningALegacySettledPlanDoesNotTurnItsOldAllocationIntoANewDeposit() = runTest {
        val f = Fixture()
        f.repo.update { current -> current.copy(economy = current.economy.copy(
            plan = current.economy.displayPlan, planning = null)) }
        val old = f.state
        f.engine.synchronizeStoryAge()
        assertEquals(old, f.state)
        assertEquals(old.economy, FinancialPeriods.adopt(old).economy)
        assertTrue(f.repo.entries.isEmpty())
    }

    private class Fixture(reason: BudgetPlanningReason = BudgetPlanningReason.INITIAL,
        allocation: BudgetPlan = BudgetPlan(35, 20, 20, 25)) {
        val repo = RecordingRepository(GameState(PetState("PLAIN", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(0, 0, 0, 0), planning = BudgetPlanning("draft", reason,
                BudgetPlanningStage.ALLOCATION, if (reason in listOf(BudgetPlanningReason.INITIAL, BudgetPlanningReason.WEEKLY)) 100 else 0,
                draft = allocation, baseAmount = 100), availableBalance = 100, savingsBalance = 30),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList(), selectedGoalId = "goal",
            engine = EngineState("budget-confirm", 0, 1, DayPhase.RUNNING, 0, 5, false, null,
                130, emptyList(), emptyList())))
        val state get() = repo.value
        val engine = GameEngine(repo, EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("meal", 5, null))),
            EngineRules("budget-confirm", 5, 3, 1))
        fun confirmRequest() = EngineRequest("confirm", state.engine!!.revision, EngineCommand.ConfirmBudget("draft", 0),
            DecisionContext(presentationId = "budget:draft:0", informationPresented = true, complete = true,
                before = FinancialPosition(state.economy.availableBalance, state.economy.savingsBalance, 35)))
    }

    /** Models the existing transactional, request-id based persistence contract. */
    private class RecordingRepository(initial: GameState) : GameRepository {
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
            val receipt = AuditEntry(request.id, sequence, "run", AuditType.COMMAND, request, context, before, after,
                facts(before, after, "run", sequence), CanonicalLedger.fromTransition(before, after, request),
                contentFingerprint = contentFingerprint)
            if (failNextCommit) {
                failNextCommit = false
                error("Write failed before commit")
            }
            entries += receipt
            flow.value = after
            if (loseNextReply) {
                loseNextReply = false
                error("Commit succeeded but its reply was lost")
            }
            return after
        }
    }
}
