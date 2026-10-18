package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.minigame.*

class FinancialDeedsTest {
    private val catalog = bundledGameCatalog()
    private val ids = catalog.deedPool.filter { it.startsWith("financial-deed-") }

    @Test fun fiveIndependentPaidOffersJoinThePoolWithoutChangingChapterAssignments() {
        assertEquals(18, catalog.deedPool.size)
        assertEquals(5, ids.size)
        assertEquals(setOf(DeedGameKind.NEED_WANT, DeedGameKind.REAL_DISCOUNT, DeedGameKind.COIN_BALANCE,
            DeedGameKind.FINANCIAL_TRAP, DeedGameKind.EXTRA_PURCHASE), ids.map { catalog.policies.getValue(it).deedGameKind }.toSet())
        EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign)
        for (id in ids) {
            val policy = catalog.policies.getValue(id)
            assertEquals(EventType.EARNING, catalog.content.events.single { it.id == id }.type)
            assertNull(policy.storyActId)
            assertEquals(1, policy.energyCost)
            assertEquals(2, policy.scheduling.cooldownDays)
            assertEquals(5L, catalog.content.choices.single { it.eventId == id }.moneyDelta)
            assertTrue(policy.choiceGameKinds.isEmpty())
            assertFalse(id in catalog.dailyEventPool)
            assertFalse(catalog.storyCampaign!!.acts.any { id in it.eventIds })
            // The existing scheduler can choose each new family without any story prerequisite.
            val onlyThisDeed = catalog.copy(deedPool = listOf(id))
            assertTrue(id, id in onlyThisDeed.plan(createInitialGameState()))
        }
        assertEquals(DeedGameKind.TELESCOPE,
            catalog.policies.getValue("figma-2163-43-v1:balance-v2:game-v3").deedGameKind)
        assertEquals(DeedGameKind.MARK,
            catalog.policies.getValue("campaign-choice-v2:G1.02").choiceGameKinds.values.single())
    }

    @Test fun eachGameStartsWithNoMoneyPausesForFreeAndPaysExactlyOnce() = runTest {
        for (id in ids) {
            val initial = createInitialGameState().let { it.copy(selectedGoalId = null,
                economy = EconomyState(BudgetPlan(0, 0, 0, 0), availableBalance = 0, savingsBalance = 0),
                story = it.story.copy(currentDayId = catalog.storyDayId, activeEventId = null),
                engine = EngineState(catalog.rules.id, 0, 3, DayPhase.RUNNING, 0, 5, true, null, 0,
                    emptyList(), listOf(DeedOffer("offer", id, 3)))) }
            val repository = Repository(initial)
            val engine = GameEngine(repository,
                EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign), catalog.rules)
            var requestNumber = 0
            suspend fun send(command: EngineCommand): EngineResult = engine.dispatch(
                EngineRequest("request-${++requestNumber}", repository.value.engine!!.revision, command))
            assertTrue(send(EngineCommand.StartDeed("offer")) is EngineResult.Applied)
            var occurrence = repository.value.engine!!.currentEvent!!.id
            assertTrue(send(EngineCommand.Choose(occurrence, "$id:complete")) is EngineResult.Blocked)
            assertTrue(send(EngineCommand.PauseEvent(occurrence)) is EngineResult.Applied)
            assertEquals(0L, repository.value.economy.availableBalance)
            assertEquals(5, repository.value.engine!!.energy)
            assertFalse(repository.value.engine!!.deeds.single().completed)
            assertTrue(send(EngineCommand.StartDeed("offer")) is EngineResult.Applied)
            occurrence = repository.value.engine!!.currentEvent!!.id
            val kind = checkNotNull(catalog.policies.getValue(id).deedGameKind)
            val score = completedFinancialGameScore(kind)
            val wrong = completedFinancialGameScore(if (kind == DeedGameKind.EXTRA_PURCHASE) DeedGameKind.NEED_WANT else DeedGameKind.EXTRA_PURCHASE)
            assertTrue(send(EngineCommand.CompleteDeed(occurrence, wrong)) is EngineResult.Blocked)
            val before = repository.value.engine!!
            val request = EngineRequest("complete", before.revision, EngineCommand.CompleteDeed(occurrence, score))
            assertTrue(engine.dispatch(request) is EngineResult.Applied)
            assertEquals(5L, repository.value.economy.availableBalance)
            assertEquals(before.energy - 1, repository.value.engine!!.energy)
            assertEquals(before.steps + 1, repository.value.engine!!.steps)
            assertTrue(repository.value.engine!!.deeds.single().completed)
            assertTrue(engine.dispatch(request) is EngineResult.Applied)
            assertTrue(send(EngineCommand.CompleteDeed(occurrence, score)) is EngineResult.Blocked)
            assertEquals(5L, repository.value.economy.availableBalance)
        }
    }

    private class Repository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState = transform(value).also { state.value = it }
    }
}
