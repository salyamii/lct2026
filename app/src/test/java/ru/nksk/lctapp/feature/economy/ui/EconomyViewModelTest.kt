package ru.nksk.lctapp.feature.economy.ui

import ru.nksk.lctapp.core.ui.game.BudgetHistoryUi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

@OptIn(ExperimentalCoroutinesApi::class)
class EconomyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun readingBudgetDoesNotOpenSessionAndEditingIntentionDoesNotMoveSavings() = runTest(dispatcher) {
        val original = createInitialGameState().copy(economy = EconomyState(BudgetPlan(35, 20, 20, 25),
            availableBalance = 100, savingsBalance = 30))
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        assertNull(repo.state.value!!.economy.planning)
        model.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, false))
        advanceUntilIdle()
        assertEquals(BudgetPlanningReason.MANUAL, repo.state.value!!.economy.planning!!.reason)
        assertEquals(20L, repo.state.value!!.economy.plan.savings)
        assertEquals(15L, repo.state.value!!.economy.displayPlan.savings)
        val recreated = model(repo)
        advanceUntilIdle()
        recreated.onAction(EconomyAction.Adjust(BudgetArticle.RESERVE, true))
        advanceUntilIdle()
        recreated.onAction(EconomyAction.SetReason(BudgetRevisionReason.CHANGED_PRIORITY))
        recreated.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        val saved = repo.state.value!!
        assertNull(saved.economy.planning)
        assertEquals(BudgetPlan(35, 20, 15, 30), saved.economy.plan)
        assertEquals(100L, saved.economy.availableBalance)
        assertEquals(30L, saved.economy.savingsBalance)
        assertEquals(original.story, saved.story)
        assertEquals(original.ownedItems, saved.ownedItems)
    }

    @Test fun receiptTransitionAndDraftSurviveNewViewModelWithoutAnotherGrant() = runTest(dispatcher) {
        val repo = BudgetRepository(createInitialGameState())
        val first = model(repo)
        advanceUntilIdle()
        first.onAction(EconomyAction.StartAllocation)
        advanceUntilIdle()
        first.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        val recreated = model(repo)
        advanceUntilIdle()
        val economy = recreated.uiState.value.economy!!
        assertEquals(BudgetPlanningStage.ALLOCATION, economy.planning!!.stage)
        assertEquals(35L, economy.displayPlan.needs)
        assertEquals(0L, economy.plan.needs)
        assertEquals(65L, economy.unallocated)
        assertEquals(100L, economy.balance)
    }

    @Test fun queuedRelativePressesUseLatestCommittedDraftWithoutChangingMoney() = runTest(dispatcher) {
        val repo = BudgetRepository(allocating())
        val model = model(repo)
        advanceUntilIdle()
        repeat(7) { model.onAction(EconomyAction.Adjust(BudgetArticle.NEEDS, true)) }
        assertTrue(model.uiState.value.saving)
        assertTrue(model.uiState.value.planEditingEnabled)
        assertFalse(model.uiState.value.exclusiveSaving)
        advanceUntilIdle()
        assertEquals(35L, repo.state.value!!.economy.displayPlan.needs)
        assertEquals(65L, repo.state.value!!.economy.unallocated)
        assertEquals(100L, repo.state.value!!.economy.availableBalance)
        assertFalse(model.uiState.value.saving)
    }

    @Test fun finalConfirmationWaitsForQueuedEditsAndCannotUseAnUnseenDraft() = runTest(dispatcher) {
        val repo = BudgetRepository(allocating())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.SAVINGS, 65))
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertTrue(repo.requests.none { it.command is EngineCommand.ConfirmBudget })
        assertNotNull(repo.state.value!!.economy.planning)
        assertEquals(65L, repo.state.value!!.economy.displayPlan.savings)
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertNull(repo.state.value!!.economy.planning)
    }

    @Test fun confirmationKeepsTheDisplayedPlanAndBlocksActionsUntilTheScreenIsRemoved() = runTest(dispatcher) {
        val ready = allocating().let { game ->
            game.copy(economy = game.economy.copy(unallocated = 0,
                planning = game.economy.planning!!.copy(draft = BudgetPlan(35, 20, 20, 25))))
        }
        val repo = BudgetRepository(ready)
        val model = model(repo)
        advanceUntilIdle()
        val displayed = model.uiState.value.budgetScreenState()
        assertFalse(displayed.budget.busy)
        assertTrue(displayed.budget.actionsEnabled)
        assertTrue(displayed.budget.canConfirm)
        assertEquals("Первый план", displayed.budget.title)
        assertFalse(displayed.historyAvailable)
        model.onAction(EconomyAction.Confirm)
        assertEquals(displayed, model.uiState.value.budgetConfirmation)
        assertEquals(displayed, model.uiState.value.budgetScreenState())
        assertFalse(model.uiState.value.planEditingEnabled)
        model.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, false))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.RESERVE, 30))
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertEquals(1, repo.requests.size)
        assertTrue(repo.requests.single().command is EngineCommand.ConfirmBudget)
        assertNull(repo.state.value!!.economy.planning)
        assertNull(model.uiState.value.economy!!.planning)
        assertEquals(BudgetPlan(35, 20, 20, 25), repo.state.value!!.economy.plan)
        // A successful commit can update Room while the outgoing entry is still in its slide animation.
        assertEquals(displayed, model.uiState.value.budgetScreenState())
        assertEquals(displayed, model.uiState.value.budgetConfirmation)
        assertFalse(model.uiState.value.budgetScreenState().budget.busy)
        assertTrue(model.uiState.value.budgetScreenState().budget.actionsEnabled)
        assertTrue(model.uiState.value.budgetScreenState().budget.canConfirm)
        assertFalse(model.uiState.value.planEditingEnabled)
        model.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, false))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.RESERVE, 30))
        model.onAction(EconomyAction.SetReason(BudgetRevisionReason.CHANGED_PRIORITY))
        model.onAction(EconomyAction.Confirm)
        model.onAction(EconomyAction.DismissError)
        advanceUntilIdle()
        assertEquals(1, repo.requests.size)
        assertEquals(displayed, model.uiState.value.budgetScreenState())
        assertEquals(BudgetRevisionReason.UNSPECIFIED, model.uiState.value.revisionReason)
    }

    @Test fun failedConfirmationReleasesTheDisplayedPlanAndRetriesTheSameRequest() = runTest(dispatcher) {
        val ready = allocating().let { game ->
            game.copy(economy = game.economy.copy(unallocated = 0,
                planning = game.economy.planning!!.copy(draft = BudgetPlan(35, 20, 20, 25))))
        }
        val repo = BudgetRepository(ready)
        val model = model(repo)
        advanceUntilIdle()
        val displayed = model.uiState.value.budgetScreenState()
        repo.failNext = true
        model.onAction(EconomyAction.Confirm)
        assertEquals(displayed, model.uiState.value.budgetConfirmation)
        advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        assertNull(model.uiState.value.budgetConfirmation)
        assertEquals(ready.economy, repo.state.value!!.economy)
        assertTrue(repo.requests.isEmpty())
        val failedRequest = repo.attemptedRequests.single()

        model.onAction(EconomyAction.Retry)
        assertEquals(displayed, model.uiState.value.budgetConfirmation)
        advanceUntilIdle()
        assertNull(model.uiState.value.error)
        assertNull(repo.state.value!!.economy.planning)
        assertEquals(2, repo.attemptedRequests.size)
        assertEquals(failedRequest, repo.attemptedRequests.last())
        assertEquals(failedRequest, repo.requests.single())
        assertEquals(displayed, model.uiState.value.budgetScreenState())
        assertFalse(model.uiState.value.planEditingEnabled)
    }

    @Test fun transferKeepsPlanEditsDisabledUntilItsResultIsCommitted() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Deposit(10))
        assertFalse(model.uiState.value.planEditingEnabled)
        model.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, false))
        advanceUntilIdle()
        assertEquals(1, repo.requests.size)
        assertNull(repo.state.value!!.economy.planning)
        assertEquals(10L, repo.state.value!!.economy.plan.savings)
        assertEquals(90L, repo.state.value!!.economy.availableBalance)
        assertEquals(40L, repo.state.value!!.economy.savingsBalance)
        assertTrue(model.uiState.value.planEditingEnabled)
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 10, 25), available = 90, savings = 40)
    }

    @Test fun failedCommandKeepsDraftAndRetryUsesTheSameOperation() = runTest(dispatcher) {
        val repo = BudgetRepository(createInitialGameState())
        val model = model(repo)
        advanceUntilIdle()
        repo.failNext = true
        model.onAction(EconomyAction.StartAllocation)
        advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        assertEquals(BudgetPlanningStage.RECEIPT, repo.state.value!!.economy.planning!!.stage)
        model.onAction(EconomyAction.Retry)
        advanceUntilIdle()
        assertNull(model.uiState.value.error)
        assertEquals(BudgetPlanningStage.ALLOCATION, repo.state.value!!.economy.planning!!.stage)
        assertEquals(100L, repo.state.value!!.economy.balance)
    }

    @Test fun confirmationRequiresCompleteDraftAndDoesNotDepositItsSavingsIntention() = runTest(dispatcher) {
        val repo = BudgetRepository(allocating())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Confirm)
        assertNotNull(model.uiState.value.budgetConfirmation)
        advanceUntilIdle()
        assertNotNull(repo.state.value!!.economy.planning)
        assertNotNull(model.uiState.value.error)
        assertNull(model.uiState.value.budgetConfirmation)
        model.onAction(EconomyAction.DismissError)
        assertTrue(model.uiState.value.planEditingEnabled)
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        model.onAction(EconomyAction.SetAmount(BudgetArticle.SAVINGS, 65))
        advanceUntilIdle()
        model.onAction(EconomyAction.ContextPresented(checkNotNull(model.uiState.value.confirmationContextId)))
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        val economy = repo.state.value!!.economy
        assertNull(economy.planning)
        assertEquals(65L, economy.plan.savings)
        assertEquals(0L, economy.savingsBalance)
        assertEquals(100L, economy.availableBalance)
        val confirmation = repo.requests.last { it.command is EngineCommand.ConfirmBudget }
        assertEquals(FinancialPosition(100, 0, 35), confirmation.context?.before)
        assertTrue(confirmation.context!!.informationPresented)
        assertTrue(confirmation.context!!.complete)
        assertTrue(confirmation.context!!.presentationId!!.startsWith("budget:initial:"))
    }

    @Test fun explicitTransferUpdatesCurrentAllocationsAndPreservesOriginalPlanHistory() = runTest(dispatcher) {
        val original = planned().let { game ->
            game.copy(financial = FinancialProgress(plans = listOf(BudgetPlanRevision("plan:confirmed", null, 1, 1,
                100, game.economy.plan, BudgetRevisionReason.INITIAL))))
        }
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Deposit(10))
        advanceUntilIdle()
        assertEquals(90L, repo.state.value!!.economy.availableBalance)
        assertEquals(40L, repo.state.value!!.economy.savingsBalance)
        assertEquals(BudgetPlan(35, 20, 10, 25), repo.state.value!!.economy.plan)
        assertEquals(original.financial.plans, repo.state.value!!.financial.plans)
        model.onAction(EconomyAction.Withdraw(5))
        advanceUntilIdle()
        assertEquals(90L, repo.state.value!!.economy.availableBalance)
        assertEquals(40L, repo.state.value!!.economy.savingsBalance)
        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        assertNotNull(model.uiState.value.withdrawal)
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertEquals(95L, repo.state.value!!.economy.availableBalance)
        assertEquals(35L, repo.state.value!!.economy.savingsBalance)
        assertEquals(BudgetPlan(35, 20, 10, 30), repo.state.value!!.economy.plan)
        assertEquals(original.financial.plans, repo.state.value!!.financial.plans)
        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        assertNull(model.uiState.value.withdrawal)
        assertEquals(SavingsReceipt(5, true, 90, 40, 95, 35), model.uiState.value.transferReceipt)
    }

    @Test fun currentCardsFollowTransfersAndReopeningWithoutStartingAnEditingSession() = runTest(dispatcher) {
        val original = planned()
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Deposit(10))
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 10, 25), available = 90, savings = 40)
        assertNull(model.uiState.value.budgetConfirmation)
        assertTrue(model.uiState.value.planEditingEnabled)
        val afterDeposit = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(afterDeposit.uiState.value, BudgetPlan(35, 20, 10, 25), available = 90, savings = 40)

        model.onAction(EconomyAction.Withdraw(5))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 10, 30), available = 95, savings = 35)
        val recreated = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(recreated.uiState.value, BudgetPlan(35, 20, 10, 30), available = 95, savings = 35)
        assertNull(repo.state.value!!.economy.planning)
        assertEquals(2, repo.requests.size)
    }

    @Test fun spendingAndEarningUpdateCurrentCardsAcrossReopening() = runTest(dispatcher) {
        val original = planned()
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        repo.state.value = original.copy(economy = EconomyOperations.spend(original.economy, 15, SpendingKind.GENERAL))
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 20, 10), available = 85, savings = 30)
        val afterSpending = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(afterSpending.uiState.value, BudgetPlan(35, 20, 20, 10), available = 85, savings = 30)

        val spent = checkNotNull(repo.state.value)
        repo.state.value = spent.copy(economy = EconomyOperations.earn(spent.economy, 25))
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 20, 35), available = 110, savings = 30)
        val afterEarning = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(afterEarning.uiState.value, BudgetPlan(35, 20, 20, 35), available = 110, savings = 30)
        assertTrue(repo.attemptedRequests.isEmpty())
        assertNull(repo.state.value!!.economy.planning)
    }

    @Test fun directEditingPreservesCurrentAllocationsAndRestoresItsDraftWithoutMovingMoney() = runTest(dispatcher) {
        val original = planned().let { it.copy(economy = EconomyOperations.deposit(it.economy, 20)) }
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 0, 25), available = 80, savings = 50)
        assertTrue(model.uiState.value.budgetScreenState().budget.canConfirm)
        assertTrue(model.uiState.value.planEditingEnabled)
        assertEquals(original, repo.state.value)
        assertTrue(repo.attemptedRequests.isEmpty())
        val reopenedBeforeEditing = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(reopenedBeforeEditing.uiState.value, BudgetPlan(35, 20, 0, 25), available = 80, savings = 50)

        model.onAction(EconomyAction.Adjust(BudgetArticle.WANTS, false))
        advanceUntilIdle()
        val draft = repo.state.value!!.economy
        assertEquals(BudgetPlanningReason.MANUAL, draft.planning!!.reason)
        assertEquals(original.economy.plan, draft.plan)
        assertEquals(BudgetPlan(35, 15, 0, 25), draft.displayPlan)
        assertEquals(80L, draft.availableBalance)
        assertEquals(50L, draft.savingsBalance)
        val recreated = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(recreated.uiState.value, BudgetPlan(35, 15, 0, 25), available = 80, savings = 50,
            isEditing = true, unallocated = 5)
        assertFalse(recreated.uiState.value.budgetScreenState().budget.canConfirm)
        assertTrue(recreated.uiState.value.planEditingEnabled)

        recreated.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, true))
        advanceUntilIdle()
        recreated.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        val confirmed = repo.state.value!!.economy
        assertNull(confirmed.planning)
        assertEquals(BudgetPlan(35, 15, 5, 25), confirmed.plan)
        assertEquals(80L, confirmed.availableBalance)
        assertEquals(50L, confirmed.savingsBalance)
        assertTrue(repo.requests.none { it.command is EngineCommand.DepositSavings || it.command is EngineCommand.WithdrawSavings })
    }

    @Test fun observedIncomeRefreshesTheCurrentBudgetAndFollowingEditsUseItsNewBalance() = runTest(dispatcher) {
        val original = planned().let { it.copy(economy = EconomyOperations.deposit(it.economy, 20)) }
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 0, 25), available = 80, savings = 50)
        repo.state.value = original.copy(economy = EconomyOperations.earn(original.economy, 5))
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 0, 30), available = 85, savings = 50)
        assertNull(repo.state.value!!.economy.planning)
        assertTrue(repo.attemptedRequests.isEmpty())
        model.onAction(EconomyAction.Adjust(BudgetArticle.WANTS, false))
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 15, 0, 30), available = 85, savings = 50,
            isEditing = true, unallocated = 5)
        assertEquals(BudgetPlanningReason.MANUAL, repo.state.value!!.economy.planning!!.reason)
        assertEquals(1, repo.requests.size)
    }

    @Test fun idleBudgetCanLeaveBelowFoodMinimumAndWithNoAvailableCoins() = runTest(dispatcher) {
        val original = planned().let { it.copy(economy = EconomyOperations.spend(it.economy, 5, SpendingKind.FEEDING)) }
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(model.uiState.value, BudgetPlan(30, 20, 20, 25), available = 95, savings = 30)
        val belowMinimum = model.uiState.value.budgetScreenState().budget
        assertTrue(belowMinimum.needs < belowMinimum.minimumNeeds)
        assertTrue(belowMinimum.canConfirm)
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertEquals(Unit, model.completed.first())
        assertEquals(original, repo.state.value)
        assertTrue(repo.attemptedRequests.isEmpty())

        val empty = original.copy(economy = EconomyOperations.spend(original.economy, 95, SpendingKind.GENERAL))
        repo.state.value = empty
        val reopened = model(repo)
        advanceUntilIdle()
        assertCurrentBudget(reopened.uiState.value, BudgetPlan(0, 0, 0, 0), available = 0, savings = 30)
        assertTrue(reopened.uiState.value.budgetScreenState().budget.canConfirm)
        reopened.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertEquals(Unit, reopened.completed.first())
        assertNull(reopened.uiState.value.error)
        assertEquals(empty, repo.state.value)
        assertNull(repo.state.value!!.economy.planning)
        assertTrue(repo.attemptedRequests.isEmpty())
    }

    @Test fun firstQueuedCurrentBudgetEditCannotRebaseItselfOntoAnUnseenBalance() = runTest(dispatcher) {
        val original = planned().let { it.copy(economy = EconomyOperations.deposit(it.economy, 20)) }
        val repo = BudgetRepository(original)
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Adjust(BudgetArticle.WANTS, false))
        // The edit is queued from the visible 80; its fresh repository read will see 85.
        repo.state.value = original.copy(economy = EconomyOperations.earn(original.economy, 5))
        advanceUntilIdle()
        assertTrue(repo.attemptedRequests.isEmpty())
        assertNotNull(model.uiState.value.error)
        assertNull(repo.state.value!!.economy.planning)
        assertEquals(BudgetPlan(35, 20, 0, 30), repo.state.value!!.economy.plan)
        assertCurrentBudget(model.uiState.value, BudgetPlan(35, 20, 0, 30), available = 85, savings = 50)
    }

    @Test fun budgetChangesHistoryLoadsOnlyWhenOpenedAndNeverWritesToTheGame() = runTest(dispatcher) {
        val history = savedBudgetHistory()
        val original = checkNotNull(history.last().after)
        val repo = BudgetRepository(original, history)
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading && !it.loading }
        val readsBeforeOpen = repo.historyReads
        assertNull(model.uiState.value.budgetHistory)
        assertFalse(model.uiState.value.budgetHistoryVisible)

        model.onAction(EconomyAction.OpenBudgetHistory)
        assertTrue(model.uiState.value.budgetHistoryVisible)
        assertTrue(model.uiState.value.budgetHistoryLoading)
        val loaded = model.uiState.first { !it.budgetHistoryLoading }
        assertNull(loaded.budgetHistoryError)
        assertEquals(BudgetHistoryUi("plan:confirmed", 100, 30, 0, 0, 10, 0, 0, 90, 40), loaded.budgetHistory)
        assertEquals(readsBeforeOpen + 1, repo.historyReads)
        assertEquals(original, repo.state.value)
        assertTrue(repo.attemptedRequests.isEmpty())
    }

    @Test fun missingBudgetHistoryAndReadFailureHaveDifferentErrorsAndCanRetry() = runTest(dispatcher) {
        val history = savedBudgetHistory()
        val repo = BudgetRepository(checkNotNull(history.last().after))
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading && !it.loading }
        model.onAction(EconomyAction.OpenBudgetHistory)
        val incomplete = model.uiState.first { !it.budgetHistoryLoading }
        assertNull(incomplete.budgetHistory)
        assertNotNull(incomplete.budgetHistoryError)

        repo.failHistory = true
        model.onAction(EconomyAction.OpenBudgetHistory)
        val failed = model.uiState.first { !it.budgetHistoryLoading }
        assertNull(failed.budgetHistory)
        assertNotNull(failed.budgetHistoryError)
        assertNotEquals(incomplete.budgetHistoryError, failed.budgetHistoryError)

        repo.failHistory = false
        repo.history = history
        model.onAction(EconomyAction.OpenBudgetHistory)
        val retried = model.uiState.first { !it.budgetHistoryLoading }
        assertNotNull(retried.budgetHistory)
        assertNull(retried.budgetHistoryError)
        assertNull(retried.error)
        assertTrue(repo.attemptedRequests.isEmpty())
    }

    @Test fun closingBudgetHistoryDiscardsEvenANoncancellableLateReadAndKeepsLaterChangesLazy() = runTest(dispatcher) {
        val history = savedBudgetHistory()
        val original = checkNotNull(history.last().after)
        val repo = BudgetRepository(original, history)
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading && !it.loading }
        val response = CompletableDeferred<List<AuditEntry>>()
        repo.historyLoader = { withContext(NonCancellable) { response.await() } }
        model.onAction(EconomyAction.OpenBudgetHistory)
        runCurrent()
        assertTrue(model.uiState.value.budgetHistoryLoading)
        val readsAfterOpen = repo.historyReads
        model.onAction(EconomyAction.CloseBudgetHistory)
        response.complete(history)
        repo.state.value = original.copy(economy = EconomyOperations.earn(original.economy, 5))
        advanceUntilIdle()
        val closed = model.uiState.value
        assertFalse(closed.budgetHistoryVisible)
        assertFalse(closed.budgetHistoryLoading)
        assertNull(closed.budgetHistory)
        assertNull(closed.budgetHistoryError)
        assertEquals(readsAfterOpen, repo.historyReads)
        assertTrue(repo.attemptedRequests.isEmpty())
    }

    @Test fun visibleBudgetHistoryClearsStaleNumbersAndReloadsForTheNewSnapshot() = runTest(dispatcher) {
        val history = savedBudgetHistory()
        val original = checkNotNull(history.last().after)
        val repo = BudgetRepository(original, history)
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading && !it.loading }
        model.onAction(EconomyAction.OpenBudgetHistory)
        val first = model.uiState.first { !it.budgetHistoryLoading }
        assertEquals(90L, first.budgetHistory!!.resultingAvailable)

        val response = CompletableDeferred<List<AuditEntry>>()
        repo.historyLoader = { response.await() }
        val earned = original.copy(economy = EconomyOperations.earn(original.economy, 5))
        val newerHistory = history + AuditEntry("entry:income", 3, "run", AuditType.COMMAND,
            request = EngineRequest("income", null, EngineCommand.BeginDay("day:1", emptyList())),
            before = original, after = earned,
            operations = listOf(LedgerEntry("income:5", LedgerKind.INCOME, 5)))
        repo.state.value = earned
        runCurrent()
        assertTrue(model.uiState.value.budgetHistoryVisible)
        assertTrue(model.uiState.value.budgetHistoryLoading)
        assertNull(model.uiState.value.budgetHistory)
        response.complete(newerHistory)
        val reloaded = model.uiState.first { !it.budgetHistoryLoading }
        assertNull(reloaded.budgetHistoryError)
        val latestHistory = checkNotNull(reloaded.budgetHistory)
        assertEquals(95L, latestHistory.resultingAvailable)
        assertEquals(5L, latestHistory.income)
        assertEquals(10L, latestHistory.deposited)
        assertEquals(earned, repo.state.value)
        assertTrue(repo.attemptedRequests.isEmpty())
    }

    @Test fun cancellingWithdrawalLeavesMoneyAndPlanUntouched() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        val before = repo.state.value
        model.onAction(EconomyAction.UpdateTransferInput("10"))
        model.onAction(EconomyAction.Withdraw(10))
        assertEquals(20L, model.uiState.value.withdrawal!!.savingsAfter)
        model.onAction(EconomyAction.CancelWithdrawal)
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertEquals(before, repo.state.value)
        assertTrue(repo.requests.isEmpty())
        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        assertNull(model.uiState.value.withdrawal)
        assertEquals("10", model.uiState.value.transferInput)
    }

    @Test fun withdrawalPreviewUsesChosenPartAndCommittedSavingsNotThePlanIntention() = runTest(dispatcher) {
        val catalog = bundledGameCatalog()
        val goal = catalog.goals.first()
        val item = catalog.content.items.first { it.id in goal.itemIds && checkNotNull(it.priceCoins) > 30L }
        val repo = BudgetRepository(planned().copy(selectedGoalId = goal.goalId, selectedSavingItemId = item.id))
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Withdraw(10))
        val preview = checkNotNull(model.uiState.value.withdrawal)
        assertEquals(item.id, preview.target!!.id)
        assertEquals(20L, preview.savingsAfter)
        assertEquals(checkNotNull(item.priceCoins) - 20L, preview.target.remaining(preview.savingsAfter))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        val command = repo.requests.last().command as EngineCommand.WithdrawSavings
        assertEquals(SavingsTransferExpectation(100, 30, item.id), command.expected)
        assertEquals(20L, repo.state.value!!.economy.plan.savings)
    }

    @Test fun changingDirectionCancelsWithdrawalWithoutMovingMoney() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        val before = repo.state.value
        model.onAction(EconomyAction.OpenTransfer(true))
        model.onAction(EconomyAction.UpdateTransferInput("10"))
        model.onAction(EconomyAction.Withdraw(10))
        assertNotNull(model.uiState.value.withdrawal)

        model.onAction(EconomyAction.OpenTransfer(false))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()

        assertEquals(SavingsStep.DEPOSIT, model.uiState.value.savingsStep)
        assertEquals("", model.uiState.value.transferInput)
        assertNull(model.uiState.value.withdrawal)
        assertNull(model.uiState.value.transferReceipt)
        assertEquals(before, repo.state.value)
        assertTrue(repo.requests.isEmpty())
    }

    @Test fun changingDirectionCancelsThePendingDepositRisk() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        val before = repo.state.value
        model.onAction(EconomyAction.Deposit(90))
        advanceUntilIdle()
        assertNotNull(model.uiState.value.depositWarning)
        assertEquals(before, repo.state.value)

        model.onAction(EconomyAction.OpenTransfer(true))
        model.onAction(EconomyAction.ConfirmDepositRisk)
        advanceUntilIdle()

        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        assertNull(model.uiState.value.depositWarning)
        assertNull(model.uiState.value.transferReceipt)
        assertEquals(before, repo.state.value)
        assertTrue(repo.requests.isEmpty())
    }

    @Test fun appliedTransferStaysInTheSameDirectionAndItsReceiptDoesNotBlockTheNextTransfer() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        assertEquals(SavingsStep.DEPOSIT, model.uiState.value.savingsStep)

        model.onAction(EconomyAction.UpdateTransferInput("10"))
        assertTrue(repo.requests.isEmpty())
        model.onAction(EconomyAction.Deposit(10))
        advanceUntilIdle()
        assertEquals(SavingsStep.DEPOSIT, model.uiState.value.savingsStep)
        assertEquals(SavingsReceipt(10, false, 100, 30, 90, 40), model.uiState.value.transferReceipt)
        assertEquals("", model.uiState.value.transferInput)
        assertNull(savingsBackAction(model.uiState.value))

        model.onAction(EconomyAction.ClearTransferReceipt)
        assertNull(model.uiState.value.transferReceipt)
        model.onAction(EconomyAction.OpenTransfer(true))
        model.onAction(EconomyAction.UpdateTransferInput("5"))
        model.onAction(EconomyAction.Withdraw(5))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        assertEquals(SavingsReceipt(5, true, 90, 40, 95, 35), model.uiState.value.transferReceipt)
        assertEquals(2, repo.requests.size)
        assertNull(savingsBackAction(model.uiState.value))
    }

    @Test fun changedBalanceInvalidatesWithdrawalProposalAndNeverUsesTheOldConfirmation() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Withdraw(10))
        val original = checkNotNull(repo.state.value)
        repo.state.value = original.copy(economy = EconomyOperations.earn(original.economy, 5))
        advanceUntilIdle()
        assertNull(model.uiState.value.withdrawal)
        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
        assertEquals(105L, repo.state.value!!.economy.availableBalance)
        assertEquals(30L, repo.state.value!!.economy.savingsBalance)
    }

    @Test fun repeatedConfirmationCannotEnqueueASecondWithdrawal() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Withdraw(10))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertEquals(110L, repo.state.value!!.economy.availableBalance)
        assertEquals(20L, repo.state.value!!.economy.savingsBalance)
        assertEquals(1, repo.requests.count { it.command is EngineCommand.WithdrawSavings })
    }

    @Test fun failedWithdrawalKeepsMoneyAndDoesNotShowSuccessfulResult() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Withdraw(10))
        repo.failNext = true
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        assertNull(model.uiState.value.transferReceipt)
        assertEquals(30L, repo.state.value!!.economy.savingsBalance)
        model.onAction(EconomyAction.Retry)
        advanceUntilIdle()
        assertEquals(20L, repo.state.value!!.economy.savingsBalance)
        assertEquals(SavingsStep.WITHDRAW, model.uiState.value.savingsStep)
        assertEquals(SavingsReceipt(10, true, 100, 30, 110, 20), model.uiState.value.transferReceipt)
        assertEquals(2, repo.attemptedRequests.size)
        assertEquals(repo.attemptedRequests.first().id, repo.attemptedRequests.last().id)
    }

    @Test fun withdrawalEvidenceBelongsToItsVisibleInlineConfirmation() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.OpenTransfer(true))
        model.onAction(EconomyAction.UpdateTransferInput("10"))
        val amountScreen = checkNotNull(model.uiState.value.transferContextId)
        model.onAction(EconomyAction.TransferContextPresented(amountScreen))
        model.onAction(EconomyAction.Withdraw(10))
        model.onAction(EconomyAction.TransferContextPresented(amountScreen))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        assertFalse(repo.requests.last().context!!.informationPresented)

        model.onAction(EconomyAction.UpdateTransferInput("5"))
        model.onAction(EconomyAction.Withdraw(5))
        model.onAction(EconomyAction.TransferContextPresented(checkNotNull(model.uiState.value.transferContextId)))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()
        val context = repo.requests.last().context!!
        assertTrue(context.informationPresented)
        assertTrue(context.complete)
        assertEquals(FinancialPosition(110, 20, 35), context.before)
    }

    @Test fun tappingTheAlreadySelectedDirectionKeepsItsVisibleContext() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.UpdateTransferInput("10"))
        model.onAction(EconomyAction.TransferContextPresented(checkNotNull(model.uiState.value.transferContextId)))
        model.onAction(EconomyAction.OpenTransfer(false))
        model.onAction(EconomyAction.Deposit(10))
        advanceUntilIdle()
        val context = checkNotNull(repo.requests.single().context)
        assertTrue(context.informationPresented)
        assertTrue(context.complete)
    }

    @Test fun anotherWithdrawalAmountRequiresItsOwnVisibleConfirmationContext() = runTest(dispatcher) {
        val repo = BudgetRepository(planned())
        val model = model(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.UpdateTransferInput("10"))
        model.onAction(EconomyAction.Withdraw(10))
        val oldContext = checkNotNull(model.uiState.value.transferContextId)
        model.onAction(EconomyAction.TransferContextPresented(oldContext))

        model.onAction(EconomyAction.CancelWithdrawal)
        model.onAction(EconomyAction.UpdateTransferInput("5"))
        model.onAction(EconomyAction.Withdraw(5))
        assertNotEquals(oldContext, model.uiState.value.transferContextId)
        model.onAction(EconomyAction.TransferContextPresented(oldContext))
        model.onAction(EconomyAction.ConfirmWithdrawal)
        advanceUntilIdle()

        val request = repo.requests.single()
        assertEquals(5L, (request.command as EngineCommand.WithdrawSavings).amount)
        val context = checkNotNull(request.context)
        assertFalse(context.informationPresented)
        assertFalse(context.complete)
        assertEquals(105L, repo.state.value!!.economy.availableBalance)
        assertEquals(25L, repo.state.value!!.economy.savingsBalance)
    }

    @Test fun savingsBackCancelsOnlyThePendingConfirmationAndOtherwiseLeavesTheSection() {
        val state = EconomyUiState(loading = false, economy = planned().economy,
            savingsStep = SavingsStep.WITHDRAW)
        val proposal = WithdrawalPreview(10, 100, 30, null, null, 35)
        assertEquals(EconomyAction.CancelWithdrawal, savingsBackAction(state.copy(withdrawal = proposal)))
        assertEquals(EconomyAction.CancelDepositRisk, savingsBackAction(state.copy(
            savingsStep = SavingsStep.DEPOSIT, depositWarning = BlockReason.FoodBudgetWarning(10, 35))))
        assertNull(savingsBackAction(state))
        assertNull(savingsBackAction(state.copy(savingsStep = SavingsStep.DEPOSIT)))
        assertNull(savingsBackAction(state.copy(transferReceipt = SavingsReceipt(10, true, 90, 40, 100, 30))))
    }

    @Test fun unexpectedExpenseMustBeSelectedExplicitlyAndPendingRetryKeepsItsOperation() = runTest(dispatcher) {
        val repo = BudgetRepository(planned(), listOf(expenseHistoryEntry("spent-1", 7)))
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading }
        assertEquals(1, repo.historyReads)
        assertNull(model.uiState.value.selectedExpenseOperationId)
        model.onAction(EconomyAction.SetReason(BudgetRevisionReason.UNEXPECTED_EXPENSE))
        assertNull(model.uiState.value.selectedExpenseOperationId)
        model.onAction(EconomyAction.SelectUnexpectedExpense("unknown"))
        assertNull(model.uiState.value.selectedExpenseOperationId)
        model.onAction(EconomyAction.SelectUnexpectedExpense("spent-1"))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        repo.failNext = true
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertNotNull(model.uiState.value.error)
        model.onAction(EconomyAction.SetReason(BudgetRevisionReason.NEW_INCOME))
        assertNull(model.uiState.value.selectedExpenseOperationId)
        model.onAction(EconomyAction.Retry)
        advanceUntilIdle()
        val command = repo.requests.last().command as EngineCommand.ConfirmBudget
        assertEquals(BudgetRevisionReason.UNEXPECTED_EXPENSE, command.reason)
        assertEquals("spent-1", command.causeActionId)
        assertEquals(1, repo.historyReads)
    }

    @Test fun unexpectedReasonWithoutASelectedExpenseDoesNotInventAttribution() = runTest(dispatcher) {
        val repo = BudgetRepository(planned(), listOf(expenseHistoryEntry("spent-1", 7)))
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading }
        model.onAction(EconomyAction.SetReason(BudgetRevisionReason.UNEXPECTED_EXPENSE))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        val command = repo.requests.last().command as EngineCommand.ConfirmBudget
        assertEquals(BudgetRevisionReason.UNEXPECTED_EXPENSE, command.reason)
        assertNull(command.causeActionId)
    }

    @Test fun unavailableExpenseHistoryDoesNotPreventSavingAReasonWithoutAttribution() = runTest(dispatcher) {
        val repo = BudgetRepository(planned()).apply { failHistory = true }
        val model = model(repo)
        model.uiState.first { !it.expenseHistoryLoading }
        assertTrue(model.uiState.value.expenseHistoryUnavailable)
        model.onAction(EconomyAction.SetReason(BudgetRevisionReason.UNEXPECTED_EXPENSE))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertNull((repo.requests.last().command as EngineCommand.ConfirmBudget).causeActionId)
        assertNull(repo.state.value!!.economy.planning)
    }

    @Test fun confirmationDoesNotClaimToShowTheContextOfANewerUnseenDraft() = runTest(dispatcher) {
        val repo = BudgetRepository(allocating())
        val model = model(repo)
        advanceUntilIdle()
        val oldContext = checkNotNull(model.uiState.value.confirmationContextId)
        model.onAction(EconomyAction.ContextPresented(oldContext))
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        model.onAction(EconomyAction.SetAmount(BudgetArticle.SAVINGS, 65))
        advanceUntilIdle()
        model.onAction(EconomyAction.ContextPresented(oldContext))
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        val context = repo.requests.last().context!!
        assertFalse(context.informationPresented)
        assertFalse(context.complete)
        assertNull(repo.state.value!!.economy.planning)
    }

    private fun assertCurrentBudget(state: EconomyUiState, expected: BudgetPlan, available: Long, savings: Long,
        isEditing: Boolean = false, unallocated: Long = 0) {
        val displayed = state.budgetScreenState().budget
        assertEquals(isEditing, displayed.isEditing)
        assertEquals(listOf(expected.needs, expected.wants, expected.savings, expected.reserve),
            BudgetArticle.entries.map(displayed::amount))
        assertEquals(unallocated, displayed.unallocated)
        assertEquals(available, expected.total + unallocated)
        assertEquals(available, displayed.total)
        assertEquals(available, displayed.availableBalance)
        assertEquals(savings, displayed.savingsBalance)
    }

    private fun savedBudgetHistory(): List<AuditEntry> {
        val allocation = BudgetPlan(35, 20, 20, 25)
        val before = planned().copy(economy = EconomyState(BudgetPlan(0, 0, 0, 0),
            planning = BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.ALLOCATION,
                100, draft = allocation, baseAmount = 100), availableBalance = 100, savingsBalance = 30))
        val saved = before.copy(economy = EconomyState(allocation, availableBalance = 100, savingsBalance = 30),
            financial = FinancialProgress(plans = listOf(BudgetPlanRevision("plan:confirmed", null, 1, 1,
                100, allocation, BudgetRevisionReason.INITIAL))))
        val deposited = saved.copy(economy = EconomyOperations.deposit(saved.economy, 10))
        return listOf(
            AuditEntry("entry:confirm", 1, "run", AuditType.COMMAND,
                request = EngineRequest("confirm", null, EngineCommand.ConfirmBudget("initial", 0)),
                before = before, after = saved),
            AuditEntry("entry:deposit", 2, "run", AuditType.COMMAND,
                request = EngineRequest("deposit", null, EngineCommand.DepositSavings(10)),
                before = saved, after = deposited,
                operations = listOf(LedgerEntry("deposit:10", LedgerKind.DEPOSIT, 10))),
        )
    }

    private fun planned() = createInitialGameState().copy(economy = EconomyState(BudgetPlan(35, 20, 20, 25),
        availableBalance = 100, savingsBalance = 30))

    private fun model(repo: BudgetRepository): EconomyViewModel {
        val catalog = bundledGameCatalog()
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, checkNotNull(repo.state.value))
        return EconomyViewModel(session)
    }

    private fun allocating() = createInitialGameState().let { game ->
        game.copy(economy = EconomyOperations.startAllocation(game.economy, "initial", 0))
    }
}

