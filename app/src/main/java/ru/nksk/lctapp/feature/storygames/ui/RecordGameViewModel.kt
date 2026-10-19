package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudy
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudyGame
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudyKind
import ru.nksk.lctapp.domain.minigame.chapterone.SignalLight

data class RecordGameUiState(val game: RecordStudy)

sealed interface RecordGameAction {
    data class Visit(val point: Int) : RecordGameAction
    data class SelectLens(val light: SignalLight) : RecordGameAction
    data class PlaceLens(val socket: SignalLight) : RecordGameAction
    data class Stamp(val light: SignalLight) : RecordGameAction
}

@HiltViewModel
class RecordGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val restoredKind = RecordStudyKind.entries.firstOrNull { it.name == savedState.get<String>("record.kind") }
        ?: RecordStudyKind.CARGO_JOURNAL
    private val mutableState = MutableStateFlow(RecordGameUiState(RecordStudyGame.restore(
        restoredKind, savedState.get<IntArray>("record.steps")?.toList().orEmpty(), savedState["record.lens"],
    )))
    val uiState = mutableState.asStateFlow()

    fun prepare(kind: RecordStudyKind) {
        if (mutableState.value.game.kind != kind) publish(RecordStudy(kind))
    }

    fun onAction(action: RecordGameAction) {
        val game = mutableState.value.game
        val next = when (action) {
            is RecordGameAction.Visit -> RecordStudyGame.visit(game, action.point)
            is RecordGameAction.SelectLens -> RecordStudyGame.selectLens(game, action.light)
            is RecordGameAction.PlaceLens -> RecordStudyGame.placeLens(game, action.socket)
            is RecordGameAction.Stamp -> RecordStudyGame.stamp(game, action.light)
        }
        if (next != game) publish(next)
    }

    private fun publish(game: RecordStudy) {
        savedState["record.kind"] = game.kind.name
        savedState["record.steps"] = game.recorded.toIntArray()
        savedState["record.lens"] = game.selectedLens
        mutableState.value = RecordGameUiState(game)
    }
}
