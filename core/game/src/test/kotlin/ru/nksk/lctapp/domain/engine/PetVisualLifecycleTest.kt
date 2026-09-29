package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.TargetStopState
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState

class PetVisualLifecycleTest {
    @Test fun purchaseReactionLastsUntilNewEventAndTheNextChoiceHasItsOwnPose() = runTest {
        val f = Fixture()
        f.begin("want", "quiet", "fork", "quiet")
        f.apply(EngineCommand.Feed("basic"))
        f.open()
        f.pose(PetVisualState.THINKING)
        f.complete("buy")
        f.pose(PetVisualState.HAPPY)
        assertEquals("BANDANA", f.state.pet.selectedLookId)
        f.open()
        f.pose(PetVisualState.NORMAL)
        f.complete("quiet-next")
        f.open()
        f.pose(PetVisualState.THINKING)
        f.complete("fork-a")
        f.pose(PetVisualState.NORMAL)
    }

    @Test fun authoredDisappointmentEndsAtTheNextNewEventWithoutBeingQueued() = runTest {
        val f = Fixture()
        f.begin("sad", "quiet", "quiet", "quiet")
        f.open(); f.complete("sad-next")
        f.pose(PetVisualState.UPSET)
        f.open(); f.pose(PetVisualState.NORMAL)
        f.complete("quiet-next")
        f.pose(PetVisualState.NORMAL)
    }

    @Test fun thirdSuccessfulStepShowsHungerAndBlockedFourthStepDoesNotMutateTheSave() = runTest {
        val f = Fixture()
        f.begin("quiet", "quiet", "quiet", "quiet")
        repeat(2) { f.open(); f.complete("quiet-next"); f.pose(PetVisualState.NORMAL) }
        f.open(); f.complete("quiet-next")
        f.pose(PetVisualState.HUNGRY)
        assertEquals(3, f.state.engine!!.steps)
        val before = f.state
        assertEquals(EngineResult.Blocked(BlockReason.MustEat), f.dispatch(EngineCommand.OpenNextEvent))
        assertEquals(before, f.state)
        f.apply(EngineCommand.Feed("basic"))
        f.pose(PetVisualState.NORMAL)
        assertTrue(f.state.engine!!.ateToday)
        assertEquals(5, f.state.engine!!.energy)
        f.open(); f.pose(PetVisualState.NORMAL)
    }

    @Test fun hungerThenMealThenRestFollowsCurrentNeedsWithoutResurrectingTheRewardReaction() = runTest {
        val f = Fixture(energy = 1, hunger = 1)
        f.begin("effort", "quiet", "quiet", "quiet")
        f.open(); f.complete("effort-next") // This outcome explicitly says HAPPY.
        f.pose(PetVisualState.HUNGRY)
        assertEquals(0, f.state.engine!!.energy)
        f.apply(EngineCommand.Feed("luxury")) // Even the authored meal reaction cannot restore effort.
        f.pose(PetVisualState.TIRED)
        f.apply(EngineCommand.FinishDay)
        f.pose(PetVisualState.TIRED)
        f.begin("quiet", "quiet", "quiet", "quiet")
        f.pose(PetVisualState.NORMAL)
        assertEquals(1, f.state.engine!!.energy)
        assertEquals(0, f.state.engine!!.steps)
        assertFalse(f.state.engine!!.ateToday)
        assertEquals("BANDANA", f.state.pet.selectedLookId)
    }

    @Test fun restoringMealResolvesExhaustionAndShowsItsAuthoredMood() = runTest {
        val f = Fixture()
        f.begin("quiet", "quiet", "quiet", "quiet")
        f.repo.update { it.copy(pet = it.pet.transitionTo(PetVisualState.TIRED), engine = it.engine!!.copy(energy = 0)) }
        f.apply(EngineCommand.Feed("restoring"))
        assertEquals(1, f.state.engine!!.energy)
        assertTrue(f.state.engine!!.ateToday)
        assertEquals(1, f.state.engine!!.journal.last().energyDelta)
        assertEquals(-7L, f.state.engine!!.journal.last().moneyDelta)
        f.pose(PetVisualState.HAPPY)
    }

