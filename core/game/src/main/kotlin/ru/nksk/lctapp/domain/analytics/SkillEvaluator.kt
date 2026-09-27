package ru.nksk.lctapp.domain.analytics

import ru.nksk.lctapp.domain.analytics.FactDetail.*
import ru.nksk.lctapp.domain.analytics.SkillId.*
import ru.nksk.lctapp.domain.analytics.ObservationOutcome.*
import ru.nksk.lctapp.domain.analytics.ObservationReason.*

/** Stateless replay of committed evidence. Input order and duplicate delivery do not change results. */
class SkillEvaluator {
    fun evaluate(facts: List<AnalyticsFact>): List<SkillObservation> {
        val unique = facts.groupBy { it.eventId }.map { (id, occurrences) ->
            require(occurrences.all { it == occurrences.first() }) { "Conflicting analytics fact: $id" }
            occurrences.first()
        }.filter { it.mode == AnalyticsMode.REAL }
            .sortedWith(compareBy<AnalyticsFact> { it.gameRunId }.thenBy { it.sequence }.thenBy { it.eventId })
        return unique.groupBy { it.gameRunId to it.episodeId }.flatMap { (_, episode) ->
            SkillId.entries.mapNotNull { skill ->
                val relevant = episode.filter { skill in candidates(it) }
                if (relevant.isEmpty()) null else evaluateEpisode(skill, relevant)
            }
        }.sortedWith(compareBy<SkillObservation> { it.gameRunId }.thenBy { it.skill.ordinal }.thenBy { it.episodeId })
    }

    fun project(gameRunId: String, facts: List<AnalyticsFact>): List<SkillProfile> {
        require(gameRunId.isNotBlank())
        val observations = evaluate(facts.filter { it.gameRunId == gameRunId })
        return SkillId.entries.map { skill ->
            SkillProfile(gameRunId, skill, observations.filter { it.skill == skill })
        }
    }

    private fun candidates(fact: AnalyticsFact): Set<SkillId> {
        if (fact.learningContext == LearningContext.COUNTERFACTUAL && fact.detail !is QuestionAnswer) return emptySet()
        val systemSummary = fact.detail is UnexpectedExpense || fact.detail is IncomeWindow ||
            fact.detail is SavingCycleClosed || fact.detail is EarningCompleted
        if (fact.actor != AnalyticsActor.CHILD && !(fact.actor == AnalyticsActor.SYSTEM && systemSummary)) return emptySet()
        return when (val d = fact.detail) {
            is Interaction, is PracticeAnswer, is TimeMachineLifecycle -> emptySet()
            is BudgetConfirmed -> setOf(PLAN_BUDGET)
            is OptionalPurchase -> setOf(PRIORITIZE_NEEDS)
            is IncomeWindow -> setOf(MAKE_MONEY_LAST)
            is SavingMovement, is SavingCycleClosed, is SavingIntentionResolved -> setOf(SAVE_FOR_GOAL)
            is DesireDeferred, is PriorityApplied, is PriorityConflict -> setOf(DELAY_PURCHASE)
            is ReserveDecision -> setOf(BUILD_EMERGENCY_FUND)
            is UnexpectedExpense, is RecoveryAction -> setOf(ADAPT_AFTER_EXPENSE)
            is ResourceChoice -> setOf(COMPARE_COSTS)
            is EarningPlan, is EarningCompleted -> setOf(PLAN_EXTRA_INCOME)
            is ComparableApplication -> setOf(RECONSIDER_DECISION)
            is QuestionAnswer -> when (d.task.purpose) {
                QuestionPurpose.COMPARE_AMOUNTS -> if (fact.learningContext == LearningContext.GAME) setOf(COMPARE_AMOUNTS) else emptySet()
                QuestionPurpose.EXPLAIN_CAUSE -> setOf(RECONSIDER_DECISION)
                QuestionPurpose.READ_LEDGER -> setOf(UNDERSTAND_INCOME_AND_EXPENSES)
            }
        }
    }

