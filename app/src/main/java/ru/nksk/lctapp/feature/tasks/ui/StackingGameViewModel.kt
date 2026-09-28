package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.minigame.StackedBlock
import ru.nksk.lctapp.domain.minigame.StackingState

data class StackingGameUiState(
    val game: StackingState,
    val missed: Boolean = false,
)

sealed interface StackingGameAction {
    data object Drop : StackingGameAction
    data object Restart : StackingGameAction
}

@HiltViewModel
class StackingGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(restore())
    val uiState = mutableUiState.asStateFlow()

    /** Позиция бегущего ящика живёт отдельно: кадры не трогаютUiState и не пишут сейв. */
    private val mutablePosition = MutableStateFlow(0.5f)
    val position = mutablePosition.asStateFlow()

    private var moveJob: Job? = null
    private var direction = 1f

    init {
        publish(uiState.value)
        runMovement()
    }

    fun onAction(action: StackingGameAction) {
        when (action) {
            StackingGameAction.Drop -> {
                val before = uiState.value
                if (before.game.finished) return
                val after = before.game.dropAt((mutablePosition.value * StackingState.SPACE).toInt())
                publish(StackingGameUiState(after, missed = !after.won && after.finished))
            }
            StackingGameAction.Restart -> {
                moveJob?.cancel()
                direction = 1f
                publish(StackingGameUiState(StackingState.create()))
                mutablePosition.value = 0.5f
                runMovement()
            }
        }
    }

    /** Ведёт переносимый ящик туда-обратно покадрово; движение не пишет сейв и не пересобирает доску. */
    private fun runMovement() {
        moveJob?.cancel()
        val state = uiState.value
        if (state.game.finished || state.missed) return
        moveJob = viewModelScope.launch {
            try {
                var lastFrame = System.nanoTime()
                while (!uiState.value.game.finished && !uiState.value.missed) {
                    delay(FRAME_MS)
                    val now = System.nanoTime()
                    val seconds = (now - lastFrame) / 1_000_000_000f
                    lastFrame = now
                    val width = uiState.value.game.currentWidth.toFloat() / StackingState.SPACE
                    var position = mutablePosition.value + direction * SPEED_PER_SECOND * seconds
                    if (position > 1f - width) {
                        position = 1f - width
                        direction = -1f
                    } else if (position < 0f) {
                        position = 0f
                        direction = 1f
                    }
                    mutablePosition.value = position
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    private fun publish(state: StackingGameUiState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["locked_x"] = state.game.locked.map { it.x }.toIntArray()
        savedState["locked_w"] = state.game.locked.map { it.width }.toIntArray()
        savedState["block_width"] = state.game.blockWidth
        savedState["placed"] = state.game.placed
        savedState["finished"] = state.game.finished
        savedState["missed"] = state.missed
        mutableUiState.value = state
    }

    private fun restore(): StackingGameUiState {
        val xs = savedState.get<IntArray>("locked_x") ?: return StackingGameUiState(StackingState.create())
        val widths = requireNotNull(savedState.get<IntArray>("locked_w"))
        val game = StackingState(
            locked = xs.indices.map { i -> StackedBlock(x = xs[i], width = widths[i]) },
            blockWidth = savedState["block_width"] ?: StackingState.START_WIDTH,
            placed = savedState["placed"] ?: 0,
            finished = savedState["finished"] ?: false,
        )
        return StackingGameUiState(game, missed = savedState["missed"] ?: false)
    }

    private companion object {
        const val SPEED_PER_SECOND = 0.45f
        const val FRAME_MS = 16L
    }
}
