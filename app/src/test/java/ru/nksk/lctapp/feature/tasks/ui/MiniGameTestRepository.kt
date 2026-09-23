package ru.nksk.lctapp.feature.tasks.ui

import kotlinx.coroutines.flow.MutableStateFlow
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

internal class MiniGameTestRepository(initial: GameState = createInitialGameState().let { it.copy(economy = EconomyState(plan = BudgetPlan(35, 20, 20, 25), unallocated = 0, planning = null)) }) : GameRepository {
    private val state = MutableStateFlow<GameState?>(initial)
    var failure: Exception? = null
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value ?: initial.also { state.value = it }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        failure?.let { throw it }
        return transform(checkNotNull(state.value)).also { state.value = it }
    }
}
