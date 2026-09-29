package ru.nksk.lctapp.domain.finance

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason

/** Five goal periods contain calendar weeks; their learning gates do not depend on the server. */
@Serializable
data class FinancialProgress(
    val currentPeriodId: String? = null,
    val periods: List<FinancialPeriod> = emptyList(),
    val plans: List<BudgetPlanRevision> = emptyList(),
    val practice: FinancialQuestion? = null,
) {
    val currentPeriod: FinancialPeriod? get() = periods.find { it.id == currentPeriodId }
    val currentPlan: BudgetPlanRevision? get() = plans.lastOrNull { it.periodId == currentPeriodId }

    init {
        require(periods.map { it.id }.distinct().size == periods.size)
        require(plans.map { it.id }.distinct().size == plans.size)
        require(currentPeriodId == null || periods.any { it.id == currentPeriodId && it.closedDay == null })
    }
}

@Serializable
data class FinancialPeriod(
    val id: String,
    val goalId: String,
    val ordinal: Int,
    val startedDay: Int,
    val openingAvailable: Long,
    val openingSavings: Long,
    val closedDay: Int? = null,
    val needsProvided: Boolean = false,
    val independentlySaved: Boolean = false,
    val reviewedPlan: Boolean = false,
    /** Existing progress is preserved; missing historical practice is not fabricated. */
    val imported: Boolean = false,
    /** Transaction-maintained period projection; authoritative operations remain in the audit ledger. */
    val income: Long = 0,
    val spentAvailable: Long = 0,
    val spentSavings: Long = 0,
    val deposited: Long = 0,
    val withdrawn: Long = 0,
    /** Null preserves the distinction between legacy progress and observed saving practice. */
    val savingPractice: SavingsPracticeState? = null,
    val reviewEvidence: PeriodReviewEvidence? = null,
) {
    init {
        require(id.isNotBlank() && goalId.isNotBlank())
        require(ordinal > 0 && startedDay > 0)
        require(closedDay == null || closedDay >= startedDay)
        require(listOf(openingAvailable, openingSavings, income, spentAvailable, spentSavings, deposited, withdrawn).all { it >= 0 })
        Math.addExact(openingAvailable, openingSavings)
    }
    val missingMilestones: List<FinancialMilestone> get() = buildList {
        if (!needsProvided) add(FinancialMilestone.PROVIDE_NEEDS)
        if (savingPractice?.let { FinancialProgressionPolicy.summary(it).ready } != true) add(FinancialMilestone.SAVE_FOR_GOAL)
        if (!FinancialProgressionPolicy.chapterReviewReady(reviewEvidence)) add(FinancialMilestone.REVIEW_PLAN)
    }
}

@Serializable
enum class FinancialMilestone { PROVIDE_NEEDS, SAVE_FOR_GOAL, REVIEW_PLAN }

/** Immutable intent, including the original; editing a draft does not mutate this record. */
@Serializable
data class BudgetPlanRevision(
    val id: String,
    val periodId: String?,
    val ordinal: Int,
    val day: Int,
    val availableBasis: Long,
    val allocation: BudgetPlan,
    val reason: BudgetRevisionReason,
    val previousId: String? = null,
    val knownNeeds: Long? = null,
    val causeActionId: String? = null,
) {
    init {
        require(id.isNotBlank() && (periodId == null || periodId.isNotBlank()))
        require(ordinal > 0 && day > 0 && availableBasis >= 0)
        require(knownNeeds == null || knownNeeds >= 0)
        require(previousId == null || previousId.isNotBlank() && previousId != id)
        require(causeActionId == null || causeActionId.isNotBlank())
    }
}

@Serializable
enum class FinancialQuestionKind { PLAN_REVIEW, TRANSACTION_ACCOUNTING, CONSEQUENCE, SAVING_PRACTICE }

@Serializable
data class FinancialAnswerOption(val id: String, val text: String)

/** A versioned task instance. The answer is checked by domain code, never supplied by the UI. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FinancialQuestion(
    val id: String,
    val kind: FinancialQuestionKind,
    val prompt: String,
    val options: List<FinancialAnswerOption>,
    val correctAnswerId: String,
    val explanation: String,
    val sourceActionIds: List<String> = emptyList(),
    val answeredOptionId: String? = null,
    val usedHint: Boolean = false,
    val attempts: Int = 0,
    val version: Int = 1,
    val ledgerTask: ru.nksk.lctapp.domain.analytics.AssessmentTask.ReadLedger? = null,
    val comparisonFamily: String? = null,
    /** Stored with the question so later plan edits cannot change what was actually reviewed. */
    val reviewEvidence: PeriodReviewEvidence? = null,
    /** Omitted for legacy questions so their signed snapshots retain the exact wire shape. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val series: FinancialPracticeSeries? = null,
) {
    init {
        require(options.map { it.id }.distinct().size == options.size)
        require(options.any { it.id == correctAnswerId })
        require(answeredOptionId == null || options.any { it.id == answeredOptionId })
        require(attempts >= 0)
        require(comparisonFamily == null || comparisonFamily.isNotBlank())
        require(series == null || series.remainingQuestions.all { it.kind == kind && it.id != id })
    }
    val correct: Boolean get() = answeredOptionId == correctAnswerId
}

/** Frozen follow-up questions keep a running practice independent of later spending or plan edits. */
@Serializable
data class FinancialPracticeSeries(
    val id: String,
    val questionNumber: Int,
    val totalQuestions: Int = 4,
    val remainingQuestions: List<FinancialQuestion> = emptyList(),
) {
    init {
        require(id.isNotBlank() && totalQuestions == 4 && questionNumber in 1..totalQuestions)
        require(remainingQuestions.size == totalQuestions - questionNumber)
        require(remainingQuestions.map { it.id }.distinct().size == remainingQuestions.size)
        require(remainingQuestions.all { it.series == null && it.answeredOptionId == null && it.attempts == 0 && !it.usedHint })
    }
    val isLast: Boolean get() = questionNumber == totalQuestions
}
