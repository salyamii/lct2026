package ru.nksk.lctapp.feature.map.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.map.ui.*

@Serializable
@SerialName("village") // Preserve saved navigation stacks from the placeholder route.
data object GameMap : NavKey

fun EntryProviderScope<NavKey>.mapEntry(onBack: (GameMap) -> Unit, onSelected: (GameMap) -> Unit) {
    entry<GameMap> { source ->
        val viewModel = hiltViewModel<MapViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(viewModel, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect { if (it.finished) onSelected(source) }
            }
        }
        BackHandler(enabled = state.saving) { /* Wait for the transaction before leaving. */ }
        val back = dropUnlessResumed { if (!state.saving) onBack(source) }
        MapContent(state,
            onSelect = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.select(it) },
            onBack = back, onRetry = viewModel::reload, onDismissError = viewModel::dismissError)
    }
}
