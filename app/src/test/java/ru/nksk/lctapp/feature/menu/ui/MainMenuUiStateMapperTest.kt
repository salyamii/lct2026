package ru.nksk.lctapp.feature.menu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.pet.toPetState
import ru.nksk.lctapp.domain.pet.PetCustomization
import ru.nksk.lctapp.domain.pet.PetFur
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor
import ru.nksk.lctapp.core.ui.game.restingPetArtwork
import ru.nksk.lctapp.domain.pet.PetVisualState

class MainMenuUiStateMapperTest {
    @Test fun customizedNewGameDisplaysCubInItsSavedColor() {
        val expected = mapOf(
            PetFur.Copper to R.drawable.ryzhik_cub_body_base_no_accessory,
            PetFur.Sand to R.drawable.ryzhik_cub_body_base_sand,
            PetFur.Russet to R.drawable.ryzhik_cub_body_base_dark_russet,
        )
        expected.forEach { (fur, resource) ->
            val game = createInitialGameState().copy(pet = PetCustomization(fur = fur).toPetState("PLAIN"))
            assertEquals(resource, game.toMainMenuUiState().pet.artworkRes)
        }
    }

    @Test fun onboardingAccessoriesRenderWithTheSavedCubFur() {
        val expected = mapOf(
            "BANDANA" to listOf(R.drawable.ryzhik_cub_body_accessory_bandana,
                R.drawable.ryzhik_cub_body_accessory_bandana_sand, R.drawable.ryzhik_cub_body_accessory_bandana_dark_russet),
            "BACKPACK" to listOf(R.drawable.ryzhik_cub_body_accessory_backpack,
                R.drawable.ryzhik_cub_body_accessory_backpack_sand, R.drawable.ryzhik_cub_body_accessory_backpack_dark_russet),
        )
        expected.forEach { (look, artwork) ->
            PetFur.entries.forEachIndexed { index, fur ->
                val pet = PetCustomization(fur = fur).toPetState(look)
                assertEquals(artwork[index], pet.toMainMenuPetUiState().artworkRes)
            }
        }
    }

    @Test fun moodChangePreservesCustomizedAgeColorAndSelectedAccessory() {
        val profile = PetCustomization(name = "Искорка", fur = PetFur.Sand)
        val pet = profile.toPetState("HAT")
        val happy = pet.transitionTo(PetVisualState.HAPPY)
        assertEquals(R.drawable.ryzhik_cub_state_joy_sand, happy.toMainMenuPetUiState().artworkRes)
        val normal = happy.transitionTo(PetVisualState.NORMAL)
        assertEquals(R.drawable.ryzhik_cub_body_accessory_hat_sand, normal.toMainMenuPetUiState().artworkRes)
        assertEquals(profile.toPetState("HAT"), normal)
    }

    @Test fun colorFollowsPetThroughEmotionGrowthAndSleepWithoutChangingTheSelectedLook() {
        val pet = PetState("HAT", PetVisualState.NORMAL, "Тоша", PetAge.CUB, PetColor.SAND)
        assertEquals(R.drawable.ryzhik_cub_body_accessory_hat_sand, pet.toMainMenuPetUiState().artworkRes)
        val tired = pet.transitionTo(PetVisualState.TIRED)
        assertEquals(R.drawable.ryzhik_cub_state_tired_sand, tired.toMainMenuPetUiState().artworkRes)
        assertEquals(R.drawable.ryzhik_cub_state_sleep_sand, restingPetArtwork(tired))
        val adult = tired.copy(age = PetAge.ADULT)
        assertEquals(R.drawable.ryzhik_adult_state_tired_sand, adult.toMainMenuPetUiState().artworkRes)
        assertEquals(R.drawable.ryzhik_adult_body_accessory_hat_sand,
            adult.transitionTo(PetVisualState.NORMAL).toMainMenuPetUiState().artworkRes)
        val dark = adult.copy(color = PetColor.DARK_RUSSET)
        assertEquals(R.drawable.ryzhik_adult_state_tired_dark_russet, dark.toMainMenuPetUiState().artworkRes)
        assertEquals(R.drawable.ryzhik_adult_state_sleep_dark_russet, restingPetArtwork(dark))
        assertNull(dark.copy(selectedLookId = "backend:future", visualState = PetVisualState.NORMAL).toMainMenuPetUiState().artworkRes)
    }

