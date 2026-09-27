package ru.nksk.lctapp.feature.settings.ui

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.domain.parentlink.*

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

    private fun model(repository: ParentLinkRepository) = SettingsViewModel(repository)
        .also { store.put("settings", it) }

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
