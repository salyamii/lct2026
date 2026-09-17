package ru.nksk.lctapp.feature.menu.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.game.GameState

/** Initial in-memory projection; game saving and observation will be added with working features. */
internal class MainMenuViewModel(initialGameState: GameState) : ViewModel() {
    val uiState: StateFlow<MainMenuUiState> =
        MutableStateFlow(initialGameState.toMainMenuUiState()).asStateFlow()
}
