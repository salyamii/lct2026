package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class ProtectedNeedsBudgetTest {
    @Test fun allOtherPurchasesAndTransfersStopBeforeTheFoodAllocation() {
        val before = EconomyState(BudgetPlan(35, 4, 3, 2), savingsBalance = 5)
        for (kind in listOf(SpendingKind.GENERAL, SpendingKind.WANT)) {
            val quote = EconomyOperations.quote(before, 10, kind)
            assertEquals(1L, quote.missing)
            assertTrue(quote.parts.none { it.section == BudgetSection.NEEDS })
            assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(before, 10, kind) }
        }
        assertEquals(1L, EconomyOperations.depositQuote(before, 10).missing)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.deposit(before, 10) }
        assertEquals(1L, EconomyOperations.goalPurchaseQuote(before, 15).missing)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.purchaseGoal(before, 15) }
        val goal = EconomyOperations.purchaseGoal(before, 14)
        assertEquals(35L, goal.availableBalance)
        assertEquals(35L, goal.plan.needs)
        assertEquals(0L, goal.savingsBalance)
        assertEquals(30L, EconomyOperations.spend(before, 5, SpendingKind.FEEDING).plan.needs)
        assertEquals(35L, before.plan.needs)
    }

    @Test fun acceptingFoodRiskCannotBypassTheProtectedAllocation() = runTest {
        val repo = Repository(state(BudgetPlan(100, 0, 0, 0)))
        val engine = engine(repo)
        val before = repo.value
        for (accept in listOf(false, true)) {
            val result = engine.dispatch(EngineRequest("deposit:$accept", before.engine!!.revision,
                EngineCommand.DepositSavings(1, acceptFoodRisk = accept)))
            assertEquals(EngineResult.Blocked(BlockReason.InsufficientMoney(1)), result)
            assertEquals(before, repo.value)
        }
        assertTrue(engine.dispatch(EngineRequest("meal", before.engine!!.revision, EngineCommand.Feed("meal"))) is EngineResult.Applied)
        assertEquals(95L, repo.value.economy.plan.needs)
        assertEquals(0L, repo.value.economy.savingsBalance)
    }

    @Test fun anUnderfundedLegacyPlanStillExplainsFoodRiskForUnprotectedMoney() = runTest {
        val repo = Repository(state(BudgetPlan(0, 0, 0, 40)))
        val engine = engine(repo)
        val before = repo.value
        assertEquals(EngineResult.Blocked(BlockReason.FoodBudgetWarning(30, 35)),
            engine.dispatch(EngineRequest("warn", before.engine!!.revision, EngineCommand.DepositSavings(10))))
        assertEquals(before, repo.value)
        assertTrue(engine.dispatch(EngineRequest("accept", before.engine.revision,
            EngineCommand.DepositSavings(10, acceptFoodRisk = true))) is EngineResult.Applied)
        assertEquals(30L, repo.value.economy.availableBalance)
        assertEquals(10L, repo.value.economy.savingsBalance)
        assertEquals(0L, repo.value.economy.plan.needs)
    }

    private fun state(plan: BudgetPlan) = GameState(PetState("PLAIN", PetVisualState.NORMAL), EconomyState(plan),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList(),
        engine = EngineState("protected-needs", 0, 1, DayPhase.RUNNING, 0, 5, false, null,
            plan.total, emptyList(), emptyList()))
    private fun engine(repo: Repository) = GameEngine(repo,
        EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("meal", 5, null))),
        EngineRules("protected-needs", 5, 3, 1))
    private class Repository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        val value get() = flow.value
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { flow.value = it }
    }
}
