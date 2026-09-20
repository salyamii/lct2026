package ru.nksk.lctapp.feature.menu.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.petArtwork
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetAppearance
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetDefaults
import ru.nksk.lctapp.domain.pet.PetVisualState

data class MainMenuPetUiState(
    @param:DrawableRes val artworkRes: Int?,
    @param:StringRes val descriptionRes: Int,
    val name: String = PetDefaults.FOX_NAME,
    val artworkScale: Float = 1f,
)

internal fun PetState.toMainMenuPetUiState(): MainMenuPetUiState {
    val art = petArtwork(age, color)
    val result = when (val appearance = appearance) {
        is PetAppearance.SelectedLook -> when (appearance.lookId) {
            "PLAIN" -> MainMenuPetUiState(
                art.plain, R.string.menu_pet_plain,
            )
            "BANDANA" -> MainMenuPetUiState(
                art.bandana, R.string.menu_pet_bandana,
            )
            "BACKPACK" -> MainMenuPetUiState(
                art.backpack, R.string.menu_fox_description,
            )
            "GLASSES" -> MainMenuPetUiState(
                art.glasses, R.string.menu_pet_glasses,
            )
            "HAT" -> MainMenuPetUiState(
                art.hat, R.string.menu_pet_hat,
            )
            else -> MainMenuPetUiState(null, R.string.menu_pet_look_unavailable)
        }
        is PetAppearance.SpecialState -> when (appearance.state) {
            // No verified artwork mapping exists for these two states. Render their state label.
            PetVisualState.NEEDS_HELP -> MainMenuPetUiState(null, R.string.menu_pet_needs_help)
            PetVisualState.WORRIED -> MainMenuPetUiState(null, R.string.menu_pet_worried)
            PetVisualState.HUNGRY -> MainMenuPetUiState(
                art.hungry, R.string.menu_pet_hungry,
            )
            PetVisualState.TIRED -> MainMenuPetUiState(
                art.tired, R.string.menu_pet_tired,
            )
            PetVisualState.THINKING -> MainMenuPetUiState(
                art.thinking, R.string.menu_pet_thinking,
            )
            PetVisualState.UPSET -> MainMenuPetUiState(
                art.upset, R.string.menu_pet_upset,
            )
            PetVisualState.HAPPY -> MainMenuPetUiState(
                art.happy, R.string.menu_pet_happy,
            )
            PetVisualState.NORMAL -> error("NORMAL is represented by PetAppearance.SelectedLook")
        }
    }

    return result.copy(name = name, artworkScale = if (age == PetAge.CUB) 0.8f else 1f)
}
