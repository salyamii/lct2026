package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.DifferencesState

data class DifferencesGameUiState(val game: DifferencesState)

sealed interface DifferencesGameAction {
    data class Tap(val cell: Int) : DifferencesGameAction
    data object Restart : DifferencesGameAction
}

@HiltViewModel
class DifferencesGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DifferencesGameUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    init { publish(uiState.value.game) }

    fun onAction(action: DifferencesGameAction) {
        when (action) {
            is DifferencesGameAction.Tap -> publish(uiState.value.game.tap(action.cell))
            DifferencesGameAction.Restart -> publish(DifferencesState.createRandom())
        }
    }

    private fun publish(game: DifferencesState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["top"] = game.top.toIntArray()
        savedState["bottom"] = game.bottom.toIntArray()
        savedState["found"] = game.found.toIntArray()
        savedState["taps"] = game.taps
        mutableUiState.value = DifferencesGameUiState(game)
    }

    private fun restore(): DifferencesState {
        val top = savedState.get<IntArray>("top") ?: return DifferencesState.createRandom()
        return DifferencesState(
            top = top.toList(),
            bottom = requireNotNull(savedState.get<IntArray>("bottom")).toList(),
            found = savedState.get<IntArray>("found")?.toSet().orEmpty(),
            taps = savedState["taps"] ?: 0,
        )
    }
}
