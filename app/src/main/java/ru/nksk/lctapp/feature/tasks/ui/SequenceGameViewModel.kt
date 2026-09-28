package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.minigame.SequenceState

/** Показ вспышек и ввод повторения разнесены по фазам, чтобы тапы не терялись. */
enum class SequencePhase { SHOWING, INPUT, FEEDBACK }

data class SequenceGameUiState(
    val game: SequenceState,
    val phase: SequencePhase = SequencePhase.INPUT,
    val showingIndex: Int? = null,
) {
    val roundNumber: Int get() = minOf(game.round + 1, SequenceState.ROUNDS)
}

sealed interface SequenceGameAction {
    data class Tap(val signal: Int) : SequenceGameAction
    data object Restart : SequenceGameAction
}

@HiltViewModel
class SequenceGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(restore())
    val uiState = mutableUiState.asStateFlow()
    private var showJob: Job? = null
    private var feedbackJob: Job? = null

    init {
        publish(uiState.value)
        runPhase()
    }

    fun onAction(action: SequenceGameAction) {
        when (action) {
            is SequenceGameAction.Tap -> {
                if (uiState.value.phase != SequencePhase.INPUT) return
                val before = uiState.value.game
                val after = before.tap(action.signal)
                if (before == after) return
                if (after.lastCorrect == null) {
                    // Верный сигнал в середине раунда: ввод продолжается, фаза не меняется.
                    publish(uiState.value.copy(game = after))
                } else {
                    publish(SequenceGameUiState(after, SequencePhase.FEEDBACK))
                    advanceAfterFeedback()
                }
            }
            SequenceGameAction.Restart -> {
                showJob?.cancel()
                feedbackJob?.cancel()
                publish(SequenceGameUiState(SequenceState.create(), SequencePhase.SHOWING))
                runPhase()
            }
        }
    }

    private fun advanceAfterFeedback() {
        val game = uiState.value.game
        if (game.finished) return
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(900)
            publish(SequenceGameUiState(uiState.value.game.next(), SequencePhase.SHOWING))
            runPhase()
        }
    }

    /** Прокручивает показ вспышек текущей последовательности, затем открывает ввод. */
    private fun runPhase() {
        when (uiState.value.phase) {
            SequencePhase.SHOWING -> {
                showJob?.cancel()
                val sequence = uiState.value.game.sequence
                showJob = viewModelScope.launch {
                    try {
                        sequence.forEachIndexed { index, _ ->
                            publish(uiState.value.copy(showingIndex = index))
                            delay(SHOW_MS)
                            publish(uiState.value.copy(showingIndex = null))
                            delay(HIDE_MS)
                        }
                        publish(SequenceGameUiState(uiState.value.game, SequencePhase.INPUT))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    }
                }
            }
            SequencePhase.FEEDBACK -> advanceAfterFeedback()
            SequencePhase.INPUT -> Unit
        }
    }

    private fun publish(state: SequenceGameUiState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["sequence"] = state.game.sequence.toIntArray()
        savedState["round"] = state.game.round
        savedState["correct"] = state.game.correct
        savedState["position"] = state.game.position
        savedState["last_correct"] = state.game.lastCorrect
        savedState["phase"] = state.phase.name
        mutableUiState.value = state
    }

    private fun restore(): SequenceGameUiState {
        val sequence = savedState.get<IntArray>("sequence") ?: return freshBoard()
        val saved = SequenceState(
            sequence = sequence.toList(),
            round = savedState["round"] ?: 0,
            correct = savedState["correct"] ?: 0,
            position = savedState["position"] ?: 0,
            lastCorrect = savedState["last_correct"],
        )
        // Незавершённый раунд перепоказывается целиком: ввод всегда начинается с первого сигнала.
        val unfinished = !saved.finished && saved.lastCorrect == null
        val game = if (unfinished && saved.position != 0) saved.copy(position = 0) else saved
        val phase = when {
            unfinished -> SequencePhase.SHOWING
            else -> savedState.get<String>("phase")?.let { stored ->
                SequencePhase.values().firstOrNull { it.name == stored }
            } ?: SequencePhase.INPUT
        }
        return SequenceGameUiState(game, phase)
    }

    private fun freshBoard(): SequenceGameUiState =
        SequenceGameUiState(SequenceState.create(), SequencePhase.SHOWING)

    private companion object {
        const val SHOW_MS = 600L
        const val HIDE_MS = 200L
    }
}
