package ru.nksk.lctapp.feature.learning.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.awaitCancellation
import ru.nksk.lctapp.feature.learning.ui.ChronoscopeStep
import ru.nksk.lctapp.feature.learning.ui.HistoryScreen
import ru.nksk.lctapp.feature.learning.ui.LearningHistoryViewModel
import ru.nksk.lctapp.feature.learning.ui.ReflectionAction
import ru.nksk.lctapp.feature.learning.ui.ReflectionScreen
import ru.nksk.lctapp.feature.learning.ui.ReflectionViewModel
import ru.nksk.lctapp.feature.learning.ui.TrainingScreen
import ru.nksk.lctapp.feature.learning.ui.TrainingViewModel
import ru.nksk.lctapp.feature.learning.ui.TrainingContinuationDestination

/** Keep the saved history route readable across the split into history and training. */
@Serializable
@SerialName("financial_learning")
data object Learning : NavKey

@Serializable
@SerialName("skill_training")
data object SkillTraining : NavKey

/** Finite catch-up for the current chapter; voluntary training keeps its own entry. */
@Serializable
@SerialName("chapter_practice")
data object ChapterPractice : NavKey

/** Only a viewing filter belongs to the route; no game snapshot or simulation is stored here. */
@Serializable
@SerialName("other_paths")
data class OtherPaths(val day: Int) : NavKey { init { require(day > 0) } }

fun EntryProviderScope<NavKey>.learningEntry(onBack: (NavKey) -> Unit, onOpenBudget: (NavKey) -> Unit = {},
    onContinueStory: (NavKey) -> Unit = onBack, onArchives: (NavKey) -> Unit = {}) {
    entry<Learning> { source ->
        val model = hiltViewModel<LearningHistoryViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.setActive(true)
                try { awaitCancellation() } finally { model.setActive(false) }
            }
        }
        HistoryScreen(state, onRetry = dropUnlessResumed { model.retry() },
            onBack = dropUnlessResumed { onBack(source) }, onArchives = dropUnlessResumed { onArchives(source) })
    }
    entry<SkillTraining> { source ->
        TrainingEntryContent(source, false, onBack, onOpenBudget, onContinueStory)
    }
    entry<ChapterPractice> { source ->
        TrainingEntryContent(source, true, onBack, onOpenBudget, onContinueStory)
    }
    entry<OtherPaths> { source -> ReflectionEntryContent(source, onBack) }
}

@Composable
private fun TrainingEntryContent(source: NavKey, chapterPractice: Boolean, onBack: (NavKey) -> Unit,
    onOpenBudget: (NavKey) -> Unit, onContinueStory: (NavKey) -> Unit) {
    val model = hiltViewModel<TrainingViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, chapterPractice) { if (chapterPractice) model.setChapterPractice() }
    LaunchedEffect(model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.setActive(true)
            try {
                model.openContinuation.collect { destination ->
                    when (destination) {
                        TrainingContinuationDestination.DAY -> onContinueStory(source)
                        TrainingContinuationDestination.BUDGET -> onOpenBudget(source)
                    }
                }
            } finally { model.setActive(false) }
        }
    }
    // Do not let a restored voluntary answer auto-advance before the entry configures its mode.
    val visibleState = if (chapterPractice && !state.chapterPractice)
        state.copy(loading = true, chapterPractice = true, practiceOpen = false) else state
    TrainingScreen(visibleState, onAction = {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it)
    }, onBack = dropUnlessResumed { onBack(source) },
        onOpenBudget = dropUnlessResumed { onOpenBudget(source) },
        onContinueStory = dropUnlessResumed { model.continueStory() })
}

@Composable
private fun ReflectionEntryContent(source: OtherPaths, onBack: (NavKey) -> Unit) {
    val model = hiltViewModel<ReflectionViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    LaunchedEffect(model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.setActive(true)
            try { awaitCancellation() } finally { model.setActive(false) }
        }
    }
    LaunchedEffect(model, state.loading, state.realGame != null, source.day, lifecycleState) {
        if (!state.loading && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) model.openReflection(source.day)
    }
    val back = dropUnlessResumed {
        if (state.chronoscopeStep !in setOf(null, ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS))
            model.onAction(ReflectionAction.ChronoscopeBack)
        else onBack(source)
    }
    BackHandler { back() }
    ReflectionScreen(state, onAction = {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.onAction(it)
    }, onBack = dropUnlessResumed { onBack(source) })
}
