package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.CanonicalLedger

/** Converts canonical outcomes into evidence. Displayed information is never inferred from a click. */
internal class EngineAnalytics(private val factory: EventFactory, private val rules: EngineRules,
    private val contentVersion: String,
    private val preview: (GameState, EngineCommand) -> EngineResult,
    private val previewEventChoice: (GameState, String, String) -> EngineResult) {
    fun facts(request: EngineRequest, before: GameState, after: GameState, runId: String, sequence: Long): List<AnalyticsFact> {
        val period = after.financial.currentPeriod ?: before.financial.currentPeriod
        val day = before.engine?.day ?: after.engine?.day ?: 1
        val window = "week:${(day - 1) / 7 + 1}"
        val savingOpportunity = after.financial.currentPeriod?.savingPractice?.incomeOpportunityId
            ?: before.financial.currentPeriod?.savingPractice?.incomeOpportunityId ?: window
        val shown = request.context ?: DecisionContext()
        val context = shown.copy(
            before = FinancialPosition(before.economy.availableBalance, before.economy.savingsBalance,
                shown.before?.knownNeeds ?: factory.mealPolicy.foodRequirement(before)),
            after = FinancialPosition(after.economy.availableBalance, after.economy.savingsBalance,
                factory.mealPolicy.foodRequirement(after)),
            financialPeriodId = period?.id, day = day,
        )
        val out = mutableListOf<AnalyticsFact>()
        val receipts = CanonicalLedger.fromTransition(before, after, request)
        fun add(detail: FactDetail, episode: String = request.id, family: String = "command", actor: AnalyticsActor = AnalyticsActor.CHILD,
            evidenceContext: DecisionContext = context, learningContext: LearningContext = LearningContext.GAME) {
            out += AnalyticsFact("${request.id}:fact:${out.size}", runId, episode, request.id, sequence, detail,
                evidenceContext, actor = actor, contextFamily = family, contentVersion = contentVersion, gameRulesVersion = rules.id,
                learningContext = learningContext)
        }
        add(FactDetail.Interaction(request.command::class.simpleName ?: "command"))
        val command = request.command
        when (command) {
            is EngineCommand.ConfirmBudget -> {
                val revision = after.financial.plans.last()
                val plan = revision.allocation
                val cause = when (revision.reason) {
                    BudgetRevisionReason.INITIAL -> BudgetRevisionCause.INITIAL
                    BudgetRevisionReason.KNOWN_NEED_OMITTED -> BudgetRevisionCause.KNOWN_NEED_OMITTED
                    BudgetRevisionReason.UNEXPECTED_EXPENSE -> BudgetRevisionCause.UNEXPECTED_EXPENSE
                    BudgetRevisionReason.NEW_INCOME -> BudgetRevisionCause.NEW_INCOME
                    else -> BudgetRevisionCause.UNSPECIFIED
                }
                add(FactDetail.BudgetConfirmed(revision.id, revision.ordinal, revision.availableBasis,
                    plan.needs, plan.wants, plan.savings, plan.reserve, cause), revision.id, "budget")
                add(FactDetail.ReserveDecision(revision.id, plan.reserve,
                    minOf(plan.reserve, (after.economy.availableBalance - context.after!!.knownNeeds).coerceAtLeast(0)),
                    0, false), "reserve:${period?.id ?: "free"}:$window", "reserve")
                if (cause == BudgetRevisionCause.UNEXPECTED_EXPENSE && command.causeActionId != null)
                    add(FactDetail.RecoveryAction(command.causeActionId, request.id,
                        context.after!!.coverage >= 0), "recovery:${command.causeActionId}", "recovery")
            }
            is EngineCommand.DepositSavings -> period?.let {
                val receipt = receipts.single { it.kind == LedgerKind.DEPOSIT }
                add(FactDetail.SavingMovement(receipt.operationId, it.goalId, savingOpportunity,
                    SavingMovementKind.DEPOSIT, command.amount), "saving:${it.id}", "saving")
                add(FactDetail.PriorityApplied(it.goalId, receipt.operationId, command.amount),
                    "priority:${it.id}:${it.goalId}", "delayed_purchase")
            }
            is EngineCommand.WithdrawSavings -> period?.let {
                add(FactDetail.SavingMovement(receipts.single { it.kind == LedgerKind.WITHDRAWAL }.operationId, it.goalId, savingOpportunity,
                    SavingMovementKind.WITHDRAWAL, command.amount), "saving:${it.id}", "saving")
            }
            is EngineCommand.BuyGoalItem -> {
                val amount = before.economy.savingsBalance - after.economy.savingsBalance
                if (amount > 0) add(FactDetail.SavingMovement(receipts.single { it.kind == LedgerKind.SAVINGS_EXPENSE }.operationId, command.goalId,
                    savingOpportunity, SavingMovementKind.GOAL_PURCHASE, amount), "saving:${period?.id ?: command.goalId}", "saving")
            }
            is EngineCommand.AnswerFinancialQuestion -> after.financial.practice?.let { question ->
                val learning = if (question.series != null) LearningContext.TRAINING else LearningContext.GAME
                if (question.kind == ru.nksk.lctapp.domain.finance.FinancialQuestionKind.PLAN_REVIEW ||
                    question.kind == ru.nksk.lctapp.domain.finance.FinancialQuestionKind.SAVING_PRACTICE) {
                    val guided = question.kind == ru.nksk.lctapp.domain.finance.FinancialQuestionKind.SAVING_PRACTICE ||
                        question.reviewEvidence?.guidedRecovery == true
                    add(FactDetail.PracticeAnswer(question.id,
                        if (question.kind == ru.nksk.lctapp.domain.finance.FinancialQuestionKind.PLAN_REVIEW) PracticeKind.PLAN_REVIEW
                        else PracticeKind.SAVINGS_REHEARSAL, command.answerId, question.correctAnswerId, question.attempts, guided),
                        question.id, "financial_practice", evidenceContext = context.copy(
                            complete = context.complete && context.presentationId == "question:${question.id}",
                            assistance = context.assistance + (if (guided && question.series == null) setOf(Assistance.WORKED_EXAMPLE) else emptySet()) +
                                (if (question.usedHint) setOf(Assistance.HINT) else emptySet()) +
                                (if (question.attempts > 1) setOf(Assistance.ANSWER_REVEALED) else emptySet())),
                        learningContext = learning)
                    return@let
                }
                val task = question.ledgerTask?.copy(answer = command.answerId.toLong())
                    ?: AssessmentTask.ExplainCause(question.correctAnswerId, command.answerId, question.sourceActionIds)
                val questionContext = context.copy(
                    complete = context.complete && context.presentationId == "question:${question.id}",
                    assistance = context.assistance + if (question.usedHint) setOf(Assistance.HINT) else emptySet())
                add(FactDetail.QuestionAnswer(question.id, question.attempts, task,
                    answerWasRevealed = question.attempts > 1, comparisonFamily = question.comparisonFamily),
                    question.id, question.comparisonFamily ?: "period_review", evidenceContext = questionContext, learningContext = learning)
            }
            else -> Unit
        }
        val occurrence = before.engine?.currentEvent
        val choiceId = when (command) {
            is EngineCommand.Choose -> command.choiceId
            is EngineCommand.CompleteEvent -> command.choiceId
            is EngineCommand.CompleteDeed -> after.story.decisions.lastOrNull()?.choiceId
            is EngineCommand.CompleteStoryGame -> command.choiceId
            else -> null
        }
        if (choiceId != null && occurrence != null) {
            val event = factory.event(occurrence.eventId)
            val choice = factory.choices(event.id).first { it.id == choiceId }
            if (event.type == EventType.WANT) {
                val price = factory.choices(event.id).maxOf { (-it.moneyDelta).coerceAtLeast(0) }
                val item = factory.content.choiceItemEffects.firstOrNull { effect ->
                    factory.choices(event.id).any { it.id == effect.choiceId } }?.itemId ?: event.id
                add(FactDetail.OptionalPurchase(item, price, choice.moneyDelta < 0), occurrence.id, "optional_purchase")
                if (command is EngineCommand.CompleteEvent && command.priorityId != null &&
                    command.priorityId == before.selectedGoalId && choice.moneyDelta == 0L && period != null) {
                    add(FactDetail.DesireDeferred(item, price, true, command.priorityId),
                        "priority:${period.id}:${command.priorityId}", "delayed_purchase")
                }
            }
            val policy = factory.policy(event.id)
            val alternate = factory.choices(event.id).firstOrNull { it.id != choiceId &&
                policy.energyFor(it.id) != policy.energyFor(choiceId) &&
                previewEventChoice(before, occurrence.id, it.id) is EngineResult.Applied }
            if (alternate != null && policy.energyFor(choice.id) != policy.energyFor(alternate.id)) {
                val priorityId = when (command) {
                    is EngineCommand.CompleteEvent -> command.resourcePriorityOfferId
                    is EngineCommand.CompleteStoryGame -> command.resourcePriorityOfferId
                    else -> null
                }
                val priority = before.engine?.deeds?.firstOrNull { it.id == priorityId && it.isAvailable(day) && it.expiresDay == day }
                val alternateState = (previewEventChoice(before, occurrence.id, alternate.id) as? EngineResult.Applied)?.state
                fun canStillDo(state: GameState?) = priority?.let { offer -> state?.let {
                    preview(it, EngineCommand.StartDeed(offer.id)) is EngineResult.Applied
                } }
                add(FactDetail.ResourceChoice(choiceId,
                    ResourceCost((-choice.moneyDelta).coerceAtLeast(0), policy.energyFor(choiceId), 0),
                    ResourceCost((-alternate.moneyDelta).coerceAtLeast(0), policy.energyFor(alternate.id), 0),
                    before.engine!!.energy, 0, priority?.id, 0,
                    priority?.let { factory.policy(it.eventId).energyCost } ?: 0, 0,
                    canStillDo(after), canStillDo(alternateState)), occurrence.id, "money_or_effort")
            }
            if (command is EngineCommand.CompleteDeed) {
                add(FactDetail.EarningCompleted(checkNotNull(occurrence.deedOfferId), day,
                    command.score.reward(choice.moneyDelta), false), occurrence.deedOfferId, "earning")
            }
        }
        if (command is EngineCommand.StartDeed || command is EngineCommand.AcceptDeedProposal) {
            val current = after.engine?.currentEvent
            val offer = after.engine?.deeds?.find { it.id == current?.deedOfferId }
            val declared = when (command) {
                is EngineCommand.StartDeed -> command.priorityId
                is EngineCommand.AcceptDeedProposal -> command.priorityId
                else -> null
            }?.takeIf { it == before.selectedGoalId }
            if (offer != null) add(FactDetail.EarningPlan(offer.id, declared, day, offer.expiresDay,
                factory.policy(offer.eventId).energyCost, before.engine?.energy ?: rules.fullEnergy,
                factory.choices(offer.eventId).maxOf { it.moneyDelta }), offer.id, "earning")
        }
        val oldEntries = before.engine?.journal.orEmpty().map { it.id }.toSet()
        after.engine?.journal.orEmpty().filter { it.id !in oldEntries && it.moneyDelta < 0 }.forEach { entry ->
            val eventId = when (entry.kind) {
                DayJournalKind.EVENT_START -> entry.sourceId
                DayJournalKind.EVENT_CHOICE -> factory.content.choices.find { it.id == entry.sourceId }?.eventId
                else -> null
            }
            if (eventId != null && factory.event(eventId).type == EventType.RANDOM) {
                add(FactDetail.UnexpectedExpense(entry.id, -entry.moneyDelta, false), "recovery:${entry.id}",
                    "unexpected_expense", AnalyticsActor.SYSTEM)
            }
        }
        if (after.completedGoalProjects.size > before.completedGoalProjects.size && period != null)
            add(FactDetail.SavingCycleClosed(period.goalId, !period.imported), "saving:${period.id}", "saving", AnalyticsActor.SYSTEM)
        return out
    }
}
