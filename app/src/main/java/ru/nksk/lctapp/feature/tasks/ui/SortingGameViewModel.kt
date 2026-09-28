package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.SortingState

data class SortingGameUiState(val game: SortingState)

sealed interface SortingGameAction {
    data class Tap(val tube: Int) : SortingGameAction
    data object Restart : SortingGameAction
}

@HiltViewModel
class SortingGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SortingGameUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    init { publish(uiState.value.game) }

    fun onAction(action: SortingGameAction) {
        when (action) {
            is SortingGameAction.Tap -> publish(uiState.value.game.tap(action.tube))
            SortingGameAction.Restart -> publish(SortingState.create())
        }
    }

    private fun publish(game: SortingState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["tubes"] = game.tubes.map { it.toIntArray() }.toTypedArray()
        savedState["selected"] = game.selected
        savedState["moves"] = game.moves
        mutableUiState.value = SortingGameUiState(game)
    }

    private fun restore(): SortingState {
        val tubes = savedState.get<Array<IntArray>>("tubes")
            ?: return SortingState.create()
        return SortingState(
            tubes = tubes.map { it.toList() },
            selected = savedState["selected"],
            moves = savedState["moves"] ?: 0,
        )
    }
}
