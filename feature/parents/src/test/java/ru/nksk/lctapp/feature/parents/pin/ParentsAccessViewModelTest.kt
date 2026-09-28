package ru.nksk.lctapp.feature.parents.pin

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParentsAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun installMain() { Dispatchers.setMain(dispatcher) }
    @After fun restoreMain() { Dispatchers.resetMain() }

    @Test fun `storage failure never offers PIN creation or unlock`() = runTest(dispatcher) {
        val repository = FakePinRepository().apply { readFailure = IOException("unavailable") }
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        assertEquals(ParentsAccessStage.StorageError, viewModel.uiState.value.stage)
        viewModel.enter("1234")
        assertFalse(viewModel.uiState.value.unlocked)

        repository.readFailure = null
        repository.status = PinStatus.Configured()
        viewModel.retry()
        runCurrent()
        assertEquals(ParentsAccessStage.Enter, viewModel.uiState.value.stage)
    }

    @Test fun `creation requires matching confirmation before access`() = runTest(dispatcher) {
        val repository = FakePinRepository()
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        assertEquals(ParentsAccessStage.Confirm, viewModel.uiState.value.stage)
        viewModel.enter("4321")
        runCurrent()
        assertEquals(ParentsAccessStage.Create, viewModel.uiState.value.stage)
        assertEquals(ParentsPinMessage.Mismatch, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.unlocked)

        viewModel.enter("1234")
        viewModel.enter("1234")
        runCurrent()
        assertTrue(viewModel.uiState.value.unlocked)
        assertEquals(PinStatus.Configured(), repository.readStatus())
    }

    @Test fun `incorrect PIN does not unlock and clears entered digits`() = runTest(dispatcher) {
        val repository = FakePinRepository().apply {
            status = PinStatus.Configured()
            verification = VerifyPinResult.Wrong
        }
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        runCurrent()
        assertEquals(ParentsAccessStage.Enter, viewModel.uiState.value.stage)
        assertEquals(ParentsPinMessage.WrongPin, viewModel.uiState.value.message)
        assertEquals(0, viewModel.uiState.value.enteredDigits)
    }

    @Test fun `throttle loaded after reopening prevents entry`() = runTest(dispatcher) {
        val repository = FakePinRepository().apply { status = PinStatus.Configured(30_000) }
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        assertEquals(0, viewModel.uiState.value.enteredDigits)
        assertEquals(30, viewModel.uiState.value.retryAfterSeconds)
        viewModel.lock()
    }

    @Test fun `background discards noncancellable late verification`() = runTest(dispatcher) {
        val completion = CompletableDeferred<VerifyPinResult>()
        val repository = FakePinRepository().apply {
            status = PinStatus.Configured()
            pendingVerification = completion
        }
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        runCurrent()
        assertTrue(viewModel.uiState.value.busy)
        viewModel.lock()
        viewModel.onForeground()
        runCurrent()
        completion.complete(VerifyPinResult.Accepted)
        runCurrent()
        assertFalse(viewModel.uiState.value.unlocked)
        assertEquals(ParentsAccessStage.Enter, viewModel.uiState.value.stage)
    }

    @Test fun `uncertain setup failure retries by reading the configured PIN`() = runTest(dispatcher) {
        val repository = FakePinRepository()
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        repository.creationFailure = IOException("write result unavailable")
        viewModel.enter("1234")
        runCurrent()
        assertEquals(ParentsAccessStage.StorageError, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.unlocked)

        repository.status = PinStatus.Configured()
        viewModel.retry()
        runCurrent()
        assertEquals(ParentsAccessStage.Enter, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.unlocked)
    }

    @Test fun `committed setup after background requires PIN on return`() = runTest(dispatcher) {
        val completion = CompletableDeferred<CreatePinResult>()
        val repository = FakePinRepository().apply { pendingCreation = completion }
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        viewModel.enter("1234")
        runCurrent()
        viewModel.lock()
        repository.status = PinStatus.Configured()
        completion.complete(CreatePinResult.Created)
        runCurrent()
        assertFalse(viewModel.uiState.value.unlocked)
        viewModel.onForeground()
        runCurrent()
        assertEquals(ParentsAccessStage.Enter, viewModel.uiState.value.stage)
    }

    @Test fun `new viewmodel never restores an unlocked session`() = runTest(dispatcher) {
        val repository = FakePinRepository().apply { status = PinStatus.Configured() }
        val original = ParentsAccessViewModel(repository)
        runCurrent()
        original.enter("1234")
        runCurrent()
        assertTrue(original.uiState.value.unlocked)

        val restored = ParentsAccessViewModel(repository)
        runCurrent()
        assertEquals(ParentsAccessStage.Enter, restored.uiState.value.stage)
        assertFalse(restored.uiState.value.unlocked)
    }

    @Test fun `PIN configured during setup cannot be overwritten or bypassed`() = runTest(dispatcher) {
        val repository = FakePinRepository()
        val viewModel = ParentsAccessViewModel(repository)
        runCurrent()
        viewModel.enter("1234")
        repository.status = PinStatus.Configured()
        viewModel.enter("1234")
        runCurrent()
        assertEquals(ParentsAccessStage.Enter, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.unlocked)
    }

    private fun ParentsAccessViewModel.enter(pin: String) = pin.forEach(::onDigit)

    private class FakePinRepository : PinRepository {
        var status: PinStatus = PinStatus.NotConfigured
        var readFailure: IOException? = null
        var creationFailure: IOException? = null
        var verification: VerifyPinResult = VerifyPinResult.Accepted
        var pendingVerification: CompletableDeferred<VerifyPinResult>? = null
        var pendingCreation: CompletableDeferred<CreatePinResult>? = null

        override suspend fun readStatus(): PinStatus {
            readFailure?.let { throw it }
            return status
        }

        override suspend fun createPin(pin: String): CreatePinResult {
            creationFailure?.let { throw it }
            pendingCreation?.let { return withContext(NonCancellable) { it.await() } }
            if (status is PinStatus.Configured) return CreatePinResult.AlreadyConfigured
            status = PinStatus.Configured()
            return CreatePinResult.Created
        }

        override suspend fun verifyPin(pin: String): VerifyPinResult =
            pendingVerification?.let { withContext(NonCancellable) { it.await() } } ?: verification
    }
}
