package ru.nksk.lctapp.feature.economy.ui

import ru.nksk.lctapp.domain.analytics.AnalyticsMode
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LearningContext
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

internal data class UnexpectedExpenseUi(val operationId: String, val title: String, val amount: Long, val day: Int?)

/** A recorded expense is an option, never an inferred explanation for the child's new plan. */
internal fun unexpectedExpenseOptions(history: List<AuditEntry>, catalog: GameCatalog): List<UnexpectedExpenseUi> {
    val runId = history.maxByOrNull { it.sequence }?.runId ?: return emptyList()
    val currentRun = history.filter { it.runId == runId }
    val recovered = currentRun.flatMap { it.facts }
        .filter { it.mode == AnalyticsMode.REAL && it.learningContext == LearningContext.GAME }
        .mapNotNull { it.detail as? FactDetail.RecoveryAction }
        .filter { it.completed }.map { it.expenseOperationId }.toSet()
    return currentRun.sortedByDescending { it.sequence }.flatMap { entry ->
        if (entry.type != AuditType.COMMAND) return@flatMap emptyList()
        entry.facts.mapNotNull facts@{ fact ->
            if (fact.mode != AnalyticsMode.REAL || fact.learningContext != LearningContext.GAME) return@facts null
            val expense = fact.detail as? FactDetail.UnexpectedExpense ?: return@facts null
            if (expense.previouslyDisclosed || expense.operationId in recovered) return@facts null
            val operation = entry.operations.singleOrNull { it.operationId == expense.operationId } ?: return@facts null
            if (operation.kind !in setOf(LedgerKind.AVAILABLE_EXPENSE, LedgerKind.SAVINGS_EXPENSE) ||
                operation.amount != expense.amount || operation.amount <= 0) return@facts null
            val journal = entry.after?.engine?.journal?.find { it.id == expense.operationId }
            val eventId = when (journal?.kind) {
                DayJournalKind.EVENT_START -> journal.sourceId
                DayJournalKind.EVENT_CHOICE -> catalog.content.choices.find { it.id == journal.sourceId }?.eventId
                else -> null
            }
            UnexpectedExpenseUi(expense.operationId,
                catalog.content.events.find { it.id == eventId }?.title ?: "Неожиданная трата",
                operation.amount, entry.after?.engine?.day ?: fact.context.day)
        }
    }.distinctBy { it.operationId }
}
