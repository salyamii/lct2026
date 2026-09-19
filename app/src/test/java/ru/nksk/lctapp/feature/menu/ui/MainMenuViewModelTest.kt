package ru.nksk.lctapp.feature.menu.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainMenuViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun persistedStateAndLaterChangesDriveTheMenu() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial.copy(satiety = 42))
        val model = MainMenuViewModel(repository, initial)
        assertEquals(MainMenuLoadState.Loading, model.uiState.value)
        advanceUntilIdle()
        assertEquals(42, (model.uiState.value as MainMenuLoadState.Ready).menu.hunger)
        repository.update { it.copy(satiety = 73, pet = it.pet.transitionTo(PetVisualState.HAPPY)) }
        advanceUntilIdle()
        assertEquals(73, (model.uiState.value as MainMenuLoadState.Ready).menu.hunger)
        assertEquals(repository.read()!!.toMainMenuUiState(), (model.uiState.value as MainMenuLoadState.Ready).menu)
    }

    @Test fun initializationFailureShowsErrorAndRetryPreservesSavedData() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial.copy(satiety = 77))
        repository.failure = IllegalStateException("Storage unavailable")
        val model = MainMenuViewModel(repository, initial)
        advanceUntilIdle()
        assertTrue(model.uiState.value is MainMenuLoadState.Error)
        repository.failure = null
        model.retry()
        advanceUntilIdle()
        assertEquals(77, (model.uiState.value as MainMenuLoadState.Ready).menu.hunger)
    }

    @Test fun observationFailureDoesNotDisplayStartingFixture() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val repository = MenuRepository(initial).apply { observationFailure = IllegalStateException("Invalid stored code") }
        val model = MainMenuViewModel(repository, initial)
        advanceUntilIdle()
        assertTrue(model.uiState.value is MainMenuLoadState.Error)
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
    override suspend fun update(transform: (GameState) -> GameState): GameState = transform(checkNotNull(state.value)).also { state.value = it }
}
