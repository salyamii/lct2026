package ru.nksk.lctapp.domain.history

import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.analytics.FactDetail.*
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.EventType

/** Rebuildable interval evidence. Never writes to the game, the audit, or the outbox. */
object HistoryLearningProjection {
    const val VERSION = 4

    fun facts(history: List<AuditEntry>, content: StoryContent? = null): List<AnalyticsFact> {
        val entries = history.groupBy { it.id }.map { (_, copies) ->
            require(copies.all { HistoryCodec.encode(it) == HistoryCodec.encode(copies.first()) }) { "Conflicting history identity" }
            copies.first()
        }.sortedWith(compareBy<AuditEntry> { it.runId }.thenBy { it.sequence })
        require(entries.map { it.runId to it.sequence }.distinct().size == entries.size) { "Conflicting history order" }
        val raw = entries.flatMap { it.facts }
        val derived = mutableListOf<AnalyticsFact>()
        entries.groupBy { it.runId }.values.forEach { run ->
            val real = run.flatMap { it.facts }.filter { it.mode == AnalyticsMode.REAL && it.learningContext == LearningContext.GAME }
            windows(run, real, derived)
            reserves(run, real, derived, content)
            earnings(run, real, derived)
            applications(run.flatMap { it.facts }, derived)
        }
        return (raw + derived).sortedWith(compareBy<AnalyticsFact> { it.gameRunId }.thenBy { it.sequence }.thenBy { it.eventId })
    }

    private fun windows(run: List<AuditEntry>, real: List<AnalyticsFact>, out: MutableList<AnalyticsFact>) {
        val starts = run.filter { entry ->
            val day = entry.after?.engine?.day
            entry.type == AuditType.COMMAND && day != null && (day - 1) % 7 == 0 && entry.before?.engine?.day != day &&
                (entry.request?.command is EngineCommand.BeginDay || entry.request?.command is EngineCommand.SelectGoal ||
                    entry.request?.command is EngineCommand.SelectSavingGoal)
        }
        // A partial imported first week has no opening checkpoint and cannot become a completed interval.
        starts.zipWithNext().forEach { (start, close) ->
            val startDay = checkNotNull(start.after?.engine?.day)
            val windowId = "week:${(startDay - 1) / 7 + 1}"
            // Onboarding confirms the first plan before BeginDay creates day 1.
            val evidenceStart = if (startDay == 1) run.first().sequence else start.sequence
            val interval = run.filter { it.sequence >= start.sequence && it.sequence < close.sequence }
            val end = close.before ?: return@forEach
            val finished = interval.mapNotNull { entry -> entry.after?.engine?.takeIf { day ->
                day.phase == DayPhase.FINISHED && (entry.request?.command == EngineCommand.FinishDay ||
                    entry.request?.command is EngineCommand.FinishDayFromEvent)
            } }.associateBy { it.day }
            val allDays = (startDay until startDay + 7).toList()
            val meals = interval.flatMap(::paidOrFallbackMeals)
            val paidDays = meals.filter { it.third < 0 }.map { it.first }.toSet()
            val fallbackOnly = meals.filter { it.third == 0L && it.first !in paidDays }
            val feedingKnown = allDays.all { finished[it]?.ateToday != true || meals.any { meal -> meal.first == it } }
            val complete = close.after?.engine?.day == startDay + 7 &&
                interval.zipWithNext().all { (a, b) -> b.sequence == a.sequence + 1 } &&
                interval.lastOrNull()?.sequence == close.sequence - 1 &&
                allDays.all { it in finished } && feedingKnown && interval.none(::discontinuous) &&
                connectedSnapshots(interval + close)
            val managed = real.filter { it.sequence >= evidenceStart && it.sequence < close.sequence &&
                isManaged(it.detail) && it.context.hasPresentation() }
            val context = managed.lastOrNull()?.context ?: DecisionContext()
            val closingPosition = positionAtEnd(real, close.sequence, end.economy.availableBalance, end.economy.savingsBalance)
            // Physical satiety after free fallback is not evidence that planned food was financed.
            // Fallback alone is never a difficulty: require an earlier observed, avoidable gap that day.
            val unmetDays = finished.values.filterNot { it.ateToday }.map { it.day }.toSet() + fallbackOnly.map { it.first }
            val avoidable = managed.filter { fact -> fact.context.day in unmetDays &&
                fallbackOnly.filter { it.first == fact.context.day }.all { fact.sequence < it.second } &&
                fact.context.alternativeAvailable == true && fact.context.before != null && fact.context.after != null &&
                fact.context.after.shortfall > fact.context.before.shortfall && !funded(fact.context, fact.context.after)
            }.map { it.actionId }.distinct()
            val source = managed.lastOrNull() ?: real.firstOrNull { it.sequence == start.sequence }
                ?: return@forEach
            out += derived(source, close.sequence, "interval:$windowId", "interval:$windowId",
                IncomeWindow(windowId, true, complete, managed.map { it.actionId }.distinct(),
                    allDays.all { finished[it]?.ateToday == true && it in paidDays }, avoidable, avoidable.isNotEmpty()),
                context.copy(after = closingPosition), AnalyticsActor.SYSTEM)

        }
    }

