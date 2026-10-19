package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.story.StoryDecision

class ChapterBoatJourneyContentTest {
    @Test fun journeyWaitsForAPaidTicketAndTheObservationStory() {
        val catalog = bundledGameCatalog()
        val condition = catalog.policies.getValue(CHAPTER_BOAT_EVENT).condition
        val base = createInitialGameState()
        val ticket = base.copy(ownedItems = base.ownedItems + OwnedItem("ticket", "stargazing-trip-v1"))
        val ready = ticket.copy(story = ticket.story.copy(decisions = ticket.story.decisions +
            StoryDecision("reply", "campaign-choice-v2:G1.11:continue")))
        assertFalse(catalog.storyProgress(base).meets(condition))
        assertFalse(catalog.storyProgress(ticket).meets(condition))
        assertTrue(catalog.storyProgress(ready).meets(condition))
        assertFalse(catalog.storyProgress(ready.copy(ownedItems = base.ownedItems)).meets(condition))
    }

    @Test fun sailingDoesNotSellTheSameTicketAgainOrGrantAnEarningReward() {
        val catalog = bundledGameCatalog()
        for (id in listOf(CHAPTER_BOAT_EVENT, CURRENT_CHAPTER_BOAT_EVENT)) {
            val event = catalog.content.events.single { it.id == id }
            val choice = catalog.content.choices.single { it.eventId == event.id }
            val policy = catalog.policies.getValue(event.id)
            assertEquals(0L, event.moneyDeltaOnStart)
            assertEquals(0L, choice.moneyDelta)
            assertEquals(0, policy.energyFor(choice.id))
            assertEquals(DeedGameKind.BOAT, policy.choiceGameKinds[choice.id])
        }
        assertEquals(30L, catalog.content.items.single { it.id == "stargazing-trip-v1" }.priceCoins)
    }

    @Test fun arrivedJourneyIsNotScheduledAgainAndLegacyFinaleIsPreserved() {
        val catalog = bundledGameCatalog()
        val chapter = checkNotNull(catalog.storyCampaign).acts.first()
        assertEquals(CURRENT_CHAPTER_BOAT_EVENT, chapter.eventIds[chapter.eventIds.lastIndex - 1])
        assertEquals(CHAPTER_PACK_EVENT, chapter.eventIds[chapter.eventIds.lastIndex - 2])
        assertEquals(CURRENT_CHAPTER_FINALE_EVENT, chapter.finaleId)
        val base = createInitialGameState()
        val arrived = base.copy(story = base.story.copy(decisions = base.story.decisions +
            StoryDecision("arrival", "$CHAPTER_BOAT_EVENT:sail")))
        assertTrue(catalog.storyProgress(arrived).completed(CHAPTER_BOAT_EVENT))
        assertTrue(catalog.storyProgress(arrived).completed(CURRENT_CHAPTER_BOAT_EVENT))
        assertTrue(catalog.storyProgress(arrived).completed(CHAPTER_PACK_EVENT))
        assertTrue("stargazing_trip_arrived" in catalog.storyProgress(arrived).facts)
    }

    @Test fun onlyNewDeparturesRequirePackingWhileOfferedLegacyBoatRemainsPlayable() {
        val catalog = bundledGameCatalog()
        val base = createInitialGameState()
        val ready = base.copy(ownedItems = base.ownedItems + OwnedItem("ticket", "stargazing-trip-v1"),
            story = base.story.copy(decisions = listOf(StoryDecision("signal", "campaign-choice-v2:G1.11:continue"),
                StoryDecision("compare", "$CHAPTER_SHOP_EVENT:compare"))))
        assertTrue(catalog.storyProgress(ready).meets(catalog.policies.getValue(CHAPTER_BOAT_EVENT).condition))
        assertTrue(catalog.storyProgress(ready).meets(catalog.policies.getValue(CHAPTER_PACK_EVENT).condition))
        assertFalse(catalog.storyProgress(ready).meets(catalog.policies.getValue(CURRENT_CHAPTER_BOAT_EVENT).condition))
        val packed = ready.copy(story = ready.story.copy(decisions = ready.story.decisions +
            StoryDecision("packed", "$CHAPTER_PACK_EVENT:pack")))
        assertTrue(catalog.storyProgress(packed).meets(catalog.policies.getValue(CURRENT_CHAPTER_BOAT_EVENT).condition))
        assertTrue(CHAPTER_BOAT_EVENT in catalog.policies.getValue(CURRENT_CHAPTER_BOAT_EVENT).scheduling.previousEventIds)
        assertFalse(catalog.eventReplacements.containsKey(CHAPTER_BOAT_EVENT))
    }
}
