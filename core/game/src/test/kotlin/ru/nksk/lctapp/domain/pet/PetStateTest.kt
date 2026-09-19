package ru.nksk.lctapp.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class PetStateTest {
    @Test
    fun normalDisplaysEachSelectedLook() {
        for (look in listOf("PLAIN", "BANDANA", "BACKPACK", "GLASSES", "HAT", "backend:new-look")) {
            val pet = PetState(selectedLookId = look, visualState = PetVisualState.NORMAL)

            assertEquals(PetAppearance.SelectedLook(look), pet.appearance)
        }
    }

    @Test
    fun everySpecialStateReplacesTheLookWithoutErasingIt() {
        val specialStates = listOf(
            PetVisualState.NEEDS_HELP,
            PetVisualState.HUNGRY,
            PetVisualState.TIRED,
            PetVisualState.WORRIED,
            PetVisualState.THINKING,
            PetVisualState.UPSET,
            PetVisualState.HAPPY,
        )
        for (look in listOf("PLAIN", "BANDANA", "BACKPACK", "GLASSES", "HAT", "backend:new-look")) {
            for (specialState in specialStates) {
                val normal = PetState(look, PetVisualState.NORMAL)

                val special = normal.transitionTo(specialState)

                assertEquals(PetAppearance.SpecialState(specialState), special.appearance)
                assertEquals(look, special.selectedLookId)
                assertEquals(
                    PetAppearance.SelectedLook(look),
                    special.transitionTo(PetVisualState.NORMAL).appearance,
                )
                assertEquals(PetVisualState.NORMAL, normal.visualState)
            }
        }
    }

    @Test
    fun explicitUpdatesReplaceTheStateEvenWhenTheNewPriorityIsLower() {
        val worried = PetState("BANDANA", PetVisualState.WORRIED)

        val hungry = worried.transitionTo(PetVisualState.HUNGRY)
        val happy = hungry.transitionTo(PetVisualState.HAPPY)
        val normal = happy.transitionTo(PetVisualState.NORMAL)

        assertEquals(PetAppearance.SpecialState(PetVisualState.HUNGRY), hungry.appearance)
        assertEquals(PetAppearance.SpecialState(PetVisualState.HAPPY), happy.appearance)
        assertEquals(PetAppearance.SelectedLook("BANDANA"), normal.appearance)
    }

    @Test
    fun changingLookKeepsHappyAndUpsetUntilAnExplicitStateUpdate() {
        for (outcome in listOf(PetVisualState.HAPPY, PetVisualState.UPSET)) {
            val pet = PetState("BANDANA", outcome)

            val changedLook = pet.copy(selectedLookId = "BACKPACK")

            assertEquals(PetAppearance.SpecialState(outcome), changedLook.appearance)
            assertEquals(
                PetAppearance.SelectedLook("BACKPACK"),
                changedLook.transitionTo(PetVisualState.NORMAL).appearance,
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalCannotBeRenderedAsASpecialStateWithoutTheSelectedLook() {
        PetAppearance.SpecialState(PetVisualState.NORMAL)
    }
}
