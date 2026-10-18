package ru.nksk.lctapp.feature.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.survey.SurveyInvitation
import ru.nksk.lctapp.domain.survey.SurveyRepository

internal enum class SurveyError { READ, SAVE, OPEN }

internal data class SurveyUiState(
    val completed: Boolean? = null,
    val busy: Boolean = false,
    val error: SurveyError? = null,
    val invitation: SurveyInvitation? = null,
) {
    val ready: Boolean get() = completed != null && !busy && error != SurveyError.READ
}

@HiltViewModel
internal class SurveyViewModel @Inject constructor(private val repository: SurveyRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SurveyUiState())
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null
    private var invitationRead: Job? = null

    init { retryRead() }

    fun retryRead() {
        if (uiState.value.busy) return
        observation?.cancel()
        mutableState.value = uiState.value.copy(error = null)
        observation = viewModelScope.launch {
            try {
                repository.observeCompleted().collect { completed ->
                    mutableState.value = uiState.value.copy(completed = completed, error = null,
                        invitation = uiState.value.invitation.takeUnless { completed })
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = uiState.value.copy(error = SurveyError.READ, invitation = null)
            }
        }
    }

    fun prepareInvitation() {
        if (!uiState.value.ready || uiState.value.completed != false ||
            uiState.value.invitation != null || invitationRead?.isActive == true) return
        invitationRead = viewModelScope.launch {
            try {
                val invitation = withContext(Dispatchers.IO) { repository.claimInvitation() }
                if (uiState.value.completed == false) mutableState.value = uiState.value.copy(invitation = invitation)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                // Never replace unreadable preferences with a new reminder history.
                mutableState.value = uiState.value.copy(error = SurveyError.READ, invitation = null)
            }
        }
    }

    fun dismissInvitation() {
        invitationRead?.cancel()
        mutableState.value = uiState.value.copy(invitation = null)
    }

    suspend fun prepareLink(): String? {
        if (!uiState.value.ready) return null
        mutableState.value = uiState.value.copy(busy = true, error = null)
        return try {
            withContext(Dispatchers.IO) { repository.formUrl() }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            linkFailed()
            null
        } finally { mutableState.value = uiState.value.copy(busy = false) }
    }

    fun linkFailed() { mutableState.value = uiState.value.copy(error = SurveyError.OPEN) }

    fun markCompleted() {
        if (!uiState.value.ready || uiState.value.completed != false) return
        mutableState.value = uiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                repository.markCompleted()
                mutableState.value = uiState.value.copy(completed = true, invitation = null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = uiState.value.copy(error = SurveyError.SAVE)
            } finally { mutableState.value = uiState.value.copy(busy = false) }
        }
    }
}