    /** A food choice has one purchase receipt, not a second invented meal expense. */
    private fun paidOrFallbackMeals(entry: AuditEntry): List<Triple<Int, Long, Long>> {
        if (entry.type != AuditType.COMMAND) return emptyList()
        val before = entry.before ?: return emptyList()
        val after = entry.after ?: return emptyList()
        val previousDay = before.engine ?: return emptyList()
        val day = after.engine ?: return emptyList()
        if (previousDay.day != day.day || !day.ateToday ||
            runCatching { CanonicalLedger.validate(before, after, entry.operations) }.isFailure) return emptyList()
        val previous = previousDay.journal.map { it.id }.toSet()
        val added = day.journal.filter { it.id !in previous }
        val command = entry.request?.command
        val choiceId = when (command) {
            is EngineCommand.Choose -> command.choiceId
            is EngineCommand.CompleteEvent -> command.choiceId
            is EngineCommand.CompleteStoryGame -> command.choiceId
            else -> null
        }
        return added.filter { receipt ->
            val ordinaryMeal = command is EngineCommand.Feed && receipt.kind == DayJournalKind.MEAL &&
                receipt.sourceId == command.mealId
            val foodChoice = !previousDay.ateToday && choiceId != null &&
                receipt.kind == DayJournalKind.EVENT_CHOICE && receipt.sourceId == choiceId
            (ordinaryMeal && receipt.moneyDelta == 0L) || ((ordinaryMeal || foodChoice) && receipt.moneyDelta < 0 &&
                entry.operations.singleOrNull { it.operationId == receipt.id }?.let {
                    it.kind == LedgerKind.AVAILABLE_EXPENSE && it.amount == -receipt.moneyDelta
                } == true)
        }.map { Triple(day.day, entry.sequence, it.moneyDelta) }
    }

    /** A gap in snapshots is not repaired by consecutive sequence numbers or matching net balances. */
    private fun connectedSnapshots(entries: List<AuditEntry>): Boolean {
        val changes = entries.filter { it.type != AuditType.FACTS && it.type != AuditType.REJECTED }
        return changes.all { entry -> entry.before != null && entry.after != null &&
            runCatching { CanonicalLedger.validate(entry.before, entry.after, entry.operations) }.isSuccess } &&
            changes.zipWithNext().all { (a, b) -> a.after == b.before }
    }

