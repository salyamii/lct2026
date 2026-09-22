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

@Serializable
@SerialName("coins") // Preserve the saved route ID of the former placeholder.
data object Economy : NavKey

fun EntryProviderScope<NavKey>.economyEntry(onBack: (Economy) -> Unit, onConfirmed: (Economy) -> Unit) {
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
                economy.unallocated > 0 -> exitMessage = "Распредели оставшиеся ${economy.unallocated} монет, чтобы продолжить."
                economy.plan.needs < EconomyOperations.minimumNeeds(economy) ->
                    exitMessage = "Добавь в «Нужно» ещё ${EconomyOperations.minimumNeeds(economy) - economy.plan.needs} монет, чтобы завершить бюджет."
                else -> model.onAction(EconomyAction.Confirm)
            }
        }
        val back: () -> Unit = { if (state.saving) exitAfterSave = true else finishExit() }
        BackHandler { back() }
        LaunchedEffect(state.saving, state.error, exitAfterSave) {
            if (exitAfterSave && !state.saving) {
                exitAfterSave = false
                if (state.error == null) finishExit()
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
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it)
        }, onBack = dropUnlessResumed { back() })
    }
}
