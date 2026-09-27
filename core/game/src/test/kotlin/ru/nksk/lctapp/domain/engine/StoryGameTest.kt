package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.minigame.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState

class StoryGameTest {
    @Test fun freeMealPreventsBothStartingAndSubmittingStoryWorkUntilTomorrow() = runTest {
        for (eventType in listOf(EventType.STORY, EventType.RANDOM)) {
            val f = Fixture(eventType)
            f.apply(EngineCommand.StartStoryGame("occurrence", "repair"))
            f.apply(EngineCommand.Feed("free"))
            val exhausted = f.state
            assertEquals(0, exhausted.engine!!.energy)
            assertEquals(3, exhausted.engine!!.nextMorningEnergy)
            for (command in listOf(EngineCommand.StartStoryGame("occurrence", "repair"),
                EngineCommand.CompleteStoryGame("occurrence", "repair", precision()))) {
                assertEquals(BlockReason.MustSleep, f.blocked(command))
                assertEquals(exhausted, f.state)
            }
            assertTrue(f.state.story.decisions.isEmpty())
            assertEquals(100L, f.state.economy.balance)
            f.apply(EngineCommand.FinishDayFromEvent("occurrence"))
            assertEquals(DayPhase.FINISHED, f.state.engine!!.phase)
            assertEquals(EventStatus.CARRIED_ACTIVE, f.state.engine!!.events.single().status)
        }
    }

