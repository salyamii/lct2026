package ru.nksk.lctapp.feature.economy.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.feature.economy.ui.EconomyAction
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import ru.nksk.lctapp.feature.economy.ui.EconomyScreen
import ru.nksk.lctapp.feature.economy.ui.EconomyViewModel
import ru.nksk.lctapp.feature.economy.ui.SavingsScreen
import ru.nksk.lctapp.feature.economy.ui.savingsBackAction

@Serializable
@SerialName("coins") // Preserve the saved route ID of the former placeholder.
data object Economy : NavKey

@Serializable
@SerialName("savings")
data object Savings : NavKey

fun EntryProviderScope<NavKey>.economyEntry(onBack: (Economy) -> Unit, onConfirmed: (Economy) -> Unit,
    onOpenSavings: (Economy) -> Unit = {}) {
    entry<Economy> { source ->
        val model = hiltViewModel<EconomyViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var exitAfterSave by remember { mutableStateOf(false) }
        var exitMessage by remember { mutableStateOf<String?>(null) }
        val finishExit: () -> Unit = {
            val economy = state.economy
            when {
                state.error != null -> Unit
                economy == null || economy.planning?.stage == BudgetPlanningStage.RECEIPT -> onBack(source)
                economy.planning == null -> onBack(source)
                EconomyOperations.allocationRemaining(economy) > 0 -> exitMessage = "Распредели оставшиеся ${EconomyOperations.allocationRemaining(economy)} монет, чтобы продолжить."
                economy.displayPlan.needs < EconomyOperations.minimumNeeds(economy, state.knownNeeds) ->
                    exitMessage = "Оставь ещё ${EconomyOperations.minimumNeeds(economy, state.knownNeeds) - economy.displayPlan.needs} монет на необходимое, чтобы на всё хватило."
                else -> model.onAction(EconomyAction.Confirm)
            }
        }
        val back: () -> Unit = {
            if (state.budgetConfirmation == null) {
                if (state.saving) exitAfterSave = true else finishExit()
            }
        }
        BackHandler { back() }
        LaunchedEffect(state.saving, state.error, exitAfterSave) {
            if (exitAfterSave && !state.saving) {
                exitAfterSave = false
                if (state.error == null && state.budgetConfirmation == null) finishExit()
            }
        }
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.completed.collect { onConfirmed(source) }
            }
        }
        exitMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { exitMessage = null },
                title = { Text("Заверши распределение") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { exitMessage = null }) { Text("Распределить") } },
            )
        }
        EconomyScreen(state, onAction = {
            if (it is EconomyAction.ContextPresented || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it)
        }, onBack = dropUnlessResumed { back() },
            onOpenSavings = dropUnlessResumed { onOpenSavings(source) })
    }
}

fun EntryProviderScope<NavKey>.savingsEntry(onBack: (Savings) -> Unit,
    onOpenGoal: (Savings) -> Unit, onOpenBudget: (Savings) -> Unit) {
    entry<Savings> { source ->
        val model = hiltViewModel<EconomyViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val back: () -> Unit = {
            if (!state.saving) {
                val cancel = savingsBackAction(state)
                if (cancel != null) model.onAction(cancel) else onBack(source)
            }
        }
        BackHandler { back() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        SavingsScreen(state, onAction = { action ->
            if (action is EconomyAction.TransferContextPresented || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(action)
        }, onBack = dropUnlessResumed { back() },
            onOpenGoal = dropUnlessResumed {
                if (!state.saving) { model.onAction(EconomyAction.OpenSavingsHub); onOpenGoal(source) }
            },
            onOpenBudget = dropUnlessResumed {
                if (!state.saving) { model.onAction(EconomyAction.OpenSavingsHub); onOpenBudget(source) }
            })
    }
}