    @Test fun nextMorningClearsYesterdayReactionAndCanOpenANewDecisionAtomically() = runTest {
        for (openFirst in listOf(false, true)) {
            val f = Fixture()
            f.begin("quiet", "quiet", "quiet", "want")
            f.apply(EngineCommand.Feed("basic"))
            repeat(3) { f.open(); f.complete("quiet-next") }
            f.open(); f.complete("buy")
            f.pose(PetVisualState.HAPPY)
            f.apply(EngineCommand.FinishDay)
            f.pose(PetVisualState.HAPPY)
            f.apply(EngineCommand.BeginDay("day", listOf("fork", "quiet", "quiet", "quiet"), openFirst))
            f.pose(if (openFirst) PetVisualState.THINKING else PetVisualState.NORMAL)
        }
    }

    @Test fun balancesBudgetEditingAndCosmeticsDoNotResetAReaction() = runTest {
        val f = Fixture()
        f.begin("want", "quiet", "quiet", "quiet")
        f.apply(EngineCommand.Feed("basic")); f.open(); f.complete("buy")
        val operations = listOf(
            EngineCommand.DepositSavings(5),
            EngineCommand.WithdrawSavings(2, confirmed = true),
            EngineCommand.RenamePet("Лис", f.state.pet.name),
            EngineCommand.SetPetColor(PetColor.entries.first { it != f.state.pet.color }, f.state.pet.color),
            EngineCommand.SetPetLook("PLAIN", "BANDANA"),
            EngineCommand.RequestFinancialPractice(),
            EngineCommand.CloseFinancialPractice,
        )
        for (operation in operations) { f.apply(operation); f.pose(PetVisualState.HAPPY) }
        f.apply(EngineCommand.ChangeBudgetAllocation("edit", 0, BudgetSection.WANTS,
            amount = f.state.economy.plan.wants, startManual = true))
        f.pose(PetVisualState.HAPPY)
        f.apply(EngineCommand.ConfirmBudget("edit", f.state.economy.planning!!.revision))
        f.pose(PetVisualState.HAPPY)
        val before = f.state
        repeat(3) { f.engine.nextEventSpendingPreview(before); f.engine.daySummary(before) }
        assertEquals(before, f.state) // Rendering/read-only navigation has no lifecycle side effect.
    }

    @Test fun resumeDoesNotReplayDecisionEntryOrEraseAReactionThatHappenedWhilePaused() = runTest {
        val f = Fixture()
        f.begin("fork", "quiet", "quiet", "quiet")
        f.open(); f.pose(PetVisualState.THINKING)
        val occurrence = f.state.engine!!.currentEvent!!.id
        f.apply(EngineCommand.PauseEvent(occurrence)); f.pose(PetVisualState.THINKING)
        f.apply(EngineCommand.Feed("luxury")); f.pose(PetVisualState.HAPPY)
        f.open()
        assertEquals(occurrence, f.state.engine!!.currentEvent!!.id)
        f.pose(PetVisualState.HAPPY)
        val before = f.state
        assertEquals(EngineResult.Blocked(BlockReason.EventInProgress), f.dispatch(EngineCommand.OpenNextEvent))
        assertEquals(before, f.state)
        f.complete("fork-a"); f.pose(PetVisualState.NORMAL)
    }

    @Test fun authoredWorryIsRespectedAtEntryAndOrdinaryAnswerEndsTheDecisionState() = runTest {
        val f = Fixture()
        f.begin("worried", "delayed", "quiet", "quiet")
        f.open(); f.pose(PetVisualState.WORRIED)
        val occurrence = f.state.engine!!.currentEvent!!.id
        f.apply(EngineCommand.PauseEvent(occurrence)); f.open(); f.pose(PetVisualState.WORRIED)
        f.complete("worried-a"); f.pose(PetVisualState.NORMAL)
        f.open(); f.pose(PetVisualState.NORMAL)
        f.complete("delayed-next"); f.pose(PetVisualState.UPSET) // COMPLETE timing is preserved.
    }

