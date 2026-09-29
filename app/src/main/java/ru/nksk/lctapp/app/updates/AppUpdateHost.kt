package ru.nksk.lctapp.app.updates

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import ru.nksk.lctapp.R

@Composable
internal fun AppUpdateHost() {
    val model: AppUpdateViewModel = hiltViewModel()
    val owner = LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsState()
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.whileResumed() }
    }
    if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && state.readyToInstall) {
        UpdateReadyDialog(onInstall = model::install, onLater = model::later)
    }
}

@Composable
private fun UpdateReadyDialog(onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.app_update_ready_title)) },
        text = { Text(stringResource(R.string.app_update_ready_message)) },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.app_update_install)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.app_update_later)) } },
    )
}
