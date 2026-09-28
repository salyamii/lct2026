package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.SlidingState

data class SlidingGameUiState(val game: SlidingState)

sealed interface SlidingGameAction {
    data class Tap(val index: Int) : SlidingGameAction
    data object Restart : SlidingGameAction
}

@HiltViewModel
class SlidingGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SlidingGameUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    init { publish(uiState.value.game) }

    fun onAction(action: SlidingGameAction) {
        when (action) {
            is SlidingGameAction.Tap -> publish(uiState.value.game.tap(action.index))
            SlidingGameAction.Restart -> publish(SlidingState.shuffled())
        }
    }

    private fun publish(game: SlidingState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["tiles"] = game.tiles.toIntArray()
        savedState["moves"] = game.moves
        mutableUiState.value = SlidingGameUiState(game)
    }

    private fun restore(): SlidingState {
        val tiles = savedState.get<IntArray>("tiles") ?: return SlidingState.shuffled()
        return SlidingState(tiles = tiles.toList(), moves = savedState["moves"] ?: 0)
    }
}
