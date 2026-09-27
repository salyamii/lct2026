package ru.nksk.lctapp.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository

internal enum class ParentCodeStatus { NONE, LOADING, READY, ERROR }
internal enum class ProfileRegistrationStatus { NONE, LOADING, REGISTERED, ERROR }
internal data class SettingsUiState(
    val loading: Boolean = true,
    val profileId: String? = null,
    val backendConfigured: Boolean = false,
    val profileError: Boolean = false,
    val codeStatus: ParentCodeStatus = ParentCodeStatus.NONE,
    val qr: ParentLinkQrMatrix? = null,
    val registrationStatus: ProfileRegistrationStatus = ProfileRegistrationStatus.NONE,
)

internal sealed interface SettingsAction {
    data object RetryProfile : SettingsAction
    data object CreateParentCode : SettingsAction
    data object RetryRegistration : SettingsAction
}

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val repository: ParentLinkRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private var request: Job? = null
    private var registration: Job? = null

    init { loadProfile() }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.RetryProfile -> loadProfile()
            SettingsAction.CreateParentCode -> createCode()
            SettingsAction.RetryRegistration -> registerProfile()
        }
    }

    private fun loadProfile() {
        if (request?.isActive == true) return
        registration?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, profileError = false,
            codeStatus = ParentCodeStatus.NONE, qr = null, registrationStatus = ProfileRegistrationStatus.NONE)
        request = viewModelScope.launch {
            try {
                val profile = repository.profile()
                mutableState.value = mutableState.value.copy(loading = false, profileId = profile.profileId,
                    backendConfigured = profile.backendConfigured)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, profileError = true) }
        }
    }

    private fun createCode() {
        val state = mutableState.value
        if (state.loading || state.profileError || state.codeStatus == ParentCodeStatus.READY || request?.isActive == true) return
        mutableState.value = state.copy(codeStatus = ParentCodeStatus.LOADING, qr = null)
        request = viewModelScope.launch {
            try {
                val code = repository.createCode()
                val matrix = encodeParentLinkQr(code.qrPayload)
                mutableState.value = mutableState.value.copy(codeStatus = ParentCodeStatus.READY, qr = matrix)
                // The public profile code is available before any network request starts.
                registerProfile()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = mutableState.value.copy(codeStatus = ParentCodeStatus.ERROR, qr = null)
            }
        }
    }

    private fun registerProfile() {
        val state = mutableState.value
        if (!state.backendConfigured || state.codeStatus != ParentCodeStatus.READY || registration?.isActive == true) return
        mutableState.value = state.copy(registrationStatus = ProfileRegistrationStatus.LOADING)
        registration = viewModelScope.launch {
            try {
                repository.registerProfile()
                mutableState.value = mutableState.value.copy(registrationStatus = ProfileRegistrationStatus.REGISTERED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // A registration failure never removes the locally generated public-ID code.
                mutableState.value = mutableState.value.copy(registrationStatus = ProfileRegistrationStatus.ERROR)
            }
        }
    }
}
