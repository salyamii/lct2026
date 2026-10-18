package ru.nksk.lctapp.feature.day.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.core.ui.components.PetIndicatorEffectUiState
import ru.nksk.lctapp.core.ui.components.PetIndicatorKind

class DayChoicePresentationTest {
    private val hunger = PetIndicatorEffectUiState(PetIndicatorKind.SATIETY, "Проголодается",
        "Перед следующим делом нужно поесть.")
    private val joy = PetIndicatorEffectUiState(PetIndicatorKind.MOOD, "Радость")

    @Test fun sharedWarningAppearsOnceWhilePurchaseKeepsItsOwnConsequence() {
        val buy = DayOption("buy", "Купить", false, false, "Не хватает монет", listOf(hunger, joy))
        val pass = DayOption("pass", "Пройти мимо", false, true, petEffects = listOf(hunger))
        val presentation = dayChoicesPresentation(listOf(buy, pass))

        assertEquals(listOf(hunger), presentation.commonEffects)
        assertEquals(buy.copy(petEffects = listOf(joy)), presentation.options[0])
        assertEquals(pass.copy(petEffects = emptyList()), presentation.options[1])
        assertEquals(listOf(hunger, joy), buy.petEffects)
    }

    @Test fun differentAmountsStayBesideTheirOwnActions() {
        val shortWork = PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия -1")
        val longWork = PetIndicatorEffectUiState(PetIndicatorKind.ENERGY, "Энергия -2")
        val options = listOf(
            DayOption("short", "Немного поработать", false, true, petEffects = listOf(shortWork)),
            DayOption("long", "Поработать подольше", false, true, petEffects = listOf(longWork)),
        )
        val presentation = dayChoicesPresentation(options)
        assertTrue(presentation.commonEffects.isEmpty())
        assertEquals(options, presentation.options)
    }

    @Test fun singleVisibleChoiceKeepsItsConsequencesEvenWhenFoodBlockedChoicesExist() {
        val visible = DayOption("pass", "Пройти мимо", false, true, petEffects = listOf(hunger))
        val foodBlocked = DayOption("buy", "Купить", true, false, petEffects = listOf(hunger))
        val presentation = dayChoicesPresentation(listOf(visible, foodBlocked))
        assertTrue(presentation.commonEffects.isEmpty())
        assertEquals(listOf(visible), presentation.options)
        assertTrue(dayChoicesPresentation(listOf(foodBlocked)).options.isEmpty())
    }
}
