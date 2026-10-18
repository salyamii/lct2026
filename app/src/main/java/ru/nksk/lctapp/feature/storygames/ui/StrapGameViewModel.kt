package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.StrapPoint
import ru.nksk.lctapp.domain.minigame.chapterone.StrapRepair

data class StrapGameUiState(val game: StrapRepair = StrapRepair())

sealed interface StrapGameAction {
    data class AlignAt(val dx: Float, val dy: Float) : StrapGameAction
    data object Align : StrapGameAction
    data class Trace(val from: StrapPoint, val to: StrapPoint) : StrapGameAction
    data class Stitch(val pointIndex: Int) : StrapGameAction
    data class Pull(val distance: Float) : StrapGameAction
    data object Tighten : StrapGameAction
    data object Restart : StrapGameAction
}

@HiltViewModel
class StrapGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StrapGameUiState(StrapRepair.restore(
        aligned = savedState["strap_aligned"] ?: false,
        stitchedPoints = savedState["strap_stitches"] ?: 0,
        knotTightened = savedState["strap_knotted"] ?: false,
    )))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: StrapGameAction) {
        val current = mutableUiState.value.game
        val next = when (action) {
            is StrapGameAction.AlignAt -> current.alignAt(action.dx, action.dy)
            StrapGameAction.Align -> current.align()
            is StrapGameAction.Trace -> current.trace(action.from, action.to)
            is StrapGameAction.Stitch -> current.stitch(action.pointIndex)
            is StrapGameAction.Pull -> current.pull(action.distance)
            StrapGameAction.Tighten -> current.tighten()
            StrapGameAction.Restart -> StrapRepair()
        }
        if (next == current) return
        // Only accepted steps are restored. Pointer positions are local drawing state.
        savedState["strap_aligned"] = next.aligned
        savedState["strap_stitches"] = next.stitchedPoints
        savedState["strap_knotted"] = next.knotTightened
        mutableUiState.value = StrapGameUiState(next)
    }
}
