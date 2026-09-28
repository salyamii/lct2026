package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState
import ru.nksk.lctapp.domain.minigame.DeedGameKind

class EventSchedulerTest {
    private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL), EconomyState(BudgetPlan(100, 0, 0, 0)),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList())
    private val ids = (1..8).map { "job$it" } + listOf("want1", "want2", "discovery", "resin", "fee1", "fee2")
    private val content = StoryContent(
        chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
        days = listOf(GameDayDefinition("day", "chapter", 1)), goals = listOf(GoalDefinition("goal", "Goal", "")),
        events = (ids + "intro").map { id -> EventDefinition(id, when { id == "intro" -> EventType.STORY;
            id.startsWith("job") -> EventType.EARNING; else -> EventType.RANDOM },
            id, id, null, null, null, 0, null, null) },
        choices = (ids + "intro").map { EventChoiceDefinition("$it:done", it, 0, "Continue", 0, null, null, GoalImpact.NEUTRAL) },
        items = listOf(ItemDefinition("part", "Part", "", priceCoins = 10)),
        requiredItems = listOf(GoalRequiredItem("goal", "part")),
    )
    private val policies = (ids + "intro").associateWith { id -> EventPolicy(if (id.startsWith("job")) 1 else 0,
        condition = if (id == "resin") StoryCondition.EquippedLook("BACKPACK") else StoryCondition.Always,
        scheduling = EventSchedulingPolicy(cooldownDays = if (id.startsWith("want")) 3 else 1, family = id,
            kind = when { id.startsWith("want") -> EverydayEventKind.WANT;
                id.startsWith("fee") || id == "resin" -> EverydayEventKind.UNEXPECTED; else -> EverydayEventKind.DISCOVERY },
            mandatoryUnexpected = id.startsWith("fee"))) }
    private val catalog = GameCatalog(content, policies, emptyMap(), EngineRules("scheduler", 5, 3, 1),
        listOf(MealDefinition("food", 5, null)), "day", "intro", ids.filter { it.startsWith("job") },
        dailyEventPool = ids.filterNot { it.startsWith("job") },
        goals = listOf(GoalCampaign("goal", "intro", listOf("part"))))

    @Test fun ordinaryPlanHasOneEarningDiverseEligibleEventsAndAtMostOneUnexpected() {
        val plan = catalog.plan(initial)
        assertEquals(4, plan.size)
        assertEquals(4, plan.distinct().size)
        assertEquals(1, plan.count { it.startsWith("job") })
        assertTrue(plan.count { policies.getValue(it).scheduling.kind == EverydayEventKind.UNEXPECTED } <= 1)
        assertFalse("No backpack means no backpack accident", "resin" in plan)
        assertEquals(plan, catalog.plan(initial))
        assertTrue(initial.eventHistory.isEmpty())
    }

    @Test fun CooldownAndMandatoryGapUseActualOfferDayRatherThanRepeatedPlanReads() {
        val state = initial.copy(engine = EngineState("scheduler", 0, 4, DayPhase.FINISHED, 0, 5, true,
            null, 100, emptyList(), emptyList()), eventHistory = listOf(
            EventExposure("want1", 4, null, 1), EventExposure("fee1", 4, 4, 1, 1)))
        val plan = catalog.plan(state)
        assertFalse("want1" in plan)
        assertFalse(plan.any { policies.getValue(it).scheduling.mandatoryUnexpected })
        assertTrue("Unseen alternatives stay available", "want2" in plan)
        assertEquals(plan, catalog.plan(state))
    }

    @Test fun CarriedOccurrencesKeepTheirEntireOrderAndAreNeverSuppressedByCooldown() {
        val carried = listOf("fee2", "job1", "want1", "fee1", "discovery")
        val state = initial.copy(engine = EngineState("scheduler", 0, 2, DayPhase.FINISHED, 2, 0, true,
            null, 100, carried.mapIndexed { index, id -> EventOccurrence("$index", id, EventOrigin.SCHEDULE, EventStatus.CARRIED) }, emptyList()),
            eventHistory = carried.map { EventExposure(it, 2, null, 1) })
        assertEquals(carried, catalog.plan(state))
    }

    @Test fun AlreadyShownConditionalProblemRemainsSolvableAfterChangingTheLook() {
        val occurrence = EventOccurrence("resin-offer", "resin", EventOrigin.SCHEDULE, EventStatus.PAUSED)
        val state = initial.copy(engine = EngineState("scheduler", 0, 1, DayPhase.RUNNING, 0, 5, false,
            null, 100, listOf(occurrence), emptyList()))
        assertTrue(catalog.storyProgress(state).eligible("resin"))
        assertFalse(catalog.storyProgress(initial).eligible("resin"))
    }

    @Test fun PendingCriticalProblemDoesNotBlockStoryBeforeItsFirstPresentation() {
        val storyId = "want1"
        val criticalPolicies = policies + (storyId to policies.getValue(storyId).copy(storyActId = "act")) +
            ("fee1" to policies.getValue("fee1").copy(scheduling = policies.getValue("fee1").scheduling.copy(blocksStoryUntilResolved = true)))
        val campaign = StoryCampaign(listOf(StoryAct("act", "Act", "day", listOf(storyId), storyId)))
        val state = initial.copy(selectedGoalId = "goal", engine = EngineState("scheduler", 0, 1, DayPhase.RUNNING,
            0, 5, false, null, 100, listOf(EventOccurrence("critical", "fee1", EventOrigin.SCHEDULE, EventStatus.PENDING)), emptyList()))
        fun progress(saved: GameState) = StoryProgress(content, criticalPolicies, catalog.goals, campaign, saved)
        assertTrue(progress(state).eligible(storyId))
        val engine = state.engine!!
        assertFalse(progress(state.copy(engine = engine.copy(events = listOf(
            engine.events.single().copy(status = EventStatus.CARRIED_ACTIVE))))).eligible(storyId))
    }

    @Test fun OnlyNeverShownEverydayPremiseCanExpireButShownProblemsAndLoreRemain() {
        val progress = catalog.storyProgress(initial)
        val unshown = EventOccurrence("resin", "resin", EventOrigin.SCHEDULE, EventStatus.CARRIED)
        val policy = policies.getValue("resin")
        assertFalse(EventScheduling.retainOccurrence(unshown, EventType.RANDOM, policy, progress))
        assertTrue(EventScheduling.retainOccurrence(unshown.copy(status = EventStatus.CARRIED_ACTIVE), EventType.RANDOM, policy, progress))
        assertTrue(EventScheduling.retainOccurrence(unshown.copy(status = EventStatus.PAUSED), EventType.RANDOM, policy, progress))
        assertTrue(EventScheduling.retainOccurrence(unshown, EventType.STORY, policy, progress))
        val state = initial.copy(engine = EngineState("scheduler", 0, 1, DayPhase.FINISHED, 1, 0, true,
            null, 100, listOf(unshown), emptyList()))
        assertFalse("resin" in catalog.plan(state))
        assertTrue(state.eventHistory.isEmpty())
        assertTrue(state.story.decisions.isEmpty())
    }

    @Test fun ActualPresentationIsRecordedOnceAndDismissedJobIsNotCompletedWork() = runTest {
        val repo = object : GameRepository {
            val flow = MutableStateFlow(initial)
            override fun observe() = flow
            override suspend fun read() = flow.value
            override suspend fun initializeIfAbsent(initial: GameState) = flow.value
            override suspend fun update(transform: (GameState) -> GameState) = transform(flow.value).also { flow.value = it }
        }
        val engine = GameEngine(repo, EventFactory(content, policies, catalog.meals), catalog.rules)
        var requestId = 0
        suspend fun send(command: EngineCommand) {
            assertTrue(engine.dispatch(EngineRequest("r${++requestId}", repo.read().engine?.revision, command)) is EngineResult.Applied)
        }
        send(EngineCommand.BeginDay("day", listOf("job1", "want1", "want2", "discovery")))
        assertTrue(repo.read().eventHistory.isEmpty())
        send(EngineCommand.OpenNextEvent)
        assertEquals(EventExposure("job1", 1, null, 1, 0), repo.read().eventHistory.single())
        send(EngineCommand.DismissDeedProposal(repo.read().engine!!.currentEvent!!.id))
        assertEquals(0, repo.read().eventHistory.single().completionCount)
        send(EngineCommand.OpenNextEvent)
        val shown = repo.read().engine!!.currentEvent!!
        send(EngineCommand.PauseEvent(shown.id)); send(EngineCommand.OpenNextEvent)
        send(EngineCommand.CompleteEvent(shown.id, "want1:done"))
        assertEquals(EventExposure("want1", 1, 1, 1, 1), repo.read().eventHistory.last())
    }

    @Test fun recentMechanicDoesNotRepeatUnderAnotherDeedNameWhenAnAlternativeIsEligible() {
        val varied = catalog.copy(policies = policies.mapValues { (id, policy) ->
            if (!id.startsWith("job")) policy else policy.copy(deedGameKind =
                if (id in setOf("job1", "job2")) DeedGameKind.PRECISION else DeedGameKind.MEMORY)
        })
        val state = finishedDay(listOf(EventExposure("job1", 4, 4, 1, 1)))
        val before = state.copy()
        val plan = varied.plan(state)
        assertEquals("job3", plan.first { it.startsWith("job") })
        assertEquals(plan, varied.plan(state))
        assertEquals(before, state)
    }

    @Test fun oldestFamilyWinsBeforeLifetimeFrequencyAndCatalogOrderBreaksEqualTies() {
        val state = finishedDay(catalog.deedPool.mapIndexed { index, id ->
            EventExposure(id, if (index == 1) 1 else 4, null, if (index == 0) 1 else 10)
        })
        assertEquals("job2", catalog.plan(state).first { it.startsWith("job") })
        assertEquals("job1", catalog.plan(initial).first { it.startsWith("job") })
    }

    @Test fun firstUsefulHintKeepsPriorityButAlreadyOfferedOrCompletedHintDoesNotMonopolizePlans() {
        val campaign = StoryCampaign(listOf(StoryAct("act", "Act", "day", listOf("intro"), "intro")),
            deedHints = listOf(StoryDeedHint(StoryCondition.Always, "job8")))
        val hinted = catalog.copy(storyCampaign = campaign, policies = policies +
            ("intro" to policies.getValue("intro").copy(storyActId = "act", finishesStoryAct = true)))
        val first = initial.copy(selectedGoalId = "goal", ownedItems = listOf(OwnedItem("owned", "part")))
        val firstPlan = hinted.plan(first)
        assertEquals("intro", firstPlan.first())
        assertEquals("job8", firstPlan.first { it.startsWith("job") })
        for (exposure in listOf(EventExposure("job8", 4, null, 1), EventExposure("job8", null, 4, 0, 1))) {
            val later = finishedDay(listOf(exposure)).copy(selectedGoalId = "goal", ownedItems = first.ownedItems)
            val plan = hinted.plan(later)
            assertEquals("intro", plan.first())
            assertEquals("job1", plan.first { it.startsWith("job") })
        }
    }

    @Test fun replacementAndPreviousIdentitiesDoNotMakeAnAlreadySeenFamilyLookNew() {
        val newWant = content.events.single { it.id == "want1" }.copy(id = "new-want")
        val newChoice = content.choices.single { it.eventId == "want1" }
            .copy(id = "new-want:done", eventId = "new-want")
        for (explicitReplacement in listOf(false, true)) {
            val varietyCatalog = catalog.copy(
                content = content.copy(events = content.events + newWant, choices = content.choices + newChoice),
                policies = policies + ("new-want" to policies.getValue("want1").copy(
                    scheduling = EventSchedulingPolicy(family = "new-want", kind = EverydayEventKind.WANT))) +
                    ("want2" to policies.getValue("want2").copy(scheduling = EventSchedulingPolicy(
                        cooldownDays = 1, family = "old-purchase", kind = EverydayEventKind.WANT,
                        previousEventIds = if (explicitReplacement) emptySet() else setOf("want1")))),
                dailyEventPool = listOf("want2", "new-want", "discovery", "fee1"),
                eventReplacements = if (explicitReplacement) mapOf("want1" to "want2") else emptyMap(),
            )
            val plan = varietyCatalog.plan(finishedDay(listOf(EventExposure("want1", 3, 4, 1, 1))))
            assertTrue("new-want" in plan)
            assertFalse("want2" in plan)
        }
    }

    @Test fun limitedCatalogFallsBackToEligibleRepeatedMechanicsAndKeepsCooldowns() {
        val precisionOnly = catalog.copy(policies = policies.mapValues { (id, policy) ->
            if (id.startsWith("job")) policy.copy(deedGameKind = DeedGameKind.PRECISION) else policy
        })
        val state = finishedDay(listOf(EventExposure("job1", 4, 4, 1, 1), EventExposure("want1", 4, null, 1)))
        val plan = precisionOnly.plan(state)
        assertEquals(4, plan.size)
        assertEquals("job2", plan.first { it.startsWith("job") })
        assertFalse("want1" in plan)
    }

    @Test fun fallbackJobsReconsiderMechanicsAfterEverySelectedSlot() {
        val deedsOnly = catalog.copy(dailyEventPool = emptyList(), policies = policies.mapValues { (id, policy) ->
            policy.copy(deedGameKind = when (id) {
                "job1", "job2" -> DeedGameKind.PRECISION
                "job3", "job4" -> DeedGameKind.MEMORY
                "job5", "job6", "job7", "job8" -> DeedGameKind.COMPARISON
                else -> null
            })
        })
        val plan = deedsOnly.plan(initial)
        assertEquals(listOf("job1", "job3", "job5"), plan.take(3))
        assertEquals(4, plan.size)
    }

    private fun finishedDay(history: List<EventExposure>) = initial.copy(
        engine = EngineState("scheduler", 0, 4, DayPhase.FINISHED, 0, 5, true,
            null, 100, emptyList(), emptyList()), eventHistory = history)
}
