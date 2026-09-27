package ru.nksk.lctapp.domain.analytics

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Facts describe committed decisions, not UI-derived balances or assessed skill levels. */
@Serializable
data class AnalyticsFact(
    val eventId: String,
    val gameRunId: String,
    val episodeId: String,
    val actionId: String,
    val sequence: Long,
    val detail: FactDetail,
    val context: DecisionContext = DecisionContext(),
    val mode: AnalyticsMode = AnalyticsMode.REAL,
    val actor: AnalyticsActor = AnalyticsActor.CHILD,
    val learningContext: LearningContext = LearningContext.GAME,
    val contextFamily: String = "unspecified",
    val contentVersion: String = "1",
    val gameRulesVersion: String = "1",
    val schemaVersion: Int = 1,
) {
    init {
        require(listOf(eventId, gameRunId, episodeId, actionId, contextFamily,
            contentVersion, gameRulesVersion).all(String::isNotBlank))
        require(sequence >= 0 && schemaVersion > 0)
    }
}

@Serializable

enum class AnalyticsMode { REAL, DEMO, SIMULATION }
@Serializable
enum class AnalyticsActor { CHILD, SYSTEM, PARENT }
@Serializable
enum class LearningContext { GAME, COUNTERFACTUAL, TRAINING }
@Serializable
enum class Assistance { INFORMATION_ONLY, HINT, WORKED_EXAMPLE, ANSWER_REVEALED, ADULT_REPORTED }

/** Two real accounts. Plans and reserve intentions are not additional wallets. */
@Serializable
data class FinancialPosition(val available: Long, val savings: Long, val knownNeeds: Long) {
    init { require(available >= 0 && savings >= 0 && knownNeeds >= 0) }
    val coverage: Long get() = available - knownNeeds
    val shortfall: Long get() = (knownNeeds - available).coerceAtLeast(0)
}

@Serializable

data class DecisionContext(
    val presentationId: String? = null,
    val informationPresented: Boolean = false,
    val complete: Boolean = false,
    val before: FinancialPosition? = null,
    val after: FinancialPosition? = null,
    val alternativeAvailable: Boolean? = null,
    val fundingPlan: FundingPlan? = null,
    val assistance: Set<Assistance> = emptySet(),
    val adultHelpKnown: Boolean = false,
    val financialPeriodId: String? = null,
    val day: Int? = null,
) {
    init { require(day == null || day > 0) }
}

/** An explicit selected funding action, not automatically accessible savings or a maximum reward. */
@Serializable
data class FundingPlan(
    val id: String,
    val amount: Long,
    val selected: Boolean,
    val feasibleBeforeNeed: Boolean,
    val guaranteedAmount: Boolean,
    val source: FundingSource,
) {
    init { require(id.isNotBlank() && amount >= 0) }
}

@Serializable

enum class FundingSource { CONFIRMED_SAVINGS_WITHDRAWAL, GUARANTEED_INCOME }
@Serializable
enum class BudgetRevisionCause { INITIAL, KNOWN_NEED_OMITTED, UNEXPECTED_EXPENSE, NEW_INCOME, UNSPECIFIED }
@Serializable
enum class SavingMovementKind { DEPOSIT, WITHDRAWAL, GOAL_PURCHASE }
@Serializable
enum class QuestionPurpose { COMPARE_AMOUNTS, EXPLAIN_CAUSE, READ_LEDGER }
@Serializable
enum class PracticeKind { PLAN_REVIEW, SAVINGS_REHEARSAL }
@Serializable
enum class ComparisonSide { LEFT, RIGHT, EQUAL }
@Serializable
enum class LedgerQuestion { INCOME, EXPENSE, DEPOSIT, WITHDRAWAL, AVAILABLE_REMAINDER, SAVINGS_REMAINDER }

/** Only supported, typed evidence is accepted; tags alone never award a skill observation. */
@Serializable
sealed interface FactDetail {
    @Serializable
    @SerialName("interaction")
    data class Interaction(val name: String) : FactDetail

