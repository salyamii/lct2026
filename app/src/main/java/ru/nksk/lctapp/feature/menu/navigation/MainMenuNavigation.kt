package ru.nksk.lctapp.feature.menu.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.feature.menu.ui.MainMenuAction
import ru.nksk.lctapp.feature.menu.ui.MainMenuDemoState
import ru.nksk.lctapp.feature.menu.ui.MainMenuScreen
import ru.nksk.lctapp.ui.menu.MainMenu as SavedMainMenu

typealias MainMenu = SavedMainMenu

fun EntryProviderScope<NavKey>.mainMenuEntry(onAction: (MainMenu, MainMenuAction) -> Unit) {
    entry<MainMenu> { source ->
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        MainMenuScreen(
            state = MainMenuDemoState,
            onAction = { action ->
                // Ignore events from an outgoing entry while a transition is in progress.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onAction(source, action)
                }
            },
        )
    }
}
