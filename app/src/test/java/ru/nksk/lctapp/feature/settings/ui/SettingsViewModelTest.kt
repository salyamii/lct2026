package ru.nksk.lctapp.feature.settings.ui

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.domain.parentlink.*
import ru.nksk.lctapp.domain.media.MediaPreferences
import ru.nksk.lctapp.domain.media.MediaPreferencesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun qrContainsExactlyTheStoredUuidAndWorksWithoutABackend() = runTest(dispatcher) {
        val repository = FakeRepository(configured = false)
        val model = model(repository)
        runCurrent()
        assertEquals(ProfileId, model.uiState.value.profileId)
        assertEquals(ParentCodeStatus.NONE, model.uiState.value.codeStatus)
        assertEquals(0, repository.createCalls)
        assertEquals(0, repository.registrationCalls)
        model.onAction(SettingsAction.CreateParentCode)
        val ready = model.uiState.first { it.codeStatus == ParentCodeStatus.READY }
        assertEquals(encodeParentLinkQr(ProfileId), ready.qr)
        assertEquals(1, repository.createCalls)
        assertEquals(0, repository.registrationCalls)
        assertEquals(ProfileRegistrationStatus.NONE, ready.registrationStatus)
    }

    @Test fun profileReadFailureHasAnExplicitRetry() = runTest(dispatcher) {
        val repository = FakeRepository().apply { profileFailure = true }
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value.profileError)
        assertNull(model.uiState.value.profileId)
        repository.profileFailure = false
        model.onAction(SettingsAction.RetryProfile)
        runCurrent()
        assertFalse(model.uiState.value.profileError)
        assertEquals(ProfileId, model.uiState.value.profileId)
        assertEquals(ParentCodeStatus.NONE, model.uiState.value.codeStatus)
        assertEquals(0, repository.registrationCalls)
    }

    @Test fun networkRequestStartsOnlyAfterTheQrIsReady() = runTest(dispatcher) {
        val repository = FakeRepository().apply { pendingRegistration = CompletableDeferred() }
        val model = model(repository)
        repository.beforeRegistration = {
            assertEquals(ParentCodeStatus.READY, model.uiState.value.codeStatus)
            assertNotNull(model.uiState.value.qr)
        }
        runCurrent()
        model.onAction(SettingsAction.CreateParentCode)
        model.uiState.first { it.registrationStatus == ProfileRegistrationStatus.LOADING }
        runCurrent()
        assertEquals(1, repository.registrationCalls)
        val matrix = model.uiState.value.qr
        assertNotNull(matrix)
        model.onAction(SettingsAction.RetryRegistration)
        runCurrent()
        assertEquals(1, repository.registrationCalls)
        repository.pendingRegistration!!.complete(Unit)
        model.uiState.first { it.registrationStatus == ProfileRegistrationStatus.REGISTERED }
        assertSame(matrix, model.uiState.value.qr)
    }

    @Test fun registrationFailureAndRetryNeverRemoveOrRegenerateTheLocalCode() = runTest(dispatcher) {
        val repository = FakeRepository().apply { registrationFailure = true }
        val model = model(repository)
        runCurrent()
        model.onAction(SettingsAction.CreateParentCode)
        val failed = model.uiState.first { it.registrationStatus == ProfileRegistrationStatus.ERROR }
        val matrix = checkNotNull(failed.qr)
        assertEquals(ParentCodeStatus.READY, failed.codeStatus)
        repository.registrationFailure = false
        model.onAction(SettingsAction.RetryRegistration)
        val registered = model.uiState.first { it.registrationStatus == ProfileRegistrationStatus.REGISTERED }
        assertSame(matrix, registered.qr)
        assertEquals(ParentCodeStatus.READY, registered.codeStatus)
        assertEquals(1, repository.createCalls)
        assertEquals(2, repository.registrationCalls)
    }

    @Test fun localGenerationErrorCanBeRetriedWithoutTryingTheNetworkFirst() = runTest(dispatcher) {
        val repository = FakeRepository(configured = false).apply { createFailure = true }
        val model = model(repository)
        runCurrent()
        model.onAction(SettingsAction.CreateParentCode)
        runCurrent()
        assertEquals(ParentCodeStatus.ERROR, model.uiState.value.codeStatus)
        assertEquals(0, repository.registrationCalls)
        assertNull(model.uiState.value.qr)
        repository.createFailure = false
        model.onAction(SettingsAction.CreateParentCode)
        assertNotNull(model.uiState.first { it.codeStatus == ParentCodeStatus.READY }.qr)
    }

    @Test fun duplicateTapsDoNotCreateMultipleCodesOrRegistrations() = runTest(dispatcher) {
        val repository = FakeRepository().apply { pendingCode = CompletableDeferred() }
        val model = model(repository)
        runCurrent()
        model.onAction(SettingsAction.CreateParentCode)
        model.onAction(SettingsAction.CreateParentCode)
        runCurrent()
        assertEquals(1, repository.createCalls)
        assertEquals(0, repository.registrationCalls)
        assertEquals(ParentCodeStatus.LOADING, model.uiState.value.codeStatus)
        assertNull(model.uiState.value.qr)
        repository.pendingCode!!.complete(ParentLinkCode(ProfileId))
        model.uiState.first { it.registrationStatus == ProfileRegistrationStatus.REGISTERED }
        model.onAction(SettingsAction.CreateParentCode)
        runCurrent()
        assertEquals(1, repository.createCalls)
        assertEquals(1, repository.registrationCalls)
    }

    @Test fun soundReadFailureDoesNotInventAValueAndDoesNotBlockParentCode() = runTest(dispatcher) {
        val sound = FakeMediaRepository().apply { readFailure = true }
        val model = model(FakeRepository(configured = false), sound)
        runCurrent()
        assertNull(model.uiState.value.sound.enabled)
        assertFalse(model.uiState.value.sound.canChange)
        assertEquals(SoundSettingsError.READ, model.uiState.value.sound.error)
        model.onAction(SettingsAction.SetSoundEnabled(false))
        model.onAction(SettingsAction.CreateParentCode)
        runCurrent()
        assertEquals(0, sound.writeCalls)
        assertEquals(ParentCodeStatus.READY, model.uiState.value.codeStatus)
        sound.readFailure = false
        sound.saved.value = MediaPreferences(soundEnabled = false)
        model.onAction(SettingsAction.RetrySound)
        runCurrent()
        assertEquals(false, model.uiState.value.sound.enabled)
        assertTrue(model.uiState.value.sound.canChange)
        assertNull(model.uiState.value.sound.error)
    }

    @Test fun failedSoundWriteKeepsConfirmedValueAndRetrySavesTheSameChoice() = runTest(dispatcher) {
        val sound = FakeMediaRepository().apply { writeFailure = true }
        val model = model(FakeRepository(), sound)
        runCurrent()
        model.onAction(SettingsAction.SetSoundEnabled(false))
        runCurrent()
        assertEquals(true, model.uiState.value.sound.enabled)
        assertEquals(SoundSettingsError.WRITE, model.uiState.value.sound.error)
        assertFalse(model.uiState.value.sound.saving)
        sound.writeFailure = false
        model.onAction(SettingsAction.RetrySound)
        runCurrent()
        assertEquals(false, model.uiState.value.sound.enabled)
        assertEquals(listOf(false, false), sound.writes)
        assertNull(model.uiState.value.sound.error)
    }

    @Test fun soundWriteBlocksDuplicateTapsAndScreenRecreationReadsSavedValue() = runTest(dispatcher) {
        val sound = FakeMediaRepository().apply { pendingWrite = CompletableDeferred() }
        val model = model(FakeRepository(), sound)
        runCurrent()
        model.onAction(SettingsAction.SetSoundEnabled(false))
        model.onAction(SettingsAction.SetSoundEnabled(true))
        model.onAction(SettingsAction.RetrySound)
        runCurrent()
        assertTrue(model.uiState.value.sound.saving)
        assertEquals(true, model.uiState.value.sound.enabled)
        assertEquals(1, sound.writeCalls)
        sound.pendingWrite!!.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.sound.saving)
        val reopened = this@SettingsViewModelTest.model(FakeRepository(), sound)
        runCurrent()
        assertEquals(false, reopened.uiState.value.sound.enabled)
        assertEquals(1, sound.writeCalls)
    }

    @Test fun successfulExternalSoundChangeConfirmsAnEarlierFailedToggle() = runTest(dispatcher) {
        val sound = FakeMediaRepository().apply { writeFailure = true }
        val model = model(FakeRepository(), sound)
        runCurrent()
        model.onAction(SettingsAction.SetSoundEnabled(false))
        runCurrent()
        assertEquals(SoundSettingsError.WRITE, model.uiState.value.sound.error)
        sound.saved.value = MediaPreferences(false)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value.sound.enabled))
        assertNull(model.uiState.value.sound.error)
        model.onAction(SettingsAction.RetrySound)
        runCurrent()
        assertEquals(1, sound.writeCalls)
    }

    private fun model(repository: ParentLinkRepository, sound: MediaPreferencesRepository = FakeMediaRepository()) = SettingsViewModel(repository, sound)
        .also { store.put("settings", it) }

    private class FakeMediaRepository : MediaPreferencesRepository {
        val saved = MutableStateFlow(MediaPreferences())
        var readFailure = false
        var writeFailure = false
        var pendingWrite: CompletableDeferred<Unit>? = null
        val writes = mutableListOf<Boolean>()
        val writeCalls get() = writes.size
        override fun observe() = flow {
            if (readFailure) error("Preference read failed")
            emitAll(saved)
        }
        override suspend fun read() = observe().first()
        override suspend fun setSoundEnabled(enabled: Boolean) {
            writes += enabled
            pendingWrite?.await()
            if (writeFailure) error("Preference write failed")
            saved.value = MediaPreferences(enabled)
        }
    }

    private class FakeRepository(private val configured: Boolean = true) : ParentLinkRepository {
        var profileFailure = false
        var createFailure = false
        var registrationFailure = false
        var createCalls = 0
        var registrationCalls = 0
        var pendingCode: CompletableDeferred<ParentLinkCode>? = null
        var pendingRegistration: CompletableDeferred<Unit>? = null
        var beforeRegistration: () -> Unit = {}
        override suspend fun profile(): ParentLinkProfile {
            if (profileFailure) error("Storage unavailable")
            return ParentLinkProfile(ProfileId, configured)
        }
        override suspend fun createCode(): ParentLinkCode {
            createCalls++
            if (createFailure) error("Storage unavailable")
            return pendingCode?.await() ?: ParentLinkCode(ProfileId)
        }
        override suspend fun registerProfile() {
            registrationCalls++
            beforeRegistration()
            if (registrationFailure) error("Connection interrupted")
            pendingRegistration?.await()
        }
    }
    private companion object { const val ProfileId = "ad64c0e4-2731-4c1e-9fc9-bac812fd5995" }
}
