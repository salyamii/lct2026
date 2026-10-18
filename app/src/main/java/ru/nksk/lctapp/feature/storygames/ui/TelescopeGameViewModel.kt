package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeAssembly
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopePart

data class TelescopeGameUiState(val game: TelescopeAssembly = TelescopeAssembly())

sealed interface TelescopeGameAction {
    data class Select(val part: TelescopePart) : TelescopeGameAction
    data class Install(val part: TelescopePart) : TelescopeGameAction
    data class Focus(val value: Float) : TelescopeGameAction
    data class SelectFastener(val index: Int) : TelescopeGameAction
    data class TurnFastener(val index: Int, val degrees: Float) : TelescopeGameAction
    data class TurnEyepiece(val degrees: Float) : TelescopeGameAction
    data object FinishTurning : TelescopeGameAction
    data object Observe : TelescopeGameAction
    data object Restart : TelescopeGameAction
}

@HiltViewModel
class TelescopeGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TelescopeGameUiState(TelescopeAssembly.restore(
        savedState["telescope_installed"] ?: 0, savedState["telescope_selected"],
        savedState["telescope_focus"] ?: .18f, savedState["telescope_complete"] ?: false,
        firstFastener = savedState["telescope_fastener_first"] ?: 0f,
        secondFastener = savedState["telescope_fastener_second"] ?: 0f,
        selectedFastener = savedState["telescope_fastener_selected"] ?: 0,
        mountSecured = savedState["telescope_mount_secured"] ?: false,
        eyepieceTurn = savedState["telescope_eyepiece_turn"] ?: 0f,
        eyepieceSecured = savedState["telescope_eyepiece_secured"] ?: false,
        version = savedState["telescope_save_version"] ?: 1,
    )))
    val uiState = mutableUiState.asStateFlow()

    fun onAction(action: TelescopeGameAction) {
        val game = mutableUiState.value.game
        val next = when (action) {
            is TelescopeGameAction.Select -> game.select(action.part)
            is TelescopeGameAction.Install -> game.install(action.part)
            is TelescopeGameAction.Focus -> game.adjustFocus(action.value)
            is TelescopeGameAction.SelectFastener -> game.selectFastener(action.index)
            is TelescopeGameAction.TurnFastener -> game.turnFastener(action.index, action.degrees)
            is TelescopeGameAction.TurnEyepiece -> game.turnEyepiece(action.degrees)
            TelescopeGameAction.FinishTurning -> game.finishTurning()
            TelescopeGameAction.Observe -> game.observe()
            TelescopeGameAction.Restart -> TelescopeAssembly()
        }
        if (next == game) return
        savedState["telescope_installed"] = next.installedCount
        savedState["telescope_selected"] = next.selectedPart?.ordinal
        savedState["telescope_focus"] = next.focus
        savedState["telescope_complete"] = next.completed
        savedState["telescope_save_version"] = TelescopeAssembly.SAVE_VERSION
        savedState["telescope_fastener_first"] = next.firstFastener
        savedState["telescope_fastener_second"] = next.secondFastener
        savedState["telescope_fastener_selected"] = next.selectedFastener
        savedState["telescope_mount_secured"] = next.mountSecured
        savedState["telescope_eyepiece_turn"] = next.eyepieceTurn
        savedState["telescope_eyepiece_secured"] = next.eyepieceSecured
        mutableUiState.value = TelescopeGameUiState(next)
    }
}
