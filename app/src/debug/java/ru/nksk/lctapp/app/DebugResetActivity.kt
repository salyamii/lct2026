package ru.nksk.lctapp.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.nksk.lctapp.MainActivity
import ru.nksk.lctapp.feature.debug.ui.DebugResetScreen

/** A separate debug-only process survives termination of the main application process. */
@AndroidEntryPoint
class DebugResetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { /* Do not restore a stale game task during reset. */ }
            val viewModel: DebugResetViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DebugResetScreen(failed = state == DebugResetState.Failed, onRetry = viewModel::reset)
            LaunchedEffect(viewModel) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.uiState.collect { current ->
                        if (current == DebugResetState.Complete) {
                            // A new task deliberately discards saved navigation and Compose state.
                            startActivity(Intent.makeRestartActivityTask(
                                ComponentName(this@DebugResetActivity, MainActivity::class.java),
                            ))
                            finishAndRemoveTask()
                            Process.killProcess(Process.myPid())
                        }
                    }
                }
            }
        }
    }
}
