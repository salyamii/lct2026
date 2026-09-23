package ru.nksk.lctapp.app.navigation

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.economy.navigation.Economy
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.tasks.navigation.Tasks

class BudgetNavigationPolicyTest {
    private val initial = BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.RECEIPT, 100)

    @Test fun initialReceiptAllowsMenuAfterOnboardingAndColdStart() {
        assertFalse(shouldPresentBudget(initial, MainMenu, null))
        assertFalse(shouldPresentBudget(initial, MainMenu, initial.id))
    }

    @Test fun unfinishedAllocationAndNewWeeklyIncomeStillOpenBudget() {
        assertTrue(shouldPresentBudget(initial.copy(stage = BudgetPlanningStage.ALLOCATION), MainMenu, null))
        assertTrue(shouldPresentBudget(initial.copy(reason = BudgetPlanningReason.WEEKLY), MainMenu, null))
        assertTrue(shouldPresentBudget(initial.copy(reason = BudgetPlanningReason.MIGRATION,
            stage = BudgetPlanningStage.ALLOCATION), MainMenu, null))
    }

    @Test fun initialReceiptCannotBeBypassedByRestoredGameplayOrTasks() {
        assertTrue(shouldPresentBudget(initial, Day, initial.id))
        assertTrue(shouldPresentBudget(initial, Tasks, initial.id))
    }

    @Test fun budgetRouteDoesNotDuplicateAndCompletedBudgetDoesNotRedirect() {
        assertFalse(shouldPresentBudget(initial, Economy, null))
        assertFalse(shouldPresentBudget(null, Day, null))
        assertFalse(shouldPresentBudget(initial.copy(reason = BudgetPlanningReason.WEEKLY), MainMenu, initial.id))
    }
}
