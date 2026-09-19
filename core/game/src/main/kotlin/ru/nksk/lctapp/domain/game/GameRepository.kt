package ru.nksk.lctapp.domain.game

import kotlinx.coroutines.flow.Flow

/** The only write boundary for the saved game. Storage errors propagate without resetting it. */
interface GameRepository {
    fun observe(): Flow<GameState?>
    suspend fun read(): GameState?
    suspend fun initializeIfAbsent(initial: GameState): GameState

    /**
     * Reads the latest aggregate inside the write transaction, then atomically stores the result.
     * Transform must be fast, pure, and derive changes from its argument, never a cached UI copy.
     * Gameplay operations own their guards and retry/occurrence semantics; this is not an event engine.
     */
    suspend fun update(transform: (GameState) -> GameState): GameState
}
