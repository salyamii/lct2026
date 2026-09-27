package ru.nksk.lctapp.domain.finance

import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

/** These local practice milestones are not the parent's assessment of mastery. */
object FinancialPeriods {
    fun adopt(state: GameState, imported: Boolean = state.engine != null): GameState {
        val goal = state.selectedGoalId ?: return state
        val existing = state.financial.currentPeriod
        if (existing != null) {
            val question = state.financial.practice
            return if (question != null && existing.id !in question.sourceActionIds)
                state.copy(financial = state.financial.copy(practice = null)) else state
        }
        val period = FinancialPeriod("period:${state.completedGoalProjects.size + 1}:$goal", goal,
            state.completedGoalProjects.size + 1, state.engine?.day ?: 1,
            state.economy.availableBalance, state.economy.savingsBalance, imported = imported,
            savingPractice = FinancialProgressionPolicy.opening("period:${state.completedGoalProjects.size + 1}:$goal",
                state.economy.savingsBalance))
        return state.copy(financial = state.financial.copy(currentPeriodId = period.id,
            periods = state.financial.periods + period, practice = null))
    }

    fun confirmed(before: GameState, after: GameState, request: EngineRequest,
        command: EngineCommand.ConfirmBudget, knownNeeds: Long): GameState {
        val progress = after.financial
        val previous = progress.plans.lastOrNull { it.periodId == progress.currentPeriodId }
        val reason = if (previous == null && progress.currentPeriodId != null ||
            before.economy.planning?.reason == BudgetPlanningReason.INITIAL)
            BudgetRevisionReason.INITIAL else command.reason
        val plan = BudgetPlanRevision("${request.id}:plan", progress.currentPeriodId,
            (previous?.ordinal ?: 0) + 1, after.engine?.day ?: 1,
            before.economy.planning?.baseAmount ?: before.economy.availableBalance,
            after.economy.plan, reason, previous?.id,
            knownNeeds.takeIf { request.context?.informationPresented == true }, command.causeActionId)
        val review = progress.currentPeriod?.reviewEvidence
        val realisticRevision = review?.answerCorrect == true && !FinancialProgressionPolicy.reviewReady(review) &&
            request.context?.let { it.complete && it.informationPresented && it.before != null &&
                plan.allocation.needs >= minOf(it.before.knownNeeds, plan.availableBasis) &&
                plan.allocation.total <= after.economy.availableBalance } == true
        val periods = if (!realisticRevision) progress.periods else progress.periods.map { period ->
            if (period.id == progress.currentPeriodId) period.copy(reviewEvidence = checkNotNull(review).copy(recoveryPlanRevisionId = plan.id)) else period
        }
        return after.copy(financial = progress.copy(plans = progress.plans + plan, periods = periods))
    }

