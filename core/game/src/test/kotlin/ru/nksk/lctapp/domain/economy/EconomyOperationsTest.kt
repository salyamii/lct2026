package ru.nksk.lctapp.domain.economy

import org.junit.Assert.*
import org.junit.Test

class EconomyOperationsTest {
    private val plan = BudgetPlan(35, 20, 20, 25)
    private fun state(available: Long = 100, savings: Long = 30) =
        EconomyState(plan, availableBalance = available, savingsBalance = savings)

    @Test fun draftEditsDoNotMoveMoneyUntilConfirmationDepositsTheChosenSavings() {
        val original = state()
        val manual = EconomyOperations.beginManual(original, "manual")
        val released = EconomyOperations.adjustAllocation(manual, "manual", 0, BudgetSection.SAVINGS, false)
        assertEquals(original.plan, released.plan)
        assertEquals(15L, released.displayPlan.savings)
        assertEquals(5L, released.unallocated)
        assertEquals(original.availableBalance, released.availableBalance)
        assertEquals(original.savingsBalance, released.savingsBalance)
        val allocated = EconomyOperations.adjustAllocation(released, "manual", 1, BudgetSection.RESERVE, true)
        val confirmed = EconomyOperations.confirm(allocated, "manual", 2)
        assertEquals(BudgetPlan(35, 20, 0, 30), confirmed.plan)
        assertEquals(original.balance, confirmed.balance)
        assertEquals(85L, confirmed.availableBalance)
        assertEquals(45L, confirmed.savingsBalance)
        assertNull(confirmed.planning)
    }

    @Test fun editingRemainingMoneyStartsWithActualAllocationsInsteadOfAnEmptyDraft() {
        val original = EconomyOperations.spend(EconomyOperations.spend(state(), 65, SpendingKind.GENERAL),
            18, SpendingKind.FEEDING)
        val manual = EconomyOperations.beginManual(original, "manual")
        assertEquals(BudgetPlan(17, 0, 0, 0), manual.plan)
        assertEquals(manual.plan, manual.displayPlan)
        assertEquals(0L, manual.unallocated)
        assertEquals(47L, manual.balance)
        assertEquals(17L, EconomyOperations.minimumNeeds(manual))
    }

    @Test fun manualRevisionCannotReduceFoodMinimumThroughButtonsOrDirectInput() {
        val original = state()
        assertEquals(35L, EconomyOperations.minimumNeeds(original))
        val manual = EconomyOperations.beginManual(original, "manual")
        assertEquals(35L, EconomyOperations.minimumNeeds(manual))
        assertThrows(EconomyViolation::class.java) {
            EconomyOperations.adjustAllocation(manual, "manual", 0, BudgetSection.NEEDS, false)
        }
        assertThrows(EconomyViolation::class.java) {
            EconomyOperations.setAllocation(manual, "manual", 0, BudgetSection.NEEDS, 34)
        }
        assertEquals(original.copy(plan = original.plan.copy(savings = 0), availableBalance = 80, savingsBalance = 50),
            EconomyOperations.confirm(manual, "manual", 0))
    }

    @Test fun oldUnderfundedManualDraftCanBeRepairedButCannotBeConfirmed() {
        val original = state().copy(plan = BudgetPlan(30, 25, 20, 25))
        val manual = EconomyOperations.beginManual(original, "manual")
        assertEquals(original.plan, manual.displayPlan)
        assertThrows(EconomyViolation::class.java) {
            EconomyOperations.confirm(manual, "manual", 0)
        }
        val released = EconomyOperations.adjustAllocation(manual, "manual", 0, BudgetSection.WANTS, false)
        val repaired = EconomyOperations.adjustAllocation(released, "manual", 1, BudgetSection.NEEDS, true)
        val confirmed = EconomyOperations.confirm(repaired, "manual", 2)
        assertEquals(BudgetPlan(35, 20, 0, 25), confirmed.plan)
        assertEquals(original.availableBalance - 20, confirmed.availableBalance)
        assertEquals(original.savingsBalance + 20, confirmed.savingsBalance)
    }

