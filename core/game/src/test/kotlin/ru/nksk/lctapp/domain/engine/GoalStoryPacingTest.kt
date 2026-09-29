package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

/** A collected chapter kit changes pacing, never the outcomes of the remaining story. */
class GoalStoryPacingTest {
    @Test fun lastPurchasePrioritizesLoreInAnAlreadySavedEverydayPlan() = runTest {
        val f = Fixture(collected = false, pending = listOf("quiet1", "job", "intro", "quiet2"))
        val before = f.state
        assertFalse(f.progress.goalReadyForStory)

        f.send(EngineCommand.BuyGoalItem("goal", "part"))
        val purchased = f.state
        assertTrue(f.progress.goalReadyForStory)
        assertEquals(before.story.decisions, purchased.story.decisions)
        assertTrue(purchased.completedGoalProjects.isEmpty())
        f.send(EngineCommand.OpenNextEvent)

        assertEquals("intro", f.day.currentEvent!!.eventId)
        assertEquals(purchased.economy, f.state.economy)
        assertEquals(purchased.ownedItems, f.state.ownedItems)
        assertEquals(purchased.story.decisions, f.state.story.decisions)
        assertTrue(f.state.eventHistory.none { it.eventId in setOf("quiet1", "quiet2", "job") })
        assertTrue(f.day.events.none { it.eventId in setOf("quiet1", "quiet2", "job") })
    }

    @Test fun collectedKitContinuesPastTwoLoreCardsEvenWhenTheDayWasReadyToEnd() = runTest {
        val f = Fixture(completed = listOf("intro", "second"), pending = emptyList(), phase = DayPhase.READY_TO_END)
        val before = f.state

        assertEquals(EngineCommand.OpenNextEvent, f.session.advanceCommand(before))
        assertEquals(before, f.state)
        f.send(EngineCommand.OpenNextEvent)

        assertEquals("third", f.day.currentEvent!!.eventId)
        assertEquals(before.story.decisions, f.state.story.decisions)
        assertEquals(before.engine!!.day, f.day.day)
        f.completeCurrent()
        f.send(EngineCommand.OpenNextEvent)
        assertEquals("final", f.day.currentEvent!!.eventId)
        assertEquals(1, f.day.day)
    }

    @Test fun dayStepPacingDoesNotCreateEmptyDaysButRealStoryPrerequisitesStillApply() = runTest {
        val f = Fixture(completed = listOf("intro", "second"), pending = emptyList())
        f.repo.update { it.copy(engine = it.engine!!.copy(steps = 0)) }
        assertTrue(f.progress.eligible("third"))
        f.send(EngineCommand.OpenNextEvent)
        assertEquals("third", f.day.currentEvent!!.eventId)

        val missingPredecessor = Fixture(completed = listOf("intro"), pending = emptyList())
        assertFalse(missingPredecessor.progress.eligible("third"))
        assertFalse(missingPredecessor.progress.eligible("final"))
    }

    @Test fun focusedPlanKeepsCarriedLoreIdentityWithoutAddingEverydayFillers() = runTest {
        val f = Fixture(pending = emptyList(), phase = DayPhase.FINISHED)
        val lore = occurrence("intro", EventStatus.CARRIED, "unfinished-lore")
        f.repo.update { it.copy(engine = it.engine!!.copy(events = listOf(
            occurrence("quiet1", EventStatus.CARRIED), lore))) }
        val before = f.state
        val plan = f.catalog.plan(before)

        assertEquals(listOf("intro"), plan)
        assertEquals(plan, f.catalog.plan(before))
        assertEquals(before, f.state)
        f.send(EngineCommand.BeginDay("day", plan))

        assertEquals(listOf(lore.copy(status = EventStatus.PENDING)), f.day.events)
        assertEquals(2, f.day.day)
        assertEquals(before.story.decisions, f.state.story.decisions)
        assertTrue(f.state.eventHistory.isEmpty())
    }

