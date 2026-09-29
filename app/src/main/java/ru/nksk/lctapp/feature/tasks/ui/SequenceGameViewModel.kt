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
    val inputSignals: Set<Int> = emptySet(),
) {
    val roundNumber: Int get() = minOf(game.round + 1, game.roundLimit)
}

sealed interface SequenceGameAction {
    data class Tap(val signal: Int) : SequenceGameAction
    data object Restart : SequenceGameAction
    data object ArtworkReady : SequenceGameAction
}

@HiltViewModel
class SequenceGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(restore())
    val uiState = mutableUiState.asStateFlow()
    private var showJob: Job? = null
    private var feedbackJob: Job? = null
    private val inputPulseJobs = mutableMapOf<Int, Job>()
    private var artworkReady = false

    init {
        publish(uiState.value)
        runPhase()
    }

    fun onAction(action: SequenceGameAction) {
        when (action) {
            SequenceGameAction.ArtworkReady -> if (!artworkReady) {
                artworkReady = true
                runPhase()
            }
            is SequenceGameAction.Tap -> {
                if (uiState.value.phase != SequencePhase.INPUT) return
                val before = uiState.value.game
                val after = before.tap(action.signal)
                if (before == after) return
                val inputSignals = uiState.value.inputSignals + action.signal
                if (after.lastCorrect == null) {
                    // Верный сигнал в середине раунда: ввод продолжается, фаза не меняется.
                    publish(uiState.value.copy(game = after, inputSignals = inputSignals))
                } else {
                    publish(SequenceGameUiState(after, SequencePhase.FEEDBACK, inputSignals = inputSignals))
                    advanceAfterFeedback()
                }
                pulseInput(action.signal)
            }
            SequenceGameAction.Restart -> {
                showJob?.cancel()
                feedbackJob?.cancel()
                cancelInputPulses()
                publish(SequenceGameUiState(SequenceState.create(), SequencePhase.SHOWING))
                runPhase()
            }
        }
    }

    /** A pulse never locks input; simultaneous fast taps keep their own visible feedback. */
    private fun pulseInput(signal: Int) {
        inputPulseJobs.remove(signal)?.cancel()
        inputPulseJobs[signal] = viewModelScope.launch {
            delay(INPUT_PULSE_MS)
            mutableUiState.value = uiState.value.copy(inputSignals = uiState.value.inputSignals - signal)
            inputPulseJobs.remove(signal)
        }
    }

    private fun cancelInputPulses() {
        inputPulseJobs.values.forEach { it.cancel() }
        inputPulseJobs.clear()
    }

    private fun advanceAfterFeedback() {
        val game = uiState.value.game
        if (game.finished) return
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(900)
            cancelInputPulses()
            publish(SequenceGameUiState(uiState.value.game.next(), SequencePhase.SHOWING))
            runPhase()
        }
    }

    /** Прокручивает показ вспышек текущей последовательности, затем открывает ввод. */
    private fun runPhase() {
        when (uiState.value.phase) {
            SequencePhase.SHOWING -> {
                if (!artworkReady) return
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
        savedState["round_limit"] = state.game.roundLimit
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
            roundLimit = savedState["round_limit"] ?: SequenceState.LEGACY_ROUNDS,
        )
        if (!saved.isValid) return freshBoard()
        // Незавершённый раунд перепоказывается целиком: ввод всегда начинается с первого сигнала.
        val unfinished = !saved.finished && saved.lastCorrect == null
        val game = if (unfinished && saved.position != 0) saved.copy(position = 0) else saved
        val phase = if (unfinished) SequencePhase.SHOWING else SequencePhase.FEEDBACK
        return SequenceGameUiState(game, phase)
    }

    private fun freshBoard(): SequenceGameUiState =
        SequenceGameUiState(SequenceState.create(), SequencePhase.SHOWING)

    private companion object {
        const val SHOW_MS = 600L
        const val HIDE_MS = 200L
        const val INPUT_PULSE_MS = 220L
    }
}
