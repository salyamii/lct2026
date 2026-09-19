package ru.nksk.lctapp.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingAction
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingArtwork
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingScreen
import ru.nksk.lctapp.feature.onboarding.ui.OnboardingViewModel

/** Startup gate outside the game back stack; successful entry never leaves a Back destination. */
@Composable
fun OnboardingEntry(
    artwork: OnboardingArtwork,
    saving: Boolean,
    saveFailed: Boolean,
    onStartAdventure: () -> Unit,
) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(state.noticeId, state.noticeVisible, accessibility) {
        if (state.noticeVisible) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(4000, containsText = true) ?: 4000)
            viewModel.onAction(OnboardingAction.DismissNotice(state.noticeId))
        }
    }
    OnboardingScreen(state, artwork, saving, saveFailed, viewModel::onAction, onStart = {
        if (!saving) {
            if (viewModel.uiState.value.foxSelected) onStartAdventure()
            else viewModel.onAction(OnboardingAction.RequireSelection)
        }
    })
}
