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
import ru.nksk.lctapp.feature.tasks.ui.DeedsScreen
import ru.nksk.lctapp.feature.tasks.ui.MemoryGameScreen
import ru.nksk.lctapp.feature.tasks.ui.MemoryGameViewModel
import ru.nksk.lctapp.feature.tasks.ui.PriceQuizScreen
import ru.nksk.lctapp.feature.tasks.ui.PriceQuizViewModel
import ru.nksk.lctapp.feature.tasks.ui.TargetStopScreen
import ru.nksk.lctapp.feature.tasks.ui.TargetStopViewModel
import ru.nksk.lctapp.feature.tasks.ui.*

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

/** Test entries for the financial mini-games (FINMINI-D-002); temporary until the user approves them. */
@Serializable @SerialName("tasks_fin_bargain") data object FinBargain : NavKey
@Serializable @SerialName("tasks_fin_need_want") data object FinNeedWant : NavKey
@Serializable @SerialName("tasks_fin_discount") data object FinDiscount : NavKey
@Serializable @SerialName("tasks_fin_balance") data object FinBalance : NavKey
@Serializable @SerialName("tasks_fin_loan") data object FinLoan : NavKey
@Serializable @SerialName("tasks_fin_packing") data object FinPacking : NavKey

val financialDemoEntries = listOf(FinBargain, FinNeedWant, FinDiscount,
    FinBalance, FinLoan, FinPacking)

fun EntryProviderScope<NavKey>.tasksEntry(
    onTraining: (Tasks) -> Unit,
    onBack: (NavKey) -> Unit,
    onEvent: (Tasks) -> Unit,
    onGame: (Tasks, String) -> Unit,
    onFinGame: (Tasks, Int) -> Unit = { _, _ -> },
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
            onDismissMessage = viewModel::dismissMessage,
            onCurrentEvent = dropUnlessResumed { onEvent(source) },
            onTraining = dropUnlessResumed { onTraining(source) },
            onFinGame = { index ->
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onFinGame(source, index)
            },
            onExit = dropUnlessResumed { onBack(source) },
        )
    }
    // Kept only for back stacks saved before demo entry points were removed from Tasks.
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
    // Test boards for the financial mini-games (FINMINI-D-002); demo coins never change the save.
    entry<FinBargain> { source ->
        val viewModel = hiltViewModel<BargainViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BargainGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<FinNeedWant> { source ->
        val viewModel = hiltViewModel<NeedWantViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        NeedWantGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<FinDiscount> { source ->
        val viewModel = hiltViewModel<DiscountViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        DiscountGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<FinBalance> { source ->
        val viewModel = hiltViewModel<BalanceViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BalanceGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<FinLoan> { source ->
        val viewModel = hiltViewModel<LoanViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LoanGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
    entry<FinPacking> { source ->
        val viewModel = hiltViewModel<PackingViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PackingGameScreen(state, viewModel::onAction, dropUnlessResumed { onBack(source) })
    }
}