    @Test fun explicitMigrationDraftStillAllocatesFoodStepByStepWithoutAutomaticDistribution() {
        val original = EconomyState(BudgetPlan(0, 0, 0, 0), unallocated = 95,
            planning = BudgetPlanning("migration", BudgetPlanningReason.MIGRATION, BudgetPlanningStage.ALLOCATION,
                0, draft = BudgetPlan(0, 0, 0, 0), baseAmount = 95), availableBalance = 95, savingsBalance = 30)
        var draft = original
        assertEquals(BudgetPlan(0, 0, 0, 0), draft.displayPlan)
        repeat(7) { revision ->
            draft = EconomyOperations.adjustAllocation(draft, "migration", revision.toLong(), BudgetSection.NEEDS, true)
            assertEquals((revision + 1) * 5L, draft.displayPlan.needs)
        }
        assertEquals(60L, draft.unallocated)
        assertEquals(original.availableBalance, draft.availableBalance)
        assertEquals(original.savingsBalance, draft.savingsBalance)
    }

    @Test fun smallManualBalanceCanBeConfirmedWithoutSubsidyOrAccessToSavings() {
        val original = EconomyState(BudgetPlan(17, 0, 0, 0), savingsBalance = 90)
        val draft = EconomyOperations.beginManual(original, "manual")
        val confirmed = EconomyOperations.confirm(draft, "manual", 0)
        assertEquals(BudgetPlan(17, 0, 0, 0), confirmed.plan)
        assertEquals(17L, confirmed.availableBalance)
        assertEquals(90L, confirmed.savingsBalance)
        val empty = EconomyOperations.beginManual(EconomyState(BudgetPlan(0, 0, 0, 0), savingsBalance = 30), "empty")
        assertEquals(0L, EconomyOperations.minimumNeeds(empty))
        assertEquals(30L, EconomyOperations.confirm(empty, "empty", 0).balance)
    }

