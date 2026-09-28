package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState

/** Transient observation of a newly committed command; never a second source of game state. */
data class AppliedGameCommand(val request: EngineRequest, val before: GameState, val after: GameState)