    @Test fun savedNameAndAgeDriveBothNormalAndEmotionalArtwork() {
        val art = listOf(
            Triple(PetAge.CUB, R.drawable.ryzhik_cub_body_accessory_hat, R.drawable.ryzhik_cub_state_tired_copper),
            Triple(PetAge.TEEN, R.drawable.ryzhik_teen_body_accessory_hat, R.drawable.ryzhik_teen_state_tired_copper),
            Triple(PetAge.ADULT, R.drawable.ryzhik_adult_body_accessory_hat, R.drawable.ryzhik_adult_state_tired_copper),
            Triple(PetAge.SENIOR, R.drawable.ryzhik_senior_body_accessory_hat, R.drawable.ryzhik_senior_state_tired_copper),
        )
        for ((age, normal, tired) in art) {
            val pet = PetState("HAT", PetVisualState.NORMAL, "Тоша", age)
            val game = createInitialGameState().copy(pet = pet)
            assertEquals("Тоша", game.toMainMenuUiState().pet.name)
            assertEquals(normal, game.toMainMenuUiState().pet.artworkRes)
            assertEquals(tired, game.copy(pet = pet.transitionTo(PetVisualState.TIRED)).toMainMenuUiState().pet.artworkRes)
        }
    }

    @Test
    fun suppliedGameSnapshotDrivesTheBalanceAndPetAppearance() {
        val initial = createInitialGameState()
        val game = initial.copy(
            economy = initial.economy.copy(balance = 3_000_000_000L),
            pet = PetState("BANDANA", PetVisualState.HUNGRY),
        )

        val state = game.toMainMenuUiState()

        assertEquals(3_000_000_000L, state.coins)
        assertEquals(R.drawable.ryzhik_cub_state_hungry_copper, state.pet.artworkRes)
        assertEquals(R.string.menu_pet_hungry, state.pet.descriptionRes)
    }

    @Test
    fun normalUsesTheArtworkForTheSelectedLook() {
        val initial = createInitialGameState()
        val expectedArtwork = mapOf(
            "PLAIN" to R.drawable.ryzhik_cub_body_base_no_accessory,
            "BANDANA" to R.drawable.ryzhik_cub_body_accessory_bandana,
            "BACKPACK" to R.drawable.ryzhik_cub_body_accessory_backpack,
            "GLASSES" to R.drawable.ryzhik_cub_body_accessory_goggles,
            "HAT" to R.drawable.ryzhik_cub_body_accessory_hat,
        )

        for ((look, artwork) in expectedArtwork) {
            val game = initial.copy(pet = PetState(look, PetVisualState.NORMAL))

            assertEquals(artwork, game.toMainMenuUiState().pet.artworkRes)
        }
    }

    @Test
    fun unknownLookKeepsAnExplicitMissingArtworkDescription() {
        val initial = createInitialGameState()
        val game = initial.copy(pet = PetState("backend:new-look", PetVisualState.NORMAL))

        val pet = game.toMainMenuUiState().pet

        assertNull(pet.artworkRes)
        assertEquals(R.string.menu_pet_look_unavailable, pet.descriptionRes)
    }

    @Test
    fun specialStatesRenderWithoutSelectedAccessories() {
        val initial = createInitialGameState()
        val expectedArtwork = mapOf(
            PetVisualState.HUNGRY to R.drawable.ryzhik_cub_state_hungry_copper,
            PetVisualState.TIRED to R.drawable.ryzhik_cub_state_tired_copper,
            PetVisualState.THINKING to R.drawable.ryzhik_cub_state_thoughtful_copper,
            PetVisualState.UPSET to R.drawable.ryzhik_cub_state_sad_copper,
            PetVisualState.HAPPY to R.drawable.ryzhik_cub_state_joy_copper,
        )
        for ((visualState, artwork) in expectedArtwork) {
            for (look in listOf("PLAIN", "BANDANA", "BACKPACK", "GLASSES", "HAT", "backend:new-look")) {
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
            val game = initial.copy(pet = PetState("BACKPACK", visualState))

            val pet = game.toMainMenuUiState().pet

            assertNull(pet.artworkRes)
            assertEquals(description, pet.descriptionRes)
        }
    }

    @Test
    fun returningToNormalUsesTheCurrentLook() {
        val initial = createInitialGameState()
        val happy = PetState("BANDANA", PetVisualState.HAPPY)
        val normal = happy.copy(selectedLookId = "HAT").transitionTo(PetVisualState.NORMAL)

        val state = initial.copy(pet = normal).toMainMenuUiState()

        assertEquals(R.drawable.ryzhik_cub_body_accessory_hat, state.pet.artworkRes)
        assertEquals(R.string.menu_pet_hat, state.pet.descriptionRes)
    }
}
