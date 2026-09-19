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
import ru.nksk.lctapp.domain.minigame.TargetStopState

data class TargetStopUiState(val game: TargetStopState, val stoppedPosition: Float? = null) {
    val roundNumber: Int get() = minOf(game.round + if (game.lastHit == null) 1 else 0, TargetStopState.ROUNDS)
}

sealed interface TargetStopAction {
    data class Stop(val position: Float) : TargetStopAction
    data object Restart : TargetStopAction
}

@HiltViewModel
class TargetStopViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(restore())
    val uiState = mutableUiState.asStateFlow()
    private var feedbackJob: Job? = null

    init {
        publish(uiState.value)
        advanceAfterFeedback()
    }

    fun onAction(action: TargetStopAction) {
        when (action) {
            is TargetStopAction.Stop -> {
                if (!action.position.isFinite()) return
                val before = uiState.value.game
                val position = action.position.coerceIn(0f, 1f)
                val after = before.stop((position * 100).toInt())
                if (before != after) {
                    publish(TargetStopUiState(after, position))
                    advanceAfterFeedback()
                }
            }
            TargetStopAction.Restart -> {
                feedbackJob?.cancel()
                publish(TargetStopUiState(TargetStopState.create()))
            }
        }
    }

    private fun advanceAfterFeedback() {
        val game = uiState.value.game
        if (game.lastHit == null || game.finished) return
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(900)
            publish(TargetStopUiState(uiState.value.game.next()))
        }
    }

    private fun publish(state: TargetStopUiState) {
        savedState["zone"] = state.game.zoneStart
        savedState["round"] = state.game.round
        savedState["hits"] = state.game.hits
        savedState["last_hit"] = state.game.lastHit
        savedState["position"] = state.stoppedPosition
        mutableUiState.value = state
    }

    private fun restore(): TargetStopUiState {
        val zone = savedState.get<Int>("zone") ?: return TargetStopUiState(TargetStopState.create())
        return TargetStopUiState(
            TargetStopState(
                zoneStart = zone,
                round = savedState["round"] ?: 0,
                hits = savedState["hits"] ?: 0,
                lastHit = savedState["last_hit"],
            ),
            stoppedPosition = savedState["position"],
        )
    }
}
