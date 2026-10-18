package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.chapterone.BoatJourney
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState

class BoatRepairPaymentTest {
    @Test fun repairPaysTwoFromReserveWithoutCompletingTripOrSpendingEnergy() = runTest {
        val f = Fixture()
        val before = f.state
        f.pay(0)
        assertEquals(before.economy.availableBalance - BoatJourney.REPAIR_PRICE, f.state.economy.availableBalance)
        assertEquals(before.economy.plan.copy(reserve = 23), f.state.economy.plan)
        assertEquals(before.economy.savingsBalance, f.state.economy.savingsBalance)
        assertEquals(before.story, f.state.story)
        val dayBefore = checkNotNull(before.engine)
        assertEquals(dayBefore.events, f.state.engine!!.events)
        assertEquals(dayBefore.steps, f.state.engine!!.steps)
        assertEquals(dayBefore.energy, f.state.engine!!.energy)
        assertEquals(before.ownedItems, f.state.ownedItems)
        assertEquals(DayJournalEntry(boatRepairReceiptId("occurrence", 0), DayJournalKind.BOAT_REPAIR,
            "trip", -2), f.state.engine!!.journal.single())
    }

    @Test fun foodAndRealSavingsCannotPayRepairEvenWhenTotalBalanceIsLarge() = runTest {
        val f = Fixture(plan = BudgetPlan(100, 0, 0, 1))
        val before = f.state
        val result = f.send(EngineCommand.PayBoatRepair("occurrence", 0))
        assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(1)), result)
        assertEquals(before, f.state)
        assertTrue(f.repo.history.isEmpty())
    }

    @Test fun repairCanUseWantsAfterReserveWithoutTouchingFood() = runTest {
        val f = Fixture(plan = BudgetPlan(35, 3, 0, 1))
        f.pay(0)
        assertEquals(BudgetPlan(35, 2, 0, 0), f.state.economy.plan)
        assertEquals(50L, f.state.economy.savingsBalance)
    }

    @Test fun anotherRequestForSameRockIsHarmlessButAnotherRockHasItsOwnExpense() = runTest {
        val f = Fixture()
        f.pay(0)
        val first = f.state
        f.pay(0)
        assertEquals(first.economy, f.state.economy)
        assertEquals(first.engine!!.journal, f.state.engine!!.journal)
        assertTrue(f.repo.history.last().operations.isEmpty())
        assertTrue(f.repo.history.last().facts.none { it.detail is FactDetail.UnexpectedExpense })
        f.pay(1)
        assertEquals(first.economy.availableBalance - 2, f.state.economy.availableBalance)
        assertEquals(2, f.state.engine!!.journal.size)
    }

    @Test fun originalRequestIsDeduplicatedEvenAfterUnrelatedWorldChanges() = runTest {
        val f = Fixture()
        val request = EngineRequest("original", 0, EngineCommand.PayBoatRepair("occurrence", 0))
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)
        f.pay(1)
        val latest = f.state
        assertEquals(EngineResult.Applied(latest), f.engine.dispatch(request))
        assertEquals(latest, f.state)
        assertEquals(2, f.repo.history.size)
    }

    @Test fun staleWrongOccurrenceAndOutOfRouteRocksDoNotChangeMoney() = runTest {
        val f = Fixture()
        for (command in listOf(EngineCommand.PayBoatRepair("other", 0),
            EngineCommand.PayBoatRepair("occurrence", -1),
            EngineCommand.PayBoatRepair("occurrence", BoatJourney.rocks.size))) {
            val before = f.state
            assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.send(command))
            assertEquals(before, f.state)
        }
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision),
            f.engine.dispatch(EngineRequest("stale", 9, EngineCommand.PayBoatRepair("occurrence", 0))))
    }

    @Test fun pausedCompletedNonBoatAndDeedOccurrencesCannotPayForBoat() = runTest {
        for (status in listOf(EventStatus.PAUSED, EventStatus.RESULT, EventStatus.COMPLETED)) {
            val f = Fixture(status = status)
            assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), f.send(EngineCommand.PayBoatRepair("occurrence", 0)))
        }
        val other = Fixture(kind = DeedGameKind.PRECISION)
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), other.send(EngineCommand.PayBoatRepair("occurrence", 0)))
        val deed = Fixture(deed = true)
        assertEquals(EngineResult.Blocked(BlockReason.InvalidEventAction), deed.send(EngineCommand.PayBoatRepair("occurrence", 0)))
    }

    @Test fun actualExpenseHasReserveCategoryAndUnexpectedReceiptWithoutInventedStoryChoiceOrSkillPenalty() = runTest {
        val f = Fixture()
        f.pay(0)
        val entry = f.repo.history.single()
        assertEquals(listOf(LedgerEntry(boatRepairReceiptId("occurrence", 0), LedgerKind.AVAILABLE_EXPENSE, 2)), entry.operations)
        CanonicalLedger.validate(checkNotNull(entry.before), checkNotNull(entry.after), entry.operations)
        val expense = entry.facts.single { it.detail is FactDetail.UnexpectedExpense }
        assertEquals(FactDetail.UnexpectedExpense(entry.operations.single().operationId, 2, false), expense.detail)
        assertEquals(AnalyticsActor.SYSTEM, expense.actor)
        assertTrue(entry.facts.none { it.detail is FactDetail.ResourceChoice || it.detail is FactDetail.OptionalPurchase ||
            it.detail is FactDetail.EarningCompleted })
        val report = FinancialBudgetProjection.report(f.state, f.repo.history, f.content).single()
        assertEquals(2L, report.actual.reserve)
        assertEquals(0L, report.actual.unknownExpenses)
        assertTrue(f.state.story.decisions.isEmpty())
    }

    @Test fun repairReceiptAndCommandRoundTripAndReplayExactly() = runTest {
        val f = Fixture()
        f.pay(3)
        val entry = f.repo.history.single()
        val decoded = HistoryCodec.decodeEntry(HistoryCodec.encode(entry))
        assertEquals(entry, decoded)
        assertEquals(entry.after, f.engine.transition(checkNotNull(entry.before), checkNotNull(decoded.request)))
    }

    private class Fixture(
        plan: BudgetPlan = BudgetPlan(35, 20, 0, 25),
        kind: DeedGameKind = DeedGameKind.BOAT,
        status: EventStatus = EventStatus.ACTIVE,
        deed: Boolean = false,
    ) {
        val content = StoryContent(
            chapters = listOf(ChapterDefinition("chapter", "Глава", "goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1)),
            goals = listOf(GoalDefinition("goal", "Цель", "Цель")),
            events = listOf(EventDefinition("trip", EventType.STORY, "Поездка к звёздам", "Доберись до причала", null, null, null, 0, null, null)),
            choices = listOf(EventChoiceDefinition("arrive", "trip", 0, "Отправиться", 0, null, null, GoalImpact.NEUTRAL)),
        )
        val factory = EventFactory(content, mapOf("trip" to EventPolicy(1, choiceGameKinds = mapOf("arrive" to kind))),
            listOf(MealDefinition("food", 5, null)))
        val rules = EngineRules("rules", 5, 3, 1)
        val repo = Memory(GameState(PetState("PLAIN", PetVisualState.NORMAL), EconomyState(plan, savingsBalance = 50),
            StoryState("day", null, "trip", emptyList()), 0, 0, emptyList(),
            EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, true, null, plan.total + 50,
                listOf(EventOccurrence("occurrence", "trip", if (deed) EventOrigin.DEED else EventOrigin.SCHEDULE,
                    status, if (deed) "offer" else null)),
                if (deed) listOf(DeedOffer("offer", "trip", 1)) else emptyList()), selectedGoalId = "goal"))
        val engine = GameEngine(repo, factory, rules)
        val state get() = repo.state.value
        private var nextRequest = 0
        suspend fun send(command: EngineCommand): EngineResult = engine.dispatch(
            EngineRequest("command-${++nextRequest}", state.engine!!.revision, command))
        suspend fun pay(rock: Int): GameState = (send(EngineCommand.PayBoatRepair("occurrence", rock)) as EngineResult.Applied).state
    }

    /** Exercises the aggregate commit contract; no independent ledger or journal write is allowed. */
    private class Memory(initial: GameState) : GameRepository {
        val state = MutableStateFlow(initial)
        val history = mutableListOf<AuditEntry>()
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value
        override suspend fun update(transform: (GameState) -> GameState) = transform(state.value).also { state.value = it }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            history.singleOrNull { it.request?.id == request.id }?.let {
                require(it.request == request)
                return state.value
            }
            val before = state.value
            val after = transform(before)
            val sequence = history.size + 1L
            val operations = CanonicalLedger.fromTransition(before, after, request)
            val entry = AuditEntry(request.id, sequence, "run", AuditType.COMMAND, request, context, before, after,
                facts(before, after, "run", sequence), operations, contentFingerprint = contentFingerprint)
            history += entry
            state.value = after
            return after
        }
    }
}
