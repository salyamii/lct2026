package ru.nksk.lctapp.feature.menu.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.menu.ui.MainMenuAction
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.feature.menu.ui.MainMenuContent
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.feature.menu.ui.MainMenuViewModel

@Serializable
@SerialName("main_menu")
data object MainMenu : NavKey

fun EntryProviderScope<NavKey>.mainMenuEntry(
    gameRepository: GameRepository,
    initialGameState: GameState,
    onAction: (MainMenu, MainMenuAction) -> Unit,
) {
    entry<MainMenu> { source ->
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val viewModel = viewModel { MainMenuViewModel(gameRepository, initialGameState) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MainMenuContent(
            state = state,
            onRetry = viewModel::retry,
            onAction = { action ->
                // Ignore events from an outgoing entry while a transition is in progress.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onAction(source, action)
                }
            },
        )
    }
}
