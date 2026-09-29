package ru.nksk.lctapp.app.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val client = FakeUpdateClient()
    private lateinit var model: AppUpdateViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        model = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppUpdateViewModel(client) as T
        })[AppUpdateViewModel::class.java]
    }

    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun decliningDownloadDoesNotPromptAgainOnResume() = runTest(dispatcher) {
        client.status = UpdateStatus.AVAILABLE
        val first = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertEquals(1, client.downloads)
        first.cancelAndJoin()
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertEquals(2, client.checks)
        assertEquals(1, client.downloads)
        assertFalse(model.uiState.value.readyToInstall)
    }

    @Test fun downloadedUpdateNeedsExplicitInstallAndCanBeDeferred() = runTest(dispatcher) {
        client.status = UpdateStatus.DOWNLOADED
        val first = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertTrue(model.uiState.value.readyToInstall)
        assertEquals(0, client.installations)
        assertEquals(0, client.downloads)
        model.later()
        client.events.emit(UpdateStatus.DOWNLOADED)
        runCurrent()
        assertFalse(model.uiState.value.readyToInstall)
        first.cancelAndJoin()
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertTrue(model.uiState.value.readyToInstall)
        model.install()
        model.install()
        runCurrent()
        assertEquals(1, client.installations)
    }

    @Test fun ongoingDownloadIsObservedWithoutStartingAnotherFlow() = runTest(dispatcher) {
        client.status = UpdateStatus.IN_PROGRESS
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        client.events.emit(UpdateStatus.DOWNLOADED)
        runCurrent()
        assertEquals(0, client.downloads)
        assertTrue(model.uiState.value.readyToInstall)
        assertEquals(0, client.installations)
    }

    @Test fun unavailableStoreDoesNotBlockTheAppOrOfferInstallation() = runTest(dispatcher) {
        client.checkFailure = IllegalStateException("RuStore unavailable")
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertFalse(model.uiState.value.readyToInstall)
        assertEquals(0, client.downloads)
    }

    @Test fun backgroundCheckCannotLaunchStoreUi() = runTest(dispatcher) {
        val response = CompletableDeferred<UpdateStatus>()
        client.pendingCheck = response
        val foreground = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        foreground.cancelAndJoin()
        response.complete(UpdateStatus.AVAILABLE)
        runCurrent()
        assertEquals(0, client.downloads)
        assertEquals(0, client.events.subscriptionCount.value)
    }

    @Test fun installationFailureAllowsExplicitRetry() = runTest(dispatcher) {
        client.status = UpdateStatus.DOWNLOADED
        client.installFailure = IllegalStateException("Installer unavailable")
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        model.install()
        advanceUntilIdle()
        assertTrue(model.uiState.value.readyToInstall)
        assertFalse(model.uiState.value.installing)
        client.installFailure = null
        model.install()
        advanceUntilIdle()
        assertEquals(2, client.installations)
    }

    @Test fun cancelledPreparationCanOfferDownloadOnNextResume() = runTest(dispatcher) {
        client.status = UpdateStatus.AVAILABLE
        client.downloadGate = CompletableDeferred()
        val foreground = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        foreground.cancelAndJoin()
        client.downloadGate = null
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertEquals(2, client.downloads)
        assertEquals(1, client.prompts)
    }

    @Test fun failedPreparationCanOfferDownloadOnNextResume() = runTest(dispatcher) {
        client.status = UpdateStatus.AVAILABLE
        client.downloadFailure = IllegalStateException("Network unavailable")
        val foreground = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        foreground.cancelAndJoin()
        client.downloadFailure = null
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertEquals(2, client.downloads)
        assertEquals(1, client.prompts)
    }

    @Test fun laterCheckFailureDoesNotForgetAnAlreadyShownPrompt() = runTest(dispatcher) {
        client.status = UpdateStatus.AVAILABLE
        val first = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        first.cancelAndJoin()
        client.checkFailure = IllegalStateException("Offline")
        val second = backgroundScope.launch { model.whileResumed() }
        runCurrent()
        second.cancelAndJoin()
        client.checkFailure = null
        backgroundScope.launch { model.whileResumed() }
        runCurrent()
        assertEquals(1, client.prompts)
    }

    private class FakeUpdateClient : AppUpdateClient {
        var status = UpdateStatus.NONE
        var checkFailure: Exception? = null
        var installFailure: Exception? = null
        var pendingCheck: CompletableDeferred<UpdateStatus>? = null
        var downloadGate: CompletableDeferred<Unit>? = null
        var downloadFailure: Exception? = null
        var checks = 0
        var downloads = 0
        var prompts = 0
        var installations = 0
        override val events = MutableSharedFlow<UpdateStatus>()
        override suspend fun check(): UpdateStatus {
            checks++
            checkFailure?.let { throw it }
            return pendingCheck?.await() ?: status
        }
        override suspend fun download(onPromptStarted: () -> Unit): Boolean {
            downloads++
            downloadGate?.await()
            downloadFailure?.let { throw it }
            prompts++
            onPromptStarted()
            return false
        }
        override suspend fun install() { installations++; installFailure?.let { throw it } }
    }
}
