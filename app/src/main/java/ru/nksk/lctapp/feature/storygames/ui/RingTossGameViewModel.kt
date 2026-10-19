package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.RingToss

sealed interface RingTossAction {
    data class Release(val dx: Float, val dy: Float) : RingTossAction
    data class AimAtPeg(val index: Int) : RingTossAction
}

@HiltViewModel
class RingTossGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(RingToss.restore(savedState["ring_toss_board"]))
    val uiState = mutableState.asStateFlow()

    fun onAction(action: RingTossAction) {
        val current = mutableState.value
        val next = when (action) {
            is RingTossAction.Release -> current.pull(action.dx, action.dy)
            is RingTossAction.AimAtPeg -> current.throwAtPeg(action.index)
        }
        if (next == current) return
        savedState["ring_toss_board"] = next.save()
        mutableState.value = next
    }
}