    fun record(before: GameState, after: GameState, request: EngineRequest): GameState {
        val old = after.financial.currentPeriod ?: return after
        val beforeIds = before.engine?.journal.orEmpty().map { it.id }.toSet()
        val newEntries = after.engine?.journal.orEmpty().filter { it.id !in beforeIds }
        var period = old
        newEntries.forEach { entry ->
            period = when {
                entry.moneyDelta > 0 -> period.copy(income = Math.addExact(period.income, entry.moneyDelta))
                entry.moneyDelta < 0 && request.command is EngineCommand.BuyGoalItem ->
                    period.copy(spentSavings = Math.subtractExact(period.spentSavings, entry.moneyDelta))
                entry.moneyDelta < 0 -> period.copy(spentAvailable = Math.subtractExact(period.spentAvailable, entry.moneyDelta))
                else -> period
            }
        }
        var saving = period.savingPractice ?: FinancialProgressionPolicy.opening(period.id, before.economy.savingsBalance)
        if (newEntries.any { it.moneyDelta > 0 }) saving = FinancialProgressionPolicy.income(saving, request.id)
        period = when (val command = request.command) {
            is EngineCommand.Feed -> period.copy(needsProvided = true)
            is EngineCommand.DepositSavings -> {
                val context = request.context
                val independent = context?.complete == true && context.informationPresented && context.assistance.none {
                    it in setOf(Assistance.HINT, Assistance.WORKED_EXAMPLE, Assistance.ANSWER_REVEALED, Assistance.ADULT_REPORTED)
                }
                saving = FinancialProgressionPolicy.deposit(saving, "${request.id}:deposit", after.engine?.day ?: 1,
                    command.amount, independent, context?.before?.let { after.economy.availableBalance >= it.knownNeeds } == true)
                period.copy(independentlySaved = period.independentlySaved || independent,
                    deposited = Math.addExact(period.deposited, command.amount))
            }
            is EngineCommand.WithdrawSavings -> {
                saving = FinancialProgressionPolicy.withdraw(saving, command.amount)
                period.copy(withdrawn = Math.addExact(period.withdrawn, command.amount))
            }
            is EngineCommand.BuyGoalItem -> {
                saving = FinancialProgressionPolicy.goalPurchase(saving, before.economy.savingsBalance - after.economy.savingsBalance)
                period
            }
            is EngineCommand.AnswerFinancialQuestion -> {
                val question = after.financial.practice?.takeIf { it.id == command.questionId && it.correct && period.id in it.sourceActionIds }
                if (question?.kind == FinancialQuestionKind.SAVING_PRACTICE &&
                    (question.series == null || question.series.questionNumber == 1))
                    saving = FinancialProgressionPolicy.completeSavingRecovery(saving, question.id)
                if (question?.kind == FinancialQuestionKind.PLAN_REVIEW && question.reviewEvidence != null)
                    period.copy(reviewedPlan = true, reviewEvidence = question.reviewEvidence.copy(answerCorrect = true)) else period
            }
            else -> period
        }
        period = period.copy(savingPractice = saving,
            needsProvided = period.needsProvided || before.engine?.ateToday != true && after.engine?.ateToday == true)
        val completed = after.completedGoalProjects.size > before.completedGoalProjects.size
        if (completed) period = period.copy(closedDay = after.engine?.day ?: period.startedDay)
        return after.copy(financial = after.financial.copy(
            currentPeriodId = if (completed) null else period.id,
            periods = after.financial.periods.map { if (it.id == period.id) period else it },
        ))
    }

    /** Ledger questions use period operations; consequences use current balances and known needs. */
    fun question(state: GameState, id: String, kind: FinancialQuestionKind, knownNeeds: Long? = null,
        purchasePrice: Long? = null, purchaseTitle: String? = null, budgetReport: PeriodBudgetReport? = null): FinancialQuestion {
        val period = requireNotNull(state.financial.currentPeriod) { "Select a goal before reviewing its period" }
        if (kind == FinancialQuestionKind.CONSEQUENCE) return consequenceQuestion(state, period, id,
            requireNotNull(knownNeeds) { "A consequence question needs the current known needs" },
            requireNotNull(purchasePrice) { "A consequence question needs a catalog purchase price" },
            purchaseTitle?.takeIf { it.isNotBlank() } ?: "Необязательная покупка")
        if (kind == FinancialQuestionKind.PLAN_REVIEW) return planReviewQuestion(period, id,
            budgetReport?.takeIf { it.periodId == period.id })
        if (kind == FinancialQuestionKind.SAVING_PRACTICE) return savingPracticeQuestion(period, id)
        val entries = listOf(
            LedgerEntry("${period.id}:income", LedgerKind.INCOME, period.income),
            LedgerEntry("${period.id}:spending", LedgerKind.AVAILABLE_EXPENSE, period.spentAvailable),
            LedgerEntry("${period.id}:goal", LedgerKind.SAVINGS_EXPENSE, period.spentSavings),
            LedgerEntry("${period.id}:deposit", LedgerKind.DEPOSIT, period.deposited),
            LedgerEntry("${period.id}:withdrawal", LedgerKind.WITHDRAWAL, period.withdrawn),
        )
        val task = AssessmentTask.ReadLedger(period.openingAvailable, period.openingSavings,
            entries, LedgerQuestion.EXPENSE, 0)
        val expense = task.expectedAnswer()
        val options = listOf(expense, Math.addExact(expense, maxOf(1L, period.deposited)),
            Math.addExact(expense, maxOf(2L, Math.addExact(period.deposited, 1))))
        return FinancialQuestion(id, kind,
            "В этой главе обычные покупки стоили ${period.spentAvailable} монет, а снаряжение для цели — ${period.spentSavings}. " +
                "В копилку положили ${period.deposited} монет, а обратно взяли ${period.withdrawn}. Сколько всего потратили на покупки?",
            // Rotate the position using a stable ID; the correct answer is not always the first button.
            options.map { FinancialAnswerOption(it.toString(), "$it монет") }.let { values ->
                val rotation = (id.hashCode().toLong().and(0x7fffffff) % values.size).toInt()
                values.drop(rotation) + values.take(rotation)
            }, expense.toString(),
            "Потратили ${period.spentAvailable} + ${period.spentSavings} = $expense монет. " +
                "Монеты, которые перекладываем в копилку и обратно, остаются нашими — это не трата.",
            sourceActionIds = listOf(period.id), ledgerTask = task)
    }

