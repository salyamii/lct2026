package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetTemperament

@Serializable
data class RegisteredPetDto(
    val name: String,
    val age: PetAge,
    val color: PetColor,
    val temperament: PetTemperament?,
    val selectedLookId: String,
)

fun PetState.registrationDto() = RegisteredPetDto(name, age, color, temperament, selectedLookId)

/** The child's saved device identifier is sent in JSON and used directly in the parent's QR. */
@Serializable
data class RegisterProfileRequest(
    val deviceId: String,
    val pet: RegisteredPetDto,
    val schemaVersion: Int = 1,
) {
    init { require(deviceId.isNotBlank()) }
}

@Serializable
data class RegisterProfileResponse(val deviceId: String) {
    init { require(deviceId.isNotBlank()) }
}

@Serializable
data class BackendError(val code: String, val message: String? = null, val requestId: String? = null)
