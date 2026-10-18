package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.WheelRepair

data class WheelGameUiState(val game: WheelRepair = WheelRepair())

sealed interface WheelGameAction {
    data class Rotate(val degrees: Float) : WheelGameAction
    data class Select(val section: Int) : WheelGameAction
    data object Secure : WheelGameAction
    data object Restart : WheelGameAction
}

@HiltViewModel
class WheelGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WheelGameUiState(WheelRepair.restore(
        savedState["wheel_fixed"] ?: 0, savedState["wheel_selected"] ?: 0,
        savedState["wheel_angle"] ?: 90f,
    )))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: WheelGameAction) {
        val game = mutableUiState.value.game
        val next = when (action) {
            is WheelGameAction.Rotate -> game.rotateBy(action.degrees)
            is WheelGameAction.Select -> game.select(action.section)
            WheelGameAction.Secure -> game.secure()
            WheelGameAction.Restart -> WheelRepair()
        }
        if (next == game) return
        savedState["wheel_fixed"] = next.fixedMask
        savedState["wheel_selected"] = next.selectedSection
        savedState["wheel_angle"] = next.angle
        mutableUiState.value = WheelGameUiState(next)
    }
}
