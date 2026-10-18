package ru.nksk.lctapp.feature.survey

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** App composition supplies the same model to Settings and the end-of-day slot. */
@Composable
internal fun SurveySettingsEntry(model: SurveyViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SurveyCard(state, invitation = false, onOpen = surveyOpenAction(model),
        onCompleted = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.markCompleted() },
        onRetry = model::retryRead)
}

@Composable
internal fun SurveyInvitationEntry(model: SurveyViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model) { onDispose { model.dismissInvitation() } }
    LaunchedEffect(model, lifecycle, state.completed) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.prepareInvitation()
            awaitCancellation()
        }
    }
    if (state.invitation != null && state.completed == false) {
        SurveyCard(state, invitation = true, onOpen = surveyOpenAction(model),
            onCompleted = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.markCompleted() },
            onLater = model::dismissInvitation)
    }
}

@Composable
private fun surveyOpenAction(model: SurveyViewModel): () -> Unit {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    return {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) scope.launch {
            val url = model.prepareLink()
            if (url != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    })
                } catch (_: RuntimeException) { model.linkFailed() }
            }
        }
    }
}
