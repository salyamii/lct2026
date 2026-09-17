package ru.nksk.lctapp.feature.menu.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.pet.PetAppearance
import ru.nksk.lctapp.domain.pet.PetLook
import ru.nksk.lctapp.domain.pet.PetVisualState

data class MainMenuPetUiState(
    @param:DrawableRes val artworkRes: Int?,
    @param:StringRes val descriptionRes: Int,
)

internal fun PetAppearance.toMainMenuPetUiState(): MainMenuPetUiState = when (this) {
    is PetAppearance.SelectedLook -> when (look) {
        PetLook.PLAIN -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_base_no_accessory, R.string.menu_pet_plain,
        )
        PetLook.BANDANA -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_accessory_bandana, R.string.menu_pet_bandana,
        )
        PetLook.BACKPACK -> MainMenuPetUiState(
            R.drawable.menu_ryzhik, R.string.menu_fox_description,
        )
        PetLook.GLASSES -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_accessory_goggles, R.string.menu_pet_glasses,
        )
        PetLook.HAT -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_accessory_hat, R.string.menu_pet_hat,
        )
    }
    is PetAppearance.SpecialState -> when (state) {
        // No verified artwork mapping exists for these two states. Render their state label.
        PetVisualState.NEEDS_HELP -> MainMenuPetUiState(null, R.string.menu_pet_needs_help)
        PetVisualState.WORRIED -> MainMenuPetUiState(null, R.string.menu_pet_worried)
        PetVisualState.HUNGRY -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_state_hungry_copper, R.string.menu_pet_hungry,
        )
        PetVisualState.TIRED -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_state_tired_copper, R.string.menu_pet_tired,
        )
        PetVisualState.THINKING -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_state_thoughtful_copper, R.string.menu_pet_thinking,
        )
        PetVisualState.UPSET -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_state_sad_copper, R.string.menu_pet_upset,
        )
        PetVisualState.HAPPY -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_state_joy_copper, R.string.menu_pet_happy,
        )
        PetVisualState.NORMAL -> error("NORMAL is represented by PetAppearance.SelectedLook")
    }
}
