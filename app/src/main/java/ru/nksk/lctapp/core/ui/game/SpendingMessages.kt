package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.economy.SpendingQuote

/** Presentation of the same quote used by the domain when committing a payment. */
internal fun SpendingQuote.playerDescription(kind: SpendingKind): String? {
    if (blocked) return null
    if (missing > 0) return "Не хватает $missing монет. Деньги не будут списаны."
    if (parts.isEmpty()) return null
    val first = when (kind) {
        SpendingKind.GENERAL -> BudgetSection.RESERVE
        SpendingKind.WANT -> BudgetSection.WANTS
        SpendingKind.FEEDING -> BudgetSection.NEEDS
        SpendingKind.GOAL -> BudgetSection.SAVINGS
        SpendingKind.EARNING -> return null
    }
    val amounts = parts.joinToString(" · ") { part ->
        val title = when (part.section) {
            BudgetSection.NEEDS -> "Нужно"
            BudgetSection.WANTS -> "Хочу"
            BudgetSection.SAVINGS -> "Коплю"
            BudgetSection.RESERVE -> "Запас"
        }
        "$title: ${part.amount}"
    }
    return "Спишется: $amounts." + if (parts.any { it.section != first })
        " Используем деньги из других статей. В следующий раз учти эту трату при планировании." else ""
}
