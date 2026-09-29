package ru.nksk.lctapp.feature.parents.quests

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class ParentQuestUiState(
    val checkedSteps: List<Boolean> = List(4) { false },
    val completed: Boolean = false,
) {
    val completedSteps: Int get() = checkedSteps.count { it }
    val canComplete: Boolean get() = checkedSteps.all { it } && !completed
}

/** Temporary demo state for this navigation entry; no persistence or reward delivery. */
@HiltViewModel
class ParentQuestViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(ParentQuestUiState())
    val uiState = mutableState.asStateFlow()

    fun setStepChecked(index: Int, checked: Boolean) {
        mutableState.update { state ->
            if (state.completed) state else state.copy(
                checkedSteps = state.checkedSteps.mapIndexed { step, value ->
                    if (step == index) checked else value
                },
            )
        }
    }

    fun complete() {
        mutableState.update { if (it.canComplete) it.copy(completed = true) else it }
    }
}
