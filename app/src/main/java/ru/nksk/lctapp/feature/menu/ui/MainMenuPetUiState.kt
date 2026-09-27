package ru.nksk.lctapp.feature.menu.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.core.ui.game.AdventurePetPresentation
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetDefaults

data class MainMenuPetUiState(
    @param:DrawableRes val artworkRes: Int?,
    @param:StringRes val descriptionRes: Int,
    val name: String = PetDefaults.FOX_NAME,
    val artworkScale: Float = 1f,
    val motionIntensity: Float = 1f,
)

internal fun PetState.toMainMenuPetUiState(): MainMenuPetUiState = toAdventurePetPresentation().toMainMenuPetUiState()

internal fun AdventurePetPresentation.toMainMenuPetUiState(): MainMenuPetUiState =
    MainMenuPetUiState(artworkRes, descriptionRes, name, artworkScale, motionIntensity)
