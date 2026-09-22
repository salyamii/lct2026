package ru.nksk.lctapp.feature.economy.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

@OptIn(ExperimentalCoroutinesApi::class)
class EconomyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun openingAllocatedBudgetIsReadOnlyUntilFirstEditThenPersistsManualDraft() = runTest(dispatcher) {
        val original = createInitialGameState().copy(economy = EconomyState(BudgetPlan(5, 0, 10, 5)))
        val repo = BudgetRepository(original)
        val model = EconomyViewModel(repo)
        advanceUntilIdle()
        assertNull(repo.state.value!!.economy.planning)
        model.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, false))
        advanceUntilIdle()
        assertEquals(BudgetPlanningReason.MANUAL, repo.state.value!!.economy.planning!!.reason)
        assertEquals(5L, repo.state.value!!.economy.unallocated)
        val restored = EconomyViewModel(repo)
        advanceUntilIdle()
        restored.onAction(EconomyAction.Adjust(BudgetArticle.RESERVE, true))
        advanceUntilIdle()
        restored.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertNull(repo.state.value!!.economy.planning)
        assertEquals(20L, repo.state.value!!.economy.balance)
        assertEquals(BudgetPlan(5, 0, 5, 10), repo.state.value!!.economy.plan)
    }

    @Test fun receiptTransitionAndDraftSurviveNewViewModelWithoutAnotherGrant() = runTest(dispatcher) {
        val repo = BudgetRepository(createInitialGameState())
        val first = EconomyViewModel(repo)
        advanceUntilIdle()
        assertEquals(BudgetPlanningStage.RECEIPT, first.uiState.value.economy!!.planning!!.stage)
        first.onAction(EconomyAction.StartAllocation)
        advanceUntilIdle()
        first.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        val recreated = EconomyViewModel(repo)
        advanceUntilIdle()
        assertEquals(BudgetPlanningStage.ALLOCATION, recreated.uiState.value.economy!!.planning!!.stage)
        assertEquals(35L, recreated.uiState.value.economy!!.plan.needs)
        assertEquals(65L, recreated.uiState.value.economy!!.unallocated)
        assertEquals(100L, recreated.uiState.value.economy!!.balance)
    }

    @Test fun queuedRelativePressesApplyToLatestCommittedAmounts() = runTest(dispatcher) {
        val repo = BudgetRepository(allocating())
        val model = EconomyViewModel(repo)
        advanceUntilIdle()
        repeat(7) { model.onAction(EconomyAction.Adjust(BudgetArticle.NEEDS, true)) }
        assertTrue(model.uiState.value.saving)
        advanceUntilIdle()
        assertEquals(35L, repo.state.value!!.economy.plan.needs)
        assertEquals(65L, repo.state.value!!.economy.unallocated)
        assertFalse(model.uiState.value.saving)
    }

    @Test fun failedWriteKeepsStageAndCanBeRetried() = runTest(dispatcher) {
        val repo = BudgetRepository(createInitialGameState())
        val model = EconomyViewModel(repo)
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

    @Test fun confirmationRequiresAllMoneyAndNeedsMinimumThenClosesSession() = runTest(dispatcher) {
        val repo = BudgetRepository(allocating())
        val model = EconomyViewModel(repo)
        advanceUntilIdle()
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertNotNull(repo.state.value!!.economy.planning)
        model.onAction(EconomyAction.DismissError)
        model.onAction(EconomyAction.SetAmount(BudgetArticle.NEEDS, 35))
        advanceUntilIdle()
        model.onAction(EconomyAction.SetAmount(BudgetArticle.RESERVE, 65))
        advanceUntilIdle()
        model.onAction(EconomyAction.Confirm)
        advanceUntilIdle()
        assertNull(repo.state.value!!.economy.planning)
        assertEquals(100L, repo.state.value!!.economy.balance)
    }

    @Test fun redistributionAndRetryPreserveTheEntireUnfinishedEventAndDay() = runTest(dispatcher) {
        for (status in listOf(EventStatus.ACTIVE, EventStatus.RESULT, EventStatus.PAUSED)) {
            for (origin in EventOrigin.entries) {
                val occurrence = EventOccurrence("current-occurrence", "event", origin, status,
                    if (origin == EventOrigin.DEED) "offer" else null)
                val original = createInitialGameState().let { initial -> initial.copy(
                    economy = EconomyState(BudgetPlan(35, 20, 20, 25)),
                    ownedItems = listOf(OwnedItem("already-owned", "item")),
                    completedMiniGames = setOf("earlier-game"),
                    story = initial.story.copy(currentDayId = "story-day",
                        activeEventId = if (status == EventStatus.PAUSED) null else "event",
                        decisions = listOf(StoryDecision("previous-decision", "previous-choice"))),
                    engine = EngineState("rules", 42, 4, DayPhase.RUNNING, 2, 3, true, 3, 105,
                        listOf(occurrence, EventOccurrence("next", "next-event", EventOrigin.SCHEDULE, EventStatus.PENDING)),
                        listOf(DeedOffer("offer", "event", 5)), openingEnergy = 5,
                        journal = listOf(DayJournalEntry("paid-on-open", DayJournalKind.EVENT_START, "event", -5))),
                ) }
                val repo = BudgetRepository(original)
                val model = EconomyViewModel(repo)
                advanceUntilIdle()
                fun assertOnlyMoneyAndRevisionChanged(revision: Long) {
                    val saved = repo.state.value!!
                    assertEquals(original.copy(economy = saved.economy,
                        engine = original.engine!!.copy(revision = revision)), saved)
                }
                model.onAction(EconomyAction.Adjust(BudgetArticle.SAVINGS, false))
                advanceUntilIdle()
                assertOnlyMoneyAndRevisionChanged(43)
                val partial = repo.state.value!!
                val restored = EconomyViewModel(repo)
                advanceUntilIdle()
                assertEquals(partial.economy, restored.uiState.value.economy)
                repo.failNext = true
                restored.onAction(EconomyAction.Adjust(BudgetArticle.RESERVE, true))
                advanceUntilIdle()
                assertEquals(partial, repo.state.value)
                assertNotNull(restored.uiState.value.error)
                restored.onAction(EconomyAction.Retry)
                advanceUntilIdle()
                assertOnlyMoneyAndRevisionChanged(44)
                restored.onAction(EconomyAction.Confirm)
                advanceUntilIdle()
                assertOnlyMoneyAndRevisionChanged(45)
                assertNull(repo.state.value!!.economy.planning)
                assertEquals(BudgetPlan(35, 20, 15, 30), repo.state.value!!.economy.plan)
            }
        }
    }

    private fun allocating() = createInitialGameState().let { game ->
        game.copy(economy = EconomyOperations.startAllocation(game.economy, "initial", 0))
    }
}

private class BudgetRepository(initial: GameState) : GameRepository {
    val state = MutableStateFlow<GameState?>(initial)
    var failNext = false
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value ?: initial.also { state.value = it }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        if (failNext) { failNext = false; error("Disk full") }
        return transform(checkNotNull(state.value)).also { state.value = it }
    }
}
