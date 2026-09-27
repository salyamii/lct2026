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
        runCurrent()
        assertNotNull(model.uiState.value.message)
        assertNull(model.uiState.value.purchaseResult)
        assertEquals(before, fixture.state)

        fixture.repository.failWrite = false
        model.onAction(GoalAction.Buy(fixture.goal.goalId, fixture.goal.itemIds.first()))
        runCurrent()
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

    @Test fun insufficientPurchaseNeverUsesAvailableMoneyOrShowsSuccess() = runTest(dispatcher) {
        val fixture = Fixture(savings = 20)
        fixture.start()
        val model = fixture.model()
        runCurrent()
        val before = fixture.state
        val part = model.uiState.value.parts.single { it.savingTarget }
        assertFalse(part.canBuy)
        assertEquals(4L, part.missingCoins)
        model.onAction(GoalAction.Buy(fixture.goal.goalId, part.id))
        runCurrent()
        assertEquals(before, fixture.state)
        assertNull(model.uiState.value.purchaseResult)
    }

    private inner class Fixture(savings: Long = 0) {
        val catalog = bundledGameCatalog()
        val goal = catalog.goals.first()
        private val initial = createInitialGameState().copy(economy = EconomyState(
            BudgetPlan(100, 0, 0, 0), availableBalance = 100, savingsBalance = savings))
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
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            val next = transform(state.value)
            if (failWrite) throw java.io.IOException("Write failed")
            state.value = next
            return next
        }
    }
}
