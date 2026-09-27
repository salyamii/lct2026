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

/** The secret is sent only in Authorization, never in the body or QR. */
@Serializable
data class RegisterProfileRequest(
    val profileId: String,
    val installationId: String,
    val pet: RegisteredPetDto,
    val schemaVersion: Int = 1,
)

@Serializable
data class RegisterProfileResponse(val profileId: String, val installationId: String)

@Serializable
data class BackendError(val code: String, val message: String? = null, val requestId: String? = null)