    private fun evaluateEpisode(skill: SkillId, facts: List<AnalyticsFact>): SkillObservation = try {
        when (skill) {
            COMPARE_AMOUNTS -> answers(skill, facts)
            PLAN_BUDGET -> budget(facts)
            PRIORITIZE_NEEDS -> purchase(facts)
            MAKE_MONEY_LAST -> interval(facts)
            SAVE_FOR_GOAL -> savings(facts)
            DELAY_PURCHASE -> desire(facts)
            BUILD_EMERGENCY_FUND -> reserve(facts)
            ADAPT_AFTER_EXPENSE -> recovery(facts)
            COMPARE_COSTS -> resources(facts)
            PLAN_EXTRA_INCOME -> earnings(facts)
            RECONSIDER_DECISION -> explanation(facts)
            UNDERSTAND_INCOME_AND_EXPENSES -> answers(skill, facts)
        }
    } catch (_: ArithmeticException) {
        observation(skill, facts, INSUFFICIENT_DATA, INVALID_CONTEXT)
    } catch (_: IllegalArgumentException) {
        observation(skill, facts, INSUFFICIENT_DATA, INVALID_CONTEXT)
    }

    private fun budget(facts: List<AnalyticsFact>): SkillObservation {
        val fact = facts.last()
        val d = fact.detail as BudgetConfirmed
        contextIssue(PLAN_BUDGET, facts, fact)?.let { return it }
        val before = fact.context.before ?: return missing(PLAN_BUDGET, facts)
        if (d.allocated > d.allocationBase) return observation(PLAN_BUDGET, facts, DIFFICULTY, INFEASIBLE_ALLOCATION)
        // Scarcity caused before this decision is not a child's allocation mistake.
        if (before.shortfall > 0 && d.allocationBase < before.knownNeeds) {
            return observation(PLAN_BUDGET, facts, NEUTRAL, ORIGINAL_SHORTFALL,
                measures = mapOf("baselineShortfall" to before.shortfall))
        }
        val plannedGap = (before.knownNeeds - d.needs).coerceAtLeast(0)
        if (d.needs < before.knownNeeds && fact.context.alternativeAvailable == true && !fundsKnownNeeds(fact.context, before, plannedGap)) {
            return observation(PLAN_BUDGET, facts, DIFFICULTY, KNOWN_NEED_OMITTED,
                measures = mapOf("plannedNeeds" to d.needs, "knownNeeds" to before.knownNeeds))
        }
        if (d.needs < before.knownNeeds) return observation(PLAN_BUDGET, facts, NEUTRAL, UNKNOWN_REVISION_CAUSE)
        return observation(PLAN_BUDGET, facts, SUPPORTED, VALID_PLAN)
    }

    private fun purchase(facts: List<AnalyticsFact>): SkillObservation {
        val fact = facts.last()
        val d = fact.detail as OptionalPurchase
        contextIssue(PRIORITIZE_NEEDS, facts, fact)?.let { return it }
        val before = fact.context.before ?: return missing(PRIORITIZE_NEEDS, facts)
        val after = fact.context.after ?: return missing(PRIORITIZE_NEEDS, facts)
        if (before.available < d.price || fact.context.alternativeAvailable != true) {
            return observation(PRIORITIZE_NEEDS, facts, NEUTRAL, NO_AVAILABLE_CHOICE)
        }
        if (after.coverage >= 0) return observation(PRIORITIZE_NEEDS, facts, SUPPORTED, NEEDS_COVERED)
        if (fundsKnownNeeds(fact.context, after)) return observation(PRIORITIZE_NEEDS, facts, SUPPORTED, FUNDING_PLAN_SELECTED)
        if (d.purchased && after.shortfall > before.shortfall) return observation(PRIORITIZE_NEEDS, facts, DIFFICULTY, AVOIDABLE_NEEDS_GAP)
        return observation(PRIORITIZE_NEEDS, facts, NEUTRAL, ORIGINAL_SHORTFALL)
    }

    private fun interval(facts: List<AnalyticsFact>): SkillObservation {
        val fact = facts.last()
        val d = fact.detail as IncomeWindow
        if (!d.closed) return pending(MAKE_MONEY_LAST, facts)
        contextIssue(MAKE_MONEY_LAST, facts, fact)?.let { return it }
        if (!d.historyComplete) return missing(MAKE_MONEY_LAST, facts)
        if (d.managedDecisionIds.isEmpty()) return observation(MAKE_MONEY_LAST, facts, NEUTRAL, NO_MANAGED_DECISION)
        if (d.avoidableUncoveredNeedIds.isNotEmpty() && d.availableRemedyWasShown) {
            return observation(MAKE_MONEY_LAST, facts, DIFFICULTY, AVOIDABLE_NEEDS_GAP)
        }
        if (d.knownNeedsMet) return observation(MAKE_MONEY_LAST, facts, SUPPORTED, INTERVAL_COVERED)
        return observation(MAKE_MONEY_LAST, facts, NEUTRAL, ORIGINAL_SHORTFALL)
    }

