package ru.nksk.lctapp.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.engine.GameSession

internal sealed interface AppStartupState {
    data object Loading : AppStartupState
    data class Choose(val saving: Boolean = false, val failed: Boolean = false) : AppStartupState
    data object Ready : AppStartupState
    data object Error : AppStartupState
}

/** App owns the launch boundary; the feature neither initializes nor navigates the game. */
@HiltViewModel
internal class AppStartupViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val uiState = state.asStateFlow()
    private var work: Job? = null

    init { retry() }

    fun retry() {
        if (work?.isActive == true || state.value == AppStartupState.Ready) return
        state.value = AppStartupState.Loading
        work = viewModelScope.launch {
            try {
                state.value = if (session.read() == null) AppStartupState.Choose() else AppStartupState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = AppStartupState.Error
            }
        }
    }

    fun startAdventure() {
        val choice = state.value as? AppStartupState.Choose ?: return
        if (choice.saving || work?.isActive == true) return
        state.value = AppStartupState.Choose(saving = true)
        work = viewModelScope.launch {
            try {
                // Existing aggregate initialization is atomic and never replaces a concurrent save.
                // This installs content and creates a snapshot, but does not start a day/event.
                session.prepare()
                state.value = AppStartupState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = AppStartupState.Choose(failed = true)
            }
        }
    }
}
