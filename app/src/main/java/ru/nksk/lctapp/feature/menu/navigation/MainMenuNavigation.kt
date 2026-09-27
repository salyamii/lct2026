package ru.nksk.lctapp.feature.menu.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.menu.ui.MainMenuAction
import ru.nksk.lctapp.feature.menu.ui.MainMenuContent
import ru.nksk.lctapp.feature.menu.ui.MainMenuLoadState
import ru.nksk.lctapp.feature.menu.ui.MainMenuViewModel

@Serializable
@SerialName("main_menu")
data object MainMenu : NavKey

fun EntryProviderScope<NavKey>.mainMenuEntry(
    settingsButton: (@Composable () -> Unit)? = null,
    onAction: (MainMenu, MainMenuAction) -> Unit,
) {
    entry<MainMenu> { source ->
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val viewModel = hiltViewModel<MainMenuViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state::class) {
            when (val current = state) {
                MainMenuLoadState.Loading -> Log.d("MainMenu", "Loading saved game")
                is MainMenuLoadState.Ready -> Log.d("MainMenu", "Rendering main menu")
                is MainMenuLoadState.Error -> Log.e("MainMenu", "Failed to load saved game", current.cause)
            }
        }
        LaunchedEffect(viewModel, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openDay.collect { onAction(source, MainMenuAction.ContinueDay) }
            }
        }
        LaunchedEffect(viewModel, lifecycle, "finance") {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openFinance.collect { onAction(source, MainMenuAction.Finance) }
            }
        }
        MainMenuContent(
            state = state,
            settingsButton = settingsButton,
            onRetry = viewModel::retry,
            onFreeMeal = dropUnlessResumed { viewModel.feedFree() },
            onDismissMeal = dropUnlessResumed { viewModel.dismissFreeMeal() },
            onAction = { action ->
                // Ignore events from an outgoing entry while a transition is in progress.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    when (action) {
                        MainMenuAction.ContinueDay -> viewModel.continueDay()
                        MainMenuAction.Feed -> viewModel.feed()
                        else -> onAction(source, action)
                    }
                }
            },
        )
    }
}
