package ru.nksk.lctapp.domain.finance

import org.junit.Assert.*
import org.junit.Test

class FinancialProgressionPolicyTest {
    private val policy = FinancialProgressionPolicy
    private fun deposit(state: SavingsPracticeState, id: String, day: Int, amount: Long = 10) =
        policy.deposit(state, id, day, amount, independent = true, needsCovered = true)

    @Test fun regularSavingNeedsSeparateDaysAndRealIncomeOpportunities() {
        val first = deposit(policy.opening("goal", 0), "first", 1)
        val split = deposit(first, "split", 1)
        assertEquals(1, policy.summary(split).creditedDecisions)
        assertFalse(policy.summary(split).ready)
        val nextDaySameIncome = deposit(split, "same-income", 2)
        assertFalse(policy.summary(nextDaySameIncome).ready)
        val earned = policy.income(nextDaySameIncome, "completed-work")
        val second = deposit(earned, "second", 2)
        assertTrue(policy.summary(second).regular)
    }

    @Test fun roundTripsDoNotCreateRegularityButPurchasedProgressSurvivesWithdrawal() {
        val first = deposit(policy.opening("goal", 0), "first", 1)
        val withdrawn = policy.withdraw(first, 10)
        val second = deposit(policy.income(withdrawn, "week-two"), "second", 8)
        assertFalse(policy.summary(second).regular)
        val invested = policy.goalPurchase(first, 10)
        val later = deposit(policy.income(invested, "reward"), "later", 2)
        assertTrue(policy.summary(later).regular)
        assertEquals(10L, policy.withdraw(later, 10).contributions.first().investedInGoal)
    }

    @Test fun inheritedMoneyAndPromptedUnsafeDepositsDoNotCountAsIndependentPractice() {
        val old = policy.opening("goal", 100)
        assertFalse(policy.summary(policy.goalPurchase(old, 90)).regular)
        val assisted = policy.deposit(old, "hint", 1, 10, independent = false, needsCovered = true)
        val unsafe = policy.deposit(policy.income(assisted, "income"), "unsafe", 2, 10, true, false)
        assertEquals(0, policy.summary(unsafe).creditedDecisions)
    }

    @Test fun catchUpPracticeCanOpenProgressWithoutInventingRegularDeposits() {
        val noChance = policy.withdraw(policy.opening("old", 0), 5)
        val recovered = policy.completeSavingRecovery(noChance, "savings-rehearsal")
        assertTrue(policy.summary(recovered).ready)
        assertFalse(policy.summary(recovered).regular)
        assertEquals(0, policy.summary(recovered).creditedDecisions)
        assertFalse(policy.summary(recovered).complete)
    }

    @Test fun completedChapterQuizDoesNotForceAnotherBudgetOrInventManagedPlanEvidence() {
        assertFalse(policy.chapterReviewReady(null))
        assertFalse(policy.chapterReviewReady(PeriodReviewEvidence("q", "plan", true, false)))
        val recovery = PeriodReviewEvidence("catch-up", null, false, true, guidedRecovery = true)
        assertTrue(policy.chapterReviewReady(recovery))
        assertFalse(policy.reviewReady(recovery))
        assertNull(recovery.recoveryPlanRevisionId)
        val period = FinancialPeriod("period", "goal", 1, 1, 100, 0,
            needsProvided = true, savingPractice = policy.completeSavingRecovery(policy.opening("period", 0), "saving"),
            reviewEvidence = recovery)
        assertTrue(period.missingMilestones.isEmpty())
        assertEquals(listOf(FinancialMilestone.REVIEW_PLAN),
            period.copy(reviewEvidence = recovery.copy(answerCorrect = false)).missingMilestones)
    }

    @Test fun managedPlanEvidenceRequiresKnownComparisonOrRealRevision() {
        assertFalse(policy.reviewReady(null))
        assertFalse(policy.reviewReady(PeriodReviewEvidence("q", "plan", true, false)))
        assertFalse(policy.reviewReady(PeriodReviewEvidence("q", null, false, true)))
        assertFalse(policy.reviewReady(PeriodReviewEvidence("q", "plan", true, true, managedPlan = false)))
        assertTrue(policy.reviewReady(PeriodReviewEvidence("q", "plan", true, true, managedPlan = true)))
        assertFalse(policy.reviewReady(PeriodReviewEvidence("catch-up", null, false, true, guidedRecovery = true)))
        assertTrue(policy.reviewReady(PeriodReviewEvidence("catch-up", null, false, true, guidedRecovery = true,
            recoveryPlanRevisionId = "revised-realistic-plan")))
    }
}