    /** Tracks an explicit intention through actual receipts; new income does not resurrect a spent reserve. */
    private fun reserves(run: List<AuditEntry>, real: List<AnalyticsFact>, out: MutableList<AnalyticsFact>, content: StoryContent?) {
        val intentions = real.filter { it.detail is ReserveDecision }
        intentions.forEachIndexed { index, source ->
            val intention = source.detail as ReserveDecision
            val sourceDay = source.context.day ?: run.find { it.sequence == source.sequence }?.after?.engine?.day ?: 1
            val sourceWeek = (sourceDay - 1) / 7
            val weekEnd = run.firstOrNull { entry -> entry.sequence > source.sequence &&
                entry.after?.engine?.day?.let { (it - 1) / 7 > sourceWeek } == true }
            val nextPlan = intentions.getOrNull(index + 1)?.sequence
            val boundary = listOfNotNull(weekEnd?.sequence, nextPlan).minOrNull()
            val interval = run.filter { it.sequence > source.sequence && (boundary == null || it.sequence < boundary) }
            var remaining = minOf(intention.declaredAmount, source.context.after?.coverage?.coerceAtLeast(0) ?: 0)
            var complete = source.context.complete && source.context.before != null && source.context.after != null
            var position = source.context.after
            var previousSequence = source.sequence
            val applications = mutableListOf<ReserveApplication>()
            for (entry in interval) {
                complete = complete && entry.sequence == previousSequence + 1 && !discontinuous(entry)
                previousSequence = entry.sequence
                if (entry.type == AuditType.FACTS || entry.type == AuditType.REJECTED) continue
                val before = entry.before ?: continue
                val after = entry.after ?: continue
                if (runCatching { CanonicalLedger.validate(before, after, entry.operations) }.isFailure) complete = false
                val context = entry.facts.firstOrNull { it.mode == AnalyticsMode.REAL && it.learningContext == LearningContext.GAME &&
                    it.context.before?.available == before.economy.availableBalance &&
                    it.context.after?.available == after.economy.availableBalance }?.context
                val prior = context?.before
                if (prior == null && before.economy != after.economy) complete = false
                remaining = minOf(remaining, prior?.coverage?.coerceAtLeast(0) ?: remaining)
                entry.operations.filter { it.kind == LedgerKind.AVAILABLE_EXPENSE }.forEach { operation ->
                    val unexpected = entry.facts.filter { it.mode == AnalyticsMode.REAL && it.learningContext == LearningContext.GAME }
                        .mapNotNull { it.detail as? UnexpectedExpense }
                        .singleOrNull { it.operationId == operation.operationId && it.amount == operation.amount && !it.previouslyDisclosed }
                    when (reserveCategory(entry, operation.operationId, unexpected != null, content)) {
                        true -> {
                            val attributed = minOf(remaining, operation.amount)
                            remaining -= attributed
                            if (unexpected != null && attributed > 0) {
                                val presented = firstExpensePresentation(run, entry)
                                if (presented == null) complete = false
                                else if (source.sequence < presented)
                                    applications += ReserveApplication(operation.operationId, attributed)
                            }
                        }
                        false -> Unit
                        null -> complete = false
                    }
                }
                position = context?.after ?: position
                remaining = minOf(remaining, position?.coverage?.coerceAtLeast(0) ?: remaining)
            }
            if (boundary != null && previousSequence != boundary - 1) complete = false
            complete = complete && interval.any { it.operations.isNotEmpty() ||
                it.after?.engine?.day?.let { day -> day > sourceDay } == true }
            // Actual use can be observed immediately. Mere preservation waits for the interval boundary.
            val closed = boundary != null || applications.isNotEmpty()
            if (!closed) return@forEachIndexed
            val used = applications.fold(0L) { sum, application -> Math.addExact(sum, application.amount) }
            out += derived(source, boundary ?: previousSequence, "reserve:${intention.intentionId}:close", source.episodeId,
                intention.copy(remainingAmount = remaining, usedForUnexpectedExpense = used, intervalClosed = true,
                    applications = applications), source.context.copy(after = position, complete = complete))
        }
    }

