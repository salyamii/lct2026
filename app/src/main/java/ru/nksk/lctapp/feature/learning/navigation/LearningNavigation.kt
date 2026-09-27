package ru.nksk.lctapp.feature.learning.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.learning.ui.ChronoscopeStep
import ru.nksk.lctapp.feature.learning.ui.LearningAction
import ru.nksk.lctapp.feature.learning.ui.LearningPageMode
import ru.nksk.lctapp.feature.learning.ui.LearningScreen
import ru.nksk.lctapp.feature.learning.ui.LearningViewModel

/** Keep the saved history route readable across the split into history and training. */
@Serializable
@SerialName("financial_learning")
data object Learning : NavKey

@Serializable
@SerialName("skill_training")
data object SkillTraining : NavKey

/** Only a viewing filter belongs to the route; no game snapshot or simulation is stored here. */
@Serializable
@SerialName("other_paths")
data class OtherPaths(val day: Int) : NavKey { init { require(day > 0) } }

fun EntryProviderScope<NavKey>.learningEntry(onBack: (NavKey) -> Unit, onOpenBudget: (NavKey) -> Unit = {}) {
    entry<Learning> { source -> LearningEntryContent(source, LearningPageMode.HISTORY, onBack, onOpenBudget) }
    entry<SkillTraining> { source -> LearningEntryContent(source, LearningPageMode.TRAINING, onBack, onOpenBudget) }
    entry<OtherPaths> { source -> LearningEntryContent(source, LearningPageMode.REFLECTION, onBack, onOpenBudget, source.day) }
}

@Composable
private fun LearningEntryContent(source: NavKey, mode: LearningPageMode, onBack: (NavKey) -> Unit,
    onOpenBudget: (NavKey) -> Unit, reflectionDay: Int? = null) {
    val model = hiltViewModel<LearningViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, state.loading, state.realGame != null, reflectionDay) {
        if (reflectionDay != null && !state.loading) model.openReflection(reflectionDay)
    }
    val back = dropUnlessResumed {
        if (mode == LearningPageMode.REFLECTION && state.chronoscopeStep !in setOf(null, ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS))
            model.onAction(LearningAction.ChronoscopeBack)
        else onBack(source)
    }
    BackHandler(enabled = mode == LearningPageMode.REFLECTION) { back() }
    LearningScreen(state, onAction = {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it)
    }, onBack = dropUnlessResumed { onBack(source) },
        onOpenBudget = dropUnlessResumed { onOpenBudget(source) }, mode = mode)
}