    /** Lifecycle provenance only. Merely running or viewing a simulation is never a skill success. */
    @Serializable
    @SerialName("time_machine_lifecycle")
    data class TimeMachineLifecycle(
        val event: TimeMachineLifecycleEvent,
        val simulationId: String,
        val resultHash: String,
        val sourceEntryId: String,
        val requestedSequence: Long,
        val reachedSequence: Long,
        val status: String,
        val questionId: String? = null,
        val submissionId: String? = null,
        val attempt: Int? = null,
    ) : FactDetail {
        init {
            require(listOf(simulationId, resultHash, sourceEntryId, status).all(String::isNotBlank))
            require(requestedSequence > 0 && reachedSequence >= 0 && reachedSequence <= requestedSequence)
            require(attempt == null || attempt > 0)
        }
    }

    /** Local progression practice remains observable without being relabelled as a financial skill. */
    @Serializable
    @SerialName("practice_answer")
    data class PracticeAnswer(
        val questionId: String,
        val kind: PracticeKind,
        val selectedAnswerId: String,
        val expectedAnswerId: String,
        val attempt: Int,
        val guidedRecovery: Boolean,
    ) : FactDetail {
        init { require(questionId.isNotBlank() && selectedAnswerId.isNotBlank() && expectedAnswerId.isNotBlank() && attempt > 0) }
        val correct: Boolean get() = selectedAnswerId == expectedAnswerId
    }

    @Serializable

    @SerialName("budget_confirmed")
    data class BudgetConfirmed(
        val planId: String,
        val planVersion: Int,
        val allocationBase: Long,
        val needs: Long,
        val wants: Long,
        val savings: Long,
        val reserve: Long,
        val cause: BudgetRevisionCause,
    ) : FactDetail {
        init {
            require(planId.isNotBlank() && planVersion > 0)
            require(listOf(allocationBase, needs, wants, savings, reserve).all { it >= 0 })
        }
        val allocated: Long get() = Math.addExact(Math.addExact(needs, wants), Math.addExact(savings, reserve))
    }

    @Serializable

    @SerialName("optional_purchase")
    data class OptionalPurchase(val itemId: String, val price: Long, val purchased: Boolean) : FactDetail {
        init { require(itemId.isNotBlank() && price >= 0) }
    }

    /** Ordered interval facts are preserved by the caller; a closing balance alone cannot form this fact. */
    @Serializable
    @SerialName("income_window")
    data class IncomeWindow(
        val windowId: String,
        val closed: Boolean,
        val historyComplete: Boolean,
        val managedDecisionIds: List<String>,
        val knownNeedsMet: Boolean,
        val avoidableUncoveredNeedIds: List<String> = emptyList(),
        val availableRemedyWasShown: Boolean = false,
    ) : FactDetail

    @Serializable

    @SerialName("saving_movement")
    data class SavingMovement(
        val operationId: String,
        val goalId: String,
        val incomeWindowId: String,
        val kind: SavingMovementKind,
        val amount: Long,
    ) : FactDetail {
        init { require(listOf(operationId, goalId, incomeWindowId).all(String::isNotBlank) && amount > 0) }
    }

    @Serializable

    @SerialName("saving_cycle_closed")
    data class SavingCycleClosed(val goalId: String, val historyComplete: Boolean) : FactDetail

    /** A previously declared deposit intention, resolved at its explicit deadline. */
    @Serializable
    @SerialName("saving_intention_resolved")
    data class SavingIntentionResolved(
        val goalId: String,
        val opportunityId: String,
        val promisedAmount: Long,
        val depositedAmount: Long,
        val priorityUnchanged: Boolean,
        val deliberatelySkipped: Boolean,
    ) : FactDetail {
        init { require(goalId.isNotBlank() && opportunityId.isNotBlank() && promisedAmount > 0 && depositedAmount >= 0) }
    }

    @Serializable

    @SerialName("desire_deferred")
    data class DesireDeferred(
        val itemId: String,
        val price: Long,
        val desireDeclared: Boolean,
        val priorityId: String?,
    ) : FactDetail {
        init { require(itemId.isNotBlank() && price >= 0) }
    }

