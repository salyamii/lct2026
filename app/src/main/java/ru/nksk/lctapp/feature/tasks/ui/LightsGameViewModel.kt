package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.LightsState

data class LightsGameUiState(val game: LightsState)

sealed interface LightsGameAction {
    data class Tap(val index: Int) : LightsGameAction
    data object Restart : LightsGameAction
}

@HiltViewModel
class LightsGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LightsGameUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    init { publish(uiState.value.game) }

    fun onAction(action: LightsGameAction) {
        when (action) {
            is LightsGameAction.Tap -> publish(uiState.value.game.tap(action.index))
            LightsGameAction.Restart -> publish(LightsState.createRandom())
        }
    }

    private fun publish(game: LightsState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["grid"] = game.grid.toBooleanArray()
        savedState["moves"] = game.moves
        mutableUiState.value = LightsGameUiState(game)
    }

    private fun restore(): LightsState {
        val grid = savedState.get<BooleanArray>("grid") ?: return LightsState.createRandom()
        return LightsState(grid = grid.toList(), moves = savedState["moves"] ?: 0)
    }
}
