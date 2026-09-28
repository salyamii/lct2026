package ru.nksk.lctapp.feature.parents.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Host calls this inside the PIN gate, once for the whole parent navigation tree. */
@Composable
fun ParentReportRefreshEffect() {
    val model: ParentReportViewModel = hiltViewModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refreshAssessments() }
    }
}

@Composable
private fun rememberParentRefreshAction(model: ParentReportViewModel): () -> Unit {
    val scope = rememberCoroutineScope()
    var request by remember(model) { mutableStateOf<Job?>(null) }
    LifecycleResumeEffect(model) {
        onPauseOrDispose {
            request?.cancel()
            request = null
        }
    }
    return dropUnlessResumed {
        if (request?.isActive != true) request = scope.launch { model.refreshAssessments() }
    }
}

@Composable
fun ParentReportEntry(
    onOpenTopic: (String) -> Unit,
    onOpenQuests: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: ParentReportViewModel = hiltViewModel()
    val onRefresh = rememberParentRefreshAction(model)
    val state by model.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    ParentReportScreen(
        state = state,
        onRetry = model::retry,
        onRefresh = onRefresh,
        onOpenTopic = onOpenTopic,
        onOpenQuests = dropUnlessResumed { onOpenQuests() },
        onClose = dropUnlessResumed { onClose() },
        modifier = modifier,
    )
}

@Composable
fun ParentTopicEntry(skillId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val model: ParentReportViewModel = hiltViewModel()
    val onRefresh = rememberParentRefreshAction(model)
    val state by model.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    ParentTopicScreen(
        skillId = skillId,
        state = state,
        onRetry = model::retry,
        onRefresh = onRefresh,
        onBack = dropUnlessResumed { onBack() },
        modifier = modifier,
    )
}
