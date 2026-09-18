package ru.nksk.lctapp.feature.menu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.pet.PetLook
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState

class MainMenuUiStateMapperTest {
    @Test
    fun suppliedGameSnapshotDrivesTheBalanceAndPetAppearance() {
        val initial = createInitialGameState()
        val game = initial.copy(
            economy = initial.economy.copy(balance = 3_000_000_000L),
            pet = PetState(PetLook.BANDANA, PetVisualState.HUNGRY),
        )

        val state = game.toMainMenuUiState()

        assertEquals(3_000_000_000L, state.coins)
        assertEquals(R.drawable.ryzhik_teen_state_hungry_copper, state.pet.artworkRes)
        assertEquals(R.string.menu_pet_hungry, state.pet.descriptionRes)
    }

    @Test
    fun normalUsesTheArtworkForTheSelectedLook() {
        val initial = createInitialGameState()
        val expectedArtwork = mapOf(
            PetLook.PLAIN to R.drawable.ryzhik_teen_body_base_no_accessory,
            PetLook.BANDANA to R.drawable.ryzhik_teen_body_accessory_bandana,
            PetLook.BACKPACK to R.drawable.menu_ryzhik,
            PetLook.GLASSES to R.drawable.ryzhik_teen_body_accessory_goggles,
            PetLook.HAT to R.drawable.ryzhik_teen_body_accessory_hat,
        )

        for ((look, artwork) in expectedArtwork) {
            val game = initial.copy(pet = PetState(look, PetVisualState.NORMAL))

            assertEquals(artwork, game.toMainMenuUiState().pet.artworkRes)
        }
    }

    @Test
    fun specialStatesRenderWithoutSelectedAccessories() {
        val initial = createInitialGameState()
        val expectedArtwork = mapOf(
            PetVisualState.HUNGRY to R.drawable.ryzhik_teen_state_hungry_copper,
            PetVisualState.TIRED to R.drawable.ryzhik_teen_state_tired_copper,
            PetVisualState.THINKING to R.drawable.ryzhik_teen_state_thoughtful_copper,
            PetVisualState.UPSET to R.drawable.ryzhik_teen_state_sad_copper,
            PetVisualState.HAPPY to R.drawable.ryzhik_teen_state_joy_copper,
        )
        for ((visualState, artwork) in expectedArtwork) {
            for (look in PetLook.entries) {
                val game = initial.copy(pet = PetState(look, visualState))

                assertEquals(artwork, game.toMainMenuUiState().pet.artworkRes)
            }
        }
    }

    @Test
    fun unmappedStatesKeepTheirDescriptionWithoutSubstitutingAnotherAppearance() {
        val initial = createInitialGameState()
        val descriptions = mapOf(
            PetVisualState.NEEDS_HELP to R.string.menu_pet_needs_help,
            PetVisualState.WORRIED to R.string.menu_pet_worried,
        )
        for ((visualState, description) in descriptions) {
            val game = initial.copy(pet = PetState(PetLook.BACKPACK, visualState))

            val pet = game.toMainMenuUiState().pet

            assertNull(pet.artworkRes)
            assertEquals(description, pet.descriptionRes)
        }
    }

    @Test
    fun returningToNormalUsesTheCurrentLook() {
        val initial = createInitialGameState()
        val happy = PetState(PetLook.BANDANA, PetVisualState.HAPPY)
        val normal = happy.copy(selectedLook = PetLook.HAT).transitionTo(PetVisualState.NORMAL)

        val state = initial.copy(pet = normal).toMainMenuUiState()

        assertEquals(R.drawable.ryzhik_teen_body_accessory_hat, state.pet.artworkRes)
        assertEquals(R.string.menu_pet_hat, state.pet.descriptionRes)
    }
}