    @Test fun focusedContinuationNeverPreemptsAnActiveMiniGameOrItsResult() = runTest {
        for (status in listOf(EventStatus.ACTIVE, EventStatus.RESULT)) {
            val f = Fixture(pending = emptyList())
            val offer = DeedOffer("accepted-job", "job", 3)
            val game = EventOccurrence("in-progress", "job", EventOrigin.DEED, status, offer.id)
            f.repo.update { it.copy(story = it.story.copy(activeEventId = "job"),
                engine = it.engine!!.copy(events = listOf(game), deeds = listOf(offer))) }
            val before = f.state

            assertNull(f.session.advanceCommand(before))
            assertEquals(BlockReason.EventInProgress, f.blocked(EngineCommand.OpenNextEvent))
            assertEquals(before, f.state)
        }
    }

    @Test fun exhaustedUnfinishedLoreResumesItsOriginalOccurrenceAfterSleep() = runTest {
        val f = Fixture(pending = emptyList())
        val lore = occurrence("intro", EventStatus.ACTIVE, "opened-lore")
        f.repo.update { it.copy(story = it.story.copy(activeEventId = "intro"),
            engine = it.engine!!.copy(events = listOf(lore), energy = 0)) }
        f.send(EngineCommand.FinishDayFromEvent(lore.id))
        assertEquals(EventStatus.CARRIED_ACTIVE, f.day.events.single().status)
        assertTrue(f.state.story.decisions.isEmpty())

        f.send(EngineCommand.BeginDay("day", f.catalog.plan(f.state)))
        assertEquals(lore.copy(status = EventStatus.PAUSED), f.day.events.single())
        f.send(EngineCommand.OpenNextEvent)

        assertEquals(lore, f.day.currentEvent)
        assertEquals(2, f.day.day)
        assertEquals(5, f.day.energy)
        assertTrue(f.state.story.decisions.isEmpty())
    }

    @Test fun anAlreadyPresentedBlockingProblemMustBeResolvedBeforeLore() = runTest {
        val f = Fixture(pending = listOf("intro"))
        val problem = occurrence("problem", EventStatus.PAUSED, "shown-problem")
        f.repo.update { it.copy(engine = it.engine!!.copy(events = listOf(problem) + it.engine.events)) }
        assertFalse(f.progress.eligible("intro"))

        f.send(EngineCommand.OpenNextEvent)
        assertEquals(problem.copy(status = EventStatus.ACTIVE), f.day.currentEvent)
        assertTrue(f.state.story.decisions.isEmpty())
        f.completeCurrent()
        f.send(EngineCommand.OpenNextEvent)

        assertEquals("intro", f.day.currentEvent!!.eventId)
        assertEquals(listOf("problem:done"), f.state.story.decisions.map { it.choiceId })
    }

    @Test fun neededDeedIsOfferedWhenItsFactIsTheOnlyRemainingLorePrerequisite() {
        val f = Fixture(completed = listOf("intro", "second"), pending = emptyList(), requiresWork = true,
            phase = DayPhase.FINISHED)
        assertFalse(f.progress.eligible("third"))
        assertEquals(listOf("job"), f.catalog.plan(f.state))
        assertFalse("tuned" in f.progress.facts)
    }

    @Test fun existingNeededDeedStartsInsteadOfIssuingAnotherProposalOrEverydayEvent() = runTest {
        val f = Fixture(completed = listOf("intro", "second"), pending = listOf("quiet1"), requiresWork = true)
        val offer = DeedOffer("existing-offer", "job", 2)
        f.repo.update { it.copy(engine = it.engine!!.copy(deeds = listOf(offer))) }
        val before = f.state

        f.send(EngineCommand.OpenNextEvent)

        val current = f.day.currentEvent!!
        assertEquals("job", current.eventId)
        assertEquals(EventOrigin.DEED, current.origin)
        assertEquals(offer.id, current.deedOfferId)
        assertEquals(EventStatus.ACTIVE, current.status)
        assertEquals(listOf(offer), f.day.deeds)
        assertEquals(before.economy, f.state.economy)
        assertEquals(before.story.decisions, f.state.story.decisions)
        assertFalse("tuned" in f.progress.facts)
    }

