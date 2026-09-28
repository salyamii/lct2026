package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.domain.economy.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.data.game.content.bundledGameCatalog

@OptIn(ExperimentalCoroutinesApi::class)
class MainMenuViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun persistedStateAndLaterChangesDriveTheMenu() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 42))))
        val model = MainMenuViewModel(session(repository, initial))
        assertEquals(MainMenuLoadState.Loading, model.uiState.value)
        advanceUntilIdle()
        assertEquals(42L, (model.uiState.value as MainMenuLoadState.Ready).menu.coins)
        repository.update { it.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 73)), pet = it.pet.transitionTo(PetVisualState.HAPPY)) }
        advanceUntilIdle()
        assertEquals(73L, (model.uiState.value as MainMenuLoadState.Ready).menu.coins)
        assertEquals(repository.read()!!.toMainMenuUiState().copy(mealPrice = 5), (model.uiState.value as MainMenuLoadState.Ready).menu)
    }

    @Test fun initializationFailureShowsErrorAndRetryPreservesSavedData() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 77))))
        repository.failure = IllegalStateException("Storage unavailable")
        val model = MainMenuViewModel(session(repository, initial))
        advanceUntilIdle()
        assertTrue(model.uiState.value is MainMenuLoadState.Error)
        repository.failure = null
        model.retry()
        advanceUntilIdle()
        assertEquals(77L, (model.uiState.value as MainMenuLoadState.Ready).menu.coins)
    }

    @Test fun observationFailureDoesNotDisplayStartingFixture() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial).apply { observationFailure = IllegalStateException("Invalid stored code") }
        val model = MainMenuViewModel(session(repository, initial))
        advanceUntilIdle()
        assertTrue(model.uiState.value is MainMenuLoadState.Error)
    }

    @Test fun feedingOnAnotherScreenClearsThePreviousHungerNotice() = runTest(dispatcher) {
        val initial = createInitialGameState().let { it.copy(economy = EconomyState(BudgetPlan(35, 20, 20, 25))) }
        val repository = MenuRepository(initial)
        val session = session(repository, initial)
        assertTrue(session.dispatch(EngineRequest("begin", null, EngineCommand.BeginDay(
            session.catalog.storyDayId, session.catalog.plan(initial),
        ))) is EngineResult.Applied)
        repository.update { it.copy(engine = it.engine!!.copy(steps = 3)) }
        val model = MainMenuViewModel(session)
        advanceUntilIdle()

        model.continueDay()
        advanceUntilIdle()
        assertTrue((model.uiState.value as MainMenuLoadState.Ready).menu.notice!!.contains("проголодался"))

        assertTrue(session.dispatch(EngineRequest("feed-in-event", repository.read()!!.engine!!.revision,
            EngineCommand.Feed(session.catalog.meals.first { it.price > 0 }.id))) is EngineResult.Applied)
        advanceUntilIdle()
        val menu = (model.uiState.value as MainMenuLoadState.Ready).menu
        assertTrue(menu.dayStatus!!.endsWith("сыт"))
        assertNull(menu.notice)
        assertFalse(menu.canFeed)
        assertEquals("Продолжить день", menu.continueLabel)
    }

    private fun session(repository: GameRepository, initial: GameState, catalog: GameCatalog = bundledGameCatalog()) = GameSession(repository, object : StoryContentRepository {
        private var content = StoryContent()
        override suspend fun read() = content
        override suspend fun install(content: StoryContent) { this.content = content }
    }, catalog, initial)

    @Test fun unallocatedMoneyWithoutAnOpenPlanRoutesToBudgetWithoutAdvancingTheWorld() = runTest(dispatcher) {
        val initial = createInitialGameState().copy(economy = EconomyState(BudgetPlan(35, 0, 0, 0), unallocated = 1))
        val repository = MenuRepository(initial)
        val model = MainMenuViewModel(session(repository, initial))
        var openedBudget = 0
        var openedDay = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openBudget.collect { openedBudget++ } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openDay.collect { openedDay++ } }
        advanceUntilIdle()
        val before = repository.read()
        assertNull(before!!.economy.planning)
        assertEquals(1L, before.economy.unallocated)

        model.continueDay()
        advanceUntilIdle()

        assertEquals(1, openedBudget)
        assertEquals(0, openedDay)
        assertEquals(before, repository.read())
    }

    @Test fun finaleBlockedByPracticeOpensTrainingWithoutAdvancingTheWorld() = runTest(dispatcher) {
        val content = StoryContent(
            chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1)),
            events = listOf(EventDefinition("final", EventType.STORY, "Finale", "Finale",
                null, null, null, 0, null, null)),
            choices = listOf(EventChoiceDefinition("final:done", "final", 0, "Continue", 0,
                null, null, GoalImpact.NEUTRAL)),
            items = listOf(ItemDefinition("part", "Part", "", priceCoins = 20)),
            goals = listOf(GoalDefinition("goal", "Goal", "")),
            requiredItems = listOf(GoalRequiredItem("goal", "part")),
        )
        val catalog = GameCatalog(content,
            policies = mapOf("final" to EventPolicy(energyCost = 0, storyActId = "act", finishesStoryAct = true)),
            cards = emptyMap(), rules = EngineRules("menu-test", 5, 3, 1),
            meals = listOf(MealDefinition("meal", 5, null)), storyDayId = "day", introductionId = "final",
            deedPool = emptyList(), goals = listOf(GoalCampaign("goal", "final", listOf("part"))),
            storyCampaign = StoryCampaign(listOf(StoryAct("act", "Act", "day", listOf("final"), "final", goalId = "goal"))))
        val initial = createInitialGameState().copy(
            economy = EconomyState(BudgetPlan(35, 0, 0, 0)), selectedGoalId = "goal",
            ownedItems = listOf(OwnedItem("owned-part", "part")),
            financial = FinancialProgress(currentPeriodId = "period", periods = listOf(
                FinancialPeriod("period", "goal", 1, 1, 35, 0, needsProvided = true))),
            engine = EngineState("menu-test", 0, 1, DayPhase.RUNNING, 0, 5, true, null, 35,
                listOf(EventOccurrence("final-occurrence", "final", EventOrigin.SCHEDULE, EventStatus.PENDING)), emptyList()),
        )
        val repository = MenuRepository(initial)
        val model = MainMenuViewModel(session(repository, initial, catalog))
        var openedTraining = 0
        var openedDay = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openTraining.collect { openedTraining++ } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openDay.collect { openedDay++ } }
        advanceUntilIdle()
        val before = repository.read()

        model.continueDay()
        advanceUntilIdle()

        assertEquals(1, openedTraining)
        assertEquals(0, openedDay)
        assertEquals(before, repository.read())
        assertTrue((model.uiState.value as MainMenuLoadState.Ready).menu.notice!!.contains("практику"))
    }

    @Test fun proactiveFeedingWithoutMoneyOffersTheFreeMealOnTheMenu() = runTest(dispatcher) {
        val initial = createInitialGameState().let { it.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 0))) }
        val repository = MenuRepository(initial)
        val session = session(repository, initial)
        assertTrue(session.dispatch(EngineRequest("begin", null, EngineCommand.BeginDay(
            session.catalog.storyDayId, session.catalog.plan(initial),
        ))) is EngineResult.Applied)
        repository.update { it.copy(engine = it.engine!!.copy(steps = 1)) }
        val model = MainMenuViewModel(session)
        var openedDay = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openDay.collect { openedDay++ } }
        advanceUntilIdle()

        model.feed()
        advanceUntilIdle()
        assertTrue((model.uiState.value as MainMenuLoadState.Ready).menu.showFreeMeal)
        assertEquals(0, openedDay)
        assertFalse(repository.read()!!.engine!!.ateToday)
        model.dismissFreeMeal()
        assertFalse((model.uiState.value as MainMenuLoadState.Ready).menu.showFreeMeal)
        assertFalse(repository.read()!!.engine!!.ateToday)

        model.feed()
        advanceUntilIdle()
        repository.failure = IllegalStateException("Save failed")
        model.feedFree()
        advanceUntilIdle()
        assertTrue((model.uiState.value as MainMenuLoadState.Ready).menu.showFreeMeal)
        assertNotNull((model.uiState.value as MainMenuLoadState.Ready).menu.notice)
        assertFalse(repository.read()!!.engine!!.ateToday)

        repository.failure = null
        model.feedFree()
        advanceUntilIdle()
        val fed = repository.read()!!
        assertTrue(fed.engine!!.ateToday)
        assertEquals(3, fed.engine!!.nextMorningEnergy)
        assertEquals(0, fed.engine!!.energy)
        assertEquals(1, fed.engine!!.steps)
        assertNull(fed.engine!!.currentEvent)
        assertEquals(0L, fed.economy.balance)
        assertFalse((model.uiState.value as MainMenuLoadState.Ready).menu.showFreeMeal)
        assertEquals(0, openedDay)
    }
}

private class MenuRepository(initial: GameState?) : GameRepository {
    private val state = MutableStateFlow(initial)
    var failure: Exception? = null
    var observationFailure: Exception? = null
    override fun observe(): Flow<GameState?> = observationFailure?.let { error -> flow { throw error } } ?: state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState): GameState {
        failure?.let { throw it }
        return state.value ?: initial.also { state.value = it }
    }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        failure?.let { throw it }
        return transform(checkNotNull(state.value)).also { state.value = it }
    }
}
