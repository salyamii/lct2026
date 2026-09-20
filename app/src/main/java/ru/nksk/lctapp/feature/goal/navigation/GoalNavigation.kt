package ru.nksk.lctapp.feature.goal.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.activity.compose.BackHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.feature.goal.ui.GoalScreen
import ru.nksk.lctapp.feature.goal.ui.GoalViewModel
import ru.nksk.lctapp.feature.goal.ui.GoalAction

@Serializable
@SerialName("goal")
data object Goal : NavKey

fun EntryProviderScope<NavKey>.goalEntry(onBack: (Goal) -> Unit) {
    entry<Goal> { source ->
        val model = hiltViewModel<GoalViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        BackHandler(enabled = !state.loading && !state.showList) { model.onAction(GoalAction.ShowList) }
        GoalScreen(
            state = state,
            onAction = model::onAction,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
