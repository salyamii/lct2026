package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.MealPolicy
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.finance.FinancialPeriods
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.finance.FinancialTraining
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

/** Refreshes wording from the original question boundary without changing the saved task or answer. */
internal fun financialPracticePresentation(
    question: FinancialQuestion?,
    history: List<AuditEntry>,
    catalog: GameCatalog,
): FinancialQuestion? {
    if (question == null) return null
    FinancialTraining.exampleWording(question)?.let { return copyVerifiedWording(question, it) }
    val anchor = history.firstOrNull { entry ->
        entry.type == AuditType.COMMAND && entry.after?.financial?.practice?.id == question.id &&
            (entry.request?.command as? EngineCommand.RequestFinancialPractice)?.kind == question.kind
    } ?: return question
    val before = anchor.before ?: return question
    val period = before.financial.currentPeriod ?: return question
    val fresh = runCatching {
        val priorHistory = history.filter { it.sequence < anchor.sequence }
        val report = FinancialBudgetProjection.report(before, priorHistory, catalog.content)
            .firstOrNull { it.periodId == period.id }
        val purchase = catalog.content.choices.firstOrNull { choice ->
            choice.moneyDelta < 0 && catalog.content.events.any { it.id == choice.eventId && it.type == EventType.WANT }
        }
        val price = purchase?.let { Math.negateExact(it.moneyDelta) }
        val title = purchase?.let { choice -> catalog.content.events.first { it.id == choice.eventId }.title }
        val needs = MealPolicy(catalog.meals).foodRequirement(before)
        // Older consequence questions did not store a structured purchase task. Verify the
        // exact authored price/title and food boundary from the saved prompt before rewording.
        if (question.kind == FinancialQuestionKind.CONSEQUENCE &&
            (price == null || title == null || !question.prompt.contains("«$title»") ||
                !(question.prompt.contains("стоит $price.") || question.prompt.contains("за $price,") ||
                    question.prompt.contains("за $price только")) ||
                question.correctAnswerId != "not_affordable" &&
                !question.prompt.contains("На еду до следующей недели нужно $needs."))) return question
        FinancialPeriods.question(before, question.id, question.kind, knownNeeds = needs,
            purchasePrice = price, purchaseTitle = title, budgetReport = report)
    }.getOrNull() ?: return question
    return copyVerifiedWording(question, fresh)
}

private fun copyVerifiedWording(question: FinancialQuestion, fresh: FinancialQuestion): FinancialQuestion {
    if (fresh.correctAnswerId != question.correctAnswerId ||
        fresh.options.map { it.id }.toSet() != question.options.map { it.id }.toSet() ||
        fresh.reviewEvidence != question.reviewEvidence || fresh.sourceActionIds != question.sourceActionIds ||
        fresh.ledgerTask != question.ledgerTask || fresh.comparisonFamily != question.comparisonFamily) {
        return question
    }
    val options = fresh.options.associateBy { it.id }
    return question.copy(
        prompt = fresh.prompt,
        explanation = fresh.explanation,
        options = question.options.map { options.getValue(it.id) },
    )
}
