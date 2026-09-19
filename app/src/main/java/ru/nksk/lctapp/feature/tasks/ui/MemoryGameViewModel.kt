package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.minigame.MiniGameKind
import ru.nksk.lctapp.domain.minigame.MemoryState
import ru.nksk.lctapp.domain.minigame.PendingPair

data class MemoryGameUiState(val game: MemoryState, val session: MiniGameSessionUiState = MiniGameSessionUiState())

sealed interface MemoryGameAction {
    data class Tap(val index: Int) : MemoryGameAction
    data object Retry : MemoryGameAction
    data object Restart : MemoryGameAction
}

@HiltViewModel
class MemoryGameViewModel @Inject constructor(private val savedState: SavedStateHandle, repository: GameRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MemoryGameUiState(restore()))
    val uiState = mutableUiState.asStateFlow()
    private var feedbackJob: Job? = null
    private val session = MiniGameSession(MiniGameKind.MEMORY, repository, savedState, viewModelScope) {
        mutableUiState.value = mutableUiState.value.copy(session = it)
    }

    init {
        session.observe()
        publish(uiState.value.game)
        resolveAfterFeedback()
    }

    fun onAction(action: MemoryGameAction) {
        when (action) {
            MemoryGameAction.Retry -> session.retry()
            is MemoryGameAction.Tap -> {
                if (!session.state.canPlay) return
                val before = uiState.value.game
                val after = before.tap(action.index)
                if (before != after) {
                    publish(after)
                    resolveAfterFeedback()
                }
            }
            MemoryGameAction.Restart -> {
                if (!session.restart()) return
                feedbackJob?.cancel()
                publish(MemoryState.deal())
            }
        }
    }

    private fun resolveAfterFeedback() {
        if (uiState.value.game.pending == null) return
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(700)
            publish(uiState.value.game.resolvePending())
        }
    }

    private fun publish(game: MemoryState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["faces"] = game.faces.toIntArray()
        savedState["face_up"] = game.faceUp.toIntArray()
        savedState["matched"] = game.matched.toIntArray()
        savedState["moves"] = game.moves
        mutableUiState.value = MemoryGameUiState(game, session.state)
        if (game.won) session.finish(success = true)
    }

    private fun restore(): MemoryState {
        val faces = savedState.get<IntArray>("faces") ?: return MemoryState.deal()
        val open = savedState.get<IntArray>("face_up")?.toList().orEmpty()
        return MemoryState(
            faces = faces.toList(),
            faceUp = open.toSet(),
            matched = savedState.get<IntArray>("matched")?.toSet().orEmpty(),
            pending = if (open.size == 2) PendingPair(open[0], open[1]) else null,
            moves = savedState["moves"] ?: 0,
        )
    }
}