    @Test fun storyInjurySurvivesNavigationFoodAndNeutralCardsUntilAnAuthoredResolution() = runTest {
        val f = Fixture()
        f.begin("injury", "quiet", "heal", "quiet")
        f.open(); f.pose(PetVisualState.NEEDS_HELP)
        val occurrence = f.state.engine!!.currentEvent!!.id
        f.apply(EngineCommand.PauseEvent(occurrence))
        f.apply(EngineCommand.DepositSavings(5))
        f.apply(EngineCommand.Feed("luxury"))
        f.pose(PetVisualState.NEEDS_HELP)
        f.open(); f.pose(PetVisualState.NEEDS_HELP)
        f.complete("injury-next"); f.pose(PetVisualState.NEEDS_HELP)
        f.open(); f.complete("quiet-next"); f.pose(PetVisualState.NEEDS_HELP)
        f.open(); f.complete("heal-next"); f.pose(PetVisualState.NORMAL)
    }

    @Test fun authoredHungerCanBeSatisfiedByFoodEvenBeforeTheStepThreshold() = runTest {
        val f = Fixture()
        f.begin("hungry", "quiet", "quiet", "quiet")
        f.open(); f.pose(PetVisualState.HUNGRY)
        assertEquals(0, f.state.engine!!.steps)
        f.apply(EngineCommand.Feed("basic")); f.pose(PetVisualState.NORMAL)
        f.complete("hungry-next"); f.pose(PetVisualState.NORMAL)
    }

