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
    onTraining: (MainMenu) -> Unit,
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
                viewModel.setActive(true)
                try {
                    viewModel.openDay.collect { onAction(source, MainMenuAction.ContinueDay) }
                } finally {
                    viewModel.setActive(false)
                }
            }
        }
        LaunchedEffect(viewModel, lifecycle, "training") {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openTraining.collect { onTraining(source) }
            }
        }
        LaunchedEffect(viewModel, lifecycle, "budget") {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openBudget.collect { onAction(source, MainMenuAction.Coins) }
            }
        }
        MainMenuContent(
            state = state,
            settingsButton = settingsButton,
            onRetry = viewModel::retry,
            onMeal = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.selectMeal(it) },
            onDismissMeal = dropUnlessResumed { viewModel.dismissFreeMeal() },
            onRename = dropUnlessResumed { viewModel.editName() },
            onNameChange = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.changeName(it) },
            onSaveName = dropUnlessResumed { viewModel.saveName() },
            onDismissName = dropUnlessResumed { viewModel.dismissName() },
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
