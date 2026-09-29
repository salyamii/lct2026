package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.petArtwork
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetAppearance
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetDefaults
import ru.nksk.lctapp.domain.pet.PetVisualState

internal data class AdventurePetPresentation(
    @param:DrawableRes val artworkRes: Int?,
    @param:StringRes val descriptionRes: Int,
    val name: String = PetDefaults.FOX_NAME,
    val artworkScale: Float = 1f,
    val motionIntensity: Float = 1f,
    val eventCompanionWidthFraction: Float = .56f,
)

internal fun PetState.toAdventurePetPresentation(showReaction: Boolean = true): AdventurePetPresentation {
    val art = petArtwork(age, color)
    // Returning to the equipped appearance is a projection, not recovery from hunger or illness.
    val visibleAppearance = if (showReaction) appearance else PetAppearance.SelectedLook(selectedLookId)
    val result = when (val appearance = visibleAppearance) {
        is PetAppearance.SelectedLook -> when (appearance.lookId) {
            "PLAIN" -> AdventurePetPresentation(
                art.plain, R.string.menu_pet_plain,
            )
            "BANDANA" -> AdventurePetPresentation(
                art.bandana, R.string.menu_pet_bandana,
            )
            "BACKPACK" -> AdventurePetPresentation(
                art.backpack, R.string.menu_fox_description,
            )
            "GLASSES" -> AdventurePetPresentation(
                art.glasses, R.string.menu_pet_glasses,
            )
            "HAT" -> AdventurePetPresentation(
                art.hat, R.string.menu_pet_hat,
            )
            "ROUTE_PATCH" -> AdventurePetPresentation(art.routePatch, R.string.menu_pet_route_patch)
            "COMPASS" -> AdventurePetPresentation(art.compass, R.string.menu_pet_compass)
            "BINOCULARS" -> AdventurePetPresentation(art.binoculars, R.string.menu_pet_binoculars)
            else -> rewardCapArtwork(appearance.lookId, age, color)?.let {
                AdventurePetPresentation(it, R.string.menu_pet_cap)
            } ?: AdventurePetPresentation(null, R.string.menu_pet_look_unavailable)
        }
        is PetAppearance.SpecialState -> when (appearance.state) {
            PetVisualState.NEEDS_HELP -> AdventurePetPresentation(art.sick, R.string.menu_pet_needs_help)
            // No verified artwork mapping exists for WORRIED.
            PetVisualState.WORRIED -> AdventurePetPresentation(null, R.string.menu_pet_worried)
            PetVisualState.HUNGRY -> AdventurePetPresentation(
                art.hungry, R.string.menu_pet_hungry,
            )
            PetVisualState.TIRED -> AdventurePetPresentation(
                art.tired, R.string.menu_pet_tired,
            )
            PetVisualState.THINKING -> AdventurePetPresentation(
                art.thinking, R.string.menu_pet_thinking,
            )
            PetVisualState.UPSET -> AdventurePetPresentation(
                art.upset, R.string.menu_pet_upset,
            )
            PetVisualState.HAPPY -> AdventurePetPresentation(
                art.happy, R.string.menu_pet_happy,
            )
            PetVisualState.NORMAL -> error("NORMAL is represented by PetAppearance.SelectedLook")
        }
    }

    return result.copy(
        name = name,
        artworkScale = if (age == PetAge.CUB) 0.8f else 1f,
        eventCompanionWidthFraction = if (age == PetAge.ADULT || age == PetAge.SENIOR) .72f else .56f,
        // This only softens the gesture on an existing special-state sprite; no mood is changed.
        motionIntensity = if (visibleAppearance is PetAppearance.SelectedLook || visualState == PetVisualState.HAPPY) 1f else .35f,
    )
}

