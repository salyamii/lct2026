package ru.nksk.lctapp.app.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AppUpdateUiState(val readyToInstall: Boolean = false, val installing: Boolean = false)

@HiltViewModel
internal class AppUpdateViewModel @Inject constructor(private val client: AppUpdateClient) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUpdateUiState())
    val uiState = mutableState.asStateFlow()
    private var downloadOffered = false
    private var deferred = false

    /** The entry cancels this scope on pause: no late check may open store UI in the background. */
    suspend fun whileResumed(): Unit = coroutineScope {
        deferred = false
        mutableState.update { it.copy(readyToInstall = false) }
        var attemptingDownload = false
        launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.catch { /* Store observation is optional; the next resume checks again. */ }
                .collect { status ->
                    if (status == UpdateStatus.DOWNLOADED) offerInstallation()
                    else mutableState.update { it.copy(readyToInstall = false) }
                }
        }
        try {
            when (client.check()) {
                UpdateStatus.DOWNLOADED -> offerInstallation()
                UpdateStatus.AVAILABLE -> if (!downloadOffered) {
                    attemptingDownload = true
                    if (client.download { downloadOffered = true }) offerInstallation()
                }
                else -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The platform adapter logs failures; no error dialog blocks the game.
            if (attemptingDownload) downloadOffered = false
        }
        awaitCancellation()
    }

    fun later() {
        deferred = true
        mutableState.update { it.copy(readyToInstall = false) }
    }

    fun install() {
        if (!uiState.value.readyToInstall || uiState.value.installing) return
        mutableState.value = AppUpdateUiState(installing = true)
        viewModelScope.launch {
            try {
                client.install()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(readyToInstall = true) }
            } finally {
                mutableState.update { it.copy(installing = false) }
            }
        }
    }

    private fun offerInstallation() {
        if (!deferred && !uiState.value.installing) {
            mutableState.update { it.copy(readyToInstall = true) }
        }
    }
}
