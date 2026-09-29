package ru.nksk.lctapp.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

class EventFactoryStoryVersionTest {
    private val ids = listOf("old-work", "new-work", "first-finale", "second-finale")
    private val content = StoryContent(
        chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
        days = listOf(GameDayDefinition("day", "chapter", 1)),
        goals = listOf(GoalDefinition("goal", "Goal", "")),
        events = ids.map { EventDefinition(it, EventType.STORY, it, it, null, null, null, 0, null, null) },
        choices = ids.map { EventChoiceDefinition("$it:done", it, 0, "Continue", 0, null, null, GoalImpact.NEUTRAL) },
    )
    private val oldPolicy = EventPolicy(1, storyActId = "first",
        choiceGameKinds = mapOf("old-work:done" to DeedGameKind.PRECISION))
    private val policies = mapOf(
        "old-work" to oldPolicy,
        "new-work" to oldPolicy.copy(choiceGameKinds = mapOf("new-work:done" to DeedGameKind.PIPES),
            scheduling = EventSchedulingPolicy(family = "old-work", previousEventIds = setOf("old-work"))),
        "first-finale" to EventPolicy(0, storyActId = "first", finishesStoryAct = true),
        "second-finale" to EventPolicy(0, storyActId = "second", finishesStoryAct = true),
    )
    private val campaign = StoryCampaign(listOf(
        StoryAct("first", "First", "day", listOf("new-work", "first-finale"), "first-finale"),
        StoryAct("second", "Second", "day", listOf("second-finale"), "second-finale"),
    ), completionAliases = mapOf("new-work" to setOf("old-work:done")))

    @Test fun explicitPreviousVersionInTheSameActKeepsItsOriginalPolicyAndChoice() {
        val factory = EventFactory(content, policies, emptyList(), campaign = campaign)

        assertEquals(oldPolicy, factory.policy("old-work"))
        assertEquals(DeedGameKind.PRECISION, factory.policy("old-work").choiceGameKinds["old-work:done"])
        assertEquals(DeedGameKind.PIPES, factory.policy("new-work").choiceGameKinds["new-work:done"])
        assertEquals("old-work", factory.create("old-work", "saved-occurrence").eventId)
    }

    @Test fun matchingFamilyAndCompletionAliasWithoutAnExplicitPredecessorAreInsufficient() {
        val changed = policies + ("new-work" to policies.getValue("new-work").copy(
            scheduling = EventSchedulingPolicy(family = "old-work")))

        assertThrows(IllegalArgumentException::class.java) {
            EventFactory(content, changed, emptyList(), campaign = campaign)
        }
    }

    @Test fun previousVersionMustRetainItsDeclaredFamily() {
        val changed = policies + ("new-work" to policies.getValue("new-work").copy(
            scheduling = EventSchedulingPolicy(family = "unrelated", previousEventIds = setOf("old-work"))))

        assertThrows(IllegalArgumentException::class.java) {
            EventFactory(content, changed, emptyList(), campaign = campaign)
        }
    }

    @Test fun anotherActsReplacementDoesNotValidateAnUnlistedOldStory() {
        val changed = policies + ("new-work" to policies.getValue("new-work").copy(storyActId = "second"))
        val moved = campaign.copy(acts = listOf(
            campaign.acts[0].copy(eventIds = listOf("first-finale")),
            campaign.acts[1].copy(eventIds = listOf("new-work", "second-finale")),
        ))

        assertThrows(IllegalArgumentException::class.java) {
            EventFactory(content, changed, emptyList(), campaign = moved)
        }
    }

    @Test fun compatibilityForOrdinaryStoryDoesNotPermitRetiringAnActFinale() {
        val changed = policies + ("old-work" to oldPolicy.copy(finishesStoryAct = true))

        assertThrows(IllegalArgumentException::class.java) {
            EventFactory(content, changed, emptyList(), campaign = campaign)
        }
    }
}
