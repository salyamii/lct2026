package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Existing immutable choice IDs and execution modes survive moving to authored specs. */
class BundledEventSpecCompatibilityTest {
    @Test fun practicalChoicesRetainTheExactLegacyGameAssignments() {
        val catalog = bundledGameCatalog()
        val expected = mapOf(
            "campaign-choice-v1:G1.03:continue" to DeedGameKind.PRECISION,
            "campaign-choice-v1:N1.WHEEL:continue" to DeedGameKind.PRECISION,
            "campaign-choice-v1:G2.03:repair" to DeedGameKind.PRECISION,
            "campaign-choice-v1:N3.ACCESS:continue" to DeedGameKind.MEMORY,
            "campaign-choice-v1:N3.WORKBENCH:continue" to DeedGameKind.PRECISION,
            "campaign-choice-v1:G3.06:continue" to DeedGameKind.MEMORY,
            "campaign-choice-v1:G3.08:continue" to DeedGameKind.PRECISION,
            "campaign-choice-v1:G3.09:continue" to DeedGameKind.PRECISION,
            "campaign-choice-v1:G3.10:continue" to DeedGameKind.MEMORY,
            "campaign-choice-v1:G3.11:continue" to DeedGameKind.PRECISION,
            "campaign-choice-v1:G4.02:continue" to DeedGameKind.MEMORY,
            "campaign-choice-v1:G4.08:continue" to DeedGameKind.MEMORY,
            "campaign-choice-v1:G5.08:continue" to DeedGameKind.PRECISION,
            "figma-2297-2-v2:work" to DeedGameKind.PRECISION,
            "figma-2308-2-v2:work" to DeedGameKind.MEMORY,
            "figma-2313-2-v2:work" to DeedGameKind.PRECISION,
            "figma-2313-2-v1:clean" to DeedGameKind.PRECISION,
            "figma-2320-50-v2:work" to DeedGameKind.PRECISION,
            "figma-2320-146-v2:work" to DeedGameKind.PRECISION,
            "figma-2320-194-v2:work" to DeedGameKind.PRECISION,
            "figma-2320-290-v2:work" to DeedGameKind.MEMORY,
            "figma-2320-338-v2:work" to DeedGameKind.PRECISION,
            "figma-2326-64-v2:work" to DeedGameKind.MEMORY,
            "figma-2326-112-v2:work" to DeedGameKind.MEMORY,
            "figma-2326-160-v3:work" to DeedGameKind.PRECISION,
            "figma-2326-256-v2:work" to DeedGameKind.PRECISION,
            "figma-2326-352-v2:work" to DeedGameKind.MEMORY,
            "figma-2326-448-v2:work" to DeedGameKind.PRECISION,
        )
        assertEquals(expected, catalog.policies.values.flatMap { it.choiceGameKinds.entries }.associate { it.toPair() })
        for ((choiceId, _) in expected) {
            val eventId = catalog.content.choices.single { it.id == choiceId }.eventId
            assertNotNull("Missing activity media for $choiceId", catalog.cards.getValue(eventId).presentation.media.game)
        }
        assertTrue(catalog.policies.getValue(LORE_PLATE_CLEANING).choiceGameKinds.isEmpty())
    }

    @Test fun authoredChoicesKeepPricesFactsRecapsAndRetiredVersions() {
        val catalog = bundledGameCatalog()
        val boughtBoat = catalog.content.choices.single { it.id == "figma-2654-194-purchase-v2:buy" }
        assertEquals(-5L, boughtBoat.moneyDelta)
        assertEquals("figma-2654-194-toy-boat-v1", catalog.content.choiceItemEffects.single { it.choiceId == boughtBoat.id }.itemId)
        val lore = catalog.policies.getValue(LORE_PLATE_CLEANING)
        assertEquals(mapOf("$LORE_PLATE_CLEANING:pay" to 0, "$LORE_PLATE_CLEANING:continue" to 1), lore.choiceEnergyCosts)
        assertEquals(setOf("plate_found", "plate_symbol"), lore.factsByChoiceId.getValue("$LORE_PLATE_CLEANING:pay"))
        assertTrue(catalog.content.events.any { it.id == LEGACY_LORE_PLATE_CLEANING })
        assertTrue(catalog.content.events.any { it.id == LEGACY_PLATE_CLEANING })
        assertEquals("Решили продолжить экспедицию обычным маршрутом",
            catalog.cards.getValue("campaign-choice-v1:G5.02").summaryByChoiceId["campaign-choice-v1:G5.02:skip"])
        val migratedEvents = catalog.content.events.filter { it.type == EventType.STORY && it.id.startsWith("campaign-choice-") ||
            it.id.endsWith("purchase-v2") || it.type == EventType.RANDOM && it.id.endsWith("-v2") }
        for (event in migratedEvents) {
            val choices = catalog.content.choices.filter { it.eventId == event.id }.sortedBy { it.position }
            assertEquals(choices.indices.toList(), choices.map { it.position })
            assertTrue("Missing recap for ${event.id}", choices.all { catalog.cards.getValue(event.id).summaryByChoiceId[it.id] != null })
        }
    }
}
