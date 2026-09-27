package ru.nksk.lctapp.domain.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.engine.EventCardCopy
import ru.nksk.lctapp.domain.engine.EventPolicy
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.pet.PetVisualState

class EventSpecTest {
    @Test fun choiceIdentityOrderAndEffectsCompileTogetherWithoutInferringMissingOverrides() {
        val result = spec(listOf(
            EventChoiceSpec("pay", "Очистить составом", moneyDelta = -3, energyCost = 0,
                facts = setOf("plate_found"), recap = "Очистили составом"),
            EventChoiceSpec("work", "Почистить самому", energyCost = 2, gameKind = DeedGameKind.PRECISION,
                facts = setOf("plate_found"), recap = "Почистили сами"),
            EventChoiceSpec("legacy", "Старый выбор", facts = emptySet(), disabled = true),
        )).compile()
        assertEquals(listOf("event:pay", "event:work", "event:legacy"), result.choices.map { it.id })
        assertEquals(listOf(0, 1, 2), result.choices.map { it.position })
        assertEquals(listOf(-3L, 0L, 0L), result.choices.map { it.moneyDelta })
        assertEquals(mapOf("event:pay" to 0, "event:work" to 2), result.policy.choiceEnergyCosts)
        assertEquals(mapOf("event:work" to DeedGameKind.PRECISION), result.policy.choiceGameKinds)
        assertEquals(mapOf("event:pay" to setOf("plate_found"), "event:work" to setOf("plate_found"),
            "event:legacy" to emptySet<String>()), result.policy.factsByChoiceId)
        assertEquals(setOf("event:legacy"), result.policy.disabledChoiceIds)
        assertEquals(mapOf("event:pay" to "Очистили составом", "event:work" to "Почистили сами"), result.card.summaryByChoiceId)
    }

    @Test fun purchaseKeepsExplicitEffectIdsAndRepeatedItemsInAuthoredOrder() {
        val result = spec(listOf(EventChoiceSpec("buy", "Купить", -5, petStateAfter = PetVisualState.HAPPY,
            feedsPet = true, itemEffects = listOf(ChoiceItemSpec("original-effect", "map", ItemOperation.ADD),
                ChoiceItemSpec("second-effect", "map", ItemOperation.ADD))))).compile()
        assertEquals(listOf(
            ChoiceItemEffect("original-effect", "event:buy", 0, "map", ItemOperation.ADD),
            ChoiceItemEffect("second-effect", "event:buy", 1, "map", ItemOperation.ADD),
        ), result.choiceItemEffects)
        assertEquals(setOf("event:buy"), result.policy.feedsPetChoiceIds)
        assertEquals(PetVisualState.HAPPY, result.choices.single().petStateAfter)
        assertTrue(result.policy.choiceEnergyCosts.isEmpty())
        assertTrue(result.policy.factsByChoiceId.isEmpty())
    }

    @Test fun duplicateIdentitiesAndParallelChoiceRuleMapsAreRejected() {
        val choice = EventChoiceSpec("work", "Почистить")
        assertTrue(runCatching { spec(listOf(choice, choice)).compile() }.isFailure)
        assertTrue(runCatching { spec(listOf(choice)).copy(policy = EventPolicy(0,
            choiceGameKinds = mapOf("event:work" to DeedGameKind.MEMORY))).compile() }.isFailure)
        assertTrue(runCatching { spec(listOf(choice.copy(itemEffects = listOf(
            ChoiceItemSpec("same", "map", ItemOperation.ADD), ChoiceItemSpec("same", "key", ItemOperation.ADD))))).compile() }.isFailure)
    }

    private fun spec(choices: List<EventChoiceSpec>) = EventSpec(
        EventDefinition("event", EventType.RANDOM, "Событие", "Текст", null, null, null, 0, null, null),
        choices, EventPolicy(0), EventCardCopy("Категория", "", "", null, "", "", "city", null),
    )
}
