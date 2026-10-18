package ru.nksk.lctapp.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.analytics.SavingMovementKind
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.finance.FinancialProgressionPolicy
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class BudgetConfirmationAnalyticsTest {
    private val rules = EngineRules("test-rules", 5, 3, 1)
    private val analytics = EngineAnalytics(
        EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("basic", 5, null))),
        rules, "catalog-v1:transition-19:test",
        preview = { _, _ -> error("Budget evidence must not preview other actions") },
        previewEventChoice = { _, _, _ -> error("Budget evidence must not preview other choices") },
    )
    private val allocation = BudgetPlan(40, 20, 25, 15)
    private val period = FinancialPeriod("period", "goal", 1, 1, 100, 10,
        savingPractice = FinancialProgressionPolicy.opening("period", 10).copy(incomeOpportunityId = "income:weekly"))
    private val command = EngineCommand.ConfirmBudget("budget", 0)
    private val context = DecisionContext("budget:shown", informationPresented = true, complete = true,
        before = FinancialPosition(100, 10, 35), alternativeAvailable = true)
    private val request = EngineRequest("confirm", 1, command, context)

    private fun transition(transfer: Boolean, plan: BudgetPlan = allocation, withPeriod: Boolean = true): Pair<GameState, GameState> {
        val progress = if (withPeriod) FinancialProgress(period.id, listOf(period)) else FinancialProgress()
        val before = GameState(PetState("PLAIN", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(0, 0, 0, 100), planning = BudgetPlanning("budget", BudgetPlanningReason.MANUAL,
                BudgetPlanningStage.ALLOCATION, 0, draft = plan, baseAmount = 100), savingsBalance = 10),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList(),
            engine = EngineState(rules.id, 1, 1, DayPhase.RUNNING, 0, 5, false, null, 110, emptyList(), emptyList()),
            selectedGoalId = "goal".takeIf { withPeriod }, financial = progress)
        val amount = if (transfer) plan.savings else 0L
        val revision = BudgetPlanRevision("confirm:plan", progress.currentPeriodId, 1, 1, 100, plan, BudgetRevisionReason.INITIAL)
        val after = before.copy(economy = EconomyState(plan.copy(savings = plan.savings - amount), savingsBalance = 10 + amount),
            financial = progress.copy(plans = listOf(revision)))
        return before to after
    }

    @Test fun confirmedAllocationAndActualDepositProduceDifferentLinkedFacts() {
        val (before, after) = transition(transfer = true)
        val facts = analytics.facts(request, before, after, "run", 2)
        val budget = facts.map { it.detail }.filterIsInstance<FactDetail.BudgetConfirmed>().single()
        assertEquals(100L, budget.allocationBase)
        assertEquals(25L, budget.savings)
        assertEquals(0L, after.economy.plan.savings)
        val movementFact = facts.single { it.detail is FactDetail.SavingMovement }
        val movement = movementFact.detail as FactDetail.SavingMovement
        assertEquals("confirm:deposit", movement.operationId)
        assertEquals(SavingMovementKind.DEPOSIT, movement.kind)
        assertEquals(25L, movement.amount)
        assertEquals("income:weekly", movement.incomeWindowId)
        assertEquals("goal", movement.goalId)
        assertEquals(FinancialPosition(75, 35, 35), movementFact.context.after)
        val priority = facts.map { it.detail }.filterIsInstance<FactDetail.PriorityApplied>().single()
        assertEquals(movement.operationId, priority.operationId)
        assertEquals(movement.amount, priority.amount)
        assertTrue(facts.all { it.actionId == request.id && it.sequence == 2L })
    }

    @Test fun legacyAndZeroSavingConfirmationsDoNotInventSavingEvidence() {
        for ((before, after) in listOf(transition(transfer = false),
            transition(transfer = true, plan = allocation.copy(wants = 45, savings = 0)))) {
            val facts = analytics.facts(request, before, after, "run", 2)
            assertTrue(facts.any { it.detail is FactDetail.BudgetConfirmed })
            assertFalse(facts.any { it.detail is FactDetail.SavingMovement || it.detail is FactDetail.PriorityApplied })
        }
    }

    @Test fun missingPresentationIsNotUpgradedToAnObservedIndependentDecision() {
        val (before, after) = transition(transfer = true)
        val facts = analytics.facts(request.copy(context = null), before, after, "run", 2)
        val movement = facts.single { it.detail is FactDetail.SavingMovement }
        assertFalse(movement.context.complete)
        assertFalse(movement.context.informationPresented)
    }

    @Test fun transferBeforeSelectingAGoalDoesNotInventAParentSkillPeriod() {
        val (before, after) = transition(transfer = true, withPeriod = false)
        val facts = analytics.facts(request, before, after, "run", 2)
        assertTrue(facts.any { it.detail is FactDetail.BudgetConfirmed })
        assertFalse(facts.any { it.detail is FactDetail.SavingMovement || it.detail is FactDetail.PriorityApplied })
    }
}
