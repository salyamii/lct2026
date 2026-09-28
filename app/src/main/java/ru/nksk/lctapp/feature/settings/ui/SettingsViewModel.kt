package ru.nksk.lctapp.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.media.MediaPreferencesRepository
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository

internal enum class ParentCodeStatus { NONE, LOADING, READY, ERROR }
internal enum class ProfileRegistrationStatus { NONE, LOADING, REGISTERED, ERROR }
internal enum class SoundSettingsError { READ, WRITE }
internal data class SoundSettingsUiState(
    val enabled: Boolean? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: SoundSettingsError? = null,
) {
    val canChange: Boolean get() = enabled != null && !loading && !saving && error != SoundSettingsError.READ
}
internal data class SettingsUiState(
    val loading: Boolean = true,
    val profileId: String? = null,
    val backendConfigured: Boolean = false,
    val profileError: Boolean = false,
    val codeStatus: ParentCodeStatus = ParentCodeStatus.NONE,
    val qr: ParentLinkQrMatrix? = null,
    val registrationStatus: ProfileRegistrationStatus = ProfileRegistrationStatus.NONE,
    val sound: SoundSettingsUiState = SoundSettingsUiState(),
)

internal sealed interface SettingsAction {
    data object RetryProfile : SettingsAction
    data object CreateParentCode : SettingsAction
    data object RetryRegistration : SettingsAction
    data class SetSoundEnabled(val enabled: Boolean) : SettingsAction
    data object RetrySound : SettingsAction
}

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val repository: ParentLinkRepository,
    private val mediaPreferences: MediaPreferencesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private var request: Job? = null
    private var registration: Job? = null
    private var soundObserver: Job? = null
    private var pendingSoundEnabled: Boolean? = null

    init { loadProfile(); observeSound() }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.RetryProfile -> loadProfile()
            SettingsAction.CreateParentCode -> createCode()
            SettingsAction.RetryRegistration -> registerProfile()
            is SettingsAction.SetSoundEnabled -> setSoundEnabled(action.enabled)
            SettingsAction.RetrySound -> {
                if (!mutableState.value.sound.saving) {
                    if (mutableState.value.sound.error == SoundSettingsError.READ) observeSound()
                    else pendingSoundEnabled?.let(::setSoundEnabled)
                }
            }
        }
    }

    private fun observeSound() {
        if (soundObserver?.isActive == true) return
        mutableState.value = mutableState.value.copy(sound = mutableState.value.sound.copy(loading = true, error = null))
        soundObserver = viewModelScope.launch {
            try {
                mediaPreferences.observe().collect { preferences ->
                    val current = mutableState.value
                    val confirmed = !current.sound.saving && pendingSoundEnabled == preferences.soundEnabled
                    if (confirmed) pendingSoundEnabled = null
                    mutableState.value = current.copy(sound = current.sound.copy(enabled = preferences.soundEnabled,
                        loading = false, error = current.sound.error.takeUnless { confirmed || it == SoundSettingsError.READ }))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                val current = mutableState.value
                mutableState.value = current.copy(sound = current.sound.copy(loading = false, error = SoundSettingsError.READ))
            }
        }
    }

    private fun setSoundEnabled(enabled: Boolean) {
        val current = mutableState.value
        if (!current.sound.canChange) return
        if (current.sound.enabled == enabled && pendingSoundEnabled == null) return
        pendingSoundEnabled = enabled
        mutableState.value = current.copy(sound = current.sound.copy(saving = true, error = null))
        viewModelScope.launch {
            try {
                mediaPreferences.setSoundEnabled(enabled)
                pendingSoundEnabled = null
                val saved = mutableState.value
                mutableState.value = saved.copy(sound = saved.sound.copy(enabled = enabled, saving = false))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                val saved = mutableState.value
                val confirmed = saved.sound.error != SoundSettingsError.READ && saved.sound.enabled == enabled
                if (confirmed) pendingSoundEnabled = null
                mutableState.value = saved.copy(sound = saved.sound.copy(saving = false,
                    error = when {
                        saved.sound.error == SoundSettingsError.READ -> SoundSettingsError.READ
                        confirmed -> null
                        else -> SoundSettingsError.WRITE
                    }))
            }
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
