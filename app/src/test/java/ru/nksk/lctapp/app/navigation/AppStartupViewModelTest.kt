package ru.nksk.lctapp.app.navigation

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
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun openingOnboardingDoesNotCreateGameOrStartStory() = runTest(dispatcher) {
        val repository = StartupRepository()
        val model = model(repository)
        advanceUntilIdle()
        assertEquals(AppStartupState.Choose(), model.uiState.value)
        assertNull(repository.read())
        model.startAdventure()
        model.startAdventure()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(createInitialGameState(), repository.read())
        assertNull(repository.read()!!.engine)
        assertEquals(1, repository.initializations)
    }

    @Test fun existingSaveSkipsOnboardingWithoutChangingProgress() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val saved = initial.copy(economy = initial.economy.copy(balance = 37))
        val repository = StartupRepository(saved)
        val model = model(repository)
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(saved, repository.read())
        assertEquals(0, repository.initializations)
    }

    @Test fun readFailureNeverStartsNewGameAndCanBeRetried() = runTest(dispatcher) {
        val repository = StartupRepository(createInitialGameState())
        repository.readFailure = IllegalStateException("Unavailable")
        val model = model(repository)
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Error)
        model.startAdventure()
        advanceUntilIdle()
        assertEquals(0, repository.initializations)
        repository.readFailure = null
        model.retry()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
    }

    @Test fun saveFailureStaysOnOnboardingAndRetryDoesNotReplaceAnExistingSave() = runTest(dispatcher) {
        val repository = StartupRepository()
        val model = model(repository)
        advanceUntilIdle()
        repository.writeFailure = IllegalStateException("Disk full")
        model.startAdventure()
        advanceUntilIdle()
        assertTrue((model.uiState.value as AppStartupState.Choose).failed)
        assertNull(repository.read())
        repository.writeFailure = null
        val saved = createInitialGameState().let { it.copy(economy = it.economy.copy(balance = 59)) }
        repository.state.value = saved
        model.startAdventure()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(saved, repository.read())
    }

    private fun model(repository: GameRepository) = AppStartupViewModel(GameSession(
        repository, object : StoryContentRepository {
            private var content = StoryContent()
            override suspend fun read() = content
            override suspend fun install(content: StoryContent) { this.content = content }
        }, bundledGameCatalog(), createInitialGameState(),
    ))
}

private class StartupRepository(initial: GameState? = null) : GameRepository {
    val state = MutableStateFlow(initial)
    var initializations = 0
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    override fun observe() = state
    override suspend fun read(): GameState? {
        readFailure?.let { throw it }
        return state.value
    }
    override suspend fun initializeIfAbsent(initial: GameState): GameState {
        writeFailure?.let { throw it }
        initializations++
        return state.value ?: initial.also { state.value = it }
    }
    override suspend fun update(transform: (GameState) -> GameState) =
        transform(requireNotNull(state.value)).also { state.value = it }
}