    @Test fun nextMorningCanResumeAnExistingPrerequisiteWithoutInventingFourNewCards() = runTest {
        val f = Fixture(completed = listOf("intro", "second"), pending = emptyList(), requiresWork = true,
            phase = DayPhase.FINISHED)
        val offer = DeedOffer("tomorrow-offer", "job", 3)
        f.repo.update { it.copy(engine = it.engine!!.copy(deeds = listOf(offer))) }
        val plan = f.catalog.plan(f.state)
        assertTrue(plan.isEmpty())

        f.send(EngineCommand.BeginDay("day", plan))
        f.send(EngineCommand.OpenNextEvent)

        assertEquals(2, f.day.day)
        assertEquals(offer.id, f.day.currentEvent!!.deedOfferId)
        assertEquals(EventOrigin.DEED, f.day.currentEvent!!.origin)
        assertEquals(listOf(offer), f.day.deeds)
        assertEquals(listOf("intro:done", "second:done"), f.state.story.decisions.map { it.choiceId })
    }

    @Test fun pausedOptionalCardDoesNotPreventRestWhenTheNextLoreNeedsMoreEnergy() = runTest {
        val f = Fixture(completed = listOf("intro", "second"), pending = emptyList(), storyEnergy = 2)
        val optional = occurrence("quiet1", EventStatus.PAUSED, "paused-optional")
        f.repo.update { it.copy(engine = it.engine!!.copy(energy = 1, events = it.engine.events + optional)) }
        val before = f.state

        assertEquals(EngineCommand.FinishDay, f.session.advanceCommand(before))
        assertEquals(before, f.state)
        f.send(EngineCommand.FinishDay)

        assertEquals(DayPhase.FINISHED, f.day.phase)
        assertEquals(before.story.decisions, f.state.story.decisions)
        assertEquals(before.economy, f.state.economy)
        assertEquals(optional.copy(status = EventStatus.CARRIED_ACTIVE),
            f.day.events.single { it.id == optional.id })
        assertEquals(EventStatus.CARRIED, f.day.events.single { it.eventId == "third" }.status)
    }

    @Test fun legacyOfferFromTheSameDeedFamilyIsResumedWithoutADuplicateProposal() = runTest {
        val f = Fixture(completed = listOf("intro", "second"), pending = listOf("quiet1"), requiresWork = true)
        val legacy = DeedOffer("legacy-offer", "old-job", 3)
        f.repo.update { it.copy(engine = it.engine!!.copy(deeds = listOf(legacy))) }
        val before = f.state
        assertEquals(listOf("job"), f.progress.goalDeedIds())
        assertTrue(f.progress.isGoalDeed(legacy.eventId))

        f.send(EngineCommand.OpenNextEvent)

        assertEquals("old-job", f.day.currentEvent!!.eventId)
        assertEquals(legacy.id, f.day.currentEvent!!.deedOfferId)
        assertEquals(EventOrigin.DEED, f.day.currentEvent!!.origin)
        assertEquals(listOf(legacy), f.day.deeds)
        assertTrue(f.day.events.none { it.eventId == "job" })
        assertEquals(before.economy, f.state.economy)
        assertEquals(before.story.decisions, f.state.story.decisions)
        assertEquals(before.engine!!.steps, f.day.steps)
        assertFalse("tuned" in f.progress.facts)
    }

    @Test fun completeKitDoesNotRemoveFoodEnergyOrFinancialPracticeGuards() = runTest {
        val hungry = Fixture(pending = listOf("intro"))
        hungry.repo.update { it.copy(engine = it.engine!!.copy(ateToday = false, steps = 3)) }
        val beforeFood = hungry.state
        assertEquals(BlockReason.MustEat, hungry.blocked(EngineCommand.OpenNextEvent))
        assertEquals(beforeFood, hungry.state)

        val tired = Fixture(pending = listOf("intro"))
        tired.repo.update { it.copy(engine = it.engine!!.copy(energy = 0)) }
        assertEquals(BlockReason.MustSleep, tired.blocked(EngineCommand.OpenNextEvent))
        assertEquals(EngineCommand.FinishDay, tired.session.advanceCommand(tired.state))

        val practice = Fixture(completed = listOf("intro", "second", "third"), pending = emptyList())
        val period = FinancialPeriod("current-period", "goal", 1, 1, 100, 0, needsProvided = true)
        practice.repo.update { it.copy(financial = FinancialProgress(period.id, listOf(period))) }
        val beforePractice = practice.state
        assertTrue(practice.blocked(EngineCommand.OpenNextEvent) is BlockReason.FinancialPracticeRequired)
        assertEquals(beforePractice, practice.state)
    }

