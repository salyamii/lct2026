package ru.nksk.lctapp.feature.goal.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.activity.compose.BackHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.feature.goal.ui.GoalScreen
import ru.nksk.lctapp.feature.goal.ui.GoalViewModel
import ru.nksk.lctapp.feature.goal.ui.GoalAction
import ru.nksk.lctapp.feature.goal.ui.GoalContinuationDestination

@Serializable
@SerialName("goal")
data object Goal : NavKey

fun EntryProviderScope<NavKey>.goalEntry(onBack: (Goal) -> Unit, onOpenSavings: (Goal) -> Unit,
    onContinueDay: (Goal) -> Unit, onBudget: (Goal) -> Unit, onTraining: (Goal) -> Unit) {
    entry<Goal> { source ->
        val model = hiltViewModel<GoalViewModel>()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val state by model.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.continueNavigation.collect { destination ->
                    when (destination) {
                        GoalContinuationDestination.DAY -> onContinueDay(source)
                        GoalContinuationDestination.BUDGET -> onBudget(source)
                        GoalContinuationDestination.TRAINING -> onTraining(source)
                    }
                }
            }
        }
        BackHandler(enabled = !state.loading &&
            (state.purchaseResult != null || (!state.showList && state.returnToList))) {
            model.onAction(if (state.purchaseResult != null) GoalAction.DismissPurchaseResult else GoalAction.ShowList)
        }
        GoalScreen(
            state = state,
            onAction = { action ->
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(action)
            },
            onBack = dropUnlessResumed { onBack(source) },
            onOpenSavings = dropUnlessResumed { onOpenSavings(source) },
        )
    }
}
