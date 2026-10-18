package ru.nksk.lctapp.core.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.core.ui.components.PetIndicatorKind
import ru.nksk.lctapp.core.ui.components.PetMoodUiState
import ru.nksk.lctapp.core.ui.components.PetSatietyUiState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.EventChoiceDefinition
import ru.nksk.lctapp.domain.content.EventDefinition
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.GoalImpact
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.EventOccurrence
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.EventPolicy
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetVisualState

class PetIndicatorsPresentationTest {
    private val catalog = bundledGameCatalog()
    private val event = EventDefinition("indicator-fixture", EventType.STORY, "Событие", "Описание",
        null, null, null, 0, null, null)
    private val choice = EventChoiceDefinition("indicator-choice", event.id, 0, "Сделать", 0,
        null, PetVisualState.HAPPY, GoalImpact.NEUTRAL)

    private fun state(energy: Int = 5, steps: Int = 0, ate: Boolean = false,
        visual: PetVisualState = PetVisualState.NORMAL): GameState = createInitialGameState().let {
        it.copy(pet = it.pet.transitionTo(visual), satiety = 99, fatigue = 98,
            engine = EngineState(catalog.rules.id, 1, 1, DayPhase.RUNNING, steps, energy, ate,
                null, it.economy.balance, emptyList(), emptyList()))
    }

    private fun catalogWith(policy: EventPolicy) = catalog.copy(
        content = catalog.content.copy(events = catalog.content.events + event,
            choices = catalog.content.choices + choice),
        policies = catalog.policies + (event.id to policy),
    )

    @Test fun currentIndicatorsUseEngineNeedsAndKeepEmotionCategorical() {
        val current = state(energy = 2, steps = 2, visual = PetVisualState.UPSET)
        val projected = petIndicators(current, catalog)
        assertEquals(2, projected.energy)
        assertEquals(5, projected.maxEnergy)
        assertEquals(PetSatietyUiState.NOT_EATEN, projected.satiety)
        assertEquals(PetMoodUiState.UPSET, projected.mood)
        assertEquals(PetSatietyUiState.HUNGRY, petIndicators(state(steps = 3), catalog).satiety)
        assertEquals(PetSatietyUiState.FED, petIndicators(state(steps = 100, ate = true), catalog).satiety)
        assertEquals(current, state(energy = 2, steps = 2, visual = PetVisualState.UPSET))
    }

    @Test fun beforeFirstDayShowsRestedHungryAndCalmWithoutStartingDay() {
        val current = state(visual = PetVisualState.UPSET).copy(engine = null)
        for (demoMode in listOf(false, true)) {
            val projected = petIndicators(current, catalog, demoMode = demoMode)
            assertEquals(catalog.rules.fullEnergy, projected.energy)
            assertEquals(PetSatietyUiState.NOT_EATEN, projected.satiety)
            assertEquals(PetMoodUiState.CALM, projected.mood)
        }
        assertNull(current.engine)
        assertTrue(mealPetEffects(current, catalog, "basic-v1").isEmpty())
    }

    @Test fun demoPreventsFatigueButDoesNotFillHunger() {
        val current = state(energy = 0, steps = 3, visual = PetVisualState.HUNGRY)
        val projected = petIndicators(current, catalog, demoMode = true)
        assertEquals(5, projected.energy)
        assertEquals(PetSatietyUiState.HUNGRY, projected.satiety)
        assertEquals(PetMoodUiState.HUNGRY, projected.mood)
    }

