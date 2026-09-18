package ru.nksk.lctapp

import kotlinx.coroutines.flow.MutableStateFlow
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

/** In-memory double for navigation tests; real persistence is covered by GamePersistenceTest. */
internal class TestGameRepository(initial: GameState = createInitialGameState()) : GameRepository {
    private val state = MutableStateFlow<GameState?>(initial)
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value ?: initial.also { state.value = it }
    override suspend fun update(transform: (GameState) -> GameState) = transform(checkNotNull(state.value)).also { state.value = it }
}
