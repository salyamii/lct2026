package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.economy.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

class EverydayEventsTest {
    @Test fun freshPlanContainsAnEarningOpportunityWantAndUnexpectedExpense() {
        val catalog = bundledGameCatalog()
        val initial = createInitialGameState()
        val plan = catalog.plan(initial)
        assertEquals(4, plan.size)
        assertEquals(plan, catalog.plan(initial))
        assertEquals(listOf(EventType.EARNING, EventType.WANT, EventType.RANDOM, EventType.EARNING),
            plan.map { id -> catalog.content.events.single { it.id == id }.type })
    }

    @Test fun purchaseCommitsMoneyAndOwnershipTogetherAndCannotBeReplayed() = runTest {
        val f = Fixture()
        f.begin(CAP)
        val before = f.state
        val request = f.request(EngineCommand.CompleteEvent(f.day.currentEvent!!.id, "$CAP:buy"))
        f.repo.failCommit = true
        try { f.session.dispatch(request); fail("Commit must fail") } catch (_: IOException) { }
        assertEquals(before, f.state)
        f.repo.failCommit = false
        assertTrue(f.session.dispatch(request) is EngineResult.Applied)
        assertEquals(75L, f.state.economy.balance)
        assertEquals("figma-2164-2-explorer-cap-v1", f.state.ownedItems.single().itemId)
        assertEquals(before.pet, f.state.pet)
        assertEquals(5, f.day.energy)
        assertEquals(1, f.day.steps)
        assertNull(f.day.currentEvent)
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.session.dispatch(request))
        assertEquals(1, f.state.ownedItems.size)
    }

    @Test fun unaffordablePurchaseCanBeDeclinedAndDoesNotReturnInFuturePlans() = runTest {
        val f = Fixture(balance = 0)
        f.begin(CAP)
        val before = f.state
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(25)),
            f.session.dispatch(f.request(EngineCommand.CompleteEvent(f.day.currentEvent!!.id, "$CAP:buy"))))
        assertEquals(before, f.state)
        f.complete("$CAP:pass")
        assertEquals(0L, f.state.economy.balance)
        assertTrue(f.state.ownedItems.isEmpty())
        assertEquals(1, f.day.steps)
        assertFalse(CAP in f.session.catalog.plan(f.state))
        assertTrue(RESIN in f.session.catalog.plan(f.state))
    }

    @Test fun payingAndWorkingHaveDifferentCostsAndDoNotCreateDeedOffers() = runTest {
        val paid = Fixture(balance = 4)
        paid.begin(RESIN)
        paid.complete("$RESIN:pay")
        assertEquals(0L, paid.state.economy.balance)
        assertEquals(5, paid.day.energy)

        val worked = Fixture(balance = 0)
        worked.begin(RESIN)
        val before = worked.state
        assertEquals(BlockReason.InsufficientMoney(4), worked.session.engine.blockReason(before,
            EngineCommand.CompleteEvent(worked.day.currentEvent!!.id, "$RESIN:pay")))
        assertEquals(before, worked.state)
        worked.complete("$RESIN:clean")
        assertEquals(0L, worked.state.economy.balance)
        assertEquals(3, worked.day.energy)
        assertEquals(1, worked.day.steps)
        assertTrue(worked.day.deeds.isEmpty())
        assertNull(worked.day.currentEvent)
    }

    @Test fun noMoneyAndTooLittleEnergyAllowsRestAndPreservesWholePlan() = runTest {
        val f = Fixture(balance = 0)
        f.begin(RESIN)
        f.repo.update { it.copy(engine = it.engine!!.copy(energy = 1, ateToday = true)) }
        val active = f.day.currentEvent!!
        assertEquals(BlockReason.MustSleep, f.session.engine.blockReason(f.state,
            EngineCommand.StartStoryGame(active.id, "$RESIN:clean")))
        f.send(EngineCommand.PauseEvent(active.id))
        assertTrue(f.state.story.decisions.isEmpty())
        assertEquals(EngineCommand.FinishDay, f.session.advanceCommand(f.state))
        f.send(EngineCommand.FinishDay)
        val carried = f.day.events
        val plan = f.session.catalog.plan(f.state)
        assertEquals(carried.map { it.eventId }, plan.take(carried.size))
        f.send(EngineCommand.BeginDay(f.session.catalog.storyDayId, plan))
        assertNull(f.day.currentEvent)
        assertEquals(carried.map { it.id }, f.day.events.take(carried.size).map { it.id })
        f.send(EngineCommand.OpenNextEvent)
        assertEquals(active.id, f.day.currentEvent!!.id)
        f.complete("$RESIN:clean")
        assertEquals(3, f.day.energy)
        assertEquals(0L, f.state.economy.balance)
    }

    @Test fun affordablePaidRepairIsAvailableWithOnlyOneEnergyLeft() = runTest {
        val f = Fixture(balance = 4)
        f.begin(RESIN)
        f.repo.update { it.copy(engine = it.engine!!.copy(energy = 1, ateToday = true)) }
        f.send(EngineCommand.PauseEvent(f.day.currentEvent!!.id))
        assertEquals(BlockReason.UnfinishedEvents, f.session.engine.blockReason(f.state, EngineCommand.FinishDay))
        assertEquals(EngineCommand.OpenNextEvent, f.session.advanceCommand(f.state))
        f.send(EngineCommand.OpenNextEvent)
        f.complete("$RESIN:pay")
        assertEquals(1, f.day.energy)
    }

    @Test fun newPoolDoesNotRewriteExistingPlansOrMergeRepeatedCarriedEvents() = runTest {
        val f = Fixture()
        f.send(EngineCommand.BeginDay(f.session.catalog.storyDayId, f.session.catalog.deedPool.take(4)))
        val before = f.state
        val reopened = f.newSession()
        reopened.prepare()
        assertEquals(before, reopened.read())
        f.repo.update { it.copy(engine = it.engine!!.copy(events = listOf(
            EventOccurrence("one", RESIN, EventOrigin.SCHEDULE, EventStatus.CARRIED_ACTIVE),
            EventOccurrence("two", RESIN, EventOrigin.SCHEDULE, EventStatus.CARRIED),
        ))) }
        assertEquals(listOf(RESIN, RESIN), f.session.catalog.plan(f.state).take(2))
        assertEquals(2, f.session.catalog.plan(f.state).count { it == RESIN })
    }

    @Test fun factoryRejectsForeignChoiceEffortAndVariableDeedEffort() {
        val catalog = bundledGameCatalog()
        val invalid = listOf(
            catalog.policies + (RESIN to EventPolicy(0, choiceEnergyCosts = mapOf("$CAP:buy" to 2))),
            catalog.policies + (catalog.deedPool.first() to EventPolicy(1,
                choiceEnergyCosts = mapOf("${catalog.deedPool.first()}:complete" to 2))),
        )
        for (policies in invalid) {
            try { EventFactory(catalog.content, policies, catalog.meals, catalog.goals, catalog.storyCampaign); fail("Invalid policy accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    private class Fixture(balance: Long = 100) {
        private val initial = createInitialGameState().let { it.copy(economy = EconomyState(BudgetPlan(0, 0, 0, balance))) }
        val repo = MemoryGameRepository(initial)
        val session = newSession()
        val state get() = repo.value
        val day get() = state.engine!!
        private var sequence = 0
        fun newSession() = GameSession(repo, object : StoryContentRepository {
            private var content = StoryContent()
            override suspend fun read() = content
            override suspend fun install(content: StoryContent) { this.content = content }
        }, bundledGameCatalog(), initial)
        fun request(command: EngineCommand) = EngineRequest("daily-${++sequence}", state.engine?.revision, command)
        suspend fun send(command: EngineCommand) {
            val result = session.dispatch(request(command))
            assertTrue("$result", result is EngineResult.Applied)
        }
        suspend fun begin(id: String) {
            send(EngineCommand.BeginDay(session.catalog.storyDayId, listOf(id) + session.catalog.deedPool.take(3), openFirst = true))
        }
        suspend fun complete(choice: String) {
            val active = day.currentEvent!!
            if (choice in session.catalog.policies.getValue(active.eventId).choiceGameKinds) {
                send(EngineCommand.StartStoryGame(active.id, choice))
                val score = checkNotNull(ru.nksk.lctapp.domain.minigame.DeedGameScore.fromPrecision(
                    ru.nksk.lctapp.domain.minigame.TargetStopState.create().copy(round = 5, hits = 3, lastHit = true)))
                send(EngineCommand.CompleteStoryGame(active.id, choice, score))
            } else send(EngineCommand.CompleteEvent(active.id, choice))
        }
    }

    private class MemoryGameRepository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        var failCommit = false
        val value get() = flow.value
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            val next = transform(value)
            if (failCommit) throw IOException("Simulated transaction failure")
            flow.value = next
            return next
        }
    }

    private companion object {
        const val CAP = "figma-2164-2-v1"
        const val RESIN = "figma-2313-2-v1"
    }
}