    @Test fun incompleteOrDifferentChapterKitKeepsTheOrdinaryPacing() {
        val incomplete = Fixture(collected = false)
        assertFalse(incomplete.progress.goalReadyForStory)
        assertEquals(4, incomplete.catalog.plan(incomplete.state).size)

        val f = Fixture()
        val wrongChapter = f.state.copy(selectedGoalId = "next-goal",
            ownedItems = listOf(OwnedItem("future-part", "next-part")))
        assertFalse(f.catalog.storyProgress(wrongChapter).goalReadyForStory)
        assertEquals(4, f.catalog.plan(wrongChapter).size)
        assertFalse(f.catalog.storyProgress(f.state.copy(selectedGoalId = null)).goalReadyForStory)
    }

    @Test fun finishingTheChapterClearsFastPacingAndKeepsOwnedItemsForTheNextChapter() = runTest {
        val f = Fixture(completed = listOf("intro", "second", "third"), pending = listOf("quiet1"))
        val owned = f.state.ownedItems
        f.send(EngineCommand.OpenNextEvent)
        assertEquals("final", f.day.currentEvent!!.eventId)
        f.completeCurrent()

        assertEquals("next-day", f.state.story.currentDayId)
        assertEquals(listOf("goal"), f.state.completedGoalProjects.map { it.goalId })
        assertEquals(owned, f.state.ownedItems)
        assertNull(f.state.selectedGoalId)
        assertFalse(f.progress.goalReadyForStory)
        val nextChapter = f.state.copy(selectedGoalId = "next-goal")
        val plan = f.catalog.plan(nextChapter)
        assertEquals(4, plan.size)
        assertTrue("next-intro" in plan)
        assertTrue(plan.any { f.catalog.policies.getValue(it).storyActId == null })
        assertFalse(f.catalog.storyProgress(nextChapter).goalReadyForStory)
    }

