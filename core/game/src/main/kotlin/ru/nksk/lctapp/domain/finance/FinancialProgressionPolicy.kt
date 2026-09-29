package ru.nksk.lctapp.domain.finance

import kotlinx.serialization.Serializable

/** Temporary gameplay pacing, not a validated threshold of financial mastery. */
data class FinancialProgressionConfig(
    val id: String = "financial-practice-v1-temporary",
    val savingDecisions: Int = 2,
    val savingDays: Int = 2,
    val incomeOpportunities: Int = 2,
) {
    init { require(id.isNotBlank() && savingDecisions >= 2 && savingDays >= 2 && incomeOpportunities >= 2) }
}

@Serializable
data class SavingsContribution(
    val operationId: String,
    val day: Int,
    val incomeOpportunityId: String,
    val amount: Long,
    val liquid: Long,
    val investedInGoal: Long = 0,
    val independent: Boolean,
    val needsCovered: Boolean,
) {
    init {
        require(operationId.isNotBlank() && incomeOpportunityId.isNotBlank() && day > 0)
        require(amount > 0 && liquid >= 0 && investedInGoal >= 0 && Math.addExact(liquid, investedInGoal) <= amount)
    }
    val retained: Long get() = Math.addExact(liquid, investedInGoal)
}

/** Compact local gate evidence. The complete original operations remain in the audit journal. */
@Serializable
data class SavingsPracticeState(
    /** Unattributed liquid savings brought into the period; never an independent contribution. */
    val openingSavings: Long = 0,
    val incomeOpportunityId: String = "opening",
    val contributions: List<SavingsContribution> = emptyList(),
    val recoveryQuestionId: String? = null,
    val complete: Boolean = true,
) {
    init {
        require(openingSavings >= 0 && incomeOpportunityId.isNotBlank())
        require(contributions.map { it.operationId }.distinct().size == contributions.size)
        require(recoveryQuestionId == null || recoveryQuestionId.isNotBlank())
    }
}

@Serializable
data class PeriodReviewEvidence(
    val questionId: String,
    val planRevisionId: String?,
    val comparedKnownFacts: Boolean,
    val answerCorrect: Boolean,
    /** Authored catch-up practice is explicit; it cannot fabricate a missing original plan. */
    val guidedRecovery: Boolean = false,
    val managedPlan: Boolean? = null,
    val recoveryPlanRevisionId: String? = null,
) {
    init {
        require(questionId.isNotBlank() && (planRevisionId == null || planRevisionId.isNotBlank()))
        require(recoveryPlanRevisionId == null || recoveryPlanRevisionId.isNotBlank())
    }
}

data class SavingPracticeSummary(
    val regular: Boolean,
    val creditedDecisions: Int,
    val days: Int,
    val incomeOpportunities: Int,
    val retainedAmount: Long,
    val guidedRecovery: Boolean,
    val complete: Boolean,
) {
    val ready: Boolean get() = regular || guidedRecovery
}

/** Pure local progression. It never creates parent-profile successes or changes either money account. */
object FinancialProgressionPolicy {
    fun opening(periodId: String, savings: Long) = SavingsPracticeState(savings, "opening:$periodId")

    /** Call only for a committed positive income, not for a withdrawal or a promised maximum reward. */
    fun income(state: SavingsPracticeState, actionId: String): SavingsPracticeState {
        require(actionId.isNotBlank())
        return state.copy(incomeOpportunityId = "income:$actionId")
    }

    fun deposit(state: SavingsPracticeState, operationId: String, day: Int, amount: Long,
        independent: Boolean, needsCovered: Boolean): SavingsPracticeState {
        state.contributions.find { it.operationId == operationId }?.let { original ->
            require(original.day == day && original.amount == amount && original.independent == independent &&
                original.needsCovered == needsCovered) { "Conflicting saving decision" }
            return state
        }
        return state.copy(contributions = state.contributions + SavingsContribution(operationId, day,
            state.incomeOpportunityId, amount, amount, independent = independent, needsCovered = needsCovered))
    }

    /** A withdrawal cancels liquid contributions newest-first; it cannot undo an already purchased part. */
    fun withdraw(state: SavingsPracticeState, amount: Long): SavingsPracticeState {
        require(amount >= 0)
        var left = amount
        val values = state.contributions.toMutableList()
        for (index in values.indices.reversed()) {
            val take = minOf(left, values[index].liquid)
            values[index] = values[index].copy(liquid = values[index].liquid - take)
            left -= take
        }
        val takeOpening = minOf(left, state.openingSavings)
        left -= takeOpening
        return state.copy(openingSavings = state.openingSavings - takeOpening, contributions = values,
            complete = state.complete && left == 0L)
    }

    /** Conservatively consume inherited money first; purchases retain actual contributed progress. */
    fun goalPurchase(state: SavingsPracticeState, amount: Long): SavingsPracticeState {
        require(amount >= 0)
        val fromOpening = minOf(amount, state.openingSavings)
        var left = amount - fromOpening
        val values = state.contributions.map { value ->
            val take = minOf(left, value.liquid)
            left -= take
            value.copy(liquid = value.liquid - take, investedInGoal = Math.addExact(value.investedInGoal, take))
        }
        return state.copy(openingSavings = state.openingSavings - fromOpening, contributions = values,
            complete = state.complete && left == 0L)
    }

    /** Caller verifies an answered savings rehearsal; this grants practice access, not mastery evidence. */
    fun completeSavingRecovery(state: SavingsPracticeState, questionId: String): SavingsPracticeState {
        require(questionId.isNotBlank())
        return state.copy(recoveryQuestionId = questionId)
    }

    fun summary(state: SavingsPracticeState, config: FinancialProgressionConfig = FinancialProgressionConfig()): SavingPracticeSummary {
        val credited = state.contributions.filter { it.independent && it.needsCovered && it.retained > 0 }
        // Splitting a transfer into many taps in one situation remains one decision opportunity.
        val decisions = credited.distinctBy { it.day to it.incomeOpportunityId }
        val days = decisions.map { it.day }.distinct().size
        val incomes = decisions.map { it.incomeOpportunityId }.distinct().size
        return SavingPracticeSummary(state.complete && decisions.size >= config.savingDecisions &&
            days >= config.savingDays && incomes >= config.incomeOpportunities,
            decisions.size, days, incomes, credited.fold(0L) { total, item -> Math.addExact(total, item.retained) },
            state.recoveryQuestionId != null, state.complete)
    }

    /** Finishing the chapter review does not require changing an otherwise confirmed budget. */
    fun chapterReviewReady(evidence: PeriodReviewEvidence?): Boolean = evidence?.answerCorrect == true

    /** Actual plan management remains distinct from answering the chapter's practice question. */
    fun reviewReady(evidence: PeriodReviewEvidence?): Boolean = evidence?.let {
        it.answerCorrect && (it.comparedKnownFacts && it.planRevisionId != null && it.managedPlan == true ||
            it.recoveryPlanRevisionId != null)
    } == true
}