    @Test fun choiceSpecificEnergyOverridesTheGeneralEventCost() {
        val content = catalogWith(EventPolicy(energyCost = 3, choiceEnergyCosts = mapOf(choice.id to 1)))
        val current = state()
        val effects = eventChoicePetEffects(current, content, choice.id)
        assertEquals("Энергия -1", effects.single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertEquals("Радость", effects.single { it.kind == PetIndicatorKind.MOOD }.label)
        assertEquals(effects, deedPetEffects(current, content, event.id))
        assertEquals(5, current.engine!!.energy)
        assertEquals(0, current.engine!!.steps)
    }

    @Test fun hungerAndExhaustionPreventFalseHappinessPromises() {
        val content = catalogWith(EventPolicy(energyCost = 1))
        val hungryAfter = eventChoicePetEffects(state(steps = 2), content, choice.id)
        assertEquals("Проголодается", hungryAfter.single { it.kind == PetIndicatorKind.SATIETY }.label)
        assertFalse(hungryAfter.any { it.kind == PetIndicatorKind.MOOD })
        val tiredAfter = eventChoicePetEffects(state(energy = 1, ate = true), content, choice.id)
        assertFalse(tiredAfter.any { it.kind == PetIndicatorKind.MOOD })
        assertEquals("Энергия -1", tiredAfter.single { it.kind == PetIndicatorKind.ENERGY }.label)
    }

    @Test fun foodChoiceAtHungerThresholdCanStillPromiseItsAuthoredJoy() {
        val content = catalogWith(EventPolicy(energyCost = 0, feedsPetChoiceIds = setOf(choice.id)))
        val effects = eventChoicePetEffects(state(steps = 3, visual = PetVisualState.HUNGRY), content, choice.id)
        assertEquals("Утолит голод", effects.single { it.kind == PetIndicatorKind.SATIETY }.label)
        assertEquals("Радость", effects.single { it.kind == PetIndicatorKind.MOOD }.label)
    }

    @Test fun binocularChoicesDoNotRepeatUnchangedHungerOrMood() {
        val choices = catalog.content.choices.filter { it.eventId == "figma-56-64-purchase-v2" }
        assertEquals(2, choices.size)
        for (current in listOf(state(visual = PetVisualState.HAPPY),
            state(steps = catalog.rules.hungerBlocksAtStep, visual = PetVisualState.HUNGRY))) {
            choices.forEach { option ->
                assertTrue(eventChoicePetEffects(current, catalog, option.id).isEmpty())
            }
        }
    }

    @Test fun binocularChoicesStillWarnWhenTheirStepCreatesBlockingHunger() {
        val current = state(steps = catalog.rules.hungerBlocksAtStep - 1)
        assertEquals(PetSatietyUiState.NOT_EATEN, petIndicators(current, catalog).satiety)
        val choices = catalog.content.choices.filter { it.eventId == "figma-56-64-purchase-v2" }
        assertEquals(2, choices.size)
        choices.forEach { option ->
            val effect = eventChoicePetEffects(current, catalog, option.id).single()
            assertEquals(PetIndicatorKind.SATIETY, effect.kind)
            assertEquals("Проголодается", effect.label)
        }
    }

    @Test fun feedingChoiceDoesNotRepeatAlreadyFedAndHappyState() {
        val content = catalogWith(EventPolicy(energyCost = 0, feedsPetChoiceIds = setOf(choice.id)))
        val current = state(ate = true, visual = PetVisualState.HAPPY)
        assertTrue(eventChoicePetEffects(current, content, choice.id).isEmpty())
    }

    @Test fun restorationIsCappedAndDoesNotAdvertiseAnUnavailableIncrease() {
        val content = catalogWith(EventPolicy(energyCost = 0, choiceEnergyRestores = mapOf(choice.id to 2)))
        assertEquals("Энергия +1", eventChoicePetEffects(state(energy = 4), content, choice.id)
            .single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertEquals("Энергия на максимуме", eventChoicePetEffects(state(), content, choice.id)
            .single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertEquals("Восстановит больше энергии", mealPetEffects(state(energy = 4), catalog, "feast-v1")
            .single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertEquals("Энергия не изменится", mealPetEffects(state(energy = 2), catalog, "basic-v1")
            .single { it.kind == PetIndicatorKind.ENERGY }.label)
    }

    @Test fun equalRestoreAndCostDoNotInventAFullEnergyBar() {
        val content = catalogWith(EventPolicy(energyCost = 1, choiceEnergyRestores = mapOf(choice.id to 1)))
        assertEquals("Энергия не изменится", eventChoicePetEffects(state(energy = 3), content, choice.id)
            .single { it.kind == PetIndicatorKind.ENERGY }.label)
    }

    @Test fun freeMealExplainsBothDaysAndDemoRemovesItsEnergyPenalty() {
        val current = state(energy = 3)
        val ordinary = mealPetEffects(current, catalog, "community-v1")
        assertEquals("Нужен отдых", ordinary.single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertEquals("Энергия: 0 сегодня, 3 из 5 утром.", ordinary.single { it.kind == PetIndicatorKind.ENERGY }.explanation)
        val demo = mealPetEffects(current, catalog, "community-v1", demoMode = true)
        assertEquals("Энергия не тратится", demo.single { it.kind == PetIndicatorKind.ENERGY }.label)
        assertFalse(demo.any { it.explanation.orEmpty().contains("утром") })
    }

    @Test fun mealCannotPromiseJoyInsteadOfUnresolvedNeedForCare() {
        val current = state(visual = PetVisualState.NEEDS_HELP)
        assertFalse(mealPetEffects(current, catalog, "feast-v1").any { it.kind == PetIndicatorKind.MOOD })
        val content = catalogWith(EventPolicy(energyCost = 0, requiresPetHelp = true))
        val openEvent = current.copy(pet = current.pet.transitionTo(PetVisualState.NORMAL),
            story = current.story.copy(activeEventId = event.id),
            engine = current.engine!!.copy(events = listOf(EventOccurrence("active", event.id,
                EventOrigin.SCHEDULE, EventStatus.ACTIVE))))
        assertEquals(PetMoodUiState.NEEDS_CARE, petIndicators(openEvent, content).mood)
        assertFalse(mealPetEffects(openEvent, content, "feast-v1").any { it.kind == PetIndicatorKind.MOOD })
    }

    @Test fun onlyTheFestiveMealPromisesAnAuthoredHappyReaction() {
        assertNull(catalog.mealPolicy.effects("luxury-v1").visualStateAfter)
        assertEquals(1, catalog.mealPolicy.effects("luxury-v1").energyRestore)
        assertEquals(2, catalog.mealPolicy.effects("feast-v1").energyRestore)
        listOf(1, 4, 5).forEach { energy ->
            val current = state(energy = energy, visual = PetVisualState.UPSET)
            assertFalse(mealPetEffects(current, catalog, "luxury-v1").any { it.kind == PetIndicatorKind.MOOD })
            assertEquals("Радость", mealPetEffects(current, catalog, "feast-v1")
                .single { it.kind == PetIndicatorKind.MOOD }.label)
        }
    }

    @Test fun resolvingOneOccurrenceDoesNotResolveAnotherOccurrenceOfTheSameEvent() {
        val content = catalogWith(EventPolicy(energyCost = 0, requiresPetHelp = true))
        val current = state(visual = PetVisualState.NEEDS_HELP).let {
            it.copy(story = it.story.copy(activeEventId = event.id),
                engine = it.engine!!.copy(events = listOf(
                    EventOccurrence("active", event.id, EventOrigin.SCHEDULE, EventStatus.ACTIVE),
                    EventOccurrence("paused", event.id, EventOrigin.SCHEDULE, EventStatus.PAUSED),
                )))
        }
        assertFalse(eventChoicePetEffects(current, content, choice.id).any { it.kind == PetIndicatorKind.MOOD })
        val onlyCurrent = current.copy(engine = current.engine!!.copy(events = current.engine!!.events.take(1)))
        assertEquals("Радость", eventChoicePetEffects(onlyCurrent, content, choice.id)
            .single { it.kind == PetIndicatorKind.MOOD }.label)
    }
}
