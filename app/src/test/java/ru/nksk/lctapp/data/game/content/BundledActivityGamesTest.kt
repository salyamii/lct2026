package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.engine.EventFactory
import ru.nksk.lctapp.domain.engine.StoryCondition
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.story.StoryDecision

class BundledActivityGamesTest {
    private val catalog = bundledGameCatalog()
    private val deedBoards = linkedMapOf(
        "2163-2" to DeedGameKind.COMPARISON, "2163-43" to DeedGameKind.PRECISION,
        "2238-120" to DeedGameKind.PRECISION, "2270-2" to DeedGameKind.STACKING,
        "2270-54" to DeedGameKind.PIPES, "2270-106" to DeedGameKind.DIFFERENCES,
        "2270-158" to DeedGameKind.PRECISION, "2270-210" to DeedGameKind.MEMORY,
        "2289-2" to DeedGameKind.PRECISION, "2289-56" to DeedGameKind.PIPES,
        "2289-110" to DeedGameKind.DIFFERENCES, "2289-164" to DeedGameKind.MEMORY,
        "2289-218" to DeedGameKind.PRECISION,
    )
    private val changedDeeds = setOf("2238-120", "2270-54", "2270-106", "2289-2", "2289-110", "2289-164", "2289-218")
    private val storyBoards = mapOf("G2.03" to DeedGameKind.PIPES, "G3.06" to DeedGameKind.DIFFERENCES,
        "G3.09" to DeedGameKind.STACKING, "G3.11" to DeedGameKind.SEQUENCE, "G5.08" to DeedGameKind.LIGHTS)
    private val revisions = changedDeeds.associate { "figma-$it-v1:balance-v2" to "figma-$it-v1:balance-v2:game-v3" } +
        storyBoards.keys.associate { "campaign-choice-v1:$it" to "campaign-choice-v2:$it" } +
        mapOf("figma-2326-112-v2" to "figma-2326-112-v3", "figma-2326-352-v2" to "figma-2326-352-v3")

    @Test fun allEightBoardsHaveMeaningfulCurrentAssignments() {
        assertEquals(deedBoards.keys.map { "figma-$it-v1:balance-v2" + if (it in changedDeeds) ":game-v3" else "" }, catalog.deedPool)
        for ((node, kind) in deedBoards) {
            val id = catalog.deedPool.single { it.startsWith("figma-$node-v1:") }
            assertEquals(node, kind, catalog.policies.getValue(id).deedGameKind)
        }
        val storyIds = catalog.storyCampaign!!.acts.flatMap { it.eventIds }
        for ((source, kind) in storyBoards) {
            val id = "campaign-choice-v2:$source"
            assertTrue(id, id in storyIds)
            assertFalse("campaign-choice-v1:$source" in storyIds)
            assertEquals(listOf(kind), catalog.policies.getValue(id).choiceGameKinds.values.toList())
        }
        assertEquals(DeedGameKind.PIPES, catalog.policies.getValue("figma-2326-112-v3").choiceGameKinds["figma-2326-112-v3:work"])
        assertEquals(DeedGameKind.PRECISION, catalog.policies.getValue("figma-2326-352-v3").choiceGameKinds["figma-2326-352-v3:work"])
        assertTrue(catalog.dailyEventPool.containsAll(listOf("figma-2326-112-v3", "figma-2326-352-v3")))
        val current = catalog.deedPool + catalog.dailyEventPool + storyIds
        val kinds = current.flatMap { id -> catalog.policies.getValue(id).let { listOfNotNull(it.deedGameKind) + it.choiceGameKinds.values } }.toSet()
        assertEquals(DeedGameKind.entries.toSet(), kinds)
        assertTrue(catalog.policies.getValue(LORE_PLATE_CLEANING).choiceGameKinds.isEmpty())
        assertFalse(catalog.policies.getValue("campaign-choice-v2:G2.03").choiceGameKinds.containsKey("campaign-choice-v2:G2.03:detour"))
        // Retained historical story versions are valid even though only the new IDs are in current acts.
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
    }

    @Test fun boardRevisionsPreserveEveryAuthoredEffectAndDoNotMigrateExistingGames() {
        for ((oldId, newId) in revisions) {
            val oldEvent = catalog.content.events.single { it.id == oldId }
            assertEquals(oldEvent, catalog.content.events.single { it.id == newId }.copy(id = oldId))
            val old = catalog.policies.getValue(oldId)
            val next = catalog.policies.getValue(newId)
            val oldChoices = catalog.content.choices.filter { it.eventId == oldId }
            val nextChoices = catalog.content.choices.filter { it.eventId == newId }
            assertEquals(oldChoices.size, nextChoices.size)
            for ((before, after) in oldChoices.zip(nextChoices)) {
                assertEquals(before, after.copy(id = before.id, eventId = oldId))
                assertEquals(old.energyFor(before.id), next.energyFor(after.id))
                assertEquals(old.factsByChoiceId[before.id], next.factsByChoiceId[after.id])
                assertEquals(old.choiceDestinations[before.id], next.choiceDestinations[after.id])
                assertEquals(before.id in old.feedsPetChoiceIds, after.id in next.feedsPetChoiceIds)
                assertEquals(before.id in old.disabledChoiceIds, after.id in next.disabledChoiceIds)
                assertEquals(catalog.cards.getValue(oldId).summaryByChoiceId[before.id], catalog.cards.getValue(newId).summaryByChoiceId[after.id])
                assertEquals(catalog.content.choiceItemEffects.filter { it.choiceId == before.id }.map { Triple(it.position, it.itemId, it.operation) },
                    catalog.content.choiceItemEffects.filter { it.choiceId == after.id }.map { Triple(it.position, it.itemId, it.operation) })
            }
            assertEquals(old.copy(deedGameKind = next.deedGameKind, choiceEnergyCosts = next.choiceEnergyCosts,
                choiceGameKinds = next.choiceGameKinds, factsByChoiceId = next.factsByChoiceId,
                choiceDestinations = next.choiceDestinations, feedsPetChoiceIds = next.feedsPetChoiceIds,
                disabledChoiceIds = next.disabledChoiceIds, scheduling = next.scheduling), next)
            assertEquals(old.scheduling.copy(family = old.scheduling.family ?: oldId,
                previousEventIds = old.scheduling.previousEventIds + oldId), next.scheduling)
            assertFalse(oldId, catalog.eventReplacements.containsKey(oldId))
            assertFalse(newId, newId in catalog.eventReplacements.values)
        }
    }

    @Test fun completingEitherStoryVersionSatisfiesBothVersionsAndDownstreamConditions() {
        val initial = createInitialGameState()
        for (source in storyBoards.keys) {
            val oldId = "campaign-choice-v1:$source"
            val newId = "campaign-choice-v2:$source"
            for (id in listOf(oldId, newId)) {
                for (choice in catalog.content.choices.filter { it.eventId == id }) {
                    val state = initial.copy(story = initial.story.copy(decisions = listOf(StoryDecision("saved", choice.id))))
                    val progress = catalog.storyProgress(state)
                    assertTrue(choice.id, progress.completed(oldId))
                    assertTrue(choice.id, progress.completed(newId))
                    assertTrue(choice.id, progress.meets(StoryCondition.EventCompleted(oldId)))
                    assertTrue(choice.id, progress.meets(StoryCondition.EventCompleted(newId)))
                }
            }
        }
    }
}
