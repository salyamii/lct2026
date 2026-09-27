package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.engine.EventFactory

class DeedBalanceTest {
    @Test fun newRewardsFollowEffortAndOldInstalledDefinitionsRemainAvailable() {
        val catalog = bundledGameCatalog()
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
        assertEquals(13, catalog.deedPool.size)
        for (id in catalog.deedPool) {
            assertTrue(id.endsWith(":balance-v2"))
            val originalId = id.removeSuffix(":balance-v2")
            assertTrue(catalog.content.events.any { it.id == originalId })
            val amount = catalog.content.choices.single { it.eventId == id }.moneyDelta
            val range = when (catalog.policies.getValue(id).energyCost) {
                1 -> 4L..5L
                2 -> 6L..8L
                3 -> 10L..12L
                else -> error("Unexpected deed effort")
            }
            assertTrue("$id should reward effort", amount in range)
            assertEquals(catalog.policies.getValue(originalId).deedGameKind, catalog.policies.getValue(id).deedGameKind)
            assertNotNull("Every offered deed must remain playable: $id", catalog.policies.getValue(id).deedGameKind)
            assertTrue(originalId in catalog.policies.getValue(id).scheduling.previousEventIds)
        }
        // An old deferred offer keeps its originally displayed terms and receipt ID.
        assertEquals(8L, catalog.content.choices.single { it.eventId == "figma-2289-218-v1" }.moneyDelta)
        assertEquals(100L, catalog.rules.weeklyIncome)
        assertEquals(5L, catalog.meals.single { it.id == "basic-v1" }.price)
    }

    @Test fun practicalStoryActionsRequireAPlayedBoardWhileDialogueAndTravelKeepTheirChoices() {
        val catalog = bundledGameCatalog()
        val mapped = catalog.policies.values.flatMap { it.choiceGameKinds.keys }.toSet()
        assertEquals(13, mapped.count { it.startsWith("campaign-choice-v1:") })
        assertEquals(14, mapped.count { it.startsWith("figma-") }) // Includes one already-saved legacy resin card.
        assertTrue("${storyEventId("N1.WHEEL")}:continue" in mapped)
        assertTrue("${storyEventId("G5.08")}:continue" in mapped)
        assertTrue("${storyEventId("G2.03")}:repair" in mapped)
        assertFalse("${storyEventId("G2.03")}:detour" in mapped)
        assertTrue(catalog.policies.getValue(storyEventId("G1.01")).choiceGameKinds.isEmpty())
        assertTrue(catalog.policies.getValue(storyEventId("N2.RETURN")).choiceGameKinds.isEmpty())
        for (choiceId in mapped) {
            val choice = catalog.content.choices.single { it.id == choiceId }
            assertEquals(0L, choice.moneyDelta)
            assertTrue(catalog.content.events.single { it.id == choice.eventId }.type in setOf(
                ru.nksk.lctapp.domain.content.EventType.STORY, ru.nksk.lctapp.domain.content.EventType.RANDOM))
        }
        assertTrue(catalog.policies.getValue("figma-2320-386-v2").choiceGameKinds.isEmpty()) // Walking around is not a repair.
        assertTrue(catalog.policies.getValue("figma-2326-208-v2").choiceGameKinds.isEmpty()) // Use another telescope.
        assertTrue(mapped.none { it.endsWith(":pay") })
    }
}
