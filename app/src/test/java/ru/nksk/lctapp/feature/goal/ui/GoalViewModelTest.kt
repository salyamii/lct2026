package ru.nksk.lctapp.feature.goal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.economy.SpendPart
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

@OptIn(ExperimentalCoroutinesApi::class)
class GoalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun openingWithoutAChosenTargetShowsTheCurrentChapterWithoutSelectingAnything() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.session.prepare()
        val before = fixture.state
        val model = fixture.model()
        runCurrent()

        val shown = model.uiState.value
        assertFalse(shown.showList)
        assertFalse(shown.returnToList)
        assertEquals(fixture.goal.goalId, shown.goalId)
        assertEquals(fixture.goal.itemIds, shown.projects.first().requirements.map { it.id })
        assertEquals(listOf(24L, 36L, 90L, 30L), shown.projects.first().requirements.map { it.price })
        assertTrue(shown.projects.first().requirements.none { it.owned })
        assertEquals(before, fixture.state)
    }

    @Test fun viewingFutureChapterAndReturningNeverChangesSavedGoalOrMoney() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.start()
        val before = fixture.state
        val model = fixture.model()
        runCurrent()
        assertFalse(model.uiState.value.showList)
        assertFalse(model.uiState.value.returnToList)
        model.onAction(GoalAction.ShowList)
        model.onAction(GoalAction.View(fixture.catalog.goals[1].goalId))
        val shown = model.uiState.value
        assertFalse(shown.showList)
        assertTrue(shown.returnToList)
        assertFalse(shown.selected)
        assertTrue(shown.parts.none { it.canSelect || it.canBuy })
        assertEquals(before, fixture.state)
        model.onAction(GoalAction.ShowList)
        assertTrue(model.uiState.value.showList)
        assertFalse(model.uiState.value.returnToList)
        assertEquals(before, fixture.state)
    }

    @Test fun restoringDetailsOpenedFromTheListPreservesTheirBackDestination() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.start()
        val before = fixture.state
        val handle = SavedStateHandle()
        val model = fixture.model(handle)
        runCurrent()
        model.onAction(GoalAction.ShowList)
        model.onAction(GoalAction.View(fixture.catalog.goals[1].goalId))

        val recreated = fixture.model(handle)
        runCurrent()
        assertFalse(recreated.uiState.value.showList)
        assertTrue(recreated.uiState.value.returnToList)
        assertEquals(fixture.catalog.goals[1].goalId, recreated.uiState.value.goalId)
        recreated.onAction(GoalAction.ShowList)
        assertTrue(recreated.uiState.value.showList)
        assertFalse(recreated.uiState.value.returnToList)
        assertEquals(before, fixture.state)
    }

    @Test fun changingTheSavingTargetUpdatesItsSelectionWithoutExtraCelebration() = runTest(dispatcher) {
        val fixture = Fixture(savings = 40)
        fixture.start()
        val model = fixture.model()
        runCurrent()
        val before = fixture.state.economy
        val next = fixture.goal.itemIds[2]

        model.onAction(GoalAction.SelectSavingGoal(fixture.goal.goalId, next))
        runCurrent()

        assertEquals(next, model.uiState.value.parts.single { it.savingTarget }.id)
        assertFalse(model.uiState.value.showList)
        assertFalse(model.uiState.value.returnToList)
        assertNull(model.uiState.value.celebration)
        assertEquals(before, fixture.state.economy)
    }

    @Test fun targetProgressReflectsTheSharedSavingsAfterDepositAndWithdrawal() = runTest(dispatcher) {
        val fixture = Fixture(savings = 40)
        fixture.start(item = 2)
        val model = fixture.model()
        runCurrent()
        assertEquals(40L, model.uiState.value.parts.single { it.savingTarget }.savedCoins)
        assertEquals(50L, model.uiState.value.parts.single { it.savingTarget }.remainingCoins)
        fixture.send(EngineCommand.DepositSavings(20))
        runCurrent()
        assertEquals(60L, model.uiState.value.balance)
        assertEquals(30L, model.uiState.value.parts.single { it.savingTarget }.remainingCoins)
        fixture.send(EngineCommand.WithdrawSavings(10, confirmed = true))
        runCurrent()
        assertEquals(50L, model.uiState.value.balance)
        assertEquals(40L, model.uiState.value.parts.single { it.savingTarget }.remainingCoins)
    }

    @Test fun purchaseResultAppearsOnlyAfterCommitAndRotationDoesNotPurchaseAgain() = runTest(dispatcher) {
        val fixture = Fixture(savings = 40)
        fixture.start()
        val handle = SavedStateHandle()
        val model = fixture.model(handle)
        runCurrent()
        val before = fixture.state
        fixture.repository.failWrite = true
        model.onAction(GoalAction.Buy(fixture.goal.goalId, fixture.goal.itemIds.first()))
        assertNotNull(model.uiState.value.confirmation)
        assertEquals(before, fixture.state)
        assertTrue(fixture.repository.purchases.isEmpty())
        model.onAction(GoalAction.ConfirmPurchase)
        runCurrent()
        assertNotNull(model.uiState.value.message)
        assertNotNull(model.uiState.value.confirmation)
        assertNull(model.uiState.value.purchaseResult)
        assertEquals(before, fixture.state)

        fixture.repository.failWrite = false
        // A late exposure callback must not improve evidence already submitted with the failed write.
        model.onAction(GoalAction.ContextPresented(checkNotNull(model.uiState.value.confirmation).contextId))
        model.onAction(GoalAction.ConfirmPurchase)
        runCurrent()
        assertEquals(2, fixture.repository.purchases.size)
        assertEquals(fixture.repository.purchases[0], fixture.repository.purchases[1])
        assertFalse(checkNotNull(fixture.repository.purchases.last().context).informationPresented)
        val committed = fixture.state
        assertEquals(100L, committed.economy.availableBalance)
        assertEquals(16L, committed.economy.savingsBalance)
        assertNull(committed.selectedSavingItemId)
        assertEquals(fixture.goal.itemIds.first(), model.uiState.value.purchaseResult?.itemId)
        assertEquals(1, model.uiState.value.collected)
        assertEquals(listOf(fixture.goal.itemIds.first()), model.uiState.value.projects.first()
            .requirements.filter { it.owned }.map { it.id })

        val recreated = fixture.model(handle)
        runCurrent()
        assertNotNull(recreated.uiState.value.purchaseResult)
        recreated.onAction(GoalAction.DismissPurchaseResult)
        assertNull(recreated.uiState.value.purchaseResult)
        assertEquals(committed, fixture.state)
        assertTrue(recreated.uiState.value.parts.filterNot { it.owned }.all { it.canSelect })
    }

    @Test fun combinedBalancesShowTheActualSplitAndOnlyExplicitConfirmationPays() = runTest(dispatcher) {
        val fixture = Fixture(savings = 20, plan = BudgetPlan(40, 20, 20, 20))
        fixture.start()
        val model = fixture.model()
        runCurrent()
        val before = fixture.state
        val part = model.uiState.value.parts.single { it.savingTarget }
        assertTrue(part.canBuy)
        assertEquals(4L, part.availableContribution)
        assertEquals(4L, part.remainingCoins)
        model.onAction(GoalAction.Buy(fixture.goal.goalId, part.id))
        val quote = checkNotNull(model.uiState.value.confirmation)
        assertEquals(20L, quote.fromSavings)
        assertEquals(listOf(SpendPart(BudgetSection.SAVINGS, 4L)), quote.availableParts)
        assertEquals(96L, quote.remainingBalance)
        assertEquals(0L, quote.remainingSavings)
        assertEquals(before, fixture.state)
        assertTrue(fixture.repository.purchases.isEmpty())

        model.onAction(GoalAction.ContextPresented(quote.contextId))
        model.onAction(GoalAction.ConfirmPurchase)
        runCurrent()
        assertEquals(96L, fixture.state.economy.availableBalance)
        assertEquals(0L, fixture.state.economy.savingsBalance)
        assertEquals(BudgetPlan(40, 20, 16, 20), fixture.state.economy.plan)
        assertEquals(part.id, model.uiState.value.purchaseResult?.itemId)
        assertTrue(checkNotNull(fixture.repository.purchases.single().context).complete)
    }

    @Test fun cancellingAPreviewPreservesAllGameStateWithoutDispatch() = runTest(dispatcher) {
        val fixture = Fixture(savings = 20)
        fixture.start()
        val model = fixture.model()
        runCurrent()
        val before = fixture.state
        model.onAction(GoalAction.Buy(fixture.goal.goalId, fixture.goal.itemIds.first()))
        assertNotNull(model.uiState.value.confirmation)
        model.onAction(GoalAction.CancelPurchase)
        runCurrent()
        assertNull(model.uiState.value.confirmation)
        assertEquals(before, fixture.state)
        assertTrue(fixture.repository.purchases.isEmpty())
    }

    @Test fun buyingWithOnlyAvailableMoneyWarnsAboutTheRemainingWeekBeforePayment() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.start(item = 2)
        val model = fixture.model()
        runCurrent()
        val before = fixture.state
        model.onAction(GoalAction.Buy(fixture.goal.goalId, fixture.goal.itemIds[2]))
        val quote = checkNotNull(model.uiState.value.confirmation)
        assertEquals(0L, quote.fromSavings)
        assertEquals(10L, quote.remainingBalance)
        assertEquals(35L, quote.foodNeeded)
        assertEquals(25L, quote.foodShortfall)
        assertEquals(before, fixture.state)
        assertTrue(fixture.repository.purchases.isEmpty())
        model.onAction(GoalAction.ConfirmPurchase)
        runCurrent()
        assertEquals(10L, fixture.state.economy.availableBalance)
        assertTrue((fixture.repository.purchases.single().command as EngineCommand.BuyGoalItem).acceptFoodRisk)
        assertNotNull(model.uiState.value.purchaseResult)
    }

    @Test fun aChangedBalanceClosesTheOldQuoteWithoutBuying() = runTest(dispatcher) {
        val fixture = Fixture(savings = 20)
        fixture.start()
        val model = fixture.model()
        runCurrent()
        model.onAction(GoalAction.Buy(fixture.goal.goalId, fixture.goal.itemIds.first()))
        val oldQuote = checkNotNull(model.uiState.value.confirmation)
        fixture.send(EngineCommand.DepositSavings(10))
        val afterDeposit = fixture.state
        runCurrent()
        assertNull(model.uiState.value.confirmation)
        model.onAction(GoalAction.ContextPresented(oldQuote.contextId))
        model.onAction(GoalAction.ConfirmPurchase)
        runCurrent()
        assertEquals(afterDeposit, fixture.state)
        assertTrue(fixture.repository.purchases.isEmpty())
        model.onAction(GoalAction.Buy(fixture.goal.goalId, fixture.goal.itemIds.first()))
        assertEquals(24L, model.uiState.value.confirmation?.fromSavings)
        assertNotEquals(oldQuote.contextId, model.uiState.value.confirmation?.contextId)
    }

    @Test fun insufficientCombinedMoneyNeverShowsConfirmationOrWrites() = runTest(dispatcher) {
        val fixture = Fixture(savings = 20, available = 3)
        fixture.start()
        val model = fixture.model()
        runCurrent()
        val before = fixture.state
        val part = model.uiState.value.parts.single { it.savingTarget }
        assertFalse(part.canBuy)
        assertEquals(1L, part.missingCoins)
        model.onAction(GoalAction.Buy(fixture.goal.goalId, part.id))
        runCurrent()
        assertEquals(before, fixture.state)
        assertNull(model.uiState.value.confirmation)
        assertNull(model.uiState.value.purchaseResult)
        assertTrue(fixture.repository.purchases.isEmpty())
    }

    private inner class Fixture(savings: Long = 0, available: Long = 100,
        plan: BudgetPlan = BudgetPlan(available, 0, 0, 0)) {
        val catalog = bundledGameCatalog()
        val goal = catalog.goals.first()
        private val initial = createInitialGameState().copy(economy = EconomyState(
            plan, availableBalance = available, savingsBalance = savings))
        val repository = MemoryRepository(initial)
        val state get() = repository.state.value
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        private var sequence = 0
        suspend fun send(command: EngineCommand) {
            val result = session.dispatch(EngineRequest("fixture-${sequence++}", state.engine?.revision, command))
            assertTrue("$result", result is EngineResult.Applied)
        }
        suspend fun start(item: Int = 0) {
            session.prepare()
            send(session.selectSavingGoalCommand(state, goal.goalId, goal.itemIds[item]))
            val plan = checkNotNull(state.economy.planning)
            send(EngineCommand.ConfirmBudget(plan.id, plan.revision))
        }
        fun model(handle: SavedStateHandle = SavedStateHandle()) = GoalViewModel(session, handle).also {
            store.put("goal-${sequence++}", it)
        }
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        val state = MutableStateFlow(initial)
        var failWrite = false
        private val requests = mutableListOf<EngineRequest>()
        val purchases get() = requests.filter { it.command is EngineCommand.BuyGoalItem }
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>,
            transform: (GameState) -> GameState): GameState {
            requests += request
            return update(transform)
        }
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            val next = transform(state.value)
            if (failWrite) throw java.io.IOException("Write failed")
            state.value = next
            return next
        }
    }
}
