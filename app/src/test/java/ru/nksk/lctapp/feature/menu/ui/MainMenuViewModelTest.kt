package ru.nksk.lctapp.feature.menu.ui

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
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineResult
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.data.game.content.bundledGameCatalog

@OptIn(ExperimentalCoroutinesApi::class)
class MainMenuViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun persistedStateAndLaterChangesDriveTheMenu() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial.copy(economy = initial.economy.copy(balance = 42)))
        val model = MainMenuViewModel(session(repository, initial))
        assertEquals(MainMenuLoadState.Loading, model.uiState.value)
        advanceUntilIdle()
        assertEquals(42L, (model.uiState.value as MainMenuLoadState.Ready).menu.coins)
        repository.update { it.copy(economy = it.economy.copy(balance = 73), pet = it.pet.transitionTo(PetVisualState.HAPPY)) }
        advanceUntilIdle()
        assertEquals(73L, (model.uiState.value as MainMenuLoadState.Ready).menu.coins)
        assertEquals(repository.read()!!.toMainMenuUiState().copy(mealPrice = 5), (model.uiState.value as MainMenuLoadState.Ready).menu)
    }

    @Test fun initializationFailureShowsErrorAndRetryPreservesSavedData() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial.copy(economy = initial.economy.copy(balance = 77)))
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
        val initial = createInitialGameState()
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
        assertTrue(menu.dayStatus!!.endsWith("Сыт"))
        assertNull(menu.notice)
        assertFalse(menu.canFeed)
        assertEquals("Продолжить день", menu.continueLabel)
    }

    private fun session(repository: GameRepository, initial: GameState) = GameSession(repository, object : StoryContentRepository {
        private var content = StoryContent()
        override suspend fun read() = content
        override suspend fun install(content: StoryContent) { this.content = content }
    }, bundledGameCatalog(), initial)

    @Test fun proactiveFeedingWithoutMoneyOffersTheFreeMealOnTheMenu() = runTest(dispatcher) {
        val initial = createInitialGameState().let { it.copy(economy = it.economy.copy(balance = 0)) }
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
        assertEquals(5, fed.engine!!.energy)
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
