package ru.nksk.lctapp.domain.pet

import org.junit.Assert.*
import org.junit.Test

class PetIdentityTest {
    @Test fun userNamesAreInsertedLiterallyWithoutRecursiveTemplateExpansion() {
        val name = "Лис ${'$'}1 \\ {petName}"
        assertEquals("$name проголодался. $name ждёт.",
            renderPetText("{petName} проголодался. {petName} ждёт.", name))
    }

    @Test fun legacyContentUsesWholeNominativeNamesWithoutGuessingDeclension() {
        assertEquals("Тоша поел. Тоша рад. Рыжиковский — название.",
            renderPetText("Рыжик поел. {petName} рад. Рыжиковский — название.", "Тоша"))
    }

    @Test fun changingVisualStatePreservesSavedIdentityAndLook() {
        val pet = PetState("backend:hat", PetVisualState.NORMAL, "Тоша", PetAge.ADULT)
        assertEquals(pet.copy(visualState = PetVisualState.HUNGRY), pet.transitionTo(PetVisualState.HUNGRY))
        assertEquals(PetAge.CUB, PetState("PLAIN", PetVisualState.NORMAL).age)
        assertEquals(PetDefaults.FOX_NAME, PetState("PLAIN", PetVisualState.NORMAL).name)
    }

    @Test fun literalSubstitutionPreservesBracesAndUnicodeWordBoundaries() {
        assertEquals("(Тоша), Тоша! {other} {petName", renderPetText("(Рыжик), {petName}! {other} {petName", "Тоша"))
        assertEquals("Рыжик2 Рыжик_ Рыжик½ Рыжик𝔸 𝔸Рыжик", renderPetText("Рыжик2 Рыжик_ Рыжик½ Рыжик𝔸 𝔸Рыжик", "Тоша"))
        assertEquals("ТошаТоша", renderPetText("{petName}{petName}", "Тоша"))
        assertEquals("", renderPetText("", "Тоша"))
    }

    @Test fun blankAndMultilineNamesAreRejectedButUserPunctuationIsPreserved() {
        for (invalid in listOf("", "  ", "a\nb", "a\rb", "a\tb", "a\u2028b", "a\u2029b")) {
            assertFalse(isValidPetName(invalid))
        }
        assertTrue(isValidPetName("Лис О’Брайен-2 🦊"))
    }
}