    @Serializable

    @SerialName("priority_applied")
    data class PriorityApplied(
        val priorityId: String,
        val operationId: String,
        val amount: Long,
    ) : FactDetail {
        init { require(priorityId.isNotBlank() && operationId.isNotBlank() && amount > 0) }
    }

    @Serializable
    @SerialName("priority_conflict")
    data class PriorityConflict(
        val priorityId: String,
        val conditionsUnchanged: Boolean,
        val alternativePreservedPriority: Boolean,
        val explanation: AssessmentTask.ExplainCause?,
    ) : FactDetail

    @Serializable

    @SerialName("reserve_decision")
    data class ReserveDecision(
        val intentionId: String,
        val declaredAmount: Long,
        val remainingAmount: Long,
        val usedForUnexpectedExpense: Long,
        val intervalClosed: Boolean,
        val applications: List<ReserveApplication> = emptyList(),
    ) : FactDetail {
        init { require(listOf(declaredAmount, remainingAmount, usedForUnexpectedExpense).all { it >= 0 }) }
    }

    @Serializable

    @SerialName("unexpected_expense")
    data class UnexpectedExpense(val operationId: String, val amount: Long, val previouslyDisclosed: Boolean) : FactDetail {
        init { require(operationId.isNotBlank() && amount > 0) }
    }

    @Serializable

    @SerialName("recovery_action")
    data class RecoveryAction(
        val expenseOperationId: String,
        val selectedActionId: String,
        val completed: Boolean,
    ) : FactDetail

    @Serializable

    @SerialName("resource_choice")
    data class ResourceChoice(
        val chosenOptionId: String,
        val chosenCost: ResourceCost,
        val alternativeCost: ResourceCost,
        val energyBefore: Int,
        val availableTimeBefore: Int,
        val priorityId: String?,
        /** Priority cost beyond knownNeeds; do not include the same food obligation twice. */
        val priorityMoney: Long,
        val priorityEnergy: Int,
        val priorityTime: Int,
        val chosenPriorityFeasible: Boolean? = null,
        val alternativePriorityFeasible: Boolean? = null,
    ) : FactDetail {
        init { require(energyBefore >= 0 && availableTimeBefore >= 0 && priorityMoney >= 0 && priorityEnergy >= 0 && priorityTime >= 0) }
    }

    @Serializable

    @SerialName("earning_plan")
    data class EarningPlan(
        val offerId: String,
        val priorityId: String?,
        val startedDay: Int,
        val deadlineDay: Int,
        val effortRequired: Int,
        val availableEffortAtChoice: Int,
        val maximumReward: Long,
    ) : FactDetail {
        init { require(startedDay > 0 && deadlineDay > 0 && effortRequired >= 0 && availableEffortAtChoice >= 0 && maximumReward >= 0) }
    }

    @Serializable

    @SerialName("earning_completed")
    data class EarningCompleted(val offerId: String, val day: Int, val actualReward: Long, val actualRewardAccountedFor: Boolean) : FactDetail {
        init { require(day > 0 && actualReward >= 0) }
    }

    @Serializable

    @SerialName("question_answer")
    data class QuestionAnswer(
        val questionId: String,
        val attempt: Int,
        val task: AssessmentTask,
        val answerWasRevealed: Boolean = false,
        val seriesClosed: Boolean = true,
        val comparisonFamily: String? = null,
        val simulationId: String? = null,
    ) : FactDetail {
        init { require(questionId.isNotBlank() && attempt > 0) }
    }

    @Serializable

    @SerialName("comparable_application")
    data class ComparableApplication(
        val comparisonFamily: String,
        val sourceActionId: String,
        val priorityPreserved: Boolean,
    ) : FactDetail
}

@Serializable
enum class TimeMachineLifecycleEvent {
    @SerialName("time_machine_simulation_started") SIMULATION_STARTED,
    @SerialName("time_machine_simulation_completed") SIMULATION_COMPLETED,
    @SerialName("time_machine_simulation_diverged") SIMULATION_DIVERGED,
    @SerialName("time_machine_simulation_unavailable") SIMULATION_UNAVAILABLE,
    @SerialName("time_machine_question_presented") QUESTION_PRESENTED,
    @SerialName("time_machine_explanation_shown") EXPLANATION_SHOWN,
}

