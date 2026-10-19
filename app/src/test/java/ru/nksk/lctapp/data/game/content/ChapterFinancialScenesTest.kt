package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.engine.EventFactory
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.story.StoryDecision

class ChapterFinancialScenesTest {
    private val catalog = bundledGameCatalog()

    @Test fun comparisonOpensAfterIntroductionAndUsesTheStoryBoard() {
        val first = checkNotNull(catalog.storyCampaign).acts.first()
        assertEquals(CHAPTER_SHOP_EVENT, first.eventIds[1])
        val base = createInitialGameState()
        val condition = catalog.policies.getValue(CHAPTER_SHOP_EVENT).condition
        assertFalse(catalog.storyProgress(base).meets(condition))
        val introduced = base.copy(story = base.story.copy(decisions = listOf(
            StoryDecision("intro", "campaign-choice-v1:G1.01:continue"))))
        assertTrue(catalog.storyProgress(introduced).meets(condition))
        assertEquals(DeedGameKind.SHOPPING_COMPARISON,
            catalog.policies.getValue(CHAPTER_SHOP_EVENT).choiceGameKinds["$CHAPTER_SHOP_EVENT:compare"])
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
    }

    @Test fun sharedSupplyExercisesNeverDebitWalletOrGrantPersonalGoalItems() {
        for (id in listOf(CHAPTER_SHOP_EVENT, CHAPTER_PACK_EVENT)) {
            val event = catalog.content.events.single { it.id == id }
            val choice = catalog.content.choices.single { it.eventId == id }
            assertEquals(0L, event.moneyDeltaOnStart)
            assertEquals(0L, choice.moneyDelta)
            assertEquals(0, catalog.policies.getValue(id).energyFor(choice.id))
            assertTrue(catalog.content.eventItemEffects.none { it.eventId == id })
            assertTrue(catalog.content.choiceItemEffects.none { it.choiceId == choice.id })
        }
        assertEquals(DeedGameKind.TRIP_PACKING,
            catalog.policies.getValue(CHAPTER_PACK_EVENT).choiceGameKinds["$CHAPTER_PACK_EVENT:pack"])
    }

    @Test fun comparisonIsAlsoARealShortJobWithoutChangingExistingRewardsOrFacts() {
        val oldId = "figma-2163-2-v1:balance-v2"
        val newId = "$oldId:game-v3"
        assertTrue(newId in catalog.deedPool)
        assertFalse(oldId in catalog.deedPool)
        val old = catalog.content.choices.single { it.eventId == oldId }
        val current = catalog.content.choices.single { it.eventId == newId }
        assertEquals(old.moneyDelta, current.moneyDelta)
        assertEquals(catalog.policies.getValue(oldId).energyCost, catalog.policies.getValue(newId).energyCost)
        assertEquals(catalog.policies.getValue(oldId).factsByChoiceId[old.id],
            catalog.policies.getValue(newId).factsByChoiceId[current.id])
        assertEquals(DeedGameKind.COMPARISON, catalog.policies.getValue(oldId).deedGameKind)
        assertEquals(DeedGameKind.SHOPPING_COMPARISON, catalog.policies.getValue(newId).deedGameKind)
    }

    @Test fun deferringPreparationCannotSkipComparisonOrArrivalBeforeTheNewFinale() {
        val base = createInitialGameState()
        val kit = catalog.goals.first().itemIds.mapIndexed { index, id -> OwnedItem("kit-$index", id) }
        val before = base.copy(selectedGoalId = STARS_GOAL, ownedItems = base.ownedItems + kit,
            story = base.story.copy(decisions = listOf(
                StoryDecision("intro", "campaign-choice-v1:G1.01:continue"),
                StoryDecision("signal", "campaign-choice-v2:G1.11:continue"))))
        assertFalse(catalog.storyProgress(before).meets(catalog.policies.getValue(CHAPTER_PACK_EVENT).condition))
        val compared = before.copy(story = before.story.copy(decisions = before.story.decisions +
            StoryDecision("compared", "$CHAPTER_SHOP_EVENT:compare")))
        val progress = catalog.storyProgress(compared)
        assertTrue(progress.meets(catalog.policies.getValue(CHAPTER_PACK_EVENT).condition))
        assertFalse(progress.eligible(CURRENT_CHAPTER_BOAT_EVENT))
        assertFalse(progress.eligible(CURRENT_CHAPTER_FINALE_EVENT))
        assertNotEquals(CURRENT_CHAPTER_FINALE_EVENT, progress.nextEvent(setOf(CHAPTER_PACK_EVENT)))
        val arrived = compared.copy(story = compared.story.copy(decisions = compared.story.decisions +
            StoryDecision("arrival", "$CURRENT_CHAPTER_BOAT_EVENT:sail")))
        assertTrue(catalog.storyProgress(arrived).eligible(CURRENT_CHAPTER_FINALE_EVENT))
        assertTrue(catalog.policies.getValue("campaign-choice-v1:G1.12").finishesStoryAct)
        assertFalse(catalog.eventReplacements.containsKey("campaign-choice-v1:G1.12"))
        val legacyFinished = before.copy(story = before.story.copy(decisions = before.story.decisions +
            StoryDecision("old-finale", "campaign-choice-v1:G1.12:continue")))
        assertTrue(catalog.storyProgress(legacyFinished).completed(CURRENT_CHAPTER_FINALE_EVENT))
    }
}