    /** Original presentation of this occurrence survives pause, carry and reopening. */
    private fun firstExpensePresentation(run: List<AuditEntry>, expense: AuditEntry): Long? {
        val command = expense.request?.command
        val occurrenceId = when (command) {
            is EngineCommand.Choose -> command.occurrenceId
            is EngineCommand.CompleteEvent -> command.occurrenceId
            is EngineCommand.CompleteStoryGame -> command.occurrenceId
            EngineCommand.OpenNextEvent, is EngineCommand.BeginDay -> expense.after?.engine?.currentEvent?.id
            else -> null
        } ?: return null
        val presented = setOf(EventStatus.ACTIVE, EventStatus.RESULT, EventStatus.PAUSED, EventStatus.CARRIED_ACTIVE)
        val prefix = run.filter { it.sequence <= expense.sequence }
        val first = prefix.firstOrNull { entry ->
            (entry.before?.engine?.events.orEmpty() + entry.after?.engine?.events.orEmpty()).any {
                it.id == occurrenceId && it.status in presented
            }
        } ?: return null
        // An imported/already visible occurrence has no trustworthy first-show timestamp.
        if (first.type != AuditType.COMMAND || first.before?.engine?.events.orEmpty().any {
                it.id == occurrenceId && it.status in presented
            } || first.after?.engine?.events.orEmpty().none {
                it.id == occurrenceId && it.status in setOf(EventStatus.ACTIVE, EventStatus.RESULT)
            }) return null
        val interval = prefix.filter { it.sequence >= first.sequence }
        if (interval.any(::discontinuous) || !connectedSnapshots(interval) ||
            interval.zipWithNext().any { (a, b) -> b.sequence != a.sequence + 1 }) return null
        return first.sequence
    }

    private fun reserveCategory(entry: AuditEntry, operationId: String, unexpected: Boolean, content: StoryContent?): Boolean? {
        // A direct goal payment is known spending, including its split wallet receipt.
        // It may reduce coverage but is never an unexpected expense paid from a reserve.
        if (entry.request?.command is EngineCommand.BuyGoalItem) return false
        if (unexpected) return true
        if (entry.facts.any { it.detail is OptionalPurchase }) return false
        val journal = entry.after?.engine?.journal?.find { it.id == operationId } ?: return null
        if (journal.kind == DayJournalKind.MEAL) return false
        val eventId = when (journal.kind) {
            DayJournalKind.EVENT_START -> journal.sourceId
            DayJournalKind.EVENT_CHOICE -> content?.choices?.find { it.id == journal.sourceId }?.eventId
            else -> null
        }
        return when (content?.events?.find { it.id == eventId }?.type) {
            EventType.RANDOM, EventType.STATE, EventType.STORY -> true
            EventType.WANT -> false
            else -> null
        }
    }

    private fun earnings(run: List<AuditEntry>, real: List<AnalyticsFact>, out: MutableList<AnalyticsFact>) {
        real.filter { it.detail is EarningCompleted && !it.detail.actualRewardAccountedFor }.forEach { completion ->
            val result = completion.detail as EarningCompleted
            val consumer = run.firstOrNull { entry ->
                entry.sequence > completion.sequence && entry.type == AuditType.COMMAND &&
                    (entry.request?.command is EngineCommand.ConfirmBudget || entry.operations.any {
                        it.amount > 0 && it.kind in setOf(LedgerKind.DEPOSIT, LedgerKind.AVAILABLE_EXPENSE, LedgerKind.SAVINGS_EXPENSE)
                    }) && entry.facts.any { it.context.hasPresentation() }
            } ?: return@forEach
            // Recognise an action using the actual post-reward accounts, not a promised maximum reward.
            val context = consumer.facts.first { it.context.hasPresentation() }.context
            out += derived(completion, consumer.sequence, "earning:${completion.eventId}:accounted", completion.episodeId,
                result.copy(actualRewardAccountedFor = true), context, AnalyticsActor.SYSTEM).copy(actionId = consumer.request!!.id)
        }
    }

