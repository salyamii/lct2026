package ru.nksk.lctapp.domain.minigame

import ru.nksk.lctapp.domain.game.GameState

/** Costs of the three bundled mini-games; story deeds have their own rules. */
enum class MiniGameKind(val fatigueCost: Int) {
    MEMORY(30), PRICE_QUIZ(50), TELESCOPE(40);

    val hungerCost: Int get() = 20

    fun canPlay(state: GameState): Boolean =
        state.satiety in 0..(100 - hungerCost) && state.fatigue in 0..(100 - fatigueCost)

    /** Apply only after success, inside the aggregate repository's write transaction. */
    fun complete(state: GameState, attemptId: String): GameState {
        require(attemptId.isNotBlank())
        if (attemptId in state.completedMiniGames) return state
        if (!canPlay(state)) throw MiniGameUnavailableException()
        return state.copy(
            satiety = state.satiety + hungerCost,
            fatigue = state.fatigue + fatigueCost,
            completedMiniGames = state.completedMiniGames + attemptId,
        )
    }
}

class MiniGameUnavailableException : IllegalStateException("Mini-game resource limit exceeded")