    private fun savings(facts: List<AnalyticsFact>): SkillObservation {
        val movementFacts = facts.filter { it.detail is SavingMovement }
        val closing = facts.lastOrNull { it.detail is SavingCycleClosed }
        if (closing == null) return pending(SAVE_FOR_GOAL, facts)
        contextIssue(SAVE_FOR_GOAL, facts, closing)?.let { return it }
        if (!(closing.detail as SavingCycleClosed).historyComplete) return missing(SAVE_FOR_GOAL, facts)
        val unfulfilled = facts.filter { fact ->
            val intention = fact.detail as? SavingIntentionResolved
            val before = fact.context.before
            intention != null && intention.goalId == closing.detail.goalId && intention.priorityUnchanged &&
                intention.deliberatelySkipped && intention.depositedAmount < intention.promisedAmount &&
                fact.context.complete && fact.context.informationPresented && fact.context.alternativeAvailable == true &&
                before != null && before.coverage >= intention.promisedAmount
        }.distinctBy { (it.detail as SavingIntentionResolved).opportunityId }
        if (unfulfilled.size > 1) return observation(SAVE_FOR_GOAL, facts, DIFFICULTY, SAVING_INTENTION_NOT_APPLIED,
            measures = mapOf("unfulfilledIntentions" to unfulfilled.size.toLong()))
        if (movementFacts.isEmpty()) return observation(SAVE_FOR_GOAL, facts, NEUTRAL, NO_SAVING_OPPORTUNITY)
        if (movementFacts.any { !it.context.complete || !it.context.informationPresented }) return missing(SAVE_FOR_GOAL, facts)
        val operations = movementFacts.map { it.detail as SavingMovement }
        val goalId = closing.detail.goalId
        if (operations.any { it.goalId != goalId }) return missing(SAVE_FOR_GOAL, facts)
        val unique = operations.distinctBy { it.operationId }
        require(unique.size == operations.size) { "Repeated money operation" }
        fun total(kind: SavingMovementKind) = unique.filter { it.kind == kind }.fold(0L) { sum, op -> Math.addExact(sum, op.amount) }
        val deposited = total(SavingMovementKind.DEPOSIT)
        val withdrawn = total(SavingMovementKind.WITHDRAWAL)
        val purchased = total(SavingMovementKind.GOAL_PURCHASE)
        val netContribution = deposited - withdrawn
        // A later withdrawal cancels retained contributions, even across income windows.
        // Goal purchases retain the contribution as owned progress rather than liquid savings.
        val retained = linkedMapOf<String, Long>()
        for (operation in unique) when (operation.kind) {
            SavingMovementKind.DEPOSIT -> retained[operation.incomeWindowId] = Math.addExact(retained[operation.incomeWindowId] ?: 0L, operation.amount)
            SavingMovementKind.WITHDRAWAL -> {
                var remaining = operation.amount
                for (window in retained.keys.toList().asReversed()) {
                    val taken = minOf(retained.getValue(window), remaining)
                    retained[window] = retained.getValue(window) - taken
                    remaining -= taken
                    if (remaining == 0L) break
                }
            }
            SavingMovementKind.GOAL_PURCHASE -> Unit
        }
        val windows = retained.filterValues { it > 0 }.keys
        val measures = mapOf("deposited" to deposited, "withdrawn" to withdrawn, "spentOnGoal" to purchased,
            "netContribution" to netContribution, "incomeWindows" to windows.size.toLong())
        if (netContribution <= 0) return observation(SAVE_FOR_GOAL, facts, NEUTRAL, CIRCULAR_TRANSFER, measures = measures)
        val needsPreserved = movementFacts.filter { (it.detail as SavingMovement).kind == SavingMovementKind.DEPOSIT }
            .all { it.context.after?.let { p -> p.coverage >= 0 || fundsKnownNeeds(it.context, p) } == true }
        if (!needsPreserved) return observation(SAVE_FOR_GOAL, facts, NEUTRAL, ORIGINAL_SHORTFALL, measures = measures)
        // More than one income opportunity establishes a sequence; this is not a mastery threshold.
        if (windows.size < 2) return observation(SAVE_FOR_GOAL, facts, INSUFFICIENT_DATA, NO_SAVING_OPPORTUNITY, measures = measures)
        return observation(SAVE_FOR_GOAL, facts, SUPPORTED, SAVING_PROGRESS, measures = measures)
    }

