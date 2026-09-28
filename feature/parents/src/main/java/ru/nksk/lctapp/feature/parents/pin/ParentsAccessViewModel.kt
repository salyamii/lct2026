package ru.nksk.lctapp.feature.parents.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ParentsAccessStage { Loading, Create, Confirm, Enter, Unlocked, StorageError }

enum class ParentsPinMessage { Mismatch, WrongPin }

data class ParentsAccessUiState(
    val stage: ParentsAccessStage = ParentsAccessStage.Loading,
    val enteredDigits: Int = 0,
    val busy: Boolean = false,
    val message: ParentsPinMessage? = null,
    val retryAfterSeconds: Int = 0,
) {
    val unlocked: Boolean get() = stage == ParentsAccessStage.Unlocked
    val acceptsDigits: Boolean get() = !busy && retryAfterSeconds == 0 &&
        stage in setOf(ParentsAccessStage.Create, ParentsAccessStage.Confirm, ParentsAccessStage.Enter)
}

/** Session access is intentionally never put in SavedStateHandle or navigation state. */
@HiltViewModel
class ParentsAccessViewModel @Inject constructor(
    private val repository: PinRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParentsAccessUiState())
    val uiState: StateFlow<ParentsAccessUiState> = mutableState.asStateFlow()

    private var digits = ""
    private var proposedPin = ""
    private var active = false
    private var generation = 0L
    private var work: Job? = null

    init { onForeground() }

    /** Called on Activity start; an ordinary configuration change retains the existing session. */
    fun onForeground() {
        if (active) return
        active = true
        reload()
    }

    /** Synchronous invalidation: even a non-cancellable late write cannot reopen this gate. */
    fun lock() {
        active = false
        generation++
        work?.cancel()
        work = null
        clearInput()
        mutableState.value = ParentsAccessUiState()
    }

    fun onDigit(digit: Char) {
        if (!active || !mutableState.value.acceptsDigits || digit !in '0'..'9') return
        if (digits.length >= PIN_LENGTH) return
        digits += digit
        mutableState.value = mutableState.value.copy(enteredDigits = digits.length, message = null)
        if (digits.length < PIN_LENGTH) return
        when (mutableState.value.stage) {
            ParentsAccessStage.Create -> {
                proposedPin = digits
                digits = ""
                mutableState.value = ParentsAccessUiState(stage = ParentsAccessStage.Confirm)
            }
            ParentsAccessStage.Confirm -> confirmPin()
            ParentsAccessStage.Enter -> verifyPin()
            else -> Unit
        }
    }

    fun onDelete() {
        if (!active || !mutableState.value.acceptsDigits) return
        digits = digits.dropLast(1)
        mutableState.value = mutableState.value.copy(enteredDigits = digits.length, message = null)
    }

    /** Retry always rereads persisted state; an uncertain save is never treated as a new PIN. */
    fun retry() {
        if (active && mutableState.value.stage == ParentsAccessStage.StorageError) reload()
    }

    private fun confirmPin() {
        if (digits != proposedPin) {
            clearInput()
            mutableState.value = ParentsAccessUiState(
                stage = ParentsAccessStage.Create,
                message = ParentsPinMessage.Mismatch,
            )
            return
        }
        val pin = proposedPin
        clearInput()
        mutableState.value = mutableState.value.copy(enteredDigits = 0, busy = true)
        launchWork { token ->
            when (repository.createPin(pin)) {
                CreatePinResult.Created -> ifCurrent(token) {
                    mutableState.value = ParentsAccessUiState(stage = ParentsAccessStage.Unlocked)
                }
                CreatePinResult.AlreadyConfigured -> loadStatus(token)
            }
        }
    }

    private fun verifyPin() {
        val pin = digits
        digits = ""
        mutableState.value = mutableState.value.copy(enteredDigits = 0, busy = true)
        launchWork { token ->
            val result = repository.verifyPin(pin)
            if (!isCurrent(token)) return@launchWork
            when (result) {
                VerifyPinResult.Accepted -> {
                    mutableState.value = ParentsAccessUiState(stage = ParentsAccessStage.Unlocked)
                }
                VerifyPinResult.Wrong -> {
                    mutableState.value = ParentsAccessUiState(
                        stage = ParentsAccessStage.Enter,
                        message = ParentsPinMessage.WrongPin,
                    )
                }
                is VerifyPinResult.Throttled -> showLocked(token, result.retryAfterMillis)
            }
        }
    }

    private fun reload() {
        clearInput()
        mutableState.value = ParentsAccessUiState()
        launchWork { token -> loadStatus(token) }
    }

    private suspend fun loadStatus(token: Long) {
        val status = repository.readStatus()
        if (!isCurrent(token)) return
        when (status) {
            PinStatus.NotConfigured -> {
                mutableState.value = ParentsAccessUiState(stage = ParentsAccessStage.Create)
            }
            is PinStatus.Configured -> showLocked(token, status.retryAfterMillis)
        }
    }

    private suspend fun showLocked(token: Long, retryAfterMillis: Long) {
        var seconds = ((retryAfterMillis.coerceAtLeast(0) + 999) / 1_000).toInt()
        mutableState.value = ParentsAccessUiState(
            stage = ParentsAccessStage.Enter,
            retryAfterSeconds = seconds,
        )
        // This is display-only. The repository independently checks its persisted deadline.
        while (seconds > 0) {
            delay(1_000)
            if (!isCurrent(token)) return
            seconds--
            mutableState.value = mutableState.value.copy(retryAfterSeconds = seconds)
        }
    }

    private fun launchWork(block: suspend (Long) -> Unit) {
        work?.cancel()
        val token = ++generation
        work = viewModelScope.launch {
            try {
                block(token)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ifCurrent(token) {
                    clearInput()
                    mutableState.value = ParentsAccessUiState(stage = ParentsAccessStage.StorageError)
                }
            }
        }
    }

    private fun clearInput() {
        digits = ""
        proposedPin = ""
    }

    private fun isCurrent(token: Long): Boolean = active && token == generation

    private inline fun ifCurrent(token: Long, action: () -> Unit) {
        if (isCurrent(token)) action()
    }
}
