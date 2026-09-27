package ru.nksk.lctapp.feature.menu.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.petArtwork
import ru.nksk.lctapp.domain.pet.*

class PurchasedPetArtworkTest {
    @Test fun everySupportedAgeAndColorShowsPurchasedLookEvenDuringHappyReaction() {
        for (age in PetAge.entries) for (color in PetColor.entries) {
            val art = petArtwork(age, color)
            val expected = mapOf("HAT" to art.hat, "GLASSES" to art.glasses, "ROUTE_PATCH" to art.routePatch,
                "COMPASS" to art.compass, "BINOCULARS" to art.binoculars)
            for ((look, resource) in expected) {
                val pet = PetState(look, PetVisualState.HAPPY, age = age, color = color)
                assertEquals(resource, pet.toMainMenuPetUiState().artworkRes)
                // Rendering the equipped look never rewrites the saved emotion.
                assertEquals(PetVisualState.HAPPY, pet.visualState)
                assertEquals(art.hungry, pet.copy(visualState = PetVisualState.HUNGRY).toMainMenuPetUiState().artworkRes)
            }
        }
    }
}
