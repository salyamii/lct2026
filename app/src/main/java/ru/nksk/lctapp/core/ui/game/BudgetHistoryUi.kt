package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.CanonicalLedger

/** Actual movements since a verified confirmation, not an interpretation of the planned categories. */
internal data class BudgetHistoryUi(
    val planId: String,
    val startingAvailable: Long,
    val startingSavings: Long,
    val income: Long,
    val spentAvailable: Long,
    val deposited: Long,
    val withdrawn: Long,
    val spentSavings: Long,
    val resultingAvailable: Long,
    val resultingSavings: Long,
)

/** Null means the history cannot fully explain this snapshot; missing amounts are never inferred. */
internal fun budgetHistoryUi(game: GameState, history: List<AuditEntry>): BudgetHistoryUi? = try {
    checkedBudgetHistoryUi(game, history)
} catch (_: IllegalArgumentException) {
    null
} catch (_: ArithmeticException) {
    null
}

private fun checkedBudgetHistoryUi(game: GameState, history: List<AuditEntry>): BudgetHistoryUi? {
    // The latest global revision is authoritative, even when an older revision has identical amounts.
    val plan = game.financial.plans.lastOrNull() ?: return null
    if (history.isEmpty()) return null
    val ordered = history.sortedBy { it.sequence }
    require(ordered.map { it.runId }.distinct().size == 1)
    require(ordered.map { it.id }.distinct().size == ordered.size)
    require(ordered.map { it.sequence }.distinct().size == ordered.size)
    val commandIds = ordered.filter { it.type == AuditType.COMMAND }.map { checkNotNull(it.request).id }
    require(commandIds.distinct().size == commandIds.size)
    val operationIds = ordered.flatMap { it.operations }.map { it.operationId }
    require(operationIds.distinct().size == operationIds.size)

    val anchor = ordered.singleOrNull { entry ->
        entry.type == AuditType.COMMAND && entry.request?.command is EngineCommand.ConfirmBudget &&
            entry.before?.financial?.plans?.none { it.id == plan.id } == true &&
            entry.after?.financial?.plans?.lastOrNull() == plan
    } ?: return null
    val before = checkNotNull(anchor.before)
    val start = checkNotNull(anchor.after)
    require(start.financial.plans == before.financial.plans + plan)
    require(start.economy.plan == plan.allocation && start.economy.planning == null && start.economy.unallocated == 0L)
    require(plan.availableBasis == start.economy.availableBalance && plan.allocation.total == plan.availableBasis)
    // Confirming an intention cannot grant, spend or transfer real coins.
    require(anchor.operations.isEmpty())
    CanonicalLedger.validate(before, start, anchor.operations)

    val tail = ordered.dropWhile { it.sequence < anchor.sequence }
    require(tail.zipWithNext().all { (previous, next) -> next.sequence == Math.addExact(previous.sequence, 1L) })
    var checkpoint = start
    var income = 0L
    var spentAvailable = 0L
    var deposited = 0L
    var withdrawn = 0L
    var spentSavings = 0L
    // Only movements after confirmation belong to this explanation.
    for (entry in tail.drop(1)) {
        when (entry.type) {
            AuditType.COMMAND, AuditType.PARENT_REWARD -> {
                require(entry.before == checkpoint)
                val after = checkNotNull(entry.after)
                CanonicalLedger.validate(checkpoint, after, entry.operations)
                entry.operations.forEach { operation ->
                    when (operation.kind) {
                        LedgerKind.INCOME -> income = Math.addExact(income, operation.amount)
                        LedgerKind.AVAILABLE_EXPENSE -> spentAvailable = Math.addExact(spentAvailable, operation.amount)
                        LedgerKind.DEPOSIT -> deposited = Math.addExact(deposited, operation.amount)
                        LedgerKind.WITHDRAWAL -> withdrawn = Math.addExact(withdrawn, operation.amount)
                        LedgerKind.SAVINGS_EXPENSE -> spentSavings = Math.addExact(spentSavings, operation.amount)
                    }
                }
                checkpoint = after
            }
            AuditType.TECHNICAL_UPDATE -> {
                require(entry.before == checkpoint && entry.operations.isEmpty())
                val after = entry.after ?: return null
                require(after.economy.availableBalance == checkpoint.economy.availableBalance &&
                    after.economy.savingsBalance == checkpoint.economy.savingsBalance)
                checkpoint = after
            }
            AuditType.FACTS, AuditType.REJECTED ->
                require(entry.before == null && entry.after == null && entry.operations.isEmpty())
            AuditType.INITIALIZED, AuditType.IMPORTED_BASELINE, AuditType.RESTORED -> return null
        }
    }
    require(checkpoint == game)
    return BudgetHistoryUi(plan.id, start.economy.availableBalance, start.economy.savingsBalance,
        income, spentAvailable, deposited, withdrawn, spentSavings,
        game.economy.availableBalance, game.economy.savingsBalance)
}
