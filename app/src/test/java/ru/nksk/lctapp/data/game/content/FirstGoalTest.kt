package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.economy.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.feature.menu.ui.toMainMenuUiState

class FirstGoalTest {
    @Test fun selectionOpensNoEventAndNextContinueIntroducesGoalWithoutDroppingEverydayPlan() = runTest {
        val f = Fixture()
        assertTrue(f.catalog.plan(f.state).all { id -> f.catalog.content.events.first { it.id == id }.type != EventType.STORY })
        f.select()
        val selected = f.state
        assertEquals(100L, selected.economy.balance)
        assertEquals(0, selected.engine!!.steps)
        assertNull(selected.engine!!.currentEvent)
        assertTrue(selected.story.decisions.isEmpty())
        val oldIds = selected.engine!!.events.map { it.id }
        f.send(checkNotNull(f.session.advanceCommand(selected)))
        assertEquals(f.catalog.introductionId, f.state.engine!!.currentEvent!!.eventId)
        assertEquals(oldIds, f.state.engine!!.events.filterNot { it.eventId == f.catalog.introductionId }.map { it.id })
        assertEquals(5, f.state.engine!!.events.size)
        assertEquals(0, f.state.engine!!.steps)
    }

    @Test fun purchaseIsAtomicConsumesOneStepAndRejectsReplayOrSecondCopy() = runTest {
        val f = Fixture()
        f.select()
        val before = f.state
        val itemId = f.goal.itemIds.first()
        val request = f.request(EngineCommand.BuyGoalItem(f.goal.goalId, itemId))
        f.repo.failCommit = true
        try { f.session.dispatch(request); fail("Commit must fail") } catch (_: IOException) { }
        assertEquals(before, f.state)
        f.repo.failCommit = false
        assertTrue(f.session.dispatch(request) is EngineResult.Applied)
        assertEquals(76L, f.state.economy.balance)
        assertEquals(listOf(itemId), f.state.ownedItems.map { it.itemId })
        assertEquals(before.engine!!.steps + 1, f.state.engine!!.steps)
        assertEquals(before.engine!!.energy, f.state.engine!!.energy)
        assertEquals(before.story, f.state.story)
        assertEquals(before.engine!!.events, f.state.engine!!.events)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.session.dispatch(request))
        assertEquals(EngineResult.Blocked(BlockReason.ItemAlreadyOwned), f.session.dispatch(f.request(request.command)))
        val menu = f.state.toMainMenuUiState(catalog = f.catalog)
        assertEquals(1, menu.completedGoals)
        assertEquals(4, menu.totalGoals)
        assertEquals("Ночь наблюдений", menu.goalTitle)
    }

    @Test fun fullPlanDefersOnlyItsLastUnopenedCardAndEarlySleepKeepsEveryOccurrence() = runTest {
        val f = Fixture()
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, f.catalog.deedPool.take(5)))
        val original = f.state.engine!!.events.map { it.id }
        f.select()
        f.send(EngineCommand.OpenNextEvent)
        val introduced = f.state.engine!!.events
        assertEquals(f.catalog.introductionId, f.state.engine!!.currentEvent!!.eventId)
        assertEquals(original, introduced.filterNot { it.eventId == f.catalog.introductionId }.map { it.id })
        assertEquals(EventStatus.CARRIED, introduced.last().status)
        f.send(EngineCommand.PauseEvent(f.state.engine!!.currentEvent!!.id))
        f.send(EngineCommand.Feed("basic-v1"))
        f.repo.update { it.copy(engine = it.engine!!.copy(energy = 0)) }
        f.send(EngineCommand.FinishDay)
        val carriedIds = f.state.engine!!.events.map { it.id }
        val tomorrow = f.catalog.plan(f.state)
        assertEquals(5, tomorrow.size)
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, tomorrow))
        assertEquals(carriedIds, f.state.engine!!.events.map { it.id })
        assertEquals(EventStatus.CARRIED, f.state.engine!!.events.last().status)
        assertEquals(EventStatus.PAUSED, f.state.engine!!.events.first().status)
    }

    @Test fun selectingAfterThePlanStillFinishesTheDayBeforeStartingTheIntroduction() = runTest {
        val f = Fixture()
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, f.catalog.deedPool.take(4)))
        f.repo.update { it.copy(engine = it.engine!!.copy(phase = DayPhase.READY_TO_END,
            ateToday = true, events = it.engine!!.events.map { event -> event.copy(status = EventStatus.COMPLETED) })) }
        f.select()
        assertEquals(EngineCommand.FinishDay, f.session.advanceCommand(f.state))
        f.send(EngineCommand.FinishDay)
        f.send(checkNotNull(f.session.advanceCommand(f.state)))
        assertNull(f.state.engine!!.currentEvent)
        f.send(checkNotNull(f.session.advanceCommand(f.state)))
        assertEquals(f.catalog.introductionId, f.state.engine!!.currentEvent!!.eventId)
    }

    @Test fun warningReservesNothingAndConfirmationUsesTheOriginalRevision() = runTest {
        val f = Fixture()
        f.select()
        val command = EngineCommand.BuyGoalItem(f.goal.goalId, f.goal.itemIds[2])
        val request = f.request(command)
        val before = f.state
        assertEquals(EngineResult.Blocked(BlockReason.FoodBudgetWarning(10, 35)), f.session.dispatch(request))
        assertEquals(before, f.state)
        f.send(EngineCommand.Feed("basic-v1"))
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision),
            f.session.dispatch(request.copy(command = command.copy(acceptFoodRisk = true))))
        assertTrue(f.state.ownedItems.isEmpty())
        f.send(command.copy(acceptFoodRisk = true))
        assertEquals(5L, f.state.economy.balance)
        assertEquals(before.economy.plan, f.state.economy.plan)
    }

    @Test fun collectingAllPartsDoesNotFinishLoreOrUnlockAnotherGoal() = runTest {
        val f = Fixture(250)
        f.select()
        f.send(EngineCommand.Feed("basic-v1"))
        val story = f.state.story
        f.goal.itemIds.reversed().forEach { f.send(EngineCommand.BuyGoalItem(f.goal.goalId, it)) }
        val progress = f.goal.progress(f.state, f.catalog.content)
        assertTrue(progress.isCollected)
        assertEquals(180L, progress.totalPrice)
        assertEquals(0L, progress.remainingPrice)
        assertEquals(65L, f.state.economy.balance)
        assertEquals(story, f.state.story)
        assertEquals(DayPhase.RUNNING, f.state.engine!!.phase)
        assertEquals(4, f.state.engine!!.steps)
        assertEquals(EngineResult.Blocked(BlockReason.GoalUnavailable),
            f.session.dispatch(f.request(EngineCommand.SelectGoal("chapter-2"))))
        assertEquals(5, f.catalog.goals.size)
    }

    @Test fun affordabilityHungerAndUnselectedGoalAreCheckedInsideTheEngine() = runTest {
        val f = Fixture(20)
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, f.catalog.plan(f.state)))
        val command = EngineCommand.BuyGoalItem(f.goal.goalId, f.goal.itemIds.first(), acceptFoodRisk = true)
        assertEquals(EngineResult.Blocked(BlockReason.GoalUnavailable), f.session.dispatch(f.request(command)))
        f.select()
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(4)), f.session.dispatch(f.request(command)))
        f.repo.update { it.copy(engine = it.engine!!.copy(steps = 3)) }
        val before = f.state
        assertEquals(EngineResult.Blocked(BlockReason.MustEat), f.session.dispatch(f.request(command)))
        assertEquals(before, f.state)
    }

    @Test fun legacyAcceptanceAndUnknownGoalIdsNeverEraseSavedProgress() = runTest {
        val f = Fixture()
        f.send(EngineCommand.BeginDay(f.catalog.storyDayId, f.catalog.plan(f.state)))
        f.repo.update { it.copy(story = it.story.copy(decisions = listOf(
            StoryDecision("old-decision", f.goal.legacyAcceptanceChoiceIds.single()),
        ))) }
        val before = f.state
        assertEquals(f.goal, f.catalog.goals.selectedGoal(before))
        assertEquals(EngineResult.Blocked(BlockReason.GoalAlreadySelected),
            f.session.dispatch(f.request(f.session.selectGoalCommand(before, f.goal.goalId))))
        assertEquals(before, f.state)
        assertEquals(0, f.goal.progress(before, f.catalog.content).boughtCount)
        f.repo.update { it.copy(selectedGoalId = "future-goal") }
        assertEquals(EngineResult.Blocked(BlockReason.GoalAlreadySelected),
            f.session.dispatch(f.request(f.session.selectGoalCommand(f.state, f.goal.goalId))))
        assertEquals("future-goal", f.state.selectedGoalId)
    }

    @Test fun repeatedReadingAndPreparingDoNotSelectBuyOrAdvance() = runTest {
        val f = Fixture()
        val before = f.state
        repeat(3) { f.session.prepare(); assertEquals(before, f.session.read()) }
        assertNull(f.state.selectedGoalId)
        assertNull(f.state.engine)
        assertEquals(0, f.state.toMainMenuUiState(catalog = f.catalog).totalGoals)
    }

    private class Fixture(balance: Long = 100) {
        val catalog = bundledGameCatalog()
        val goal = catalog.goals.first { it.goalId == "figma-stargazing-180-v1" }
        private val initial = createInitialGameState().let { it.copy(economy = EconomyState(BudgetPlan(0, 0, balance, 0))) }
        val repo = MemoryRepository(initial)
        val session = GameSession(repo, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        val state get() = repo.value
        private var sequence = 0
        fun request(command: EngineCommand) = EngineRequest("goal-${++sequence}", state.engine?.revision, command)
        suspend fun send(command: EngineCommand) {
            val result = session.dispatch(request(command))
            assertTrue("$result", result is EngineResult.Applied)
        }
        suspend fun select() = send(session.selectGoalCommand(state, goal.goalId))
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        var failCommit = false
        val value get() = flow.value
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            val next = transform(value)
            if (failCommit) throw IOException("Simulated failed commit")
            flow.value = next
            return next
        }
    }
}