    private fun applications(all: List<AnalyticsFact>, out: MutableList<AnalyticsFact>) {
        val answers = all.filter { it.mode == AnalyticsMode.REAL && it.actor == AnalyticsActor.CHILD &&
            it.context.hasPresentation() && it.detail is QuestionAnswer &&
            it.detail.task is AssessmentTask.ExplainCause && it.detail.attempt == 1 && it.detail.comparisonFamily != null }
        val usedApplications = mutableSetOf<String>()
        answers.sortedByDescending { it.sequence }.forEach { answer ->
            val question = answer.detail as QuestionAnswer
            if (!question.task.isCorrect()) return@forEach
            val candidate = all.firstOrNull { fact -> fact.sequence > answer.sequence && fact.mode == AnalyticsMode.REAL &&
                fact.learningContext == LearningContext.GAME && fact.actor == AnalyticsActor.CHILD &&
                fact.actionId !in usedApplications && fact.contextFamily == question.comparisonFamily && applicable(fact.detail) &&
                fact.context.hasPresentation() && fact.context.alternativeAvailable == true && priorityPreserved(fact) != null }
                ?: return@forEach
            val preserved = priorityPreserved(candidate)
            usedApplications += candidate.actionId
            out += derived(candidate, candidate.sequence, "application:${answer.eventId}:${candidate.eventId}", answer.episodeId,
                ComparableApplication(checkNotNull(question.comparisonFamily), candidate.actionId, preserved == true),
                candidate.context.copy(complete = candidate.context.complete && preserved != null))
        }
    }

    private fun applicable(detail: FactDetail) = detail is OptionalPurchase || detail is ResourceChoice || detail is SavingMovement

    private fun priorityPreserved(fact: AnalyticsFact): Boolean? {
        val before = fact.context.before ?: return null
        val after = fact.context.after ?: return null
        return when (val d = fact.detail) {
            is OptionalPurchase -> if (before.knownNeeds > 0 && before.available >= d.price)
                after.coverage >= 0 || funded(fact.context, after) else null
            is SavingMovement -> if (d.kind == SavingMovementKind.WITHDRAWAL) null else after.coverage >= 0 || funded(fact.context, after)
            is ResourceChoice -> if (d.priorityId == null || d.chosenPriorityFeasible == null) null else
                d.chosenPriorityFeasible && d.chosenCost.money <= before.available && d.chosenCost.energy <= d.energyBefore && d.chosenCost.time <= d.availableTimeBefore &&
                    before.available - d.chosenCost.money >= Math.addExact(before.knownNeeds, d.priorityMoney) &&
                    d.energyBefore - d.chosenCost.energy >= d.priorityEnergy && d.availableTimeBefore - d.chosenCost.time >= d.priorityTime
            else -> null
        }
    }

    private fun isManaged(d: FactDetail) = d is BudgetConfirmed || d is OptionalPurchase || d is SavingMovement || d is ResourceChoice
    private fun DecisionContext.hasPresentation() = complete && informationPresented && presentationId != null
    private fun discontinuous(entry: AuditEntry): Boolean = entry.type == AuditType.RESTORED || entry.type == AuditType.IMPORTED_BASELINE ||
        (entry.type == AuditType.TECHNICAL_UPDATE && (entry.before?.economy != entry.after?.economy || entry.before?.engine?.day != entry.after?.engine?.day))

    private fun positionAtEnd(real: List<AnalyticsFact>, beforeSequence: Long, available: Long, savings: Long): FinancialPosition? =
        real.lastOrNull { it.sequence < beforeSequence && it.context.after != null }?.context?.after?.let { last ->
            // Do not carry unknown obligations across a missing financial context.
            if (last.available == available && last.savings == savings) last else null
        }

    private fun funded(context: DecisionContext, position: FinancialPosition): Boolean {
        val funding = context.fundingPlan ?: return false
        return funding.selected && funding.feasibleBeforeNeed && funding.guaranteedAmount && funding.amount >= position.shortfall &&
            (funding.source != FundingSource.CONFIRMED_SAVINGS_WITHDRAWAL || funding.amount <= position.savings)
    }

    private fun derived(source: AnalyticsFact, sequence: Long, identity: String, episode: String, detail: FactDetail,
        context: DecisionContext, actor: AnalyticsActor = source.actor) = source.copy(
        eventId = "derived:$VERSION:${source.gameRunId}:$identity", sequence = sequence, episodeId = episode,
        detail = detail, context = context, actor = actor, mode = AnalyticsMode.REAL, learningContext = LearningContext.GAME,
    )
}
