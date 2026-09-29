package ru.nksk.lctapp.feature.menutour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.tutorial.MenuTourPreferences

internal data class MenuTourUiState(
    val step: Int? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    val dismissedForSession: Boolean = false,
)

@HiltViewModel
internal class MenuTourViewModel @Inject constructor(private val preferences: MenuTourPreferences) : ViewModel() {
    private val mutableState = MutableStateFlow(MenuTourUiState())
    val uiState = mutableState.asStateFlow()
    private var retryOperation: (suspend () -> Int)? = null

    fun prepare(newPlayer: Boolean) {
        if (uiState.value.step != null || uiState.value.busy || uiState.value.error || uiState.value.dismissedForSession) return
        perform {
            val saved = preferences.readStep()
            initialTourStep(saved, newPlayer).also { if (saved == null) preferences.saveStep(it) }
        }
    }

    fun restart() {
        if (uiState.value.busy) return
        mutableState.value = MenuTourUiState()
        save(0)
    }

    fun next() { uiState.value.step?.let { save((it + 1).coerceAtMost(TourFinished)) } }
    fun previous() { uiState.value.step?.takeIf { it in 1 until MainTourStepCount }?.let { save(it - 1) } }
    fun skip() = save(TourFinished)
    fun retry() { retryOperation?.let(::perform) }
    fun dismissError() {
        if (!uiState.value.busy) mutableState.value = uiState.value.copy(error = false, dismissedForSession = true)
    }

    private fun save(step: Int) = perform { preferences.saveStep(step); step }

    private fun perform(operation: suspend () -> Int) {
        if (uiState.value.busy) return
        retryOperation = operation
        mutableState.value = uiState.value.copy(busy = true, error = false)
        viewModelScope.launch {
            try {
                mutableState.value = MenuTourUiState(step = operation())
                retryOperation = null
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = uiState.value.copy(busy = false, error = true)
            }
        }
    }
}
