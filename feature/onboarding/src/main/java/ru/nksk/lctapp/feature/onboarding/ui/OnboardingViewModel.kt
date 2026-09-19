package ru.nksk.lctapp.feature.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingUiState(
    val foxSelected: Boolean = false,
    val noticeVisible: Boolean = false,
    val noticeId: Long = 0,
    val selectionRequired: Boolean = false,
)

sealed interface OnboardingAction {
    data object SelectFox : OnboardingAction
    data object UnavailableCharacter : OnboardingAction
    data class DismissNotice(val id: Long) : OnboardingAction
    data object RequireSelection : OnboardingAction
    data object DismissSelectionDialog : OnboardingAction
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    private val state = MutableStateFlow(OnboardingUiState(foxSelected = saved[SelectedKey] ?: false))
    val uiState = state.asStateFlow()

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.SelectFox -> {
                saved[SelectedKey] = true
                state.update { it.copy(foxSelected = true, noticeVisible = false, selectionRequired = false) }
            }
            OnboardingAction.UnavailableCharacter -> state.update {
                it.copy(noticeVisible = true, noticeId = it.noticeId + 1)
            }
            is OnboardingAction.DismissNotice -> state.update {
                if (it.noticeId == action.id) it.copy(noticeVisible = false) else it
            }
            OnboardingAction.RequireSelection -> state.update {
                if (!it.foxSelected) it.copy(selectionRequired = true) else it
            }
            OnboardingAction.DismissSelectionDialog -> state.update { it.copy(selectionRequired = false) }
        }
    }

    private companion object { const val SelectedKey = "onboarding.foxSelected" }
}
