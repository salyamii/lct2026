package ru.nksk.lctapp.domain.economy

import org.junit.Assert.*
import org.junit.Test

class EconomyOperationsTest {
    @Test fun manualRedistributionPreservesMoneyAndWorksBelowWeeklyMinimum() {
        val original = EconomyState(BudgetPlan(5, 0, 10, 5))
        val manual = EconomyOperations.beginManual(original, "manual")
        val released = EconomyOperations.adjustAllocation(manual, "manual", 0, BudgetSection.NEEDS, false)
        assertEquals(5L, released.unallocated)
        val allocated = EconomyOperations.adjustAllocation(released, "manual", 1, BudgetSection.RESERVE, true)
        val confirmed = EconomyOperations.confirm(allocated, "manual", 2)
        assertEquals(BudgetPlan(0, 0, 10, 10), confirmed.plan)
        assertEquals(original.balance, confirmed.balance)
        assertNull(confirmed.planning)
        assertThrows(EconomyViolation::class.java) {
            EconomyOperations.beginManual(manual, "replacement")
        }
    }

    @Test fun spendingUsesPriorityAndRejectsWithoutMutation() {
        val state = EconomyState(BudgetPlan(35, 20, 10, 3))
        val quote = EconomyOperations.quote(state, 10, SpendingKind.GENERAL)
        assertEquals(listOf(SpendPart(BudgetSection.RESERVE, 3), SpendPart(BudgetSection.WANTS, 7)), quote.parts)
        assertEquals(BudgetPlan(35, 13, 10, 0), EconomyOperations.spend(state, 10, SpendingKind.GENERAL).plan)
        assertEquals(1L, EconomyOperations.quote(state, 11, SpendingKind.GOAL).missing)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(state, 11, SpendingKind.GOAL) }
        assertEquals(68L, state.balance)
    }
    @Test fun rewardAndWeeklyIncomeHaveDifferentDestinations() {
        val state = EconomyState(BudgetPlan(35, 20, 20, 25))
        assertEquals(32L, EconomyOperations.earn(state, 7).plan.reserve)
        val weekly = EconomyOperations.weekly(state, "week:8", 100)
        assertEquals(state.plan, weekly.plan)
        assertEquals(100L, weekly.unallocated)
        assertEquals(BudgetPlanningStage.RECEIPT, weekly.planning!!.stage)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.weekly(weekly, "week:8", 100) }
    }
    @Test fun planningPreservesSumRequiresMinimumAndRejectsStaleRevision() {
        val state = EconomyState(BudgetPlan(0, 0, 0, 0), 38,
            BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.RECEIPT, 38))
        val allocating = EconomyOperations.startAllocation(state, "initial", 0)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.confirm(allocating, "initial", 1) }
        val needs = EconomyOperations.setAllocation(allocating, "initial", 1, BudgetSection.NEEDS, 35)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.setAllocation(needs, "initial", 1, BudgetSection.WANTS, 3) }
        val all = EconomyOperations.adjustAllocation(needs, "initial", 2, BudgetSection.RESERVE, true)
        assertEquals(3L, all.plan.reserve)
        assertEquals(38L, all.balance)
        assertNull(EconomyOperations.confirm(all, "initial", 3).planning)
    }
    @Test fun earningCannotMaskCostAndPlanningBlocksSpend() {
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(EconomyState(BudgetPlan(35, 20, 20, 25)), 1, SpendingKind.EARNING) }
        val pending = EconomyOperations.weekly(EconomyState(BudgetPlan(35, 20, 20, 25)), "week", 100)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(pending, 1, SpendingKind.FEEDING) }
    }
    @Test fun needsCanRiseGraduallyButDirectInputAndDecreaseRespectMinimum() {
        val initial = EconomyState(BudgetPlan(10, 0, 0, 0), 90,
            BudgetPlanning("week", BudgetPlanningReason.WEEKLY, BudgetPlanningStage.ALLOCATION, 100))
        assertThrows(EconomyViolation::class.java) { EconomyOperations.setAllocation(initial, "week", 0, BudgetSection.NEEDS, 20) }
        val raised = EconomyOperations.adjustAllocation(initial, "week", 0, BudgetSection.NEEDS, true)
        assertEquals(15L, raised.plan.needs)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.adjustAllocation(raised, "week", 1, BudgetSection.NEEDS, false) }
    }

    @Test fun allPrioritiesMatchApprovedOrderAndNeedsMayBeSpentBelow35() {
        val state = EconomyState(BudgetPlan(35, 20, 10, 3))
        assertEquals(listOf(BudgetSection.WANTS, BudgetSection.RESERVE, BudgetSection.SAVINGS, BudgetSection.NEEDS),
            EconomyOperations.quote(state, state.balance, SpendingKind.WANT).parts.map { it.section })
        assertEquals(listOf(BudgetSection.NEEDS, BudgetSection.RESERVE, BudgetSection.WANTS, BudgetSection.SAVINGS),
            EconomyOperations.quote(state, state.balance, SpendingKind.FEEDING).parts.map { it.section })
        assertEquals(31L, EconomyOperations.spend(state, 4, SpendingKind.FEEDING).plan.needs)
        assertEquals(0L, EconomyOperations.spend(state, state.balance, SpendingKind.GENERAL).balance)
    }

}
