package ru.nksk.lctapp.feature.tasks.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.repeatOnLifecycle
import ru.nksk.lctapp.feature.tasks.ui.DeedsViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.tasks.ui.DeedsAction
import ru.nksk.lctapp.feature.tasks.ui.DeedsScreen
import ru.nksk.lctapp.feature.tasks.ui.DifferencesGameScreen
import ru.nksk.lctapp.feature.tasks.ui.DifferencesGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.LightsGameScreen
import ru.nksk.lctapp.feature.tasks.ui.LightsGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.MemoryGameScreen
import ru.nksk.lctapp.feature.tasks.ui.MemoryGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.PipesGameScreen
import ru.nksk.lctapp.feature.tasks.ui.PipesGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.PriceQuizScreen
import ru.nksk.lctapp.feature.tasks.ui.PriceQuizViewModel
import ru.nksk.lctapp.feature.tasks.ui.SequenceGameScreen
import ru.nksk.lctapp.feature.tasks.ui.SequenceGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.SlidingGameScreen
import ru.nksk.lctapp.feature.tasks.ui.SlidingGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.SortingGameScreen
import ru.nksk.lctapp.feature.tasks.ui.SortingGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.StackingGameScreen
import ru.nksk.lctapp.feature.tasks.ui.StackingGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.TargetStopScreen
import ru.nksk.lctapp.feature.tasks.ui.TargetStopViewModel

@Serializable
@SerialName("tasks")
data object Tasks : NavKey

@Serializable
@SerialName("tasks_star_plates")
data object StarPlates : NavKey

@Serializable
@SerialName("tasks_price_check")
data object PriceCheck : NavKey

@Serializable
@SerialName("tasks_telescope")
data object Telescope : NavKey

@Serializable
@SerialName("tasks_lights")
data object LightsGame : NavKey

@Serializable
@SerialName("tasks_sequence")
data object SequenceGame : NavKey

@Serializable
@SerialName("tasks_sliding")
data object SlidingGame : NavKey

@Serializable
@SerialName("tasks_pipes")
data object PipesGame : NavKey

@Serializable
@SerialName("tasks_sorting")
data object SortingGame : NavKey

@Serializable
@SerialName("tasks_differences")
data object DifferencesGame : NavKey

@Serializable
@SerialName("tasks_stacking")
data object StackingGame : NavKey

fun EntryProviderScope<NavKey>.tasksEntry(
    onOpen: (Tasks, DeedsAction) -> Unit,
    onBack: (NavKey) -> Unit,
    onEvent: (Tasks) -> Unit,
    onGame: (Tasks, String) -> Unit,
) {
    entry<Tasks> { source ->
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val viewModel = hiltViewModel<DeedsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.onScreenResumed()
                try {
                    viewModel.openEvent.collect { onGame(source, it) }
                } finally {
                    viewModel.onScreenHidden()
                }
            }
        }
        DeedsScreen(
            state = state,
            onStart = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.start(it) },
            onFeed = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.feed(it) },
            onRetry = viewModel::retry,
            onCurrentEvent = dropUnlessResumed { onEvent(source) },
            onOpen = { action ->
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onOpen(source, action)
            },
            onExit = dropUnlessResumed { onBack(source) },
        )
    }
    entry<StarPlates> { source ->
        val viewModel = hiltViewModel<MemoryGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MemoryGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<PriceCheck> { source ->
        val viewModel = hiltViewModel<PriceQuizViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PriceQuizScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<Telescope> { source ->
        val viewModel = hiltViewModel<TargetStopViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TargetStopScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<LightsGame> { source ->
        val viewModel = hiltViewModel<LightsGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LightsGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<SequenceGame> { source ->
        val viewModel = hiltViewModel<SequenceGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SequenceGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<SlidingGame> { source ->
        val viewModel = hiltViewModel<SlidingGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SlidingGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<PipesGame> { source ->
        val viewModel = hiltViewModel<PipesGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PipesGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<SortingGame> { source ->
        val viewModel = hiltViewModel<SortingGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SortingGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<DifferencesGame> { source ->
        val viewModel = hiltViewModel<DifferencesGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        DifferencesGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<StackingGame> { source ->
        val viewModel = hiltViewModel<StackingGameViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        StackingGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
}