    private fun planReviewQuestion(period: FinancialPeriod, id: String, report: PeriodBudgetReport?): FinancialQuestion {
        val comparisons = report?.comparisons.orEmpty().filter { it.complete }
        val comparison = comparisons.lastOrNull { it.actual.availableExpenses > 0 || it.actual.deposited > 0 || it.actual.withdrawn > 0 }
            ?: comparisons.lastOrNull()
        if (comparison == null) return FinancialQuestion(id, FinancialQuestionKind.PLAN_REVIEW,
            "Представь: на ремонт рюкзака ушло больше монет, чем собирались. Что делать с бюджетом?",
            listOf(FinancialAnswerOption("compare", "Проверить траты и распределить оставшиеся монеты заново"),
                FinancialAnswerOption("erase", "Оставить прежние цифры и не учитывать ремонт"),
                FinancialAnswerOption("money", "Увеличить цифры, чтобы потраченные монеты вернулись")),
            "compare", "Ремонт уже оплачен. Считаем оставшиеся монеты и сначала оставляем на еду.",
            sourceActionIds = listOf(period.id), reviewEvidence = PeriodReviewEvidence(id, null, false, false, guidedRecovery = true))
        val actual = comparison.actual
        val section = when {
            actual.needs > 0 -> BudgetSection.NEEDS
            actual.wants > 0 -> BudgetSection.WANTS
            actual.reserve > 0 -> BudgetSection.RESERVE
            actual.deposited > 0 || actual.withdrawn > 0 -> BudgetSection.SAVINGS
            else -> BudgetSection.NEEDS
        }
        val planned = comparison.revision.allocation.amount(section)
        val spent = actual.amount(section)
        val label = when (section) {
            BudgetSection.NEEDS -> "еду и нужные покупки"
            BudgetSection.WANTS -> "приятные покупки"
            BudgetSection.RESERVE -> "неожиданные траты"
            BudgetSection.SAVINGS -> "пополнение копилки"
        }
        val correct = when { spent > planned -> "more"; spent < planned -> "less"; else -> "equal" }
        val prompt = if (section == BudgetSection.SAVINGS)
            "Хотели добавить в копилку $planned монет. Положили ${actual.deposited}, а взяли обратно ${actual.withdrawn}. " +
                "Удалось сберечь больше, меньше или столько же, сколько собирались?"
        else "На $label оставили $planned монет, а потратили $spent. " +
            "Потратили больше, меньше или столько же, сколько собирались?"
        val explanation = if (section == BudgetSection.SAVINGS && spent < 0) {
            val taken = Math.negateExact(spent)
            "Взяли на $taken монет больше, чем положили: ${actual.withdrawn} − ${actual.deposited} = $taken. " +
                "Копилка стала меньше, хотя мы хотели добавить $planned монет."
        } else {
            val difference = when {
                spent > planned -> "На ${Math.subtractExact(spent, planned)} монет больше: $spent − $planned = ${Math.subtractExact(spent, planned)}."
                spent < planned -> "На ${Math.subtractExact(planned, spent)} монет меньше: $planned − $spent = ${Math.subtractExact(planned, spent)}."
                else -> "Столько же, сколько собирались: $planned монет."
            }
            if (section == BudgetSection.SAVINGS)
                "Всего сберегли ${actual.deposited} − ${actual.withdrawn} = $spent монет. $difference"
            else difference
        }
        val recoveryPlan = comparisons.lastOrNull { candidate ->
            candidate.revision.ordinal > comparison.revision.ordinal && candidate.managedPlan == true &&
                candidate.revision.knownNeeds?.let { candidate.revision.allocation.needs >= minOf(it, candidate.revision.availableBasis) } == true
        }?.revision?.id
        return FinancialQuestion(id, FinancialQuestionKind.PLAN_REVIEW,
            prompt,
            listOf(FinancialAnswerOption("more", "Больше, чем собирались"), FinancialAnswerOption("less", "Меньше, чем собирались"),
                FinancialAnswerOption("equal", "Столько же")), correct, explanation,
            sourceActionIds = listOf(period.id) + comparison.sourceActionIds,
            reviewEvidence = PeriodReviewEvidence(id, comparison.revision.id, true, false,
                managedPlan = comparison.managedPlan, recoveryPlanRevisionId = recoveryPlan))
    }

