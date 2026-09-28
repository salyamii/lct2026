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
import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.CloudSyncPhase
import ru.nksk.lctapp.domain.backend.CloudSyncRepository
import ru.nksk.lctapp.domain.backend.CloudSyncResult
import ru.nksk.lctapp.domain.backend.CloudSyncState

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

    @Test fun downloadingACopyNeverRestoresWithoutExplicitConfirmation() = runTest(dispatcher) {
        val cloud = FakeCloudRepository()
        val model = model(FakeRepository(), cloud = cloud)
        runCurrent()
        assertEquals(0, cloud.prepareCalls)
        model.onAction(SettingsAction.ConfirmCloudRestore(cloud.preview.id))
        model.onAction(SettingsAction.PrepareCloudRestore)
        model.onAction(SettingsAction.PrepareCloudRestore)
        runCurrent()
        assertEquals(1, cloud.prepareCalls)
        assertEquals(cloud.preview, model.uiState.value.cloud.restorePreview)
        assertTrue(cloud.restores.isEmpty())
        model.onAction(SettingsAction.ConfirmCloudRestore("stale-preview"))
        model.onAction(SettingsAction.DismissCloudRestore("stale-preview"))
        model.onAction(SettingsAction.SyncCloud)
        model.onAction(SettingsAction.PrepareCloudRestore)
        runCurrent()
        assertEquals(0, cloud.syncCalls)
        assertEquals(1, cloud.prepareCalls)
        model.onAction(SettingsAction.DismissCloudRestore(cloud.preview.id))
        runCurrent()
        assertEquals(listOf(cloud.preview.id), cloud.dismissals)
        assertNull(model.uiState.value.cloud.restorePreview)
        assertTrue(cloud.restores.isEmpty())
    }

    @Test fun confirmedRestoreUsesPreparedIdAndBlocksDuplicatesWhileSaving() = runTest(dispatcher) {
        val cloud = FakeCloudRepository().apply { pendingRestore = CompletableDeferred() }
        val model = model(FakeRepository(), cloud = cloud)
        runCurrent()
        model.onAction(SettingsAction.PrepareCloudRestore)
        runCurrent()
        model.onAction(SettingsAction.ConfirmCloudRestore(cloud.preview.id))
        model.onAction(SettingsAction.ConfirmCloudRestore(cloud.preview.id))
        model.onAction(SettingsAction.DismissCloudRestore(cloud.preview.id))
        model.onAction(SettingsAction.SyncCloud)
        runCurrent()
        assertEquals(listOf(cloud.preview.id), cloud.restores)
        assertTrue(cloud.dismissals.isEmpty())
        assertEquals(0, cloud.syncCalls)
        assertTrue(model.uiState.value.cloud.busy)
        cloud.pendingRestore!!.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.cloud.busy)
        assertNull(model.uiState.value.cloud.restorePreview)
    }

    @Test fun failedCloudOperationsKeepTheExistingQrAndReleaseTheirControls() = runTest(dispatcher) {
        val cloud = FakeCloudRepository().apply { syncFailure = true; restoreFailure = true }
        val model = model(FakeRepository(), cloud = cloud)
        runCurrent()
        model.onAction(SettingsAction.CreateParentCode)
        runCurrent()
        val qr = checkNotNull(model.uiState.value.qr)
        model.onAction(SettingsAction.SyncCloud)
        runCurrent()
        assertFalse(model.uiState.value.cloud.busy)
        assertNotNull(model.uiState.value.cloud.feedback)
        model.onAction(SettingsAction.PrepareCloudRestore)
        runCurrent()
        model.onAction(SettingsAction.ConfirmCloudRestore(cloud.preview.id))
        runCurrent()
        assertSame(qr, model.uiState.value.qr)
        assertEquals(ParentCodeStatus.READY, model.uiState.value.codeStatus)
        assertFalse(model.uiState.value.cloud.busy)
        assertNull(model.uiState.value.cloud.restorePreview)
        assertEquals(listOf(cloud.preview.id), cloud.dismissals)
        assertNotNull(model.uiState.value.cloud.feedback)
    }

    @Test fun cloudObservationPreventsOverlappingWorkAndShowsTheLatestState() = runTest(dispatcher) {
        val cloud = FakeCloudRepository()
        val model = model(FakeRepository(), cloud = cloud)
        runCurrent()
        cloud.state.value = CloudSyncState(phase = CloudSyncPhase.SYNCING)
        runCurrent()
        model.onAction(SettingsAction.SyncCloud)
        model.onAction(SettingsAction.PrepareCloudRestore)
        runCurrent()
        assertEquals(0, cloud.syncCalls)
        assertEquals(0, cloud.prepareCalls)
        assertTrue(model.uiState.value.cloud.busy)
        cloud.state.value = CloudSyncState(lastSyncedAt = "2026-09-27T13:45:00Z")
        runCurrent()
        assertFalse(model.uiState.value.cloud.busy)
        assertNotNull(model.uiState.value.cloud.lastSyncedLabel)
        assertEquals(cloud.state.value, model.uiState.value.cloud.sync)
        model.onAction(SettingsAction.SyncCloud)
        model.onAction(SettingsAction.SyncCloud)
        runCurrent()
        assertEquals(1, cloud.syncCalls)
    }

    @Test fun missingBackendCannotStartCloudRequestsButKeepsLocalParentCode() = runTest(dispatcher) {
        val cloud = FakeCloudRepository()
        val model = model(FakeRepository(configured = false), cloud = cloud)
        runCurrent()
        model.onAction(SettingsAction.SyncCloud)
        model.onAction(SettingsAction.PrepareCloudRestore)
        model.onAction(SettingsAction.CreateParentCode)
        runCurrent()
        assertEquals(0, cloud.syncCalls)
        assertEquals(0, cloud.prepareCalls)
        assertEquals(ParentCodeStatus.READY, model.uiState.value.codeStatus)
    }

    @Test fun leavingSettingsDiscardsAnUnconfirmedRestorePreview() = runTest(dispatcher) {
        val cloud = FakeCloudRepository()
        val model = model(FakeRepository(), cloud = cloud)
        runCurrent()
        model.onAction(SettingsAction.PrepareCloudRestore)
        runCurrent()
        store.clear()
        assertEquals(listOf(cloud.preview.id), cloud.dismissals)
        assertTrue(cloud.restores.isEmpty())
    }

    private fun model(repository: ParentLinkRepository, sound: MediaPreferencesRepository = FakeMediaRepository(),
        cloud: CloudSyncRepository = FakeCloudRepository()) = SettingsViewModel(repository, sound, cloud)
        .also { store.put("settings", it) }

    private class FakeCloudRepository : CloudSyncRepository {
        override val state = MutableStateFlow(CloudSyncState())
        val preview = CloudRestorePreview("preview-id", "Лис", 4, 21L, 8L)
        var syncCalls = 0
        var prepareCalls = 0
        var syncFailure = false
        var restoreFailure = false
        var pendingRestore: CompletableDeferred<Unit>? = null
        val restores = mutableListOf<String>()
        val dismissals = mutableListOf<String>()
        override suspend fun synchronize(): CloudSyncResult {
            syncCalls++
            if (syncFailure) error("Network unavailable")
            return CloudSyncResult.SUCCESS
        }
        override suspend fun prepareRestore(): CloudRestorePreview {
            prepareCalls++
            return preview
        }
        override suspend fun restore(previewId: String) {
            restores += previewId
            pendingRestore?.await()
            if (restoreFailure) error("Revision changed")
        }
        override fun dismissRestore(previewId: String) { dismissals += previewId }
    }

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
