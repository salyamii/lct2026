package ru.nksk.lctapp.feature.menu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

internal sealed interface MainMenuLoadState {
    data object Loading : MainMenuLoadState
    data class Ready(val menu: MainMenuUiState) : MainMenuLoadState
    data class Error(val cause: Exception) : MainMenuLoadState
}

/** Repository observation owns runtime data; a fixture is used only for a genuinely absent save. */
@HiltViewModel
internal class MainMenuViewModel @Inject constructor(
    private val repository: GameRepository,
    private val initialGameState: GameState,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MainMenuLoadState>(MainMenuLoadState.Loading)
    val uiState: StateFlow<MainMenuLoadState> = mutableState.asStateFlow()
    private var loading: Job? = null

    init { retry() }

    fun retry() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            mutableState.value = MainMenuLoadState.Loading
            try {
                repository.initializeIfAbsent(initialGameState)
                repository.observe().collect { saved ->
                    val game = checkNotNull(saved) { "Saved game disappeared after initialization" }
                    mutableState.value = MainMenuLoadState.Ready(game.toMainMenuUiState())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = MainMenuLoadState.Error(error)
            }
        }
    }
}
