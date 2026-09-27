package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.finance.FinancialPeriods
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

/** Refreshes wording from the original question boundary without changing the saved task or answer. */
internal fun financialPracticePresentation(
    question: FinancialQuestion?,
    history: List<AuditEntry>,
    catalog: GameCatalog,
): FinancialQuestion? {
    if (question == null || question.kind != FinancialQuestionKind.PLAN_REVIEW || question.reviewEvidence == null) {
        return question
    }
    val anchor = history.firstOrNull { entry ->
        entry.type == AuditType.COMMAND && entry.after?.financial?.practice?.id == question.id &&
            (entry.request?.command as? EngineCommand.RequestFinancialPractice)?.kind == FinancialQuestionKind.PLAN_REVIEW
    } ?: return question
    val before = anchor.before ?: return question
    val period = before.financial.currentPeriod ?: return question
    val fresh = runCatching {
        val priorHistory = history.filter { it.sequence < anchor.sequence }
        val report = FinancialBudgetProjection.report(before, priorHistory, catalog.content)
            .firstOrNull { it.periodId == period.id }
        FinancialPeriods.question(before, question.id, FinancialQuestionKind.PLAN_REVIEW, budgetReport = report)
    }.getOrNull() ?: return question
    if (fresh.correctAnswerId != question.correctAnswerId ||
        fresh.options.map { it.id }.toSet() != question.options.map { it.id }.toSet() ||
        fresh.reviewEvidence != question.reviewEvidence || fresh.sourceActionIds != question.sourceActionIds) {
        return question
    }
    val options = fresh.options.associateBy { it.id }
    return question.copy(
        prompt = fresh.prompt,
        explanation = fresh.explanation,
        options = question.options.map { options.getValue(it.id) },
    )
}