    private fun desire(facts: List<AnalyticsFact>): SkillObservation {
        val deferrals = facts.filter { it.detail is DesireDeferred }
        val fact = deferrals.firstOrNull { candidate ->
            val desire = candidate.detail as DesireDeferred
            desire.desireDeclared && desire.priorityId != null && candidate.context.complete &&
                candidate.context.informationPresented && candidate.context.presentationId != null &&
                candidate.context.alternativeAvailable == true &&
                candidate.context.before?.let { it.available >= desire.price } == true
        } ?: deferrals.firstOrNull() ?: return missing(DELAY_PURCHASE, facts)
        val d = fact.detail as DesireDeferred
        contextIssue(DELAY_PURCHASE, facts, fact)?.let { return it }
        if (!d.desireDeclared) return observation(DELAY_PURCHASE, facts, NEUTRAL, DESIRE_UNKNOWN)
        if (d.priorityId == null) return observation(DELAY_PURCHASE, facts, NEUTRAL, PRIORITY_UNKNOWN)
        val before = fact.context.before ?: return missing(DELAY_PURCHASE, facts)
        if (before.available < d.price || fact.context.alternativeAvailable != true) return observation(DELAY_PURCHASE, facts, NEUTRAL, NO_AVAILABLE_CHOICE)
        val conflict = facts.lastOrNull { later ->
            val conflict = later.detail as? PriorityConflict
            later.sequence > fact.sequence && conflict?.priorityId == d.priorityId
        }
        if (conflict != null) {
            contextIssue(DELAY_PURCHASE, facts, conflict)?.let { return it }
            val c = conflict.detail as PriorityConflict
            if (c.conditionsUnchanged && c.alternativePreservedPriority && c.explanation?.isCorrect() == false &&
                conflict.context.alternativeAvailable == true) return observation(DELAY_PURCHASE, facts, DIFFICULTY, PRIORITY_CONFLICT_MISUNDERSTOOD)
            return observation(DELAY_PURCHASE, facts, NEUTRAL, UNKNOWN_REVISION_CAUSE)
        }
        val applied = facts.firstOrNull { it.sequence > fact.sequence && (it.detail as? PriorityApplied)?.priorityId == d.priorityId }
            ?: return pending(DELAY_PURCHASE, facts)
        contextIssue(DELAY_PURCHASE, facts, applied)?.let { return it }
        return observation(DELAY_PURCHASE, facts, SUPPORTED, PRIORITY_APPLIED)
    }

    private fun reserve(facts: List<AnalyticsFact>): SkillObservation {
        val overstated = facts.firstOrNull { candidate ->
            val declared = candidate.detail as ReserveDecision
            val position = candidate.context.before
            candidate.context.complete && candidate.context.informationPresented && candidate.context.presentationId != null &&
                position != null && position.shortfall == 0L && candidate.context.alternativeAvailable == true &&
                declared.declaredAmount > position.coverage.coerceAtLeast(0)
        }
        if (overstated != null) return observation(BUILD_EMERGENCY_FUND, facts, DIFFICULTY, RESERVE_OVERSTATED)
        // A later replan cannot erase an earlier verified application of the same week's reserve.
        val fact = facts.lastOrNull { candidate ->
            val value = candidate.detail as ReserveDecision
            value.intervalClosed && value.usedForUnexpectedExpense > 0 && candidate.context.complete &&
                Math.addExact(value.remainingAmount, value.usedForUnexpectedExpense) == value.declaredAmount
        } ?: facts.lastOrNull { (it.detail as ReserveDecision).intervalClosed } ?: facts.last()
        val d = fact.detail as ReserveDecision
        contextIssue(BUILD_EMERGENCY_FUND, facts, fact)?.let { return it }
        val before = fact.context.before ?: return missing(BUILD_EMERGENCY_FUND, facts)
        if (d.declaredAmount == 0L) return observation(BUILD_EMERGENCY_FUND, facts, NEUTRAL, NO_RESERVE_INTENTION)
        if (d.declaredAmount > before.coverage.coerceAtLeast(0)) {
            return observation(BUILD_EMERGENCY_FUND, facts, if (before.shortfall == 0L && fact.context.alternativeAvailable == true) DIFFICULTY else NEUTRAL,
                if (before.shortfall == 0L) RESERVE_OVERSTATED else ORIGINAL_SHORTFALL)
        }
        if (!d.intervalClosed) return pending(BUILD_EMERGENCY_FUND, facts)
        if (d.applications.map { it.expenseOperationId }.distinct().size != d.applications.size ||
            d.applications.fold(0L) { total, item -> Math.addExact(total, item.amount) } != d.usedForUnexpectedExpense)
            return observation(BUILD_EMERGENCY_FUND, facts, INSUFFICIENT_DATA, INVALID_CONTEXT)
        val accounted = Math.addExact(d.remainingAmount, d.usedForUnexpectedExpense)
        if (accounted > d.declaredAmount) return observation(BUILD_EMERGENCY_FUND, facts, INSUFFICIENT_DATA, INVALID_CONTEXT)
        if (accounted == d.declaredAmount) return observation(BUILD_EMERGENCY_FUND, facts, SUPPORTED,
            if (d.usedForUnexpectedExpense > 0) RESERVE_USED else RESERVE_PRESERVED,
            measures = mapOf("usedForUnexpectedExpense" to d.usedForUnexpectedExpense,
                "remainingReserve" to d.remainingAmount, "attributedExpenses" to d.applications.size.toLong()))
        return observation(BUILD_EMERGENCY_FUND, facts, NEUTRAL, UNKNOWN_REVISION_CAUSE)
    }