    private fun savingPracticeQuestion(period: FinancialPeriod, id: String): FinancialQuestion =
        FinancialQuestion(id, FinancialQuestionKind.SAVING_PRACTICE,
            "Как копить на снаряжение и не забывать о еде?",
            listOf(FinancialAnswerOption("repeat", "Каждый раз оставлять на еду, а часть новых монет откладывать"),
                FinancialAnswerOption("split", "Разделить одно пополнение на много маленьких"),
                FinancialAnswerOption("circle", "Взять монеты из копилки и вернуть — тогда их станет больше")),
            "repeat", "Каждый раз бережём часть новых монет. Так копилка растёт, а на еду остаётся.", sourceActionIds = listOf(period.id))

    /** A teaching situation uses current balances and a real catalog price, without purchasing anything. */
    private fun consequenceQuestion(state: GameState, period: FinancialPeriod, id: String,
        knownNeeds: Long, price: Long, title: String): FinancialQuestion {
        require(knownNeeds >= 0 && price > 0)
        val available = state.economy.availableBalance
        val savings = state.economy.savingsBalance
        val affordable = available >= price
        val remainder = if (affordable) available - price else null
        val covered = remainder != null && remainder >= knownNeeds
        val correctId: String
        val options: List<FinancialAnswerOption>
        val explanation: String
        if (!affordable) {
            correctId = "not_affordable"
            options = listOf(
                FinancialAnswerOption(correctId, "Нет, не хватает ${price - available} монет"),
                FinancialAnswerOption("automatic_savings", "Да, недостающее само возьмётся из копилки"),
                FinancialAnswerOption("plan_pays", "Да, если записать покупку в бюджет"),
            )
            explanation = "Не хватает $price − $available = ${price - available} монет. " +
                "Копилка не оплачивает покупку сама. Деньги из неё берём отдельным действием."
        } else {
            correctId = if (covered) "needs_covered" else "needs_uncovered"
            val uncovered = if (available < knownNeeds)
                "Нет, на еду не хватало и раньше, а теперь останется ещё меньше"
            else "Нет, после покупки на еду не хватит"
            options = listOf(
                FinancialAnswerOption("needs_covered", "Да, на еду останется достаточно"),
                FinancialAnswerOption("needs_uncovered", uncovered),
                FinancialAnswerOption("automatic_savings", "Да, покупку сама оплатит копилка"),
            )
            explanation = "После покупки останется $available − $price = $remainder монет. " +
                if (covered) "На еду нужно $knownNeeds — хватает."
                else if (available < knownNeeds) "На еду не хватало и раньше, а теперь не хватит ${knownNeeds - checkNotNull(remainder)} монет."
                else "На еду нужно $knownNeeds, не хватает ${knownNeeds - checkNotNull(remainder)} монет."
        }
        val rotation = (id.hashCode().toLong().and(0x7fffffff) % options.size).toInt()
        return FinancialQuestion(id, FinancialQuestionKind.CONSEQUENCE,
            if (!affordable) "У нас $available монет, а «$title» стоит $price. В копилке $savings. " +
                "Можем оплатить покупку, не открывая копилку?"
            else "У нас $available монет. На еду до следующей недели нужно $knownNeeds. " +
                "Если купим «$title» за $price, хватит ли после этого на еду?",
            options.drop(rotation) + options.take(rotation), correctId, explanation,
            sourceActionIds = listOf(period.id), comparisonFamily = "optional_purchase")
    }
}
