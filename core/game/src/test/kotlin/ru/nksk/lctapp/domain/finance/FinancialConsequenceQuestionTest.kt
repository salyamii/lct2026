package ru.nksk.lctapp.domain.finance

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class FinancialConsequenceQuestionTest {
    private fun state(available: Long, savings: Long = 90): GameState {
        val period = FinancialPeriod("period:1:stars", "stars", 1, 1, available, savings)
        return GameState(PetState("PLAIN", PetVisualState.NORMAL),
            // No food allocation: the question still compares known needs with the actual accounts.
            EconomyState(BudgetPlan(0, 0, 0, available), availableBalance = available, savingsBalance = savings),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList(), selectedGoalId = "stars",
            financial = FinancialProgress(currentPeriodId = period.id, periods = listOf(period)))
    }

    private fun question(state: GameState, needs: Long = 20, price: Long = 10) =
        FinancialPeriods.question(state, "consequence", FinancialQuestionKind.CONSEQUENCE,
            knownNeeds = needs, purchasePrice = price, purchaseTitle = "Яркий шарф")

    @Test fun purchaseThatLeavesEnoughForFoodHasAnExplanationBasedOnActualMoney() {
        val before = state(35)
        val task = question(before)
        assertEquals("needs_covered", task.correctAnswerId)
        assertTrue(task.prompt.contains("У нас 35 монет"))
        assertTrue(task.prompt.contains("На еду до следующей недели нужно 20"))
        assertTrue(task.explanation.contains("35 − 10 = 25"))
        assertEquals("optional_purchase", task.comparisonFamily)
        assertNull(task.ledgerTask)
        assertEquals(35L, before.economy.availableBalance)
        assertEquals(90L, before.economy.savingsBalance)
    }

    @Test fun affordablePurchaseCanCreateAShortageForKnownNeeds() {
        val task = question(state(25))
        assertEquals("needs_uncovered", task.correctAnswerId)
        assertTrue(task.explanation.contains("25 − 10 = 15"))
        assertTrue(task.explanation.contains("На еду нужно 20, не хватает 5"))
    }

    @Test fun existingShortageIsNotAttributedEntirelyToTheOptionalPurchase() {
        val task = question(state(15))
        assertEquals("needs_uncovered", task.correctAnswerId)
        assertTrue(task.explanation.contains("На еду не хватало и раньше"))
        assertTrue(task.options.single { it.id == task.correctAnswerId }.text.contains("не хватало и раньше"))
    }

    @Test fun savingsAndBudgetIntentionsCannotPayAnUnaffordablePurchase() {
        val task = question(state(5, savings = 200))
        assertEquals("not_affordable", task.correctAnswerId)
        assertTrue(task.explanation.contains("Не хватает 10 − 5 = 5"))
        assertTrue(task.explanation.contains("Деньги из неё берём отдельным действием"))
    }

    @Test fun onlyPlanReviewCanCompleteThePeriodReviewMilestone() {
        FinancialQuestionKind.entries.forEach { kind ->
            val before = state(35)
            val task = if (kind == FinancialQuestionKind.CONSEQUENCE) question(before)
                else FinancialPeriods.question(before, "ledger", kind)
            val answer = task.copy(answeredOptionId = task.correctAnswerId, attempts = 1)
            val after = before.copy(financial = before.financial.copy(practice = answer))
            val command = EngineCommand.AnswerFinancialQuestion(task.id, task.correctAnswerId)
            val result = FinancialPeriods.record(before, after, EngineRequest("answer", null, command))
            assertEquals(kind == FinancialQuestionKind.PLAN_REVIEW, result.financial.currentPeriod!!.reviewedPlan)
            assertEquals(before.economy, result.economy)
        }
    }

    @Test fun firstManualPlanForTheSelectedPeriodIsInitialAndLaterRevisionKeepsItsReason() {
        val initial = state(35)
        val before = initial.copy(economy = initial.economy.copy(planning = BudgetPlanning("manual",
            BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION, 0, baseAmount = 35)))
        val command = EngineCommand.ConfirmBudget("manual", 0, BudgetRevisionReason.UNSPECIFIED)
        val first = FinancialPeriods.confirmed(before, initial, EngineRequest("first", null, command), command, 20)
        assertEquals(BudgetRevisionReason.INITIAL, first.financial.plans.single().reason)
        val revision = command.copy(reason = BudgetRevisionReason.UNEXPECTED_EXPENSE)
        val revised = FinancialPeriods.confirmed(first, first, EngineRequest("revision", null, revision), revision, 20)
        assertEquals(BudgetRevisionReason.UNEXPECTED_EXPENSE, revised.financial.plans.last().reason)
        assertEquals(first.financial.plans.single(), revised.financial.plans.first())
    }
}