    private fun recovery(facts: List<AnalyticsFact>): SkillObservation {
        val expense = facts.firstOrNull { it.detail is UnexpectedExpense }
            ?: return observation(ADAPT_AFTER_EXPENSE, facts, NEUTRAL, NO_EXTERNAL_EXPENSE)
        val d = expense.detail as UnexpectedExpense
        if (d.previouslyDisclosed) return observation(ADAPT_AFTER_EXPENSE, facts, NEUTRAL, CHANGE_NOT_UNEXPECTED)
        val action = facts.lastOrNull { (it.detail as? RecoveryAction)?.expenseOperationId == d.operationId && it.sequence > expense.sequence }
            ?: return pending(ADAPT_AFTER_EXPENSE, facts)
        contextIssue(ADAPT_AFTER_EXPENSE, facts, action)?.let { return it }
        if (!(action.detail as RecoveryAction).completed) return pending(ADAPT_AFTER_EXPENSE, facts)
        val before = action.context.before ?: return missing(ADAPT_AFTER_EXPENSE, facts)
        val after = action.context.after ?: return missing(ADAPT_AFTER_EXPENSE, facts)
        if (after.coverage >= 0 || fundsKnownNeeds(action.context, after)) return observation(ADAPT_AFTER_EXPENSE, facts, SUPPORTED, RECOVERY_COMPLETED)
        if (after.shortfall > before.shortfall && action.context.alternativeAvailable == true) return observation(ADAPT_AFTER_EXPENSE, facts, DIFFICULTY, GAP_WORSENED)
        return observation(ADAPT_AFTER_EXPENSE, facts, NEUTRAL, ORIGINAL_SHORTFALL)
    }

    private fun resources(facts: List<AnalyticsFact>): SkillObservation {
        val fact = facts.last()
        val d = fact.detail as ResourceChoice
        contextIssue(COMPARE_COSTS, facts, fact)?.let { return it }
        val before = fact.context.before ?: return missing(COMPARE_COSTS, facts)
        if (d.priorityId == null) return observation(COMPARE_COSTS, facts, NEUTRAL, PRIORITY_UNKNOWN)
        fun feasible(cost: ResourceCost) = cost.money <= before.available && cost.energy <= d.energyBefore && cost.time <= d.availableTimeBefore
        fun preserves(cost: ResourceCost) = feasible(cost) &&
            before.available - cost.money >= Math.addExact(before.knownNeeds, d.priorityMoney) &&
            d.energyBefore - cost.energy >= d.priorityEnergy && d.availableTimeBefore - cost.time >= d.priorityTime
        val chosen = preserves(d.chosenCost) && d.chosenPriorityFeasible == true
        val alternative = preserves(d.alternativeCost) && d.alternativePriorityFeasible == true
        if (chosen) return observation(COMPARE_COSTS, facts, SUPPORTED, PRIORITY_RESOURCE_PRESERVED)
        // Remaining resources alone do not prove that hunger, deadlines and other guards permit the priority.
        if (preserves(d.chosenCost) && d.chosenPriorityFeasible == null) return missing(COMPARE_COSTS, facts)
        if (alternative && fact.context.alternativeAvailable == true) return observation(COMPARE_COSTS, facts, DIFFICULTY, PRIORITY_RESOURCE_LOST)
        if (preserves(d.alternativeCost) && d.alternativePriorityFeasible == null) return missing(COMPARE_COSTS, facts)
        return observation(COMPARE_COSTS, facts, NEUTRAL, NO_RANKED_ALTERNATIVE)
    }

