package ru.nksk.lctapp.feature.menu.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetFur
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetAppearance
import ru.nksk.lctapp.domain.pet.PetVisualState

data class MainMenuPetUiState(
    @param:DrawableRes val artworkRes: Int?,
    @param:StringRes val descriptionRes: Int,
)

internal fun PetAppearance.toMainMenuPetUiState(): MainMenuPetUiState = when (this) {
    is PetAppearance.SelectedLook -> when (lookId) {
        "PLAIN" -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_base_no_accessory, R.string.menu_pet_plain,
        )
        "BANDANA" -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_accessory_bandana, R.string.menu_pet_bandana,
        )
        "BACKPACK" -> MainMenuPetUiState(
            R.drawable.menu_ryzhik, R.string.menu_fox_description,
        )
        "GLASSES" -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_accessory_goggles, R.string.menu_pet_glasses,
        )
        "HAT" -> MainMenuPetUiState(
            R.drawable.ryzhik_teen_body_accessory_hat, R.string.menu_pet_hat,
        )
        else -> MainMenuPetUiState(null, R.string.menu_pet_look_unavailable)
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

/** Legacy saves retain the original art; customized cubs keep their age and fur in every known pose. */
internal fun PetState.toMainMenuPetUiState(): MainMenuPetUiState {
    val legacy = appearance.toMainMenuPetUiState()
    val profile = customization ?: return legacy
    check(profile.age == PetAge.Cub)
    fun fur(copper: Int, sand: Int, russet: Int) = when (profile.fur) {
        PetFur.Copper -> copper
        PetFur.Sand -> sand
        PetFur.Russet -> russet
    }
    val artwork = when (visualState) {
        PetVisualState.HUNGRY -> fur(R.drawable.ryzhik_cub_state_hungry_copper,
            R.drawable.ryzhik_cub_state_hungry_sand, R.drawable.ryzhik_cub_state_hungry_dark_russet)
        PetVisualState.TIRED -> fur(R.drawable.ryzhik_cub_state_tired_copper,
            R.drawable.ryzhik_cub_state_tired_sand, R.drawable.ryzhik_cub_state_tired_dark_russet)
        PetVisualState.THINKING -> fur(R.drawable.ryzhik_cub_state_thoughtful_copper,
            R.drawable.ryzhik_cub_state_thoughtful_sand, R.drawable.ryzhik_cub_state_thoughtful_dark_russet)
        PetVisualState.UPSET -> fur(R.drawable.ryzhik_cub_state_sad_copper,
            R.drawable.ryzhik_cub_state_sad_sand, R.drawable.ryzhik_cub_state_sad_dark_russet)
        PetVisualState.HAPPY -> fur(R.drawable.ryzhik_cub_state_joy_copper,
            R.drawable.ryzhik_cub_state_joy_sand, R.drawable.ryzhik_cub_state_joy_dark_russet)
        PetVisualState.WORRIED, PetVisualState.NEEDS_HELP -> null
        PetVisualState.NORMAL -> when (selectedLookId) {
            "PLAIN" -> fur(R.drawable.ryzhik_cub_body_base_no_accessory,
                R.drawable.ryzhik_cub_body_base_sand, R.drawable.ryzhik_cub_body_base_dark_russet)
            "BANDANA" -> fur(R.drawable.ryzhik_cub_body_accessory_bandana,
                R.drawable.ryzhik_cub_body_accessory_bandana_sand, R.drawable.ryzhik_cub_body_accessory_bandana_dark_russet)
            "BACKPACK" -> fur(R.drawable.ryzhik_cub_body_accessory_backpack,
                R.drawable.ryzhik_cub_body_accessory_backpack_sand, R.drawable.ryzhik_cub_body_accessory_backpack_dark_russet)
            "GLASSES" -> fur(R.drawable.ryzhik_cub_body_accessory_goggles,
                R.drawable.ryzhik_cub_body_accessory_goggles_sand, R.drawable.ryzhik_cub_body_accessory_goggles_dark_russet)
            "HAT" -> fur(R.drawable.ryzhik_cub_body_accessory_hat,
                R.drawable.ryzhik_cub_body_accessory_hat_sand, R.drawable.ryzhik_cub_body_accessory_hat_dark_russet)
            else -> null
        }
    }
    return legacy.copy(artworkRes = artwork)
}