    @Test fun manualRandomRepairKeepsItsMoneyOrEffortComparisonAndPreviewCannotBypassTheRealGate() = runTest {
        val f = Fixture(eventType = EventType.RANDOM)
        val before = f.state
        val hypothetical = (f.engine.previewEventChoice(before, "occurrence", "repair") as EngineResult.Applied).state
        assertEquals(before.economy, hypothetical.economy)
        assertEquals(4, hypothetical.engine!!.energy)
        assertEquals(before, f.state)
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteEvent("occurrence", "repair")))
        val request = EngineRequest("actual-work", f.state.engine!!.revision,
            EngineCommand.CompleteStoryGame("occurrence", "repair", precision()),
            ru.nksk.lctapp.domain.analytics.DecisionContext("shown", true, true,
                ru.nksk.lctapp.domain.analytics.FinancialPosition(100, 0, 30), alternativeAvailable = true))
        val after = (f.engine.dispatch(request) as EngineResult.Applied).state
        val facts = EngineAnalytics(f.factory, f.rules, "test", f.engine::preview, f.engine::previewEventChoice)
            .facts(request, before, after, "run", 1)
        val resource = facts.single { it.detail is ru.nksk.lctapp.domain.analytics.FactDetail.ResourceChoice }
        val comparison = resource.detail as ru.nksk.lctapp.domain.analytics.FactDetail.ResourceChoice
        assertTrue(resource.context.complete)
        assertEquals(1, comparison.chosenCost.energy)
        assertEquals(4L, comparison.alternativeCost.money)
        assertTrue(facts.none { it.detail is ru.nksk.lctapp.domain.analytics.FactDetail.EarningCompleted })
    }
    @Test fun openingAndLeavingWorkNeverCompleteOrSpendAndOldActiveCardsStillUseTheGate() = runTest {
        val f = Fixture()
        val before = f.state
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteEvent("occurrence", "repair")))
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.Choose("occurrence", "repair")))
        f.apply(EngineCommand.StartStoryGame("occurrence", "repair"))
        assertEquals(before.economy, f.state.economy)
        assertEquals(before.story, f.state.story)
        assertEquals(before.engine!!.energy, f.state.engine!!.energy)
        assertEquals(before.engine!!.steps, f.state.engine!!.steps)
        f.apply(EngineCommand.PauseEvent("occurrence"))
        assertEquals(EventStatus.PAUSED, f.state.engine!!.events.single().status)
        assertEquals(before.economy, f.state.economy)
        assertTrue(f.state.story.decisions.isEmpty())
        f.apply(EngineCommand.OpenNextEvent)
        assertNull(f.engine.blockReason(f.state, EngineCommand.StartStoryGame("occurrence", "repair")))
    }

    @Test fun completionRequiresTheRightChoiceAndFinishedGameAndPaysAuthoredStoryRewardOnce() = runTest {
        val f = Fixture()
        val wrong = checkNotNull(DeedGameScore.fromComparison(PriceQuizState.create().copy(current = 5, correctAnswers = 0)))
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteStoryGame("occurrence", "repair", wrong)))
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.StartStoryGame("occurrence", "detour")))
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteStoryGame("occurrence", "detour", precision())))
        assertNull(DeedGameScore.fromPrecision(TargetStopState.create()))
        val before = f.state
        f.apply(EngineCommand.CompleteStoryGame("occurrence", "repair", precision()))
        assertEquals(107L, f.state.economy.availableBalance) // Story reward does not depend on precision hits.
        assertEquals(before.engine!!.energy - 1, f.state.engine!!.energy)
        assertEquals(before.engine!!.steps + 1, f.state.engine!!.steps)
        assertEquals("repair", f.state.story.decisions.single().choiceId)
        assertEquals(EventStatus.COMPLETED, f.state.engine!!.events.single().status)
        assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.CompleteStoryGame("occurrence", "repair", precision())))
        assertEquals(107L, f.state.economy.availableBalance)
    }

    @Test fun admissionAndCompletionRecheckHungerAndEnergyButTheOtherBranchStaysAnOrdinaryChoice() = runTest {
        val f = Fixture()
        f.repo.state.value = f.state.copy(engine = f.state.engine!!.copy(steps = 3, ateToday = false))
        assertEquals(BlockReason.MustEat, f.blocked(EngineCommand.StartStoryGame("occurrence", "repair")))
        f.repo.state.value = f.state.copy(engine = f.state.engine!!.copy(steps = 0, ateToday = true, energy = 0))
        assertEquals(BlockReason.MustSleep, f.blocked(EngineCommand.CompleteStoryGame("occurrence", "repair", precision())))
        f.repo.state.value = f.state.copy(engine = f.state.engine!!.copy(energy = 5))
        f.apply(EngineCommand.CompleteEvent("occurrence", "detour"))
        assertEquals("detour", f.state.story.decisions.single().choiceId)
        assertEquals(100L, f.state.economy.availableBalance)
    }

    @Test fun storyGameReceiptsRoundTripAndReplayWithTheirCompletedScore() = runTest {
        val f = Fixture()
        val before = f.state
        val request = EngineRequest("played", 0, EngineCommand.CompleteStoryGame("occurrence", "repair", precision()))
        val after = (f.engine.dispatch(request) as EngineResult.Applied).state
        val entry = AuditEntry("played", 1, "run", AuditType.COMMAND, request,
            before = before, after = after, operations = CanonicalLedger.fromTransition(before, after, request))
        val decoded = HistoryCodec.decodeEntry(HistoryCodec.encode(entry))
        assertTrue(decoded.request!!.command is EngineCommand.CompleteStoryGame)
        assertEquals(HistoryCodec.encodeState(after), HistoryCodec.encodeState(f.engine.transition(before, decoded.request!!)))
    }

    private fun precision() = checkNotNull(DeedGameScore.fromPrecision(TargetStopState.create().copy(round = 5, hits = 0, lastHit = false)))

    private class Fixture(eventType: EventType = EventType.STORY) {
        val repo = Memory(GameState(PetState("PLAIN", PetVisualState.NORMAL), EconomyState(BudgetPlan(35, 20, 20, 25)),
            StoryState("day", null, "work", emptyList()), 0, 0, emptyList(),
            EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, true, null, 100,
                listOf(EventOccurrence("occurrence", "work", EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList())))
        val content = StoryContent(
            chapters = listOf(ChapterDefinition("chapter", "Глава", "goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1)),
            goals = listOf(GoalDefinition("goal", "Цель", "Цель")),
            events = listOf(EventDefinition("work", eventType, "Ремонт", "Починим", null, null, null, 0, null, null)),
            choices = listOf(EventChoiceDefinition("repair", "work", 0, "Починить", if (eventType == EventType.STORY) 7 else 0, null, null, GoalImpact.NEUTRAL),
                EventChoiceDefinition("detour", "work", 1, "Другой путь", if (eventType == EventType.RANDOM) -4 else 0, null, null, GoalImpact.NEUTRAL)),
        )
        val factory = EventFactory(content, mapOf("work" to EventPolicy(1, choiceEnergyCosts = mapOf("detour" to 0),
            choiceGameKinds = mapOf("repair" to DeedGameKind.PRECISION))),
            listOf(MealDefinition("food", 5, null), MealDefinition("free", 0, null, nextMorningEnergy = 3)))
        val rules = EngineRules("rules", 5, 3, 1)
        val engine = GameEngine(repo, factory, rules)
        val state get() = repo.state.value
        private var sequence = 0
        suspend fun apply(command: EngineCommand) = (engine.dispatch(EngineRequest("command-${++sequence}", state.engine!!.revision, command)) as EngineResult.Applied).state
        suspend fun blocked(command: EngineCommand) = (engine.dispatch(EngineRequest("command-${++sequence}", state.engine!!.revision, command)) as EngineResult.Blocked).reason
    }

    private class Memory(initial: GameState) : GameRepository {
        val state = MutableStateFlow(initial)
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value
        override suspend fun update(transform: (GameState) -> GameState): GameState = transform(state.value).also { state.value = it }
    }
}
