package ru.nksk.lctapp.domain.history

import ru.nksk.lctapp.domain.game.GameState

/** Current world and its audit cursor read together, without loading historical checkpoints. */
data class GameSnapshotHead(val runId: String, val state: GameState, val historySequence: Long)
