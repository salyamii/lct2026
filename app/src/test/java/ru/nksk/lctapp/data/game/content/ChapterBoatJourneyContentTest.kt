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
        val event = catalog.content.events.single { it.id == CHAPTER_BOAT_EVENT }
        val choice = catalog.content.choices.single { it.eventId == event.id }
        val policy = catalog.policies.getValue(event.id)
        assertEquals(0L, event.moneyDeltaOnStart)
        assertEquals(0L, choice.moneyDelta)
        assertEquals(0, policy.energyFor(choice.id))
        assertEquals(DeedGameKind.BOAT, policy.choiceGameKinds[choice.id])
        assertEquals(30L, catalog.content.items.single { it.id == "stargazing-trip-v1" }.priceCoins)
    }

    @Test fun arrivedJourneyIsNotScheduledAgainAndLegacyFinaleIsPreserved() {
        val catalog = bundledGameCatalog()
        val chapter = checkNotNull(catalog.storyCampaign).acts.first()
        assertEquals(CHAPTER_BOAT_EVENT, chapter.eventIds[chapter.eventIds.lastIndex - 1])
        assertEquals("campaign-choice-v1:G1.12", chapter.finaleId)
        val base = createInitialGameState()
        val arrived = base.copy(story = base.story.copy(decisions = base.story.decisions +
            StoryDecision("arrival", "$CHAPTER_BOAT_EVENT:sail")))
        assertTrue(catalog.storyProgress(arrived).completed(CHAPTER_BOAT_EVENT))
        assertTrue("stargazing_trip_arrived" in catalog.storyProgress(arrived).facts)
    }
}