    private fun earnings(facts: List<AnalyticsFact>): SkillObservation {
        val plan = facts.firstOrNull { it.detail is EarningPlan } ?: return missing(PLAN_EXTRA_INCOME, facts)
        val d = plan.detail as EarningPlan
        contextIssue(PLAN_EXTRA_INCOME, facts, plan)?.let { return it }
        if (d.priorityId == null) return observation(PLAN_EXTRA_INCOME, facts, NEUTRAL, PRIORITY_UNKNOWN)
        // Future rest may supply energy: only a deadline already missed is certainly infeasible here.
        val impossibleByDeadline = d.deadlineDay < d.startedDay ||
            (d.deadlineDay == d.startedDay && d.effortRequired > d.availableEffortAtChoice)
        if (impossibleByDeadline && plan.context.alternativeAvailable == true) return observation(PLAN_EXTRA_INCOME, facts, DIFFICULTY, EARNING_PLAN_INFEASIBLE)
        val result = facts.lastOrNull { (it.detail as? EarningCompleted)?.offerId == d.offerId && it.sequence > plan.sequence }
            ?: return pending(PLAN_EXTRA_INCOME, facts)
        contextIssue(PLAN_EXTRA_INCOME, facts, result)?.let { return it }
        val completed = result.detail as EarningCompleted
        if (completed.day > d.deadlineDay) return observation(PLAN_EXTRA_INCOME, facts, NEUTRAL, NO_AVAILABLE_CHOICE)
        if (!completed.actualRewardAccountedFor) return pending(PLAN_EXTRA_INCOME, facts)
        return observation(PLAN_EXTRA_INCOME, facts, SUPPORTED, EARNING_PLAN_COMPLETED,
            measures = mapOf("actualReward" to completed.actualReward, "maximumReward" to d.maximumReward))
    }

    private fun answers(skill: SkillId, facts: List<AnalyticsFact>): SkillObservation {
        val answers = firstAnswers(facts)
        if (answers.isEmpty()) return missing(skill, facts)
        for (fact in answers) contextIssue(skill, facts, fact)?.let { return it }
        if (facts.none { (it.detail as? QuestionAnswer)?.seriesClosed == true }) return pending(skill, facts)
        val correct = answers.count { (it.detail as QuestionAnswer).task.isCorrect() }
        val allCorrect = correct == answers.size
        return observation(skill, facts, if (allCorrect) SUPPORTED else DIFFICULTY,
            if (skill == COMPARE_AMOUNTS) { if (allCorrect) CORRECT_COMPARISON else INCORRECT_COMPARISON }
            else { if (allCorrect) CORRECT_LEDGER_ANSWER else INCORRECT_LEDGER_ANSWER },
            measures = mapOf("correctFirstAnswers" to correct.toLong(), "questions" to answers.size.toLong()), assessedFacts = answers)
    }

    private fun explanation(facts: List<AnalyticsFact>): SkillObservation {
        val answers = firstAnswers(facts)
        val answer = answers.firstOrNull() ?: return missing(RECONSIDER_DECISION, facts)
        contextIssue(RECONSIDER_DECISION, facts, answer)?.let { return it }
        val d = answer.detail as QuestionAnswer
        if (!d.task.isCorrect()) return observation(RECONSIDER_DECISION, facts, DIFFICULTY, INCORRECT_EXPLANATION, assessedFacts = listOf(answer))
        if (d.comparisonFamily == null) return observation(RECONSIDER_DECISION, facts, INSUFFICIENT_DATA, CORRECT_EXPLANATION, pending = true, assessedFacts = listOf(answer))
        val applied = facts.firstOrNull {
            val a = it.detail as? ComparableApplication
            a?.comparisonFamily == d.comparisonFamily && it.sequence > answer.sequence && it.learningContext == LearningContext.GAME
        } ?: return observation(RECONSIDER_DECISION, facts, INSUFFICIENT_DATA, CORRECT_EXPLANATION, pending = true, assessedFacts = listOf(answer))
        contextIssue(RECONSIDER_DECISION, facts, applied)?.let { return it }
        if (!(applied.detail as ComparableApplication).priorityPreserved) return observation(RECONSIDER_DECISION, facts, NEUTRAL, NO_RANKED_ALTERNATIVE)
        return observation(RECONSIDER_DECISION, facts, SUPPORTED, EXPLANATION_APPLIED, assessedFacts = listOf(answer, applied))
    }

