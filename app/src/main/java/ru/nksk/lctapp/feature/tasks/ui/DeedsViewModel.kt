package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.minigame.MiniGameKind

data class DeedsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val hunger: Int = 0,
    val fatigue: Int = 0,
    val available: Set<MiniGameKind> = emptySet(),
)

@HiltViewModel
class DeedsViewModel @Inject constructor(private val repository: GameRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DeedsUiState())
    val uiState = mutableState.asStateFlow()
    private var observer: Job? = null

    init { retry() }

    fun retry() {
        observer?.cancel()
        mutableState.value = DeedsUiState()
        observer = viewModelScope.launch {
            try {
                repository.observe().collect { saved ->
                    val game = checkNotNull(saved)
                    mutableState.value = DeedsUiState(
                        loading = false, hunger = game.satiety, fatigue = game.fatigue,
                        available = MiniGameKind.entries.filter { it.canPlay(game) }.toSet(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = DeedsUiState(loading = false, error = true)
            }
        }
    }
}
