package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.engine.EventFactory
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.story.StoryDecision

class ChapterDiscoveryScenesTest {
    private val catalog = bundledGameCatalog()
    private val revisions = mapOf(
        "campaign-choice-v1:G1.01" to CHAPTER_INTRO_EVENT,
        LORE_PLATE_CLEANING to LORE_PLATE_GAME_EVENT,
        "campaign-choice-v1:G1.05" to "campaign-choice-v2:G1.05",
        "campaign-choice-v1:G1.07" to "campaign-choice-v2:G1.07",
        "campaign-choice-v1:G1.09" to "campaign-choice-v2:G1.09",
        ARRIVAL_CHAPTER_FINALE_EVENT to CURRENT_CHAPTER_FINALE_EVENT,
    )

    @Test fun firstChapterHasInteractiveCurrentScenesAndNewIntroduction() {
        val first = checkNotNull(catalog.storyCampaign).acts.first()
        assertEquals(CHAPTER_INTRO_EVENT, catalog.introductionId)
        assertEquals(CHAPTER_INTRO_EVENT, first.eventIds.first())
        assertEquals(CURRENT_CHAPTER_FINALE_EVENT, first.finaleId)
        assertEquals(16, first.eventIds.size)
        first.eventIds.forEach { id -> assertTrue(id, catalog.policies.getValue(id).choiceGameKinds.isNotEmpty()) }
        val legacyGoal = catalog.goals.single { it.legacyAcceptanceChoiceIds.isNotEmpty() }
        assertTrue(legacyGoal.legacyAcceptanceChoiceIds.all { id ->
            catalog.content.choices.single { it.id == id }.eventId == legacyGoal.introductionEventId
        })
        assertTrue(catalog.goals.filter { it.legacyAcceptanceChoiceIds.isEmpty() }
            .all { it.introductionEventId == CHAPTER_INTRO_EVENT })
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
    }

    @Test fun newScenesKeepMoneyEnergyFactsAndItemEffectsOfPreviousDefinitions() {
        revisions.forEach { (oldId, currentId) ->
            val oldEvent = catalog.content.events.single { it.id == oldId }
            assertEquals(oldEvent.copy(id = currentId), catalog.content.events.single { it.id == currentId })
            val oldPolicy = catalog.policies.getValue(oldId)
            val currentPolicy = catalog.policies.getValue(currentId)
            assertEquals(oldPolicy.condition, currentPolicy.condition)
            assertEquals(oldPolicy.previousLoreEventId, currentPolicy.previousLoreEventId)
            assertEquals(oldPolicy.finishesStoryAct, currentPolicy.finishesStoryAct)
            assertEquals(oldPolicy.chapterEntryDayId, currentPolicy.chapterEntryDayId)
            catalog.content.choices.filter { it.eventId == oldId }.forEach { choice ->
                val id = "$currentId:${choice.id.removePrefix("$oldId:")}"
                assertEquals(choice.copy(id = id, eventId = currentId), catalog.content.choices.single { it.id == id })
                assertEquals(oldPolicy.energyFor(choice.id), currentPolicy.energyFor(id))
                assertEquals(oldPolicy.factsByChoiceId[choice.id], currentPolicy.factsByChoiceId[id])
                assertEquals(oldPolicy.choiceDestinations[choice.id], currentPolicy.choiceDestinations[id])
                assertEquals(catalog.content.choiceItemEffects.filter { it.choiceId == choice.id }.map { it.itemId to it.operation },
                    catalog.content.choiceItemEffects.filter { it.choiceId == id }.map { it.itemId to it.operation })
            }
            assertFalse("Offered cards must keep their original board", catalog.eventReplacements.containsKey(oldId))
        }
    }

    @Test fun paidAndManualPlateActionsRemainDistinctAndTheirGamesCannotBeSwapped() {
        val policy = catalog.policies.getValue(LORE_PLATE_GAME_EVENT)
        val paid = catalog.content.choices.single { it.id == "$LORE_PLATE_GAME_EVENT:pay" }
        val manual = catalog.content.choices.single { it.id == "$LORE_PLATE_GAME_EVENT:continue" }
        assertEquals(-3L, paid.moneyDelta)
        assertEquals(0L, manual.moneyDelta)
        assertEquals(0, policy.energyFor(paid.id))
        assertEquals(1, policy.energyFor(manual.id))
        assertEquals(DeedGameKind.PLATE_SOLUTION, policy.choiceGameKinds[paid.id])
        assertEquals(DeedGameKind.PLATE_HAND, policy.choiceGameKinds[manual.id])
        assertEquals(policy.factsByChoiceId[paid.id], policy.factsByChoiceId[manual.id])
    }

    @Test fun everyVersionCompletionDirectlyClosesAllOtherVersions() {
        val groups = revisions.map { (old, current) -> setOf(old, current) + when (current) {
            LORE_PLATE_GAME_EVENT -> setOf(LEGACY_LORE_PLATE_CLEANING)
            CURRENT_CHAPTER_FINALE_EVENT -> setOf("campaign-choice-v1:G1.12")
            else -> emptySet()
        } }
        val base = createInitialGameState()
        groups.forEach { versions ->
            val choices = catalog.content.choices.filter { it.eventId in versions && it.id !in catalog.policies.getValue(it.eventId).disabledChoiceIds }
            choices.forEach { choice ->
                val state = base.copy(story = base.story.copy(decisions = listOf(StoryDecision("completed", choice.id))))
                versions.forEach { version -> assertTrue("${choice.id} must close $version", catalog.storyProgress(state).completed(version)) }
            }
        }
    }

    @Test fun optionalFindingsDoNotBecomeNewPrerequisitesForTheFinale() {
        val old = catalog.policies.getValue(ARRIVAL_CHAPTER_FINALE_EVENT)
        val current = catalog.policies.getValue(CURRENT_CHAPTER_FINALE_EVENT)
        assertEquals(old.condition, current.condition)
        assertEquals(old.requiredItemIds, current.requiredItemIds)
        assertEquals(old.previousLoreEventId, current.previousLoreEventId)
        assertEquals(DeedGameKind.CHAPTER_FINALE, current.choiceGameKinds["$CURRENT_CHAPTER_FINALE_EVENT:continue"])
        assertTrue(current.finishesStoryAct)
        assertTrue(setOf("campaign-choice-v1:G1.12", ARRIVAL_CHAPTER_FINALE_EVENT).all { it in current.scheduling.previousEventIds })
    }
}