    private fun firstAnswers(facts: List<AnalyticsFact>) = facts.filter { it.detail is QuestionAnswer }
        .groupBy { (it.detail as QuestionAnswer).questionId }.values.map { attempts ->
            val first = attempts.minWith(compareBy<AnalyticsFact> { (it.detail as QuestionAnswer).attempt }.thenBy { it.sequence }.thenBy { it.eventId })
            require((first.detail as QuestionAnswer).attempt == 1) { "The initial answer is missing" }
            val identity = questionIdentity(first.detail.task)
            require(attempts.all { questionIdentity((it.detail as QuestionAnswer).task) == identity }) { "Question parameters changed within one task" }
            val original = first.detail.task
            require(attempts.filter { (it.detail as QuestionAnswer).attempt == 1 }.all { (it.detail as QuestionAnswer).task == original }) {
                "Conflicting initial answers"
            }
            first
        }

    private fun questionIdentity(task: AssessmentTask): Any = when (task) {
        is AssessmentTask.CompareAmounts -> task.copy(chosen = ComparisonSide.EQUAL)
        is AssessmentTask.ExplainCause -> task.copy(chosenOptionId = task.expectedOptionId)
        is AssessmentTask.ReadLedger -> task.copy(answer = 0)
    }

    private fun fundsKnownNeeds(context: DecisionContext, position: FinancialPosition, required: Long = position.shortfall): Boolean {
        val p = context.fundingPlan ?: return false
        if (!p.selected || !p.feasibleBeforeNeed || !p.guaranteedAmount) return false
        if (p.source == FundingSource.CONFIRMED_SAVINGS_WITHDRAWAL && p.amount > position.savings) return false
        return required > 0 && p.amount >= required
    }

    private fun contextIssue(skill: SkillId, facts: List<AnalyticsFact>, fact: AnalyticsFact): SkillObservation? = when {
        !fact.context.complete -> missing(skill, facts)
        !fact.context.informationPresented || fact.context.presentationId == null -> observation(skill, facts, INSUFFICIENT_DATA, PRESENTATION_MISSING)
        else -> null
    }

    private fun missing(skill: SkillId, facts: List<AnalyticsFact>) = observation(skill, facts, INSUFFICIENT_DATA, CONTEXT_MISSING)
    private fun pending(skill: SkillId, facts: List<AnalyticsFact>) = observation(skill, facts, INSUFFICIENT_DATA, EPISODE_PENDING, pending = true)

    private fun observation(
        skill: SkillId,
        facts: List<AnalyticsFact>,
        outcome: ObservationOutcome,
        reason: ObservationReason,
        pending: Boolean = false,
        measures: Map<String, Long> = emptyMap(),
        assessedFacts: List<AnalyticsFact> = facts,
    ): SkillObservation {
        val support = assessedFacts.flatMap { it.context.assistance }.toMutableSet()
        if (assessedFacts.any { (it.detail as? QuestionAnswer)?.answerWasRevealed == true }) support += Assistance.ANSWER_REVEALED
        return SkillObservation(
            gameRunId = facts.first().gameRunId, skill = skill, episodeId = facts.first().episodeId,
            outcome = outcome,
            eligibility = when (outcome) {
                INSUFFICIENT_DATA -> ObservationEligibility.UNDETERMINED
                NEUTRAL -> ObservationEligibility.NOT_ELIGIBLE
                else -> ObservationEligibility.ELIGIBLE
            },
            completion = if (pending) EpisodeCompletion.PENDING else EpisodeCompletion.COMPLETE,
            reason = reason, sourceEventIds = facts.map { it.eventId }, assistance = support,
            adultHelpKnown = assessedFacts.all { it.context.adultHelpKnown },
            learningContexts = assessedFacts.map { it.learningContext }.toSet(),
            contextFamilies = assessedFacts.map { it.contextFamily }.toSet(), measures = measures,
        )
    }
}
