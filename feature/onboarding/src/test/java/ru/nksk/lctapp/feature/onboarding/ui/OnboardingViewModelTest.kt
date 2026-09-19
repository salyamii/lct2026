package ru.nksk.lctapp.feature.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class OnboardingViewModelTest {
    @Test fun startWithoutSelectionShowsDismissibleDialog() {
        val model = OnboardingViewModel(SavedStateHandle())
        model.onAction(OnboardingAction.RequireSelection)
        assertTrue(model.uiState.value.selectionRequired)
        assertFalse(model.uiState.value.foxSelected)
        model.onAction(OnboardingAction.DismissSelectionDialog)
        assertFalse(model.uiState.value.selectionRequired)
        model.onAction(OnboardingAction.RequireSelection)
        model.onAction(OnboardingAction.SelectFox)
        model.onAction(OnboardingAction.RequireSelection)
        assertFalse(model.uiState.value.selectionRequired)
        assertTrue(model.uiState.value.foxSelected)
    }

    @Test fun unsupportedCharacterDoesNotEnableStartOrClearFoxSelection() {
        val model = OnboardingViewModel(SavedStateHandle())
        assertFalse(model.uiState.value.foxSelected)
        model.onAction(OnboardingAction.UnavailableCharacter)
        assertFalse(model.uiState.value.foxSelected)
        assertTrue(model.uiState.value.noticeVisible)
        model.onAction(OnboardingAction.SelectFox)
        assertTrue(model.uiState.value.foxSelected)
        model.onAction(OnboardingAction.UnavailableCharacter)
        assertTrue(model.uiState.value.foxSelected)
    }

    @Test fun restoringSelectionDoesNotRestoreOldNotice() {
        val saved = SavedStateHandle()
        val first = OnboardingViewModel(saved)
        first.onAction(OnboardingAction.SelectFox)
        first.onAction(OnboardingAction.UnavailableCharacter)
        val restored = OnboardingViewModel(saved)
        assertTrue(restored.uiState.value.foxSelected)
        assertFalse(restored.uiState.value.noticeVisible)
    }

    @Test fun oldTimeoutCannotDismissNewNotice() {
        val model = OnboardingViewModel(SavedStateHandle())
        model.onAction(OnboardingAction.UnavailableCharacter)
        val old = model.uiState.value.noticeId
        model.onAction(OnboardingAction.UnavailableCharacter)
        model.onAction(OnboardingAction.DismissNotice(old))
        assertTrue(model.uiState.value.noticeVisible)
        model.onAction(OnboardingAction.DismissNotice(model.uiState.value.noticeId))
        assertFalse(model.uiState.value.noticeVisible)
    }
}
