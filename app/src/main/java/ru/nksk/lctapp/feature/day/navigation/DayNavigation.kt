package ru.nksk.lctapp.feature.day.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import ru.nksk.lctapp.feature.day.ui.DayScreen
import ru.nksk.lctapp.feature.day.ui.DayViewModel

@Serializable
@SerialName("day")
data object Day : NavKey

fun EntryProviderScope<NavKey>.dayEntry(onBack: (Day) -> Unit, onFinished: (Day, String?) -> Unit,
    onGame: (Day, String) -> Unit) {
    entry<Day> { source ->
        val viewModel = hiltViewModel<DayViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(viewModel, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.exit.collect { onFinished(source, it) }
            }
        }
        LaunchedEffect(viewModel, lifecycle, "game") {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openGame.collect { onGame(source, it) }
            }
        }
        DayScreen(
            state = state,
            onAction = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onAction(it) },
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
