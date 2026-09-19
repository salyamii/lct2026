package ru.nksk.lctapp.feature.gear.ui

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
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository

@HiltViewModel
internal class GearViewModel @Inject constructor(
    private val games: GameRepository,
    private val content: StoryContentRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<GearLoadState>(GearLoadState.Loading)
    val uiState: StateFlow<GearLoadState> = mutableState.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch {
            mutableState.value = GearLoadState.Loading
            try {
                games.observe().collect { saved ->
                    val game = checkNotNull(saved) { "Inventory requires an existing save" }
                    // Read after ownership: newly acquired definitions must already be installed.
                    val catalog = content.read()
                    mutableState.value = GearLoadState.Ready(gearUiState(game.ownedItems, catalog.items))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = GearLoadState.Error
            }
        }
    }
}
