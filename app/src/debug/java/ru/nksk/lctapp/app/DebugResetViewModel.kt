package ru.nksk.lctapp.app

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.nksk.lctapp.data.game.DebugGameResetRepository

internal enum class DebugResetState { Resetting, Failed, Complete }

@HiltViewModel
internal class DebugResetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DebugGameResetRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DebugResetState.Resetting)
    val uiState = state.asStateFlow()
    private var running = false

    init { reset() }

    fun reset() {
        if (running || state.value == DebugResetState.Complete) return
        running = true
        state.value = DebugResetState.Resetting
        viewModelScope.launch {
            try {
                // The helper runs in :debug_reset. Stop all old ViewModels/writers before deletion.
                val manager = context.getSystemService(ActivityManager::class.java)
                fun mainProcesses() = manager.runningAppProcesses.orEmpty().filter {
                    it.uid == Process.myUid() && it.processName == context.applicationInfo.processName &&
                        it.pid != Process.myPid()
                }
                mainProcesses().forEach { Process.killProcess(it.pid) }
                check(withTimeoutOrNull(5_000) {
                    while (mainProcesses().isNotEmpty()) delay(50)
                    true
                } == true) { "Main process did not stop" }
                repository.reset()
                state.value = DebugResetState.Complete
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = DebugResetState.Failed
            } finally {
                running = false
            }
        }
    }
}
