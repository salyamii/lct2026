package ru.nksk.lctapp.domain.history

import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState

/** Shared receipts for the real commit and isolated replay. A net delta is never a substitute. */
object CanonicalLedger {
    fun fromTransition(before: GameState, after: GameState, request: EngineRequest): List<LedgerEntry> {
        val command = request.command
        val entries = when (command) {
            is EngineCommand.DepositSavings -> listOf(LedgerEntry("${request.id}:deposit", LedgerKind.DEPOSIT, command.amount))
            is EngineCommand.WithdrawSavings -> listOf(LedgerEntry("${request.id}:withdrawal", LedgerKind.WITHDRAWAL, command.amount))
            else -> {
                val previous = before.engine?.journal.orEmpty().map { it.id }.toSet()
                after.engine?.journal.orEmpty().filter { it.id !in previous && it.moneyDelta != 0L }.map { entry ->
                    val kind = when {
                        entry.moneyDelta > 0 -> LedgerKind.INCOME
                        command is EngineCommand.BuyGoalItem -> LedgerKind.SAVINGS_EXPENSE
                        else -> LedgerKind.AVAILABLE_EXPENSE
                    }
                    LedgerEntry(entry.id, kind, if (entry.moneyDelta >= 0) entry.moneyDelta else Math.negateExact(entry.moneyDelta))
                }
            }
        }
        validate(before, after, entries)
        return entries
    }

    fun validate(before: GameState, after: GameState, entries: List<LedgerEntry>) {
        require(entries.map { it.operationId }.distinct().size == entries.size) { "Duplicate financial receipt" }
        var available = before.economy.availableBalance
        var savings = before.economy.savingsBalance
        for (entry in entries) when (entry.kind) {
            LedgerKind.INCOME -> available = Math.addExact(available, entry.amount)
            LedgerKind.AVAILABLE_EXPENSE -> available = Math.subtractExact(available, entry.amount)
            LedgerKind.SAVINGS_EXPENSE -> savings = Math.subtractExact(savings, entry.amount)
            LedgerKind.DEPOSIT -> { available = Math.subtractExact(available, entry.amount); savings = Math.addExact(savings, entry.amount) }
            LedgerKind.WITHDRAWAL -> { savings = Math.subtractExact(savings, entry.amount); available = Math.addExact(available, entry.amount) }
        }
        require(available == after.economy.availableBalance && savings == after.economy.savingsBalance) {
            "Financial receipts do not reconcile with both saved accounts"
        }
    }
}