    @Test fun ordinaryExpenseConsumesCurrentAllocationWithoutSilentlySpendingTheBank() {
        val original = EconomyState(BudgetPlan(4, 0, 0, 0), savingsBalance = 90)
        assertEquals(1L, EconomyOperations.quote(original, 5, SpendingKind.FEEDING).missing)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(original, 5, SpendingKind.FEEDING) }
        val spent = EconomyOperations.spend(original, 4, SpendingKind.FEEDING)
        assertEquals(0L, spent.availableBalance)
        assertEquals(90L, spent.savingsBalance)
        assertEquals(BudgetPlan(0, 0, 0, 0), spent.plan)
    }

    @Test fun legacySavingsExpenseStillDebitsOnlyItsNamedAccount() {
        val original = state(available = 100, savings = 9)
        assertEquals(1L, EconomyOperations.quote(original, 10, SpendingKind.GOAL).missing)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(original, 10, SpendingKind.GOAL) }
        val bought = EconomyOperations.spend(original, 9, SpendingKind.GOAL)
        assertEquals(100L, bought.availableBalance)
        assertEquals(0L, bought.savingsBalance)
        assertEquals(original.plan, bought.plan)
    }

    @Test fun goalPurchaseUsesSavingsThenOtherAvailableSourcesWithoutSpendingNeeds() {
        val original = state(savings = 9)
        val quote = EconomyOperations.goalPurchaseQuote(original, 74)
        assertEquals(9L, quote.fromSavings)
        assertEquals(65L, quote.fromAvailableAmount)
        assertEquals(listOf(SpendPart(BudgetSection.SAVINGS, 20), SpendPart(BudgetSection.RESERVE, 25),
            SpendPart(BudgetSection.WANTS, 20)), quote.fromAvailable.parts)
        assertTrue(quote.affordable)
        assertEquals(0L, quote.missing)
        val paid = EconomyOperations.purchaseGoal(original, 74)
        assertEquals(0L, paid.savingsBalance)
        assertEquals(35L, paid.availableBalance)
        assertEquals(BudgetPlan(35, 0, 0, 0), paid.plan)
        assertEquals(original.balance - 74, paid.balance)
    }

    @Test fun directGoalPurchaseNeedsNoDepositAndSavingsOnlyPurchaseLeavesAllocationsUntouched() {
        val wallet = state(savings = 0)
        val direct = EconomyOperations.purchaseGoal(wallet, 24)
        assertEquals(76L, direct.availableBalance)
        assertEquals(0L, direct.savingsBalance)
        assertEquals(BudgetPlan(35, 20, 0, 21), direct.plan)
        val saved = state(savings = 30)
        val quote = EconomyOperations.goalPurchaseQuote(saved, 24)
        assertEquals(24L, quote.fromSavings)
        assertEquals(0L, quote.fromAvailableAmount)
        assertTrue(quote.fromAvailable.parts.isEmpty())
        assertEquals(saved.copy(savingsBalance = 6), EconomyOperations.purchaseGoal(saved, 24))
    }

    @Test fun combinedGoalShortfallAndUnfinishedBudgetDoNotProduceAPartialPayment() {
        val original = EconomyState(BudgetPlan(4, 0, 0, 0), savingsBalance = 3)
        val quote = EconomyOperations.goalPurchaseQuote(original, 10)
        assertEquals(3L, quote.fromSavings)
        assertEquals(0L, quote.fromAvailableAmount)
        assertEquals(7L, quote.missing)
        assertFalse(quote.affordable)
        val insufficient = assertThrows(EconomyViolation::class.java) { EconomyOperations.purchaseGoal(original, 10) }
        assertEquals(EconomyFailure.INSUFFICIENT_MONEY, insufficient.reason)
        assertEquals(7L, insufficient.missing)
        assertEquals(7L, original.balance)

        val draft = EconomyOperations.beginManual(state(), "draft")
        // Enough savings alone must not bypass an unfinished allocation.
        assertFalse(EconomyOperations.goalPurchaseQuote(draft, 10).affordable)
        val blocked = assertThrows(EconomyViolation::class.java) { EconomyOperations.purchaseGoal(draft, 10) }
        assertEquals(EconomyFailure.PLANNING_REQUIRED, blocked.reason)
        assertEquals(130L, draft.balance)
    }

    @Test fun depositAndConfirmedWithdrawalConserveMoneyAndUpdateCurrentAllocations() {
        val original = state()
        val deposited = EconomyOperations.deposit(original, 20)
        assertEquals(80L, deposited.availableBalance)
        assertEquals(50L, deposited.savingsBalance)
        assertEquals(original.balance, deposited.balance)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.withdraw(deposited, 20, false) }
        assertEquals(BudgetPlan(35, 20, 0, 25), deposited.plan)
        val returned = EconomyOperations.withdraw(deposited, 20, true)
        assertEquals(original.availableBalance, returned.availableBalance)
        assertEquals(original.savingsBalance, returned.savingsBalance)
        assertEquals(BudgetPlan(35, 20, 0, 45), returned.plan)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.deposit(original, 101) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.withdraw(original, 31, true) }
    }

    @Test fun unallocatedIsIncludedInAvailableAndNeverAnExtraWallet() {
        val pending = EconomyState(BudgetPlan(0, 0, 0, 0), unallocated = 100,
            availableBalance = 100, savingsBalance = 20)
        assertEquals(120L, pending.balance)
    }

    @Test fun extraIncomeGoesToReserveWithoutStartingMandatoryAllocation() {
        val original = state()
        val earned = EconomyOperations.earn(original, 7)
        assertEquals(107L, earned.availableBalance)
        assertEquals(original.plan.copy(reserve = original.plan.reserve + 7), earned.plan)
        assertEquals(original.savingsBalance, earned.savingsBalance)
        assertNull(earned.planning)
    }

    @Test fun weeklyPlanningAddsIncomeOnceAndKeepsSavingsOutsideItsBasis() {
        val original = EconomyState(BudgetPlan(10, 3, 0, 2), savingsBalance = 90)
        val weekly = EconomyOperations.weekly(original, "week", 100)
        assertEquals(115L, weekly.availableBalance)
        assertEquals(90L, weekly.savingsBalance)
        assertEquals(115L, EconomyOperations.planningAmount(weekly))
        assertEquals(205L, weekly.balance)
        assertEquals(original.plan, weekly.plan)
        assertEquals(original.plan, weekly.displayPlan)
        assertEquals(100L, weekly.unallocated)
        assertEquals(BudgetPlanningStage.RECEIPT, weekly.planning!!.stage)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.weekly(weekly, "again", 100) }
    }

    @Test fun smallInitialBudgetCanBeFullyPlannedWithoutAnImpossibleMinimum() {
        val pending = EconomyState(BudgetPlan(0, 0, 0, 0), 12,
            BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.RECEIPT, 12),
            availableBalance = 12, savingsBalance = 0)
        val allocating = EconomyOperations.startAllocation(pending, "initial", 0)
        assertEquals(12L, EconomyOperations.minimumNeeds(allocating))
        val ready = EconomyOperations.setAllocation(allocating, "initial", 1, BudgetSection.NEEDS, 12)
        val confirmed = EconomyOperations.confirm(ready, "initial", 2)
        assertEquals(12L, confirmed.balance)
        assertEquals(0L, confirmed.savingsBalance)
    }

    @Test fun stalePlanRevisionAndForbiddenEarningCostAreRejected() {
        val allocating = EconomyOperations.beginManual(state(), "manual")
        val changed = EconomyOperations.adjustAllocation(allocating, "manual", 0, BudgetSection.WANTS, false)
        assertThrows(EconomyViolation::class.java) {
            EconomyOperations.adjustAllocation(changed, "manual", 0, BudgetSection.RESERVE, true)
        }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(state(), 1, SpendingKind.EARNING) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.deposit(allocating, 5) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(allocating, 1, SpendingKind.FEEDING) }
    }

    @Test fun arithmeticOverflowDoesNotInventMoney() {
        val large = EconomyState(BudgetPlan(0, 0, 0, Long.MAX_VALUE))
        assertThrows(ArithmeticException::class.java) { EconomyOperations.earn(large, 1) }
        assertThrows(ArithmeticException::class.java) { EconomyOperations.weekly(large, "week", 1) }
        assertEquals(Long.MAX_VALUE, large.balance)
    }

    @Test fun readingALegacySettledPlanDoesNotRetroactivelyDepositItsSavingAllocation() {
        val created = EconomyState(plan)
        assertEquals(plan, EconomyOperations.proposedPlan(created))
        assertEquals(plan, created.displayPlan)
        assertEquals(100L, created.availableBalance)
        assertEquals(0L, created.savingsBalance)
        assertEquals(100L, created.balance)
    }

    @Test fun ordinarySpendingUsesItsCategoryAndOnlyFoodCanConsumeNeeds() {
        val cases = listOf(
            SpendingKind.GENERAL to listOf(SpendPart(BudgetSection.RESERVE, 25), SpendPart(BudgetSection.WANTS, 20), SpendPart(BudgetSection.SAVINGS, 15)),
            SpendingKind.WANT to listOf(SpendPart(BudgetSection.WANTS, 20), SpendPart(BudgetSection.RESERVE, 25), SpendPart(BudgetSection.SAVINGS, 15)),
            SpendingKind.FEEDING to listOf(SpendPart(BudgetSection.NEEDS, 35), SpendPart(BudgetSection.RESERVE, 25)),
        )
        for ((kind, expectedParts) in cases) {
            val original = state()
            val quote = EconomyOperations.quote(original, 60, kind)
            assertEquals(kind.name, expectedParts, quote.parts)
            assertTrue(quote.affordable)
            val spent = EconomyOperations.spend(original, 60, kind)
            assertEquals(40L, spent.availableBalance)
            assertEquals(spent.availableBalance, spent.plan.total)
            assertEquals(original.savingsBalance, spent.savingsBalance)
            if (kind != SpendingKind.FEEDING) assertEquals(35L, spent.plan.needs)
        }
        val lastFood = EconomyOperations.quote(state(), 90, SpendingKind.WANT)
        assertEquals(25L, lastFood.missing)
        assertTrue(lastFood.parts.none { it.section == BudgetSection.NEEDS })
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(state(), 100, SpendingKind.GENERAL) }
        val all = EconomyOperations.spend(state(), 65, SpendingKind.GENERAL)
        assertEquals(BudgetPlan(35, 0, 0, 0), all.plan)
        assertEquals(30L, all.savingsBalance)
    }

    @Test fun depositExhaustsPlannedSavingThenReserveAndWantsWhileProtectingNeeds() {
        val quote = EconomyOperations.depositQuote(state(), 90)
        assertEquals(listOf(SpendPart(BudgetSection.SAVINGS, 20), SpendPart(BudgetSection.RESERVE, 25),
            SpendPart(BudgetSection.WANTS, 20)), quote.parts)
        assertFalse(quote.affordable)
        assertEquals(25L, quote.missing)
        assertEquals(36L, EconomyOperations.depositQuote(state(), 101).missing)
        assertTrue(EconomyOperations.depositQuote(EconomyOperations.beginManual(state(), "editing"), 10).blocked)
        assertEquals(BudgetPlan(35, 20, 5, 25), EconomyOperations.deposit(state(), 15).plan)
        assertEquals(BudgetPlan(35, 20, 0, 10), EconomyOperations.deposit(state(), 35).plan)
        assertEquals(BudgetPlan(35, 10, 0, 0), EconomyOperations.deposit(state(), 55).plan)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.deposit(state(), 90) }
        val large = EconomyOperations.deposit(state(), 65)
        assertEquals(BudgetPlan(35, 0, 0, 0), large.plan)
        assertEquals(35L, large.availableBalance)
        assertEquals(95L, large.savingsBalance)
        assertEquals(130L, large.balance)
    }

    @Test fun depositMaximumExcludesNeedsAndUnavailableDraftMoney() {
        assertEquals(65L, EconomyOperations.depositAvailableBalance(state()))
        assertEquals(0L, EconomyOperations.depositAvailableBalance(EconomyState(BudgetPlan(35, 0, 0, 0))))
        assertEquals(0L, EconomyOperations.depositAvailableBalance(EconomyOperations.beginManual(state(), "draft")))
        assertEquals(0L, EconomyOperations.depositAvailableBalance(state().copy(unallocated = 1)))
        assertEquals(0L, EconomyOperations.depositAvailableBalance(state().copy(availableBalance = 99)))
    }

    @Test fun revisingAfterFoodWasPaidRequiresOnlyStillKnownNeeds() {
        val remaining = EconomyOperations.spend(state(), 15, SpendingKind.FEEDING)
        assertEquals(20L, remaining.plan.needs)
        val draft = EconomyOperations.beginManual(remaining, "manual")
        assertEquals(20L, EconomyOperations.minimumNeeds(draft, knownNeeds = 20))
        val confirmed = EconomyOperations.confirm(draft, "manual", 0, knownNeeds = 20)
        assertEquals(remaining.copy(plan = remaining.plan.copy(savings = 0), availableBalance = 65, savingsBalance = 50), confirmed)
        assertThrows(EconomyViolation::class.java) {
            EconomyOperations.adjustAllocation(draft, "manual", 0, BudgetSection.NEEDS, false, knownNeeds = 20)
        }
        val lastDay = EconomyOperations.beginManual(remaining, "last-day")
        val released = EconomyOperations.setAllocation(lastDay, "last-day", 0, BudgetSection.NEEDS, 0, knownNeeds = 0)
        val allocated = EconomyOperations.setAllocation(released, "last-day", 1, BudgetSection.RESERVE, 45, knownNeeds = 0)
        assertEquals(65L, EconomyOperations.confirm(allocated, "last-day", 2, knownNeeds = 0).availableBalance)
    }

    @Test fun explicitConfirmationDepositsForEveryPlanningReasonAndSecondEditStartsWithZeroToDeposit() {
        for (reason in BudgetPlanningReason.entries) {
            val before = state().copy(planning = BudgetPlanning("draft", reason, BudgetPlanningStage.ALLOCATION,
                if (reason in listOf(BudgetPlanningReason.INITIAL, BudgetPlanningReason.WEEKLY)) 100 else 0,
                draft = plan, baseAmount = 100))
            val confirmed = EconomyOperations.confirm(before, "draft", 0)
            assertEquals(reason.name, 80L, confirmed.availableBalance)
            assertEquals(reason.name, 50L, confirmed.savingsBalance)
            assertEquals(reason.name, 130L, confirmed.balance)
            assertEquals(confirmed.availableBalance, confirmed.plan.total)
            assertEquals(0L, confirmed.plan.savings)
            assertEquals(0L, confirmed.unallocated)
            assertNull(confirmed.planning)
            assertThrows(EconomyViolation::class.java) { EconomyOperations.confirm(confirmed, "draft", 0) }

            val second = EconomyOperations.beginManual(confirmed, "second")
            assertEquals(0L, second.displayPlan.savings)
            assertEquals(confirmed, EconomyOperations.confirm(second, "second", 0))
        }
    }

    @Test fun confirmationRejectsStaleRevisionAndChangedAvailableBasisWithoutMovingMoney() {
        val draft = EconomyOperations.beginManual(state(), "draft")
        assertEquals(EconomyFailure.STALE_SESSION,
            assertThrows(EconomyViolation::class.java) { EconomyOperations.confirm(draft, "draft", 1) }.reason)
        val staleBasis = draft.copy(availableBalance = 99)
        assertEquals(EconomyFailure.INVALID_ALLOCATION,
            assertThrows(EconomyViolation::class.java) { EconomyOperations.confirm(staleBasis, "draft", 0) }.reason)
        assertEquals(30L, staleBasis.savingsBalance)
        assertEquals(plan, staleBasis.displayPlan)
    }

    @Test fun freshWeeklyMinimumIsFullWeekWhileMigrationUsesRemainingNeed() {
        val weekly = EconomyOperations.weekly(state(), "weekly", 100)
        assertEquals(35L, EconomyOperations.minimumNeeds(weekly, knownNeeds = 5))
        val migration = weekly.copy(planning = weekly.planning!!.copy(reason = BudgetPlanningReason.MIGRATION))
        assertEquals(5L, EconomyOperations.minimumNeeds(migration, knownNeeds = 5))
        val started = EconomyOperations.startAllocation(weekly, "weekly", 0)
        assertEquals(plan, started.displayPlan)
        assertEquals(100L, EconomyOperations.allocationRemaining(started))
    }

    @Test fun inconsistentLegacyAllocationMustBeMigratedRatherThanResetDuringGameplay() {
        val legacy = state(available = 80)
        assertEquals(plan, EconomyOperations.proposedPlan(legacy))
        assertTrue(EconomyOperations.quote(legacy, 5, SpendingKind.FEEDING).blocked)
        assertTrue(EconomyOperations.depositQuote(legacy, 5).blocked)
        assertThrows(EconomyViolation::class.java) { EconomyOperations.spend(legacy, 5, SpendingKind.FEEDING) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.beginManual(legacy, "manual") }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.deposit(legacy, 5) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.withdraw(legacy, 5, true) }
        assertThrows(EconomyViolation::class.java) { EconomyOperations.earn(legacy, 5) }
    }
}