/** Attribution to an actual external expense, not a separate reserve money account. */
@Serializable
data class ReserveApplication(val expenseOperationId: String, val amount: Long) {
    init { require(expenseOperationId.isNotBlank() && amount > 0) }
}

@Serializable

data class ResourceCost(val money: Long, val energy: Int, val time: Int) {
    init { require(money >= 0 && energy >= 0 && time >= 0) }
}

@Serializable

sealed interface AssessmentTask {
    val purpose: QuestionPurpose
    fun isCorrect(): Boolean

    @Serializable

    @SerialName("compare_amounts")
    data class CompareAmounts(val left: Long, val right: Long, val chosen: ComparisonSide) : AssessmentTask {
        init { require(left >= 0 && right >= 0) }
        override val purpose = QuestionPurpose.COMPARE_AMOUNTS
        override fun isCorrect() = chosen == when {
            left > right -> ComparisonSide.LEFT
            left < right -> ComparisonSide.RIGHT
            else -> ComparisonSide.EQUAL
        }
    }

    /** expectedOptionId is from validated authored content or the computed counterfactual result. */
    @Serializable
    @SerialName("explain_cause")
    data class ExplainCause(val expectedOptionId: String, val chosenOptionId: String, val sourceFactIds: List<String>) : AssessmentTask {
        init { require(expectedOptionId.isNotBlank() && chosenOptionId.isNotBlank() && sourceFactIds.isNotEmpty()) }
        override val purpose = QuestionPurpose.EXPLAIN_CAUSE
        override fun isCorrect() = expectedOptionId == chosenOptionId
    }

    @Serializable

    @SerialName("read_ledger")
    data class ReadLedger(
        val openingAvailable: Long,
        val openingSavings: Long,
        val entries: List<LedgerEntry>,
        val question: LedgerQuestion,
        val answer: Long,
    ) : AssessmentTask {
        init { require(openingAvailable >= 0 && openingSavings >= 0) }
        override val purpose = QuestionPurpose.READ_LEDGER
        override fun isCorrect() = answer == expectedAnswer()
        fun expectedAnswer(): Long {
            val distinct = entries.distinctBy { it.operationId }
            require(distinct.size == entries.size) { "A ledger question cannot duplicate operations" }
            fun total(kind: LedgerKind) = distinct.filter { it.kind == kind }.fold(0L) { sum, entry -> Math.addExact(sum, entry.amount) }
            val income = total(LedgerKind.INCOME)
            val availableExpense = total(LedgerKind.AVAILABLE_EXPENSE)
            val goalExpense = total(LedgerKind.SAVINGS_EXPENSE)
            val deposit = total(LedgerKind.DEPOSIT)
            val withdrawal = total(LedgerKind.WITHDRAWAL)
            return when (question) {
                LedgerQuestion.INCOME -> income
                LedgerQuestion.EXPENSE -> Math.addExact(availableExpense, goalExpense)
                LedgerQuestion.DEPOSIT -> deposit
                LedgerQuestion.WITHDRAWAL -> withdrawal
                LedgerQuestion.AVAILABLE_REMAINDER -> Math.subtractExact(Math.subtractExact(Math.addExact(Math.addExact(openingAvailable, income), withdrawal), availableExpense), deposit)
                LedgerQuestion.SAVINGS_REMAINDER -> Math.subtractExact(Math.subtractExact(Math.addExact(openingSavings, deposit), withdrawal), goalExpense)
            }
        }
    }
}

@Serializable

enum class LedgerKind { INCOME, AVAILABLE_EXPENSE, SAVINGS_EXPENSE, DEPOSIT, WITHDRAWAL }
@Serializable
data class LedgerEntry(val operationId: String, val kind: LedgerKind, val amount: Long) {
    init { require(operationId.isNotBlank() && amount >= 0) }
}
