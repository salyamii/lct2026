package ru.nksk.lctapp.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.LivePetReaction
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.engine.PetEventCondition
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetVisualState

/** A root-scoped presentation clock. Expiry never changes the saved pet or gameplay. */
@HiltViewModel
internal class PetReactionViewModel @Inject constructor(session: GameSession) : ViewModel() {
    private val state = MutableStateFlow<LivePetReaction?>(null)
    val uiState = state.asStateFlow()
    private var visualState: PetVisualState? = null
    private var expiry: Job? = null

    init {
        viewModelScope.launch {
            try {
                session.observe().retryWhen { error, attempt ->
                    if (error is CancellationException || error !is Exception) throw error
                    clearReaction()
                    // A transient read can recover, but a broken store cannot cause a hot retry loop.
                    if (attempt < 2) {
                        delay(2_000)
                        true
                    } else false
                }.collect { game ->
                    if (game == null) clearReaction() else present(game, session)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Null means unavailable; do not substitute the new-game fixture or write a reset.
                clearReaction()
            }
        }
    }

    private fun present(game: GameState, session: GameSession) {
        val effective = PetEventCondition.forPresentation(game, session.catalog.policies)
        val changed = visualState != effective.visualState
        val visible = if (changed) effective.visualState != PetVisualState.NORMAL
            else state.value?.reactionVisible == true
        state.value = LivePetReaction(game.pet, effective, visible)
        if (!changed) return

        visualState = effective.visualState
        expiry?.cancel()
        expiry = if (visible) viewModelScope.launch {
            delay(4_000)
            // Keep the latest name, color and selected equipment if they changed during the window.
            state.value = state.value?.copy(reactionVisible = false)
        } else null
    }

    private fun clearReaction() {
        expiry?.cancel()
        expiry = null
        visualState = null
        state.value = null
    }
}