private class BudgetRepository(initial: GameState, var history: List<AuditEntry> = emptyList()) : GameRepository {
    val state = MutableStateFlow<GameState?>(initial)
    val requests = mutableListOf<EngineRequest>()
    val attemptedRequests = mutableListOf<EngineRequest>()
    var failNext = false
    var failHistory = false
    var historyReads = 0
    var historyLoader: (suspend () -> List<AuditEntry>)? = null
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value ?: initial.also { state.value = it }
    override suspend fun readHistory(): List<AuditEntry> {
        historyReads++
        if (failHistory) error("History unavailable")
        return historyLoader?.invoke() ?: history
    }
    override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
        facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
        attemptedRequests += request
        return update(transform).also { requests += request }
    }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        if (failNext) { failNext = false; error("Disk full") }
        return transform(checkNotNull(state.value)).also { state.value = it }
    }
}

internal fun expenseHistoryEntry(operationId: String, amount: Long, sequence: Long = 1,
    previouslyDisclosed: Boolean = false): AuditEntry {
    val before = createInitialGameState()
    val eventId = bundledGameCatalog().content.events.first { it.type == ru.nksk.lctapp.domain.content.EventType.RANDOM }.id
    val after = before.copy(engine = EngineState("rules", sequence, 2, DayPhase.RUNNING, 1, 5, false,
        null, 100, emptyList(), emptyList(), journal = listOf(
            DayJournalEntry(operationId, DayJournalKind.EVENT_START, eventId, -amount))))
    return AuditEntry("entry:$operationId", sequence, "run", AuditType.COMMAND,
        request = EngineRequest("action:$operationId", null, EngineCommand.OpenNextEvent), before = before, after = after,
        facts = listOf(AnalyticsFact("fact:$operationId", "run", "recovery:$operationId", "action:$operationId", sequence,
            FactDetail.UnexpectedExpense(operationId, amount, previouslyDisclosed))),
        operations = listOf(LedgerEntry(operationId, LedgerKind.AVAILABLE_EXPENSE, amount)))
}