    private class Fixture(
        collected: Boolean = true,
        completed: List<String> = emptyList(),
        pending: List<String> = listOf("intro", "quiet1", "quiet2", "quiet3"),
        phase: DayPhase = DayPhase.RUNNING,
        requiresWork: Boolean = false,
        storyEnergy: Int = 0,
    ) {
        private val lore = listOf("intro", "second", "third", "final", "next-intro", "next-final")
        private val everyday = listOf("quiet1", "quiet2", "quiet3", "quiet4", "problem")
        private val jobs = listOf("job", "old-job")
        private val events = (lore + everyday + jobs).map { id -> EventDefinition(id,
            when (id) { in jobs -> EventType.EARNING; in lore -> EventType.STORY; else -> EventType.RANDOM },
            id, id, null, null, null, 0, null, if (id == "final") "next-chapter" else null) }
        private val content = StoryContent(
            chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal"),
                ChapterDefinition("next-chapter", "Next chapter", "next-goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1), GameDayDefinition("next-day", "next-chapter", 1)),
            goals = listOf(GoalDefinition("goal", "Goal", ""), GoalDefinition("next-goal", "Next goal", "")),
            events = events,
            choices = events.map { EventChoiceDefinition("${it.id}:done", it.id, 0, "Continue",
                if (it.id in jobs) 5 else 0, null, null, GoalImpact.NEUTRAL) },
            items = listOf(ItemDefinition("part", "Part", "", priceCoins = 10),
                ItemDefinition("next-part", "Next part", "", priceCoins = 10)),
            requiredItems = listOf(GoalRequiredItem("goal", "part"), GoalRequiredItem("next-goal", "next-part")),
        )
        private val policies = events.associate { event -> event.id to EventPolicy(
            energyCost = when (event.id) { in jobs -> 1; in lore -> storyEnergy; else -> 0 },
            storyActId = when (event.id) {
                "intro", "second", "third", "final" -> "act"
                "next-intro", "next-final" -> "next-act"
                else -> null
            },
            finishesStoryAct = event.id in listOf("final", "next-final"),
            chapterEntryDayId = "next-day".takeIf { event.id == "final" },
            deedGameKind = DeedGameKind.PRECISION.takeIf { event.id in jobs },
            condition = when (event.id) {
                "second" -> StoryCondition.EventCompleted("intro")
                "third" -> StoryCondition.All(listOf(StoryCondition.EventCompleted("second"),
                    StoryCondition.DayStepsAtLeast(2)) + if (requiresWork) listOf(StoryCondition.Fact("tuned")) else emptyList())
                "final" -> StoryCondition.EventCompleted("third")
                "next-final" -> StoryCondition.EventCompleted("next-intro")
                else -> StoryCondition.Always
            },
            factsByChoiceId = if (event.id in jobs) mapOf("${event.id}:done" to setOf("tuned")) else emptyMap(),
            scheduling = EventSchedulingPolicy(blocksStoryUntilResolved = event.id == "problem",
                family = "telescope-job".takeIf { event.id in jobs },
                previousEventIds = if (event.id == "job") setOf("old-job") else emptySet()),
        ) }
        val catalog = GameCatalog(content, policies, emptyMap(), EngineRules("pacing-test", 5, 3, 1),
            listOf(MealDefinition("meal", 5, null)), "day", "intro", listOf("job"),
            dailyEventPool = everyday,
            goals = listOf(GoalCampaign("goal", "intro", listOf("part")),
                GoalCampaign("next-goal", "next-intro", listOf("next-part"))),
            storyCampaign = StoryCampaign(listOf(
                StoryAct("act", "Act", "day", lore.take(4), "final", goalId = "goal"),
                StoryAct("next-act", "Next act", "next-day", lore.takeLast(2), "next-final", goalId = "next-goal")),
                deedHints = if (requiresWork) listOf(StoryDeedHint(StoryCondition.All(listOf(
                    StoryCondition.EventCompleted("second"), StoryCondition.Not(StoryCondition.Fact("tuned")))), "job")) else emptyList()),
        )
        private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(100, 0, 0, 0), availableBalance = 100),
            StoryState("day", null, null, completed.map { StoryDecision("completed:$it:decision", "$it:done") }),
            0, 0, if (collected) listOf(OwnedItem("owned-part", "part")) else emptyList(),
            engine = EngineState("pacing-test", 0, 1, phase, completed.size, 5, true, null, 100,
                completed.map { occurrence(it, EventStatus.COMPLETED, "completed:$it") } +
                    pending.map { occurrence(it, EventStatus.PENDING) }, emptyList()),
            selectedGoalId = "goal", selectedSavingItemId = "part".takeUnless { collected },
        )
        val repo = Repository(initial)
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        val state get() = repo.value
        val day get() = checkNotNull(state.engine)
        val progress get() = catalog.storyProgress(state)
        private var sequence = 0
        private fun request(command: EngineCommand) = EngineRequest("pacing:${++sequence}", day.revision, command)
        suspend fun send(command: EngineCommand) {
            val result = session.engine.dispatch(request(command))
            assertTrue("$command -> $result", result is EngineResult.Applied)
        }
        suspend fun blocked(command: EngineCommand): BlockReason {
            val result = session.engine.dispatch(request(command))
            assertTrue("$command -> $result", result is EngineResult.Blocked)
            return (result as EngineResult.Blocked).reason
        }
        suspend fun completeCurrent() {
            val current = checkNotNull(day.currentEvent)
            send(EngineCommand.CompleteEvent(current.id, "${current.eventId}:done"))
        }
    }

    private class Repository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        val value get() = flow.value
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { flow.value = it }
    }

    companion object {
        private fun occurrence(eventId: String, status: EventStatus, id: String = "saved:$eventId") =
            EventOccurrence(id, eventId, EventOrigin.SCHEDULE, status)
    }
}
