package ru.nksk.lctapp.feature.menu.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.petArtwork
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.domain.pet.*

class PurchasedPetArtworkTest {
    @Test fun everySupportedAgeAndColorReturnsToEquippedLookAfterTheReaction() {
        for (age in PetAge.entries) for (color in PetColor.entries) {
            val art = petArtwork(age, color)
            val expected = mapOf("PLAIN" to art.plain, "BANDANA" to art.bandana, "BACKPACK" to art.backpack,
                "HAT" to art.hat, "GLASSES" to art.glasses, "ROUTE_PATCH" to art.routePatch,
                "COMPASS" to art.compass, "BINOCULARS" to art.binoculars)
            for ((look, resource) in expected) {
                val pet = PetState(look, PetVisualState.HAPPY, age = age, color = color)
                assertEquals(art.happy, pet.toMainMenuPetUiState().artworkRes)
                for (reaction in PetVisualState.entries) {
                    val reacting = pet.copy(visualState = reaction)
                    val returned = reacting.toAdventurePetPresentation(showReaction = false)
                    assertEquals(resource, returned.artworkRes)
                    assertEquals(1f, returned.motionIntensity)
                    // Timer expiry projects the selected look without healing/changing the saved state.
                    assertEquals(reaction, reacting.visualState)
                    assertEquals(look, reacting.selectedLookId)
                }
                assertEquals(PetVisualState.HAPPY, pet.visualState)
                assertEquals(art.hungry, pet.copy(visualState = PetVisualState.HUNGRY).toMainMenuPetUiState().artworkRes)
            }
        }
    }
}