    @Test fun openedHealthEventKeepsItsOwnPoseUntilCareIsPaidWithoutChangingOtherRules() = runTest {
        val f = Fixture()
        f.begin("care", "quiet", "quiet", "quiet")
        f.pose(PetVisualState.NORMAL) // Merely scheduling tomorrow's possibility is not an illness.
        val original = f.state.pet
        f.open(); f.pose(PetVisualState.NEEDS_HELP)
        assertEquals(100L, f.state.economy.availableBalance)
        assertEquals(5, f.state.engine!!.energy)
        val occurrenceId = f.state.engine!!.currentEvent!!.id
        f.apply(EngineCommand.PauseEvent(occurrenceId))
        f.apply(EngineCommand.Feed("luxury"))
        f.pose(PetVisualState.NEEDS_HELP) // Food's HAPPY reaction cannot resolve the opened illness.
        repeat(3) { f.open(); f.complete("quiet-next"); f.pose(PetVisualState.NEEDS_HELP) }
        f.apply(EngineCommand.FinishDay)
        f.begin("care", "quiet", "quiet", "quiet")
        f.pose(PetVisualState.NEEDS_HELP)
        assertEquals(5, f.state.engine!!.energy)
        f.open()
        assertEquals(occurrenceId, f.state.engine!!.currentEvent!!.id)
        f.complete("care-pay")
        f.pose(PetVisualState.NORMAL)
        assertEquals(original, f.state.pet)
        assertEquals(80L, f.state.economy.availableBalance)
        assertEquals(5, f.state.engine!!.energy)
        assertEquals(1, f.state.engine!!.steps)
        val after = f.state
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction),
            f.dispatch(EngineCommand.CompleteEvent(occurrenceId, "care-pay")))
        assertEquals(after, f.state)
        var replay = f.receipts.first().first
        f.receipts.forEach { (before, request, expected) ->
            assertEquals(before, replay)
            replay = f.engine.transition(replay, request)
            assertEquals(expected, replay)
        }
        assertEquals(after, replay)
    }

    @Test fun oldOpenedHealthCardProjectsCareWithoutRewritingTheSavedPetOrUnseenCards() = runTest {
        val f = Fixture()
        f.begin("care", "quiet", "quiet", "quiet")
        for (status in EventStatus.entries) {
            val before = f.state.copy(pet = f.state.pet.copy(visualState = PetVisualState.HAPPY),
                engine = f.state.engine!!.let { day -> day.copy(events = day.events.mapIndexed { index, event ->
                    if (index == 0) event.copy(status = status) else event
                }) })
            val decoded = HistoryCodec.decodeState(HistoryCodec.encodeState(before))
            val presented = PetEventCondition.forPresentation(decoded, f.policies)
            val expected = if (status in setOf(EventStatus.ACTIVE, EventStatus.PAUSED, EventStatus.CARRIED_ACTIVE))
                PetVisualState.NEEDS_HELP else PetVisualState.HAPPY
            assertEquals("$status", expected, presented.visualState)
            assertEquals(before.pet.copy(visualState = expected), presented)
            assertEquals(before, decoded)
        }
    }

    @Test fun explicitNewEventStateCanReplaceStoryInjuryWithoutAHiddenCondition() = runTest {
        val f = Fixture()
        f.begin("injury", "recovery", "quiet", "quiet")
        f.apply(EngineCommand.Feed("basic"))
        f.open(); f.complete("injury-next"); f.pose(PetVisualState.NEEDS_HELP)
        f.open(); f.pose(PetVisualState.NORMAL)
        f.complete("recovery-next"); f.pose(PetVisualState.NORMAL)
        f.open(); f.pose(PetVisualState.NORMAL)
    }

    @Test fun practicalWorkChangesPoseOnlyOnCompletionAndZeroHitsAreNotPunished() = runTest {
        val f = Fixture()
        f.begin("work", "quiet", "quiet", "quiet")
        f.apply(EngineCommand.Feed("basic")); f.open(); f.pose(PetVisualState.THINKING)
        val occurrence = f.state.engine!!.currentEvent!!.id
        f.apply(EngineCommand.StartStoryGame(occurrence, "work-next"))
        f.pose(PetVisualState.THINKING)
        val finished = checkNotNull(DeedGameScore.fromPrecision(TargetStopState.create().copy(round = 5, hits = 0, lastHit = false)))
        f.apply(EngineCommand.CompleteStoryGame(occurrence, "work-next", finished))
        f.pose(PetVisualState.NORMAL)
        assertEquals(EventStatus.COMPLETED, f.state.engine!!.events.first().status)
        assertEquals(97L, f.state.economy.availableBalance)
    }

    @Test fun decodedReceiptsReplayExactlyTheSamePetLifecycleWithoutWritingTheLiveSave() = runTest {
        val f = Fixture()
        f.begin("want", "quiet", "fork", "quiet")
        f.apply(EngineCommand.Feed("basic"))
        val start = f.state
        f.open(); f.complete("buy"); f.open(); f.complete("quiet-next"); f.open(); f.complete("fork-a")
        var replay = start
        for ((before, request, expected) in f.receipts.filter { (it.first.engine?.revision ?: -1) >= start.engine!!.revision }) {
            assertEquals(before, replay)
            val decoded = HistoryCodec.decodeEntry(HistoryCodec.encode(ru.nksk.lctapp.domain.history.AuditEntry(
                request.id, 1, "run", ru.nksk.lctapp.domain.history.AuditType.COMMAND, request,
                before = before, after = expected,
            )))
            replay = f.engine.transition(replay, decoded.request!!)
            assertEquals(expected, replay)
        }
        assertEquals(f.state, replay)
        assertEquals(f.state, f.repo.state.value)
    }

    private class Fixture(energy: Int = 5, hunger: Int = 3) {
        private fun event(id: String, type: EventType = EventType.STORY, start: PetVisualState? = null) =
            EventDefinition(id, type, id, id, null, null, start, 0, null, null)
        private fun choice(id: String, eventId: String, delta: Long = 0, pose: PetVisualState? = null, position: Int = 0) =
            EventChoiceDefinition(id, eventId, position, id, delta, null, pose, GoalImpact.NEUTRAL)
        private val content = StoryContent(
            chapters = listOf(ChapterDefinition("chapter", "Глава", "goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1)),
            goals = listOf(GoalDefinition("goal", "Цель", "Цель")),
            events = listOf(event("want", EventType.WANT), event("quiet"), event("fork"), event("effort"), event("sad"),
                event("injury", start = PetVisualState.NEEDS_HELP), event("heal"), event("worried", start = PetVisualState.WORRIED),
                event("hungry", start = PetVisualState.HUNGRY), event("delayed", start = PetVisualState.UPSET), event("work"),
                event("recovery", start = PetVisualState.NORMAL), event("care", EventType.RANDOM)),
            choices = listOf(choice("buy", "want", -6, PetVisualState.HAPPY), choice("pass", "want", position = 1),
                choice("quiet-next", "quiet"), choice("fork-a", "fork"), choice("fork-b", "fork", position = 1),
                choice("effort-next", "effort", pose = PetVisualState.HAPPY), choice("sad-next", "sad", pose = PetVisualState.UPSET),
                choice("injury-next", "injury"), choice("heal-next", "heal", pose = PetVisualState.NORMAL),
                choice("worried-a", "worried"), choice("worried-b", "worried", position = 1), choice("hungry-next", "hungry"),
                choice("delayed-next", "delayed"), choice("work-next", "work", 2), choice("recovery-next", "recovery"),
                choice("care-pay", "care", -12)),
        )
        val policies = content.events.associate { event -> event.id to EventPolicy(
            energyCost = if (event.id in setOf("effort", "work")) 1 else 0,
            startEffectsTiming = when { event.id == "delayed" -> EffectTiming.COMPLETE; event.petStateOnStart != null -> EffectTiming.OPEN; else -> null },
            choiceGameKinds = if (event.id == "work") mapOf("work-next" to DeedGameKind.PRECISION) else emptyMap(),
            requiresPetHelp = event.id == "care",
            scheduling = EventSchedulingPolicy(kind = if (event.id == "care") EverydayEventKind.UNEXPECTED else EverydayEventKind.OTHER),
        ) }
        val repo = Memory(GameState(PetState("BANDANA", PetVisualState.NORMAL), EconomyState(BudgetPlan(35, 20, 20, 25)),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList()))
        val engine = GameEngine(repo, EventFactory(content, policies,
            listOf(MealDefinition("basic", 5, null), MealDefinition("luxury", 8, PetVisualState.HAPPY),
                MealDefinition("restoring", 7, PetVisualState.HAPPY, energyRestore = 1))),
            EngineRules("rules", energy, hunger, 1))
        val state get() = repo.state.value
        val receipts = mutableListOf<Triple<GameState, EngineRequest, GameState>>()
        private var sequence = 0
        suspend fun dispatch(command: EngineCommand): EngineResult {
            val before = state
            val request = EngineRequest("pet-${++sequence}", before.engine?.revision, command)
            return engine.dispatch(request).also { if (it is EngineResult.Applied) receipts += Triple(before, request, it.state) }
        }
        suspend fun apply(command: EngineCommand) {
            val result = dispatch(command)
            assertTrue("$command: $result", result is EngineResult.Applied)
        }
        suspend fun begin(vararg events: String) = apply(EngineCommand.BeginDay("day", events.toList()))
        suspend fun open() = apply(EngineCommand.OpenNextEvent)
        suspend fun complete(choice: String) = apply(EngineCommand.CompleteEvent(state.engine!!.currentEvent!!.id, choice))
        fun pose(expected: PetVisualState) = assertEquals(expected, state.pet.visualState)
    }

    private class Memory(initial: GameState) : GameRepository {
        val state = MutableStateFlow(initial)
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value
        override suspend fun update(transform: (GameState) -> GameState): GameState = transform(state.value).also { state.value = it }
    }
}
