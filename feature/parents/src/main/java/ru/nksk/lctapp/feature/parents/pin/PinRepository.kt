package ru.nksk.lctapp.feature.parents.pin

/** Device preference, separate from game snapshots and cloud restore. Errors must propagate. */
interface PinRepository {
    suspend fun readStatus(): PinStatus
    suspend fun createPin(pin: String): CreatePinResult
    suspend fun verifyPin(pin: String): VerifyPinResult
}

sealed interface PinStatus {
    data object NotConfigured : PinStatus
    data class Configured(val retryAfterMillis: Long = 0) : PinStatus
}

enum class CreatePinResult { Created, AlreadyConfigured }

sealed interface VerifyPinResult {
    data object Accepted : VerifyPinResult
    data object Wrong : VerifyPinResult
    data class Throttled(val retryAfterMillis: Long) : VerifyPinResult
}

internal fun requireValidPin(pin: String) {
    require(pin.length == PIN_LENGTH && pin.all { it in '0'..'9' })
}

internal const val PIN_LENGTH = 4
